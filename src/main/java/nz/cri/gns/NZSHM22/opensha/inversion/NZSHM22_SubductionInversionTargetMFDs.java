package nz.cri.gns.NZSHM22.opensha.inversion;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.commons.util.modules.SubModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.MFDEqualityInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.MFDInequalityInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.modules.SubSeismoOnFaultMFDs;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import nz.cri.gns.NZSHM22.opensha.analysis.NZSHM22_FaultSystemRupSetCalc;
import nz.cri.gns.NZSHM22.opensha.data.region.NewZealandRegions;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_SpatialSeisPDF;
import nz.cri.gns.NZSHM22.opensha.ruptures.NZSHM22_SlipEnabledRuptureSet;
import scratch.UCERF3.SlipEnabledRupSet;
import scratch.UCERF3.analysis.DeformationModelsCalc;

import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.InversionModels;
import scratch.UCERF3.enumTreeBranches.MaxMagOffFault;
import scratch.UCERF3.enumTreeBranches.MomentRateFixes;
import scratch.UCERF3.enumTreeBranches.SpatialSeisPDF;
import scratch.UCERF3.enumTreeBranches.TotalMag5Rate;
import scratch.UCERF3.griddedSeismicity.GriddedSeisUtils;
import scratch.UCERF3.inversion.InversionFaultSystemRupSet;
import scratch.UCERF3.inversion.InversionTargetMFDs;
import scratch.UCERF3.inversion.U3InversionTargetMFDs;
import scratch.UCERF3.logicTree.U3LogicTreeBranch;
import scratch.UCERF3.utils.MFD_InversionConstraint;
import scratch.UCERF3.utils.RELM_RegionUtils;

/**
 * This class constructs and stores the various pre-inversion MFD Targets.
 * 
 * Details on what's returned are:
 * 
 * getTotalTargetGR() returns:
 * 
 * The total regional target GR (Same for both GR and Char branches)
 * 
 * getTotalGriddedSeisMFD() returns:IncrementalMagFreqDist
 * 
 * getTrulyOffFaultMFD()+getTotalSubSeismoOnFaultMFD()
 * 
 * getTotalOnFaultMFD() returns:
 * 
 * getTotalSubSeismoOnFaultMFD() + getOnFaultSupraSeisMFD();
 * 
 * TODO: this contains mostly UCERF3 stuff that will be replaced for NSHM
 * 
 * @author chrisbc
 *
 */
public class NZSHM22_SubductionInversionTargetMFDs extends NZSHM22_InversionTargetMFDs{

	static boolean MFD_STATS = true; //print some curves for analytics
	
//	// discretization parameters for MFDs
	public final static double MIN_MAG = 5.05;
	public final static double MAX_MAG = 9.75;
	public final static int NUM_MAG = (int) ((MAX_MAG - MIN_MAG) * 10.0d);
	public final static double DELTA_MAG = 0.1;

	protected List<MFD_InversionConstraint> mfdConstraints;
	protected List<IncrementalMagFreqDist> mfdConstraintComponents;

	public  NZSHM22_SubductionInversionTargetMFDs (NZSHM22_InversionFaultSystemRuptSet invRupSet){
		this(invRupSet, 0.7, 1.1, 7.85);
	}

	public NZSHM22_SubductionInversionTargetMFDs(NZSHM22_InversionFaultSystemRuptSet invRupSet,
									  double totalRateM5, double bValue, double mfdTransitionMag){
		
		// TODO: we're getting a UCERF3 LTB now, this needs to be replaced with NSHM
		// equivalent
		U3LogicTreeBranch logicTreeBranch = invRupSet.getLogicTreeBranch();
		InversionModels inversionModel = logicTreeBranch.getValue(InversionModels.class);
		//this.totalRegionRateMgt5 = this.totalRateM5;
		double mMaxOffFault = logicTreeBranch.getValue(MaxMagOffFault.class).getMaxMagOffFault(); //TODO: set this to 8.05 (more NZ ish)

		// convert mMaxOffFault to bin center
		List<? extends FaultSection> faultSectionData = invRupSet.getFaultSectionDataList();
		
		double origOnFltDefModMoRate = DeformationModelsCalc.calculateTotalMomentRate(faultSectionData,true);
		
		// make the total target GR MFD
		// TODO: why MIN_MAG = 0 ??
		GutenbergRichterMagFreqDist totalTargetGR = new GutenbergRichterMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);
		if (MFD_STATS) {
			System.out.println("totalTargetGR");
			System.out.println(totalTargetGR.toString());
			System.out.println("");	
		}
		
		// sorting out scaling
		double roundedMmaxOnFault = totalTargetGR.getX(totalTargetGR.getClosestXIndex(invRupSet.getMaxMag()));
		totalTargetGR.setAllButTotMoRate(MIN_MAG, roundedMmaxOnFault, totalRateM5, bValue); //TODO: revisit
		
		if (MFD_STATS) {
			System.out.println("totalTargetGR after setAllButTotMoRate");
			System.out.println(totalTargetGR.toString());
			System.out.println("");		
		}
		
//		IncrementalMagFreqDist subMagSevenMFD = totalTargetGR.deepClone();
//
//		This is a horrible API !!
//		int idx7 = subMagSevenMFD.getClosestXIndex(6.8);
//		double magAtSeven = subMagSevenMFD.getX(idx7);
//		subMagSevenMFD.zeroAboveMag(magAtSeven);

		//Doctor the target, setting a small value instead of 0
	 	for (int i = 0; i < 20; i++) {
			totalTargetGR.set(i, 1.0e-20); 
		}
		
		SummedMagFreqDist targetOnFaultSupraSeisMFD = new SummedMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);
		targetOnFaultSupraSeisMFD.addIncrementalMagFreqDist(totalTargetGR);
//		targetOnFaultSupraSeisMFD.subtractIncrementalMagFreqDist(subMagSevenMFD);
//		targetOnFaultSupraSeisMFD.subtractIncrementalMagFreqDist(totalSubSeismoOnFaultMFD);
//
		if (MFD_STATS) {
			System.out.println("targetOnFaultSupraSeisMFD (SummedMagFreqDist)");
			System.out.println(targetOnFaultSupraSeisMFD.toString());
			System.out.println("");		
		}		
//		

//		// compute coupling coefficients
//		impliedOnFaultCouplingCoeff = (targetOnFaultSupraSeisMFD.getTotalMomentRate()
//				+ totalSubSeismoOnFaultMFD.getTotalMomentRate()) / origOnFltDefModMoRate;
//		impliedTotalCouplingCoeff = totalTargetGR.getTotalMomentRate() / (origOnFltDefModMoRate + offFltDefModMoRate);

		// Build the MFD Constraints for regions
		List<MFD_InversionConstraint> mfdConstraints = new ArrayList<>();

		mfdConstraints.add(new MFD_InversionConstraint(targetOnFaultSupraSeisMFD, null));	
		
		// Now collect the target MFDS we might want for plots
		targetOnFaultSupraSeisMFD.setName("targetOnFaultSupraSeisMFD");
		List<IncrementalMagFreqDist> mfdConstraintComponents = new ArrayList<>();
		mfdConstraintComponents.add(targetOnFaultSupraSeisMFD);

		setParent(invRupSet);
		this.totalTargetGR = totalTargetGR;
		this.targetOnFaultSupraSeisMFD = targetOnFaultSupraSeisMFD;
		this.mfdConstraints = mfdConstraints;
		this.mfdConstraintComponents = mfdConstraintComponents;


//		return new InversionTargetMFDs.Precomputed( invRupSet,
//				totalTargetGR, targetOnFaultSupraSeisMFD, null,
//				null, mfdConstraints, null);

	}

	@Override
	public List<MFD_InversionConstraint> getMFD_Constraints(){
		return mfdConstraints;
	}

	public List<IncrementalMagFreqDist> getMFDConstraintComponents(){
		return mfdConstraintComponents;
	}

	@Override
	public String getName() {
		return "NZSHM22 Subduction Inversion Target MFDs";
	}

	// only used for plots
	@Override
	public GutenbergRichterMagFreqDist getTotalTargetGR_NoCal() {throw new UnsupportedOperationException();}

	// only used for plots
	@Override
	public GutenbergRichterMagFreqDist getTotalTargetGR_SoCal() {throw new UnsupportedOperationException();}

	@Override
	public double[] getPDF(){
		return spatialSeisPDF.getPDF();
	}

}

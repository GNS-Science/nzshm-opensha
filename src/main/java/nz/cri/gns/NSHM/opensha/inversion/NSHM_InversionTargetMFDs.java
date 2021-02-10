package nz.cri.gns.NSHM.opensha.inversion;

import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.geo.GriddedRegion;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;

import nz.cri.gns.NSHM.opensha.ruptures.NSHMSlipEnabledRuptureSet;
import scratch.UCERF3.SlipEnabledRupSet;
import scratch.UCERF3.analysis.DeformationModelsCalc;
import scratch.UCERF3.analysis.FaultSystemRupSetCalc;
import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.InversionModels;
import scratch.UCERF3.enumTreeBranches.MaxMagOffFault;
import scratch.UCERF3.enumTreeBranches.MomentRateFixes;
import scratch.UCERF3.enumTreeBranches.SpatialSeisPDF;
import scratch.UCERF3.enumTreeBranches.TotalMag5Rate;
import scratch.UCERF3.griddedSeismicity.GriddedSeisUtils;
import scratch.UCERF3.inversion.InversionTargetMFDs;
import scratch.UCERF3.logicTree.LogicTreeBranch;
import scratch.UCERF3.utils.MFD_InversionConstraint;
import scratch.UCERF3.utils.RELM_RegionUtils;

/**
 * TODO: this contains mostly UCERF3 stuff that will be replaced for NSHM
 *  
 * @author chrisbc
 *
 */
public class NSHM_InversionTargetMFDs extends InversionTargetMFDs {
	
	// debugging flag
	final static boolean D = false;
	final static boolean GR_OFF_FAULT_IS_TAPERED = true;
	String debugString;
	
	SlipEnabledRupSet invRupSet;
	double totalRegionRateMgt5, onFaultRegionRateMgt5, offFaultRegionRateMgt5;
	double mMaxOffFault;
	boolean applyImpliedCouplingCoeff;
	SpatialSeisPDF spatialSeisPDF;
	SpatialSeisPDF spatialSeisPDFforOnFaultRates;
	InversionModels inversionModel;
	GriddedSeisUtils gridSeisUtils;
	
	double origOnFltDefModMoRate, offFltDefModMoRate, aveMinSeismoMag, roundedMmaxOnFault;
	double fractSeisInSoCal;
	double fractionSeisOnFault;
	double impliedOnFaultCouplingCoeff;
	double impliedTotalCouplingCoeff;
	double finalOffFaultCouplingCoeff;
	GutenbergRichterMagFreqDist totalTargetGR, totalTargetGR_NoCal, totalTargetGR_SoCal;
	SummedMagFreqDist targetOnFaultSupraSeisMFD;
	IncrementalMagFreqDist trulyOffFaultMFD;
	ArrayList<GutenbergRichterMagFreqDist> subSeismoOnFaultMFD_List;
	SummedMagFreqDist totalSubSeismoOnFaultMFD;		// this is a sum of the MFDs in subSeismoOnFaultMFD_List
	IncrementalMagFreqDist noCalTargetSupraMFD, soCalTargetSupraMFD;

	
	List<MFD_InversionConstraint> mfdConstraintsForNoAndSoCal;

	// discretization parameters for MFDs
	public final static double MIN_MAG = 0.05;
	public final static double MAX_MAG = 8.95;
	public final static int NUM_MAG = 90;
	public final static double DELTA_MAG = 0.1;
	
	public final static double FAULT_BUFFER = 12d;	// buffer for fault polygons

	public NSHM_InversionTargetMFDs(NSHM_InversionFaultSystemRuptSet invRupSet) {
		this.invRupSet=invRupSet;
		
		LogicTreeBranch logicTreeBranch = invRupSet.getLogicTreeBranch();
		this.inversionModel = logicTreeBranch.getValue(InversionModels.class);
		this.totalRegionRateMgt5 = logicTreeBranch.getValue(TotalMag5Rate.class).getRateMag5();
		this.mMaxOffFault = logicTreeBranch.getValue(MaxMagOffFault.class).getMaxMagOffFault();
		this.applyImpliedCouplingCoeff = logicTreeBranch.getValue(MomentRateFixes.class).isApplyCC();	// true if MomentRateFixes = APPLY_IMPLIED_CC or APPLY_CC_AND_RELAX_MFD
		this.spatialSeisPDF = logicTreeBranch.getValue(SpatialSeisPDF.class);
		
		// convert mMaxOffFault to bin center
		mMaxOffFault -= DELTA_MAG/2;
		
		boolean noMoRateFix = (logicTreeBranch.getValue(MomentRateFixes.class) == MomentRateFixes.NONE);
		
		// this prevents using any non smoothed seismicity PDF for computing rates on fault (def mod PDF doesn't make sense)
//		if (!(spatialSeisPDF == SpatialSeisPDF.UCERF2 || spatialSeisPDF == SpatialSeisPDF.UCERF3))
//			System.out.println("WARNING: Was previously hardcoded (for unknown reasons) to force U3 or U2 spatial seismicity on faults. "
//					+ "This has been disabled.");
			
//		spatialSeisPDFforOnFaultRates = spatialSeisPDF;
//		else
//			spatialSeisPDFforOnFaultRates = SpatialSeisPDF.UCERF3;

		
		// test to make sure it's a statewide deformation model
//		DeformationModels dm = invRupSet.getDeformationModel();
//		if(dm == DeformationModels.UCERF2_BAYAREA || dm == DeformationModels.UCERF2_NCAL)
//			throw new RuntimeException("Error - "+dm+" not yet supported by InversionMFD");
		
		List<? extends FaultSection> faultSectionData =  invRupSet.getFaultSectionDataList();
		
		gridSeisUtils = new GriddedSeisUtils(faultSectionData, spatialSeisPDFforOnFaultRates, FAULT_BUFFER);
		
		GriddedRegion noCalGrid = RELM_RegionUtils.getNoCalGriddedRegionInstance();
		GriddedRegion soCalGrid = RELM_RegionUtils.getSoCalGriddedRegionInstance();
		
		fractSeisInSoCal = spatialSeisPDFforOnFaultRates.getFractionInRegion(soCalGrid);
//		fractionSeisOnFault = DeformationModelsCalc.getFractSpatialPDF_InsideSectionPolygons(faultSectionData, spatialSeisPDFforOnFaultRates);
		fractionSeisOnFault = gridSeisUtils.pdfInPolys();

		onFaultRegionRateMgt5 = totalRegionRateMgt5*fractionSeisOnFault;
		offFaultRegionRateMgt5 = totalRegionRateMgt5-onFaultRegionRateMgt5;
		origOnFltDefModMoRate = DeformationModelsCalc.calculateTotalMomentRate(faultSectionData,true);
		offFltDefModMoRate = DeformationModelsCalc.calcMoRateOffFaultsForDefModel(invRupSet.getFaultModel(), invRupSet.getDeformationModel());

		// make the total target GR for region
		totalTargetGR = new GutenbergRichterMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);
		roundedMmaxOnFault = totalTargetGR.getX(totalTargetGR.getClosestXIndex(invRupSet.getMaxMag()));
		totalTargetGR.setAllButTotMoRate(MIN_MAG, roundedMmaxOnFault, totalRegionRateMgt5*1e5, 1.0);
		
		totalTargetGR_NoCal = new GutenbergRichterMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);	
		totalTargetGR_NoCal.setAllButTotMoRate(MIN_MAG, roundedMmaxOnFault, totalRegionRateMgt5*(1-fractSeisInSoCal)*1e5, 1.0);
			
		totalTargetGR_SoCal = new GutenbergRichterMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);	
		totalTargetGR_SoCal.setAllButTotMoRate(MIN_MAG, roundedMmaxOnFault, totalRegionRateMgt5*fractSeisInSoCal*1e5, 1.0);
		
		// get ave min seismo mag for region
		double tempMag = FaultSystemRupSetCalc.getMeanMinMag(invRupSet, true);
		
		// This is a test of applying the minimum rather than average among section min mags in the tri-linear target
		// (increases on fault target by up to 11% (at mean mag) by doing this for case tested; not a big diff, and will make implied off-fault CC worse)
//		double tempMag = 100;
//		for(int s=0;s<invRupSet.getNumSections();s++) {
//			double minMag = invRupSet.getFinalMinMagForSection(s);
//			if(minMag<tempMag) tempMag = minMag;
//			if(minMag<6.301)
//				System.out.println("\t"+(float)minMag+"\t"+invRupSet.getFaultSectionData(s).getParentSectionName());
//		}
//		System.out.println("\ntempMag="+tempMag+"\n");
		
		aveMinSeismoMag = totalTargetGR.getX(totalTargetGR.getClosestXIndex(tempMag));	// round to nearest MFD value

		if(D) {
			debugString = "\ttotalRegionRateMgt5 =\t"+totalRegionRateMgt5+"\n"+
					"\tmMaxOffFault =\t"+mMaxOffFault+"\n"+
					"\tapplyImpliedCouplingCoeff =\t"+applyImpliedCouplingCoeff+"\n"+
					"\tspatialSeisPDF =\t"+spatialSeisPDF+"\n"+
					"\tspatialSeisPDFforOnFaultRates =\t"+spatialSeisPDFforOnFaultRates+"\n"+
					"\tinversionModel =\t"+inversionModel+"\n"+
					"\tfractSeisInSoCal =\t"+(float)fractSeisInSoCal+"\n"+
					"\tfractionSeisOnFault =\t"+(float)fractionSeisOnFault+"\n"+
					"\tonFaultRegionRateMgt5 =\t"+(float)onFaultRegionRateMgt5+"\n"+
					"\toffFaultRegionRateMgt5 =\t"+(float)offFaultRegionRateMgt5+"\n"+
					"\torigOnFltDefModMoRate =\t"+(float)origOnFltDefModMoRate+"\n"+
					"\toffFltDefModMoRate =\t"+(float)offFltDefModMoRate+"\n"+
					"\troundedMmaxOnFault =\t"+(float)roundedMmaxOnFault+"\n"+
					"\ttotalTargetGR(5.05) =\t"+(float)totalTargetGR.getCumRate(5.05)+"\n"+
					"\taveMinSeismoMag =\t"+(float)aveMinSeismoMag+"\n";
		}

		
//		if (inversionModel.isCharacteristic()) {
		
	}

}

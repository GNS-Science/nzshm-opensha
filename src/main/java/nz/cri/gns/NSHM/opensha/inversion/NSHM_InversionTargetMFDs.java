package nz.cri.gns.NSHM.opensha.inversion;

import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.MFDEqualityInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.MFDInequalityInversionConstraint;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import nz.cri.gns.NSHM.opensha.analysis.NSHM_FaultSystemRupSetCalc;
import nz.cri.gns.NSHM.opensha.data.region.NewZealandRegions;
import nz.cri.gns.NSHM.opensha.enumTreeBranches.NSHM_SpatialSeisPDF;
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
 * This class constructs and stores the various pre-inversion MFD Targets.
 * 
 * Details on what's returned are:
 * 
 * getTotalTargetGR() returns:
 * 
 * The total regional target GR (Same for both GR and Char branches)
 * 
 * getTotalGriddedSeisMFD() returns:
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
public class NSHM_InversionTargetMFDs extends InversionTargetMFDs {

	NSHM_SpatialSeisPDF spatialSeisPDF;
	NSHM_SpatialSeisPDF spatialSeisPDFforOnFaultRates;

//	// discretization parameters for MFDs
//	public final static double MIN_MAG = 0.05;
//	public final static double MAX_MAG = 8.95;
//	public final static int NUM_MAG = 90;
//	public final static double DELTA_MAG = 0.1;

	/*
	 * MFD constraint default settings
	 */
	protected double totalRateM5 = 5d;
	protected double bValue = 1d;
	protected double mfdTransitionMag = 7.85; // TODO: how to validate this number for NZ? (ref Morgan Page in
												// USGS/UCERF3) [KKS, CBC]
	protected int mfdNum = 40;
	protected double mfdMin = 5.05d;
	protected double mfdMax = 8.95;

	protected double mfdEqualityConstraintWt = 10;
	protected double mfdInequalityConstraintWt = 1000;

	// protected List<MFD_InversionConstraint> constraints = new ArrayList<>();
	protected List<MFD_InversionConstraint> mfdConstraints = new ArrayList<>();
//    private ArrayList<MFD_InversionConstraint> mfdConstraints;

	public List<MFD_InversionConstraint> getMFDConstraints() {
		return mfdConstraints;
	}

	/**
	 * Sets GutenbergRichterMFD arguments
	 * 
	 * @param totalRateM5      the number of M>=5's per year. TODO: ref David
	 *                         Rhodes/Chris Roland? [KKS, CBC]
	 * @param bValue
	 * @param mfdTransitionMag magnitude to switch from MFD equality to MFD
	 *                         inequality TODO: how to validate this number for NZ?
	 *                         (ref Morgan Page in USGS/UCERF3) [KKS, CBC]
	 * @param mfdNum
	 * @param mfdMin
	 * @param mfdMax
	 * @return
	 */
	public NSHM_InversionTargetMFDs setGutenbergRichterMFD(double totalRateM5, double bValue, double mfdTransitionMag,
			int mfdNum, double mfdMin, double mfdMax) {
		this.totalRateM5 = totalRateM5;
		this.bValue = bValue;
		this.mfdTransitionMag = mfdTransitionMag;
		this.mfdNum = mfdNum;
		this.mfdMin = mfdMin;
		this.mfdMax = mfdMax;
		return this;
	}

	/**
	 * @param mfdEqualityConstraintWt
	 * @param mfdInequalityConstraintWt
	 * @return
	 */
	public NSHM_InversionTargetMFDs setGutenbergRichterMFDWeights(double mfdEqualityConstraintWt,
			double mfdInequalityConstraintWt) {
		this.mfdEqualityConstraintWt = mfdEqualityConstraintWt;
		this.mfdInequalityConstraintWt = mfdInequalityConstraintWt;
		return this;
	}

	public NSHM_InversionTargetMFDs(NSHM_InversionFaultSystemRuptSet invRupSet) {
		this.invRupSet = invRupSet;

		// TODO: we're getting a UCERF3 LTB now, this needs to be replaced with NSHM
		// equivalent
		LogicTreeBranch logicTreeBranch = invRupSet.getLogicTreeBranch();
		this.inversionModel = logicTreeBranch.getValue(InversionModels.class);
		// this.totalRegionRateMgt5 =
		// logicTreeBranch.getValue(TotalMag5Rate.class).getRateMag5();

		this.totalRegionRateMgt5 = this.totalRateM5;
		this.mMaxOffFault = logicTreeBranch.getValue(MaxMagOffFault.class).getMaxMagOffFault();
		this.applyImpliedCouplingCoeff = logicTreeBranch.getValue(MomentRateFixes.class).isApplyCC(); // true if
																										// MomentRateFixes
																										// =
																										// APPLY_IMPLIED_CC
																										// or
																										// APPLY_CC_AND_RELAX_MFD
//		this.spatialSeisPDF = logicTreeBranch.getValue(SpatialSeisPDF.class);
		this.spatialSeisPDF = NSHM_SpatialSeisPDF.NZSHM22_1246;

		// convert mMaxOffFault to bin center
		mMaxOffFault -= DELTA_MAG / 2;

		boolean noMoRateFix = (logicTreeBranch.getValue(MomentRateFixes.class) == MomentRateFixes.NONE);

		List<? extends FaultSection> faultSectionData = invRupSet.getFaultSectionDataList();

		GriddedRegion regionNzGridded = new NewZealandRegions.NZ_TEST_GRIDDED();
		GriddedRegion regionSansTVZGridded = new NewZealandRegions.NZ_RECTANGLE_SANS_TVZ_GRIDDED();
		GriddedRegion regionTVZGridded = new NewZealandRegions.NZ_TVZ_GRIDDED();

		gridSeisUtils = new GriddedSeisUtils(faultSectionData, spatialSeisPDF.getPDF(), FAULT_BUFFER, regionNzGridded);
		fractionSeisOnFault = gridSeisUtils.pdfInPolys();
		double fractSeisInSansTVZ = this.spatialSeisPDF.getFractionInRegion(regionSansTVZGridded);

		onFaultRegionRateMgt5 = totalRegionRateMgt5 * fractionSeisOnFault;
		offFaultRegionRateMgt5 = totalRegionRateMgt5 - onFaultRegionRateMgt5;
		origOnFltDefModMoRate = DeformationModelsCalc.calculateTotalMomentRate(faultSectionData, true);
		offFltDefModMoRate = DeformationModelsCalc.calcMoRateOffFaultsForDefModel(invRupSet.getFaultModel(),
				invRupSet.getDeformationModel());

		// make the total target GR MFD
		totalTargetGR = new GutenbergRichterMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);
		roundedMmaxOnFault = totalTargetGR.getX(totalTargetGR.getClosestXIndex(invRupSet.getMaxMag()));
		totalTargetGR.setAllButTotMoRate(MIN_MAG, roundedMmaxOnFault, totalRegionRateMgt5 * 1e5, 1.0);

		GutenbergRichterMagFreqDist totalTargetGR_SansTVZ = new GutenbergRichterMagFreqDist(MIN_MAG, NUM_MAG,
				DELTA_MAG);
		totalTargetGR_SansTVZ.setAllButTotMoRate(MIN_MAG, roundedMmaxOnFault,
				totalRegionRateMgt5 * fractSeisInSansTVZ * 1e5, 1.0);

		GutenbergRichterMagFreqDist totalTargetGR_TVZ = new GutenbergRichterMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);
		totalTargetGR_TVZ.setAllButTotMoRate(MIN_MAG, roundedMmaxOnFault,
				totalRegionRateMgt5 * (1 - fractSeisInSansTVZ) * 1e5, 1.0);

		// get ave min seismo mag for region
		double tempMag = FaultSystemRupSetCalc.getMeanMinMag(invRupSet, true);
		aveMinSeismoMag = totalTargetGR.getX(totalTargetGR.getClosestXIndex(tempMag)); // round to nearest MFD value

		trulyOffFaultMFD = FaultSystemRupSetCalc.getTriLinearCharOffFaultTargetMFD(totalTargetGR, onFaultRegionRateMgt5,
				aveMinSeismoMag, mMaxOffFault);
		subSeismoOnFaultMFD_List = FaultSystemRupSetCalc.getCharSubSeismoOnFaultMFD_forEachSection(invRupSet,
				gridSeisUtils, totalTargetGR);

		totalSubSeismoOnFaultMFD = new SummedMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);
		for (int m = 0; m < subSeismoOnFaultMFD_List.size(); m++) {
			GutenbergRichterMagFreqDist mfd = subSeismoOnFaultMFD_List.get(m);
			if (mfd.getMagUpper() <= 5.05 & D) {
				debugString += "\tWARNING: " + faultSectionData.get(m).getName() + " has a max subSeism mag of "
						+ mfd.getMagUpper() + " so no contribution above M5!\n";
			}
			totalSubSeismoOnFaultMFD.addIncrementalMagFreqDist(mfd);
		}

		targetOnFaultSupraSeisMFD = new SummedMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);
		targetOnFaultSupraSeisMFD.addIncrementalMagFreqDist(totalTargetGR);
		targetOnFaultSupraSeisMFD.subtractIncrementalMagFreqDist(trulyOffFaultMFD);
		targetOnFaultSupraSeisMFD.subtractIncrementalMagFreqDist(totalSubSeismoOnFaultMFD);

		// split the above between Regions
		IncrementalMagFreqDist targetSupraSeisMFD_sansTVZ = new IncrementalMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);
		IncrementalMagFreqDist targetSupraSeisMFD_TVZ = new IncrementalMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);
		for (int i = 0; i < NUM_MAG; i++) {
			targetSupraSeisMFD_sansTVZ.set(i, targetOnFaultSupraSeisMFD.getY(i) * (fractSeisInSansTVZ));
			targetSupraSeisMFD_TVZ.set(i, targetOnFaultSupraSeisMFD.getY(i) * (1 - fractSeisInSansTVZ));
		}

		// compute coupling coefficients
		impliedOnFaultCouplingCoeff = (targetOnFaultSupraSeisMFD.getTotalMomentRate()
				+ totalSubSeismoOnFaultMFD.getTotalMomentRate()) / origOnFltDefModMoRate;
		finalOffFaultCouplingCoeff = trulyOffFaultMFD.getTotalMomentRate() / offFltDefModMoRate;
		impliedTotalCouplingCoeff = totalTargetGR.getTotalMomentRate() / (origOnFltDefModMoRate + offFltDefModMoRate);

		// set the names
		totalTargetGR.setName("InversionTargetMFDs.totalTargetGR");
		totalTargetGR_SansTVZ.setName("InversionTargetMFDs.totalTargetGR_SansTVZ");
		totalTargetGR_TVZ.setName("InversionTargetMFDs.totalTargetGR_TVZ");
		targetOnFaultSupraSeisMFD.setName("InversionTargetMFDs.targetOnFaultSupraSeisMFD");
		trulyOffFaultMFD.setName("InversionTargetMFDs.trulyOffFaultMFD");
		totalSubSeismoOnFaultMFD.setName("InversionTargetMFDs.totalSubSeismoOnFaultMFD");
		targetSupraSeisMFD_sansTVZ.setName("InversionTargetMFDs.targetSupraSeisMFD_sansTVZ");
		targetSupraSeisMFD_TVZ.setName("InversionTargetMFDs.targetSupraSeisMFD_TVZ");

		// Build the MFD Constraints for regions
		mfdConstraints = new ArrayList<MFD_InversionConstraint>();
		mfdConstraints.add(new MFD_InversionConstraint(targetSupraSeisMFD_sansTVZ, regionSansTVZGridded));
		mfdConstraints.add(new MFD_InversionConstraint(targetSupraSeisMFD_TVZ, regionTVZGridded));

	}

//	private void buildConstraints() {

	/*
	 * BELOW is old stuff
	 */

//		Region regionSansTVZ = new NewZealandRegions.NZ_RECTANGLE_SANS_TVZ(); // now the tighter geometry from our gridded seis
//	    Region regionTVZ = new NewZealandRegions.NZ_TVZ();
//	    
//	    //configure GR, this will use defaults unless user calls setGutenbergRichterMFD() to override 	
//	    GutenbergRichterMagFreqDist mfd = new GutenbergRichterMagFreqDist(bValue, totalRateM5, mfdMin, mfdMax, mfdNum);
//	    int transitionIndex = mfd.getClosestXIndex(mfdTransitionMag);
//	
//	    // snap it to the discretization if it wasn't already
//	    mfdTransitionMag = mfd.getX(transitionIndex);
//	    Preconditions.checkState(transitionIndex >= 0);       
//	    
//	    //GR Equality
//	    GutenbergRichterMagFreqDist equalityMFD_SansTVZ = new GutenbergRichterMagFreqDist(
//	            bValue, totalRateM5, mfdMin, mfdTransitionMag, transitionIndex);
//	    
//	    //and a different bvalue for TVZ equality
//	    GutenbergRichterMagFreqDist equalityMFD_TVZ = new GutenbergRichterMagFreqDist(
//	            0.75, totalRateM5, mfdMin, mfdTransitionMag, transitionIndex);   
//	    
//	    MFD_InversionConstraint equalityConstr_SansTVZ = new MFD_InversionConstraint(equalityMFD_SansTVZ, regionSansTVZ);
//	    MFD_InversionConstraint equalityConstr_TVZ = new MFD_InversionConstraint(equalityMFD_TVZ, regionTVZ);
//	    
//	    //GR Inequality
//	    GutenbergRichterMagFreqDist inequalityMFD_SansTVZ = new GutenbergRichterMagFreqDist(
//	            bValue, totalRateM5, mfdTransitionMag, mfdMax, mfd.size() - equalityMFD_SansTVZ.size());
//	
//	    //and a different bvalue for TVZ Inequality
//	    GutenbergRichterMagFreqDist inequalityMFD_TVZ = new GutenbergRichterMagFreqDist(
//	    		otherbValue, totalRateM5, mfdTransitionMag, mfdMax, mfd.size() - equalityMFD_TVZ.size());
//	    
//	    MFD_InversionConstraint inequalityConstr_SansTVZ = new MFD_InversionConstraint(inequalityMFD_SansTVZ, regionSansTVZ);
//	    MFD_InversionConstraint inequalityConstr_TVZ = new MFD_InversionConstraint(inequalityMFD_TVZ, regionTVZ);

//	    //create the constraints
//	    constraints.add(new MFDEqualityInversionConstraint(invRupSet, mfdEqualityConstraintWt,
//	            Lists.newArrayList(equalityConstr_SansTVZ, equalityConstr_TVZ), null));
//	    constraints.add(new MFDInequalityInversionConstraint(invRupSet, mfdInequalityConstraintWt,
//	            Lists.newArrayList(inequalityConstr_SansTVZ, inequalityConstr_TVZ)));		
//	
//	}

	@Override
	public GutenbergRichterMagFreqDist getTotalTargetGR_NoCal() {
		throw new UnsupportedOperationException();
	}

	@Override
	public GutenbergRichterMagFreqDist getTotalTargetGR_SoCal() {
		throw new UnsupportedOperationException();
	}

	@Override
	public List<MFD_InversionConstraint> getMFD_ConstraintsForNoAndSoCal() {
		throw new UnsupportedOperationException();
	}

}

package nz.cri.gns.NZSHM22.opensha.inversion;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.awt.geom.Point2D;

import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.MFDEqualityInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.MFDInequalityInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.modules.PolygonFaultGridAssociations;
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
import scratch.UCERF3.analysis.FaultSystemRupSetCalc;
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
public class NZSHM22_CrustalInversionTargetMFDs extends NZSHM22_InversionTargetMFDs {

	NZSHM22_SpatialSeisPDF spatialSeisPDF;
	NZSHM22_SpatialSeisPDF spatialSeisPDFforOnFaultRates;

	boolean MFD_STATS = true; //print some curves for analytics

	/*
	 * MFD constraint default settings
	 */
	
	protected double mfdTransitionMag = 7.85; // TODO: how to validate this number for NZ? (ref Morgan Page in
												// USGS/UCERF3) [KKS, CBC]
	protected int mfdNum = 40;
	protected double mfdMin = 5.05d;
	protected double mfdMax = 8.95;

	protected double mfdEqualityConstraintWt = 10;
	protected double mfdInequalityConstraintWt = 1000;

	protected List<MFD_InversionConstraint> mfdConstraints = new ArrayList<>();
//	protected GutenbergRichterMagFreqDist totalTargetGR;

	//New fields

	// NZSHM22 bValue and MinMag5 rates by region,
	private double totalRateM5_SansTVZ = 3.6; //TODO: sweep these!
	private double totalRateM5_TVZ = 0.4;
	private double bValue_SansTVZ = 1.05; //1.08
	private double bValue_TVZ = 1.25; //1.4
	
	private double onFaultRegionRateMgt5_SansTVZ;
	private double onFaultRegionRateMgt5_TVZ;
//	private double offFaultRegionRateMgt5_SansTVZ;
//	private double offFaultRegionRateMgt5_TVZ;
	
	private GutenbergRichterMagFreqDist totalTargetGR_SansTVZ;
	private GutenbergRichterMagFreqDist totalTargetGR_TVZ;
	private double aveMinSeismoMag_SansTVZ;
	private double aveMinSeismoMag_TVZ;
	private IncrementalMagFreqDist trulyOffFaultMFD_SansTVZ;
	private IncrementalMagFreqDist trulyOffFaultMFD_TVZ;
	private ArrayList<GutenbergRichterMagFreqDist> subSeismoOnFaultMFD_List_SansTVZ;
	private ArrayList<GutenbergRichterMagFreqDist> subSeismoOnFaultMFD_List_TVZ;
	private SummedMagFreqDist totalSubSeismoOnFaultMFD_SansTVZ;
	private SummedMagFreqDist totalSubSeismoOnFaultMFD_TVZ;
	private SummedMagFreqDist targetOnFaultSupraSeisMFD_SansTVZ;
	private SummedMagFreqDist targetOnFaultSupraSeisMFD_TVZ;	
	
	public final static double NZ_MIN_MAG = 5.05; //used instead of UCERF3 value 0.05
	public final static int NZ_NUM_BINS = 40;  //used instead of UCERF3 value 90

	private List<IncrementalMagFreqDist> mfdConstraintComponents = new ArrayList<IncrementalMagFreqDist>();
	
	public List<IncrementalMagFreqDist> getMFDConstraintComponents() {
    	return mfdConstraintComponents;
    }

	@Override
    public List<MFD_InversionConstraint> getMFD_Constraints() {
    	return mfdConstraints;
    }

	public NZSHM22_CrustalInversionTargetMFDs(NZSHM22_InversionFaultSystemRuptSet invRupSet,
			double totalRateM5_Sans, double totalRateM5_TVZ, double bValue_Sans, double bValue_TVZ, double mfdTransitionMag) {
			this.totalRateM5_SansTVZ = totalRateM5_Sans;
			this.totalRateM5_TVZ = totalRateM5_TVZ;

			this.bValue_SansTVZ = bValue_Sans;
			this.bValue_TVZ = bValue_TVZ;

			this.mfdTransitionMag = mfdTransitionMag;
			init(invRupSet);
		}

	public NZSHM22_CrustalInversionTargetMFDs(NZSHM22_InversionFaultSystemRuptSet invRupSet) {
		init(invRupSet);
	}


    /**
     * @param mfdEqualityConstraintWt
     * @param mfdInequalityConstraintWt
     * @return
     */
    public NZSHM22_CrustalInversionTargetMFDs setGutenbergRichterMFDWeights(double mfdEqualityConstraintWt, 
    		double mfdInequalityConstraintWt) {
    	this.mfdEqualityConstraintWt = mfdEqualityConstraintWt;
    	this.mfdInequalityConstraintWt = mfdInequalityConstraintWt;
    	return this;
    }
    

	protected void init(NZSHM22_InversionFaultSystemRuptSet invRupSet) {

		U3LogicTreeBranch logicTreeBranch = invRupSet.getLogicTreeBranch();
		this.inversionModel = logicTreeBranch.getValue(InversionModels.class);
		this.mMaxOffFault = 8.05d; // NZ-ish
		this.spatialSeisPDF = NZSHM22_SpatialSeisPDF.NZSHM22_1346;

		// convert mMaxOffFault to bin center
		mMaxOffFault -= DELTA_MAG / 2;  // TODO is 8.05 already a bin centre?

		List<? extends FaultSection> faultSectionData = invRupSet.getFaultSectionDataList();

		GriddedRegion regionSansTVZGridded = new NewZealandRegions.NZ_RECTANGLE_SANS_TVZ_GRIDDED();
		GriddedRegion regionTVZGridded = new NewZealandRegions.NZ_TVZ_GRIDDED();

		gridSeisUtils = new GriddedSeisUtils(faultSectionData,
				spatialSeisPDF.getPDF(), invRupSet.requireModule(PolygonFaultGridAssociations.class)); // TODO: OAKLEY: check this is ours already
		fractionSeisOnFault = gridSeisUtils.pdfInPolys(); 		//TODO: split this for TVZ? Matt says yes

		onFaultRegionRateMgt5_SansTVZ = totalRateM5_SansTVZ * fractionSeisOnFault;
		onFaultRegionRateMgt5_TVZ = totalRateM5_TVZ * fractionSeisOnFault;

		// make the total target GR MFD with empty bins
		totalTargetGR_SansTVZ = new GutenbergRichterMagFreqDist(NZ_MIN_MAG, NZ_NUM_BINS, DELTA_MAG);
		totalTargetGR_TVZ = new GutenbergRichterMagFreqDist(NZ_MIN_MAG, NZ_NUM_BINS, DELTA_MAG);
		totalTargetGR = new GutenbergRichterMagFreqDist(NZ_MIN_MAG, NZ_NUM_BINS, DELTA_MAG);

		// populate the MFD bins
		double roundedMmaxOnFault_SansTVZ = totalTargetGR_SansTVZ.getX(totalTargetGR_SansTVZ.getClosestXIndex(invRupSet.getMaxMag()));
		double roundedMmaxOnFault_TVZ = totalTargetGR_TVZ.getX(totalTargetGR_TVZ.getClosestXIndex(invRupSet.getMaxMag()));
		totalTargetGR_SansTVZ.setAllButTotMoRate(NZ_MIN_MAG, roundedMmaxOnFault_SansTVZ, totalRateM5_SansTVZ, bValue_SansTVZ);
		totalTargetGR_TVZ.setAllButTotMoRate(NZ_MIN_MAG, roundedMmaxOnFault_TVZ, totalRateM5_TVZ, bValue_TVZ);

		if (MFD_STATS) {
			System.out.println("totalTargetGR_SansTVZ after setAllButTotMoRate");
			System.out.println(totalTargetGR_SansTVZ.toString());
			System.out.println("");
			System.out.println("totalTargetGR_TVZ after setAllButTotMoRate");
			System.out.println(totalTargetGR_TVZ.toString());
			System.out.println("");
		}

		// get ave min seismo mag for region
		// TODO: this is weighted by moment, so exponentially biased to larger ruptures (WHY?)
		// Kevin weighted by moment (which comes from slip rate) so higher momentrate faults WILL predominate
		// NZ many tiny faults will not really contribute much
		double tempMag = NZSHM22_FaultSystemRupSetCalc.getMeanMinMag(invRupSet, true);

		//TODO: why derive this from the rupt set and not use mMaxOffFault??
		aveMinSeismoMag_SansTVZ = totalTargetGR_SansTVZ.getX(totalTargetGR_SansTVZ.getClosestXIndex(tempMag));	// round to nearest MFD value
		aveMinSeismoMag_TVZ = totalTargetGR_TVZ.getX(totalTargetGR_TVZ.getClosestXIndex(tempMag));	// round to nearest MFD value

		//TODO: why aveMinSeismoMag (Ned??)
		// seems to calculate our corner magnitude for tapered GR
		trulyOffFaultMFD_SansTVZ  = NZSHM22_FaultSystemRupSetCalc.getTriLinearCharOffFaultTargetMFD(totalTargetGR_SansTVZ , onFaultRegionRateMgt5_SansTVZ , aveMinSeismoMag_SansTVZ , mMaxOffFault);
		trulyOffFaultMFD_TVZ  = NZSHM22_FaultSystemRupSetCalc.getTriLinearCharOffFaultTargetMFD(totalTargetGR_TVZ , onFaultRegionRateMgt5_TVZ , aveMinSeismoMag_TVZ , mMaxOffFault);

		subSeismoOnFaultMFD_List_SansTVZ = NZSHM22_FaultSystemRupSetCalc.getCharSubSeismoOnFaultMFD_forEachSection(invRupSet, gridSeisUtils, totalTargetGR_SansTVZ);
		subSeismoOnFaultMFD_List_TVZ = NZSHM22_FaultSystemRupSetCalc.getCharSubSeismoOnFaultMFD_forEachSection(invRupSet, gridSeisUtils, totalTargetGR_TVZ);

		//What are the min magnitude per section?
		if (MFD_STATS) {
			System.out.println("trulyOffFaultMFD_SansTVZ (TriLinearCharOffFaultTargetMFD)");
			System.out.println(trulyOffFaultMFD_SansTVZ.toString());
			System.out.println("");
			System.out.println("trulyOffFaultMFD_TVZ (TriLinearCharOffFaultTargetMFD)");
			System.out.println(trulyOffFaultMFD_TVZ.toString());
			System.out.println("");
		}

		//MATT debug
		if (MFD_STATS && false) {
			//	HistogramFunction hist = NZSHM22_FaultSystemRupSetCalc.getMagHistogram(invRupSet, MIN_MAG, NUM_MAG+10, DELTA_MAG);
			//
			// Build our own histogram
			// using systemWideMinMag of 0.0 here, to get actual values
			double [] sect_mins =  NZSHM22_FaultSystemRupSetCalc.computeMinSeismoMagForSections(invRupSet, 0.0d);
			HistogramFunction hist = new HistogramFunction(0.0d, 90, 0.1d);
			for (int r=0;r<invRupSet.getNumRuptures(); r++) {
				hist.add(invRupSet.getMagForRup(r), 1.0);
			}
			System.out.println("getMagHistogram");
			System.out.println(hist.toString());
			System.out.println("");
		}

		// TODO: use computeMinSeismoMagForSections to find NZ values and explain 7.4
		// histogram to look for min values > 7.X
		totalSubSeismoOnFaultMFD_SansTVZ= new SummedMagFreqDist(NZ_MIN_MAG, NZ_NUM_BINS, DELTA_MAG);
		for (int m = 0; m < subSeismoOnFaultMFD_List_SansTVZ.size(); m++) {
			GutenbergRichterMagFreqDist mfd = subSeismoOnFaultMFD_List_SansTVZ.get(m);
			if (mfd.getMagUpper() <= 5.05 & D) {
				debugString += "\tWARNING: " + faultSectionData.get(m).getName() + " has a max subSeism mag of "
						+ mfd.getMagUpper() + " so no contribution above M5!\n";
			}
			totalSubSeismoOnFaultMFD_SansTVZ.addIncrementalMagFreqDist(mfd);
		}

		totalSubSeismoOnFaultMFD_TVZ= new SummedMagFreqDist(NZ_MIN_MAG, NZ_NUM_BINS, DELTA_MAG);
		for (int m = 0; m < subSeismoOnFaultMFD_List_TVZ.size(); m++) {
			GutenbergRichterMagFreqDist mfd = subSeismoOnFaultMFD_List_TVZ.get(m);
			if (mfd.getMagUpper() <= 5.05 & D) {
				debugString += "\tWARNING: " + faultSectionData.get(m).getName() + " has a max subSeism mag of "
						+ mfd.getMagUpper() + " so no contribution above M5!\n";
			}
			totalSubSeismoOnFaultMFD_TVZ.addIncrementalMagFreqDist(mfd);
		}

		if (MFD_STATS) {
			System.out.println("totalSubSeismoOnFaultMFD_SansTVZ (SummedMagFreqDist)");
			System.out.println(totalSubSeismoOnFaultMFD_SansTVZ.toString());
			System.out.println("");
			System.out.println("totalSubSeismoOnFaultMFD_TVZ (SummedMagFreqDist)");
			System.out.println(totalSubSeismoOnFaultMFD_TVZ.toString());
			System.out.println("");
		}


		targetOnFaultSupraSeisMFD_SansTVZ = new SummedMagFreqDist(NZ_MIN_MAG, NZ_NUM_BINS, DELTA_MAG);
		targetOnFaultSupraSeisMFD_SansTVZ.addIncrementalMagFreqDist(totalTargetGR_SansTVZ);
		targetOnFaultSupraSeisMFD_SansTVZ.subtractIncrementalMagFreqDist(trulyOffFaultMFD_SansTVZ);
		targetOnFaultSupraSeisMFD_SansTVZ.subtractIncrementalMagFreqDist(totalSubSeismoOnFaultMFD_SansTVZ);

		targetOnFaultSupraSeisMFD_TVZ = new SummedMagFreqDist(NZ_MIN_MAG, NZ_NUM_BINS, DELTA_MAG);
		targetOnFaultSupraSeisMFD_TVZ.addIncrementalMagFreqDist(totalTargetGR_TVZ);
		targetOnFaultSupraSeisMFD_TVZ.subtractIncrementalMagFreqDist(trulyOffFaultMFD_TVZ);
		targetOnFaultSupraSeisMFD_TVZ.subtractIncrementalMagFreqDist(totalSubSeismoOnFaultMFD_TVZ);

//		//Doctor the target, setting a small value instead of 0
//	 	for (int i = 0; i < 20; i++) {
////			totalTargetGR.set(i, 1.0e-20);
////			totalTargetGR_SansTVZ.set(i, 1.0e-20);
////			totalTargetGR_TVZ.set(i, 1.0e-20);
//	 		targetOnFaultSupraSeisMFD_SansTVZ.set(i, 1.0e-20);
//	 		targetOnFaultSupraSeisMFD_TVZ.set(i, 1.0e-20);
//	 	}

//		// plan B
//	 	IncrementalMagFreqDist set_sub_mags = new IncrementalMagFreqDist(NZ_MIN_MAG, NZ_NUM_BINS, DELTA_MAG);
//	 	for (int i = 0; i < 20; i++) {
//	 		set_sub_mags.set(i, 1.0e-20);
//	 	}
//	 	targetOnFaultSupraSeisMFD_TVZ.addIncrementalMagFreqDist(set_sub_mags);

	 	//Plan C
	 	IncrementalMagFreqDist mutableMFD_SansTVZ = new IncrementalMagFreqDist(NZ_MIN_MAG, NZ_NUM_BINS, DELTA_MAG);
	 	for (int i = 0; i < targetOnFaultSupraSeisMFD_SansTVZ.size(); i++) {
	 		mutableMFD_SansTVZ.set(i, targetOnFaultSupraSeisMFD_SansTVZ.get(i).getY());
	 		if (i < 20)
	 			mutableMFD_SansTVZ.set(i, 1.0e-20);
	 	}

	 	IncrementalMagFreqDist mutableMFD_TVZ = new IncrementalMagFreqDist(NZ_MIN_MAG, NZ_NUM_BINS, DELTA_MAG);
	 	for (int i = 0; i < targetOnFaultSupraSeisMFD_TVZ.size(); i++) {
	 		mutableMFD_TVZ.set(i, targetOnFaultSupraSeisMFD_TVZ.get(i).getY());
	 		if (i < 20)
	 			mutableMFD_TVZ.set(i, 1.0e-20);
	 	}

		if (MFD_STATS) {
			System.out.println("targetOnFaultSupraSeisMFD_SansTVZ(SummedMagFreqDist)");
			System.out.println(mutableMFD_SansTVZ.toString());
			System.out.println("");
			System.out.println("targetOnFaultSupraSeisMFD_TVZ (SummedMagFreqDist)");
			System.out.println(mutableMFD_TVZ.toString());
			System.out.println("");
		}
		//TODO are these purely analysis?? for now they're off
//		// compute coupling coefficients
//		impliedOnFaultCouplingCoeff = (targetOnFaultSupraSeisMFD.getTotalMomentRate()
//				+ totalSubSeismoOnFaultMFD.getTotalMomentRate()) / origOnFltDefModMoRate;
//		finalOffFaultCouplingCoeff = trulyOffFaultMFD.getTotalMomentRate() / offFltDefModMoRate;
//		impliedTotalCouplingCoeff = totalTargetGR.getTotalMomentRate() / (origOnFltDefModMoRate + offFltDefModMoRate);

		// set the names
		totalTargetGR_SansTVZ.setName("InversionTargetMFDs.totalTargetGR_SansTVZ");
		totalTargetGR_TVZ.setName("InversionTargetMFDs.totalTargetGR_TVZ");
		
		mutableMFD_SansTVZ.setName("InversionTargetMFDs.targetOnFaultSupraSeisMFD_SansTVZ");
		mutableMFD_TVZ.setName("InversionTargetMFDs.targetOnFaultSupraSeisMFD_TVZ");
		
		trulyOffFaultMFD_SansTVZ.setName("InversionTargetMFDs.trulyOffFaultMFD_SansTVZ.");
		trulyOffFaultMFD_TVZ.setName("InversionTargetMFDs.trulyOffFaultMFD_TVZ.");

		totalSubSeismoOnFaultMFD_SansTVZ.setName("InversionTargetMFDs.totalSubSeismoOnFaultMFD_SansTVZ.");
		totalSubSeismoOnFaultMFD_TVZ.setName("InversionTargetMFDs.totalSubSeismoOnFaultMFD_TVZ.");

		// Build the MFD Constraints for regions
		mfdConstraints = new ArrayList<MFD_InversionConstraint>();

		mfdConstraints.add(new MFD_InversionConstraint(mutableMFD_SansTVZ, regionSansTVZGridded));
		mfdConstraints.add(new MFD_InversionConstraint(mutableMFD_TVZ, regionTVZGridded));
		
		/*
		 * TODO CBC the following block sets up base class var required later to save the solution,
		 * namely:
		 *  - totalTargetGR
		 *  - trulyOffFaultMFD
		 *  - totalSubSeismoOnFaultMFD
		 */
		SummedMagFreqDist tempTargetGR =  new SummedMagFreqDist(NZ_MIN_MAG, NZ_NUM_BINS, DELTA_MAG);
		tempTargetGR.addIncrementalMagFreqDist(totalTargetGR_SansTVZ);
		tempTargetGR.addIncrementalMagFreqDist(totalTargetGR_TVZ);

		Iterator<Point2D> it2 = tempTargetGR.getPointsIterator();
		while (it2.hasNext()) {
			Point2D point = (Point2D)it2.next();
			totalTargetGR.set(point);
		}
		SummedMagFreqDist tempTrulyOffFaultMFD =  new SummedMagFreqDist(NZ_MIN_MAG, NZ_NUM_BINS, DELTA_MAG);
		trulyOffFaultMFD = new IncrementalMagFreqDist(NZ_MIN_MAG, NZ_NUM_BINS, DELTA_MAG);
		tempTrulyOffFaultMFD.addIncrementalMagFreqDist(trulyOffFaultMFD_SansTVZ);
		tempTrulyOffFaultMFD.addIncrementalMagFreqDist(trulyOffFaultMFD_TVZ);

		Iterator<Point2D> it3 = tempTrulyOffFaultMFD.getPointsIterator();
		while (it3.hasNext()) {
			Point2D point = (Point2D)it3.next();
			trulyOffFaultMFD.set(point);
		}


		//TODO: review this (if really needed) should add the SansTVZ and TVZ
		List<GutenbergRichterMagFreqDist> subSeismoOnFaultMFD_List = NZSHM22_FaultSystemRupSetCalc.getCharSubSeismoOnFaultMFD_forEachSection(invRupSet, gridSeisUtils, totalTargetGR);
		totalSubSeismoOnFaultMFD = new SummedMagFreqDist(NZ_MIN_MAG, NZ_NUM_BINS, DELTA_MAG);
		for(int m=0; m<subSeismoOnFaultMFD_List.size(); m++) {
			GutenbergRichterMagFreqDist mfd = subSeismoOnFaultMFD_List.get(m);
			if(mfd.getMagUpper() <= 5.05 & D) {
				debugString += "\tWARNING: "+faultSectionData.get(m).getName()+" has a max subSeism mag of "+mfd.getMagUpper()+" so no contribution above M5!\n";
			}
			totalSubSeismoOnFaultMFD.addIncrementalMagFreqDist(mfd);
		}

		// TODO is this correct? It's just a guess by Oakley
		subSeismoOnFaultMFDs = new SubSeismoOnFaultMFDs(subSeismoOnFaultMFD_List);

		setParent(invRupSet);

//		totalTargetGR.setName("totalTargetGR");
//		trulyOffFaultMFD_SansTVZ.setName("trulyOffFaultMFD_SansTVZ");
//		trulyOffFaultMFD_TVZ.setName("trulyOffFaultMFD_TVZ");
		trulyOffFaultMFD.setName("trulyOffFaultMFD.all");	
		mutableMFD_SansTVZ.setName("targetOnFaultSupraSeisMFD_SansTVZ");
		mutableMFD_TVZ.setName("targetOnFaultSupraSeisMFD_TVZ");
		totalSubSeismoOnFaultMFD.setName("totalSubSeismoOnFaultMFD");

		// Now collect the target MFDS we might want for plots
//		mfdConstraintComponents.add(totalTargetGR);
//		mfdConstraintComponents.add(trulyOffFaultMFD_SansTVZ);
//		mfdConstraintComponents.add(trulyOffFaultMFD_TVZ);
		mfdConstraintComponents.add(trulyOffFaultMFD);
		mfdConstraintComponents.add(mutableMFD_SansTVZ);
		mfdConstraintComponents.add(mutableMFD_TVZ);
		mfdConstraintComponents.add(totalSubSeismoOnFaultMFD);

		if (MFD_STATS) {

			System.out.println("totalTargetGR");
			System.out.println(totalTargetGR.toString());
			System.out.println("");

			System.out.println("trulyOffFaultMFD (TriLinearCharOffFaultTargetMFD)");
			System.out.println(trulyOffFaultMFD.toString());
			System.out.println("");

			System.out.println("totalSubSeismoOnFaultMFD");
			System.out.println(totalSubSeismoOnFaultMFD.toString());
			System.out.println("");

		}
		/*
		 * END CODE BLOCK
		 */

	}
	
	@Override
	public GutenbergRichterMagFreqDist getTotalTargetGR_NoCal() {
		throw new UnsupportedOperationException();
	}

	@Override
	public GutenbergRichterMagFreqDist getTotalTargetGR_SoCal() {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getName() {
		return "NZSHM22 Crustal Inversion Target MFDs";
	}

	@Override
	public double[] getPDF(){
		return spatialSeisPDF.getPDF();
	}

}

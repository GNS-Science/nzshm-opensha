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
 * TODO: this contains mostly UCERF3 stuff that will be replaced for NSHM
 *  
 * @author chrisbc
 *
 */
public class NSHM_InversionTargetMFDs extends InversionTargetMFDs {
	
//	// debugging flag
//	final static boolean D = false;
//	final static boolean GR_OFF_FAULT_IS_TAPERED = true;
//	String debugString;
//	
//	SlipEnabledRupSet invRupSet;
//	double totalRegionRateMgt5, onFaultRegionRateMgt5, offFaultRegionRateMgt5;
//	double mMaxOffFault;
//	boolean applyImpliedCouplingCoeff;
	NSHM_SpatialSeisPDF spatialSeisPDF;
	NSHM_SpatialSeisPDF spatialSeisPDFforOnFaultRates;
//	InversionModels inversionModel;
//	GriddedSeisUtils gridSeisUtils;
//	
//	double origOnFltDefModMoRate, offFltDefModMoRate, aveMinSeismoMag, roundedMmaxOnFault;
//	double fractSeisInSoCal;
//	double fractionSeisOnFault;
//	double impliedOnFaultCouplingCoeff;
//	double impliedTotalCouplingCoeff;
//	double finalOffFaultCouplingCoeff;
//	GutenbergRichterMagFreqDist totalTargetGR, totalTargetGR_NoCal, totalTargetGR_SoCal;
//	SummedMagFreqDist targetOnFaultSupraSeisMFD;
//	IncrementalMagFreqDist trulyOffFaultMFD;
//	ArrayList<GutenbergRichterMagFreqDist> subSeismoOnFaultMFD_List;
//	SummedMagFreqDist totalSubSeismoOnFaultMFD;		// this is a sum of the MFDs in subSeismoOnFaultMFD_List
//	IncrementalMagFreqDist noCalTargetSupraMFD, soCalTargetSupraMFD;

//	
//	List<MFD_InversionConstraint> mfdConstraintsForNoAndSoCal;
//
//	// discretization parameters for MFDs
//	public final static double MIN_MAG = 0.05;
//	public final static double MAX_MAG = 8.95;
//	public final static int NUM_MAG = 90;
//	public final static double DELTA_MAG = 0.1;
//	
//	public final static double FAULT_BUFFER = 12d;	// buffer for fault polygons

    /*
     * MFD constraint default settings
     */
    protected double totalRateM5 = 5d;
    protected double bValue = 1d;
    protected double mfdTransitionMag = 7.85; // TODO: how to validate this number for NZ? (ref Morgan Page in USGS/UCERF3) [KKS, CBC]
    protected int mfdNum = 40;
    protected double mfdMin = 5.05d;
    protected double mfdMax = 8.95;
    
    protected double mfdEqualityConstraintWt = 10;
    protected double mfdInequalityConstraintWt = 1000;
    
    protected List<InversionConstraint> constraints = new ArrayList<>();
	
    public List<InversionConstraint> getMFDConstraints() {
    	return constraints;
    }
    
    /**
     * Sets GutenbergRichterMFD arguments
     * @param totalRateM5 the number of  M>=5's per year. TODO: ref David Rhodes/Chris Roland? [KKS, CBC]
     * @param bValue
     * @param mfdTransitionMag magnitude to switch from MFD equality to MFD inequality TODO: how to validate this number for NZ? (ref Morgan Page in USGS/UCERF3) [KKS, CBC]
     * @param mfdNum
     * @param mfdMin
     * @param mfdMax
     * @return
     */
    public NSHM_InversionTargetMFDs setGutenbergRichterMFD(double totalRateM5, double bValue, 
    		double mfdTransitionMag, int mfdNum, double mfdMin, double mfdMax ) {
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
		this.invRupSet=invRupSet;
		  		
		//TODO: we're getting a UCERF3 LTB now, this needs to be replaced with NSHM equivalent
		LogicTreeBranch logicTreeBranch = invRupSet.getLogicTreeBranch();
		this.inversionModel = logicTreeBranch.getValue(InversionModels.class);
		this.totalRegionRateMgt5 = logicTreeBranch.getValue(TotalMag5Rate.class).getRateMag5();
		this.mMaxOffFault = logicTreeBranch.getValue(MaxMagOffFault.class).getMaxMagOffFault();
		this.applyImpliedCouplingCoeff = logicTreeBranch.getValue(MomentRateFixes.class).isApplyCC();	// true if MomentRateFixes = APPLY_IMPLIED_CC or APPLY_CC_AND_RELAX_MFD
//		this.spatialSeisPDF = logicTreeBranch.getValue(SpatialSeisPDF.class);
		this.spatialSeisPDF = NSHM_SpatialSeisPDF.NZSHM22_1246;
		
		// convert mMaxOffFault to bin center
		mMaxOffFault -= DELTA_MAG/2;
		
		boolean noMoRateFix = (logicTreeBranch.getValue(MomentRateFixes.class) == MomentRateFixes.NONE);
		
		List<? extends FaultSection> faultSectionData =  invRupSet.getFaultSectionDataList();
		
		/*
		 * NEW !! Gridded Seismicity
		 */
		NewZealandRegions.NZ_TEST_GRIDDED regionNzGridded = new NewZealandRegions.NZ_TEST_GRIDDED();

		gridSeisUtils = new GriddedSeisUtils(faultSectionData, 
				spatialSeisPDF.getPDF(), FAULT_BUFFER, regionNzGridded);
		
		fractionSeisOnFault = gridSeisUtils.pdfInPolys();

		onFaultRegionRateMgt5 = totalRegionRateMgt5*fractionSeisOnFault;
		offFaultRegionRateMgt5 = totalRegionRateMgt5-onFaultRegionRateMgt5;
		origOnFltDefModMoRate = DeformationModelsCalc.calculateTotalMomentRate(faultSectionData,true);
		offFltDefModMoRate = DeformationModelsCalc.calcMoRateOffFaultsForDefModel(invRupSet.getFaultModel(), invRupSet.getDeformationModel());

		
		// make the total target GR for region TODO: - UCERF3 for what??
		//		totalTargetGR = new GutenbergRichterMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);
		//		roundedMmaxOnFault = totalTargetGR.getX(totalTargetGR.getClosestXIndex(invRupSet.getMaxMag()));
		//		totalTargetGR.setAllButTotMoRate(MIN_MAG, roundedMmaxOnFault, totalRegionRateMgt5*1e5, 1.0);
		//		
		//		

		/*
		 * 
		 */
        
		Region regionSansTVZ = new NewZealandRegions.NZ_RECTANGLE_SANS_TVZ(); // now the tighter geometry from our gridded seis
        Region regionTVZ = new NewZealandRegions.NZ_TVZ();
        
        //configure GR, this will use defaults unless user calls setGutenbergRichterMFD() to override 	
        GutenbergRichterMagFreqDist mfd = new GutenbergRichterMagFreqDist(bValue, totalRateM5, mfdMin, mfdMax, mfdNum);
        int transitionIndex = mfd.getClosestXIndex(mfdTransitionMag);

        // snap it to the discretization if it wasn't already
        mfdTransitionMag = mfd.getX(transitionIndex);
        Preconditions.checkState(transitionIndex >= 0);       
        
        //GR Equality
        GutenbergRichterMagFreqDist equalityMFD_SansTVZ = new GutenbergRichterMagFreqDist(
                bValue, totalRateM5, mfdMin, mfdTransitionMag, transitionIndex);
        
        //and a different bvalue for TVZ equality
        GutenbergRichterMagFreqDist equalityMFD_TVZ = new GutenbergRichterMagFreqDist(
                0.75, totalRateM5, mfdMin, mfdTransitionMag, transitionIndex);   
        
        MFD_InversionConstraint equalityConstr_SansTVZ = new MFD_InversionConstraint(equalityMFD_SansTVZ, regionSansTVZ);
        MFD_InversionConstraint equalityConstr_TVZ = new MFD_InversionConstraint(equalityMFD_TVZ, regionTVZ);
        
        //GR Inequality
        GutenbergRichterMagFreqDist inequalityMFD_SansTVZ = new GutenbergRichterMagFreqDist(
                bValue, totalRateM5, mfdTransitionMag, mfdMax, mfd.size() - equalityMFD_SansTVZ.size());

        //and a different bvalue for TVZ Inequality
        GutenbergRichterMagFreqDist inequalityMFD_TVZ = new GutenbergRichterMagFreqDist(
        		0.75, totalRateM5, mfdTransitionMag, mfdMax, mfd.size() - equalityMFD_TVZ.size());
        
        MFD_InversionConstraint inequalityConstr_SansTVZ = new MFD_InversionConstraint(inequalityMFD_SansTVZ, regionSansTVZ);
        MFD_InversionConstraint inequalityConstr_TVZ = new MFD_InversionConstraint(inequalityMFD_TVZ, regionTVZ);

        //create the constraints
        constraints.add(new MFDEqualityInversionConstraint(invRupSet, mfdEqualityConstraintWt,
                Lists.newArrayList(equalityConstr_SansTVZ, equalityConstr_TVZ), null));
        constraints.add(new MFDInequalityInversionConstraint(invRupSet, mfdInequalityConstraintWt,
                Lists.newArrayList(inequalityConstr_SansTVZ, inequalityConstr_TVZ)));		
		
		/**
		 * END of port
		 * 
		 */
		
//		totalTargetGR_NoCal = new GutenbergRichterMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);	
//		totalTargetGR_NoCal.setAllButTotMoRate(MIN_MAG, roundedMmaxOnFault, totalRegionRateMgt5*(1-fractSeisInSoCal)*1e5, 1.0);
//			
//		totalTargetGR_SoCal = new GutenbergRichterMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);	
//		totalTargetGR_SoCal.setAllButTotMoRate(MIN_MAG, roundedMmaxOnFault, totalRegionRateMgt5*fractSeisInSoCal*1e5, 1.0);
		

		if(D) {
			// get ave min seismo mag for region
			double tempMag = FaultSystemRupSetCalc.getMeanMinMag(invRupSet, true);
			
			// This is a test of applying the minimum rather than average among section min mags in the tri-linear target
			// (increases on fault target by up to 11% (at mean mag) by doing this for case tested; not a big diff, and will make implied off-fault CC worse)
//			double tempMag = 100;
//			for(int s=0;s<invRupSet.getNumSections();s++) {
//				double minMag = invRupSet.getFinalMinMagForSection(s);
//				if(minMag<tempMag) tempMag = minMag;
//				if(minMag<6.301)
//					System.out.println("\t"+(float)minMag+"\t"+invRupSet.getFaultSectionData(s).getParentSectionName());
//			}
//			System.out.println("\ntempMag="+tempMag+"\n");
			
			aveMinSeismoMag = totalTargetGR.getX(totalTargetGR.getClosestXIndex(tempMag));	// round to nearest MFD value

			
			debugString = "\ttotalRegionRateMgt5 =\t"+totalRegionRateMgt5+"\n"+
					"\tmMaxOffFault =\t"+mMaxOffFault+"\n"+
					"\tapplyImpliedCouplingCoeff =\t"+applyImpliedCouplingCoeff+"\n"+
					"\tspatialSeisPDF =\t"+spatialSeisPDF+"\n"+
					"\tspatialSeisPDFforOnFaultRates =\t"+spatialSeisPDFforOnFaultRates+"\n"+
					"\tinversionModel =\t"+inversionModel+"\n"+
//					"\tfractSeisInSoCal =\t"+(float)fractSeisInSoCal+"\n"+
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

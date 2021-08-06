package nz.cri.gns.NZSHM22.opensha.inversion;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.opensha.commons.data.function.HistogramFunction;
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
public class NZSHM22_CrustalInversionTargetMFDs extends U3InversionTargetMFDs {

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
	private double totalRateM5_SansTVZ = 3.6;
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
	
    public List<MFD_InversionConstraint> getMFDConstraints() {
    	return mfdConstraints;
    }
    
//    /**
//     * Sets GutenbergRichterMFD arguments
//     * @param totalRateM5 the number of  M>=5's per year. TODO: ref David Rhodes/Chris Roland? [KKS, CBC]
//     * @param bValue
//     * @param mfdTransitionMag magnitude to switch from MFD equality to MFD inequality TODO: how to validate this number for NZ? (ref Morgan Page in USGS/UCERF3) [KKS, CBC]
//     * @param mfdNum
//     * @param mfdMin
//     * @param mfdMax
//     * @return
//     */
//    public NZSHM22_InversionTargetMFDs setGutenbergRichterMFD(double totalRateM5, double bValue, 
//    		double mfdTransitionMag, int mfdNum, double mfdMin, double mfdMax ) {
////        this.totalRateM5 = totalRateM5; 
////        this.bValue = bValue;
//        this.mfdTransitionMag = mfdTransitionMag;      
//        this.mfdNum = mfdNum;
//        this.mfdMin = mfdMin;
//        this.mfdMax = mfdMax;
//        return this;
//    }    

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
    
    @SuppressWarnings("unused")
	public NZSHM22_CrustalInversionTargetMFDs(NZSHM22_InversionFaultSystemRuptSet invRupSet) {
    	// FIXME
    	throw new RuntimeException("not yet refactored");
	}

	
	@Override
	public GutenbergRichterMagFreqDist getTotalTargetGR_NoCal() {
		throw new UnsupportedOperationException();
	}

	@Override
	public GutenbergRichterMagFreqDist getTotalTargetGR_SoCal() {
		throw new UnsupportedOperationException();
	}

	public double[] getPDF(){
		return spatialSeisPDF.getPDF();
	}

}

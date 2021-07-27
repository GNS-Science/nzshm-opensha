package nz.cri.gns.NZSHM22.opensha.inversion;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.dom4j.Element;
import org.opensha.commons.metadata.XMLSaveable;
import org.opensha.commons.util.XMLUtils;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;

import com.google.common.collect.Lists;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.enumTreeBranches.InversionModels;
import scratch.UCERF3.inversion.InversionTargetMFDs;
import scratch.UCERF3.inversion.UCERF3InversionConfiguration;
import scratch.UCERF3.inversion.UCERF3InversionConfiguration.SlipRateConstraintWeightingType;
import scratch.UCERF3.utils.MFD_InversionConstraint;

/**
 * This represents all of the inversion configuration parameters specific to an
 * individual model on the NZSHM22 logic tree. Parameters can be fetched for a
 * given logic tree branch with the <code>forModel(...)</code> method.
 * 
 * based on scratch.UCERF3.inversion.UCERF3InversionConfiguration
 * 
 * @author chrisbc
 *
 */
public class NZSHM22_CrustalInversionConfiguration extends AbstractInversionConfiguration {



//	private double smoothnessWt; // rupture rate smoothness (entropy)
//	private double eventRateSmoothnessWt; // parent section event-rate smoothing
	protected final static boolean D = true; // for debugging

//	// If true, a Priori rup-rate constraint is applied to zero rates (eg, rups not in UCERF2)
//	private boolean aPrioriConstraintForZeroRates = true;
//	// Amount to multiply standard a-priori rup rate weight by when applying to zero rates (minimization constraint for rups not in UCERF2)
//	private double aPrioriConstraintForZeroRatesWtFactor = 0.1;
//	// If true, rates of Parkfield M~6 ruptures do not count toward MFD Equality Constraint misfit
//	private boolean excludeParkfieldRupsFromMfdEqualityConstraints = true;

	private String metadata;

	/**
	 * 
	 */
	public NZSHM22_CrustalInversionConfiguration() {
	}

//	NZSHM22_InversionConfiguration(
//			double slipRateConstraintWt_normalized,
//			double slipRateConstraintWt_unnormalized,
//			SlipRateConstraintWeightingType slipRateWeighting,
////			double paleoRateConstraintWt,
////			double paleoSlipConstraintWt,
//			double magnitudeEqualityConstraintWt,
//			double magnitudeInequalityConstraintWt,
//			double rupRateConstraintWt, 
////			double participationSmoothnessConstraintWt,
////			double participationConstraintMagBinSize,
////			double nucleationMFDConstraintWt,
//			double mfdSmoothnessConstraintWt,
//			double mfdSmoothnessConstraintWtForPaleoParents,
////			double rupRateSmoothingConstraintWt,
////			double minimizationConstraintWt,
////			double momentConstraintWt,
////			double parkfieldConstraintWt,
////			double[] aPrioriRupConstraint,
////			double[] initialRupModel,
////			double[] minimumRuptureRateBasis, 
////			double smoothnessWt,
////			double eventRateSmoothnessWt,
//			double MFDTransitionMag,
//			List<MFD_InversionConstraint> mfdEqualityConstraints,
//			List<MFD_InversionConstraint> mfdInequalityConstraints,
////			double minimumRuptureRateFraction,
//			String metadata) {
//		if (metadata == null || metadata.isEmpty())
//			metadata = "";
//		else
//			metadata += "\n";
//		this.slipRateConstraintWt_normalized = slipRateConstraintWt_normalized;
//		metadata += "slipRateConstraintWt_normalized: "+slipRateConstraintWt_normalized;
//		this.slipRateConstraintWt_unnormalized = slipRateConstraintWt_unnormalized;
//		metadata += "\nslipRateConstraintWt_unnormalized: "+slipRateConstraintWt_unnormalized;
//		this.slipRateWeighting = slipRateWeighting;
//		metadata += "\nslipRateWeighting: "+slipRateWeighting.name();
////		this.paleoRateConstraintWt = paleoRateConstraintWt;
////		metadata += "\npaleoRateConstraintWt: "+paleoRateConstraintWt;
////		this.paleoSlipConstraintWt = paleoSlipConstraintWt;
////		metadata += "\npaleoSlipConstraintWt: "+paleoSlipConstraintWt;
//		this.magnitudeEqualityConstraintWt = magnitudeEqualityConstraintWt;
//		metadata += "\nmagnitudeEqualityConstraintWt: "+magnitudeEqualityConstraintWt;
//		this.magnitudeInequalityConstraintWt = magnitudeInequalityConstraintWt;
//		metadata += "\nmagnitudeInequalityConstraintWt: "+magnitudeInequalityConstraintWt;
////		this.rupRateConstraintWt = rupRateConstraintWt;
//		metadata += "\nrupRateConstraintWt: "+rupRateConstraintWt;
////		this.participationSmoothnessConstraintWt = participationSmoothnessConstraintWt;
////		metadata += "\nparticipationSmoothnessConstraintWt: "+participationSmoothnessConstraintWt;
////		this.participationConstraintMagBinSize = participationConstraintMagBinSize;
////		metadata += "\nparticipationConstraintMagBinSize: "+participationConstraintMagBinSize;
////		this.nucleationMFDConstraintWt = nucleationMFDConstraintWt;
////		metadata += "\nnucleationMFDConstraintWt: "+nucleationMFDConstraintWt;
////		this.mfdSmoothnessConstraintWt = mfdSmoothnessConstraintWt;
//		metadata += "\nmfdSmoothnessConstraintWt: "+mfdSmoothnessConstraintWt;
////		this.mfdSmoothnessConstraintWtForPaleoParents = mfdSmoothnessConstraintWtForPaleoParents;
//		metadata += "\nmfdSmoothnessConstraintWtForPaleoParents: "+mfdSmoothnessConstraintWtForPaleoParents;
////		this.rupRateSmoothingConstraintWt = rupRateSmoothingConstraintWt;
////		metadata += "\nrupRateSmoothingConstraintWt: "+rupRateSmoothingConstraintWt;
////		this.minimizationConstraintWt = minimizationConstraintWt;
////		metadata += "\nminimizationConstraintWt: "+minimizationConstraintWt;
////		this.momentConstraintWt = momentConstraintWt;
////		metadata += "\nmomentConstraintWt: "+momentConstraintWt;
////		this.parkfieldConstraintWt = parkfieldConstraintWt;
////		metadata += "\nparkfieldConstraintWt: "+parkfieldConstraintWt;
////		this.aPrioriRupConstraint = aPrioriRupConstraint;
////		this.initialRupModel = initialRupModel;
////		this.minimumRuptureRateBasis = minimumRuptureRateBasis;
////		this.smoothnessWt = smoothnessWt;
////		metadata += "\nsmoothnessWt: "+smoothnessWt;
////		this.eventRateSmoothnessWt = eventRateSmoothnessWt;
////		metadata += "\neventRateSmoothnessWt: "+eventRateSmoothnessWt;
//		this.mfdEqualityConstraints = mfdEqualityConstraints;
//		this.mfdInequalityConstraints = mfdInequalityConstraints;
//		this.MFDTransitionMag = MFDTransitionMag;
////		this.minimumRuptureRateFraction = minimumRuptureRateFraction;
////		metadata += "\nminimumRuptureRateFraction: "+minimumRuptureRateFraction;
////		
//		this.metadata = metadata;
//	}

	public static final double DEFAULT_MFD_EQUALITY_WT = 10;
	public static final double DEFAULT_MFD_INEQUALITY_WT = 1000;

	/**
	 * This generates an inversion configuration for the given inversion model and
	 * rupture set
	 * 
	 * @param model
	 * @param rupSet
	 * @return
	 */
	public static NZSHM22_CrustalInversionConfiguration forModel(InversionModels model, NZSHM22_InversionFaultSystemRuptSet rupSet) {
		double mfdEqualityConstraintWt = DEFAULT_MFD_EQUALITY_WT;
		double mfdInequalityConstraintWt = DEFAULT_MFD_INEQUALITY_WT;

		return forModel(model, rupSet, mfdEqualityConstraintWt, mfdInequalityConstraintWt);
	}

	/**
	 * This generates an inversion configuration for the given inversion model and
	 * rupture set
	 * 
	 * @param model
	 * @param rupSet
	 * @param mfdEqualityConstraintWt   weight of magnitude-distribution EQUALITY
	 *                                  constraint relative to slip-rate constraint
	 *                                  (recommended: 10)
	 * @param mfdInequalityConstraintWt weight of magnitude-distribution INEQUALITY
	 *                                  constraint relative to slip-rate constraint
	 *                                  (recommended: 1000)
	 * @return
	 */
	public static NZSHM22_CrustalInversionConfiguration forModel(InversionModels model, NZSHM22_InversionFaultSystemRuptSet rupSet,
			double mfdEqualityConstraintWt, double mfdInequalityConstraintWt) {
		double totalRateM5 = 5; 
		double bValue = 1;
		double mfdTransitionMag = 7.75;
		return forModel(model, rupSet, mfdEqualityConstraintWt, mfdInequalityConstraintWt, totalRateM5, totalRateM5, bValue, bValue, mfdTransitionMag);		
	}

	/**
	 * This generates an inversion configuration for the given inversion model and
	 * rupture set
	 * 
	 * @param model
	 * @param rupSet
	 * @param mfdEqualityConstraintWt   weight of magnitude-distribution EQUALITY
	 *                                  constraint relative to slip-rate constraint
	 *                                  (recommended: 10)
	 * @param mfdInequalityConstraintWt weight of magnitude-distribution INEQUALITY
	 *                                  constraint relative to slip-rate constraint
	 *                                  (recommended: 1000)
	 * @param totalRateM5
	 * @param bValue
	 * @param mfdTransitionMag
	 * @return
	 */
	public static NZSHM22_CrustalInversionConfiguration forModel(InversionModels model,
				NZSHM22_InversionFaultSystemRuptSet rupSet, double mfdEqualityConstraintWt, double mfdInequalityConstraintWt,
				double totalRateM5_Sans, double totalRateM5_TVZ, 
				double bValue_Sans, double bValue_TVZ, double mfdTransitionMag) {
		/*
		 * ******************************************* COMMON TO ALL MODELS
		 * *******************************************
		 */
		// Setting slip-rate constraint weights to 0 does not disable them! To disable
		// one or the other (both cannot be), use slipConstraintRateWeightingType Below
		double slipRateConstraintWt_normalized = 1; // For SlipRateConstraintWeightingType.NORMALIZED (also used for
													// SlipRateConstraintWeightingType.BOTH) -- NOT USED if
													// UNNORMALIZED!
		double slipRateConstraintWt_unnormalized = 100; // For SlipRateConstraintWeightingType.UNNORMALIZED (also used
														// for SlipRateConstraintWeightingType.BOTH) -- NOT USED if
														// NORMALIZED!
		// If normalized, slip rate misfit is % difference for each section (recommended
		// since it helps fit slow-moving faults). If unnormalized, misfit is absolute
		// difference.
		// BOTH includes both normalized and unnormalized constraints.
		SlipRateConstraintWeightingType slipRateWeighting = SlipRateConstraintWeightingType.BOTH; // (recommended: BOTH)

		// weight of paleo-rate constraint relative to slip-rate constraint
		// (recommended: 1.2)
//		double paleoRateConstraintWt = 1.2;

		// weight of mean paleo slip constraint relative to slip-rate constraint
//		double paleoSlipConstraintWt = paleoRateConstraintWt*0.1;

		// weight of magnitude-distribution EQUALITY constraint relative to slip-rate
		// constraint (recommended: 10)
//		double mfdEqualityConstraintWt = 10;

		// weight of magnitude-distribution INEQUALITY constraint relative to slip-rate
		// constraint (recommended: 1000)
//		double mfdInequalityConstraintWt = 1000;

		// magnitude-bin size for MFD participation smoothness constraint
		double participationConstraintMagBinSize = 0.1;

		// weight of rupture-rate smoothing constraint
		double rupRateSmoothingConstraintWt = 0;

		// weight of rupture-rate minimization constraint weights relative to slip-rate
		// constraint (recommended: 10,000)
		// (currently used to minimization rates of rups below sectMinMag)
		double minimizationConstraintWt = 10000;

		// weight of entropy-maximization constraint (should smooth rupture rates)
		// (recommended: 10000)
		double smoothnessWt = 0;

		// weight of Moment Constraint (set solution moment to equal deformation model
		// moment) (recommended: 1e-17)
		double momentConstraintWt = 0;

		double MFDTransitionMag = 7.85; // magnitude to switch from MFD equality to MFD inequality

		String metadata = "";

//		/* *******************************************
//		 * MODEL SPECIFIC
//		 * ******************************************* */
//		// define model specific value here (leave them as null or unassigned, then set values
//		// in the below switch statement
//		
//		// weight of rupture rate constraint (recommended strong weight: 5.0, weak weight: 0.1;
//		// 100X those weights if weightSlipRates=true) - can be UCERF2 rates or Smooth G-R rates
//		double rupRateConstraintWt;
//		
//		// weight of participation MFD smoothness - applied on subsection basis (recommended:  0.01)
//		double participationSmoothnessConstraintWt;
//		
//		// weight of nucleation MFD constraint - applied on subsection basis
		double nucleationMFDConstraintWt;
//		
//		// weight of spatial MFD smoothness constraint (recommended:  1000)
//		double mfdSmoothnessConstraintWt;
//		double mfdSmoothnessConstraintWtForPaleoParents; // weight for parent sections that have paleo constraints
//		
//		// weight of parent-section event-rate smoothness constraint
//		double eventRateSmoothnessWt;
//		
//		// fraction of the minimum rupture rate basis to be used as initial rates
		double minimumRuptureRateFraction = 0;
//		
//		double[] aPrioriRupConstraint;
		double[] initialRupModel = null;
		double[] minimumRuptureRateBasis = null;
//		

		// setup MFD constraints
		NZSHM22_CrustalInversionTargetMFDs inversionMFDs = new NZSHM22_CrustalInversionTargetMFDs(rupSet, 
				totalRateM5_Sans, totalRateM5_TVZ, bValue_Sans, bValue_TVZ, mfdTransitionMag);
		rupSet.setInversionTargetMFDs(inversionMFDs);
		List<MFD_InversionConstraint> mfdConstraints = inversionMFDs.getMFDConstraints();

		NZSHM22_CrustalInversionTargetMFDs inversionTargetMfds = (NZSHM22_CrustalInversionTargetMFDs) rupSet.getInversionTargetMFDs();		


//		// get MFD constraints
//		List<MFD_InversionConstraint> mfdConstraints = ((NZSHM22_CrustalInversionTargetMFDs) rupSet.getInversionTargetMFDs())
//				.getMFDConstraints();
//		NZSHM22_CrustalInversionTargetMFDs inversionTargetMfds = (NZSHM22_CrustalInversionTargetMFDs) rupSet.getInversionTargetMFDs();
		//SummedMagFreqDist targetOnFaultMFD = inversionTargetMfds.getOnFaultSupraSeisMFD();
		
		
////		System.out.println("SUPRA SEIS MFD = ");
////		System.out.println(rupSet.getInversionMFDs().getTargetOnFaultSupraSeisMFD());

		if (model.isConstrained()) {
			// CONSTRAINED BRANCHES
			if (model == InversionModels.CHAR_CONSTRAINED) {
//				participationSmoothnessConstraintWt = 0;
				nucleationMFDConstraintWt = 0.01;
//				mfdSmoothnessConstraintWt = 0;
//				mfdSmoothnessConstraintWtForPaleoParents = 1000;
//				eventRateSmoothnessWt = 0;
//				rupRateConstraintWt = 0;
//				aPrioriRupConstraint = getUCERF2Solution(rupSet);
//				initialRupModel = Arrays.copyOf(aPrioriRupConstraint, aPrioriRupConstraint.length); 

				// For water level
// >>			minimumRuptureRateFraction = 0.01;
				minimumRuptureRateFraction = 0.0;

// >>				minimumRuptureRateBasis = UCERF3InversionConfiguration.adjustStartingModel(
// >>						UCERF3InversionConfiguration.getSmoothStartingSolution(rupSet, targetOnFaultMFD),
// >>						mfdConstraints, rupSet, true);

//				initialRupModel = adjustIsolatedSections(rupSet, initialRupModel);
//				if (mfdInequalityConstraintWt>0.0 || mfdEqualityConstraintWt>0.0) initialRupModel = adjustStartingModel(initialRupModel, mfdConstraints, rupSet, true);
//				initialRupModel = adjustParkfield(rupSet, initialRupModel);
//				initialRupModel = removeRupsBelowMinMag(rupSet, initialRupModel);
				initialRupModel = new double[rupSet.getNumRuptures()];
			} else if (model == InversionModels.GR_CONSTRAINED) {
//				participationSmoothnessConstraintWt = 1000;
//				nucleationMFDConstraintWt = 0;
//				mfdSmoothnessConstraintWt = 0;
//				mfdSmoothnessConstraintWtForPaleoParents = 0;
//				eventRateSmoothnessWt = 0;
//				rupRateConstraintWt = 0;
//				aPrioriRupConstraint = null;
//				initialRupModel = getSmoothStartingSolution(rupSet,targetOnFaultMFD);
//				minimumRuptureRateFraction = 0.01;
//				minimumRuptureRateBasis = adjustStartingModel(initialRupModel, mfdConstraints, rupSet, true);
//				if (mfdInequalityConstraintWt>0.0 || mfdEqualityConstraintWt>0.0) initialRupModel = adjustStartingModel(initialRupModel, mfdConstraints, rupSet, true); 
//				initialRupModel = adjustParkfield(rupSet, initialRupModel);
//				initialRupModel = removeRupsBelowMinMag(rupSet, initialRupModel);
			} else
				throw new IllegalStateException("Unknown inversion model: " + model);
		} else {
			// UNCONSTRAINED BRANCHES
//			participationSmoothnessConstraintWt = 0;
//			nucleationMFDConstraintWt = 0;
//			mfdSmoothnessConstraintWt = 0;
//			mfdSmoothnessConstraintWtForPaleoParents = 0;
//			eventRateSmoothnessWt = 0;
//			rupRateConstraintWt = 0;
//			aPrioriRupConstraint = null;
//			initialRupModel = new double[rupSet.getNumRuptures()];
//			minimumRuptureRateBasis = null;
//			minimumRuptureRateFraction = 0;
		}

		/* end MODIFIERS */

		List<MFD_InversionConstraint> mfdInequalityConstraints = new ArrayList<MFD_InversionConstraint>();
		List<MFD_InversionConstraint> mfdEqualityConstraints = new ArrayList<MFD_InversionConstraint>();

		if (mfdEqualityConstraintWt > 0.0 && mfdInequalityConstraintWt > 0.0) {
			// we have both MFD constraints, apply a transition mag from equality to
			// inequality
			metadata += "\nMFDTransitionMag: " + MFDTransitionMag;
			mfdEqualityConstraints = restrictMFDConstraintMagRange(mfdConstraints,
					mfdConstraints.get(0).getMagFreqDist().getMinX(), MFDTransitionMag);
			mfdInequalityConstraints = restrictMFDConstraintMagRange(mfdConstraints, MFDTransitionMag,
					mfdConstraints.get(0).getMagFreqDist().getMaxX());
		} else if (mfdEqualityConstraintWt > 0.0) {
			mfdEqualityConstraints = mfdConstraints;
		} else if (mfdInequalityConstraintWt > 0.0) {
			mfdInequalityConstraints = mfdConstraints;
		} else {
			// no MFD constraints, do nothing
		}

		// NSHM-style config using setter methods...
		NZSHM22_CrustalInversionConfiguration newConfig = (NZSHM22_CrustalInversionConfiguration) new NZSHM22_CrustalInversionConfiguration()
				.setInversionTargetMfds(inversionTargetMfds)
				// MFD config
				.setMagnitudeEqualityConstraintWt(mfdEqualityConstraintWt)
				.setMagnitudeInequalityConstraintWt(mfdInequalityConstraintWt)
				// Slip Rate config
				.setSlipRateConstraintWt_normalized(slipRateConstraintWt_normalized)
				.setSlipRateConstraintWt_unnormalized(slipRateConstraintWt_unnormalized)
				.setSlipRateWeightingType(slipRateWeighting)
				
				.setMfdEqualityConstraints(mfdEqualityConstraints)
				.setMfdInequalityConstraints(mfdInequalityConstraints)

				// Rate Minimization config
				.setMinimizationConstraintWt(minimizationConstraintWt)
				.setMinimumRuptureRateFraction(minimumRuptureRateFraction)
				.setMinimumRuptureRateBasis(minimumRuptureRateBasis)
				.setInitialRupModel(initialRupModel);

		return newConfig;
	}



	

	
	
//	public double getPaleoRateConstraintWt() {
//		return paleoRateConstraintWt;
//	}
//
//	public void setPaleoRateConstraintWt(double paleoRateConstraintWt) {
//		this.paleoRateConstraintWt = paleoRateConstraintWt;
//	}
//
//	public double getPaleoSlipConstraintWt() {
//		return paleoSlipConstraintWt;
//	}
//
//	public void setPaleoSlipWt(double paleoSlipConstraintWt) {
//		this.paleoSlipConstraintWt = paleoSlipConstraintWt;
//	}

//	public double getRupRateConstraintWt() {
//		return rupRateConstraintWt;
//	}
//
//	public NZSHM22_InversionConfiguration setRupRateConstraintWt(double relativeRupRateConstraintWt) {
//		this.rupRateConstraintWt = relativeRupRateConstraintWt;
//		return this;
//	}

//	public double getParticipationSmoothnessConstraintWt() {
//		return participationSmoothnessConstraintWt;
//	}
//
//	public void setParticipationSmoothnessConstraintWt(
//			double relativeParticipationSmoothnessConstraintWt) {
//		this.participationSmoothnessConstraintWt = relativeParticipationSmoothnessConstraintWt;
//	}

//	public double getParticipationConstraintMagBinSize() {
//		return participationConstraintMagBinSize;
//	}
//
//	public void setParticipationConstraintMagBinSize(
//			double participationConstraintMagBinSize) {
//		this.participationConstraintMagBinSize = participationConstraintMagBinSize;
//	}



	// public double getMomentConstraintWt() {
//		return momentConstraintWt;
//	}
//
//	public void setMomentConstraintWt(
//			double relativeMomentConstraintWt) {
//		this.momentConstraintWt = relativeMomentConstraintWt;
//	}

//	public double[] getA_PrioriRupConstraint() {
//		return aPrioriRupConstraint;
//	}
//
//	public void setA_PrioriRupConstraint(double[] aPrioriRupConstraint) {
//		this.aPrioriRupConstraint = aPrioriRupConstraint;
//	}



//
//	public double getSmoothnessWt() {
//		return smoothnessWt;
//	}
//
//	public void setSmoothnessWt(double relativeSmoothnessWt) {
//		this.smoothnessWt = relativeSmoothnessWt;
//	}
//

//	
//	public double getMFDSmoothnessConstraintWt() {
//		return mfdSmoothnessConstraintWt;
//	}
//
//	public void setMFDSmoothnessConstraintWt(double relativeMFDSmoothnessConstraintWt) {
//		this.mfdSmoothnessConstraintWt = relativeMFDSmoothnessConstraintWt;
//	}
//	
//	public double getMFDSmoothnessConstraintWtForPaleoParents() {
//		return mfdSmoothnessConstraintWtForPaleoParents;
//	}
//
//	public void setMFDSmoothnessConstraintWtForPaleoParents(double relativeMFDSmoothnessConstraintWtForPaleoParents) {
//		this.mfdSmoothnessConstraintWtForPaleoParents = relativeMFDSmoothnessConstraintWtForPaleoParents;
//	}


	public String getMetadata() {
		return metadata;
	}

	public void updateRupSetInfoString(FaultSystemRupSet rupSet) {
		String info = rupSet.getInfoString();
		info += "\n\n****** Inversion Configuration Metadata ******";
		info += "\n" + getMetadata();
		info += "\n**********************************************";
		rupSet.setInfoString(info);
	}

//	public double getEventRateSmoothnessWt() {
//		return eventRateSmoothnessWt;
//	}
//
//	public NZSHM22_InversionConfiguration setEventRateSmoothnessWt(double eventRateSmoothnessWt) {
//		this.eventRateSmoothnessWt = eventRateSmoothnessWt;
//	}
//	
//	public double getRupRateSmoothingConstraintWt() {
//		return rupRateSmoothingConstraintWt;
//	}
//
//	public NZSHM22_InversionConfiguration setRupRateSmoothingConstraintWt(double rupRateSmoothingConstraintWt) {
//		this.rupRateSmoothingConstraintWt = rupRateSmoothingConstraintWt;
//	}

//	public enum SlipRateConstraintWeightingType {
//		NORMALIZED_BY_SLIP_RATE,  // Normalize each slip-rate constraint by the slip-rate target (So the inversion tries to minimize ratio of model to target)
//		UNNORMALIZED, // Do not normalize slip-rate constraint (inversion will minimize difference of model to target, effectively fitting fast faults better than slow faults on a ratio basis)
//		BOTH;  // Include both normalized and unnormalized constraints.  This doubles the number of slip-rate constraints, and is a compromise between normalized (which fits slow faults better on a difference basis) and the unnormalized constraint (which fits fast faults better on a ratio basis)
//	}


//	public boolean isAPrioriConstraintForZeroRates() {
//		return aPrioriConstraintForZeroRates;
//	}
//
//	public void setAPrioriConstraintForZeroRates(boolean aPrioriConstraintForZeroRates) {
//		this.aPrioriConstraintForZeroRates = aPrioriConstraintForZeroRates;
//	}
//
//	public double getAPrioriConstraintForZeroRatesWtFactor() {
//		return aPrioriConstraintForZeroRatesWtFactor;
//	}
//
//	public void setAPrioriConstraintForZeroRatesWtFactor(double aPrioriConstraintForZeroRatesWtFactor) {
//		this.aPrioriConstraintForZeroRatesWtFactor = aPrioriConstraintForZeroRatesWtFactor;
//	}
//



//	public static NZSHM22_InversionConfiguration fromXMLMetadata(Element confEl) {
//		double slipRateConstraintWt_normalized = Double.parseDouble(confEl.attributeValue("slipRateConstraintWt_normalized"));
//		double slipRateConstraintWt_unnormalized = Double.parseDouble(confEl.attributeValue("slipRateConstraintWt_unnormalized"));
//		SlipRateConstraintWeightingType slipRateWeighting = SlipRateConstraintWeightingType.valueOf(confEl.attributeValue("slipRateWeighting"));
//		double paleoRateConstraintWt = Double.parseDouble(confEl.attributeValue("paleoRateConstraintWt"));
//		double paleoSlipConstraintWt = Double.parseDouble(confEl.attributeValue("paleoSlipConstraintWt"));
//		double magnitudeEqualityConstraintWt = Double.parseDouble(confEl.attributeValue("magnitudeEqualityConstraintWt"));
//		double magnitudeInequalityConstraintWt = Double.parseDouble(confEl.attributeValue("magnitudeInequalityConstraintWt"));
//		double rupRateConstraintWt = Double.parseDouble(confEl.attributeValue("rupRateConstraintWt"));
//		double participationSmoothnessConstraintWt = Double.parseDouble(confEl.attributeValue("participationSmoothnessConstraintWt"));
//		double participationConstraintMagBinSize = Double.parseDouble(confEl.attributeValue("participationConstraintMagBinSize"));
//		double nucleationMFDConstraintWt = Double.parseDouble(confEl.attributeValue("nucleationMFDConstraintWt"));
//		double mfdSmoothnessConstraintWt = Double.parseDouble(confEl.attributeValue("mfdSmoothnessConstraintWt"));
//		double mfdSmoothnessConstraintWtForPaleoParents = Double.parseDouble(confEl.attributeValue("mfdSmoothnessConstraintWtForPaleoParents"));
//		double rupRateSmoothingConstraintWt = Double.parseDouble(confEl.attributeValue("rupRateSmoothingConstraintWt"));
//		double minimizationConstraintWt = Double.parseDouble(confEl.attributeValue("minimizationConstraintWt"));
//		double momentConstraintWt = Double.parseDouble(confEl.attributeValue("momentConstraintWt"));
//		double parkfieldConstraintWt = Double.parseDouble(confEl.attributeValue("parkfieldConstraintWt"));
//		double MFDTransitionMag = Double.parseDouble(confEl.attributeValue("MFDTransitionMag"));
//		double minimumRuptureRateFraction = Double.parseDouble(confEl.attributeValue("minimumRuptureRateFraction"));
//		double smoothnessWt = Double.parseDouble(confEl.attributeValue("smoothnessWt"));
//		double eventRateSmoothnessWt = Double.parseDouble(confEl.attributeValue("eventRateSmoothnessWt"));
//		
//		List<MFD_InversionConstraint> mfdEqualityConstraints = mfdsFromXML(confEl.element("MFD_EqualityConstraints"));
//		List<MFD_InversionConstraint> mfdInequalityConstraints = mfdsFromXML(confEl.element("MFD_InequalityConstraints"));
//		
//		return new NZSHM22_InversionConfiguration(slipRateConstraintWt_normalized, slipRateConstraintWt_unnormalized, slipRateWeighting, paleoRateConstraintWt,
//				paleoSlipConstraintWt, magnitudeEqualityConstraintWt, magnitudeInequalityConstraintWt, rupRateConstraintWt,
//				participationSmoothnessConstraintWt, participationConstraintMagBinSize, nucleationMFDConstraintWt,
//				mfdSmoothnessConstraintWt, mfdSmoothnessConstraintWtForPaleoParents, rupRateSmoothingConstraintWt,
//				minimizationConstraintWt, momentConstraintWt, parkfieldConstraintWt, null, null, null, smoothnessWt,
//				eventRateSmoothnessWt, MFDTransitionMag, mfdEqualityConstraints, mfdInequalityConstraints, minimumRuptureRateFraction, null);
//	}

	private static List<MFD_InversionConstraint> mfdsFromXML(Element mfdsEl) {
		List<Element> mfdElList = XMLUtils.getSortedChildElements(mfdsEl, null, "index");
		List<MFD_InversionConstraint> mfds = Lists.newArrayList();
		for (Element mfdEl : mfdElList) {
			MFD_InversionConstraint constr = MFD_InversionConstraint.fromXMLMetadata(mfdEl);
			mfds.add(constr);
		}
		return mfds;
	}


}

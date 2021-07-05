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
public class NZSHM22_SubductionInversionConfiguration extends NZSHM22_CrustalInversionConfiguration {

	public static final String XML_METADATA_NAME = "InversionConfiguration";

	private double slipRateConstraintWt_normalized;
	private double slipRateConstraintWt_unnormalized;
	private SlipRateConstraintWeightingType slipRateWeighting;
//	private double magnitudeEqualityConstraintWt;
//	private double magnitudeInequalityConstraintWt;

	private double nucleationMFDConstraintWt;

	private double minimizationConstraintWt;

	private double[] initialRupModel;
	// these are the rates that should be used for water level computation. this
	// will
	// often be set equal to initial rup model or a priori rup constraint
	private double[] minimumRuptureRateBasis;
	private double MFDTransitionMag;
	private List<MFD_InversionConstraint> mfdEqualityConstraints;
	private List<MFD_InversionConstraint> mfdInequalityConstraints;
	private double minimumRuptureRateFraction;

	protected final static boolean D = true; // for debugging

	private String metadata;

	/**
	 *
	 */
	public NZSHM22_SubductionInversionConfiguration() {
	}

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
	public static NZSHM22_SubductionInversionConfiguration forModel(InversionModels model,
			NZSHM22_InversionFaultSystemRuptSet rupSet) {
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
	public static NZSHM22_SubductionInversionConfiguration forModel(InversionModels model,
			NZSHM22_InversionFaultSystemRuptSet rupSet, double mfdEqualityConstraintWt, double mfdInequalityConstraintWt) {
		double totalRateM5 = 5; 
		double bValue = 1;
		double mfdTransitionMag = 7.75;
		return forModel(model, rupSet, mfdEqualityConstraintWt, mfdInequalityConstraintWt, totalRateM5,bValue, mfdTransitionMag);
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
	public static NZSHM22_SubductionInversionConfiguration forModel(InversionModels model,
			NZSHM22_InversionFaultSystemRuptSet rupSet, double mfdEqualityConstraintWt, double mfdInequalityConstraintWt,
			double totalRateM5, double bValue, double mfdTransitionMag) {

		double MFDTransitionMag = mfdTransitionMag; // magnitude to switch from MFD equality to MFD inequality
		
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

		// weight of magnitude-distribution EQUALITY constraint relative to slip-rate
		// constraint (recommended: 10)
		// mfdEqualityConstraintWt = 10;

		// weight of magnitude-distribution INEQUALITY constraint relative to slip-rate
		// constraint (recommended: 1000)
		// double mfdInequalityConstraintWt = 1000;

		// magnitude-bin size for MFD participation smoothness constraint
//		double participationConstraintMagBinSize = 0.1;

		// weight of rupture-rate smoothing constraint
//		double rupRateSmoothingConstraintWt = 0;

		// weight of rupture-rate minimization constraint weights relative to slip-rate
		// constraint (recommended: 10,000)
		// (currently used to minimization rates of rups below sectMinMag)
		double minimizationConstraintWt = 10000;

		// weight of entropy-maximization constraint (should smooth rupture rates)
		// (recommended: 10000)
//		double smoothnessWt = 0;

		// weight of Moment Constraint (set solution moment to equal deformation model
		// moment) (recommended: 1e-17)
//		double momentConstraintWt = 0;

		// setup MFD constraints
		NZSHM22_SubductionInversionTargetMFDs inversionMFDs = new NZSHM22_SubductionInversionTargetMFDs(rupSet, totalRateM5, bValue, mfdTransitionMag);
		rupSet.setInversionTargetMFDs(inversionMFDs);
		List<MFD_InversionConstraint> mfdConstraints = inversionMFDs.getMFDConstraints();



		String metadata = "";

//		/* *******************************************
//		 * MODEL SPECIFIC
//		 * ******************************************* */
//		// define model specific value here (leave them as null or unassigned, then set values
//		// in the below switch statement
//
//		// weight of nucleation MFD constraint - applied on subsection basis
		double nucleationMFDConstraintWt;
		// fraction of the minimum rupture rate basis to be used as initial rates
		double minimumRuptureRateFraction = 0;
		double[] initialRupModel = null;
		double[] minimumRuptureRateBasis = null;

		SummedMagFreqDist targetOnFaultMFD = rupSet.getInversionTargetMFDs().getOnFaultSupraSeisMFD();

		if (model == InversionModels.CHAR_CONSTRAINED) {
			nucleationMFDConstraintWt = 0.01;
			// For water level
			minimumRuptureRateFraction = 0.0;

//			minimumRuptureRateBasis = UCERF3InversionConfiguration.adjustStartingModel(
//					UCERF3InversionConfiguration.getSmoothStartingSolution(rupSet, targetOnFaultMFD), mfdConstraints,
//					rupSet, true);

			initialRupModel = new double[rupSet.getNumRuptures()];
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
		} else if (mfdEqualityConstraintWt > 0.0) { // no ineq wt
			mfdEqualityConstraints = mfdConstraints;
		} else if (mfdInequalityConstraintWt > 0.0) { // no eq wt
			mfdInequalityConstraints = mfdConstraints;
		} else {
			// no MFD constraints, do nothing
		}

		// NSHM-style config using setter methods...
		NZSHM22_SubductionInversionConfiguration newConfig = (NZSHM22_SubductionInversionConfiguration) new NZSHM22_SubductionInversionConfiguration()
				// MFD config
				.setMagnitudeEqualityConstraintWt(mfdEqualityConstraintWt)
				.setMagnitudeInequalityConstraintWt(mfdInequalityConstraintWt)
				.setMfdEqualityConstraints(mfdEqualityConstraints)
				.setMfdInequalityConstraints(mfdInequalityConstraints)
				// Slip Rate config
				.setSlipRateConstraintWt_normalized(slipRateConstraintWt_normalized)
				.setSlipRateConstraintWt_unnormalized(slipRateConstraintWt_unnormalized)
				.setSlipRateWeightingType(slipRateWeighting)
				// Rate Minimization config
				.setMinimizationConstraintWt(minimizationConstraintWt)
				.setMinimumRuptureRateFraction(minimumRuptureRateFraction)
				.setMinimumRuptureRateBasis(minimumRuptureRateBasis).setInitialRupModel(initialRupModel);

		return newConfig;
	}

}

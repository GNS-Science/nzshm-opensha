package nz.cri.gns.NZSHM22.opensha.inversion;

import java.util.ArrayList;
import java.util.List;

import com.google.common.base.Preconditions;
import org.opensha.commons.data.uncertainty.UncertainIncrMagFreqDist;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import scratch.UCERF3.enumTreeBranches.InversionModels;

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
public class NZSHM22_SubductionInversionConfiguration extends AbstractInversionConfiguration {

	protected final static boolean D = true; // for debugging

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
		double mfdMinMag = 7.05;
		return forModel(model, rupSet, null, mfdEqualityConstraintWt, mfdInequalityConstraintWt, 0, 0, 0.4, totalRateM5, bValue, mfdTransitionMag, mfdMinMag);
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
	 * @param mfdUncertaintyWeightedConstraintScalar TODO
	 * @param totalRateM5
	 * @param bValue
	 * @param mfdTransitionMag
	 * @return
	 */
	public static NZSHM22_SubductionInversionConfiguration forModel(InversionModels model,
			NZSHM22_InversionFaultSystemRuptSet rupSet, double[] initialSolution, double mfdEqualityConstraintWt, double mfdInequalityConstraintWt,
			double mfdUncertaintyWeightedConstraintWt, double mfdUncertaintyWeightedConstraintPower,
			double mfdUncertaintyWeightedConstraintScalar, double totalRateM5, double bValue, double mfdTransitionMag, double mfdMinMag) {

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
		NZSlipRateConstraintWeightingType slipRateWeighting = NZSlipRateConstraintWeightingType.BOTH; // (recommended: BOTH)

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
		NZSHM22_SubductionInversionTargetMFDs inversionMFDs =  new NZSHM22_SubductionInversionTargetMFDs(rupSet, totalRateM5, bValue, mfdTransitionMag,
				mfdMinMag, mfdUncertaintyWeightedConstraintWt, mfdUncertaintyWeightedConstraintPower, mfdUncertaintyWeightedConstraintScalar);
		rupSet.setInversionTargetMFDs(inversionMFDs);

//		NZSHM22_SubductionInversionTargetMFDs inversionTargetMfds = (NZSHM22_SubductionInversionTargetMFDs) rupSet.getInversionTargetMFDs();

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

		@SuppressWarnings("unchecked")
		List<IncrementalMagFreqDist> mfdConstraints = new ArrayList<>();
		mfdConstraints.addAll(inversionMFDs.getMfdEqIneqConstraints());
		mfdConstraints.addAll(inversionMFDs.getMfdUncertaintyConstraints());

//		SummedMagFreqDist targetOnFaultMFD = rupSet.getInversionTargetMFDs().getOnFaultSupraSeisMFD();
		IncrementalMagFreqDist targetOnFaultMFD =  inversionMFDs.getTotalOnFaultSupraSeisMFD();

		if (model == InversionModels.CHAR_CONSTRAINED) {
			nucleationMFDConstraintWt = 0.01;
			// For water level
			minimumRuptureRateFraction = 0.0;
			
			// Made local copy of adjustStartingModel as it's private in UCERF3InversionConfiguration
			minimumRuptureRateBasis = adjustStartingModel(
					getSmoothStartingSolution(rupSet, targetOnFaultMFD),
					mfdConstraints,
					rupSet,
					true);

			if(initialSolution != null) {
				Preconditions.checkArgument(rupSet.getNumRuptures() == initialSolution.length, "Initial solution is for the wrong number of ruptures.");
				initialRupModel = initialSolution;
			}else {
				initialRupModel = new double[rupSet.getNumRuptures()];
			}
		}

		/* end MODIFIERS */

		// NSHM-style config using setter methods...
		NZSHM22_SubductionInversionConfiguration newConfig = (NZSHM22_SubductionInversionConfiguration) new NZSHM22_SubductionInversionConfiguration()
				.setInversionTargetMfds(inversionMFDs)
				// MFD config is now below
				// Slip Rate config
				.setSlipRateConstraintWt_normalized(slipRateConstraintWt_normalized)
				.setSlipRateConstraintWt_unnormalized(slipRateConstraintWt_unnormalized)
				.setSlipRateWeightingType(slipRateWeighting)
				// Rate Minimization config
				.setMinimizationConstraintWt(minimizationConstraintWt)
				.setMinimumRuptureRateFraction(minimumRuptureRateFraction)
				.setMinimumRuptureRateBasis(minimumRuptureRateBasis)
				.setInitialRupModel(initialRupModel);


		//MFD constraint configuration
		List<IncrementalMagFreqDist> mfdInequalityConstraints = new ArrayList<>();
		List<IncrementalMagFreqDist> mfdEqualityConstraints = new ArrayList<>();
		List<UncertainIncrMagFreqDist> mfdUncertaintyWeightedConstraints = new ArrayList<>();

		mfdConstraints = inversionMFDs.getMfdEqIneqConstraints();
		if (mfdEqualityConstraintWt > 0.0 && mfdInequalityConstraintWt > 0.0) {
			// we have both MFD constraints, apply a transition mag from equality to
			// inequality
			mfdEqualityConstraints = MFDManipulation.restrictMFDConstraintMagRange(mfdConstraints,
					mfdConstraints.get(0).getMinX(), MFDTransitionMag);
			mfdInequalityConstraints = MFDManipulation.restrictMFDConstraintMagRange(mfdConstraints, MFDTransitionMag,
					mfdConstraints.get(0).getMaxX());
			newConfig
				.setMagnitudeEqualityConstraintWt(mfdEqualityConstraintWt)
				.setMagnitudeInequalityConstraintWt(mfdInequalityConstraintWt)
				.setMfdEqualityConstraints(mfdEqualityConstraints)
				.setMfdInequalityConstraints(mfdInequalityConstraints);
		} else if (mfdEqualityConstraintWt > 0.0) { // no ineq wt
			mfdEqualityConstraints = mfdConstraints;
			newConfig
				.setMagnitudeEqualityConstraintWt(mfdEqualityConstraintWt)
				.setMfdEqualityConstraints(mfdEqualityConstraints);
		} else if (mfdInequalityConstraintWt > 0.0) { // no eq wt
			mfdInequalityConstraints = mfdConstraints;
			newConfig
				.setMagnitudeInequalityConstraintWt(mfdInequalityConstraintWt)
				.setMfdInequalityConstraints(mfdInequalityConstraints);
		}

		if (mfdUncertaintyWeightedConstraintWt > 0.0 ) {
			mfdUncertaintyWeightedConstraints = inversionMFDs.getMfdUncertaintyConstraints();
			newConfig
				.setMagnitudeUncertaintyWeightedConstraintWt(mfdUncertaintyWeightedConstraintWt) //NEW constraint
				.setMfdUncertaintyWeightedConstraints(mfdUncertaintyWeightedConstraints);
		}

		return newConfig;
	}

	
	/**
	 * CBC: cloned this from UCERF3InversionConfiguration just for testing with some water levels. It needs work to  
	 *      decided a) if its wanted and b) how it should work!! @MCG
	 *      
	 * This method adjusts the starting model to ensure that for each MFD inequality constraint magnitude-bin, the starting model is below the MFD.
	 * If adjustOnlyIfOverMFD = false, it will adjust the starting model so that it's MFD equals the MFD constraint.
	 * It will uniformly reduce the rates of ruptures in any magnitude bins that need adjusting.
	 */
	private static double[] adjustStartingModel(double[] initialRupModel,
			List<IncrementalMagFreqDist> mfdInequalityConstraints, FaultSystemRupSet rupSet, boolean adjustOnlyIfOverMFD) {
		
		double[] rupMeanMag = rupSet.getMagForAllRups();
		
		
		for (int i=0; i<mfdInequalityConstraints.size(); i++) {
			double[] fractRupsInside = rupSet.getFractRupsInsideRegion(mfdInequalityConstraints.get(i).getRegion(), false);
			IncrementalMagFreqDist targetMagFreqDist = mfdInequalityConstraints.get(i);
			IncrementalMagFreqDist startingModelMagFreqDist = new IncrementalMagFreqDist(targetMagFreqDist.getMinX(), targetMagFreqDist.size(), targetMagFreqDist.getDelta());
			startingModelMagFreqDist.setTolerance(0.1);
			
			// Find the starting model MFD
			for(int rup=0; rup<rupSet.getNumRuptures(); rup++) {
				double mag = rupMeanMag[rup];
				double fractRupInside = fractRupsInside[rup];
				if (fractRupInside > 0) 
					if (mag<8.5)  // b/c the mfdInequalityConstraints only go to M8.5!
						startingModelMagFreqDist.add(mag, fractRupInside * initialRupModel[rup]);
			}
			
			// Find the amount to adjust starting model MFD to be below or equal to Target MFD
			IncrementalMagFreqDist adjustmentRatio = new IncrementalMagFreqDist(targetMagFreqDist.getMinX(), targetMagFreqDist.size(), targetMagFreqDist.getDelta());
			for (double m=targetMagFreqDist.getMinX(); m<=targetMagFreqDist.getMaxX(); m+= targetMagFreqDist.getDelta()) {
				if (adjustOnlyIfOverMFD == false)
					adjustmentRatio.set(m, targetMagFreqDist.getClosestYtoX(m) / startingModelMagFreqDist.getClosestYtoX(m));
				else {
					if (startingModelMagFreqDist.getClosestYtoX(m) > targetMagFreqDist.getClosestYtoX(m))
						adjustmentRatio.set(m, targetMagFreqDist.getClosestYtoX(m) / startingModelMagFreqDist.getClosestYtoX(m));
					else
						adjustmentRatio.set(m, 1.0);
				}
			}
			
			// Adjust initial model rates
			for(int rup=0; rup<rupSet.getNumRuptures(); rup++) {
				double mag = rupMeanMag[rup];
				if (!Double.isNaN(adjustmentRatio.getClosestYtoX(mag)) && !Double.isInfinite(adjustmentRatio.getClosestYtoX(mag)))
					initialRupModel[rup] = initialRupModel[rup] * adjustmentRatio.getClosestYtoX(mag);
			}
			
		}
		
		
/*		// OPTIONAL: Adjust rates of largest rups to equal global target MFD
		IncrementalMagFreqDist targetMagFreqDist = UCERF3_MFD_ConstraintFetcher.getTargetMFDConstraint(TimeAndRegion.ALL_CA_1850).getMagFreqDist();
		IncrementalMagFreqDist startingModelMagFreqDist = new IncrementalMagFreqDist(targetMagFreqDist.getMinX(), targetMagFreqDist.getNum(), targetMagFreqDist.getDelta());
		startingModelMagFreqDist.setTolerance(0.1);
		for(int rup=0; rup<rupSet.getNumRuptures(); rup++) {
			double mag = rupMeanMag[rup];
			if (mag<8.5)
				startingModelMagFreqDist.add(mag, initialRupModel[rup]);
		}	
		IncrementalMagFreqDist adjustmentRatio = new IncrementalMagFreqDist(targetMagFreqDist.getMinX(), targetMagFreqDist.getNum(), targetMagFreqDist.getDelta());
		for (double m=targetMagFreqDist.getMinX(); m<=targetMagFreqDist.getMaxX(); m+= targetMagFreqDist.getDelta()) {
			if (m>8.0)	adjustmentRatio.set(m, targetMagFreqDist.getClosestY(m) / startingModelMagFreqDist.getClosestY(m));
			else adjustmentRatio.set(m, 1.0);
		}
		for(int rup=0; rup<rupSet.getNumRuptures(); rup++) {
			double mag = rupMeanMag[rup];
			if (!Double.isNaN(adjustmentRatio.getClosestY(mag)) && !Double.isInfinite(adjustmentRatio.getClosestY(mag)))
				initialRupModel[rup] = initialRupModel[rup] * adjustmentRatio.getClosestY(mag);
		}	*/
		
		
		return initialRupModel;
	}

	/**
	 * --- This method was copied from UCERF3 in order to be able to modify it ---
	 * This creates a smooth starting solution, which partitions the available rates from the target MagFreqDist
	 * to each rupture in the rupture set.  So the total rate of all ruptures in a given magnitude bin as defined by the MagFreqDist
	 * is partitioned among all the ruptures with a magnitude in that bin, in proportion to the minimum slip rate section for each rupture.
	 * NaN slip rates are treated as zero (so any ruptures with a NaN or 0 slip rate section will have a zero rate in the returned starting solution).
	 *
	 * Making rates proportional to the minimum slip rate section of a rupture was found to work better than making the rates proportional to the mean slip rate
	 * for each rupture.  Also, the current code does not account for overlap of ruptures.  This was tested and did not lead to better starting solutions,
	 * and in addition had a great computational cost.
	 *
	 * @param faultSystemRupSet, targetMagFreqDist
	 * @return initial_state
	 */
	public static double[] getSmoothStartingSolution(
			FaultSystemRupSet faultSystemRupSet, IncrementalMagFreqDist targetMagFreqDist) {

		double[] rupMeanMag = faultSystemRupSet.getMagForAllRups();
		double[] sectSlipRateReduced = faultSystemRupSet.getSlipRateForAllSections();
		int numRup = rupMeanMag.length;
		double[] initial_state = new double[numRup];  // starting model to be returned
		double[] minimumSlipRate = new double[numRup];  // mean slip rate per section for each rupture

		// Calculate minimum slip rates for ruptures
		// If there are NaN slip rates, treat them as 0
		for (int rup=0; rup<numRup; rup++) {
			List<Integer> sects = faultSystemRupSet.getSectionsIndicesForRup(rup);
			minimumSlipRate[rup] = Double.POSITIVE_INFINITY;
			for (int i=0; i<sects.size(); i++) {
				int sect = sects.get(i);
				if (Double.isNaN(sectSlipRateReduced[sect])  || sectSlipRateReduced[sect] == 0)  {
					minimumSlipRate[rup] = 0;
				} else 	if (sectSlipRateReduced[sect] < minimumSlipRate[rup]) {
					minimumSlipRate[rup] = sectSlipRateReduced[sect];
				}
			}
		}


		// Find magnitude distribution of ruptures (as discretized)
		double minMag = Math.floor(faultSystemRupSet.getMinMag()*10.0)/10.0;
		double maxMag = Math.ceil(faultSystemRupSet.getMaxMag()*10.0)/10.0;
		IncrementalMagFreqDist magHist = new IncrementalMagFreqDist(minMag,(int) Math.round((maxMag-minMag)*10+1),0.1);
		magHist.setTolerance(0.05);
		for(int rup=0; rup<numRup;rup++) {
			// Each bin in the magnitude histogram should be weighted by the mean slip rates of those ruptures
			// (since later we weight the ruptures by the mean slip rate, which would otherwise result in
			// starting solution that did not match target MFD if the mean slip rates per rupture
			// differed between magnitude bins)
			if (minimumSlipRate[rup]!=0)
				magHist.add(rupMeanMag[rup], minimumSlipRate[rup]);  // each bin
			else magHist.add(rupMeanMag[rup], 1E-4);
		}


		// Set up initial (non-normalized) target MFD rates for each rupture, normalized by meanSlipRate
		for (int rup=0; rup<numRup; rup++) {
			initial_state[rup] = targetMagFreqDist.getClosestYtoX(rupMeanMag[rup]) * minimumSlipRate[rup] / magHist.getClosestYtoX(rupMeanMag[rup]);
			if (Double.isNaN(initial_state[rup]) || Double.isInfinite(initial_state[rup]))
				throw new IllegalStateException("Pre-normalization initial_state["+rup+"] = "+initial_state[rup]);
		}


		// Find normalization for all ruptures (so that MFD matches target MFD normalization)
		// Can't just add up all the mag bins to normalize because some bins don't have ruptures.
		// Instead let's choose one mag bin (that we know has rups) that has rups and normalize
		// all bins by the amount it's off:
		double magNorm = 8.1;
		double totalEventRate=0;
		for (int rup=0; rup<numRup; rup++) {
			if (rupMeanMag[rup]>magNorm && rupMeanMag[rup]<=magNorm+0.1)
				totalEventRate += initial_state[rup];
		}
		double normalization = targetMagFreqDist.getClosestYtoX(magNorm)/totalEventRate;
		if (targetMagFreqDist.getClosestYtoX(magNorm)==0)
			throw new IllegalStateException("targetMagFreqDist.getClosestY(magNorm) = 0.  Check rupSet.getInversionMFDs().getTargetOnFaultSupraSeisMFD()");
		// Adjust rates by normalization to match target MFD total event rates
		for (int rup=0; rup<numRup; rup++) {
			initial_state[rup]=initial_state[rup]*normalization;
			if (Double.isNaN(initial_state[rup]) || Double.isInfinite(initial_state[rup]))
				throw new IllegalStateException("initial_state["+rup+"] = "+initial_state[rup]
						+" (norm="+normalization+", totalEventRate="+totalEventRate+")");
		}


		return initial_state;

	}


}

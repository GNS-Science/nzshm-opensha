package nz.cri.gns.NZSHM22.opensha.inversion;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.IntPredicate;

import com.google.common.base.Preconditions;
import nz.cri.gns.NZSHM22.opensha.data.region.NewZealandRegions;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_LogicTreeBranch;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_SpatialSeisPDF;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModSectMinMags;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import scratch.UCERF3.enumTreeBranches.InversionModels;

/**
 * This represents all of the inversion configuration parameters specific to an
 * individual model on the NZSHM22 logic tree.
 * 
 * based on scratch.UCERF3.inversion.UCERF3InversionConfiguration
 * 
 * @author chrisbc
 *
 */
public class NZSHM22_CrustalInversionConfiguration extends AbstractInversionConfiguration {

	protected final static boolean D = true; // for debugging
	private double paleoRateConstraintWt;
	private double mfdSmoothnessConstraintWtForPaleoParents;

	/**
	 * 
	 */
	public NZSHM22_CrustalInversionConfiguration() {
	}

	public static final double DEFAULT_MFD_EQUALITY_WT = 10;
	public static final double DEFAULT_MFD_INEQUALITY_WT = 1000;

	public static void setRegionalData(NZSHM22_InversionFaultSystemRuptSet rupSet, double mMin_Sans, double mMin_TVZ){

		GriddedRegion tvzRegion = new NewZealandRegions.NZ_TVZ_GRIDDED();
		GriddedRegion sansTvzRegion = new NewZealandRegions.NZ_RECTANGLE_SANS_TVZ_GRIDDED();

		IntPredicate tvzFilter = RegionalRupSetData.createRegionFilter(rupSet, tvzRegion);

		RegionalRupSetData tvz = new RegionalRupSetData(rupSet, tvzRegion, tvzFilter, mMin_TVZ);
		RegionalRupSetData sansTvz = new RegionalRupSetData(rupSet, sansTvzRegion, tvzFilter.negate(), mMin_Sans);

		rupSet.setRegionalData(tvz, sansTvz);

		double[] minMags = new double[rupSet.getNumSections()];

		for(int s = 0; s < minMags.length; s++){
			if(tvz.isInRegion(s)){
				minMags[s] = tvz.getMinMagForOriginalSectionid(s);
			} else {
				minMags[s] = sansTvz.getMinMagForOriginalSectionid(s);
			}
		}

		if (rupSet.hasAvailableModule(ModSectMinMags.class)) {
			rupSet.removeModuleInstances(ModSectMinMags.class);
		}
		rupSet.addAvailableModule(new Callable<ModSectMinMags>() {
			@Override
			public ModSectMinMags call() throws Exception {
				return ModSectMinMags.instance(rupSet, minMags);
			}
		}, ModSectMinMags.class);
	}

	/**
	 * This generates an inversion configuration for the given inversion model and
	 * rupture set
	 * 
	 * @param model
	 * @param rupSet
	 * @return
	 */
	public static NZSHM22_CrustalInversionConfiguration forModel(InversionModels model,
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
	public static NZSHM22_CrustalInversionConfiguration forModel(InversionModels model,
			NZSHM22_InversionFaultSystemRuptSet rupSet, double mfdEqualityConstraintWt,
			double mfdInequalityConstraintWt) {
		double totalRateM5 = 5;
		double bValue = 1;
		double mfdTransitionMag = 7.75;
		return forModel(model, rupSet,  null, mfdEqualityConstraintWt, mfdInequalityConstraintWt, totalRateM5, totalRateM5, // here xxx
				bValue, bValue, mfdTransitionMag, 7.0, 7.0);
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
	 * @param totalRateM5_Sans
	 * @param totalRateM5_TVZ
	 * @param bValue_Sans
	 * @param bValue_TVZ
	 * @param mfdTransitionMag
	 * @return
	 */
	public static NZSHM22_CrustalInversionConfiguration forModel(InversionModels model,
			NZSHM22_InversionFaultSystemRuptSet rupSet, double[] initialSolution, double mfdEqualityConstraintWt,
			double mfdInequalityConstraintWt, double totalRateM5_Sans, double totalRateM5_TVZ, double bValue_Sans,
			double bValue_TVZ, double mfdTransitionMag, double mMin_Sans, double mMin_TVZ) {
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

		// weight of rupture-rate minimization constraint weights relative to slip-rate
		// constraint (recommended: 10,000)
		// (currently used to minimization rates of rups below sectMinMag)
		double minimizationConstraintWt = 10000;

//		/* *******************************************
//		 * MODEL SPECIFIC
//		 * ******************************************* */
//		// fraction of the minimum rupture rate basis to be used as initial rates
		double minimumRuptureRateFraction = 0;

		double[] initialRupModel = null;
		double[] minimumRuptureRateBasis = null;

		setRegionalData(rupSet, mMin_Sans, mMin_TVZ);

		// setup MFD constraints
		NZSHM22_CrustalInversionTargetMFDs inversionMFDs = new NZSHM22_CrustalInversionTargetMFDs(rupSet,
				totalRateM5_Sans, totalRateM5_TVZ, bValue_Sans, bValue_TVZ, mMin_Sans, mMin_TVZ);
		rupSet.setInversionTargetMFDs(inversionMFDs);
		List<IncrementalMagFreqDist> mfdConstraints = inversionMFDs.getMFD_Constraints();

		if (model.isConstrained()) {
			// CONSTRAINED BRANCHES
			if (model == InversionModels.CHAR_CONSTRAINED) {
				// For water level
				minimumRuptureRateFraction = 0.0;

// >>				minimumRuptureRateBasis = UCERF3InversionConfiguration.adjustStartingModel(
// >>						UCERF3InversionConfiguration.getSmoothStartingSolution(rupSet, targetOnFaultMFD),
// >>						mfdConstraints, rupSet, true);

//				initialRupModel = adjustIsolatedSections(rupSet, initialRupModel);
//				if (mfdInequalityConstraintWt>0.0 || mfdEqualityConstraintWt>0.0) initialRupModel = adjustStartingModel(initialRupModel, mfdConstraints, rupSet, true);

//				initialRupModel = removeRupsBelowMinMag(rupSet, initialRupModel);
				if(initialSolution != null) {
					Preconditions.checkArgument(rupSet.getNumRuptures() == initialSolution.length, "Initial solution is for the wrong number of ruptures.");
					initialRupModel = initialSolution;
				}else {
					initialRupModel = new double[rupSet.getNumRuptures()];
				}
			} else
				throw new IllegalStateException("Unknown inversion model: " + model);
		}

		/* end MODIFIERS */

		List<IncrementalMagFreqDist> mfdInequalityConstraints = new ArrayList<>();
		List<IncrementalMagFreqDist> mfdEqualityConstraints = new ArrayList<>();

		if (mfdEqualityConstraintWt > 0.0 && mfdInequalityConstraintWt > 0.0) {
			// we have both MFD constraints, apply a transition mag from equality to
			// inequality

			mfdEqualityConstraints = restrictMFDConstraintMagRange(mfdConstraints,
					mfdConstraints.get(0).getMinX(), mfdTransitionMag);
			mfdInequalityConstraints = restrictMFDConstraintMagRange(mfdConstraints, mfdTransitionMag,
					mfdConstraints.get(0).getMaxX());
		} else if (mfdEqualityConstraintWt > 0.0) {
			mfdEqualityConstraints = mfdConstraints;
		} else if (mfdInequalityConstraintWt > 0.0) {
			mfdInequalityConstraints = mfdConstraints;
		} else {
			// no MFD constraints, do nothing
		}

		// NSHM-style config using setter methods...
		NZSHM22_CrustalInversionConfiguration newConfig = (NZSHM22_CrustalInversionConfiguration) new NZSHM22_CrustalInversionConfiguration()
				.setInversionTargetMfds(inversionMFDs)
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
				.setMinimumRuptureRateBasis(minimumRuptureRateBasis).setInitialRupModel(initialRupModel);

		return newConfig;
	}

	public NZSHM22_CrustalInversionConfiguration setPaleoRateConstraintWt(double paleoRateConstraintWt){
		this.paleoRateConstraintWt = paleoRateConstraintWt;
		return this;
	}

	public double getPaleoRateConstraintWt() {
		return paleoRateConstraintWt;
	}

	public NZSHM22_CrustalInversionConfiguration setpaleoParentRateSmoothnessConstraintWeight(double paleoParentRateSmoothnessConstraintWeight){
		this.mfdSmoothnessConstraintWtForPaleoParents = paleoParentRateSmoothnessConstraintWeight;
		return this;
	}

	public double getpaleoParentRateSmoothnessConstraintWeight(){
		return mfdSmoothnessConstraintWtForPaleoParents;
	}

}

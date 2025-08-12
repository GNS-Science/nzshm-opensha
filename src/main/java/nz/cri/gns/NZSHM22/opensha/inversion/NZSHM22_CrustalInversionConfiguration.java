package nz.cri.gns.NZSHM22.opensha.inversion;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.List;
import nz.cri.gns.NZSHM22.opensha.data.region.NewZealandRegions;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import scratch.UCERF3.enumTreeBranches.InversionModels;

/**
 * This represents all of the inversion configuration parameters specific to an individual model on
 * the NZSHM22 logic tree.
 *
 * <p>based on scratch.UCERF3.inversion.UCERF3InversionConfiguration
 *
 * @author chrisbc
 */
public class NZSHM22_CrustalInversionConfiguration extends AbstractInversionConfiguration {

    private double paleoRateConstraintWt;
    private double mfdSmoothnessConstraintWtForPaleoParents;

    /** */
    public NZSHM22_CrustalInversionConfiguration() {}

    public static void setRegionalData(
            NZSHM22_InversionFaultSystemRuptSet rupSet, double mMin_Sans, double mMin_TVZ) {

        // TVZ is hard-coded to always be empty, and sansTVZ is hardcoded to always be all of NZ
        RegionalRupSetData tvz =
                new RegionalRupSetData(
                        rupSet, new NewZealandRegions.NZ_EMPTY_GRIDDED(), id -> false, mMin_TVZ);
        RegionalRupSetData sansTvz =
                new RegionalRupSetData(rupSet, NewZealandRegions.NZ, id -> true, mMin_Sans);

        rupSet.setRegionalData(tvz, sansTvz);
    }

    /**
     * This generates an inversion configuration for the given inversion model and rupture set
     *
     * @param model
     * @param rupSet
     * @param mfdEqualityConstraintWt weight of magnitude-distribution EQUALITY constraint relative
     *     to slip-rate constraint (recommended: 10)
     * @param mfdInequalityConstraintWt weight of magnitude-distribution INEQUALITY constraint
     *     relative to slip-rate constraint (recommended: 1000)
     * @param totalRateM5_Sans
     * @param totalRateM5_TVZ
     * @param bValue_Sans
     * @param bValue_TVZ
     * @param mfdTransitionMag
     * @param mfdUncertWtdConstraintScalar TODO
     * @return
     */
    public static NZSHM22_CrustalInversionConfiguration forModel(
            InversionModels model,
            NZSHM22_InversionFaultSystemRuptSet rupSet,
            double[] initialSolution,
            double mfdEqualityConstraintWt,
            double mfdInequalityConstraintWt,
            double totalRateM5_Sans,
            double totalRateM5_TVZ,
            double bValue_Sans,
            double bValue_TVZ,
            double mfdTransitionMag,
            double mMin_Sans,
            double mMin_TVZ,
            double maxMagSans,
            double maxMagTVZ,
            double mfdUncertWtdConstraintWt,
            double mfdUncertWtdConstraintPower,
            double mfdUncertWtdConstraintScalar,
            boolean excludeMinMag) {

        /*
         * ******************************************* COMMON TO ALL MODELS
         * *******************************************
         */
        // Setting slip-rate constraint weights to 0 does not disable them! To disable
        // one or the other (both cannot be), use slipConstraintRateWeightingType Below
        double slipRateConstraintWt_normalized =
                1; // For SlipRateConstraintWeightingType.NORMALIZED (also used for
        // SlipRateConstraintWeightingType.BOTH) -- NOT USED if
        // UNNORMALIZED!
        double slipRateConstraintWt_unnormalized =
                100; // For SlipRateConstraintWeightingType.UNNORMALIZED (also used
        // for SlipRateConstraintWeightingType.BOTH) -- NOT USED if
        // NORMALIZED!
        // If normalized, slip rate misfit is % difference for each section (recommended
        // since it helps fit slow-moving faults). If unnormalized, misfit is absolute
        // difference.
        // BOTH includes both normalized and unnormalized constraints.
        NZSlipRateConstraintWeightingType slipRateWeighting =
                NZSlipRateConstraintWeightingType.BOTH; // (recommended: BOTH)

        // weight of rupture-rate minimization constraint weights relative to slip-rate
        // constraint (recommended: 10,000)
        // (currently used to minimization rates of rups below sectMinMag)
        double minimizationConstraintWt = 10000;

        //        /* *******************************************
        //         * MODEL SPECIFIC
        //         * ******************************************* */
        //        // fraction of the minimum rupture rate basis to be used as initial rates
        double minimumRuptureRateFraction = 0;

        double[] initialRupModel = null;
        double[] minimumRuptureRateBasis = null;

        setRegionalData(rupSet, mMin_Sans, mMin_TVZ);

        // setup MFD constraints
        NZSHM22_CrustalInversionTargetMFDs inversionMFDs =
                new NZSHM22_CrustalInversionTargetMFDs(
                        rupSet,
                        totalRateM5_Sans,
                        totalRateM5_TVZ,
                        bValue_Sans,
                        bValue_TVZ,
                        mMin_Sans,
                        mMin_TVZ,
                        maxMagSans,
                        maxMagTVZ,
                        mfdUncertWtdConstraintPower,
                        mfdUncertWtdConstraintScalar);
        rupSet.setInversionTargetMFDs(inversionMFDs);
        List<IncrementalMagFreqDist> mfdConstraints = inversionMFDs.getMFD_Constraints();

        if (model.isConstrained()) {
            // CONSTRAINED BRANCHES
            if (model == InversionModels.CHAR_CONSTRAINED) {
                // For water level
                minimumRuptureRateFraction = 0.0;

                // >>                minimumRuptureRateBasis =
                // UCERF3InversionConfiguration.adjustStartingModel(
                // >>
                // UCERF3InversionConfiguration.getSmoothStartingSolution(rupSet,
                // targetOnFaultMFD),
                // >>                        mfdConstraints, rupSet, true);

                //                initialRupModel = adjustIsolatedSections(rupSet, initialRupModel);
                //                if (mfdInequalityConstraintWt>0.0 || mfdEqualityConstraintWt>0.0)
                // initialRupModel = adjustStartingModel(initialRupModel, mfdConstraints, rupSet,
                // true);

                //                initialRupModel = removeRupsBelowMinMag(rupSet, initialRupModel);
                if (initialSolution != null) {
                    Preconditions.checkArgument(
                            rupSet.getNumRuptures() == initialSolution.length,
                            "Initial solution is for the wrong number of ruptures.");
                    initialRupModel = initialSolution;
                } else {
                    initialRupModel = new double[rupSet.getNumRuptures()];
                }
            } else throw new IllegalStateException("Unknown inversion model: " + model);
        }

        /* end MODIFIERS */

        List<IncrementalMagFreqDist> mfdInequalityConstraints = new ArrayList<>();
        List<IncrementalMagFreqDist> mfdEqualityConstraints = new ArrayList<>();

        if (mfdEqualityConstraintWt > 0.0 && mfdInequalityConstraintWt > 0.0) {
            // we have both MFD constraints, apply a transition mag from equality to
            // inequality

            mfdEqualityConstraints =
                    MFDManipulation.restrictMFDConstraintMagRange(
                            mfdConstraints, mfdConstraints.get(0).getMinX(), mfdTransitionMag);
            mfdInequalityConstraints =
                    MFDManipulation.restrictMFDConstraintMagRange(
                            mfdConstraints, mfdTransitionMag, mfdConstraints.get(0).getMaxX());
        } else if (mfdEqualityConstraintWt > 0.0) {
            mfdEqualityConstraints = mfdConstraints;
        } else if (mfdInequalityConstraintWt > 0.0) {
            mfdInequalityConstraints = mfdConstraints;
        } else {
            // no MFD constraints, do nothing
        }

        // NSHM-style config using setter methods...
        NZSHM22_CrustalInversionConfiguration newConfig =
                (NZSHM22_CrustalInversionConfiguration)
                        new NZSHM22_CrustalInversionConfiguration()
                                .setInversionTargetMfds(inversionMFDs)
                                // MFD config
                                .setMagnitudeEqualityConstraintWt(mfdEqualityConstraintWt)
                                .setMagnitudeInequalityConstraintWt(mfdInequalityConstraintWt)
                                // Slip Rate config
                                .setSlipRateConstraintWt_normalized(slipRateConstraintWt_normalized)
                                .setSlipRateConstraintWt_unnormalized(
                                        slipRateConstraintWt_unnormalized)
                                .setSlipRateWeightingType(slipRateWeighting)
                                .setMfdEqualityConstraints(mfdEqualityConstraints)
                                .setMfdInequalityConstraints(mfdInequalityConstraints)
                                // Rate Minimization config
                                .setMinimumRuptureRateFraction(minimumRuptureRateFraction)
                                .setMinimumRuptureRateBasis(minimumRuptureRateBasis)
                                .setInitialRupModel(initialRupModel);

        // ExcludeMinMag is handled in the runner. if that's used, do not use old-fashioned
        // constraint
        if (!excludeMinMag) {
            newConfig.setMinimizationConstraintWt(minimizationConstraintWt);
        }

        if (mfdUncertWtdConstraintWt > 0.0) {
            newConfig
                    .setMagnitudeUncertaintyWeightedConstraintWt(mfdUncertWtdConstraintWt)
                    .setMfdUncertaintyWeightedConstraints(
                            inversionMFDs.getMfdUncertaintyConstraints());
        }

        return newConfig;
    }

    public NZSHM22_CrustalInversionConfiguration setPaleoRateConstraintWt(
            double paleoRateConstraintWt) {
        this.paleoRateConstraintWt = paleoRateConstraintWt;
        return this;
    }

    public double getPaleoRateConstraintWt() {
        return paleoRateConstraintWt;
    }

    public NZSHM22_CrustalInversionConfiguration setpaleoParentRateSmoothnessConstraintWeight(
            double paleoParentRateSmoothnessConstraintWeight) {
        this.mfdSmoothnessConstraintWtForPaleoParents = paleoParentRateSmoothnessConstraintWeight;
        return this;
    }

    public double getpaleoParentRateSmoothnessConstraintWeight() {
        return mfdSmoothnessConstraintWtForPaleoParents;
    }
}

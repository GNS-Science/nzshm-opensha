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

        double[] initialRupModel = null;
        double[] minimumRuptureRateBasis = null;

        if (model.isConstrained()) {
            // CONSTRAINED BRANCHES
            if (model == InversionModels.CHAR_CONSTRAINED) {

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

        // NSHM-style config using setter methods...
        NZSHM22_CrustalInversionConfiguration newConfig =
                (NZSHM22_CrustalInversionConfiguration)
                        new NZSHM22_CrustalInversionConfiguration()
                                .setInversionTargetMfds(inversionMFDs)
                                // MFD config
                                .setMagnitudeEqualityConstraintWt(mfdEqualityConstraintWt)
                                .setMagnitudeInequalityConstraintWt(mfdInequalityConstraintWt)
                                // Slip Rate config
                                .setSlipRateConstraintWt_normalized(
                                        SLIP_WEIGHT_CONSTRAINT_WT_NORMALIZED_DEFAULT)
                                .setSlipRateConstraintWt_unnormalized(
                                        SLIP_WEIGHT_CONSTRAINT_WT_UNNORMALIZED_DEFAULT)
                                .setSlipRateWeightingType(SLIP_RATE_WEIGHTING_DEFAULT)
                                .setMfdEqualityConstraints(mfdEqualityConstraints)
                                .setMfdInequalityConstraints(mfdInequalityConstraints)
                                // Rate Minimization config
                                .setMinimumRuptureRateFraction(
                                        MINIMUM_RUPTURE_RATE_FRACTION_DEFAULT)
                                .setMinimumRuptureRateBasis(minimumRuptureRateBasis)
                                .setInitialRupModel(initialRupModel)
                                .setMfdConstraints(
                                        inversionMFDs.getMFD_Constraints(),
                                        mfdEqualityConstraintWt,
                                        mfdInequalityConstraintWt,
                                        mfdUncertWtdConstraintWt,
                                        mfdTransitionMag,
                                        inversionMFDs.getMfdUncertaintyConstraints());

        // ExcludeMinMag is handled in the runner. if that's used, do not use old-fashioned
        // constraint
        if (!excludeMinMag) {
            newConfig.setMinimizationConstraintWt(MINIMIZATION_CONSTRAINT_WT_DEFAULT);
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

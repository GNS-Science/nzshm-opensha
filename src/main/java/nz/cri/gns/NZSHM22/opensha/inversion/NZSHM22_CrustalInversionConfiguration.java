package nz.cri.gns.NZSHM22.opensha.inversion;

import nz.cri.gns.NZSHM22.opensha.data.region.NewZealandRegions;
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
     * @param totalRateM5_Sans
     * @param totalRateM5_TVZ
     * @param bValue_Sans
     * @param bValue_TVZ
     * @param mfdUncertWtdConstraintScalar TODO
     * @return
     */
    public static NZSHM22_CrustalInversionConfiguration forModel(
            NZSHM22_CrustalInversionRunner runner,
            InversionModels model,
            NZSHM22_InversionFaultSystemRuptSet rupSet,
            double[] initialSolution,
            double totalRateM5_Sans,
            double totalRateM5_TVZ,
            double bValue_Sans,
            double bValue_TVZ,
            double mMin_Sans,
            double mMin_TVZ,
            double maxMagSans,
            double maxMagTVZ,
            double mfdUncertWtdConstraintPower,
            double mfdUncertWtdConstraintScalar) {

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

        return (NZSHM22_CrustalInversionConfiguration)
                new NZSHM22_CrustalInversionConfiguration()
                        .setInversionTargetMfds(inversionMFDs)
                        .initialiseFromRunner(
                                runner,
                                model,
                                inversionMFDs.getMFD_Constraints(),
                                inversionMFDs.getMfdUncertaintyConstraints(),
                                initialSolution,
                                null);
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

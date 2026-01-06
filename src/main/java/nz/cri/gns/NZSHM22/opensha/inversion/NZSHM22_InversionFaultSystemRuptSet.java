package nz.cri.gns.NZSHM22.opensha.inversion;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;
import nz.cri.gns.NZSHM22.opensha.analysis.NZSHM22_FaultSystemRupSetCalc;
import nz.cri.gns.NZSHM22.opensha.data.region.NewZealandRegions;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.*;
import nz.cri.gns.NZSHM22.opensha.griddedSeismicity.NZSHM22_FaultPolyMgr;
import nz.cri.gns.NZSHM22.opensha.ruptures.CustomFaultModel;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.util.io.archive.ArchiveInput;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.*;
import scratch.UCERF3.inversion.InversionFaultSystemRupSet;

/**
 * This class provides specialisatations needed to override some UCERF3 defaults in the base class.
 *
 * @author chrisbc
 */
public class NZSHM22_InversionFaultSystemRuptSet extends InversionFaultSystemRupSet {

    private static final long serialVersionUID = 1091962054533163866L;

    protected NZSHM22_LogicTreeBranch branch;
    protected RegionalRupSetData sansTvz;
    protected RegionalRupSetData tvz;
    boolean[] isRupBelowMinMagsForSects;

    private NZSHM22_InversionFaultSystemRuptSet(
            FaultSystemRupSet rupSet, NZSHM22_LogicTreeBranch branch) {
        super(rupSet, branch.getU3Branch());
        init(branch);
    }

    /**
     * Loads a subduction RuptureSet from file. Strips the RuptureSet of stray U3 modules that are
     * added when loading pre-modular files. Recalculates magnitudes if specified by the LTB.
     *
     * @param ruptureSetFile
     * @param branch
     * @return
     * @throws IOException
     */
    public static NZSHM22_InversionFaultSystemRuptSet loadSubductionRuptureSet(
            File ruptureSetFile, NZSHM22_LogicTreeBranch branch) throws IOException {
        FaultSystemRupSet rupSet = FaultSystemRupSet.load(ruptureSetFile);
        return fromExistingSubductionRuptureSet(rupSet, branch);
    }

    /**
     * Loads a subduction RuptureSet from file. Strips the RuptureSet of stray U3 modules that are
     * added when loading pre-modular files. Recalculates magnitudes if specified by the LTB.
     *
     * @param ruptureSetInput
     * @param branch
     * @return
     * @throws IOException
     */
    public static NZSHM22_InversionFaultSystemRuptSet loadSubductionRuptureSet(
            ArchiveInput ruptureSetInput, NZSHM22_LogicTreeBranch branch) throws IOException {
        FaultSystemRupSet rupSet = FaultSystemRupSet.load(ruptureSetInput);
        return fromExistingSubductionRuptureSet(rupSet, branch);
    }

    public static NZSHM22_InversionFaultSystemRuptSet fromExistingSubductionRuptureSet(
            FaultSystemRupSet rupSet, NZSHM22_LogicTreeBranch branch) {
        rupSet = recalcMags(rupSet, branch);
        return new NZSHM22_InversionFaultSystemRuptSet(rupSet, branch);
    }

    protected static NZSHM22_FaultPolyMgr faultPolyMgr(
            FaultSystemRupSet rupSet, NZSHM22_LogicTreeBranch branch) {
        NZSHM22_FaultPolyParameters parameters = branch.getValue(NZSHM22_FaultPolyParameters.class);
        if (parameters == null) {
            parameters = new NZSHM22_FaultPolyParameters();
            branch.setValue(parameters);
        }
        return NZSHM22_FaultPolyMgr.create(
                rupSet.getFaultSectionDataList(),
                parameters.getBufferSize(),
                parameters.getMinBufferSize(),
                new NewZealandRegions.NZ_RECTANGLE_GRIDDED());
    }

    /**
     * Loads a RuptureSet from file. Strips the RuptureSet of stray U3 modules that are added when
     * loading pre-modular files. Recalculates magnitudes if specified by the LTB.
     *
     * @param ruptureSetFile
     * @param branch
     * @return
     * @throws IOException
     */
    public static NZSHM22_InversionFaultSystemRuptSet loadCrustalRuptureSet(
            File ruptureSetFile, NZSHM22_LogicTreeBranch branch) throws IOException {
        return fromExistingCrustalSet(FaultSystemRupSet.load(ruptureSetFile), branch);
    }

    /**
     * Loads a RuptureSet from file. Strips the RuptureSet of stray U3 modules that are added when
     * loading pre-modular files. Recalculates magnitudes if specified by the LTB.
     *
     * @param rupSetInput
     * @param branch
     * @return
     * @throws IOException
     */
    public static NZSHM22_InversionFaultSystemRuptSet loadCrustalRuptureSet(
            ArchiveInput rupSetInput, NZSHM22_LogicTreeBranch branch) throws IOException {
        return fromExistingCrustalSet(FaultSystemRupSet.load(rupSetInput), branch);
    }

    public static NZSHM22_InversionFaultSystemRuptSet fromExistingCrustalSet(
            FaultSystemRupSet rupSet, NZSHM22_LogicTreeBranch branch) throws IOException {
        ClusterRuptures ruptures = rupSet.getModule(ClusterRuptures.class);
        if (ruptures == null) {
            ruptures = ClusterRuptures.singleStranged(rupSet);
            rupSet.addModule(ruptures);
        }
        rupSet = recalcMags(rupSet, branch);
        return new NZSHM22_InversionFaultSystemRuptSet(rupSet, branch);
    }

    protected static void applySlipRateFactor(
            FaultSystemRupSet rupSet, NZSHM22_LogicTreeBranch branch) {
        NZSHM22_SlipRateFactors factors = branch.getValue(NZSHM22_SlipRateFactors.class);
        if (factors == null || (factors.getSansFactor() < 0 && factors.getTvzFactor() < 0)) {
            return;
        }
        TvzDomainSections tvzSections = rupSet.getModule(TvzDomainSections.class);
        SectSlipRates origSlips = rupSet.getModule(SectSlipRates.class);
        double[] slipRates = origSlips.getSlipRates();

        if (factors.getTvzFactor() >= 0) {
            for (int i = 0; i < slipRates.length; i++) {
                if (tvzSections.isInRegion(i)) {
                    slipRates[i] *= factors.getTvzFactor();
                }
            }
        }

        if (factors.getSansFactor() >= 0) {
            for (int i = 0; i < slipRates.length; i++) {
                if (!tvzSections.isInRegion(i)) {
                    slipRates[i] *= factors.getSansFactor();
                }
            }
        }

        rupSet.addModule(
                SectSlipRates.precomputed(rupSet, slipRates, origSlips.getSlipRateStdDevs()));
    }

    /**
     * Returns a new RuptureSet with recalculated magnitudes.
     *
     * @return
     */
    public static FaultSystemRupSet recalcMags(
            FaultSystemRupSet rupSet, NZSHM22_LogicTreeBranch branch) {
        NZSHM22_ScalingRelationshipNode scaling =
                branch.getValue(NZSHM22_ScalingRelationshipNode.class);
        if (scaling != null && scaling.getReCalc()) {
            return FaultSystemRupSet.buildFromExisting(rupSet)
                    .forScalingRelationship(scaling)
                    .build();
        } else {
            return rupSet;
        }
    }

    protected void applyDeformationModel(NZSHM22_LogicTreeBranch branch) {
        NZSHM22_DeformationModel model = branch.getValue(NZSHM22_DeformationModel.class);
        if (model != null) {
            model.applyTo(this, (sectionId) -> true);
        }
    }

    private void initLogicTreeBranch(NZSHM22_LogicTreeBranch branch) {
        NZSHM22_LogicTreeBranch originalBranch = getModule(NZSHM22_LogicTreeBranch.class);
        if (originalBranch != null) {
            NZSHM22_FaultModels faultModel = originalBranch.getValue(NZSHM22_FaultModels.class);
            if (faultModel != null) {
                branch.setValue(faultModel);
            }
            NZSHM22_ScalingRelationshipNode scaling =
                    originalBranch.getValue(NZSHM22_ScalingRelationshipNode.class);
            if (branch.getValue(NZSHM22_ScalingRelationshipNode.class) == null && scaling != null) {
                branch.setValue(scaling);
            }
        }
        removeModuleInstances(LogicTreeBranch.class);
        addModule(branch);
        this.branch = branch;
    }

    private void init(NZSHM22_LogicTreeBranch branch) {

        initLogicTreeBranch(branch);

        CustomFaultModel customFaultModel = getModule(CustomFaultModel.class);
        if (customFaultModel != null) {
            NZSHM22_FaultModels faultModel = branch.getValue(NZSHM22_FaultModels.class);
            faultModel.setCustomModel(customFaultModel.getModelData());
        }

        // overwrite behaviour of super class
        removeModuleInstances(FaultGridAssociations.class);
        removeModuleInstances(SectSlipRates.class);

        if (branch.hasValue(NZSHM22_ScalingRelationshipNode.class)) {
            addModule(
                    AveSlipModule.forModel(
                            this, branch.getValue(NZSHM22_ScalingRelationshipNode.class)));
        }

        applyDeformationModel(branch);
        removeModuleInstances(SectSlipRates.class);
        SectSlipRates rates = SectSlipRates.fromFaultSectData(this);
        addModule(
                SectSlipRates.precomputed(this, rates.getSlipRates(), rates.getSlipRateStdDevs()));

        FaultRegime regime = branch.getValue(FaultRegime.class);

        // oakley: this looks like a garbage module with default values that will be overwritten
        // later

        //        if (regime == FaultRegime.SUBDUCTION) {
        //            addAvailableModule(
        //                    new Callable<NZSHM22_SubductionInversionTargetMFDs>() {
        //                        @Override
        //                        public NZSHM22_SubductionInversionTargetMFDs call() throws
        // Exception {
        //                            return new NZSHM22_SubductionInversionTargetMFDs(
        //                                    NZSHM22_InversionFaultSystemRuptSet.this);
        //                        }
        //                    },
        //                    NZSHM22_SubductionInversionTargetMFDs.class);
        //
        //        } else
        if (regime == FaultRegime.CRUSTAL) {
            // TODO joint: faultpolymgr
            addModule(faultPolyMgr(this, branch));
            if (branch.hasValue(NZSHM22_FaultModels.class)) {
                addModule(new TvzDomainSections(this));
            }
            applySlipRateFactor(this, branch);
        }
    }

    public NZSHM22_InversionFaultSystemRuptSet setInversionTargetMFDs(
            InversionTargetMFDs inversionMFDs) {
        removeModuleInstances(InversionTargetMFDs.class);
        addModule(inversionMFDs);
        return this;
    }

    public NZSHM22_InversionFaultSystemRuptSet setRegionalData(
            RegionalRupSetData tvz, RegionalRupSetData sansTvz) {
        this.tvz = tvz;
        this.sansTvz = sansTvz;

        double[] minMags = new double[getNumSections()];

        for (int s = 0; s < minMags.length; s++) {
            if (tvz.isInRegion(s)) {
                minMags[s] = tvz.getMinMagForOriginalSectionid(s);
            } else {
                minMags[s] = sansTvz.getMinMagForOriginalSectionid(s);
            }
        }

        if (hasAvailableModule(ModSectMinMags.class)) {
            removeModuleInstances(ModSectMinMags.class);
        }
        addAvailableModule(
                new Callable<ModSectMinMags>() {
                    @Override
                    public ModSectMinMags call() throws Exception {
                        return ModSectMinMags.instance(
                                NZSHM22_InversionFaultSystemRuptSet.this, minMags);
                    }
                },
                ModSectMinMags.class);

        return this;
    }

    public RegionalRupSetData getTvzRegionalData() {
        return tvz;
    }

    public RegionalRupSetData getSansTvzRegionalData() {
        return sansTvz;
    }

    /**
     * This tells whether the given rup is below any of the final minimum magnitudes of the sections
     * utilized by the rup. Actually, the test is really whether the mag falls below the lower bin
     * edge implied by the section min mags; see doc for computeWhichRupsFallBelowSectionMinMags().
     *
     * @param rupIndex
     * @return
     */
    @Override
    public synchronized boolean isRuptureBelowSectMinMag(int rupIndex) {
        if (isRupBelowMinMagsForSects == null) {
            ModSectMinMags minMagsModule = getModule(ModSectMinMags.class);
            isRupBelowMinMagsForSects =
                    NZSHM22_FaultSystemRupSetCalc.computeWhichRupsFallBelowSectionMinMags(
                            this, minMagsModule);
        }
        return isRupBelowMinMagsForSects[rupIndex];
    }

    @Override
    public double getUpperMagForSubseismoRuptures(int sectIndex) {
        throw new RuntimeException("Not supported, don't use this!");
    }

    /**
     * Asserts that all fault sections have an MFD associated with them. This is to weed out
     * solutions generated with code affected by #377 where sections in the TVZ were excluded from
     * MFD generation.
     */
    public static void checkMFDConsistency(FaultSystemRupSet rupSet) {
        int mfdSize = rupSet.getModule(InversionTargetMFDs.class).getOnFaultSubSeisMFDs().size();
        if (mfdSize != rupSet.getNumSections()) {
            throw new RuntimeException(
                    "This solution was created with a faulty nzshm-opensha version. See issue #377.");
        }
    }
}

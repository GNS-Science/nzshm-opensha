package nz.cri.gns.NZSHM22.opensha.inversion.joint;

import java.io.IOException;
import java.util.function.IntPredicate;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_LogicTreeBranch;
import nz.cri.gns.NZSHM22.opensha.ruptures.CustomFaultModel;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.AveSlipModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.FaultGridAssociations;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;

public class RuptureSetSetup {

    public static FaultSystemRupSet recalcMags(Config config) {
        if (config.scalingRelationship != null && config.recalcMags) {
            return FaultSystemRupSet.buildFromExisting(config.ruptureSet)
                    .forScalingRelationship(config.scalingRelationship)
                    .build();
        }
        return config.ruptureSet;
    }

    protected static void applyDeformationModel(Config config) {

        FaultSystemRupSet ruptureSet = config.ruptureSet;
        for (PartitionConfig partition : config.partitions) {
            partition.deformationModel.applyTo(ruptureSet, partition.partitionPredicate);
        }
        SectSlipRates rates = SectSlipRates.fromFaultSectData(ruptureSet);
        ruptureSet.addModule(
                SectSlipRates.precomputed(
                        ruptureSet, rates.getSlipRates(), rates.getSlipRateStdDevs()));
    }

    protected static void applySlipRateFactor(
            PartitionPredicate region, double slipRateFactor, FaultSystemRupSet rupSet) {
        if (slipRateFactor < 0) {
            return;
        }
        IntPredicate inRegion = region.getPredicate(rupSet);
        SectSlipRates origSlips = rupSet.getModule(SectSlipRates.class);
        double[] slipRates = origSlips.getSlipRates();

        for (int i = 0; i < slipRates.length; i++) {
            if (inRegion.test(i)) {
                slipRates[i] *= slipRateFactor;
            }
        }
        rupSet.addModule(
                SectSlipRates.precomputed(rupSet, slipRates, origSlips.getSlipRateStdDevs()));
    }

    public static void setup(Config config) throws IOException {

        config.ruptureSet = recalcMags(config);

        // shortcut
        FaultSystemRupSet ruptureSet = config.ruptureSet;

        ruptureSet.removeModuleInstances(FaultGridAssociations.class);
        ruptureSet.removeModuleInstances(SectSlipRates.class);
        ruptureSet.removeModuleInstances(AveSlipModule.class);

        // TODO: do we actually need a fault model? does this make sense for joint ruptures?
        CustomFaultModel customFaultModel = ruptureSet.getModule(CustomFaultModel.class);
        if (customFaultModel != null) {
            NZSHM22_LogicTreeBranch ltb = ruptureSet.getModule(NZSHM22_LogicTreeBranch.class);
            NZSHM22_FaultModels faultModel = ltb.getValue(NZSHM22_FaultModels.class);
            faultModel.setCustomModel(customFaultModel.getModelData());
        }

        ruptureSet.addModule(AveSlipModule.forModel(ruptureSet, config.scalingRelationship));

        applyDeformationModel(config);

        applySlipRateFactor(PartitionPredicate.TVZ, config.tvzSlipRateFactor, ruptureSet);
        applySlipRateFactor(PartitionPredicate.SANS_TVZ, config.sansSlipRateFactor, ruptureSet);
    }
}

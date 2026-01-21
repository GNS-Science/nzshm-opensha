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
import org.opensha.sha.faultSurface.FaultSection;

public class RuptureSetSetup {

    public static void applyScalingRelationship(Config config) {
        if (config.scalingRelationship != null && config.recalcMags) {
            double[] mags = config.ruptureSet.getMagForAllRups();
            double[] aveSlips = new double[mags.length];
            IntPredicate isCrustal = PartitionPredicate.CRUSTAL.getPredicate(config.ruptureSet);
            for (int rupture = 0; rupture < mags.length; rupture++) {
                double aveRake = config.ruptureSet.getAveRakeForRup(rupture);
                double crustalArea = 0;
                double subductionArea = 0;
                for (FaultSection section :
                        config.ruptureSet.getFaultSectionDataForRupture(rupture)) {
                    if (isCrustal.test(section.getSectionId())) {
                        crustalArea += section.getArea(true);
                    } else {
                        subductionArea += section.getArea(true);
                    }
                }
                mags[rupture] =
                        config.scalingRelationship.getMag(crustalArea, subductionArea, aveRake);
                aveSlips[rupture] =
                        config.scalingRelationship.getAveSlip(crustalArea, subductionArea, aveRake);
            }
            config.ruptureSet.removeModuleInstances(AveSlipModule.class);
            config.ruptureSet.addModule(AveSlipModule.precomputed(config.ruptureSet, aveSlips));
        }
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

    /**
     * Sets up the various required modules of the rupture set
     *
     * @param config
     * @throws IOException
     */
    public static void setup(Config config) throws IOException {

        FaultSystemRupSet ruptureSet = config.ruptureSet;
        ruptureSet.removeModuleInstances(FaultGridAssociations.class);
        ruptureSet.removeModuleInstances(SectSlipRates.class);

        applyScalingRelationship(config);

        // TODO: do we actually need a fault model? does this make sense for joint ruptures?
        CustomFaultModel customFaultModel = ruptureSet.getModule(CustomFaultModel.class);
        if (customFaultModel != null) {
            NZSHM22_LogicTreeBranch ltb = ruptureSet.getModule(NZSHM22_LogicTreeBranch.class);
            NZSHM22_FaultModels faultModel = ltb.getValue(NZSHM22_FaultModels.class);
            faultModel.setCustomModel(customFaultModel.getModelData());
        }

        applyDeformationModel(config);

        applySlipRateFactor(PartitionPredicate.TVZ, config.tvzSlipRateFactor, ruptureSet);
        applySlipRateFactor(PartitionPredicate.SANS_TVZ, config.sansSlipRateFactor, ruptureSet);
    }
}

package nz.cri.gns.NZSHM22.opensha.inversion.joint;

import java.io.IOException;
import java.util.function.IntPredicate;
import nz.cri.gns.NZSHM22.opensha.analysis.NZSHM22_FaultSystemRupSetCalc;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_LogicTreeBranch;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.scaling.JointScalingRelationship;
import nz.cri.gns.NZSHM22.opensha.ruptures.CustomFaultModel;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.AveSlipModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.FaultGridAssociations;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModSectMinMags;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import org.opensha.sha.faultSurface.FaultSection;

public class RuptureSetSetup {

    public static void applyScalingRelationship(
            FaultSystemRupSet ruptureSet,
            JointScalingRelationship scalingRelationship,
            boolean recalcMags) {
        if (scalingRelationship != null && recalcMags) {
            double[] mags = ruptureSet.getMagForAllRups();
            double[] aveSlips = new double[mags.length];
            IntPredicate isCrustal = PartitionPredicate.CRUSTAL.getPredicate(ruptureSet);
            for (int rupture = 0; rupture < mags.length; rupture++) {
                double aveRake = ruptureSet.getAveRakeForRup(rupture);
                double crustalArea = 0;
                double subductionArea = 0;
                for (FaultSection section : ruptureSet.getFaultSectionDataForRupture(rupture)) {
                    if (isCrustal.test(section.getSectionId())) {
                        crustalArea += section.getArea(true);
                    } else {
                        subductionArea += section.getArea(true);
                    }
                }
                mags[rupture] = scalingRelationship.getMag(crustalArea, subductionArea, aveRake);
                aveSlips[rupture] =
                        scalingRelationship.getAveSlip(crustalArea, subductionArea, aveRake);
            }
            ruptureSet.removeModuleInstances(AveSlipModule.class);
            ruptureSet.addModule(AveSlipModule.precomputed(ruptureSet, aveSlips));
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

    protected static void createModSectMinMags(Config config) {
        double[] minMags = NZSHM22_FaultSystemRupSetCalc.computeMinSeismoMagForSections(config);
        ModSectMinMags modSectMinMags = ModSectMinMags.instance(config.ruptureSet, minMags);
        config.ruptureSet.addModule(modSectMinMags);
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
        ruptureSet.removeModuleInstances(ModSectMinMags.class);

        applyScalingRelationship(config.ruptureSet, config.scalingRelationship, config.recalcMags);

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

        createModSectMinMags(config);
    }
}

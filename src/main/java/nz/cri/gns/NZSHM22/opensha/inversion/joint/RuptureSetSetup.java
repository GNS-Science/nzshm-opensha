package nz.cri.gns.NZSHM22.opensha.inversion.joint;

import java.io.File;
import java.io.IOException;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_DeformationModel;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_LogicTreeBranch;
import nz.cri.gns.NZSHM22.opensha.ruptures.CustomFaultModel;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.FaultGridAssociations;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;

public class RuptureSetSetup {

    protected static void createSectSlipRates(
            FaultSystemRupSet ruptureSet, NZSHM22_DeformationModel deformationModel) {
        if (deformationModel == null || !deformationModel.applyTo(ruptureSet)) {
            SectSlipRates rates = SectSlipRates.fromFaultSectData(ruptureSet);
            ruptureSet.addModule(
                    SectSlipRates.precomputed(
                            ruptureSet, rates.getSlipRates(), rates.getSlipRateStdDevs()));
        }
    }

    public static void setup(Config config) throws IOException {

        if (config.ruptureSet == null && config.ruptureSetPath != null) {
            config.ruptureSet = FaultSystemRupSet.load(new File(config.ruptureSetPath));
        }

        // shortcut
        FaultSystemRupSet ruptureSet = config.ruptureSet;

        ruptureSet.removeModuleInstances(FaultGridAssociations.class);
        ruptureSet.removeModuleInstances(SectSlipRates.class);

        CustomFaultModel customFaultModel = ruptureSet.getModule(CustomFaultModel.class);
        if (customFaultModel != null) {
            NZSHM22_LogicTreeBranch ltb = ruptureSet.getModule(NZSHM22_LogicTreeBranch.class);
            NZSHM22_FaultModels faultModel = ltb.getValue(NZSHM22_FaultModels.class);
            faultModel.setCustomModel(customFaultModel.getModelData());
        }

        createSectSlipRates(ruptureSet, config.deformationModel);
    }
}

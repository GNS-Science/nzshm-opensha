package nz.cri.gns.NZSHM22.opensha.inversion.joint;

import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_DeformationModel;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
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

    public static void setup(Config config) {

        createSectSlipRates(config.ruptureSet, config.deformationModel);
    }
}

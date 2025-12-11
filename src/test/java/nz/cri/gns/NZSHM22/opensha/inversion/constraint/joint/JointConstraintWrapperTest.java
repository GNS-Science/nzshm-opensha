package nz.cri.gns.NZSHM22.opensha.inversion.constraint.joint;

import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_ScalingRelationshipNode;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.constraints.ConstraintRegionConfig;
import org.dom4j.DocumentException;
import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.ConstraintWeightingType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.SlipRateInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.modules.AveSlipModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import org.opensha.sha.earthquake.faultSysSolution.modules.SlipAlongRuptureModel;
import org.opensha.sha.earthquake.faultSysSolution.util.SlipAlongRuptureModelBranchNode;
import org.opensha.sha.faultSurface.FaultSection;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;

import java.io.IOException;
import java.util.List;

import static nz.cri.gns.NZSHM22.opensha.util.TestHelpers.createRupSet;

public class JointConstraintWrapperTest {

    static final int CRU_SECTION = 10;
    static final int SUB_SECTION = 20;

    public FaultSystemRupSet makeRupSet() throws DocumentException, IOException {
        FaultSystemRupSet rupSet = createRupSet(NZSHM22_FaultModels.CFM_1_0A_DOM_ALL, NZSHM22_ScalingRelationshipNode.createRelationShip("TMG_CRU_2017"),
                List.of(List.of(CRU_SECTION), List.of(SUB_SECTION), List.of(CRU_SECTION, SUB_SECTION)));

        FaultSection cruSection = rupSet.getFaultSectionData(CRU_SECTION);
        FaultSection subSection = rupSet.getFaultSectionData(SUB_SECTION);
        subSection.setSectionName("row: 1");

        AveSlipModule aveSlip = AveSlipModule.precomputed(rupSet, new double[]{1,2});
        rupSet.addModule(aveSlip);
        rupSet.addModule(SlipAlongRuptureModels.UNIFORM.getModel());
        SectSlipRates targets = SectSlipRates.precomputed(rupSet, new double[]{.1, .2}, new double[]{0.01, 0.01});
        rupSet.addModule(targets);
        return rupSet;

    }

    @Test
    public void encodeTest() throws DocumentException, IOException {
        FaultSystemRupSet rupSet = makeRupSet();
        ConstraintRegionConfig  cruConfig = new ConstraintRegionConfig(List.of(CRU_SECTION));
        ConstraintRegionConfig subConfig = new ConstraintRegionConfig(List.of(SUB_SECTION));

        SlipRateInversionConstraint slipConstraint = new SlipRateInversionConstraint(
                0.5,
                ConstraintWeightingType.UNNORMALIZED,
                rupSet);

        

    }
}

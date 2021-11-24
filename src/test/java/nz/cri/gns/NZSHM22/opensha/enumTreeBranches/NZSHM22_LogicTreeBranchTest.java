package nz.cri.gns.NZSHM22.opensha.enumTreeBranches;

import static org.junit.Assert.*;

import nz.cri.gns.NZSHM22.opensha.calc.SimplifiedScalingRelationship;
import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;
import scratch.UCERF3.logicTree.U3LogicTreeBranch;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

public class NZSHM22_LogicTreeBranchTest {

    public FaultSystemRupSet oldSchoolRupSet() throws URISyntaxException, IOException {
        URL alpineVernonRupturesUrl = Thread.currentThread().getContextClassLoader().getResource("AlpineVernonInversionSolution.zip");
        return FaultSystemSolution.load(new File(alpineVernonRupturesUrl.toURI())).getRupSet();
    }

    public FaultSystemRupSet modularRupSet() throws URISyntaxException, IOException {
        URL alpineVernonRupturesUrl = Thread.currentThread().getContextClassLoader().getResource("ModularAlpineVernonInversionSolution.zip");
        return FaultSystemSolution.load(new File(alpineVernonRupturesUrl.toURI())).getRupSet();
    }

    @Test
    public void testJSON() throws IOException {
        NZSHM22_LogicTreeBranch branch = new NZSHM22_LogicTreeBranch();

        branch.setValue(SlipAlongRuptureModels.UNIFORM);
        SimplifiedScalingRelationship scale = new SimplifiedScalingRelationship();
        scale.setupCrustal(4.0, 4.1);
        branch.setValue(new NZSHM22_ScalingRelationshipNode(scale));
        branch.setValue(NZSHM22_SpatialSeisPDF.NZSHM22_1346);
        branch.setValue(FaultRegime.SUBDUCTION);
        branch.setValue(NZSHM22_DeformationModel.GEOD_NO_PRIOR_UNISTD_2010_RmlsZTo4NTkuMDM2Z2Rw);

        String json = branch.getJSON();

        NZSHM22_LogicTreeBranch branch2 = new NZSHM22_LogicTreeBranch();
        branch2.initFromJSON(json);

        assertEquals(branch, branch2);

        String json2 = branch2.getJSON();

        assertEquals(json, json2);
    }

    @Test
    public void testFromOldSchool() throws URISyntaxException, IOException {
        FaultSystemRupSet rupSet = oldSchoolRupSet();
        U3LogicTreeBranch originalBranch = rupSet.getModule(U3LogicTreeBranch.class);
        NZSHM22_LogicTreeBranch nzBranch  = NZSHM22_LogicTreeBranch.crustalFromModuleContainer(rupSet);

        // asserting that an NZ LTB is created with some of the original values copied across

        assertEquals(
                originalBranch.getValue(ScalingRelationships.class),
                nzBranch.getValue(NZSHM22_ScalingRelationshipNode.class).getScalingRelationship());

        assertEquals(
                originalBranch.getValue(SlipAlongRuptureModels.class),
                nzBranch.getValue(SlipAlongRuptureModels.class));

        assertEquals(FaultRegime.CRUSTAL, nzBranch.getValue(FaultRegime.class));
    }

    @Test
    public void testFromModular() throws URISyntaxException, IOException {
        FaultSystemRupSet rupSet = modularRupSet();

        NZSHM22_LogicTreeBranch branch = rupSet.getModule(NZSHM22_LogicTreeBranch.class);

        assertEquals(FaultRegime.CRUSTAL, branch.getValue(FaultRegime.class));
        assertEquals(NZSHM22_SpatialSeisPDF.NZSHM22_1346, branch.getValue(NZSHM22_SpatialSeisPDF.class));
        assertEquals(SlipAlongRuptureModels.UNIFORM, branch.getValue(SlipAlongRuptureModels.class));
        SimplifiedScalingRelationship scaling = new SimplifiedScalingRelationship();
        scaling.setupCrustal(4, 4.1);
        assertEquals(
                scaling,
                branch.getValue(NZSHM22_ScalingRelationshipNode.class).getScalingRelationship());
    }
}

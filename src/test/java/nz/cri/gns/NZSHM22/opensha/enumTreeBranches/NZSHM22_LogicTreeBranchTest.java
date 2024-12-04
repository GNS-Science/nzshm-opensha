package nz.cri.gns.NZSHM22.opensha.enumTreeBranches;

import static nz.cri.gns.NZSHM22.opensha.util.TestHelpers.createRupSetForSections;
import static org.junit.Assert.*;

import nz.cri.gns.NZSHM22.opensha.calc.SimplifiedScalingRelationship;
import nz.cri.gns.NZSHM22.opensha.inversion.NZSHM22_InversionFaultSystemRuptSet;
import nz.cri.gns.NZSHM22.opensha.util.TestHelpers;
import org.dom4j.DocumentException;
import org.junit.Test;
import org.opensha.commons.util.io.archive.ArchiveOutput;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;

import java.io.IOException;

public class NZSHM22_LogicTreeBranchTest {

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

    /**
     * Create a rupture set with crustal LTB, write and read it from file.
     */
    public static FaultSystemRupSet makeRupSet() throws DocumentException, IOException {
        NZSHM22_LogicTreeBranch branch = NZSHM22_LogicTreeBranch.crustalInversion();
        NZSHM22_ScalingRelationshipNode scalingNode = branch.getValue(NZSHM22_ScalingRelationshipNode.class);
        FaultSystemRupSet rupSet = TestHelpers.makeRupSet(NZSHM22_FaultModels.CFM_1_0A_DOM_ALL, scalingNode);
        rupSet = NZSHM22_InversionFaultSystemRuptSet.fromExistingCrustalSet(rupSet, branch);
        ArchiveOutput.InMemoryZipOutput output = new ArchiveOutput.InMemoryZipOutput(true);
        rupSet.getArchive().write(output);
        return FaultSystemRupSet.load(output.getCompletedInput());
    }

    @Test
    public void testFromModular() throws IOException, DocumentException {
        NZSHM22_LogicTreeBranch branch = makeRupSet().getModule(NZSHM22_LogicTreeBranch.class);

        assertEquals(FaultRegime.CRUSTAL, branch.getValue(FaultRegime.class));
        assertEquals(NZSHM22_SpatialSeisPDF.NZSHM22_1346, branch.getValue(NZSHM22_SpatialSeisPDF.class));
        assertEquals(SlipAlongRuptureModels.UNIFORM, branch.getValue(SlipAlongRuptureModels.class));
        SimplifiedScalingRelationship scaling = new SimplifiedScalingRelationship();
        scaling.setupCrustal(4, 4);
        assertTrue(
                scaling.equals(
                        branch.getValue(NZSHM22_ScalingRelationshipNode.class).getScalingRelationship()));
    }
}

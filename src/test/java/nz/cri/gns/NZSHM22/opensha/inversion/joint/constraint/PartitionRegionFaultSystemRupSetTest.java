package nz.cri.gns.NZSHM22.opensha.inversion.joint.constraint;

import static nz.cri.gns.NZSHM22.opensha.util.TestHelpers.createRupSet;
import static org.junit.Assert.*;

import java.io.IOException;
import java.util.List;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_ScalingRelationshipNode;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionPredicate;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.constraints.PartitionRegionFaultSystemRupSet;
import nz.cri.gns.NZSHM22.opensha.ruptures.FaultSectionProperties2;
import org.dom4j.DocumentException;
import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;

public class PartitionRegionFaultSystemRupSetTest {

    static final double DELTA = 0.00000001;

    public FaultSystemRupSet makeRupSet() throws DocumentException, IOException {
        FaultSystemRupSet rupSet =
                createRupSet(
                        NZSHM22_FaultModels.CFM_1_0A_DOM_ALL,
                        NZSHM22_ScalingRelationshipNode.createRelationShip("TMG_CRU_2017"),
                        List.of(
                                List.of(0),
                                List.of(0, 1),
                                List.of(0, 1, 2),
                                List.of(1, 2),
                                List.of(2)));

        rupSet.getFaultSectionDataList().removeIf((s) -> s.getSectionId() > 2);

        FaultSectionProperties2 props = new FaultSectionProperties2(rupSet.getFaultSectionData(0));
        props.setPartition(PartitionPredicate.CRUSTAL);
        props = new FaultSectionProperties2(rupSet.getFaultSectionData(1));
        props.setPartition(PartitionPredicate.CRUSTAL);
        props = new FaultSectionProperties2(rupSet.getFaultSectionData(2));
        props.setPartition(PartitionPredicate.HIKURANGI);

        return rupSet;
    }

    @Test
    public void fractsInRegionTest() throws DocumentException, IOException {
        FaultSystemRupSet originalRupSet = makeRupSet();
        PartitionPredicate partition = PartitionPredicate.CRUSTAL;
        PartitionRegionFaultSystemRupSet toTest =
                new PartitionRegionFaultSystemRupSet(
                        originalRupSet, partition.getPredicate(originalRupSet));

        double area0 = originalRupSet.getAreaForSection(0);
        double area1 = originalRupSet.getAreaForSection(1);

        double[] actual = toTest.getFractRupsInsideRegion(null, false);

        assertEquals(1.0, actual[0], DELTA);
        assertEquals(1.0, actual[1], DELTA);
        assertEquals((area0 + area1) / originalRupSet.getAreaForRup(2), actual[2], DELTA);
        assertEquals(area1 / originalRupSet.getAreaForRup(3), actual[3], DELTA);
        assertEquals(0.0, actual[4], DELTA);
    }

    @Test
    public void minMaxMagTest() throws DocumentException, IOException {
        FaultSystemRupSet originalRupSet = makeRupSet();

        originalRupSet.getMagForAllRups()[0] = 1;
        originalRupSet.getMagForAllRups()[1] = 2;
        originalRupSet.getMagForAllRups()[2] = 3;
        originalRupSet.getMagForAllRups()[3] = 4;
        originalRupSet.getMagForAllRups()[4] = 5;

        PartitionPredicate partition = PartitionPredicate.CRUSTAL;
        PartitionRegionFaultSystemRupSet toTest =
                new PartitionRegionFaultSystemRupSet(
                        originalRupSet, partition.getPredicate(originalRupSet));

        assertEquals(1.0, toTest.getMinMag(), DELTA);
        // rupture 4 with mag 5 is completely outside the partition and not used as max
        assertEquals(4.0, toTest.getMaxMag(), DELTA);
    }
}

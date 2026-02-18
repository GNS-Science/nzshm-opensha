package nz.cri.gns.NZSHM22.opensha.ruptures;

import static nz.cri.gns.NZSHM22.opensha.util.TestHelpers.createRupSetForSections;
import static org.junit.Assert.*;

import java.io.IOException;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionPredicate;
import org.dom4j.DocumentException;
import org.junit.Test;
import org.opensha.commons.util.io.archive.ArchiveOutput;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;

public class FaultSectionPropertiesTest {

    @Test
    public void testReadWrite() throws DocumentException, IOException {
        FaultSystemRupSet rupSet = createRupSetForSections(NZSHM22_FaultModels.CFM_1_0A_DOM_ALL);

        FaultSectionProperties props = new FaultSectionProperties(rupSet.getFaultSectionData(0));

        props.setPartition(PartitionPredicate.CRUSTAL);
        props.setOriginalId(42);
        props.setOriginalParent(4200);
        props.setTvz();

        ArchiveOutput output = new ArchiveOutput.InMemoryZipOutput(false);
        rupSet.writeToArchive(output, FaultSystemRupSet.NESTING_PREFIX);
        output.close();
        rupSet = FaultSystemRupSet.load(output.getCompletedInput());

        props = new FaultSectionProperties(rupSet.getFaultSectionData(0));

        assertEquals(PartitionPredicate.CRUSTAL, props.getPartition());
        assertEquals(42, (int) props.getOriginalId());
        assertEquals(4200, (int) props.getOriginalParent());
        assertTrue(props.getTvz());
    }
}

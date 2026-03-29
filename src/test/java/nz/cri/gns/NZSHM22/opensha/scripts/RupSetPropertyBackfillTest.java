package nz.cri.gns.NZSHM22.opensha.scripts;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.List;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionPredicate;
import nz.cri.gns.NZSHM22.opensha.ruptures.FaultSectionProperties;
import nz.cri.gns.NZSHM22.opensha.util.TestHelpers;
import org.dom4j.DocumentException;
import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.faultSurface.FaultSection;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;

public class RupSetPropertyBackfillTest {

    @Test
    public void testCrustalBackfill() throws DocumentException, IOException {
        NZSHM22_FaultModels model = NZSHM22_FaultModels.CFM_1_0A_DOM_ALL;
        FaultSystemRupSet rupSet =
                TestHelpers.createRupSet(
                        model,
                        ScalingRelationships.SHAW_2009_MOD,
                        List.of(List.of(0, 1, 2), List.of(3, 4, 5)));

        // Write to temp file and reload — properties survive because they're in GeoJSON
        // but we verify backfill can re-populate them
        File tempFile = File.createTempFile("crustal-backfill", ".zip");
        tempFile.deleteOnExit();
        rupSet.write(tempFile);

        FaultSystemRupSet result = RupSetPropertyBackfill.backfill(tempFile.getAbsolutePath());

        for (FaultSection section : result.getFaultSectionDataList()) {
            FaultSectionProperties props = new FaultSectionProperties(section);
            assertEquals(
                    "Section " + section.getSectionName() + " should be CRUSTAL",
                    PartitionPredicate.CRUSTAL,
                    props.getPartition());
            assertNotNull(
                    "Section " + section.getSectionName() + " should have domain",
                    props.getDomain());
        }
    }

    @Test
    public void testSubductionBackfill() throws DocumentException, IOException {
        NZSHM22_FaultModels model = NZSHM22_FaultModels.SBD_0_1_HKR_KRM_30;
        FaultSystemRupSet rupSet =
                TestHelpers.createRupSet(
                        model,
                        ScalingRelationships.TMG_SUB_2017,
                        List.of(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8), List.of(4, 5, 6, 10, 11, 12)));

        // Write and reload to simulate a "legacy" rupture set
        File tempFile = File.createTempFile("subduction-backfill", ".zip");
        tempFile.deleteOnExit();
        rupSet.write(tempFile);

        FaultSystemRupSet result = RupSetPropertyBackfill.backfill(tempFile.getAbsolutePath());

        boolean foundRowCol = false;
        for (FaultSection section : result.getFaultSectionDataList()) {
            FaultSectionProperties props = new FaultSectionProperties(section);
            assertEquals("Subduction section rake should be 90", 90.0, section.getAveRake(), 0.0);
            if (section.getSectionName().contains("Hikurangi")) {
                assertEquals(PartitionPredicate.HIKURANGI, props.getPartition());
            }
            if (props.getRowIndex() != null && props.getColIndex() != null) {
                foundRowCol = true;
                assertTrue("Row index should be >= 0", props.getRowIndex() >= 0);
                assertTrue("Col index should be >= 0", props.getColIndex() >= 0);
            }
        }
        assertTrue("Should have found sections with row/col indices", foundRowCol);

        // All ruptures consist only of subduction sections, so rupture rakes should be 90
        for (int r = 0; r < result.getNumRuptures(); r++) {
            assertEquals("Rupture rake should be 90", 90.0, result.getAveRakeForRup(r), 0.01);
        }

        // Magnitudes should be preserved after rebuild
        for (int r = 0; r < result.getNumRuptures(); r++) {
            assertEquals(rupSet.getMagForRup(r), result.getMagForRup(r), 0.0);
        }
    }
}

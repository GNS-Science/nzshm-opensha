package nz.cri.gns.NZSHM22.opensha.ruptures.downDip;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.faults.FaultSectionList;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionPredicate;
import nz.cri.gns.NZSHM22.opensha.ruptures.FaultSectionProperties;
import org.junit.Test;
import org.opensha.sha.faultSurface.FaultSection;

public class DownDipSubSectBuilderTest {

    @Test
    public void testLoadFromStream() throws IOException {
        NZSHM22_FaultModels model = NZSHM22_FaultModels.SBD_0_1_HKR_KRM_10;
        InputStream in = model.getStream(model.getFileName());
        FaultSectionList sections = new FaultSectionList();
        DownDipSubSectBuilder.loadFromStream(
                sections,
                model.getParentSectionId(),
                model.getName(),
                in,
                PartitionPredicate.HIKURANGI);

        assertEquals(5223, sections.size());

        assertEquals(
                0,
                sections.stream()
                        .mapToInt(s -> new FaultSectionProperties(s).getRowIndex())
                        .min()
                        .getAsInt());
        assertEquals(
                25,
                sections.stream()
                        .mapToInt(s -> new FaultSectionProperties(s).getRowIndex())
                        .max()
                        .getAsInt());
        assertEquals(
                0,
                sections.stream()
                        .mapToInt(s -> new FaultSectionProperties(s).getColIndex())
                        .min()
                        .getAsInt());
        assertEquals(
                333,
                sections.stream()
                        .mapToInt(s -> new FaultSectionProperties(s).getColIndex())
                        .max()
                        .getAsInt());

        FaultSection section = sections.get(0);
        FaultSectionProperties props = new FaultSectionProperties(section);

        assertEquals(0, section.getSectionId());
        assertEquals(model.getParentSectionId(), section.getParentSectionId());
        assertEquals(0, (int) props.getRowIndex());
        assertEquals(0, (int) props.getColIndex());
        assertEquals("Hikurangi,Kermadec 10km; col: 0, row: 0", section.getSectionName());
        assertEquals(4.6281328201293945, section.getAveDip(), 0.00001);
        assertEquals(90.0, section.getAveRake(), 0.00001);
    }
}

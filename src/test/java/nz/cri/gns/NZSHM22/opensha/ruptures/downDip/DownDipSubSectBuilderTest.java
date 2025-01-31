package nz.cri.gns.NZSHM22.opensha.ruptures.downDip;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.faults.FaultSectionList;
import nz.cri.gns.NZSHM22.opensha.ruptures.DownDipFaultSection;
import org.junit.Test;

public class DownDipSubSectBuilderTest {

    @Test
    public void testLoadFromStream() throws IOException {
        NZSHM22_FaultModels model = NZSHM22_FaultModels.SBD_0_1_HKR_KRM_10;
        InputStream in = model.getStream(model.getFileName());
        FaultSectionList sections = new FaultSectionList();
        DownDipSubSectBuilder.loadFromStream(
                sections, model.getParentSectionId(), model.getName(), in);

        assertEquals(5223, sections.size());

        assertEquals(
                0,
                sections.stream()
                        .mapToInt(s -> ((DownDipFaultSection) s).getRowIndex())
                        .min()
                        .getAsInt());
        assertEquals(
                25,
                sections.stream()
                        .mapToInt(s -> ((DownDipFaultSection) s).getRowIndex())
                        .max()
                        .getAsInt());
        assertEquals(
                0,
                sections.stream()
                        .mapToInt(s -> ((DownDipFaultSection) s).getColIndex())
                        .min()
                        .getAsInt());
        assertEquals(
                333,
                sections.stream()
                        .mapToInt(s -> ((DownDipFaultSection) s).getColIndex())
                        .max()
                        .getAsInt());

        DownDipFaultSection section = (DownDipFaultSection) sections.get(0);

        assertEquals(0, section.getSectionId());
        assertEquals(model.getParentSectionId(), section.getParentSectionId());
        assertEquals(0, section.getRowIndex());
        assertEquals(0, section.getColIndex());
        assertEquals("Hikurangi,Kermadec 10km; col: 0, row: 0", section.getSectionName());
        assertEquals(4.6281328201293945, section.getAveDip(), 0.00001);
        assertEquals(90.0, section.getAveRake(), 0.00001);
    }
}

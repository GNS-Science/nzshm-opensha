package nz.cri.gns.NZSHM22.opensha.util;

import static nz.cri.gns.NZSHM22.opensha.util.TestHelpers.createRupSetForSections;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.StringWriter;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import org.dom4j.DocumentException;
import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.faultSurface.FaultSection;

public class FaultSectionCsvWriterTest {

    @Test
    public void testDumpFaultSections() throws DocumentException, IOException {
        FaultSystemRupSet ruptSet = createRupSetForSections(NZSHM22_FaultModels.CFM_1_0A_DOM_ALL);
        StringWriter out = new StringWriter();
        FaultSectionCsvWriter.dumpFaultSections(ruptSet, out);

        String[] lines = out.toString().split("\n");
        assertEquals(FaultSectionCsvWriter.FAULT_SECTION_CSV_HEADER, lines[0]);
        assertEquals(ruptSet.getNumSections() + 1, lines.length);

        FaultSection section = ruptSet.getFaultSectionData(0);
        assertEquals(
                String.join(
                        ",",
                        "0",
                        FaultSectionCsvWriter.quote(section.getSectionName()),
                        "" + section.getAveDip(),
                        "" + section.getAveRake(),
                        "" + section.getAveLowerDepth(),
                        "" + section.getOrigAveUpperDepth(),
                        "" + section.getDipDirection(),
                        "" + section.getAseismicSlipFactor(),
                        "" + section.getCouplingCoeff(),
                        "" + section.getOrigAveSlipRate(),
                        "" + section.getParentSectionId(),
                        FaultSectionCsvWriter.quote(section.getParentSectionName()),
                        "" + section.getOrigSlipRateStdDev()),
                lines[1]);
    }

    @Test
    public void testQuote() {
        assertEquals("", FaultSectionCsvWriter.quote(null));
        assertEquals("Acheron", FaultSectionCsvWriter.quote("Acheron"));
        assertEquals(
                "\"Acheron, Subsection 0\"", FaultSectionCsvWriter.quote("Acheron, Subsection 0"));
        assertEquals("\"say \"\"hi\"\"\"", FaultSectionCsvWriter.quote("say \"hi\""));
    }
}

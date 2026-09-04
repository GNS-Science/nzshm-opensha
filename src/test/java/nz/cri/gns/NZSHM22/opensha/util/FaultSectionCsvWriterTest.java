package nz.cri.gns.NZSHM22.opensha.util;

import static nz.cri.gns.NZSHM22.opensha.util.TestHelpers.createRupSetForSections;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.List;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import org.dom4j.DocumentException;
import org.junit.Test;
import org.opensha.commons.data.CSVFile;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.faultSurface.FaultSection;

public class FaultSectionCsvWriterTest {

    @Test
    public void testToCSV() throws DocumentException, IOException {
        FaultSystemRupSet ruptSet = createRupSetForSections(NZSHM22_FaultModels.CFM_1_0A_DOM_ALL);
        CSVFile<String> csv = FaultSectionCsvWriter.toCSV(ruptSet);

        assertEquals(FaultSectionCsvWriter.HEADER, csv.getLine(0));
        assertEquals(ruptSet.getNumSections() + 1, csv.getNumRows());

        FaultSection section = ruptSet.getFaultSectionData(0);
        assertEquals(
                List.of(
                        "0",
                        section.getSectionName(),
                        "" + section.getAveDip(),
                        "" + section.getAveRake(),
                        "" + section.getAveLowerDepth(),
                        "" + section.getOrigAveUpperDepth(),
                        "" + section.getDipDirection(),
                        "" + section.getAseismicSlipFactor(),
                        "" + section.getCouplingCoeff(),
                        "" + section.getOrigAveSlipRate(),
                        "" + section.getParentSectionId(),
                        section.getParentSectionName(),
                        "" + section.getOrigSlipRateStdDev()),
                csv.getLine(1));
    }

    // names containing a comma are quoted by the opensha CSV code
    @Test
    public void testDumpFaultSections() throws DocumentException, IOException {
        FaultSystemRupSet ruptSet = createRupSetForSections(NZSHM22_FaultModels.CFM_1_0A_DOM_ALL);
        FaultSection section = ruptSet.getFaultSectionData(0);
        section.setSectionName("Acheron, Subsection 0");

        File file = File.createTempFile("faultSections", ".csv");
        file.deleteOnExit();
        FaultSectionCsvWriter.dumpFaultSections(ruptSet, file);

        CSVFile<String> csv = CSVFile.readFile(file, true);
        assertEquals(FaultSectionCsvWriter.HEADER, csv.getLine(0));
        assertEquals(ruptSet.getNumSections() + 1, csv.getNumRows());
        assertEquals("Acheron, Subsection 0", csv.get(1, 1));
    }
}

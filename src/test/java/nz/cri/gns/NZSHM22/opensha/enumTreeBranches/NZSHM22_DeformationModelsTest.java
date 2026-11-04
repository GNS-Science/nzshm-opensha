package nz.cri.gns.NZSHM22.opensha.enumTreeBranches;

import static nz.cri.gns.NZSHM22.opensha.util.TestHelpers.createRupSetForSections;
import static org.junit.Assert.*;

import java.io.*;
import java.util.*;
import org.dom4j.DocumentException;
import org.junit.After;
import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.faultSurface.FaultSection;

public class NZSHM22_DeformationModelsTest {

    static final double DELTA = 1e-9;

    /** The CUSTOM model is a mutable singleton, so we clear it after every test. */
    @After
    public void clearCustomModel() {
        NZSHM22_DeformationModel.CUSTOM.setCustomModel(null);
    }

    /**
     * Creates deformation model CSV data where slip and stdv of each section equal its section id.
     */
    public static String deformationData(FaultSystemRupSet rupSet) {
        StringBuilder builder = new StringBuilder();
        for (int id = 0; id < rupSet.getNumSections(); id++) {
            builder.append(id + "," + id + "," + id + "," + id + "\n");
        }
        return builder.toString();
    }

    @Test
    public void testCustomModel() throws DocumentException, IOException {
        FaultSystemRupSet ruptSet = createRupSetForSections(NZSHM22_FaultModels.CFM_1_0A_DOM_ALL);
        assertEquals(0.2, ruptSet.getFaultSectionData(0).getOrigAveSlipRate(), DELTA);

        NZSHM22_DeformationModel.CUSTOM.setCustomModel(deformationData(ruptSet));
        NZSHM22_DeformationModel.CUSTOM.applyTo(ruptSet, (sectionId) -> true);

        for (FaultSection section : ruptSet.getFaultSectionDataList()) {
            assertEquals(section.getSectionId(), section.getOrigAveSlipRate(), DELTA);
            assertEquals(section.getSectionId(), section.getOrigSlipRateStdDev(), DELTA);
        }
    }

    @Test
    public void testCustomModelFile() throws DocumentException, IOException {
        FaultSystemRupSet ruptSet = createRupSetForSections(NZSHM22_FaultModels.CFM_1_0A_DOM_ALL);
        File file = File.createTempFile("deformationModel", ".csv");
        file.deleteOnExit();
        try (Writer out = new FileWriter(file)) {
            out.write("% a comment\n");
            out.write(deformationData(ruptSet));
        }

        NZSHM22_DeformationModel.CUSTOM.setCustomModelFile(file.getAbsolutePath());
        NZSHM22_DeformationModel.CUSTOM.applyTo(ruptSet, (sectionId) -> true);

        assertEquals(
                deformationData(ruptSet),
                NZSHM22_DeformationModel.CUSTOM.getCustomModel().replace("% a comment\n", ""));
        for (FaultSection section : ruptSet.getFaultSectionDataList()) {
            assertEquals(section.getSectionId(), section.getOrigAveSlipRate(), DELTA);
            assertEquals(section.getSectionId(), section.getOrigSlipRateStdDev(), DELTA);
        }
    }

    // a deformation model that matches no sections is an error
    @Test
    public void testApplyToNoMatches() throws DocumentException, IOException {
        FaultSystemRupSet ruptSet = createRupSetForSections(NZSHM22_FaultModels.CFM_1_0A_DOM_ALL);
        NZSHM22_DeformationModel.CUSTOM.setCustomModel("100000, 100000, 1, 2\n");

        String message = null;
        try {
            NZSHM22_DeformationModel.CUSTOM.applyTo(ruptSet, null);
        } catch (IllegalStateException x) {
            message = x.getMessage();
        }
        assertEquals(
                "Deformation model (custom model) does not match any sections in the rupture set.",
                message);
    }

    // a deformation model that matches no sections within the predicate is an error
    @Test(expected = IllegalStateException.class)
    public void testApplyToNoMatchesInPartition() throws DocumentException, IOException {
        FaultSystemRupSet ruptSet = createRupSetForSections(NZSHM22_FaultModels.CFM_1_0A_DOM_ALL);
        NZSHM22_DeformationModel.CUSTOM.setCustomModel("0, 0, 1, 2\n");
        NZSHM22_DeformationModel.CUSTOM.applyTo(ruptSet, (sectionId) -> sectionId == 1);
    }

    // error messages of deformations loaded from a file carry the source line number
    @Test
    public void testApplyToErrorRowNumber() throws DocumentException, IOException {
        FaultSystemRupSet ruptSet = createRupSetForSections(NZSHM22_FaultModels.CFM_1_0A_DOM_ALL);

        // section 1 has parent id 1, this row claims parent id 0
        NZSHM22_DeformationModel.CUSTOM.setCustomModel(
                "% a comment\n" + "0, 0, 1, 2\n" + "1, 0, 3, 4\n");

        String message = null;
        try {
            NZSHM22_DeformationModel.CUSTOM.applyTo(ruptSet, null);
        } catch (IllegalArgumentException x) {
            message = x.getMessage();
        }
        assertEquals(
                "Section 1 Deformation parent id 0 does not match section parent id 1"
                        + " (deformation model line 3)",
                message);
    }

    // we can parse all deformation models
    @Test
    public void testLoad() {
        for (NZSHM22_DeformationModel model : NZSHM22_DeformationModel.values()) {
            // System.out.println(model.name());
            model.load();
        }
    }

    @Test
    public void testDuplicateFileNames() {
        Set<String> seen = new HashSet<>();
        for (NZSHM22_DeformationModel model : NZSHM22_DeformationModel.values()) {
            // System.out.println(model.name());
            if (model.getFileName() == null) {
                continue;
            }
            assert (!seen.contains(model.getFileName()));
            seen.add(model.getFileName());
        }
    }

    public String read(NZSHM22_DeformationModel model) throws IOException {
        StringBuilder s = new StringBuilder();
        BufferedReader in = new BufferedReader(new InputStreamReader(model.getModel().getStream()));
        String line;
        do {
            line = in.readLine();
            s.append(line);
            s.append('\n');
        } while (line != null);
        return s.toString();
    }

    @Test
    public void testDuplicateFiles() throws IOException {
        Map<String, NZSHM22_DeformationModel> hashes = new HashMap<>();
        for (NZSHM22_DeformationModel model : NZSHM22_DeformationModel.values()) {
            if (model.getFileName() == null) {
                continue;
            }
            System.out.println(model.name());
            String data = read(model);
            assert (!hashes.containsKey(data));
            hashes.put(data, model);
        }
    }
}

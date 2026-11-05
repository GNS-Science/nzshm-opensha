package nz.cri.gns.NZSHM22.opensha.inputs;

import static nz.cri.gns.NZSHM22.opensha.util.TestHelpers.createRupSetForSections;
import static org.junit.Assert.*;

import java.io.*;
import java.util.*;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.inputs.DeformationModel.SlipDeformation;
import nz.cri.gns.NZSHM22.opensha.ruptures.FaultSectionProperties;
import org.dom4j.DocumentException;
import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.faultSurface.FaultSection;

public class DeformationModelTest {

    static final double DELTA = 1e-9;

    @Test
    public void testMetaData() throws IOException {
        InputModelMetaDataTest.writeCached(
                DeformationModel.MODELS_PATH + "metaTest.csv" + InputModel.META_DATA_SUFFIX,
                "{\"description\": \"desc\", \"compatibility\": \"comp\"}");
        DeformationModel model = new DeformationModel("metaTest.csv");
        assertEquals("desc", model.getDescription());
        assertEquals("comp", model.getCompatibility());
        assertFalse(model.isLocal());
    }

    @Test
    public void testLocalFile() throws IOException {
        java.nio.file.Path file =
                java.nio.file.Files.createTempDirectory("deformationModelTest").resolve("dm.csv");
        java.nio.file.Files.writeString(file, "0, 0, 1, 2\n");
        java.nio.file.Files.writeString(
                java.nio.file.Path.of(file + InputModel.META_DATA_SUFFIX),
                "{\"description\": \"local\"}");
        DeformationModel model = new DeformationModel(InputModel.FILE_PREFIX + file);
        assertEquals("local", model.getDescription());
        assertEquals(1, model.getDeformations().get(0).slip, DELTA);
        assertTrue(model.isLocal());
        assertEquals("0, 0, 1, 2\n", model.getData());
    }

    // meta data is optional for local files
    @Test
    public void testLocalFileWithoutMetaData() throws IOException {
        java.nio.file.Path file =
                java.nio.file.Files.createTempDirectory("deformationModelTest").resolve("dm.csv");
        java.nio.file.Files.writeString(file, "0, 0, 1, 2\n");
        DeformationModel model = new DeformationModel(InputModel.FILE_PREFIX + file);
        assertNull(model.getDescription());
        assertNull(model.getCompatibility());
        assertEquals(2, model.getDeformations().get(0).stdv, DELTA);
    }

    // meta data is required for files in the repo
    @Test(expected = IllegalStateException.class)
    public void testRepoFileWithoutMetaData() throws IOException {
        InputModelMetaDataTest.writeCached(DeformationModel.MODELS_PATH + "noMeta.csv", "0,0,1,2");
        new DeformationModel("noMeta.csv").getMetaData();
    }

    @Test
    public void testFromData() {
        DeformationModel model = DeformationModel.fromData("0, 0, 1, 2\n");
        assertNull(model.getPath());
        assertFalse(model.isLocal());
        assertNull(model.getDescription());
        assertEquals("0, 0, 1, 2\n", model.getData());
        assertEquals(1, model.getDeformations().get(0).slip, DELTA);
    }

    // row numbers reflect the actual position in the file, comments included
    @Test
    public void testRowNumbers() {
        DeformationModel helper =
                DeformationModel.fromData(
                        "% a comment\n"
                                + "0, 0, 1, 2\n"
                                + "// another comment\n"
                                + "// and another one\n"
                                + "1, 1, 3, 4\n");

        Map<Integer, SlipDeformation> deformations = helper.getDeformations();

        assertEquals(2, deformations.get(0).rowNum);
        assertEquals(5, deformations.get(1).rowNum);
    }

    // parse errors report the actual line number
    @Test
    public void testParseErrorRowNumber() {
        DeformationModel helper =
                DeformationModel.fromData(
                        "% a comment\n" + "0, 0, 1, 2\n" + "1, 1, not a number, 4\n");

        String message = null;
        try {
            helper.getDeformations();
        } catch (RuntimeException x) {
            message = x.getMessage();
        }
        assertEquals("Error parsing deformation model at line 3", message);
    }

    @Test
    public void testApplyTo() throws DocumentException, IOException {
        FaultSystemRupSet ruptSet = createRupSetForSections(NZSHM22_FaultModels.CFM_1_0A_DOM_ALL);
        FaultSection s = ruptSet.getFaultSectionData(0);
        assertEquals(0, s.getSectionId());
        assertEquals(0.2, s.getOrigAveSlipRate(), DELTA);

        DeformationModel helper =
                new DeformationModel("file not needed") {
                    // We bypass loading a file by overriding this method.
                    public InputStream getStream() {
                        // simulate a deformationFile where each slip and stdev for each section is
                        // equal to the section id
                        StringBuilder builder = new StringBuilder();
                        for (int id = 0; id < ruptSet.getNumSections(); id++) {
                            builder.append(id + "," + id + "," + id + "," + id + "\n");
                        }
                        String data = builder.toString();
                        return new ByteArrayInputStream(data.getBytes());
                    }
                };

        // set slip to be equal section ID
        helper.applyTo(ruptSet, (sectionId) -> true);

        for (FaultSection section : ruptSet.getFaultSectionDataList()) {
            assertEquals(section.getSectionId(), section.getOrigAveSlipRate(), DELTA);
            assertEquals(section.getSectionId(), section.getOrigSlipRateStdDev(), DELTA);
        }
    }

    // deformation model honours origId and origParent
    @Test
    public void testApplyToProps() throws DocumentException, IOException {
        FaultSystemRupSet ruptSet = createRupSetForSections(NZSHM22_FaultModels.CFM_1_0A_DOM_ALL);
        FaultSection s = ruptSet.getFaultSectionData(0);
        assertEquals(0.2, s.getOrigAveSlipRate(), DELTA);
        s = ruptSet.getFaultSectionData(1);
        assertEquals(0.02, s.getOrigAveSlipRate(), DELTA);

        FaultSectionProperties props0 = new FaultSectionProperties(ruptSet.getFaultSectionData(0));
        FaultSectionProperties props1 = new FaultSectionProperties(ruptSet.getFaultSectionData(1));

        // deformation model will be applied based on original id
        props0.setOriginalId(1000);
        props0.setOriginalParent(1200);
        props1.setOriginalId(1001);
        props1.setOriginalParent(1001);

        DeformationModel helper =
                new DeformationModel("file not needed") {
                    protected Map<Integer, SlipDeformation> getDeformations() {
                        Map<Integer, SlipDeformation> result = new HashMap<>();
                        SlipDeformation deformation = new SlipDeformation();
                        deformation.sectionId = 1001;
                        deformation.parentId = 1001;
                        deformation.slip = 42;
                        deformation.stdv = 3.14;
                        result.put(1001, deformation);
                        deformation = new SlipDeformation();
                        deformation.sectionId = 1000;
                        deformation.parentId = 1200;
                        deformation.slip = 6;
                        deformation.stdv = 7;
                        result.put(1000, deformation);
                        return result;
                    }
                };

        // set slip to be equal section ID
        helper.applyTo(ruptSet, (sectionId) -> true);

        FaultSection section = ruptSet.getFaultSectionData(0);
        assertEquals(6, section.getOrigAveSlipRate(), DELTA);
        assertEquals(7, section.getOrigSlipRateStdDev(), DELTA);

        section = ruptSet.getFaultSectionData(1);
        assertEquals(42, section.getOrigAveSlipRate(), DELTA);
        assertEquals(3.14, section.getOrigSlipRateStdDev(), DELTA);
    }

    // honour partition predicate
    @Test
    public void testApplyToPartition() throws DocumentException, IOException {
        FaultSystemRupSet ruptSet = createRupSetForSections(NZSHM22_FaultModels.CFM_1_0A_DOM_ALL);
        FaultSection s = ruptSet.getFaultSectionData(0);
        assertEquals(0.2, s.getOrigAveSlipRate(), DELTA);
        s = ruptSet.getFaultSectionData(1);
        assertEquals(0.02, s.getOrigAveSlipRate(), DELTA);

        DeformationModel helper =
                new DeformationModel("file not needed") {
                    protected Map<Integer, SlipDeformation> getDeformations() {
                        Map<Integer, SlipDeformation> result = new HashMap<>();
                        SlipDeformation deformation = new SlipDeformation();
                        deformation.sectionId = 0;
                        deformation.parentId = 0;
                        deformation.slip = 42;
                        deformation.stdv = 3.14;
                        result.put(0, deformation);
                        deformation = new SlipDeformation();
                        deformation.sectionId = 1;
                        deformation.parentId = 1;
                        deformation.slip = 6;
                        deformation.stdv = 7;
                        result.put(1, deformation);
                        return result;
                    }
                };

        // only apply deformation model to section 1
        helper.applyTo(ruptSet, (sectionId) -> sectionId == 1);

        // section 0 is unchanged
        FaultSection section = ruptSet.getFaultSectionData(0);
        assertEquals(0.2, section.getOrigAveSlipRate(), DELTA);
        assertEquals(0.15, section.getOrigSlipRateStdDev(), DELTA);

        // section 1 is modified
        section = ruptSet.getFaultSectionData(1);
        assertEquals(6, section.getOrigAveSlipRate(), DELTA);
        assertEquals(7, section.getOrigSlipRateStdDev(), DELTA);
    }

    @Test
    public void testApplyToError() throws DocumentException, IOException {
        FaultSystemRupSet ruptSet = createRupSetForSections(NZSHM22_FaultModels.CFM_1_0A_DOM_ALL);
        // Testing that we check the parent section id
        DeformationModel helper =
                new DeformationModel("file not needed") {
                    protected Map<Integer, SlipDeformation> getDeformations() {
                        Map<Integer, SlipDeformation> result = new HashMap<>();
                        for (int i = 0; i < ruptSet.getNumSections(); i++) {
                            SlipDeformation deformation = new SlipDeformation();
                            deformation.sectionId = i;
                            deformation.rowNum = i + 1;
                            result.put(i, deformation);
                        }
                        return result;
                    }
                };

        String message = null;
        try {
            helper.applyTo(ruptSet, null);
        } catch (IllegalArgumentException x) {
            message = x.getMessage();
        }
        assertEquals(
                "Section 1 Deformation parent id 0 does not match section parent id 1"
                        + " (deformation model line 2)",
                message);
    }
}

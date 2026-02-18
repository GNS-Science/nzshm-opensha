package nz.cri.gns.NZSHM22.opensha.enumTreeBranches;

import static nz.cri.gns.NZSHM22.opensha.util.TestHelpers.createRupSetForSections;
import static org.junit.Assert.*;

import java.io.*;
import java.util.*;
import nz.cri.gns.NZSHM22.opensha.ruptures.FaultSectionProperties;
import org.dom4j.DocumentException;
import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.faultSurface.FaultSection;

public class NZSHM22_DeformationModelsTest {

    static final double DELTA = 1e-9;

    @Test
    public void testApplyTo() throws DocumentException, IOException {
        FaultSystemRupSet ruptSet = createRupSetForSections(NZSHM22_FaultModels.CFM_1_0A_DOM_ALL);
        FaultSection s = ruptSet.getFaultSectionData(0);
        assertEquals(0, s.getSectionId());
        assertEquals(0.2, s.getOrigAveSlipRate(), DELTA);

        NZSHM22_DeformationModel.DeformationHelper helper =
                new NZSHM22_DeformationModel.DeformationHelper("file not needed") {
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

        FaultSectionProperties props0 =
                new FaultSectionProperties(ruptSet.getFaultSectionData(0));
        FaultSectionProperties props1 =
                new FaultSectionProperties(ruptSet.getFaultSectionData(1));

        // deformation model will be applied based on original id
        props0.setOriginalId(1000);
        props0.setOriginalParent(1200);
        props1.setOriginalId(1001);
        props1.setOriginalParent(1001);

        NZSHM22_DeformationModel.DeformationHelper helper =
                new NZSHM22_DeformationModel.DeformationHelper("file not needed") {
                    public Map<Integer, SlipDeformation> getDeformations() {
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

        NZSHM22_DeformationModel.DeformationHelper helper =
                new NZSHM22_DeformationModel.DeformationHelper("file not needed") {
                    public Map<Integer, SlipDeformation> getDeformations() {
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
        NZSHM22_DeformationModel.DeformationHelper helper =
                new NZSHM22_DeformationModel.DeformationHelper("file not needed") {
                    public Map<Integer, SlipDeformation> getDeformations() {
                        Map<Integer, SlipDeformation> result = new HashMap<>();
                        for (int i = 0; i < ruptSet.getNumSections(); i++) {
                            SlipDeformation deformation = new SlipDeformation();
                            deformation.sectionId = i;
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
                "Section 1 Deformation parent id 0 does not match section parent id 1", message);
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
            assert (!seen.contains(model.getFileName()));
            seen.add(model.getFileName());
        }
    }

    public String read(NZSHM22_DeformationModel model) throws IOException {
        StringBuilder s = new StringBuilder();
        BufferedReader in = new BufferedReader(new InputStreamReader(model.helper.getStream()));
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
            if (model == NZSHM22_DeformationModel.FAULT_MODEL) {
                continue;
            }
            System.out.println(model.name());
            String data = read(model);
            assert (!hashes.containsKey(data));
            hashes.put(data, model);
        }
    }
}

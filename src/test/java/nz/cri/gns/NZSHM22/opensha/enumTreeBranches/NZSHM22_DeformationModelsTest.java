package nz.cri.gns.NZSHM22.opensha.enumTreeBranches;

import static nz.cri.gns.NZSHM22.opensha.util.TestHelpers.createRupSetForSections;
import static org.junit.Assert.*;

import org.dom4j.DocumentException;
import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.faultSurface.FaultSection;

import java.io.*;
import java.util.*;

public class NZSHM22_DeformationModelsTest {

    @Test
    public void testApplyTo() throws DocumentException, IOException {
        FaultSystemRupSet ruptSet = createRupSetForSections(NZSHM22_FaultModels.CFM_1_0A_DOM_ALL);
        FaultSection s = ruptSet.getFaultSectionData(0);
        assertEquals(0, s.getSectionId());
        assertEquals(0.2, s.getOrigAveSlipRate(), 0.00000001);
        assertEquals(2.0E-4, ruptSet.getSlipRateForSection(0), 0.0000001);
        assertEquals(2.0E-5, ruptSet.getSlipRateForSection(1), 0.0000001);

        NZSHM22_DeformationModel.DeformationHelper helper = new NZSHM22_DeformationModel.DeformationHelper("file not needed") {
            // We bypass loading a file by overriding this method.
            public InputStream getStream() {
                // simulate a deformationFile where each slip and stdev for each section is equal to the section id
                StringBuilder builder = new StringBuilder();
                for (int id = 0; id < ruptSet.getNumSections(); id++) {
                    builder.append(id + "," + id + "," + id + "," + id + "\n");
                }
                String data = builder.toString();
                return new ByteArrayInputStream(data.getBytes());
            }
        };

        // set slip to be equal section ID
        helper.applyTo(ruptSet);

        assertEquals(0.0, ruptSet.getSlipRateForSection(0), 0.0000001);
        assertEquals(0.001, ruptSet.getSlipRateForSection(1), 0.0000001);

        for (FaultSection section : ruptSet.getFaultSectionDataList()) {
            assertEquals(section.getSectionId(), section.getOrigAveSlipRate(), 0.000000001);
            assertEquals(section.getSectionId(), section.getOrigSlipRateStdDev(), 0.000000001);
        }

        // Testing that we check the length
        helper = new NZSHM22_DeformationModel.DeformationHelper("file not needed") {
            public List<SlipDeformation> getDeformations() {
                return new ArrayList<>();
            }
        };

        String message = null;
        try {
            helper.applyTo(ruptSet);
        } catch (IllegalArgumentException x) {
            message = x.getMessage();
        }
        assertEquals("Deformation model length does not match number of sections.", message);

        // Testing that we check the parent section id
        helper = new NZSHM22_DeformationModel.DeformationHelper("file not needed") {
            public List<SlipDeformation> getDeformations() {
                List<SlipDeformation> result = new ArrayList<>();
                for (int i = 0; i < ruptSet.getNumSections(); i++) {
                    SlipDeformation deformation = new SlipDeformation();
                    deformation.sectionId = i;
                    result.add(deformation);
                }
                return result;
            }
        };

        message = null;
        try {
            helper.applyTo(ruptSet);
        } catch (IllegalArgumentException x) {
            message = x.getMessage();
        }
        assertEquals("Deformation parent id does not match section parent id.", message);

    }

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

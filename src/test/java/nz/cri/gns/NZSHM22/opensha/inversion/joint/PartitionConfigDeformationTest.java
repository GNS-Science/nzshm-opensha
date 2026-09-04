package nz.cri.gns.NZSHM22.opensha.inversion.joint;

import static nz.cri.gns.NZSHM22.opensha.util.TestHelpers.createRupSetForSections;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_DeformationModel;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import org.dom4j.DocumentException;
import org.junit.After;
import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.faultSurface.FaultSection;

/** Tests side-loading a deformation model file via the joint inversion config. */
public class PartitionConfigDeformationTest {

    /** The CUSTOM model is a mutable singleton, so we clear it after every test. */
    @After
    public void clearCustomModel() {
        NZSHM22_DeformationModel.CUSTOM.setCustomModel(null);
    }

    /**
     * Writes deformation model data where slip and stdv of each section equal its section id.
     *
     * @param rupSet the rupture set to create data for
     * @return the file the data was written to
     */
    public static File deformationFile(FaultSystemRupSet rupSet) throws IOException {
        File file = File.createTempFile("deformationModel", ".csv");
        file.deleteOnExit();
        try (Writer out = new FileWriter(file)) {
            for (int id = 0; id < rupSet.getNumSections(); id++) {
                out.write(id + "," + id + "," + id + "," + id + "\n");
            }
        }
        return file;
    }

    public static Config configFor(FaultSystemRupSet rupSet, PartitionConfig partition) {
        Config config = new Config();
        config.setRuptureSet(rupSet);
        config.partitions.add(partition);
        return config;
    }

    @Test
    public void testDeformationModelFile() throws DocumentException, IOException {
        FaultSystemRupSet rupSet = createRupSetForSections(NZSHM22_FaultModels.CFM_1_0A_DOM_ALL);
        assertEquals(0.2, rupSet.getFaultSectionData(0).getOrigAveSlipRate(), 1e-9);

        PartitionConfig partition = new PartitionConfig(PartitionPredicate.CRUSTAL);
        partition.deformationModel = NZSHM22_DeformationModel.CUSTOM;
        partition.deformationModelFile = deformationFile(rupSet).getAbsolutePath();
        partition.partitionPredicate = (sectionId) -> true;

        RuptureSetSetup.applyDeformationModel(configFor(rupSet, partition));

        for (FaultSection section : rupSet.getFaultSectionDataList()) {
            assertEquals(section.getSectionId(), section.getOrigAveSlipRate(), 1e-9);
            assertEquals(section.getSectionId(), section.getOrigSlipRateStdDev(), 1e-9);
        }
    }

    // a deformation model file is only valid for the CUSTOM deformation model
    @Test
    public void testInitRejectsFileWithoutCustomModel()
            throws DocumentException, IOException, IllegalStateException {
        FaultSystemRupSet rupSet = createRupSetForSections(NZSHM22_FaultModels.CFM_1_0A_DOM_ALL);

        PartitionConfig partition = new PartitionConfig(PartitionPredicate.CRUSTAL);
        partition.deformationModel = NZSHM22_DeformationModel.FAULT_MODEL;
        partition.deformationModelFile = deformationFile(rupSet).getAbsolutePath();

        String message = null;
        try {
            configFor(rupSet, partition).init();
        } catch (IllegalStateException x) {
            message = x.getMessage();
        }
        assertTrue(
                message,
                message != null
                        && message.startsWith(
                                "deformationModelFile is only valid for the CUSTOM deformation"
                                        + " model"));
    }

    // only one partition may specify a deformation model file
    @Test
    public void testInitRejectsTwoFiles() throws DocumentException, IOException {
        FaultSystemRupSet rupSet = createRupSetForSections(NZSHM22_FaultModels.CFM_1_0A_DOM_ALL);
        String file = deformationFile(rupSet).getAbsolutePath();

        Config config = new Config();
        config.setRuptureSet(rupSet);
        for (PartitionPredicate predicate :
                new PartitionPredicate[] {
                    PartitionPredicate.CRUSTAL, PartitionPredicate.HIKURANGI
                }) {
            PartitionConfig partition = new PartitionConfig(predicate);
            partition.deformationModel = NZSHM22_DeformationModel.CUSTOM;
            partition.deformationModelFile = file;
            config.partitions.add(partition);
        }

        String message = null;
        try {
            config.init();
        } catch (IllegalStateException x) {
            message = x.getMessage();
        }
        assertEquals("Only one partition can specify a deformationModelFile, but 2 do.", message);
    }
}

package nz.cri.gns.NZSHM22.opensha.inversion.joint;

import static nz.cri.gns.NZSHM22.opensha.util.TestHelpers.createRupSetForSections;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.util.zip.ZipFile;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.inputs.InputModel;
import org.dom4j.DocumentException;
import org.junit.Test;
import org.opensha.commons.util.modules.ModuleArchive;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.faultSurface.FaultSection;

/** Tests deformation models in the joint inversion config. */
public class PartitionConfigDeformationTest {

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

    public static Config configFor(FaultSystemRupSet rupSet, PartitionConfig... partitions) {
        Config config = new Config();
        config.setRuptureSet(rupSet);
        for (PartitionConfig partition : partitions) {
            config.partitions.add(partition);
        }
        return config;
    }

    @Test
    public void testLocalDeformationModel() throws DocumentException, IOException {
        FaultSystemRupSet rupSet = createRupSetForSections(NZSHM22_FaultModels.CFM_1_0A_DOM_ALL);
        assertEquals(0.2, rupSet.getFaultSectionData(0).getOrigAveSlipRate(), 1e-9);

        PartitionConfig partition = new PartitionConfig(PartitionPredicate.CRUSTAL);
        partition.deformationModel =
                InputModel.FILE_PREFIX + deformationFile(rupSet).getAbsolutePath();
        partition.partitionPredicate = (sectionId) -> true;
        partition.hydrateDeformationModel();

        RuptureSetSetup.applyDeformationModel(configFor(rupSet, partition));

        for (FaultSection section : rupSet.getFaultSectionDataList()) {
            assertEquals(section.getSectionId(), section.getOrigAveSlipRate(), 1e-9);
            assertEquals(section.getSectionId(), section.getOrigSlipRateStdDev(), 1e-9);
        }
    }

    // without a deformation model, the fault model's slip rates are used
    @Test
    public void testNoDeformationModel() throws DocumentException, IOException {
        FaultSystemRupSet rupSet = createRupSetForSections(NZSHM22_FaultModels.CFM_1_0A_DOM_ALL);

        PartitionConfig partition = new PartitionConfig(PartitionPredicate.CRUSTAL);
        partition.partitionPredicate = (sectionId) -> true;
        partition.hydrateDeformationModel();
        assertNull(partition.hydratedDeformationModel);

        RuptureSetSetup.applyDeformationModel(configFor(rupSet, partition));

        assertEquals(0.2, rupSet.getFaultSectionData(0).getOrigAveSlipRate(), 1e-9);
    }

    @Test
    public void testHydrateRepoDeformationModel() {
        PartitionConfig partition = new PartitionConfig(PartitionPredicate.PUYSEGUR);
        partition.deformationModel = "dm_puysegur.csv";
        partition.hydrateDeformationModel();
        assertEquals("dm_puysegur.csv", partition.hydratedDeformationModel.getPath());
        assertFalse(partition.hydratedDeformationModel.isLocal());
    }

    // only local deformation models are stored, one per partition
    @Test
    public void testLocalDeformationModels() throws DocumentException, IOException {
        FaultSystemRupSet rupSet = createRupSetForSections(NZSHM22_FaultModels.CFM_1_0A_DOM_ALL);
        File file = deformationFile(rupSet);
        String data = Files.readString(file.toPath());

        PartitionConfig crustal = new PartitionConfig(PartitionPredicate.CRUSTAL);
        crustal.deformationModel = InputModel.FILE_PREFIX + file.getAbsolutePath();
        PartitionConfig hikurangi = new PartitionConfig(PartitionPredicate.HIKURANGI);
        hikurangi.deformationModel = InputModel.FILE_PREFIX + file.getAbsolutePath();
        PartitionConfig puysegur = new PartitionConfig(PartitionPredicate.PUYSEGUR);
        puysegur.deformationModel = "dm_puysegur.csv";
        PartitionConfig none = new PartitionConfig(PartitionPredicate.TVZ);
        Config config = configFor(rupSet, crustal, hikurangi, puysegur, none);
        for (PartitionConfig partition : config.partitions) {
            partition.hydrateDeformationModel();
        }

        LocalDeformationModels module = LocalDeformationModels.fromConfig(config);

        File archiveFile = File.createTempFile("archive", ".zip");
        archiveFile.deleteOnExit();
        ModuleArchive<OpenSHA_Module> archive = new ModuleArchive<>();
        archive.addModule(module);
        archive.write(archiveFile);
        LocalDeformationModels actual =
                new ModuleArchive<>(new ZipFile(archiveFile), OpenSHA_Module.class)
                        .getModule(LocalDeformationModels.class);

        assertEquals(2, actual.getModels().size());
        assertTrue(actual.getModels().containsKey(PartitionPredicate.CRUSTAL));
        assertEquals(
                crustal.deformationModel, actual.getModels().get(PartitionPredicate.CRUSTAL).path);
        assertEquals(data, actual.getModels().get(PartitionPredicate.CRUSTAL).data);
        assertEquals(data, actual.getModels().get(PartitionPredicate.HIKURANGI).data);
    }

    @Test
    public void testNoLocalDeformationModels() throws DocumentException, IOException {
        FaultSystemRupSet rupSet = createRupSetForSections(NZSHM22_FaultModels.CFM_1_0A_DOM_ALL);
        PartitionConfig partition = new PartitionConfig(PartitionPredicate.CRUSTAL);
        partition.hydrateDeformationModel();
        assertNull(LocalDeformationModels.fromConfig(configFor(rupSet, partition)));
    }
}

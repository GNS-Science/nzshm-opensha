package nz.cri.gns.NZSHM22.opensha.inversion;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.util.List;

import nz.cri.gns.NZSHM22.opensha.calc.SimplifiedScalingRelationship;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_LogicTreeBranch;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_ScalingRelationshipNode;
import nz.cri.gns.NZSHM22.opensha.faults.FaultSectionList;
import org.dom4j.DocumentException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.RupSetScalingRelationship;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;

public class NZSHM22_InversionRunner_IntegrationTest {

    private static URL alpineVernonRupturesUrl;
    private static File tempFolder;

    @BeforeClass
    public static void setUp() throws IOException {
        alpineVernonRupturesUrl = Thread.currentThread().getContextClassLoader().getResource("AlpineVernonRuptureSet.zip");
        tempFolder = Files.createTempDirectory("_testNew").toFile();
    }

    @AfterClass
    public static void tearDown() throws IOException {
        File[] files = tempFolder.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f != null) {
                    f.delete();
                }
            }
        }
        try {
            Files.deleteIfExists(tempFolder.toPath());
        } catch (DirectoryNotEmptyException x) {
            // if there are rupture sets in the temp folder, it might not be possible to delete them right away
            x.printStackTrace();
        }
    }

    public static FaultSystemRupSet createRupSet(NZSHM22_FaultModels faultModel, RupSetScalingRelationship scalingRelationship, List<List<Integer>> sectionForRups) throws DocumentException, IOException {
        FaultSectionList sections = new FaultSectionList();
        faultModel.fetchFaultSections(sections);
        // simulate subsections exactly the same size as the parents
        sections.forEach(section -> {
            section.setParentSectionId(section.getSectionId());
            section.setParentSectionName(section.getSectionName());
        });

        NZSHM22_LogicTreeBranch branch = new NZSHM22_LogicTreeBranch();
        branch.setValue(faultModel);
        branch.setValue(new NZSHM22_ScalingRelationshipNode(scalingRelationship));

        return FaultSystemRupSet.builder(sections, sectionForRups)
                .forScalingRelationship(scalingRelationship)
                .addModule(branch)
                .build();
    }


    public NZSHM22_AbstractInversionRunner buildRunner() throws URISyntaxException, DocumentException, IOException {
        SimplifiedScalingRelationship scaling = (SimplifiedScalingRelationship) NZSHM22_ScalingRelationshipNode.createRelationShip("SimplifiedScalingRelationship");
        scaling.setupCrustal(4, 4.1);
        FaultSystemRupSet rupSet = createRupSet(
                NZSHM22_FaultModels.CFM_1_0A_DOM_ALL,
                scaling,
                List.of(List.of(0, 1),
                        List.of(5, 6, 7, 8, 9)));
        File rupSetFile = new File(tempFolder, "buildRunnerRupSet.zip");
        rupSet.write(rupSetFile);

        return new NZSHM22_CrustalInversionRunner()
                .setGutenbergRichterMFD(4.0, 0.81, 0.91, 1.05, 7.85)
                .setInversionSeconds(1)
                .setSelectionInterval(1)
                .setScalingRelationship(scaling, false)
                .setRuptureSetFile(rupSetFile)
                //.setGutenbergRichterMFDWeights(100.0, 1000.0)
                //.setSlipRateConstraint("BOTH", 1000, 1000)
                .setSlipRateUncertaintyConstraint(1000, 2)
                .setUncertaintyWeightedMFDWeights(0.5, 0.5, 0.5);
    }

    @Test
    public void testRunExclusion() throws URISyntaxException, DocumentException, IOException {
        NZSHM22_AbstractInversionRunner runner = buildRunner().setExcludeRupturesBelowMinMag(true);
        FaultSystemSolution solution = runner.runInversion();

        for (int r = 0; r < solution.getRupSet().getNumRuptures(); r++) {
            if (((NZSHM22_InversionFaultSystemRuptSet) solution.getRupSet()).isRuptureBelowSectMinMag(r)) {
                assertEquals(0, solution.getRateForRup(r), 0);
            }
        }
    }

    /**
     * Test showing how we create a new NZSHM22_InversionFaultSystemRuptSet from an existing rupture set
     *
     * @throws IOException
     * @throws DocumentException
     * @throws URISyntaxException
     */
    @Test
    public void testLoadRuptureSetForInversion() throws IOException, DocumentException, URISyntaxException {
        NZSHM22_InversionFaultSystemRuptSet ruptureSet = NZSHM22_InversionFaultSystemRuptSet.loadCrustalRuptureSet(new File(alpineVernonRupturesUrl.toURI()), NZSHM22_LogicTreeBranch.crustalInversion());
        assertEquals(3101, ruptureSet.getModule(ClusterRuptures.class).getAll().size());
    }

    //TODO we should use junit>=4.13 and assertThrows instead
    // see https://stackoverflow.com/questions/156503/how-do-you-assert-that-a-certain-exception-is-thrown-in-junit-4-tests
    @Test(expected = IllegalArgumentException.class)
    public void testSetSlipRateConstraintThrowsWithInvalidArgument() {
        NZSHM22_CrustalInversionRunner runner = (NZSHM22_CrustalInversionRunner) new NZSHM22_CrustalInversionRunner()
                .setSlipRateConstraint(AbstractInversionConfiguration.NZSlipRateConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY, 1, 2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetSlipRateUncertaintyConstraintThrowsWithInvalidArgument() {
        new NZSHM22_CrustalInversionRunner()
                .setSlipRateConstraint(AbstractInversionConfiguration.NZSlipRateConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY, 1, 2);
    }

    @Test(expected = IllegalStateException.class)
    public void testSetSlipRateUncertaintyConstraintThrowsWithInvalidArgument2() {
        new NZSHM22_CrustalInversionRunner()
                .setSlipRateConstraint(AbstractInversionConfiguration.NZSlipRateConstraintWeightingType.NORMALIZED, 1, 2)
                .setSlipRateUncertaintyConstraint(1, 2);
    }

}

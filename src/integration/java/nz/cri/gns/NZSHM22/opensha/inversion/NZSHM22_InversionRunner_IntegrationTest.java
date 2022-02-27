package nz.cri.gns.NZSHM22.opensha.inversion;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;

import nz.cri.gns.NZSHM22.opensha.calc.SimplifiedScalingRelationship;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_LogicTreeBranch;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_ScalingRelationshipNode;
import org.dom4j.DocumentException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
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
        Files.deleteIfExists(tempFolder.toPath());
    }


    public NZSHM22_AbstractInversionRunner buildRunner() throws URISyntaxException {

        SimplifiedScalingRelationship scaling = (SimplifiedScalingRelationship) NZSHM22_ScalingRelationshipNode.createRelationShip("SimplifiedScalingRelationship");
        scaling.setupCrustal(4, 4.1);

        return new NZSHM22_CrustalInversionRunner()
                .setGutenbergRichterMFD(4.0, 0.81, 0.91, 1.05, 7.85)
                .setInversionSeconds(1)
                .setSelectionInterval(1)
                .setScalingRelationship(scaling, true)
                .setRuptureSetFile(new File(alpineVernonRupturesUrl.toURI()))
                //.setGutenbergRichterMFDWeights(100.0, 1000.0)
                //.setSlipRateConstraint("BOTH", 1000, 1000)
                .setSlipRateUncertaintyConstraint(1000, 2)
                .setUncertaintyWeightedMFDWeights(0.5, 0.5);
    }

//    @Test
//    public void testRunExclusion() throws URISyntaxException, DocumentException, IOException {
//        NZSHM22_AbstractInversionRunner runner = buildRunner().setExcludeRupturesBelowMinMag(false);
//        FaultSystemSolution solution = runner.runInversion();
//
//        assertTrue(solution.getRateForRup(0) > 0);
//        assertTrue(solution.getRateForRup(6) > 0);
//
//        runner = buildRunner().setExcludeRupturesBelowMinMag(true);
//        solution = runner.runInversion();
//
//        // when excluding minMag ruptures, rupture 0 no longer has a rate
//        assertEquals(0, solution.getRateForRup(0), 0.0);
//        assertTrue(solution.getRateForRup(6) > 0);
//    }

    /**
     * Test showing how we create a new NZSHM22_InversionFaultSystemRuptSet from an existing rupture set
     *
     * @throws IOException
     * @throws DocumentException
     * @throws URISyntaxException
     */
    @Test
    public void testLoadRuptureSetForInversion() throws IOException, DocumentException, URISyntaxException {
        NZSHM22_InversionFaultSystemRuptSet ruptureSet = NZSHM22_InversionFaultSystemRuptSet.loadRuptureSet(new File(alpineVernonRupturesUrl.toURI()), NZSHM22_LogicTreeBranch.crustalInversion());
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

package nz.cri.gns.NZSHM22.opensha.inversion;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;

import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_LogicTreeBranch;
import org.dom4j.DocumentException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;

public class NZSHM22_InversionRunner_IntegrationTest {

    private static URL alpineVernonRupturesUrl;
    private static URL amatrixUrl;
    private static File tempFolder;

    @BeforeClass
    public static void setUp() throws IOException {
        alpineVernonRupturesUrl = Thread.currentThread().getContextClassLoader().getResource("AlpineVernonRuptureSet.zip");
        amatrixUrl = Thread.currentThread().getContextClassLoader().getResource("amatrix.csv");
        tempFolder = Files.createTempDirectory("_testNew").toFile();
    }

    @AfterClass
    public static void tearDown() throws IOException {
        //Clean up the temp folder
        File[] files = tempFolder.listFiles();
        for (File f : files) {
            f.delete();
        }
        Files.deleteIfExists(tempFolder.toPath());
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
        NZSHM22_InversionFaultSystemRuptSet ruptureSet = NZSHM22_CrustalInversionRunner.loadRuptureSet(new File(alpineVernonRupturesUrl.toURI()), NZSHM22_LogicTreeBranch.crustal());
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
        NZSHM22_CrustalInversionRunner runner = new NZSHM22_CrustalInversionRunner()
                .setSlipRateUncertaintyConstraint(AbstractInversionConfiguration.NZSlipRateConstraintWeightingType.BOTH, 1, 2);
    }

}

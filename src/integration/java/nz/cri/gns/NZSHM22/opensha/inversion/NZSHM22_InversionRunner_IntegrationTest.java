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

import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import scratch.UCERF3.inversion.UCERF3InversionConfiguration.SlipRateConstraintWeightingType;

public class NZSHM22_InversionRunner_IntegrationTest {
	
	private static URL alpineVernonRupturesUrl;
	private static File tempFolder;

	@BeforeClass
	public static void setUp() throws IOException, DocumentException, URISyntaxException {
		alpineVernonRupturesUrl = Thread.currentThread().getContextClassLoader().getResource("AlpineVernonRuptureSet.zip");
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
		NZSHM22_InversionFaultSystemRuptSet ruptureSet = NZSHM22_CrustalInversionRunner.loadRuptureSet(new File(alpineVernonRupturesUrl.toURI()), NZSHM22_LogicTreeBranch.crustal(), null);
		assertEquals(3101, ruptureSet.getModule(ClusterRuptures.class).getAll().size());
	}

	/**
	 * Top-level NZSHM22_InversionRunner API test
	 * 
	 * @throws IOException
	 * @throws DocumentException
	 * @throws URISyntaxException
	 */
	@Test
    public void testRunCrustalInversion_AlpineVernon() throws IOException, DocumentException, URISyntaxException {
	
		long inversionSecs = 2; // run it for this many secs
        long syncInterval = 1; // seconds between inversion synchronisations
        File solFile = new File(tempFolder, "testAlpineVernonInversion.zip");

        NZSHM22_CrustalInversionRunner runner = (NZSHM22_CrustalInversionRunner) new NZSHM22_CrustalInversionRunner()
                .setInversionSeconds(inversionSecs) // or use inversionMinutes
                .setSelectionInterval(syncInterval)
        		.setRuptureSetFile(new File(alpineVernonRupturesUrl.toURI()))
        		.setGutenbergRichterMFDWeights(10d, 1000d)
        		.configure(); //do this last thing before runInversion!
        FaultSystemSolution solution = runner.runInversion();
        System.out.println(runner.byFaultNameMetrics());
        solution.write(solFile);
	}
	
	//TODO we should use junit>=4.13 and assertThrows instead 
	// see https://stackoverflow.com/questions/156503/how-do-you-assert-that-a-certain-exception-is-thrown-in-junit-4-tests 
	@Test(expected = IllegalArgumentException.class)
	public void testSetSlipRateConstraintThrowsWithInvalidArgument() {
        NZSHM22_CrustalInversionRunner runner = (NZSHM22_CrustalInversionRunner) new NZSHM22_CrustalInversionRunner()
        		.setSlipRateConstraint(SlipRateConstraintWeightingType.UNCERTAINTY_ADJUSTED, 1, 2);
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void testSetSlipRateUncertaintyConstraintThrowsWithInvalidArgument() {
        NZSHM22_CrustalInversionRunner runner = new NZSHM22_CrustalInversionRunner()
        		.setSlipRateUncertaintyConstraint(SlipRateConstraintWeightingType.BOTH, 1, 2);
	}

}

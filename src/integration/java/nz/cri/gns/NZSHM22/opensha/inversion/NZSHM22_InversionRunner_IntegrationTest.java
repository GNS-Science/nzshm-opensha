package nz.cri.gns.NZSHM22.opensha.inversion;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;

import org.apache.commons.io.FileUtils;
//import com.google.common.collect.Sets;
import org.dom4j.DocumentException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import nz.cri.gns.NZSHM22.opensha.inversion.NZSHM22_InversionRunner;
import nz.cri.gns.NZSHM22.opensha.inversion.NZSHM22_InversionConfiguration;
import nz.cri.gns.NZSHM22.opensha.inversion.NZSHM22_InversionFaultSystemRuptSet;
import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.inversion.InversionFaultSystemRupSet;
import scratch.UCERF3.logicTree.LogicTreeBranch;
import scratch.UCERF3.utils.FaultSystemIO;

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
		LogicTreeBranch branch = (LogicTreeBranch) LogicTreeBranch.DEFAULT;
		FaultSystemRupSet rupSetA = FaultSystemIO.loadRupSet(new File(alpineVernonRupturesUrl.toURI()));
		NZSHM22_InversionFaultSystemRuptSet ruptureSet = new NZSHM22_InversionFaultSystemRuptSet(rupSetA, branch);
		assertEquals(3101, ruptureSet.getClusterRuptures().size());
	}
	
	/**
	 * Test create a new NZSHM22_InversionFaultSystemRuptSet from an existing rupture set with no cluster json
	 * 
	 * @throws IOException
	 * @throws DocumentException
	 * @throws URISyntaxException 
	 */
	@Test 
	public void testLoadRuptureSetForInversionRebuldingClusters() throws IOException, DocumentException, URISyntaxException {
		LogicTreeBranch branch = (LogicTreeBranch) LogicTreeBranch.DEFAULT;
		FaultSystemRupSet rupSetA = FaultSystemIO.loadRupSet(new File(alpineVernonRupturesUrl.toURI()));
		rupSetA.setClusterRuptures(null); //forces cluster rebuild
		NZSHM22_InversionFaultSystemRuptSet ruptureSet = new NZSHM22_InversionFaultSystemRuptSet(rupSetA, branch);
		assertEquals(3101, ruptureSet.getClusterRuptures().size());		
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

           NZSHM22_InversionRunner runner = new NZSHM22_InversionRunner()
                .setInversionSeconds(inversionSecs) // or use inversionMinutes
                .setSyncInterval(syncInterval)
        		.setRuptureSetFile(new File(alpineVernonRupturesUrl.toURI()))
        		.setGutenbergRichterMFDWeights(10d, 1000d)
        		.configure(); //do this last thing before runInversion!
        FaultSystemSolution solution = runner.runInversion();       
        System.out.println(runner.byFaultNameMetrics());
        FaultSystemIO.writeSol(solution, solFile);

	}

}

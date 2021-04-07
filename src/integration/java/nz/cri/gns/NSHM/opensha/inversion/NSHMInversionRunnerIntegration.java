package nz.cri.gns.NSHM.opensha.inversion;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
//import com.google.common.collect.Sets;
import org.dom4j.DocumentException;
import org.junit.Test;

import nz.cri.gns.NSHM.opensha.inversion.NSHMInversionRunner;
import nz.cri.gns.NSHM.opensha.inversion.NSHM_InversionConfiguration;
import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.inversion.InversionFaultSystemRupSet;
import scratch.UCERF3.logicTree.LogicTreeBranch;
import scratch.UCERF3.utils.FaultSystemIO;

public class NSHMInversionRunnerIntegration {
	
	/**
	 * Test showing how we create a new NSHM_InversionFaultSystemRuptSet from an existing rupture set
	 * 
	 * @throws IOException
	 * @throws DocumentException
	 */
	@Test 
	public void testLoadRuptureSetForInversion() throws IOException, DocumentException {
		FaultSystemRupSet rupSetA = FaultSystemIO.loadRupSet(new File("src/integration/resources/AlpineVernonRuptureSet.zip"));
		LogicTreeBranch branch = (LogicTreeBranch) LogicTreeBranch.DEFAULT;
		
		NSHM_InversionFaultSystemRuptSet ruptureSet = new NSHM_InversionFaultSystemRuptSet(rupSetA, branch);
		assertEquals(3101, ruptureSet.getClusterRuptures().size());
	}
	
	/**
	 * Test create a new NSHM_InversionFaultSystemRuptSet from an existing rupture set with no cluster json
	 * 
	 * @throws IOException
	 * @throws DocumentException
	 */
	@Test 
	public void testLoadRuptureSetForInversionRebuldingClusters() throws IOException, DocumentException {
		FaultSystemRupSet rupSetA = FaultSystemIO.loadRupSet(new File("src/integration/resources/AlpineVernonRuptureSet.zip"));
		LogicTreeBranch branch = (LogicTreeBranch) LogicTreeBranch.DEFAULT;
	
		rupSetA.setClusterRuptures(null); //forces cluster rebuild
		NSHM_InversionFaultSystemRuptSet ruptureSet = new NSHM_InversionFaultSystemRuptSet(rupSetA, branch);
		assertEquals(3101, ruptureSet.getClusterRuptures().size());		
	}	
	
	
	@Test
    public void testRunCrustalInversion_AlpineVernon() throws IOException, DocumentException {
	
		long inversionSecs = 2; // run it for this many secs
        long syncInterval = 1; // seconds between inversion synchronisations
        File outputDir = new File("/tmp");
        File solFile = new File(outputDir, "testAlpineVernonInversion.zip");

           NSHMInversionRunner runner = new NSHMInversionRunner()
                .setInversionSeconds(inversionSecs) // or use inversionMinutes
                .setSyncInterval(syncInterval)
        		.setRuptureSetFile(new File("src/integration/resources/AlpineVernonRuptureSet.zip"))
        		.setGutenbergRichterMFDWeights(10d, 1000d)
        		.configure(); //do this last thing before runInversion!
        FaultSystemSolution solution = runner.runInversion();       
        System.out.println(runner.byFaultNameMetrics());
        FaultSystemIO.writeSol(solution, solFile);
        assertTrue(true);
	}

}

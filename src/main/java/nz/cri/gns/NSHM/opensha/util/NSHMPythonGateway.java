package nz.cri.gns.NSHM.opensha.util;
import java.io.File;
import java.io.IOException;

import org.dom4j.DocumentException;

import nz.cri.gns.NSHM.opensha.inversion.NSHMInversionRunner;
import nz.cri.gns.NSHM.opensha.ruptures.NSHMRuptureSetBuilder;
import py4j.GatewayServer;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.SlipAlongRuptureModelRupSet;
import scratch.UCERF3.utils.FaultSystemIO;

/**
 * A py4j gateway for building ruptures and running inversions.
 *  
 */
public class NSHMPythonGateway {

	static CachedNSHMRuptureSetBuilder builder = new CachedNSHMRuptureSetBuilder();
	static CachedNSHMInversionRunner runner = new CachedNSHMInversionRunner();
	
	public static CachedNSHMRuptureSetBuilder getBuilder() {
		return builder;
	}

	public static NSHMInversionRunner getRunner() {return runner;}

	public static void main(String[] args) {
		NSHMPythonGateway app = new NSHMPythonGateway();
		
		// app is now the gateway.entry_point
		GatewayServer server = new GatewayServer(app);
		server.start();
	}	

	/**
	 * Provide a little help for python clients using NSHMRuptureSetBuilder
	 * 
	 */
	static class CachedNSHMRuptureSetBuilder extends NSHMRuptureSetBuilder {
		SlipAlongRuptureModelRupSet ruptureSet;

		/**
		 * 
		 * @param permutationStrategyClass one of 'DOWNDIP', 'POINTS', 'UCERF3'
		 * @return this
		 */
		public NSHMRuptureSetBuilder setPermutationStrategy(String permutationStrategyClass) {
		 
			super.setPermutationStrategy(RupturePermutationStrategy.valueOf(permutationStrategyClass));
			return this;
		}
		
		/**
		 * Caches the results of the build
		 */
		@Override
		public SlipAlongRuptureModelRupSet buildRuptureSet(String fsdFileName) throws DocumentException, IOException {
			ruptureSet = super.buildRuptureSet(fsdFileName);
			return ruptureSet;
		}
		/**
		 * Write the cached rupture set to disk.
		 *  
		 * @param rupSetFileName
		 * @throws IOException
		 */
		public void writeRuptureSet(String rupSetFileName) throws IOException {
			File rupSetFile = new File(rupSetFileName);
			FaultSystemIO.writeRupSet(ruptureSet, rupSetFile);
		}		
	}

	/**
	 * Python helper that wraps NSHMInversionRunner
	 */
	static class CachedNSHMInversionRunner extends NSHMInversionRunner{
		FaultSystemSolution solution = null;

		/**
		 * like run(File ruptureSetFile), but caches the result
		 * @param ruptureSetFileName the name of a rupture set file
		 * @return the solution
		 * @throws IOException
		 * @throws DocumentException
		 */
		public FaultSystemSolution run(String ruptureSetFileName) throws IOException, DocumentException {
			File rupSetFile = new File(ruptureSetFileName);
			solution = run(rupSetFile);
			return solution;
		}

		/**
		 * Writes the cached solution (see the run method) to file.
		 * @param solutionFileName the file name
		 * @throws IOException
		 */
		public void writeSolution(String solutionFileName) throws IOException {
			File solutionFile = new File(solutionFileName);
			FaultSystemIO.writeSol(solution, solutionFile);
		}
	}
}


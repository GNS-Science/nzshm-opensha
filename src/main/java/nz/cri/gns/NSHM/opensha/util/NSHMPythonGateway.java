package nz.cri.gns.NSHM.opensha.util;

import java.io.File;
import java.io.IOException;

import nz.cri.gns.NSHM.opensha.hazard.NSHMHazardCalculatorBuilder;
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

	static CachedNSHMRuptureSetBuilder builder;
	static CachedNSHMInversionRunner runner;
	static NSHMHazardCalculatorBuilder calculator = new NSHMHazardCalculatorBuilder();

	public static CachedNSHMRuptureSetBuilder getBuilder() {
		builder = new CachedNSHMRuptureSetBuilder();
		return builder;
	}

	public static NSHMInversionRunner getRunner() {
		runner = new CachedNSHMInversionRunner();
		return runner;
	}

	public static NSHMHazardCalculatorBuilder getCalculator() {
		return calculator;
	}

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
		 * Sets the FaultModel file for all crustal faults
		 * 
		 * @param fsdFileName the XML FaultSection data file containing source fault
		 *                    information
		 * @return this builder
		 */
		public CachedNSHMRuptureSetBuilder setFaultModelFile(String fsdFileName) {
			setFaultModelFile(new File(fsdFileName));
			return this;
		}

		/**
		 * Sets the subduction fault. At the moment, only one fault can be set.
		 * 
		 * @param faultName The name fo the fault.
		 * @param fileName  the CSV file containing all sections.
		 * @return this builder
		 */
		public CachedNSHMRuptureSetBuilder setSubductionFault(String faultName, String fileName) {
			setSubductionFault(faultName, new File(fileName));
			return this;
		}

		/**
		 * Caches the results of the build
		 */
		@Override
		public SlipAlongRuptureModelRupSet buildRuptureSet() throws DocumentException, IOException {
			ruptureSet = super.buildRuptureSet();
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
	static class CachedNSHMInversionRunner extends NSHMInversionRunner {
		FaultSystemSolution solution = null;

		/**
		 * like run(File ruptureSetFile), but caches the result
		 * 
		 * @param ruptureSetFileName the name of a rupture set file
		 * @return the solution
		 * @throws IOException
		 * @throws DocumentException
		 */
		public FaultSystemSolution runInversion() throws IOException, DocumentException {
			solution = super.runInversion();
			return solution;
		}

		/**
		 * Writes the cached solution (see the run method) to file.
		 * 
		 * @param solutionFileName the file name
		 * @throws IOException
		 */
		public void writeSolution(String solutionFileName) throws IOException {
			File solutionFile = new File(solutionFileName);
			FaultSystemIO.writeSol(solution, solutionFile);
		}
	}
}

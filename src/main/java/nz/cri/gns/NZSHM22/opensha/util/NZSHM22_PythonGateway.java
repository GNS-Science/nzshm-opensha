package nz.cri.gns.NZSHM22.opensha.util;

import java.io.File;
import java.io.IOException;

//import nz.cri.gns.NZSHM22.util.NZSHM22_InversionDiagnosticsReportBuilder;
import org.dom4j.DocumentException;

import nz.cri.gns.NZSHM22.opensha.hazard.NZSHM22_HazardCalculatorBuilder;
import nz.cri.gns.NZSHM22.opensha.inversion.NZSHM22_InversionRunner;
import nz.cri.gns.NZSHM22.opensha.ruptures.NZSHM22_RuptureSetBuilder;
import py4j.GatewayServer;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.SlipAlongRuptureModelRupSet;
import scratch.UCERF3.utils.FaultSystemIO;

/**
 * A py4j gateway for building ruptures and running inversions.
 * 
 */
public class NZSHM22_PythonGateway {

	static CachedNSHMRuptureSetBuilder builder;
	static CachedNSHMInversionRunner runner;
	static NZSHM22_HazardCalculatorBuilder calculator = new NZSHM22_HazardCalculatorBuilder();

	/**
	 * Get a new cached rupture set builder. Note that we want a new builder for
	 * each new rupture set to ensure the setup is clean. The produced rupture set
	 * is cached to allow inspection etc.
	 */
	public static CachedNSHMRuptureSetBuilder getBuilder() {
		builder = new CachedNSHMRuptureSetBuilder();
		return builder;
	}

	/**
	 * Get a new cached inversion runner. For now we want a new one to ensure the
	 * setup is clean, but this can maybe be optimised. The produced solution is
	 * cached to allow inspection etc.
	 */
	public static NZSHM22_InversionRunner getRunner() {
		runner = new CachedNSHMInversionRunner();
		return runner;
	}

	public static NZSHM22_HazardCalculatorBuilder getCalculator() {
		return calculator;
	}

	public static void main(String[] args) {
		NZSHM22_PythonGateway app = new NZSHM22_PythonGateway();

		// app is now the gateway.entry_point
		GatewayServer server = new GatewayServer(app);
		server.start();
	}

	/**
	 * Provide a little help for python clients using NZSHM22_RuptureSetBuilder
	 * 
	 */
	static class CachedNSHMRuptureSetBuilder extends NZSHM22_RuptureSetBuilder {
		SlipAlongRuptureModelRupSet ruptureSet;

		/**
		 * 
		 * @param permutationStrategyClass one of 'DOWNDIP', 'POINTS', 'UCERF3'
		 * @return this
		 */
		public NZSHM22_RuptureSetBuilder setPermutationStrategy(String permutationStrategyClass) {

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
	 * Python helper that wraps NZSHM22_InversionRunner
	 */
	static class CachedNSHMInversionRunner extends NZSHM22_InversionRunner {
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

	// TODO: restore this with the required upstream changes in opensha-ucerf3
//    public static NZSHM22_InversionDiagnosticsReportBuilder createReportBuilder() {
//        return new NZSHM22_InversionDiagnosticsReportBuilder();
//    }

}

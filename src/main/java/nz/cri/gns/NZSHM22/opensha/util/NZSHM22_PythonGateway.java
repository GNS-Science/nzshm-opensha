package nz.cri.gns.NZSHM22.opensha.util;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

//import nz.cri.gns.NZSHM22.util.NZSHM22_InversionDiagnosticsReportBuilder;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.ruptures.NZSHM22_AbstractRuptureSetBuilder;
import nz.cri.gns.NZSHM22.opensha.ruptures.NZSHM22_CoulombRuptureSetBuilder;
import org.dom4j.DocumentException;

import nz.cri.gns.NZSHM22.opensha.hazard.NZSHM22_HazardCalculatorBuilder;
import nz.cri.gns.NZSHM22.opensha.inversion.NZSHM22_InversionRunner;
import nz.cri.gns.NZSHM22.opensha.ruptures.NZSHM22_AzimuthalRuptureSetBuilder;
import py4j.GatewayServer;
import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.utils.FaultSystemIO;

/**
 * A py4j gateway for building ruptures and running inversions.
 */
public class NZSHM22_PythonGateway {

    static NZSHM22_AbstractRuptureSetBuilder builder;
    static FaultSystemRupSet ruptureSet;
    static CachedNSHMInversionRunner runner;
    static NZSHM22_HazardCalculatorBuilder calculator = new NZSHM22_HazardCalculatorBuilder();

    /**
     * Get a new rupture set builder. Note that we want a new builder for
     * each new rupture set to ensure the setup is clean. Use the method provided by the gateway
     * to cache the produced rupture set to allow inspection etc.
     */
    public static NZSHM22_AbstractRuptureSetBuilder getBuilder(String type) {
        switch (type) {
            case "azimuth":
                builder = new NZSHM22_AzimuthalRuptureSetBuilder();
                break;
            case "coulomb":
                builder = new NZSHM22_CoulombRuptureSetBuilder();
                break;
        }
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

        String DEFAULT_PORT = "25333";
        String port = Optional.of(System.getenv("NZSHM22_APP_PORT")).orElse(DEFAULT_PORT);
        System.out.println("NZSHM22 Java gateway listening on port; " + port);

        // app is now the gateway.entry_point
        GatewayServer server = new GatewayServer(app, Integer.parseInt(port));
        server.start();
    }

    /**
     * Chooses a known fault model for the rupture set builder
     *
     * @param faultModel the name of a known fault model
     * @return this object
     */
    public NZSHM22_PythonGateway setFaultModel(String faultModel) {
        builder.setFaultModel(NZSHM22_FaultModels.valueOf(faultModel));
        return this;
    }

    /**
     * Sets the permutation strategy of the builder
     *
     * @param permutationStrategyClass one of 'DOWNDIP', 'POINTS', 'UCERF3'
     * @return this
     */
    public NZSHM22_PythonGateway setPermutationStrategy(String permutationStrategyClass) {
        ((NZSHM22_AzimuthalRuptureSetBuilder) builder).setPermutationStrategy(NZSHM22_AzimuthalRuptureSetBuilder.RupturePermutationStrategy.valueOf(permutationStrategyClass));
        return this;
    }

    /**
     * Sets the builder's FaultModel file for all crustal faults
     *
     * @param fsdFileName the XML FaultSection data file containing source fault
     *                    information
     * @return this builder
     */
    public NZSHM22_PythonGateway setFaultModelFile(String fsdFileName) {
        builder.setFaultModelFile(new File(fsdFileName));
        return this;
    }

    /**
     * Sets the subduction fault. At the moment, only one fault can be set.
     *
     * @param faultName The name fo the fault.
     * @param fileName  the CSV file containing all sections.
     * @return this builder
     */
    public NZSHM22_PythonGateway setSubductionFault(String faultName, String fileName) {
        builder.setSubductionFault(faultName, new File(fileName));
        return this;
    }

    /**
     * Caches the results of the build
     */

    public FaultSystemRupSet buildRuptureSet() throws DocumentException, IOException {
        ruptureSet = builder.buildRuptureSet();
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

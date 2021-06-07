package nz.cri.gns.NZSHM22.opensha.util;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

//import nz.cri.gns.NZSHM22.util.NZSHM22_InversionDiagnosticsReportBuilder;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.ruptures.NZSHM22_AbstractRuptureSetBuilder;
import nz.cri.gns.NZSHM22.opensha.ruptures.NZSHM22_CoulombRuptureSetBuilder;
import nz.cri.gns.NZSHM22.opensha.ruptures.NZSHM22_SlipEnabledRuptureSet;
import org.dom4j.DocumentException;

import nz.cri.gns.NZSHM22.opensha.hazard.NZSHM22_HazardCalculatorBuilder;
import nz.cri.gns.NZSHM22.opensha.inversion.NZSHM22_InversionRunner;
import nz.cri.gns.NZSHM22.opensha.ruptures.NZSHM22_AzimuthalRuptureSetBuilder;
import py4j.GatewayServer;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.utils.FaultSystemIO;

/**
 * A py4j gateway for building ruptures and running inversions.
 */
public class NZSHM22_PythonGateway {

	static NZSHM22_AbstractRuptureSetBuilder builder;
    static CachedNSHMInversionRunner runner;
    static NZSHM22_HazardCalculatorBuilder calculator = new NZSHM22_HazardCalculatorBuilder();

    public static NZSHM22_AzimuthalRuptureSetBuilder getAzimuthalRuptureSetBuilder(){
        NZSHM22_AzimuthalRuptureSetBuilder azBuilder = new NZSHM22_CachedAzimuthalRuptureSetBuilder();
        builder = azBuilder;
        return azBuilder;
    }

    public static NZSHM22_CoulombRuptureSetBuilder getCoulombRuptureSetBuilder(){
        NZSHM22_CoulombRuptureSetBuilder coulBuilder = new NZSHM22_CachedCoulombRuptureSetBuilder();
        builder = coulBuilder;
        return coulBuilder;
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

    public static MFDPlotBuilder getMFDPlotBuilder(){
        return new MFDPlotBuilder();
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
     * Provide a little help for python clients using NZSHM22_AzimuthalRuptureSetBuilder
     */
    static class NZSHM22_CachedAzimuthalRuptureSetBuilder extends NZSHM22_AzimuthalRuptureSetBuilder {
        NZSHM22_SlipEnabledRuptureSet ruptureSet;

        /**
         * Chooses a known fault model.
         * @param faultModel the name of a known fault model
         * @return this object
         */
        public NZSHM22_CachedAzimuthalRuptureSetBuilder setFaultModel(String faultModel){
            setFaultModel(NZSHM22_FaultModels.valueOf(faultModel));
            return this;
        }

        /**
         *
         * @param permutationStrategyClass one of 'DOWNDIP', 'POINTS', 'UCERF3'
         * @return this
         */
        public NZSHM22_CachedAzimuthalRuptureSetBuilder setPermutationStrategy(String permutationStrategyClass) {

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
        public NZSHM22_CachedAzimuthalRuptureSetBuilder setFaultModelFile(String fsdFileName) {
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
        public NZSHM22_CachedAzimuthalRuptureSetBuilder setSubductionFault(String faultName, String fileName) {
            setSubductionFault(faultName, new File(fileName));
            return this;
        }

        /**
         * Caches the results of the build
         */
        @Override
        public NZSHM22_SlipEnabledRuptureSet buildRuptureSet() throws DocumentException, IOException {
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
     * Provide a little help for python clients using NZSHM22_AzimuthalRuptureSetBuilder
     */
    static class NZSHM22_CachedCoulombRuptureSetBuilder extends NZSHM22_CoulombRuptureSetBuilder {
        NZSHM22_SlipEnabledRuptureSet ruptureSet;

        /**
         * Chooses a known fault model.
         * @param faultModel the name of a known fault model
         * @return this object
         */
        public NZSHM22_CachedCoulombRuptureSetBuilder setFaultModel(String faultModel){
            setFaultModel(NZSHM22_FaultModels.valueOf(faultModel));
            return this;
        }

        /**
         * Sets the FaultModel file for all crustal faults
         *
         * @param fsdFileName the XML FaultSection data file containing source fault
         *                    information
         * @return this builder
         */
        public NZSHM22_CachedCoulombRuptureSetBuilder setFaultModelFile(String fsdFileName) {
            setFaultModelFile(new File(fsdFileName));
            return this;
        }

        /**
         * Caches the results of the build
         */
        @Override
        public NZSHM22_SlipEnabledRuptureSet buildRuptureSet() throws DocumentException, IOException {
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

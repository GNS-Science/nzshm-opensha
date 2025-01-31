package nz.cri.gns.NZSHM22.opensha.util;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_ScalingRelationshipNode;
import nz.cri.gns.NZSHM22.opensha.hazard.NZSHM22_GridHazardCalculator;
import nz.cri.gns.NZSHM22.opensha.hazard.NZSHM22_HazardCalculator;
import nz.cri.gns.NZSHM22.opensha.hazard.NZSHM22_HazardCalculatorBuilder;
import nz.cri.gns.NZSHM22.opensha.inversion.CrustalMFDRunner;
import nz.cri.gns.NZSHM22.opensha.inversion.NZSHM22_CrustalInversionRunner;
import nz.cri.gns.NZSHM22.opensha.inversion.NZSHM22_SubductionInversionRunner;
import nz.cri.gns.NZSHM22.opensha.ruptures.NZSHM22_AbstractRuptureSetBuilder;
import nz.cri.gns.NZSHM22.opensha.ruptures.NZSHM22_CoulombRuptureSetBuilder;
import nz.cri.gns.NZSHM22.opensha.ruptures.NZSHM22_SubductionRuptureSetBuilder;
import nz.cri.gns.NZSHM22.opensha.timeDependent.TimeDependentRatesGenerator;
import nz.cri.gns.NZSHM22.util.GitVersion;
import nz.cri.gns.NZSHM22.util.NZSHM22_ReportPageGen;
import org.dom4j.DocumentException;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.RupSetScalingRelationship;
import py4j.GatewayServer;

/** A py4j gateway for building ruptures and running inversions. */
public class NZSHM22_PythonGateway {

    static NZSHM22_AbstractRuptureSetBuilder builder;
    static CachedCrustalInversionRunner crustalInversionRunner;
    static CachedSubductionInversionRunner subductionInversionRunner;
    static NZSHM22_HazardCalculatorBuilder hazardCalcBuilder;
    static NZSHM22_GridHazardCalculator gridHazCalc;

    public static NZSHM22_CachedCoulombRuptureSetBuilder getCoulombRuptureSetBuilder() {
        NZSHM22_CachedCoulombRuptureSetBuilder coulBuilder =
                new NZSHM22_CachedCoulombRuptureSetBuilder();
        builder = coulBuilder;
        return coulBuilder;
    }

    public static NZSHM22_CachedSubductionRuptureSetBuilder getSubductionRuptureSetBuilder() {
        NZSHM22_CachedSubductionRuptureSetBuilder subBuilder =
                new NZSHM22_CachedSubductionRuptureSetBuilder();
        builder = subBuilder;
        return subBuilder;
    }

    /**
     * Get a new cached inversion runner. For now we want a new one to ensure the setup is clean,
     * but this can maybe be optimised. The produced solution is cached to allow inspection etc.
     */
    public static CachedCrustalInversionRunner getCrustalInversionRunner() {
        crustalInversionRunner = new CachedCrustalInversionRunner();
        return crustalInversionRunner;
    }

    /**
     * Get a new cached inversion runner. For now we want a new one to ensure the setup is clean,
     * but this can maybe be optimised. The produced solution is cached to allow inspection etc.
     */
    public static CachedSubductionInversionRunner getSubductionInversionRunner() {
        subductionInversionRunner = new CachedSubductionInversionRunner();
        return subductionInversionRunner;
    }

    public static NZSHM22_HazardCalculatorBuilder getHazardCalculatorBuilder() {
        hazardCalcBuilder = new NZSHM22_HazardCalculatorBuilder();
        return hazardCalcBuilder;
    }

    public static NZSHM22_GridHazardCalculator getGridHazardCalculator(
            NZSHM22_HazardCalculator calculator) {
        gridHazCalc = new NZSHM22_GridHazardCalculator(calculator);
        return gridHazCalc;
    }

    /**
     * Returns a new TimeDependentRatesGenerator.
     *
     * @return the generator
     */
    public static TimeDependentRatesGenerator getTimeDependentRatesGenerator() {
        return new TimeDependentRatesGenerator();
    }

    // move these up and add comments

    /**
     * Returns a new MFDPlotBuilder to create MFD plots
     *
     * @return
     */
    public static MFDPlotBuilder getMFDPlotBuilder() {
        return new MFDPlotBuilder();
    }

    /**
     * Returns a wrapper around the new (modular) ReportPageGen
     *
     * @return
     */
    public static NZSHM22_ReportPageGen getReportPageGen() {
        return new NZSHM22_ReportPageGen();
    }

    public static CrustalMFDRunner getCrustalMFDRunner() {
        return new CrustalMFDRunner();
    }

    public static GitVersion getGitVersion() {
        return new GitVersion();
    }

    public static void main(String[] args) {

        GitVersion version = new GitVersion();
        System.out.println("NZSHM22_PythonGateway tagged version: " + version.getVersion());
        System.out.println("opensha git ref: " + version.getOpenshaGitRef());

        NZSHM22_PythonGateway app = new NZSHM22_PythonGateway();

        String DEFAULT_PORT = "25333";
        String port = Optional.of(System.getenv("NZSHM22_APP_PORT")).orElse(DEFAULT_PORT);
        System.out.println("NZSHM22 Java gateway listening on port; " + port);

        // app is now the gateway.entry_point
        GatewayServer server = new GatewayServer(app, Integer.parseInt(port));
        server.start();
    }

    /** Provide a little help for python clients */
    public static class NZSHM22_CachedCoulombRuptureSetBuilder
            extends NZSHM22_CoulombRuptureSetBuilder {
        FaultSystemRupSet ruptureSet;

        /**
         * Chooses a known fault model.
         *
         * @param faultModel the name of a known fault model
         * @return this object
         */
        public NZSHM22_CachedCoulombRuptureSetBuilder setFaultModel(String faultModel) {
            setFaultModel(NZSHM22_FaultModels.valueOf(faultModel));
            return this;
        }

        /**
         * Sets the FaultModel file for all crustal faults
         *
         * @param fsdFileName the XML FaultSection data file containing source fault information
         * @return this builder
         */
        public NZSHM22_CachedCoulombRuptureSetBuilder setFaultModelFile(String fsdFileName) {
            setFaultModelFile(new File(fsdFileName));
            return this;
        }

        /** Caches the results of the build */
        @Override
        public FaultSystemRupSet buildRuptureSet() throws DocumentException, IOException {
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
            ruptureSet.write(rupSetFile);
        }
    }

    /** Provide a little help for python clients using NZSHM22_SubductionRuptureSetBuilder */
    public static class NZSHM22_CachedSubductionRuptureSetBuilder
            extends NZSHM22_SubductionRuptureSetBuilder {
        FaultSystemRupSet ruptureSet;

        /** Caches the results of the build */
        @Override
        public FaultSystemRupSet buildRuptureSet() throws DocumentException, IOException {
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
            ruptureSet.write(rupSetFile);
        }
    }

    /** Python helper that wraps NZSHM22_InversionRunner */
    public static class CachedCrustalInversionRunner extends NZSHM22_CrustalInversionRunner {
        private FaultSystemSolution solution;

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
            solution.write(solutionFile);
        }
    }
    /** Python helper that wraps NZSHM22_InversionRunner */
    public static class CachedSubductionInversionRunner extends NZSHM22_SubductionInversionRunner {
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
            solution.write(solutionFile);
        }
    }

    /**
     * Returns a RupSetScalingRelationship that can be configured and passed on to the inversion
     * runner.
     *
     * @param name
     * @return
     */
    public static RupSetScalingRelationship getScalingRelationship(String name) {
        return NZSHM22_ScalingRelationshipNode.createRelationShip(name);
    }
}

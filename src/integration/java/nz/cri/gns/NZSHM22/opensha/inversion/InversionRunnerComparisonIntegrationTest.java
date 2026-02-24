package nz.cri.gns.NZSHM22.opensha.inversion;

import static nz.cri.gns.NZSHM22.util.TestHelpers.createRupSet;
import static org.junit.Assert.*;

import cern.colt.matrix.tdouble.impl.SparseDoubleMatrix2D;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.*;
import nz.cri.gns.NZSHM22.opensha.ruptures.FaultSectionProperties;
import nz.cri.gns.NZSHM22.opensha.util.NZSHM22_PythonGateway;
import nz.cri.gns.NZSHM22.opensha.util.ParameterRunner;
import nz.cri.gns.NZSHM22.opensha.util.Parameters;
import nz.cri.gns.NZSHM22.util.TestHelpers;
import org.dom4j.DocumentException;
import org.junit.Test;
import org.opensha.commons.util.io.archive.ArchiveInput;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionTargetMFDs;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;

public class InversionRunnerComparisonIntegrationTest {

    public static final double DELTA = 1e-10;

    public void compareConstraints(
            int numRups, InversionConstraint constraintA, InversionConstraint constraintB) {

        System.out.println(
                "Comparing constraints "
                        + constraintA.getShortName()
                        + " and "
                        + constraintB.getShortName());

        assertEquals(constraintA.getName(), constraintB.getName());

        assertEquals(constraintA.getRange(0), constraintB.getRange(0));

        int rowsA = constraintA.getNumRows();
        int rowsB = constraintB.getNumRows();

        assertEquals(rowsA, rowsB);
        SparseDoubleMatrix2D aA = new SparseDoubleMatrix2D(rowsA, numRups);
        SparseDoubleMatrix2D aB = new SparseDoubleMatrix2D(rowsA, numRups);
        double[] dA = new double[rowsA];
        double[] dB = new double[rowsA];

        constraintA.encode(aA, dA, 0);
        constraintB.encode(aB, dB, 0);

        assertArrayEquals(dA, dB, DELTA);
        // assertEquals(aA.toString(), aB.toString());

        compareMatrices(aA, aB);
    }

    public void compareConstraints(
            int numRups,
            List<InversionConstraint> constraintsA,
            List<InversionConstraint> constraintsB) {
        assertEquals(constraintsA.size(), constraintsB.size());
        for (int c = 0; c < constraintsA.size(); c++) {
            compareConstraints(numRups, constraintsA.get(c), constraintsB.get(c));
        }
    }

    public static void compareRuptureSets(
            FaultSystemRupSet jointRupSet, FaultSystemRupSet crustalRupSet) {
        assertArrayEquals(jointRupSet.getMagForAllRups(), crustalRupSet.getMagForAllRups(), DELTA);

        double[] slipsA =
                jointRupSet.getFaultSectionDataList().stream()
                        .mapToDouble(FaultSection::getOrigAveSlipRate)
                        .toArray();
        double[] slipsB =
                crustalRupSet.getFaultSectionDataList().stream()
                        .mapToDouble(FaultSection::getOrigAveSlipRate)
                        .toArray();

        assertArrayEquals(slipsA, slipsB, DELTA);

        assertArrayEquals(
                jointRupSet.getSlipRateForAllSections(),
                crustalRupSet.getSlipRateForAllSections(),
                DELTA);
        assertArrayEquals(
                jointRupSet.getSlipRateStdDevForAllSections(),
                jointRupSet.getSlipRateStdDevForAllSections(),
                DELTA);

        boolean crustal = FaultSectionProperties.isCrustal(jointRupSet.getFaultSectionData(0));

        PartitionMfds partitionMfds = jointRupSet.getModule(PartitionMfds.class);
        InversionTargetMFDs jointMfds =
                partitionMfds.get(
                        crustal ? PartitionPredicate.CRUSTAL : PartitionPredicate.HIKURANGI);
        InversionTargetMFDs oldMfds =
                FaultSectionProperties.isCrustal(jointRupSet.getFaultSectionData(0))
                        ? crustalRupSet.getModule(NZSHM22_CrustalInversionTargetMFDs.class)
                        : crustalRupSet.getModule(NZSHM22_SubductionInversionTargetMFDs.class);

        assertEquals(jointMfds.getTotalOnFaultMFD(), oldMfds.getTotalOnFaultMFD());
        assertEquals(jointMfds.getTotalOnFaultSubSeisMFD(), oldMfds.getTotalOnFaultSubSeisMFD());
        assertEquals(
                jointMfds.getTotalOnFaultSupraSeisMFD(), oldMfds.getTotalOnFaultSupraSeisMFD());
        assertEquals(jointMfds.getTrulyOffFaultMFD(), oldMfds.getTrulyOffFaultMFD());

        List<? extends IncrementalMagFreqDist> jointConstraints = jointMfds.getMFD_Constraints();
        List<? extends IncrementalMagFreqDist> crustalConstraints = oldMfds.getMFD_Constraints();

        assertEquals(jointConstraints.size(), crustalConstraints.size());

        for (int i = 0; i < jointConstraints.size(); i++) {
            assertEquals(jointConstraints.get(i).toString(), crustalConstraints.get(i).toString());
        }
    }

    // function to compare two matrices
    public void compareMatrices(SparseDoubleMatrix2D a, SparseDoubleMatrix2D b) {
        assertEquals(a.rows(), b.rows());
        assertEquals(a.columns(), b.columns());

        for (int r = 0; r < a.rows(); r++) {
            for (int c = 0; c < a.columns(); c++) {
                assertEquals(a.getQuick(r, c), b.getQuick(r, c), DELTA);
            }
        }
    }

    @Test
    public void testInversionRunnerGeneratesIdenticalRatesToCrustalRunner() throws Exception {
        // Configure InversionRunner with JSON config
        InversionRunner inversionRunner =
                configureInversionRunnerForCrustal(createCrustalTestRupSet());

        // Configure NZSHM22_CrustalInversionRunner with equivalent params
        NZSHM22_AbstractInversionRunner crustalRunner =
                configureCrustalRunner(createCrustalTestRupSet());

        // Run both inversions
        FaultSystemSolution inversionSolution = inversionRunner.run();
        FaultSystemSolution crustalSolution = crustalRunner.runInversion();

        compareRuptureSets(inversionSolution.getRupSet(), crustalSolution.getRupSet());

        compareConstraints(
                inversionSolution.getRateForAllRups().length,
                inversionRunner.getConfig().annealing.inversionInputGenerator.getConstraints(),
                crustalRunner.getInversionInputGenerator().getConstraints());

        assertArrayEquals(
                inversionSolution.getRateForAllRups(), crustalSolution.getRateForAllRups(), DELTA);
    }

    @Test
    public void testInversionRunnerGeneratesIdenticalRatesToSubductionRunner() throws Exception {
        // Configure InversionRunner with JSON config
        InversionRunner inversionRunner =
                configureInversionRunnerForSubduction(createSubductionTestRupSet());

        // Configure NZSHM22_CrustalInversionRunner with equivalent params
        NZSHM22_AbstractInversionRunner crustalRunner =
                configureSubductionRunner(createSubductionTestRupSet());

        // Run both inversions
        FaultSystemSolution inversionSolution = inversionRunner.run();
        FaultSystemSolution crustalSolution = crustalRunner.runInversion();

        compareRuptureSets(inversionSolution.getRupSet(), crustalSolution.getRupSet());

        compareConstraints(
                inversionSolution.getRateForAllRups().length,
                inversionRunner.getConfig().annealing.inversionInputGenerator.getConstraints(),
                crustalRunner.getInversionInputGenerator().getConstraints());

        assertArrayEquals(
                inversionSolution.getRateForAllRups(), crustalSolution.getRateForAllRups(), DELTA);
    }

    private FaultSystemRupSet createCrustalTestRupSet() throws DocumentException, IOException {
        FaultSystemRupSet rupSet =
                createRupSet(
                        NZSHM22_FaultModels.CFM_1_0A_DOM_ALL,
                        ScalingRelationships.SHAW_2009_MOD,
                        List.of(List.of(0, 1), List.of(5, 6, 7, 8, 9)));
        return rupSet;
    }

    private FaultSystemRupSet createSubductionTestRupSet() throws DocumentException, IOException {
        FaultSystemRupSet rupSet =
                createRupSet(
                        NZSHM22_FaultModels.SBD_0_4_HKR_LR_30,
                        ScalingRelationships.SHAW_2009_MOD,
                        List.of(
                                List.of(0, 1),
                                // we need a rupture with mag between 8 and 8.1, so this one is long
                                List.of(
                                        5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
                                        21, 22)));
        return rupSet;
    }

    private InversionRunner configureInversionRunnerForCrustal(FaultSystemRupSet testRupSet)
            throws IOException {
        // Load the test-specific crustal configuration
        String configContent =
                Files.readString(
                        Path.of("src/main/resources/parameters/crustal-reproducible.jsonc"));
        Config config = ConfigModule.fromJson(configContent);
        config.setRuptureSet(testRupSet);
        return new InversionRunner(config);
    }

    private InversionRunner configureInversionRunnerForSubduction(FaultSystemRupSet testRupSet)
            throws IOException {
        // Load the test-specific crustal configuration
        String configContent =
                Files.readString(
                        Path.of("src/main/resources/parameters/Hikurangi-reproducible.jsonc"));
        Config config = ConfigModule.fromJson(configContent);
        config.setRuptureSet(testRupSet);
        return new InversionRunner(config);
    }

    private NZSHM22_AbstractInversionRunner configureCrustalRunner(FaultSystemRupSet testRupSet)
            throws IOException {
        ArchiveInput archiveInput = TestHelpers.archiveInput(testRupSet);

        ParameterRunner parameterRunner = new ParameterRunner(Parameters.NZSHM22.INVERSION_CRUSTAL);
        NZSHM22_CrustalInversionRunner runner = NZSHM22_PythonGateway.getCrustalInversionRunner();
        parameterRunner.ensurePaths();
        parameterRunner.setUpCrustalInversionRunner(runner);

        runner.setRuptureSetArchiveInput(archiveInput);
        runner.setExcludeRupturesBelowMinMag(true);
        runner.setRepeatable(true);
        runner.setIterationCompletionCriteria(100);
        runner.setSelectionIterations(1);
        runner.setInversionAveraging(false);

        return runner;
    }

    private NZSHM22_AbstractInversionRunner configureSubductionRunner(FaultSystemRupSet testRupSet)
            throws IOException {
        ArchiveInput archiveInput = TestHelpers.archiveInput(testRupSet);

        ParameterRunner parameterRunner =
                new ParameterRunner(Parameters.NZSHM22.INVERSION_HIKURANGI);
        NZSHM22_SubductionInversionRunner runner =
                NZSHM22_PythonGateway.getSubductionInversionRunner();
        parameterRunner.ensurePaths();
        parameterRunner.setUpSubductionInversionRunner(runner);

        runner.setRuptureSetArchiveInput(archiveInput);
        runner.setExcludeRupturesBelowMinMag(true);
        runner.setRepeatable(true);
        runner.setIterationCompletionCriteria(100);
        runner.setSelectionIterations(1);
        runner.setInversionAveraging(false);

        return runner;
    }
}

package nz.cri.gns.NZSHM22.opensha.inversion;

import static nz.cri.gns.NZSHM22.util.TestHelpers.createRupSet;
import static org.junit.Assert.*;

import cern.colt.matrix.tdouble.impl.SparseDoubleMatrix2D;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.Config;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.ConfigModule;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.InversionRunner;
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
import org.opensha.sha.faultSurface.FaultSection;
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
        assertEquals(aA.toString(), aB.toString());
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

    public static void compareRuptureSets(FaultSystemRupSet rupSetA, FaultSystemRupSet rupSetB) {
        assertArrayEquals(rupSetA.getMagForAllRups(), rupSetB.getMagForAllRups(), DELTA);

        double[] slipsA =
                rupSetA.getFaultSectionDataList().stream()
                        .mapToDouble(FaultSection::getOrigAveSlipRate)
                        .toArray();
        double[] slipsB =
                rupSetB.getFaultSectionDataList().stream()
                        .mapToDouble(FaultSection::getOrigAveSlipRate)
                        .toArray();

        assertArrayEquals(slipsA, slipsB, DELTA);

        assertArrayEquals(
                rupSetA.getSlipRateForAllSections(), rupSetB.getSlipRateForAllSections(), DELTA);
        assertArrayEquals(
                rupSetA.getSlipRateStdDevForAllSections(),
                rupSetA.getSlipRateStdDevForAllSections(),
                DELTA);
    }

    @Test
    public void testInversionRunnerGeneratesIdenticalRatesToCrustalRunner() throws Exception {
        // Configure InversionRunner with JSON config
        InversionRunner inversionRunner = configureInversionRunner(createTestRupSet());

        // Configure NZSHM22_CrustalInversionRunner with equivalent params
        NZSHM22_AbstractInversionRunner crustalRunner = configureCrustalRunner(createTestRupSet());

        // Run both inversions
        FaultSystemSolution inversionSolution = inversionRunner.run();
        FaultSystemSolution crustalSolution = crustalRunner.runInversion();

        compareRuptureSets(inversionSolution.getRupSet(), crustalSolution.getRupSet());

        compareConstraints(
                inversionSolution.getRateForAllRups().length,
                inversionRunner.getConfig().annealing.inversionInputGenerator.getConstraints(),
                crustalRunner.getInversionInputGenerator().getConstraints());

        // compare matrices
        assertEquals(
                inversionRunner.getConfig().annealing.inversionInputGenerator.getA().toString(),
                crustalRunner.getInversionInputGenerator().getA().toString());

        assertArrayEquals(
                inversionRunner.getConfig().annealing.inversionInputGenerator.getD(),
                crustalRunner.getInversionInputGenerator().getD(),
                0.000001);

        assertArrayEquals(
                inversionSolution.getRateForAllRups(),
                crustalSolution.getRateForAllRups(),
                0.000000001);
    }

    private FaultSystemRupSet createTestRupSet() throws DocumentException, IOException {
        FaultSystemRupSet rupSet =
                createRupSet(
                        NZSHM22_FaultModels.CFM_1_0A_DOM_ALL,
                        ScalingRelationships.SHAW_2009_MOD,
                        List.of(List.of(0, 1), List.of(5, 6, 7, 8, 9)));
        return rupSet;
    }

    private InversionRunner configureInversionRunner(FaultSystemRupSet testRupSet)
            throws IOException {
        // Load the test-specific crustal configuration
        String configContent =
                Files.readString(
                        Path.of("src/main/resources/parameters/crustal-reproducible.jsonc"));
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
}

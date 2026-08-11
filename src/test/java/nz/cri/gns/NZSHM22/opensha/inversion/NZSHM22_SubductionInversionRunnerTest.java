package nz.cri.gns.NZSHM22.opensha.inversion;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.List;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.util.ParameterRunner;
import nz.cri.gns.NZSHM22.opensha.util.Parameters;
import nz.cri.gns.NZSHM22.opensha.util.TestHelpers;
import org.dom4j.DocumentException;
import org.junit.Test;
import org.opensha.commons.util.io.archive.ArchiveOutput;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;

public class NZSHM22_SubductionInversionRunnerTest {

    // returns a runner that can quickly create a solution
    public static NZSHM22_SubductionInversionRunner makeRunner()
            throws DocumentException, IOException {
        FaultSystemRupSet rupSet =
                TestHelpers.createRupSet(
                        NZSHM22_FaultModels.SBD_0_2_HKR_LR_30,
                        ScalingRelationships.TMG_SUB_2017,
                        List.of(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9), List.of(4, 5, 6, 10, 11, 12)));
        ArchiveOutput archiveOutput = new ArchiveOutput.InMemoryZipOutput(true);
        rupSet.getArchive().write(archiveOutput);

        ParameterRunner parameterRunner =
                new ParameterRunner(Parameters.NZSHM22.INVERSION_HIKURANGI);
        NZSHM22_SubductionInversionRunner runner = new NZSHM22_SubductionInversionRunner();
        parameterRunner.setUpSubductionInversionRunner(runner);
        // The real deformation model is keyed by the real Hikurangi fault section ids and does
        // not apply to this synthetic rupture set; leaving it unset makes deformation model
        // application a no-op (see NZSHM22_InversionFaultSystemRuptSet.applyDeformationModel).
        runner.deformationModel = null;
        runner.setIterationCompletionCriteria(100)
                .setSelectionIterations(1)
                .setRepeatable(true)
                .setInversionAveraging(false)
                .setRuptureSetArchiveInput(archiveOutput.getCompletedInput());

        return runner;
    }

    @Test
    public void testRunnerModuleAttachedToSolution() throws DocumentException, IOException {
        NZSHM22_SubductionInversionRunner runner = makeRunner();

        FaultSystemSolution solution = runner.runInversion();
        String expectedJson = runner.toJson();

        NZSHM22_InversionRunnerModule module =
                solution.getModule(NZSHM22_InversionRunnerModule.class);
        assertNotNull(module);
        assertTrue(module.getRunner() instanceof NZSHM22_SubductionInversionRunner);
        assertEquals(expectedJson, module.getRunner().toJson());
    }

    @Test
    public void testFromJsonRoundTrip() throws DocumentException, IOException {
        NZSHM22_SubductionInversionRunner runner = makeRunner();
        NZSHM22_SubductionInversionRunner fromJson =
                NZSHM22_SubductionInversionRunner.fromJson(runner.toJson());
        assertEquals(runner.toJson(), fromJson.toJson());
    }
}

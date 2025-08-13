package nz.cri.gns.NZSHM22.opensha.inversion;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.List;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.util.ParameterRunner;
import nz.cri.gns.NZSHM22.opensha.util.Parameters;
import nz.cri.gns.NZSHM22.opensha.util.TestHelpers;
import org.dom4j.DocumentException;
import org.hamcrest.core.StringStartsWith;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensha.commons.util.io.archive.ArchiveOutput;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.UncertainDataConstraint;
import org.opensha.sha.earthquake.faultSysSolution.modules.PaleoseismicConstraintData;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;

public class NZSHM22_CrustalInversionRunnerTest {

    // returns a runner that can quickly create a solution
    public static NZSHM22_CrustalInversionRunner makeRunner()
            throws DocumentException, IOException {
        FaultSystemRupSet rupSet =
                TestHelpers.makeRupSet(
                        NZSHM22_FaultModels.CFM_1_0A_DOM_SANSTVZ,
                        ScalingRelationships.SHAW_2009_MOD);
        ArchiveOutput archiveOutput = new ArchiveOutput.InMemoryZipOutput(true);
        rupSet.getArchive().write(archiveOutput);

        ParameterRunner parameterRunner = new ParameterRunner(Parameters.NZSHM22.INVERSION_CRUSTAL);
        NZSHM22_CrustalInversionRunner runner = new NZSHM22_CrustalInversionRunner();
        parameterRunner.setUpCrustalInversionRunner(runner);
        runner.setIterationCompletionCriteria(100)
                .setSelectionIterations(1)
                .setRepeatable(true)
                .setInversionAveraging(false)
                .setRuptureSetArchiveInput(archiveOutput.getCompletedInput());

        return runner;
    }

    @Test
    public void testPaleoRatesSetup() throws DocumentException, IOException {

        // Approach: create two solutions: one with paleo rates enum, the other with the file that
        // backs that enum value. Then see if they are equivalent.

        NZSHM22_CrustalInversionRunner runner = makeRunner();
        runner.setPaleoRateConstraints(0.4, 0.5, "PALEO_RI_GEOLOGIC_MAY24", "NZSHM22_C_42");
        FaultSystemSolution solution = runner.runInversion();
        PaleoseismicConstraintData constraints =
                solution.getRupSet().getModule(PaleoseismicConstraintData.class);
        List<? extends UncertainDataConstraint.SectMappedUncertainDataConstraint> paleoConstraints =
                constraints.getPaleoRateConstraints();

        runner = makeRunner();
        runner.setPaleoRateConstraints(0.4, 0.5, "CUSTOM", "NZSHM22_C_42");
        runner.setPaleoRatesFile(
                "src/main/resources/paleoRates/NZNSHM_paleotimings_GEOLOGIC_24May.txt");
        solution = runner.runInversion();
        constraints = solution.getRupSet().getModule(PaleoseismicConstraintData.class);
        List<? extends UncertainDataConstraint.SectMappedUncertainDataConstraint>
                paleoFileConstraints = constraints.getPaleoRateConstraints();

        for (int index = 0; index < paleoConstraints.size(); index++) {
            UncertainDataConstraint.SectMappedUncertainDataConstraint fromEnum =
                    paleoConstraints.get(index);
            UncertainDataConstraint.SectMappedUncertainDataConstraint fromFile =
                    paleoFileConstraints.get(index);

            assertEquals(fromEnum.name, fromFile.name);
            assertEquals(fromEnum.dataLocation, fromFile.dataLocation);
            assertEquals(fromEnum.sectionIndex, fromFile.sectionIndex);
        }
    }

    @Rule public ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void testPaleoRatesDoubleUp() throws DocumentException, IOException {

        NZSHM22_CrustalInversionRunner runner = makeRunner();
        runner.setPaleoRateConstraints(0.4, 0.5, "PALEO_RI_GEOLOGIC_MAY24", "NZSHM22_C_42");
        runner.setPaleoRatesFile(
                "src/main/resources/paleoRates/NZNSHM_paleotimings_GEOLOGIC_24May.txt");

        exceptionRule.expect(IllegalStateException.class);
        exceptionRule.expectMessage(new StringStartsWith("Paleo rate location double-up"));
        runner.runInversion();
    }
}

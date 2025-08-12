package nz.cri.gns.NZSHM22.opensha.hazard;

import static org.junit.Assert.*;

import java.io.IOException;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.inversion.NZSHM22_CrustalInversionRunner;
import nz.cri.gns.NZSHM22.opensha.util.ParameterRunner;
import nz.cri.gns.NZSHM22.opensha.util.Parameters;
import nz.cri.gns.NZSHM22.util.TestHelpers;
import org.dom4j.DocumentException;
import org.junit.Test;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.util.io.archive.ArchiveInput;
import org.opensha.commons.util.io.archive.ArchiveOutput;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;

public class NZSHM22_HazardCalculatorBuilder_IntegrationTest {

    public static ArchiveInput makeSolution() throws DocumentException, IOException {
        FaultSystemRupSet rupSet =
                TestHelpers.makeRupSet(
                        NZSHM22_FaultModels.CFM_1_0A_DOM_SANSTVZ,
                        ScalingRelationships.SHAW_2009_MOD);
        ArchiveOutput archiveOutput = new ArchiveOutput.InMemoryZipOutput(true);
        rupSet.getArchive().write(archiveOutput);

        // rupSet.getArchive().write(new File("testrup.zip"));

        ParameterRunner parameterRunner = new ParameterRunner(Parameters.NZSHM22.INVERSION_CRUSTAL);
        NZSHM22_CrustalInversionRunner runner = new NZSHM22_CrustalInversionRunner();
        parameterRunner.setUpCrustalInversionRunner(runner);
        runner.setIterationCompletionCriteria(100)
                .setSelectionIterations(1)
                .setRepeatable(true)
                .setInversionAveraging(false)
                .setRuptureSetArchiveInput(archiveOutput.getCompletedInput());

        FaultSystemSolution solution = runner.runInversion();
        // solution.write(new File("testsolution.zip"));

        ArchiveOutput out = new ArchiveOutput.InMemoryZipOutput(true);
        solution.getArchive().write(out);
        return out.getCompletedInput();
    }

    @Test
    public void hazardCalcTest() throws DocumentException, IOException {
        NZSHM22_HazardCalculatorBuilder builder = new NZSHM22_HazardCalculatorBuilder();
        // off rupture
        //        double lat = -41.167;
        //        double lon = 175.342;

        // on rupture
        //        double lat = -41.1225;
        //        double lon = 175.0372;

        // on section 1
        //        double lon = 174.8494;
        //        double lat = -37.2555;

        // outside polygon of section 1
        double lat = -37.36402246219363;
        double lon = 174.9079855447284;

        builder.setSolution(makeSolution());
        builder.setBackgroundOption("EXCLUDE");
        NZSHM22_HazardCalculator calculator = builder.build();
        // a location on a rupture
        DiscretizedFunc actual1; // = calculator.calc(-41.1225, 175.0372);
        actual1 = calculator.calc(lat, lon);

        // We have not verified that these values are correct. We only want to catch unexpected
        // changes with his test.
        double[] expected =
                new double[] {
                    0.010674170719832898,
                    0.010674170719832898,
                    0.010674170719832898,
                    0.010674170719832898,
                    0.010674170719832898,
                    0.010674170719832898,
                    0.010674170719832898,
                    0.010674170719832898,
                    0.010674170719832898,
                    0.010674170719832898,
                    0.010674170719832676,
                    0.010674170719831566,
                    0.010674170719822573,
                    0.010674170719751186,
                    0.010674170719260467,
                    0.010674170715920805,
                    0.010674170694564555,
                    0.010674170567876562,
                    0.010674169867356698,
                    0.010674166331911983,
                    0.01067414982896886,
                    0.01067407987469382,
                    0.010673810133134398,
                    0.01067286770993603,
                    0.010669874615915287,
                    0.010661295621475553,
                    0.010638994595421392,
                    0.010586575671253762,
                    0.0104750751612952,
                    0.01026061652242527,
                    0.009886802272396245,
                    0.009297480457119867,
                    0.008456158408889358,
                    0.007369202985779921,
                    0.006098207145589285,
                    0.0047528354763811675,
                    0.0034641484844992743,
                    0.0023472051813597794,
                    0.0014712876572381406,
                    8.498250117680017E-4,
                    4.509003956173485E-4,
                    2.1920282732956764E-4,
                    9.744499879282831E-5,
                    3.954355731405901E-5,
                    1.462953257169719E-5,
                    4.929776495687932E-6,
                    1.5123643396508513E-6,
                    4.2244047271378093E-7,
                    1.0752945789338497E-7,
                    2.4985659208276445E-8,
                    5.314120921084964E-9
                };
        assertArrayEquals(
                expected, actual1.yValues().stream().mapToDouble(v -> v).toArray(), 0.000000001);

        builder = new NZSHM22_HazardCalculatorBuilder();
        builder.setSolution(makeSolution());
        builder.setBackgroundOption("INCLUDE");
        calculator = builder.build();
        //  actual = calculator.calc(-41.1225, 175.0372);
        DiscretizedFunc actual2 = calculator.calc(lat, lon);
        assertNotEquals(actual1, actual2);

        // We have not verified that these values are correct. This test only checks that hazard
        // changes when background is included.
        expected =
                new double[] {
                    0.9756702441463271,
                    0.974838367077197,
                    0.9736775781997997,
                    0.971658636160229,
                    0.9684559808810308,
                    0.9628620639347886,
                    0.9550948016742842,
                    0.9436368627704893,
                    0.9263865658565861,
                    0.9024556243027562,
                    0.8682653507319368,
                    0.8241772461701704,
                    0.7704658251782508,
                    0.7043753849606222,
                    0.6331028113407269,
                    0.5563940282192192,
                    0.4785733147008565,
                    0.4032704640755226,
                    0.33281383997897196,
                    0.2695217188542498,
                    0.213987887837932,
                    0.1669371980401546,
                    0.12812240676411313,
                    0.09698270423948774,
                    0.07260387128793466,
                    0.05407590769957005,
                    0.0403271143914119,
                    0.03038603608419499,
                    0.023344713930792627,
                    0.01841113536836525,
                    0.014909903498540933,
                    0.01230848319926281,
                    0.010208982470399808,
                    0.008358355106686255,
                    0.0066378774649564765,
                    0.005036473935365882,
                    0.003607165543957236,
                    0.0024160726765631857,
                    0.0015028083869602638,
                    8.634748059839215E-4,
                    4.5646840158886093E-4,
                    2.2133365206156164E-4,
                    9.820726740983332E-5,
                    3.979762195716052E-5,
                    1.4708212222602768E-5,
                    4.952365861510266E-6,
                    1.5183672466001497E-6,
                    4.2391561938526223E-7,
                    1.0786464121892436E-7,
                    2.505612539671631E-8,
                    5.3278393918887446E-9
                };

        assertArrayEquals(
                expected, actual2.yValues().stream().mapToDouble(v -> v).toArray(), 0.00000001);
    }
}

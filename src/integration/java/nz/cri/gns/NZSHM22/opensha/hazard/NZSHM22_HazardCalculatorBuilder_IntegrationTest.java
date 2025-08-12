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
                    0.9758996094211274,
                    0.9750734334117824,
                    0.9739204835308194,
                    0.9719148693544343,
                    0.968732616370211,
                    0.9631723970956172,
                    0.9554484247456217,
                    0.9440482327610595,
                    0.9268739616647104,
                    0.903031746467323,
                    0.8689428194861298,
                    0.824952364161639,
                    0.7713220132911743,
                    0.705287602068448,
                    0.6340330279174293,
                    0.557304016059982,
                    0.47942798305757806,
                    0.4040431776766821,
                    0.33348774097399037,
                    0.27009052264665634,
                    0.21445283093660794,
                    0.16730619234655375,
                    0.12840701353912776,
                    0.0971962762995997,
                    0.07275976268218642,
                    0.05418671996625668,
                    0.040403765788833534,
                    0.0304376401492068,
                    0.023378514891661872,
                    0.01843267367228152,
                    0.014923240937210869,
                    0.012316506766824054,
                    0.01021366534171142,
                    0.008361002415922969,
                    0.006639323516470785,
                    0.005037234570323679,
                    0.003607549327338422,
                    0.002416257606076333,
                    0.0015028930992332379,
                    8.635115290078765E-4,
                    4.5648340194559545E-4,
                    2.213394021676196E-4,
                    9.820932851156705E-5,
                    3.979831047196125E-5,
                    1.470842598449984E-5,
                    4.952427391513581E-6,
                    1.5183836463705802E-6,
                    4.2391967292054744E-7,
                    1.0786555937336573E-7,
                    2.5056319463701016E-8,
                    5.327877028449279E-9
                };
        assertArrayEquals(
                expected, actual2.yValues().stream().mapToDouble(v -> v).toArray(), 0.00000001);
    }
}

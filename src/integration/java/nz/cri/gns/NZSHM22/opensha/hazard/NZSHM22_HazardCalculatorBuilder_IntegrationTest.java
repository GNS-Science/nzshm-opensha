package nz.cri.gns.NZSHM22.opensha.hazard;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.griddedSeismicity.NZSHM22_GriddedData;
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

    protected File getSolution() throws URISyntaxException {
        URL vernonSolution =
                Thread.currentThread()
                        .getContextClassLoader()
                        .getResource("ModularAlpineVernonInversionSolution.zip");
        return new File(vernonSolution.toURI());
    }

    public static ArchiveInput makeSolution() throws DocumentException, IOException {
        FaultSystemRupSet rupSet = TestHelpers.makeRupSet(NZSHM22_FaultModels.CFM_1_0A_DOM_SANSTVZ, ScalingRelationships.SHAW_2009_MOD);
        ArchiveOutput archiveOutput = new ArchiveOutput.InMemoryZipOutput(true);
        rupSet.getArchive().write(archiveOutput);

        rupSet.getArchive().write(new File("testrup.zip"));

        ParameterRunner parameterRunner = new ParameterRunner(Parameters.NZSHM22.INVERSION_CRUSTAL);
        NZSHM22_CrustalInversionRunner runner = new NZSHM22_CrustalInversionRunner();
        parameterRunner.setUpCrustalInversionRunner(runner);
        runner
                .setIterationCompletionCriteria(100)
                .setSelectionIterations(1)
                .setRepeatable(true)
                .setInversionAveraging(false)
                .setRuptureSetArchiveInput(archiveOutput.getCompletedInput());

        FaultSystemSolution solution = runner.runInversion();
        solution.write(new File("testolution.zip"));

        ArchiveOutput out = new ArchiveOutput.InMemoryZipOutput(true);
        solution.getArchive().write(out);
        return out.getCompletedInput();
    }

    @Test
    public void hazardCalcTest() throws URISyntaxException, DocumentException, IOException {
        NZSHM22_HazardCalculatorBuilder builder = new NZSHM22_HazardCalculatorBuilder();
        // off rupture
//        double lat = -41.167;
//        double lon = 175.342;

        //on rupture
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
        DiscretizedFunc actual1;//= calculator.calc(-41.1225, 175.0372);
        actual1 = calculator.calc(lat, lon);

        // We have not verified that these values are correct. We only want to catch unexpected changes with his test.
        //   List<Double> expected = Arrays.asList(0.09532602505251131, 0.09532602505251131, 0.09532602505251109, 0.09532602505250931, 0.09532602505249643, 0.09532602505237686, 0.09532602505156873, 0.09532602504617882, 0.09532602500931642, 0.09532602478570473, 0.09532602339475482, 0.09532601583929279, 0.09532597936109133, 0.09532580308639438, 0.09532509270520095, 0.09532237633234575, 0.09531298454770032, 0.09528372093206106, 0.09520076160535595, 0.09498954857743602, 0.09449879057111943, 0.09347147712475035, 0.09152255495544248, 0.08817563392388372, 0.0829503092649424, 0.07557325884105659, 0.06610609991554084, 0.05509946995555548, 0.04350295873495669, 0.03244534262465637, 0.022885187451926914, 0.015393616735604576, 0.010037100576270275, 0.006500535128294294, 0.0042870911710641835, 0.0029132490176523307, 0.002020124928060807, 0.0013936081153361757, 9.302925224911052E-4, 5.879681232344725E-4, 3.467620074188993E-4, 1.8910879175393358E-4, 9.483560321732476E-5, 4.3572955102266775E-5, 1.829699035560406E-5, 7.009762983734014E-6, 2.447086358436934E-6, 7.77854806566225E-7, 2.2510616670690098E-7, 5.934050295586246E-8, 1.426880802402053E-8);
        //   assertEquals(expected, actual.yValues());

        builder = new NZSHM22_HazardCalculatorBuilder();
        builder.setSolution(makeSolution());
        builder.setBackgroundOption("INCLUDE");
        calculator = builder.build();
        //  actual = calculator.calc(-41.1225, 175.0372);
        DiscretizedFunc actual2 = calculator.calc(lat, lon);

        assertNotEquals(actual1, actual2);

        // We have not verified that these values are correct. This test only checks that hazard changes when background is included.
        //   expected = Arrays.asList(0.9999999999913296, 0.9999999999897597, 0.9999999999872021, 0.9999999999815505, 0.9999999999685879, 0.9999999999287628, 0.9999999998127505, 0.9999999993905798, 0.9999999974642347, 0.9999999879516961, 0.9999999313242015, 0.9999995990800863, 0.9999977418003023, 0.9999870087293586, 0.9999359008739048, 0.9997134852199828, 0.9988863755801156, 0.9962961437063166, 0.9894170397126908, 0.9740584390535683, 0.9445236249014294, 0.8956748283705995, 0.8248515119150669, 0.7337438777045995, 0.6279797427902616, 0.5163840636884308, 0.40754289365671115, 0.30873534520871493, 0.22446126993837512, 0.15657132341972801, 0.10463384187006208, 0.0669238592218393, 0.040901828780002925, 0.02385964787518513, 0.013272944282620491, 0.007036300515632088, 0.003553236643354829, 0.0017083268201186774, 7.811947136414643E-4, 3.392037710656659E-4, 1.3950143765106837E-4, 5.4158053138353424E-5, 1.977227061933373E-5, 6.759932810074254E-6, 2.155724318275709E-6, 6.389622968505648E-7, 1.755254912527704E-7, 4.459924496380552E-8, 1.0471993161509374E-8, 2.2725783477284267E-9, 4.564267852558146E-10);
        // assertEquals(expected, actual.yValues());
    }}

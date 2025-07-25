package nz.cri.gns.NZSHM22.opensha.hazard;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
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

        rupSet.getArchive().write(new File("testrup.zip"));

        ParameterRunner parameterRunner = new ParameterRunner(Parameters.NZSHM22.INVERSION_CRUSTAL);
        NZSHM22_CrustalInversionRunner runner = new NZSHM22_CrustalInversionRunner();
        parameterRunner.setUpCrustalInversionRunner(runner);
        runner.setIterationCompletionCriteria(100)
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
        List<Double> expected =
                Arrays.asList(
                        0.0315352306268023,
                        0.0315352306268023,
                        0.0315352306268023,
                        0.0315352306268023,
                        0.0315352306268023,
                        0.0315352306268023,
                        0.0315352306268023,
                        0.0315352306268023,
                        0.0315352306268023,
                        0.03153523062680219,
                        0.03153523062680186,
                        0.031535230626798305,
                        0.03153523062677199,
                        0.03153523062656338,
                        0.031535230625128974,
                        0.03153523061536767,
                        0.03153523055294494,
                        0.03153523018264526,
                        0.0315352281350787,
                        0.031535217801240356,
                        0.03153516956438096,
                        0.03153496509328746,
                        0.031534176658693736,
                        0.03153142202215531,
                        0.03152267338488435,
                        0.031497597198938965,
                        0.03143240983787432,
                        0.03127917439558747,
                        0.030953173030186742,
                        0.030325941098977793,
                        0.02923199286720124,
                        0.02750570626350335,
                        0.025037705146303102,
                        0.021842980722104666,
                        0.01809851264351403,
                        0.014124553070391177,
                        0.010308012084529472,
                        0.006992163966526022,
                        0.0043866815092901135,
                        0.0025353394771844284,
                        0.001345734206996796,
                        6.543720678398035E-4,
                        2.909314452906875E-4,
                        1.1806789349977365E-4,
                        4.368147282673984E-5,
                        1.4719675492269602E-5,
                        4.515739783550465E-6,
                        1.2613583006970686E-6,
                        3.2107060154995537E-7,
                        7.46043127275442E-8,
                        1.586735576264431E-8);
        assertEquals(expected, actual1.yValues());

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
                Arrays.asList(
                        0.9987056924065442,
                        0.9986350386845625,
                        0.9985330863785602,
                        0.9983472927129348,
                        0.9980317872150657,
                        0.9974223167157087,
                        0.9964575951335973,
                        0.9947922108732852,
                        0.9917598847867114,
                        0.9865447384879054,
                        0.9771251849880259,
                        0.9616494487090761,
                        0.9378265231763041,
                        0.9011179088942345,
                        0.852451249041678,
                        0.7895793129439609,
                        0.7146955915987756,
                        0.6316061734714433,
                        0.5444223810520752,
                        0.45835462121920334,
                        0.3768309725011585,
                        0.30339587111586974,
                        0.23981935095294882,
                        0.1868651103615585,
                        0.14420006578769295,
                        0.1110647528801263,
                        0.08607495802166931,
                        0.06777502393368795,
                        0.05465082130129384,
                        0.04528452777602099,
                        0.03840219808896983,
                        0.032964528147754635,
                        0.028189469807850087,
                        0.023605548695560863,
                        0.01905113280474069,
                        0.014620574047329526,
                        0.010555913912867365,
                        0.007110585945296455,
                        0.004440513695874504,
                        0.00255852444441651,
                        0.00135515511397033,
                        6.579692395395353E-4,
                        2.922174289839985E-4,
                        1.1849686165021911E-4,
                        4.381459538937982E-5,
                        1.4758015694171789E-5,
                        4.525968430479921E-6,
                        1.2638831792255445E-6,
                        3.216470756406409E-7,
                        7.472612262304779E-8,
                        1.5891202242990232E-8);
        assertEquals(expected, actual2.yValues());
    }
}

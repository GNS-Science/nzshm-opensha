package nz.cri.gns.NZSHM22.opensha.hazard;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import org.dom4j.DocumentException;
import org.junit.Test;
import org.opensha.commons.data.function.DiscretizedFunc;

public class NZSHM22_HazardCalculatorBuilder_IntegrationTest {

    protected File getSolution() throws URISyntaxException {
        URL vernonSolution =
                Thread.currentThread()
                        .getContextClassLoader()
                        .getResource("ModularAlpineVernonInversionSolution.zip");
        return new File(vernonSolution.toURI());
    }

    @Test
    public void hazardCalcTest() throws URISyntaxException, DocumentException, IOException {
        NZSHM22_HazardCalculatorBuilder builder = new NZSHM22_HazardCalculatorBuilder();
        builder.setSolutionFile(getSolution());
        builder.setBackgroundOption("EXCLUDE");
        NZSHM22_HazardCalculator calculator = builder.build();
        DiscretizedFunc actual = calculator.calc(-41.289, 174.777);

        List<Double> expected =
                Arrays.asList(
                        0.0778137512486966,
                        0.07781375124869183,
                        0.07781375124866374,
                        0.07781375124848389,
                        0.07781375124739276,
                        0.07781375123968137,
                        0.07781375120020961,
                        0.07781375100432308,
                        0.07781375003254265,
                        0.07781374579288103,
                        0.07781372706386747,
                        0.0778136544843071,
                        0.0778134005002663,
                        0.07781250480338087,
                        0.07780981114759933,
                        0.07780196822496799,
                        0.07778091997835213,
                        0.07772893120155766,
                        0.07760970054198757,
                        0.0773590310426645,
                        0.07686829898015257,
                        0.07598445164687606,
                        0.07450872774913642,
                        0.072221996352557,
                        0.06890966629235729,
                        0.06443295989283337,
                        0.05873915311143396,
                        0.05192969861762964,
                        0.04426101535752136,
                        0.036147499881604594,
                        0.02809576856482321,
                        0.020651729952447018,
                        0.014264859392911888,
                        0.00920770329562337,
                        0.005526981824169441,
                        0.0030720454393283747,
                        0.0015757346318683307,
                        7.437288313860702E-4,
                        3.2225038734257083E-4,
                        1.2793079472195323E-4,
                        4.646029707811028E-5,
                        1.5417194252353994E-5,
                        4.671927854360547E-6,
                        1.2927043256949489E-6,
                        3.2682755080060133E-7,
                        7.561856840698766E-8,
                        1.605050159447785E-8,
                        3.1360924968026893E-9,
                        5.665684588151976E-10,
                        9.514788956721532E-11,
                        1.4943934978361995E-11);
        assertEquals(expected, actual.yValues());

        // the following section should be uncommented when
        // https://github.com/GNS-Science/nzshm-opensha/issues/359 is fixed
        /*
        builder = new NZSHM22_HazardCalculatorBuilder();
        builder.setSolutionFile(getSolution());
        builder.setBackgroundOption("INCLUDE");
        calculator = builder.build();
        actual = calculator.calc(-41.289, 174.777);

        if(NZSHM22_GriddedData.GRID_SPACING == 0.1) {
            expected = Arrays.asList(0.9999999999582688, 0.999999999952393, 0.9999999999430508, 0.9999999999232628, 0.9999999998808006, 0.9999999997627773, 0.9999999994591091, 0.999999998500612, 0.999999994783072, 0.9999999793053742, 0.9999999020312221, 0.9999995185463371, 0.9999976706931961, 0.9999882798686462, 0.9999476857134101, 0.9997818855141064, 0.9991835403378778, 0.9973087263491843, 0.9922045819924482, 0.9803308020614999, 0.9563375708900821, 0.9145673406148302, 0.851024257845397, 0.7657665131277069, 0.6632643364793765, 0.5520431736988205, 0.44118898765405257, 0.3388431366390908, 0.2503124098341698, 0.17799033830883126, 0.12173109050004371, 0.07998370507068508, 0.050357084914912864, 0.03028844984715362, 0.01734755680823985, 0.009431262705570265, 0.004855205668274909, 0.0023626243991314855, 0.001085657460272338, 4.7088650992677117E-4, 1.9274938862801072E-4, 7.443661580441852E-5, 2.710052815402264E-5, 9.286480581183199E-6, 2.9879148262246247E-6, 8.999459244485308E-7, 2.5290594263260857E-7, 6.610879499380218E-8, 1.6035405225878208E-8, 3.604335496731892E-9, 7.506500976361963E-10);
        } else if(NZSHM22_GriddedData.GRID_SPACING == 0.05) {
            expected = Arrays.asList(0.9999999998064021, 0.999999999780703, 0.9999999997402402, 0.9999999996557773, 0.9999999994785365, 0.9999999990026438, 0.9999999978327678, 0.9999999943420695, 0.9999999817134319, 0.9999999331569618, 0.9999997115488475, 0.9999987113943188, 0.9999943331547049, 0.9999741902882648, 0.999895209016672, 0.9996021215281853, 0.9986387914620511, 0.9958771659369933, 0.9889566103383715, 0.974020290616405, 0.9457640216504745, 0.8992486480211278, 0.8316418983544077, 0.744105169431706, 0.6416100110317446, 0.5324272002736739, 0.42487765207376704, 0.3262315701803571, 0.24113509308963244, 0.17163347066814738, 0.11750172277290416, 0.07725966603064938, 0.04864967106454954, 0.02924306331979487, 0.016720938462077783, 0.009063264309767005, 0.004643651349384825, 0.0022439747628518747, 0.001021137601526112, 4.3715844703995366E-4, 1.7596478022019468E-4, 6.656219253031725E-5, 2.3647904738743897E-5, 7.881952110988522E-6, 2.460809792714791E-6, 7.182191771315516E-7, 1.9551772978410042E-7, 4.953722188005827E-8, 1.1662090182440465E-8, 2.5488879895618766E-9, 5.172848815959696E-10);
        } else {
            expected = null; // cause an assertion failure
        }

        assertEquals(expected.size(), actual.yValues().size());
        for(int i =0;i<expected.size(); i++){
            assertEquals(expected.get(i), actual.yValues().get(i), 0.000001);
        }
        */

    }
}

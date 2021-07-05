package nz.cri.gns.NZSHM22.opensha.hazard;

import static org.junit.Assert.*;

import org.dom4j.DocumentException;
import org.junit.Test;
import org.opensha.commons.data.function.DiscretizedFunc;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

public class NZSHM22_HazardCalculatorBuilder_IntegrationTest {

    protected File getSolution() throws URISyntaxException {
        URL vernonSolution = Thread.currentThread().getContextClassLoader().getResource("AlpineVernonInversionSolution.zip");
        return new File(vernonSolution.toURI());
    }

    @Test
    public void hazardCalcTest() throws URISyntaxException, DocumentException, IOException {
        NZSHM22_HazardCalculatorBuilder builder = new NZSHM22_HazardCalculatorBuilder();
        builder.setSolutionFile(getSolution());
        NZSHM22_HazardCalculator calculator = builder.build();
        DiscretizedFunc actual = calculator.calc(-41.289, 174.777);

        List<Double> expected = Arrays.asList(0.3211698929961557, 0.321169875872248, 0.321169819371695, 0.32116960057608834, 0.3211688422242548, 0.3211659531186831, 0.3211578766674793, 0.32113585560139657, 0.3210772215174471, 0.3209405044288577, 0.3206238227109448, 0.31998302664823286, 0.3188002110050704, 0.316627208248234, 0.3131898059300432, 0.3078601185932023, 0.30015219624537304, 0.2896442627081436, 0.2759246655503975, 0.2588567331973849, 0.23823749668436656, 0.21432501991813124, 0.18757606021993234, 0.15889827149190539, 0.12951930830484204, 0.10110962950401492, 0.07519915322225756, 0.053087672222047466, 0.0354650303195333, 0.02237632131241074, 0.013304000503337465, 0.0074465124186932075, 0.003917423467559411, 0.0019342891187102973, 8.948809773987598E-4, 3.8704594923621993E-4, 1.5614154895604582E-4, 5.8606383023573905E-5, 2.0414223449005675E-5, 6.583034185103642E-6, 1.961041652176476E-6, 5.38721671161646E-7, 1.3634744389090514E-7, 3.1784101461873604E-8, 6.82988587907829E-9, 1.3555038202994751E-9, 2.4922408581318223E-10, 4.262723507508781E-11, 6.817990616525549E-12, 1.0254019855437946E-12, 1.4555023852835802E-13);
        assertEquals(expected, actual.yValues());
    }
}

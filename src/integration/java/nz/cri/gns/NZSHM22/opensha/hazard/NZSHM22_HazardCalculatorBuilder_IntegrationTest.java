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

        List<Double> expected = Arrays.asList(0.9999999999989787, 0.9999999999988443, 0.9999999999986285, 0.9999999999981677, 0.9999999999971689, 0.9999999999943461, 0.9999999999868749, 0.9999999999622026, 0.9999999998595401, 0.9999999993882895, 0.9999999967086148, 0.9999999811353208, 0.9999998913907493, 0.9999993319974715, 0.9999963302613682, 0.9999809303102645, 0.9999104679667767, 0.9996291411218713, 0.9986503450942419, 0.9957410716677754, 0.9882446649443569, 0.971683878467343, 0.9399809603009542, 0.8871006359822108, 0.8093541400570841, 0.7083760753003028, 0.5910070254364669, 0.46834191293354877, 0.35200681845256887, 0.25110342624668214, 0.1702254173039286, 0.10994781670131182, 0.06780388691247163, 0.04000355405057654, 0.022609216203905436, 0.012245502003112474, 0.0063537739919172775, 0.0031544225993979103, 0.0014953283467294964, 6.748909420829508E-4, 2.889770042796558E-4, 1.1690912801987086E-4, 4.449674749751331E-5, 1.586257138297409E-5, 5.274510773922714E-6, 1.6298290258509596E-6, 4.6655114549487564E-7, 1.2343915101187264E-7, 3.0145901108724615E-8, 6.793930418247385E-9, 1.4142993443044816E-9);
        assertEquals(expected, actual.yValues());
    }
}

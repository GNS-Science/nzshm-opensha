package nz.cri.gns.NZSHM22.util;

import nz.cri.gns.NZSHM22.opensha.inversion.NZSHM22_CrustalInversionRunner;
import nz.cri.gns.NZSHM22.opensha.util.NZSHM22_PythonGateway;
import nz.cri.gns.NZSHM22.opensha.util.ParameterRunner;
import nz.cri.gns.NZSHM22.opensha.util.Parameters;
import org.dom4j.DocumentException;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;

import java.io.IOException;

public class OakleyRunner {

    public static void runRepeatableInversion() throws IOException, DocumentException {
        ParameterRunner parameterRunner = new ParameterRunner(Parameters.NZSHM22.INVERSION_CRUSTAL);
        NZSHM22_CrustalInversionRunner runner = NZSHM22_PythonGateway.getCrustalInversionRunner();
        parameterRunner.ensurePaths();
        parameterRunner.setUpCrustalInversionRunner(runner);
        runner.setInversionSeconds(2);
        runner.setRepeatable(true);
        runner.setInversionAveraging(false);
        runner.setIterationCompletionCriteria(2);
        runner.setSelectionIterations(2);

        runner.setPaleoRatesFile("C:\\tmp\\paleo_rates_avalon_demo.csv");
        FaultSystemSolution solution = runner.runInversion();
        parameterRunner.saveSolution(solution);
    }

    public static void main(String[] args) throws DocumentException, IOException {
runRepeatableInversion();
    }
}

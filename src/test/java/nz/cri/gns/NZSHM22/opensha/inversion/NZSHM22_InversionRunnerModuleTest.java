package nz.cri.gns.NZSHM22.opensha.inversion;

import static org.junit.Assert.*;

import java.io.IOException;
import nz.cri.gns.NZSHM22.opensha.util.ParameterRunner;
import nz.cri.gns.NZSHM22.opensha.util.Parameters;
import nz.cri.gns.NZSHM22.opensha.util.TestHelpers;
import org.dom4j.DocumentException;
import org.junit.Test;

public class NZSHM22_InversionRunnerModuleTest {

    @Test
    public void roundTripsCrustalRunner() throws DocumentException, IOException {
        NZSHM22_CrustalInversionRunner runner = NZSHM22_CrustalInversionRunnerTest.makeRunner();

        NZSHM22_InversionRunnerModule module =
                (NZSHM22_InversionRunnerModule)
                        TestHelpers.serialiseDeserialise(new NZSHM22_InversionRunnerModule(runner));

        assertTrue(module.getRunner() instanceof NZSHM22_CrustalInversionRunner);
        assertEquals(runner.toJson(), module.getRunner().toJson());
    }

    @Test
    public void roundTripsSubductionRunner() throws IOException {
        ParameterRunner parameterRunner =
                new ParameterRunner(Parameters.NZSHM22.INVERSION_HIKURANGI);
        NZSHM22_SubductionInversionRunner runner = new NZSHM22_SubductionInversionRunner();
        parameterRunner.setUpSubductionInversionRunner(runner);

        NZSHM22_InversionRunnerModule module =
                (NZSHM22_InversionRunnerModule)
                        TestHelpers.serialiseDeserialise(new NZSHM22_InversionRunnerModule(runner));

        assertTrue(module.getRunner() instanceof NZSHM22_SubductionInversionRunner);
        assertEquals(runner.toJson(), module.getRunner().toJson());
    }
}

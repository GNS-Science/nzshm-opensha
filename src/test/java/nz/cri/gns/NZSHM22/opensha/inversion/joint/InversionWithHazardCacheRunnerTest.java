package nz.cri.gns.NZSHM22.opensha.inversion.joint;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.File;
import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;

public class InversionWithHazardCacheRunnerTest {

    @Test
    public void writesSolutionWithHazardCache() throws Exception {
        FaultSystemSolution inverted = mock(FaultSystemSolution.class);
        FaultSystemSolution withCache = mock(FaultSystemSolution.class);
        File solutionFile = new File("solution.zip");

        InversionWithHazardCacheRunner runner =
                new InversionWithHazardCacheRunner() {
                    @Override
                    protected FaultSystemSolution runInversion(String configPath) {
                        assertEquals("config.json", configPath);
                        return inverted;
                    }

                    @Override
                    protected FaultSystemSolution addHazardCache(FaultSystemSolution solution) {
                        assertSame(inverted, solution);
                        return withCache;
                    }
                };

        assertSame(withCache, runner.run("config.json", solutionFile));
        verify(withCache).write(solutionFile);
        verify(inverted, never()).write(solutionFile);
    }
}

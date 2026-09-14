package nz.cri.gns.NZSHM22.opensha.inversion.joint;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;

/** Tests deserialisation and hydration of {@link AnnealingConfig}. */
public class AnnealingConfigTest {

    @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

    protected AnnealingConfig parse(String annealingBody) {
        Config config = ConfigModule.fromJson("{\"annealing\": {" + annealingBody + "}}");
        return config.getAnnealingConfig();
    }

    protected FaultSystemRupSet rupSetWithRuptures(int numRuptures) {
        FaultSystemRupSet rupSet = Mockito.mock(FaultSystemRupSet.class);
        Mockito.when(rupSet.getNumRuptures()).thenReturn(numRuptures);
        return rupSet;
    }

    /** Writes a rates CSV and returns it as a JSON-escaped absolute path. */
    protected String ratesCsvPath(double... rates) throws IOException {
        StringBuilder csv = new StringBuilder("Rupture Index,Annual Rate\n");
        for (int r = 0; r < rates.length; r++) {
            csv.append(r).append(",").append(rates[r]).append("\n");
        }
        File file = tempFolder.newFile("rates.csv");
        Files.writeString(file.toPath(), csv.toString());
        return file.getAbsolutePath().replace("\\", "\\\\");
    }

    @Test
    public void completionEnergyIsRead() {
        assertEquals(0.25, parse("\"completionEnergy\": 0.25").completionEnergy, 0);
    }

    /** Configs written before the spelling was fixed must still deserialise. */
    @Test
    public void legacyMisspelledCompletionEnergyIsRead() {
        assertEquals(0.25, parse("\"completionEenergy\": 0.25").completionEnergy, 0);
    }

    /** Round-tripping a legacy config must write out the corrected spelling. */
    @Test
    public void completionEnergyIsWrittenWithTheCorrectedName() {
        Config config = ConfigModule.fromJson("{\"annealing\": {\"completionEenergy\": 0.25}}");
        String json = new ConfigModule(config).toJson();
        assertTrue(json, json.contains("\"completionEnergy\": 0.25"));
        assertFalse(json, json.contains("completionEenergy"));
    }

    /** The criteria is gated on energyDelta, so completionEnergy alone does not enable it. */
    @Test
    public void energyDeltaGatesTheCompletionCriteria() {
        AnnealingConfig noDelta = parse("\"completionEnergy\": 0.25");
        noDelta.init();
        assertNull(noDelta.energyChangeCompletionCriteria);

        AnnealingConfig withDelta = parse("\"completionEnergy\": 0.25, \"energyDelta\": 1");
        withDelta.init();
        assertNotNull(withDelta.energyChangeCompletionCriteria);
        // completionEnergy is the percent-change threshold
        assertTrue(
                withDelta.energyChangeCompletionCriteria.toString(),
                withDelta.energyChangeCompletionCriteria.toString().contains("Delta=0.25"));
    }

    @Test
    public void initialSolutionDefaultsToNull() throws IOException {
        assertNull(parse("").getInitialSolution(rupSetWithRuptures(3)));
    }

    @Test
    public void initialSolutionIsLoadedFromPath() throws IOException {
        AnnealingConfig config =
                parse("\"initialSolutionPath\": \"" + ratesCsvPath(0.1, 0.2, 0.3) + "\"");

        double[] initialSolution = config.getInitialSolution(rupSetWithRuptures(3));

        assertEquals(3, initialSolution.length);
        assertEquals(0.2, initialSolution[1], 1e-12);
    }

    @Test
    public void initialSolutionLengthMustMatchTheRuptureSet() throws IOException {
        AnnealingConfig config =
                parse("\"initialSolutionPath\": \"" + ratesCsvPath(0.1, 0.2, 0.3) + "\"");

        try {
            config.getInitialSolution(rupSetWithRuptures(4));
            fail("expected a length mismatch to be rejected");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("3 rates"));
        }
    }

    @Test
    public void initialSolutionSourcesAreMutuallyExclusive() throws IOException {
        AnnealingConfig config =
                parse(
                        "\"initialSolutionPath\": \"x.csv\","
                                + "\"varPertBasisAsInitialSolution\": true");

        try {
            config.getInitialSolution(rupSetWithRuptures(3));
            fail("expected both initial solution sources to be rejected");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Only one of"));
        }
    }

    /** A supplied basis is used as-is rather than computed from the rupture set. */
    @Test
    public void varPertBasisCanSeedTheInitialSolution() throws IOException {
        AnnealingConfig config =
                parse(
                        "\"varPertBasisAsInitialSolution\": true,"
                                + "\"variablePerturbationBasis\": [0.1, 0.2, 0.3]");
        FaultSystemRupSet rupSet = rupSetWithRuptures(3);

        double[] initialSolution = config.getInitialSolution(rupSet);

        assertArrayEquals(new double[] {0.1, 0.2, 0.3}, initialSolution, 1e-12);
        // a copy, so annealing cannot mutate the basis
        assertNotSame(config.getVariablePerturbationBasis(rupSet), initialSolution);
    }
}

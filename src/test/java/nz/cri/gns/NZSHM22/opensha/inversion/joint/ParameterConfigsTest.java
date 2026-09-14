package nz.cri.gns.NZSHM22.opensha.inversion.joint;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import nz.cri.gns.NZSHM22.opensha.inversion.AbstractInversionConfiguration.NZSlipRateConstraintWeightingType;
import org.junit.Test;

/**
 * Every joint inversion config shipped in src/main/resources/parameters must deserialise. Gson is
 * lenient and silently ignores keys it does not recognise, so a malformed or misspelled config
 * otherwise fails only at run time, after the rupture set has been loaded.
 */
public class ParameterConfigsTest {

    public static final File PARAMETERS_DIR = new File("src/main/resources/parameters");

    /** Configs for the joint inversion, which are the ones {@link Config} can read. */
    protected boolean isJointConfig(File file) {
        return file.getName().endsWith(".jsonc")
                && file.getName().startsWith("NZSHM_config-parallel");
    }

    @Test
    public void allParallelConfigsParse() throws IOException {
        File[] files = PARAMETERS_DIR.listFiles();
        assertNotNull("parameters directory not found", files);
        int checked = 0;
        for (File file : files) {
            if (!isJointConfig(file)) {
                continue;
            }
            Config config = ConfigModule.fromJson(Files.readString(file.toPath()));
            assertNotNull(file.getName(), config);
            assertNotNull(file.getName() + ": no annealing block", config.getAnnealingConfig());
            assertNotNull(file.getName() + ": no partitions", config.partitions);
            assertTrue(file.getName() + ": no partitions", config.partitions.size() > 0);
            checked++;
        }
        assertTrue("expected some parallel configs to check", checked >= 2);
    }

    /** The even-fit configs only work if every constraint is visible to the reweighter. */
    @Test
    public void evenFitConfigsAreFullyUncertaintyWeighted() throws IOException {
        for (String name :
                new String[] {
                    "NZSHM_config-parallel-even-fit.jsonc",
                    "NZSHM_config-parallel-even-fit-diverse.jsonc"
                }) {
            File file = new File(PARAMETERS_DIR, name);
            Config config = ConfigModule.fromJson(Files.readString(file.toPath()));
            assertNotNull(name, config.getAnnealingConfig().reweightTargetQuantity);
            for (PartitionConfig partition : config.partitions) {
                assertTrue(
                        name + ": " + partition.partition + " slip is not uncertainty weighted",
                        partition.slipRateWeightingType
                                == NZSlipRateConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY);
                assertTrue(
                        name + ": " + partition.partition + " MFD is not uncertainty weighted",
                        partition.mfdUncertaintyWeight > 0);
                assertTrue(
                        name + ": " + partition.partition + " still has an MFD equality weight",
                        partition.mfdEqualityConstraintWt == 0);
            }
        }
    }
}

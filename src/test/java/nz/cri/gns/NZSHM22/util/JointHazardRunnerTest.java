package nz.cri.gns.NZSHM22.util;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.List;
import nz.cri.gns.NZSHM22.opensha.hazard.joint.JointHazardInput;
import nz.cri.gns.NZSHM22.opensha.hazard.joint.JointHazardInput.GmmMode;
import nz.cri.gns.NZSHM22.util.JointHazardRunner.Options;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Tests for {@link JointHazardRunner.Options}. The calculation itself is covered by the joint
 * hazard tests; what matters here is which GMM mode a set of solutions ends up being calculated
 * with.
 */
public class JointHazardRunnerTest {

    @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

    private File crustal;
    private File subduction;

    @Before
    public void createSolutionFiles() throws IOException {
        crustal = tempFolder.newFile("crustal.zip");
        subduction = tempFolder.newFile("subduction.zip");
    }

    /** A single solution keeps the mode it was given. */
    @Test
    public void testSingleSolutionKeepsMode() {
        Options options =
                new Options(
                        List.of(crustal),
                        JointHazardRunner.DEFAULT_OUTPUT_DIR,
                        JointHazardInput.DEFAULT_SPACING,
                        GmmMode.JOINT_RUPTURE);

        assertEquals(List.of(crustal), options.getSolutionFiles());
        assertEquals(GmmMode.JOINT_RUPTURE, options.getMode());
        assertEquals(JointHazardRunner.DEFAULT_OUTPUT_DIR, options.getOutputDir());
        assertEquals(JointHazardInput.DEFAULT_SPACING, options.getSpacing(), 1e-9);
    }

    /**
     * Merged solutions cannot hold joint ruptures, so more than one solution is always calculated
     * per tectonic region type whatever mode was asked for.
     */
    @Test
    public void testManySolutionsImplyPerTectonicRegion() {
        Options options =
                new Options(
                        List.of(crustal, subduction),
                        JointHazardRunner.DEFAULT_OUTPUT_DIR,
                        JointHazardInput.DEFAULT_SPACING,
                        GmmMode.JOINT_RUPTURE);

        assertEquals(List.of(crustal, subduction), options.getSolutionFiles());
        assertEquals(GmmMode.PER_TECTONIC_REGION, options.getMode());
    }

    @Test
    public void testRejectsNoSolutions() {
        try {
            new Options(
                    List.of(),
                    JointHazardRunner.DEFAULT_OUTPUT_DIR,
                    JointHazardInput.DEFAULT_SPACING,
                    GmmMode.JOINT_RUPTURE);
            fail("expected no solutions to be rejected");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("at least one solution"));
        }
    }
}

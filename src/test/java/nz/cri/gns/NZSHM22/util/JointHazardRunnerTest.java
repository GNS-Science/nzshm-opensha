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
 * Tests for {@link JointHazardRunner}'s command line parsing. The calculation itself is covered by
 * the joint hazard tests; what matters here is that solutions, options and mistakes are told apart.
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

    /** A single solution is calculated with the joint GMM, with the documented defaults. */
    @Test
    public void testSingleSolutionDefaults() {
        Options options = JointHazardRunner.parse(new String[] {crustal.getPath()});

        assertEquals(List.of(crustal), options.getSolutionFiles());
        assertEquals(GmmMode.JOINT_RUPTURE, options.getMode());
        assertEquals(JointHazardRunner.DEFAULT_OUTPUT_DIR, options.getOutputDir());
        assertEquals(JointHazardInput.DEFAULT_SPACING, options.getSpacing(), 1e-9);
    }

    @Test
    public void testPerTrtFlag() {
        Options options = JointHazardRunner.parse(new String[] {"--per-trt", crustal.getPath()});
        assertEquals(GmmMode.PER_TECTONIC_REGION, options.getMode());
    }

    /** Options are named, so they can be given before, after or between the solutions. */
    @Test
    public void testNamedOptions() {
        File outputDir = new File("some/output");
        Options options =
                JointHazardRunner.parse(
                        new String[] {
                            "--out",
                            outputDir.getPath(),
                            crustal.getPath(),
                            "--spacing",
                            "0.25",
                            subduction.getPath()
                        });

        assertEquals(List.of(crustal, subduction), options.getSolutionFiles());
        assertEquals(outputDir, options.getOutputDir());
        assertEquals(0.25, options.getSpacing(), 1e-9);
    }

    /** More than two solutions can be calculated together, in the order they are given. */
    @Test
    public void testManySolutions() throws IOException {
        File third = tempFolder.newFile("subduction2.zip");
        Options options =
                JointHazardRunner.parse(
                        new String[] {crustal.getPath(), subduction.getPath(), third.getPath()});

        assertEquals(List.of(crustal, subduction, third), options.getSolutionFiles());
    }

    /**
     * Merged solutions cannot hold joint ruptures, so more than one solution is always calculated
     * per tectonic region type whether or not the flag is given.
     */
    @Test
    public void testManySolutionsImplyPerTectonicRegion() {
        Options options =
                JointHazardRunner.parse(new String[] {crustal.getPath(), subduction.getPath()});
        assertEquals(GmmMode.PER_TECTONIC_REGION, options.getMode());
    }

    /**
     * The point of named options: a mistyped solution path used to be silently taken for the output
     * directory, and the run would then quietly calculate the wrong thing.
     */
    @Test
    public void testRejectsMissingSolutionFile() {
        String message = parseFailure(new String[] {crustal.getPath(), "subducton.zip"}); // typo
        assertTrue(message, message.contains("does not exist"));
    }

    @Test
    public void testRejectsNoSolutions() {
        assertTrue(parseFailure(new String[] {"--per-trt"}).contains("at least one solution"));
    }

    @Test
    public void testRejectsUnknownOption() {
        String message = parseFailure(new String[] {crustal.getPath(), "--nope"});
        assertTrue(message, message.contains("Unknown option --nope"));
    }

    @Test
    public void testRejectsFlagWithoutValue() {
        String message = parseFailure(new String[] {crustal.getPath(), "--spacing"});
        assertTrue(message, message.contains("--spacing needs a value"));
    }

    @Test
    public void testRejectsNonNumericSpacing() {
        String message = parseFailure(new String[] {crustal.getPath(), "--spacing", "coarse"});
        assertTrue(message, message.contains("needs a number"));
    }

    @Test
    public void testRejectsNegativeSpacing() {
        String message = parseFailure(new String[] {crustal.getPath(), "--spacing", "-0.1"});
        assertTrue(message, message.contains("must be positive"));
    }

    /** Parses a command line, expecting it to fail, and returns the failure message. */
    private static String parseFailure(String[] args) {
        try {
            JointHazardRunner.parse(args);
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
        fail("expected parsing to fail");
        return null;
    }
}

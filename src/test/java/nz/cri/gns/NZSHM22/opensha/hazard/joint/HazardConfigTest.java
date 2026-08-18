package nz.cri.gns.NZSHM22.opensha.hazard.joint;

import static nz.cri.gns.NZSHM22.opensha.hazard.joint.JointTestSolutions.*;
import static org.junit.Assert.*;

import java.io.File;
import java.util.List;
import nz.cri.gns.NZSHM22.opensha.hazard.joint.JointHazardInput.GmmMode;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Tests for {@link HazardConfig}: the named hazard sources of a comparison report. */
public class HazardConfigTest {

    @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

    /** Solutions calculated together get a GMM per tectonic region type. */
    @Test
    public void testCombined() {
        HazardConfig config =
                HazardConfig.combined("NZSHM22", makeCrustalSolution(), makeSubductionSolution());

        assertEquals("NZSHM22", config.getName());
        assertEquals(GmmMode.PER_TECTONIC_REGION, config.getInput().getGmmMode());
        assertEquals(4, config.getSolution().getRupSet().getNumSections());
    }

    /** A joint solution is calculated with the experimental joint GMM. */
    @Test
    public void testJoint() {
        HazardConfig config = HazardConfig.joint("Joint", makeSolution());

        assertEquals(GmmMode.JOINT_RUPTURE, config.getInput().getGmmMode());
        assertSame(config.getSolution(), config.getInput().getSolution());
    }

    /** Ids are used in file names, so they hold nothing that needs escaping. */
    @Test
    public void testGetId() {
        assertEquals("nzshm22", HazardConfig.joint("NZSHM22", makeSolution()).getId());
        assertEquals(
                "joint_inversion_v2",
                HazardConfig.joint("Joint Inversion (v2)", makeSolution()).getId());
    }

    /** A directory of solutions is picked up in a stable order, ignoring anything else in it. */
    @Test
    public void testSolutionsIn() throws Exception {
        File dir = tempFolder.newFolder("solutions");
        assertTrue(new File(dir, "b.zip").createNewFile());
        assertTrue(new File(dir, "a.zip").createNewFile());
        assertTrue(new File(dir, "notes.txt").createNewFile());

        List<File> solutions = HazardConfig.solutionsIn(dir);
        assertEquals(2, solutions.size());
        assertEquals("a.zip", solutions.get(0).getName());
        assertEquals("b.zip", solutions.get(1).getName());
    }

    /** An empty directory is a mistake worth reporting rather than an empty comparison. */
    @Test
    public void testSolutionsInRejectsEmptyDirectory() throws Exception {
        File dir = tempFolder.newFolder("empty");
        try {
            HazardConfig.solutionsIn(dir);
            fail("expected an empty directory to be rejected");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("No solution zip files"));
        }
    }

    @Test
    public void testRejectsBlankName() {
        try {
            HazardConfig.joint("  ", makeSolution());
            fail("expected a blank name to be rejected");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("name"));
        }
    }
}

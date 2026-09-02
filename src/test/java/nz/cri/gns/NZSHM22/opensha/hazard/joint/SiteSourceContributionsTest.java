package nz.cri.gns.NZSHM22.opensha.hazard.joint;

import static nz.cri.gns.NZSHM22.opensha.hazard.joint.JointTestSolutions.*;
import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.opensha.commons.data.CSVFile;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;

/**
 * Tests for {@link SiteSourceContributions}, the per-rupture decomposition of a site's hazard. The
 * contributions are supplied directly here rather than calculated, so that the aggregation and the
 * reporting can be checked against known numbers; {@link SiteSourceExplorerTest} covers the
 * calculation.
 */
public class SiteSourceContributionsTest {

    @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

    /**
     * Contributions over the three rupture test solution: crustal 1, interface 2, joint nothing.
     */
    static SiteSourceContributions contributions() {
        FaultSystemSolution solution = makeSolution();
        double[] rupRates = new double[solution.getRupSet().getNumRuptures()];
        rupRates[CRUSTAL_RUP] = 1e-3;
        rupRates[INTERFACE_RUP] = 2e-3;
        rupRates[JOINT_RUP] = 0d;
        return new SiteSourceContributions(solution, SITE, 0d, 0.5d, rupRates);
    }

    /** The contributions are rates, so the total is simply their sum and the shares add to one. */
    @Test
    public void testTotalRateAndFractions() {
        SiteSourceContributions contributions = contributions();
        assertEquals(3e-3, contributions.getTotalRate(), 1e-12);
        assertEquals(1d / 3d, contributions.getRupFraction(CRUSTAL_RUP), 1e-12);
        assertEquals(2d / 3d, contributions.getRupFraction(INTERFACE_RUP), 1e-12);
        assertEquals(0d, contributions.getRupFraction(JOINT_RUP), 1e-12);
        assertEquals(2, contributions.getNumContributingRuptures());
    }

    /** A rupture's rate reaches every section it uses, so section rates do not sum to the total. */
    @Test
    public void testSectionRates() {
        double[] sectionRates = contributions().getSectionRates();
        assertArrayEquals(new double[] {1e-3, 1e-3, 2e-3, 2e-3}, sectionRates, 1e-12);
    }

    /** Ruptures come back largest first, and ones contributing nothing are left out entirely. */
    @Test
    public void testTopRuptures() {
        SiteSourceContributions contributions = contributions();
        assertEquals(List.of(INTERFACE_RUP, CRUSTAL_RUP), contributions.topRuptures(0));
        assertEquals(List.of(INTERFACE_RUP), contributions.topRuptures(1));
        assertEquals(List.of(INTERFACE_RUP, CRUSTAL_RUP), contributions.topRuptures(10));
    }

    /** Sections of a rupture set built without a fault model fall back to their own names. */
    @Test
    public void testParentNames() {
        assertEquals(List.of("Section 0", "Section 1"), contributions().parentNames(CRUSTAL_RUP));
    }

    /** The CSV lists only contributing ruptures, in order, with the shares accumulating to 100%. */
    @Test
    public void testWriteCSV() throws IOException {
        File file = new File(tempFolder.getRoot(), "contributions.csv");
        contributions().writeCSV(file, 0);

        CSVFile<String> csv = CSVFile.readFile(file, true);
        assertEquals(3, csv.getNumRows());
        assertEquals("Rupture Index", csv.get(0, 0));
        assertEquals(String.valueOf(INTERFACE_RUP), csv.get(1, 0));
        assertEquals(String.valueOf(CRUSTAL_RUP), csv.get(2, 0));
        assertEquals(100d, Double.parseDouble(csv.get(2, 3)), 1e-4);
        assertEquals("INTERFACE", csv.get(1, 6));
        assertEquals("CRUSTAL", csv.get(2, 6));
    }

    /** The limit truncates the CSV the same way it truncates {@link #testTopRuptures}. */
    @Test
    public void testWriteCSVLimit() throws IOException {
        File file = new File(tempFolder.getRoot(), "top1.csv");
        contributions().writeCSV(file, 1);
        assertEquals(2, CSVFile.readFile(file, true).getNumRows());
    }

    /** A contribution per rupture is the whole point, so a mismatched array is rejected. */
    @Test
    public void testRejectsWrongLength() {
        FaultSystemSolution solution = makeSolution();
        try {
            new SiteSourceContributions(solution, SITE, 0d, 0.5d, new double[] {1d});
            fail("expected a mismatched contribution array to be rejected");
        } catch (IllegalArgumentException expected) {
            // as expected
        }
    }
}

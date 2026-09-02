package nz.cri.gns.NZSHM22.opensha.hazard.joint;

import static nz.cri.gns.NZSHM22.opensha.hazard.joint.JointTestSolutions.*;
import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.geo.Location;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.faultSurface.FaultSection;

/**
 * Tests for {@link SiteSourceComparison}: matching two solutions' sections by name and reporting
 * how each section's contribution changed.
 */
public class SiteSourceComparisonTest {

    @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

    static final double IML = 0.5d;

    /**
     * Contributions over the three rupture test solution, with the rate of each rupture given
     * explicitly so that the comparison has known numbers to work from.
     */
    static SiteSourceContributions contributions(double crustal, double interfce) {
        FaultSystemSolution solution = makeSolution();
        double[] rupRates = new double[solution.getRupSet().getNumRuptures()];
        rupRates[CRUSTAL_RUP] = crustal;
        rupRates[INTERFACE_RUP] = interfce;
        return new SiteSourceContributions(solution, SITE, 0d, IML, rupRates);
    }

    static SiteSourceComparison comparison() {
        return new SiteSourceComparison(
                contributions(1e-3, 2e-3),
                contributions(2e-3, 1e-3),
                SectionWeighting.participation());
    }

    static List<String> names(List<FaultSection> sections) {
        return sections.stream().map(FaultSection::getSectionName).collect(Collectors.toList());
    }

    /** Both solutions route hazard through all four sections, so all four are compared. */
    @Test
    public void testSectionsAndRates() {
        SiteSourceComparison comparison = comparison();
        assertEquals(
                List.of("Section 0", "Section 1", "Section 2", "Section 3"),
                names(comparison.getSections()));
        assertArrayEquals(
                new double[] {1e-3, 1e-3, 2e-3, 2e-3}, comparison.getReferenceRates(), 1e-12);
        assertArrayEquals(
                new double[] {2e-3, 2e-3, 1e-3, 1e-3}, comparison.getComparisonRates(), 1e-12);
    }

    /** The crustal sections double and the interface sections halve. */
    @Test
    public void testRatios() {
        assertArrayEquals(new double[] {2d, 2d, 0.5d, 0.5d}, comparison().getRatios(), 1e-12);
    }

    /** A section that only one solution routes hazard through gives an unbounded ratio. */
    @Test
    public void testRatioForOneSidedSection() {
        SiteSourceComparison comparison =
                new SiteSourceComparison(
                        contributions(1e-3, 0d),
                        contributions(1e-3, 2e-3),
                        SectionWeighting.participation());

        // the interface sections are a source only in the comparison solution
        assertEquals(
                List.of("Section 0", "Section 1", "Section 2", "Section 3"),
                names(comparison.getSections()));
        double[] ratios = comparison.getRatios();
        assertEquals(1d, ratios[0], 1e-12);
        assertTrue(Double.isInfinite(ratios[2]));
        assertTrue(ratios[2] > 0);
    }

    /** Sections that neither solution routes hazard through are left out of the comparison. */
    @Test
    public void testDropsSectionsThatAreNoSourceInEither() {
        SiteSourceComparison comparison =
                new SiteSourceComparison(
                        contributions(1e-3, 0d),
                        contributions(2e-3, 0d),
                        SectionWeighting.participation());
        assertEquals(List.of("Section 0", "Section 1"), names(comparison.getSections()));
    }

    /** The change in each section's contribution, which the difference map is coloured by. */
    @Test
    public void testDifferences() {
        assertArrayEquals(
                new double[] {1e-3, 1e-3, -1e-3, -1e-3}, comparison().getDifferences(), 1e-12);
    }

    /** Unlike a ratio, the change is finite even where only one solution has the section. */
    @Test
    public void testDifferenceForOneSidedSection() {
        SiteSourceComparison comparison =
                new SiteSourceComparison(
                        contributions(1e-3, 0d),
                        contributions(1e-3, 2e-3),
                        SectionWeighting.participation());
        double[] differences = comparison.getDifferences();
        assertEquals(0d, differences[0], 1e-12);
        assertEquals(2e-3, differences[2], 1e-12);
    }

    /** The share used for greying is each section's larger share of its own solution's total. */
    @Test
    public void testMaxPercentages() {
        // reference total 3e-3, comparison total 3e-3
        double[] percentages = comparison().getMaxPercentages();
        // section 0 is 1/3 of the reference and 2/3 of the comparison
        assertEquals(200d / 3d, percentages[0], 1e-9);
        assertEquals(200d / 3d, percentages[2], 1e-9);
    }

    /** Comparing rates of exceeding different levels would be meaningless, so it is rejected. */
    @Test
    public void testRejectsDifferentLevels() {
        SiteSourceContributions reference = contributions(1e-3, 2e-3);
        FaultSystemSolution solution = makeSolution();
        SiteSourceContributions other =
                new SiteSourceContributions(
                        solution,
                        SITE,
                        0d,
                        IML * 2,
                        new double[solution.getRupSet().getNumRuptures()]);
        try {
            new SiteSourceComparison(reference, other, SectionWeighting.participation());
            fail("expected differing intensity measure levels to be rejected");
        } catch (IllegalArgumentException expected) {
            // as expected
        }
    }

    /** Comparing two different sites is equally meaningless. */
    @Test
    public void testRejectsDifferentSites() {
        FaultSystemSolution solution = makeSolution();
        SiteSourceContributions elsewhere =
                new SiteSourceContributions(
                        solution,
                        new Location(-36.85, 174.76),
                        0d,
                        IML,
                        new double[solution.getRupSet().getNumRuptures()]);
        try {
            new SiteSourceComparison(
                    contributions(1e-3, 2e-3), elsewhere, SectionWeighting.participation());
            fail("expected differing sites to be rejected");
        } catch (IllegalArgumentException expected) {
            // as expected
        }
    }

    /** The CSV lists every compared section, largest absolute change first. */
    @Test
    public void testWriteCSV() throws IOException {
        File file = new File(tempFolder.getRoot(), "diff.csv");
        new SiteSourceComparison(
                        contributions(1e-3, 2e-3),
                        contributions(9e-3, 2e-3),
                        SectionWeighting.participation())
                .writeCSV(file, 0);

        CSVFile<String> csv = CSVFile.readFile(file, true);
        assertEquals(5, csv.getNumRows());
        assertEquals("Section", csv.get(0, 0));
        // the crustal sections changed by 8e-3, the interface ones not at all
        assertEquals("Section 0", csv.get(1, 0));
        assertEquals("Section 1", csv.get(2, 0));
        assertEquals(9d, Double.parseDouble(csv.get(1, 4)), 1e-6);
        assertEquals(1d, Double.parseDouble(csv.get(4, 4)), 1e-6);
    }
}

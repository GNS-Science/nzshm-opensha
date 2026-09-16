package nz.cri.gns.NZSHM22.opensha.hazard.joint;

import static nz.cri.gns.NZSHM22.opensha.hazard.joint.JointTestSolutions.*;
import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.geo.Location;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc.ReturnPeriods;
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
        return new SiteSourceComparison(contributions(1e-3, 2e-3), contributions(2e-3, 1e-3));
    }

    static List<String> names(List<FaultSection> sections) {
        return sections.stream().map(FaultSection::getSectionName).collect(Collectors.toList());
    }

    /** The per-section changes keyed by name, so a test does not depend on their ordering. */
    static Map<String, SiteSourceComparison.SectionChange> changesByName(
            SiteSourceComparison comparison) {
        return comparison.topChanges(0).stream()
                .collect(Collectors.toMap(change -> change.name, change -> change));
    }

    static final ReturnPeriods RETURN_PERIOD = ReturnPeriods.TEN_IN_50;

    /** Rate given to every rupture of the test solution when a real disaggregation is wanted. */
    static final double RATE = 1e-3;

    /** A rate so low the site's hazard never reaches the return period. */
    static final double NEGLIGIBLE_RATE = 1e-12;

    /** An explorer over the shared test solution with every rupture at the given rate. */
    static SiteSourceExplorer explorer(double rate) {
        FaultSystemRupSet rupSet = makeRupSet(0d);
        double[] rates = new double[rupSet.getNumRuptures()];
        Arrays.fill(rates, rate);
        return new SiteSourceExplorer(new FaultSystemSolution(rupSet, rates));
    }

    /**
     * Comparing through {@link SiteSourceComparison#compare} puts both solutions at the one level
     * the reference solution reaches at the return period, which is the only framing under which
     * their section rates can be subtracted.
     */
    @Test
    public void testCompare() {
        SiteSourceComparison comparison =
                SiteSourceComparison.compare(
                        explorer(RATE), explorer(2 * RATE), SITE, 0d, RETURN_PERIOD);

        assertNotNull(comparison);
        assertEquals(SITE, comparison.getSite());
        assertEquals(0d, comparison.getPeriod(), 0d);
        // both sides were disaggregated at the same level, taken off the reference curve
        assertEquals(comparison.getIml(), comparison.getReference().getIml(), 0d);
        assertEquals(comparison.getIml(), comparison.getComparison().getIml(), 0d);
        // the comparison solution ruptures twice as often, so more hazard reaches that level
        assertTrue(
                comparison.getComparison().getTotalRate()
                        > comparison.getReference().getTotalRate());
    }

    /**
     * A site whose reference hazard never reaches the return period has no level to compare at.
     * That is an ordinary outcome for a quiet site, so it gives null rather than throwing, and a
     * report covering many sites can carry on past it.
     */
    @Test
    public void testCompareReturnsNullBelowTheReturnPeriod() {
        assertNull(
                SiteSourceComparison.compare(
                        explorer(NEGLIGIBLE_RATE), explorer(RATE), SITE, 0d, RETURN_PERIOD));
    }

    /**
     * Only the reference solution has to reach the level. A comparison solution whose hazard has
     * collapsed contributes zero everywhere, which is the finding rather than an error.
     */
    @Test
    public void testCompareAllowsAComparisonThatNoLongerReachesTheLevel() {
        SiteSourceComparison comparison =
                SiteSourceComparison.compare(
                        explorer(RATE), explorer(NEGLIGIBLE_RATE), SITE, 0d, RETURN_PERIOD);

        assertNotNull(comparison);
        assertTrue(comparison.getReference().getTotalRate() > 0);
        assertEquals(0d, comparison.getComparison().getTotalRate(), 0d);
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
        Map<String, SiteSourceComparison.SectionChange> changes = changesByName(comparison());
        assertEquals(2d, changes.get("Section 0").getRatio(), 1e-12);
        assertEquals(2d, changes.get("Section 1").getRatio(), 1e-12);
        assertEquals(0.5d, changes.get("Section 2").getRatio(), 1e-12);
        assertEquals(0.5d, changes.get("Section 3").getRatio(), 1e-12);
    }

    /** A section that only one solution routes hazard through gives an unbounded ratio. */
    @Test
    public void testRatioForOneSidedSection() {
        SiteSourceComparison comparison =
                new SiteSourceComparison(contributions(1e-3, 0d), contributions(1e-3, 2e-3));

        // the interface sections are a source only in the comparison solution
        assertEquals(
                List.of("Section 0", "Section 1", "Section 2", "Section 3"),
                names(comparison.getSections()));
        Map<String, SiteSourceComparison.SectionChange> changes = changesByName(comparison);
        assertEquals(1d, changes.get("Section 0").getRatio(), 1e-12);
        double oneSided = changes.get("Section 2").getRatio();
        assertTrue(Double.isInfinite(oneSided));
        assertTrue(oneSided > 0);
    }

    /** Sections that neither solution routes hazard through are left out of the comparison. */
    @Test
    public void testDropsSectionsThatAreNoSourceInEither() {
        SiteSourceComparison comparison =
                new SiteSourceComparison(contributions(1e-3, 0d), contributions(2e-3, 0d));
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
                new SiteSourceComparison(contributions(1e-3, 0d), contributions(1e-3, 2e-3));
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
            new SiteSourceComparison(reference, other);
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
            new SiteSourceComparison(contributions(1e-3, 2e-3), elsewhere);
            fail("expected differing sites to be rejected");
        } catch (IllegalArgumentException expected) {
            // as expected
        }
    }

    /**
     * The changes come back largest first and carry the columns that separate the ways a section's
     * hazard can move: its rupture rate, the size of the events behind it, and how much of it is
     * now joint.
     */
    @Test
    public void testTopChanges() {
        FaultSystemSolution solution = makeSolution();
        double[] withJoint = new double[solution.getRupSet().getNumRuptures()];
        withJoint[CRUSTAL_RUP] = 1e-3;
        withJoint[INTERFACE_RUP] = 2e-3;
        withJoint[JOINT_RUP] = 3e-3;
        SiteSourceComparison comparison =
                new SiteSourceComparison(
                        contributions(1e-3, 2e-3),
                        new SiteSourceContributions(solution, SITE, 0d, IML, withJoint));

        List<SiteSourceComparison.SectionChange> changes = comparison.topChanges(0);
        assertEquals(4, changes.size());
        // the crustal sections gain the joint rupture, the interface ones gain it too but they
        // already carried the larger single-fault rupture, so all four move by the same 3e-3
        assertEquals(3e-3, changes.get(0).getChange(), 1e-12);

        SiteSourceComparison.SectionChange crustal =
                changes.stream()
                        .filter(change -> change.name.equals("Section 0"))
                        .findFirst()
                        .orElseThrow();
        assertEquals(1e-3, crustal.referenceRate, 1e-12);
        assertEquals(4e-3, crustal.comparisonRate, 1e-12);
        // the rupture rate is a property of the solution, which is the same on both sides here
        assertEquals(crustal.referenceSolutionRate, crustal.comparisonSolutionRate, 1e-12);
        assertEquals(75d, crustal.jointPercent, 1e-9);
        assertTrue(
                "the joint rupture should raise the size of the events behind the section",
                crustal.comparisonMeanMag > crustal.referenceMeanMag);
    }

    /** A section only one solution routes hazard through has no magnitude on the other side. */
    @Test
    public void testChangeOfSectionOnlyOneSolutionHas() {
        SiteSourceComparison comparison =
                new SiteSourceComparison(contributions(1e-3, 0d), contributions(1e-3, 2e-3));
        SiteSourceComparison.SectionChange added =
                comparison.topChanges(0).stream()
                        .filter(change -> change.name.equals("Section 2"))
                        .findFirst()
                        .orElseThrow();
        assertEquals(0d, added.referenceRate, 1e-12);
        assertTrue(Double.isNaN(added.referenceMeanMag));
        assertTrue(Double.isNaN(added.referenceMaxMag));
        assertFalse(Double.isNaN(added.comparisonMeanMag));
    }

    /** The CSV lists every compared section, largest absolute change first. */
    @Test
    public void testWriteCSV() throws IOException {
        File file = new File(tempFolder.getRoot(), "diff.csv");
        new SiteSourceComparison(contributions(1e-3, 2e-3), contributions(9e-3, 2e-3))
                .writeCSV(file, 0);

        CSVFile<String> csv = CSVFile.readFile(file, true);
        assertEquals(5, csv.getNumRows());
        assertEquals("Section", csv.get(0, 0));
        assertEquals(13, csv.getLine(0).size());
        // the crustal sections changed by 8e-3, the interface ones not at all
        assertEquals("Section 0", csv.get(1, 0));
        assertEquals("Section 1", csv.get(2, 0));
        assertEquals(9d, Double.parseDouble(csv.get(1, 4)), 1e-6);
        assertEquals(1d, Double.parseDouble(csv.get(4, 4)), 1e-6);
        // nothing joint reaches the site in either solution
        assertEquals(0d, Double.parseDouble(csv.get(1, 12)), 1e-9);
    }
}

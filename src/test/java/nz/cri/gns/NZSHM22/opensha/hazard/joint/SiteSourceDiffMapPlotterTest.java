package nz.cri.gns.NZSHM22.opensha.hazard.joint;

import static nz.cri.gns.NZSHM22.opensha.hazard.joint.SiteSourceComparisonTest.*;
import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.sha.faultSurface.FaultSection;

/**
 * Tests for {@link SiteSourceDiffMapPlotter}: the change in each section's contribution, in 1/yr,
 * and the scale it is drawn on.
 */
public class SiteSourceDiffMapPlotterTest {

    @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

    /**
     * The map is coloured by the change itself, so a section that gained hazard is positive and one
     * that lost it is negative, both in 1/yr.
     */
    @Test
    public void testDifferences() {
        double[] differences = SiteSourceDiffMapPlotter.differences(comparison(), Double.NaN);
        assertArrayEquals(new double[] {1e-3, 1e-3, -1e-3, -1e-3}, differences, 1e-12);
    }

    /**
     * A section that only one solution routes hazard through still has a finite change, so unlike a
     * ratio there is nothing to clamp.
     */
    @Test
    public void testDifferencesForOneSidedSections() {
        SiteSourceComparison comparison =
                new SiteSourceComparison(contributions(1e-3, 0d), contributions(1e-3, 2e-3));
        double[] differences = SiteSourceDiffMapPlotter.differences(comparison, Double.NaN);
        assertEquals(0d, differences[0], 1e-12);
        assertEquals(2e-3, differences[2], 1e-12);
    }

    /** Without a threshold every section that is a source in either solution is drawn. */
    @Test
    public void testDrawsEverySourceWithoutAThreshold() {
        assertEquals(4, SiteSourceDiffMapPlotter.sections(comparison(), Double.NaN).size());
    }

    /**
     * Sections that are negligible in both solutions are left off the map entirely, and the values
     * stay aligned with the sections that remain.
     */
    @Test
    public void testOmitsNegligibleSections() {
        // the crustal sections carry 1e-3 in both solutions and the interface ones about 9e-3, so
        // a threshold between the two keeps only the interface pair
        SiteSourceComparison comparison =
                new SiteSourceComparison(
                        contributions(1e-3, 9e-3), contributions(1e-3, 9e-3 - 1e-4));
        List<FaultSection> sections = SiteSourceDiffMapPlotter.sections(comparison, 5e-3);
        assertEquals(
                List.of("Section 2", "Section 3"),
                sections.stream().map(FaultSection::getSectionName).collect(Collectors.toList()));
        assertEquals(
                sections.size(), SiteSourceDiffMapPlotter.differences(comparison, 5e-3).length);
    }

    /**
     * The cut is on an absolute rate, so a section survives or not on what it carries and not on
     * how the other solution's total moved. This is the bug the rate threshold exists for: under a
     * share threshold the section below would pass on the reference map and fail on the comparison
     * one beside it, purely because the comparison's total grew.
     */
    @Test
    public void testThresholdIsNotAffectedByTheOtherSolutionsTotal() {
        // the interface sections contribute the same 2e-3 either way; the comparison's total is
        // far larger because its crustal ruptures grew, which halves their share of it
        SiteSourceComparison comparison =
                new SiteSourceComparison(contributions(1e-3, 2e-3), contributions(9e-3, 2e-3));
        assertEquals(4, SiteSourceDiffMapPlotter.sections(comparison, 1e-3).size());
    }

    /** Bigger changes sort above smaller ones, so they end up on top of the map. */
    @Test
    public void testSortables() {
        double[] sortables = SiteSourceDiffMapPlotter.sortables(new double[] {2e-3, 1e-3, 0d});
        assertTrue(sortables[2] < sortables[1]);
        assertTrue(sortables[1] < sortables[0]);
        // a loss and a gain of the same size are equally big changes
        assertEquals(
                sortables[0], SiteSourceDiffMapPlotter.sortables(new double[] {-2e-3})[0], 1e-12);
    }

    /** The scale covers the whole map, rounded outwards, with no change on the neutral colour. */
    @Test
    public void testDifferenceCPT() throws IOException {
        CPT cpt =
                new SiteSourceDiffMapPlotter()
                        .differenceCPT(new double[] {1.3e-3, -4e-4, Double.NaN});
        assertEquals(-5e-4, cpt.getMinValue(), 1e-12);
        assertEquals(2e-3, cpt.getMaxValue(), 1e-12);
        assertNotEquals(cpt.getColorRaw(-5e-4f), cpt.getColorRaw(2e-3f));
    }

    /**
     * A map where every section moved the same way gets the whole ramp for that direction, rather
     * than spending half of it on changes that do not occur.
     */
    @Test
    public void testDifferenceCPTFitsEachSide() throws IOException {
        CPT cpt = new SiteSourceDiffMapPlotter().differenceCPT(new double[] {1e-3, 3e-3});
        assertEquals(0d, cpt.getMinValue(), 1e-12);
        assertEquals(5e-3, cpt.getMaxValue(), 1e-12);
    }

    /** Bounds are rounded up to one, two or five times a power of ten so the legend reads well. */
    @Test
    public void testNiceCeiling() {
        assertEquals(2e-3, DivergingCPT.niceCeiling(1.3e-3), 1e-15);
        assertEquals(5e-3, DivergingCPT.niceCeiling(2.1e-3), 1e-15);
        assertEquals(1e-2, DivergingCPT.niceCeiling(5.1e-3), 1e-15);
        // a value that is already round is not pushed up a step
        assertEquals(2e-3, DivergingCPT.niceCeiling(2e-3), 1e-15);
        assertEquals(0d, DivergingCPT.niceCeiling(0d), 1e-15);
    }

    /**
     * The changes are reported over enough years to land on numbers a colour bar can print: at a
     * thousandth per year, a bar labelled in 1/yr comes out as a row of zeroes.
     */
    @Test
    public void testUnitYears() {
        assertEquals(1000d, SiteSourceDiffMapPlotter.unitYears(new double[] {2e-3, -4e-4}), 1e-9);
        assertEquals(10000d, SiteSourceDiffMapPlotter.unitYears(new double[] {4e-4}), 1e-9);
        assertEquals(1d, SiteSourceDiffMapPlotter.unitYears(new double[] {2.5, -1d}), 1e-9);
        // a map with no change at all still needs a unit
        assertEquals(1d, SiteSourceDiffMapPlotter.unitYears(new double[] {0d}), 1e-9);
    }

    /** Converting to that unit is a plain scaling of the rates. */
    @Test
    public void testPerUnit() {
        double[] scaled = SiteSourceDiffMapPlotter.perUnit(new double[] {2e-3, -4e-4}, 1000d);
        assertEquals(2d, scaled[0], 1e-12);
        assertEquals(-0.4, scaled[1], 1e-12);
    }

    /** The colour bar says which unit it is in. */
    @Test
    public void testRateUnit() {
        assertEquals("1/yr", HazardLabels.rateUnit(1d));
        assertEquals("per 1,000 years", HazardLabels.rateUnit(1000d));
        assertEquals("per 10,000 years", HazardLabels.rateUnit(10000d));
    }

    /** Plotting writes the map. */
    @Test
    public void testPlot() throws IOException {
        File outputDir = tempFolder.newFolder("diff");
        File map =
                new SiteSourceDiffMapPlotter().plot(outputDir, "diff", comparison(), "Test Site");

        assertTrue(map.exists());
        assertEquals("diff.png", map.getName());
    }

    /** A threshold that leaves out everything would give an empty map, so it is rejected. */
    @Test
    public void testRejectsThresholdAboveEverything() throws IOException {
        try {
            new SiteSourceDiffMapPlotter()
                    .setOmitBelowRate(1d)
                    .plot(tempFolder.newFolder("empty"), "diff", comparison(), "Test Site");
            fail("expected a threshold that leaves out every section to be rejected");
        } catch (IllegalStateException expected) {
            // as expected
        }
    }

    /** Two identical solutions leave no change to draw, which is said rather than drawn blank. */
    @Test
    public void testRejectsNoChange() throws IOException {
        SiteSourceComparison unchanged =
                new SiteSourceComparison(contributions(1e-3, 2e-3), contributions(1e-3, 2e-3));
        try {
            new SiteSourceDiffMapPlotter()
                    .plot(tempFolder.newFolder("same"), "diff", unchanged, "Test Site");
            fail("expected an unchanged comparison to be rejected");
        } catch (IllegalStateException expected) {
            // as expected
        }
    }
}

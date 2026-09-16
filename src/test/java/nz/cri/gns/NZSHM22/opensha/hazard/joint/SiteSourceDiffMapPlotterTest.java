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
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
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
                        .differenceCPT(new double[] {1.3e-3, -4e-4, Double.NaN}, 1d);
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
        CPT cpt = new SiteSourceDiffMapPlotter().differenceCPT(new double[] {1e-3, 3e-3}, 1d);
        assertEquals(0d, cpt.getMinValue(), 1e-12);
        assertEquals(5e-3, cpt.getMaxValue(), 1e-12);
    }

    /**
     * Colour follows the logarithm of the change, so a section that moved by a tenth of the largest
     * change is still well into the palette instead of sitting in its first sliver.
     */
    @Test
    public void testDifferenceCPTIsLogarithmic() throws IOException {
        // changes up to 100 per unit, with no change called anything under 0.1
        CPT cpt =
                new SiteSourceDiffMapPlotter()
                        .setNoChangeRate(0.1)
                        .differenceCPT(new double[] {80d, -80d}, 1d);
        CPT palette = GMT_CPT_Files.DIVERGING_BLUE_RED_UNIFORM.instance().rescale(-1d, 1d);

        // three decades from 0.1 to 100, so a change of 10 is two thirds of the way along
        assertColorNear(palette.getColor(2f / 3f), cpt.getColor(10f));
        assertColorNear(palette.getColor(1f / 3f), cpt.getColor(1f));
        assertColorNear(palette.getColor(1f), cpt.getColor(100f));
        // and the decrease side mirrors it
        assertColorNear(palette.getColor(-2f / 3f), cpt.getColor(-10f));
    }

    /** Changes below the no-change rate are one flat band in the no-change colour. */
    @Test
    public void testDifferenceCPTHasANoChangeBand() throws IOException {
        CPT cpt =
                new SiteSourceDiffMapPlotter()
                        .setNoChangeRate(0.1)
                        .differenceCPT(new double[] {80d, -80d}, 1d);

        for (float value : new float[] {-0.05f, 0f, 0.05f}) {
            assertEquals("at " + value, DivergingCPT.DEFAULT_ZERO_COLOR, cpt.getColor(value));
        }
        assertNotEquals(DivergingCPT.DEFAULT_ZERO_COLOR, cpt.getColor(50f));
    }

    /** The no-change rate is in 1/yr, so it is converted to whatever unit the map is drawn in. */
    @Test
    public void testNoChangeRateFollowsTheMapUnit() throws IOException {
        SiteSourceDiffMapPlotter plotter = new SiteSourceDiffMapPlotter().setNoChangeRate(1e-4);
        // drawn per 1000 years, so 1e-4 /yr is 0.1 of a unit
        assertEquals(0.1, plotter.logFloor(-1d, 1d, 1000d), 1e-12);
        assertEquals(1e-4, plotter.logFloor(-1d, 1d, 1d), 1e-16);
    }

    /** Without one of its own, the floor is the rate that decides which sections are drawn. */
    @Test
    public void testNoChangeRateDefaultsToTheOmitRate() throws IOException {
        SiteSourceDiffMapPlotter plotter = new SiteSourceDiffMapPlotter().setOmitBelowRate(2e-4);
        assertEquals(0.2, plotter.logFloor(-1d, 1d, 1000d), 1e-12);

        // and an explicit no-change rate wins over it
        plotter.setNoChangeRate(5e-4);
        assertEquals(0.5, plotter.logFloor(-1d, 1d, 1000d), 1e-12);
    }

    /** With neither set the ramp falls back to a fixed number of decades below its largest side. */
    @Test
    public void testLogFloorFallsBackToDecades() throws IOException {
        SiteSourceDiffMapPlotter plotter = new SiteSourceDiffMapPlotter();
        assertEquals(3d, SiteSourceDiffMapPlotter.DEFAULT_LOG_DECADES, 0d);
        assertEquals(2e-3, plotter.logFloor(-0.5, 2d, 1d), 1e-12);
        // the longer side sets it, whichever way it points
        assertEquals(2e-3, plotter.logFloor(-2d, 0.5, 1d), 1e-12);
    }

    /**
     * A map where nothing moved by as much as the no-change rate is drawn entirely in the no-change
     * colour rather than failing.
     */
    @Test
    public void testDifferenceCPTHandlesAFloorAboveEverything() throws IOException {
        CPT cpt =
                new SiteSourceDiffMapPlotter()
                        .setNoChangeRate(10d)
                        .differenceCPT(new double[] {0.3, -0.2}, 1d);

        // the ramp is rounded outwards to -0.2 and 0.5, and all of it is the no-change band
        assertEquals(-0.2, cpt.getMinValue(), 1e-12);
        assertEquals(0.5, cpt.getMaxValue(), 1e-12);
        assertEquals(DivergingCPT.DEFAULT_ZERO_COLOR, cpt.getColor(0.25f));
        assertEquals(DivergingCPT.DEFAULT_ZERO_COLOR, cpt.getColor(-0.15f));
    }

    /** Asserts two colours match up to the rounding of the ramp's colour steps. */
    protected static void assertColorNear(java.awt.Color expected, java.awt.Color actual) {
        int tolerance = 2;
        assertTrue(
                "expected " + expected + " but got " + actual,
                Math.abs(expected.getRed() - actual.getRed()) <= tolerance
                        && Math.abs(expected.getGreen() - actual.getGreen()) <= tolerance
                        && Math.abs(expected.getBlue() - actual.getBlue()) <= tolerance);
    }

    /** Bounds are rounded up to one, two or five times a power of ten so the legend reads well. */
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

    /**
     * Two identical solutions are a valid comparison: the map is drawn with every section in the
     * neutral colour rather than failing.
     */
    @Test
    public void testDrawsNoChange() throws IOException {
        SiteSourceComparison unchanged =
                new SiteSourceComparison(contributions(1e-3, 2e-3), contributions(1e-3, 2e-3));
        SiteSourceDiffMapPlotter plotter = new SiteSourceDiffMapPlotter();

        File map = plotter.plot(tempFolder.newFolder("same"), "diff", unchanged, "Test Site");

        assertTrue(map.exists());
        CPT cpt = plotter.differenceCPT(new double[] {0d, 0d}, 1d);
        assertEquals(-1d, cpt.getMinValue(), 1e-12);
        assertEquals(1d, cpt.getMaxValue(), 1e-12);
    }
}

package nz.cri.gns.NZSHM22.opensha.hazard.joint;

import static nz.cri.gns.NZSHM22.opensha.hazard.joint.JointTestSolutions.*;
import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.faultSurface.FaultSection;

/**
 * Tests for {@link SiteSourceMapPlotter}: the colour scale it derives from a set of contributions,
 * and the files it writes.
 */
public class SiteSourceMapPlotterTest {

    @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

    static SiteSourceContributions contributions() {
        FaultSystemSolution solution = makeSolution();
        double[] rupRates = new double[solution.getRupSet().getNumRuptures()];
        rupRates[CRUSTAL_RUP] = 1e-3;
        rupRates[INTERFACE_RUP] = 3e-3;
        return new SiteSourceContributions(solution, SITE, 0d, 0.5d, rupRates);
    }

    /** Sections are coloured by their share of the site's total, in percent. */
    @Test
    public void testPercentages() {
        double[] percentages = new SiteSourceMapPlotter().percentages(contributions());
        assertArrayEquals(new double[] {25d, 25d, 75d, 75d}, percentages, 1e-9);
        assertEquals(75d, SiteSourceMapPlotter.max(percentages), 1e-9);
    }

    /**
     * Under a partitioning weighting the section percentages add to 100 rather than over-counting
     * multi-section ruptures the way participation does.
     */
    @Test
    public void testPercentagesUnderProximityWeighting() {
        double[] percentages =
                new SiteSourceMapPlotter()
                        .setWeighting(SectionWeighting.proximity())
                        .percentages(contributions());
        assertEquals(100d, sum(percentages), 1e-6);
    }

    static double sum(double[] values) {
        double sum = 0;
        for (double value : values) {
            sum += value;
        }
        return sum;
    }

    /**
     * The scalars cover only the sections that are a source for the site, in section order, and are
     * clamped at the bottom of the scale. They stay percentages: it is the colour scale that is
     * logarithmic, not the values, which is what lets the legend read "0.1%" instead of "-1".
     */
    @Test
    public void testScalars() {
        double[] scalars =
                SiteSourceMapPlotter.scalars(new double[] {100d, 0.001d, 0d}, Double.NaN, 0.1d);
        assertArrayEquals(new double[] {100d, 0.1d}, scalars, 1e-9);
    }

    /**
     * A threshold leaves the negligible sections out entirely, and the scalars stay aligned with
     * the sections that remain.
     */
    @Test
    public void testScalarsWithAThreshold() {
        double[] percentages = {100d, 0.001d, 0d};
        double[] scalars = SiteSourceMapPlotter.scalars(percentages, 0.1d, 0.1d);
        assertArrayEquals(new double[] {100d}, scalars, 1e-12);
        assertEquals(1, SiteSourceMapPlotter.numDrawn(percentages, 0.1d));
    }

    /** Sections that no rupture reaching the site runs over are left off the map entirely. */
    @Test
    public void testDrawnExcludesNonContributors() {
        double[] percentages = new SiteSourceMapPlotter().percentages(contributions());
        // the joint rupture has no rate here, but its sections are used by the other two
        assertEquals(4, SiteSourceMapPlotter.numDrawn(percentages, Double.NaN));

        percentages[2] = 0;
        assertEquals(3, SiteSourceMapPlotter.numDrawn(percentages, Double.NaN));
        List<FaultSection> drawn =
                SiteSourceMapPlotter.drawn(contributions().getRupSet(), percentages, Double.NaN);
        assertEquals(List.of(0, 1, 3), drawn.stream().map(FaultSection::getSectionId).toList());
    }

    /** Sections that are a source but a negligible one are left off too. */
    @Test
    public void testDrawnExcludesNegligibleSections() {
        // the crustal sections carry 25% each here and the interface ones 75% each
        double[] percentages = new SiteSourceMapPlotter().percentages(contributions());
        List<FaultSection> drawn =
                SiteSourceMapPlotter.drawn(contributions().getRupSet(), percentages, 50d);
        assertEquals(List.of(2, 3), drawn.stream().map(FaultSection::getSectionId).toList());
    }

    /** A threshold above everything on the map would leave nothing to draw, so it is rejected. */
    @Test
    public void testRejectsThresholdAboveEverything() throws IOException {
        try {
            new SiteSourceMapPlotter()
                    .setOmitBelowPercent(99d)
                    .plot(tempFolder.newFolder("above"), "sources", contributions(), "Test Site");
            fail("expected a threshold above the largest contribution to be rejected");
        } catch (IllegalStateException expected) {
            // as expected
        }
    }

    /** The threshold, not the decade count, sets the bottom of the scale when it is given. */
    @Test
    public void testPlotWithGreyThreshold() throws IOException {
        File outputDir = tempFolder.newFolder("grey");
        File map =
                new SiteSourceMapPlotter()
                        .setWeighting(SectionWeighting.proximity())
                        .setOmitBelowPercent(1d)
                        .plot(outputDir, "sources", contributions(), "Test Site");
        assertTrue(map.exists());
    }

    /**
     * The palette reports its bounds as percentages rather than as logarithms, which is what makes
     * OpenSHA label the colour bar in percent.
     */
    @Test
    public void testLogCPT() throws IOException {
        CPT cpt = new SiteSourceMapPlotter().logCPT(-2d, 1d);
        assertTrue(cpt.isLog10());
        assertEquals(0.01d, cpt.getMinValue(), 1e-9);
        assertEquals(10d, cpt.getMaxValue(), 1e-9);
    }

    /** Plotting writes both the map and the rupture listing next to it. */
    @Test
    public void testPlot() throws IOException {
        File outputDir = tempFolder.newFolder("map");
        File map =
                new SiteSourceMapPlotter().plot(outputDir, "sources", contributions(), "Test Site");

        assertTrue(map.exists());
        assertEquals("sources.png", map.getName());
    }

    /**
     * There is no scale to draw if nothing contributes, so say so rather than write an empty map.
     */
    @Test
    public void testRejectsEmptyContributions() throws IOException {
        FaultSystemSolution solution = makeSolution();
        SiteSourceContributions empty =
                new SiteSourceContributions(
                        solution,
                        SITE,
                        0d,
                        0.5d,
                        new double[solution.getRupSet().getNumRuptures()]);
        try {
            new SiteSourceMapPlotter().plot(tempFolder.getRoot(), "empty", empty, "Test");
            fail("expected empty contributions to be rejected");
        } catch (IllegalStateException expected) {
            // as expected
        }
    }
}

package nz.cri.gns.NZSHM22.opensha.hazard.joint;

import static nz.cri.gns.NZSHM22.opensha.hazard.joint.JointTestSolutions.*;
import static org.junit.Assert.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jfree.data.Range;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.commons.util.cpt.CPT;

/** Tests for {@link HazardComparisonReport}: the side by side hazard comparison report. */
public class HazardComparisonReportTest {

    @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

    /** The sites the report compares curves at all exist and keep the order they are listed in. */
    @Test
    public void testDefaultSites() {
        Map<String, Location> sites = HazardComparisonReport.defaultSites();
        assertEquals(HazardComparisonReport.DEFAULT_SITE_NAMES.size(), sites.size());
        assertEquals(HazardComparisonReport.DEFAULT_SITE_NAMES, new ArrayList<>(sites.keySet()));
        assertNotNull(sites.get("Wellington"));
        assertNotNull(sites.get("Invercargill"));
    }

    /**
     * End to end: both hazard sources are calculated, and the report holds a map row and a curve
     * row per site, each with the two configs and their difference.
     */
    @Test
    public void testGenerate() throws Exception {
        File outputDir = tempFolder.newFolder("report");
        File index = report(outputDir).generate();

        assertTrue(index.exists());
        assertEquals(HazardComparisonReport.INDEX_FILE, index.getName());

        String html = Files.readString(index.toPath(), StandardCharsets.UTF_8);
        assertTrue(html.contains("Classic"));
        assertTrue(html.contains("Joint"));
        assertTrue(html.contains("Test Site"));
        assertTrue("expected a hazard map section", html.contains("id=\"maps\""));
        assertTrue("expected a hazard curve section", html.contains("id=\"curves\""));
        assertTrue("expected a hazard source section", html.contains("id=\"sources\""));
        // clicking a figure opens the image, both with and without javascript
        assertTrue(
                html.contains(
                        "<a class=\"zoom\" href=\"" + HazardComparisonReport.IMAGE_DIR + "/"));
        assertTrue(html.contains("id=\"lightbox\""));
        // the anchor closes before the caption, so the caption is not part of the link
        assertTrue(
                "the figure caption should not be inside the link",
                html.contains("></a>\n<figcaption>"));
        // the source figures link to the site page instead of opening in place
        assertTrue(
                html.contains(
                        "<a href=\""
                                + SiteSourcePage.SOURCES_DIR
                                + "/test_site/"
                                + ReportPage.INDEX_FILE));

        List<String> images = imagesIn(html);
        // two return periods and one site, each with two configs and a difference, plus the one
        // source influence difference map
        assertEquals(10, images.size());
        for (String image : images) {
            File file = new File(outputDir, image);
            assertTrue(image + " should exist", file.exists());
            assertTrue(image + " should not be empty", file.length() > 0);
        }
        // the difference figures are the point of the report
        assertTrue(images.stream().anyMatch(i -> i.contains("map_pga_two_in_50_diff")));
        assertTrue(images.stream().anyMatch(i -> i.contains("curve_test_site_pga_diff")));
        assertTrue(images.stream().anyMatch(i -> i.contains("test_site_influence_diff")));
    }

    /**
     * The source sites all exist, and the spread deliberately covers the Alpine Fault and the Taupo
     * Volcanic Zone.
     */
    @Test
    public void testDefaultSourceSites() {
        Map<String, Location> sites = HazardComparisonReport.defaultSourceSites();
        assertEquals(
                HazardComparisonReport.DEFAULT_SOURCE_SITE_NAMES, new ArrayList<>(sites.keySet()));
        assertNotNull("expected a site on the Alpine Fault", sites.get("Franz Josef"));
        assertNotNull("expected a site in the TVZ", sites.get("Taupo"));
    }

    /**
     * Each source site gets its own page holding the maps the report itself does not show: both
     * weightings, each solution on its own and their difference.
     */
    @Test
    public void testSourceSitePage() throws Exception {
        File outputDir = tempFolder.newFolder("site-page");
        report(outputDir).generate();

        File siteDir = new File(new File(outputDir, SiteSourcePage.SOURCES_DIR), "test_site");
        File page = new File(siteDir, ReportPage.INDEX_FILE);
        assertTrue(page.exists());

        String html = Files.readString(page.toPath(), StandardCharsets.UTF_8);
        assertTrue(html.contains("Test Site hazard sources"));
        assertTrue("expected a link back to the report", html.contains("../../index.html"));
        assertTrue(html.contains(SectionWeighting.proximity().getLabel()));
        assertTrue(html.contains(SectionWeighting.participation().getLabel()));

        // both weightings, each with a difference and one map per solution
        List<String> images = imagesIn(html);
        assertEquals(6, images.size());
        for (String image : images) {
            assertTrue(image + " should exist", new File(siteDir, image).exists());
        }
        assertTrue(new File(siteDir, "test_site_influence_sections.csv").exists());
    }

    /** Maps of different regions cannot be differenced, so this is caught before calculating. */
    @Test
    public void testRejectsMismatchedRegions() throws Exception {
        HazardComparisonReport report = report(tempFolder.newFolder("mismatched"));
        report.second
                .getInput()
                .setRegion(
                        new GriddedRegion(
                                new Region(new Location(-41.5, 174.6), new Location(-41.2, 175.0)),
                                0.1,
                                GriddedRegion.ANCHOR_0_0));
        try {
            report.generate();
            fail("expected differing regions to be rejected");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("same region"));
        }
    }

    @Test
    public void testRejectsMismatchedPeriods() throws Exception {
        HazardComparisonReport report = report(tempFolder.newFolder("periods"));
        report.second.getInput().setPeriods(1d);
        try {
            report.generate();
            fail("expected differing periods to be rejected");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("same periods"));
        }
    }

    /** The difference is a percentage change, and undefined where the first map has no hazard. */
    @Test
    public void testPercentDiff() {
        GriddedRegion region = mapRegion();
        GriddedGeoDataSet first = new GriddedGeoDataSet(region, false);
        GriddedGeoDataSet second = new GriddedGeoDataSet(region, false);
        for (int i = 0; i < region.getNodeCount(); i++) {
            first.set(i, i == 0 ? 0d : 0.2);
            second.set(i, i == 0 ? 0.1 : 0.3);
        }

        GriddedGeoDataSet diff = HazardComparisonReport.percentDiff(first, second);
        assertTrue("no hazard to compare against", Double.isNaN(diff.get(0)));
        assertEquals(50d, diff.get(1), 1e-9);
    }

    /** The difference map is the ratio, undefined where the first map has no hazard. */
    @Test
    public void testRatioMap() {
        GriddedRegion region = mapRegion();
        GriddedGeoDataSet first = new GriddedGeoDataSet(region, false);
        GriddedGeoDataSet second = new GriddedGeoDataSet(region, false);
        for (int i = 0; i < region.getNodeCount(); i++) {
            first.set(i, i == 0 ? 0d : 0.2);
            second.set(i, i == 0 ? 0.1 : 0.3);
        }

        GriddedGeoDataSet ratio = HazardComparisonReport.ratioMap(first, second);
        assertTrue("no hazard to compare against", Double.isNaN(ratio.get(0)));
        assertEquals(1.5, ratio.get(1), 1e-9);
    }

    /**
     * The difference colour ramp follows the data: small differences are not flattened, and large
     * ones are covered rather than clipped.
     */
    @Test
    public void testRatioCPTScalesToTheData() throws Exception {
        assertEquals(1.1, ratioCPT(1.05).getMaxValue(), 1e-9);
        assertEquals(2d, ratioCPT(1.8).getMaxValue(), 1e-9);
        assertEquals(30d, ratioCPT(10.4).getMaxValue(), 1e-9);
        // a map of decreases scales the other side instead
        assertEquals(0.5, ratioCPT(1 / 1.8).getMinValue(), 1e-9);
    }

    /**
     * However far the map runs, the scale covers it. A tenfold increase used to saturate against a
     * ramp that stopped at +500%.
     */
    @Test
    public void testRatioCPTCoversEverything() throws Exception {
        assertTrue(ratioCPT(10.4).getMaxValue() >= 10.4);
        assertTrue(ratioCPT(2500d).getMaxValue() >= 2500d);
        assertTrue(ratioCPT(1e6).getMaxValue() >= 1e6);
        assertTrue(ratioCPT(1e-6).getMinValue() <= 1e-6);
    }

    /**
     * Each side of the ramp is fitted separately, so a map where everything moved the same way uses
     * the whole ramp instead of spending half of it on changes that do not occur.
     */
    @Test
    public void testRatioCPTFitsEachSide() throws Exception {
        CPT cpt = ratioCPT(1.8);
        assertEquals(2d, cpt.getMaxValue(), 1e-9);
        assertEquals(
                "nothing decreased, so the ramp starts at no change", 1d, cpt.getMinValue(), 1e-9);
    }

    /** No change keeps the palette's neutral colour however lopsided the two sides are. */
    @Test
    public void testRatioCPTPinsNoChange() throws Exception {
        CPT lopsided = HazardComparisonReport.divergingRatioCPT(-Math.log10(1.5), Math.log10(100d));
        CPT even = HazardComparisonReport.divergingRatioCPT(-1d, 1d);
        assertEquals(even.getColorRaw(0f), lopsided.getColorRaw(0f));
        // the bounds are the log ratios the ramp is laid out over
        assertEquals(-Math.log10(1.5), lopsided.getMinValue(), 1e-9);
        assertEquals(Math.log10(100d), lopsided.getMaxValue(), 1e-9);
    }

    /** Nodes where the second model has no hazard at all sit at the bottom of the ramp. */
    @Test
    public void testRatioCPTColoursZero() throws Exception {
        CPT cpt = ratioCPT(4d);
        assertTrue(cpt.isLog10());
        assertEquals(cpt.getMinColor(), cpt.getColor(0f));
    }

    /** The ramp a difference map gets when every node changed by the same factor. */
    private static CPT ratioCPT(double factor) throws Exception {
        GriddedRegion region = mapRegion();
        GriddedGeoDataSet ratio = new GriddedGeoDataSet(region, false);
        for (int i = 0; i < region.getNodeCount(); i++) {
            ratio.set(i, factor);
        }
        return HazardComparisonReport.ratioCPT(ratio);
    }

    /** The curve ratio ignores the tail where the two curves are just noise. */
    @Test
    public void testRatio() {
        DiscretizedFunc first = new ArbitrarilyDiscretizedFunc();
        DiscretizedFunc second = new ArbitrarilyDiscretizedFunc();
        first.set(0.1, 1e-2);
        second.set(0.1, 2e-2);
        first.set(0.2, 1e-9); // below the comparable threshold
        second.set(0.2, 1e-9);

        DiscretizedFunc ratio = HazardComparisonReport.ratio(first, second);
        assertEquals(1, ratio.size());
        assertEquals(2d, ratio.getY(0), 1e-9);
    }

    /** The ratio axis always shows no change, even when the two configs agree exactly. */
    @Test
    public void testRatioRangeAlwaysIncludesOne() {
        DiscretizedFunc ratio = new ArbitrarilyDiscretizedFunc();
        ratio.set(0.1, 1d);
        ratio.set(0.2, 1d);

        Range range = HazardComparisonReport.ratioRange(ratio);
        assertTrue(range.getLowerBound() < 1d);
        assertTrue(range.getUpperBound() > 1d);
    }

    /**
     * Ids name the figures, so two configs sharing one would overwrite each other's images and the
     * report would show the same figure under both captions. Caught before anything is calculated.
     */
    @Test
    public void testRejectsCollidingIds() throws Exception {
        HazardReportSource classic =
                HazardReportSource.combined(
                        "NZSHM22 v1", makeCrustalSolution(), makeSubductionSolution());
        HazardReportSource joint = HazardReportSource.joint("NZSHM22-v1", makeSolution());
        assertEquals("ids should collide for this test", classic.getId(), joint.getId());

        HazardComparisonReport report =
                new HazardComparisonReport(classic, joint, tempFolder.newFolder("colliding"))
                        .setRegion(mapRegion())
                        .setPeriods(0d)
                        .setSites(Map.of("Test Site", SITE));
        try {
            report.generate();
            fail("expected colliding ids to be rejected");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("nzshm22_v1"));
        }
    }

    /** A report over a small region, one period and one site, so that tests stay quick. */
    private HazardComparisonReport report(File outputDir) {
        HazardReportSource classic =
                HazardReportSource.combined(
                        "Classic", makeCrustalSolution(), makeSubductionSolution());
        HazardReportSource joint = HazardReportSource.joint("Joint", makeSolution());
        classic.getInput().setNumThreads(1);
        joint.getInput().setNumThreads(1);

        return new HazardComparisonReport(classic, joint, outputDir)
                .setRegion(mapRegion())
                .setPeriods(0d)
                .setSites(Map.of("Test Site", SITE))
                .setSourceSites(Map.of("Test Site", SITE));
    }

    /** Big enough to plot a map: a single row or column of nodes cannot be drawn. */
    private static GriddedRegion mapRegion() {
        return new GriddedRegion(
                new Region(new Location(-41.6, 174.5), new Location(-41.1, 175.2)),
                0.25,
                GriddedRegion.ANCHOR_0_0);
    }

    private static List<String> imagesIn(String html) {
        List<String> images = new ArrayList<>();
        Matcher matcher = Pattern.compile("<img src=\"([^\"]+)\"").matcher(html);
        while (matcher.find()) {
            images.add(matcher.group(1));
        }
        return images;
    }
}

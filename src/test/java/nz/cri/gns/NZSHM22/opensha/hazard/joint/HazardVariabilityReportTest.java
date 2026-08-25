package nz.cri.gns.NZSHM22.opensha.hazard.joint;

import static org.junit.Assert.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;

/** Tests for {@link HazardVariabilityReport}: the hazard variability across repeat runs. */
public class HazardVariabilityReportTest {

    @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

    /**
     * End to end: every run is calculated, and the report holds the mean and the two variability
     * maps per return period, plus one curve plot per site.
     */
    @Test
    public void testGenerate() throws Exception {
        File outputDir = tempFolder.newFolder("report");
        File index = report(outputDir).generate();

        assertTrue(index.exists());
        assertEquals(HazardVariabilityReport.INDEX_FILE, index.getName());

        String html = Files.readString(index.toPath(), StandardCharsets.UTF_8);
        assertTrue(html.contains("run 1"));
        assertTrue(html.contains("run 3"));
        assertTrue("expected a hazard map section", html.contains("id=\"maps\""));
        assertTrue("expected a hazard curve section", html.contains("id=\"curves\""));
        assertTrue(html.contains("Test Site"));

        List<String> images = imagesIn(html);
        // one period: two return periods with a mean, a cov and a spread map, plus one site curve
        assertEquals(7, images.size());
        for (String image : images) {
            File file = new File(outputDir, image);
            assertTrue(image + " should exist", file.exists());
            assertTrue(image + " should not be empty", file.length() > 0);
        }
        assertTrue(images.stream().anyMatch(i -> i.contains("map_pga_two_in_50_mean")));
        assertTrue(images.stream().anyMatch(i -> i.contains("map_pga_two_in_50_cov")));
        assertTrue(images.stream().anyMatch(i -> i.contains("map_pga_two_in_50_spread")));
        assertTrue(images.stream().anyMatch(i -> i.contains("curve_test_site_pga")));
    }

    /** A single run says nothing about variability. */
    @Test
    public void testRejectsASingleRun() {
        try {
            new HazardVariabilityReport(
                    List.of(HazardConfig.joint("only", JointTestSolutions.makeSolution())),
                    tempFolder.getRoot());
            fail("expected a single run to be rejected");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("at least two runs"));
        }
    }

    /** Maps of different regions cannot be compared, so this is caught before calculating. */
    @Test
    public void testRejectsMismatchedRegions() throws Exception {
        HazardVariabilityReport report = report(tempFolder.newFolder("mismatched"));
        report.configs
                .get(1)
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
        HazardVariabilityReport report = report(tempFolder.newFolder("periods"));
        report.configs.get(1).getInput().setPeriods(1d);
        try {
            report.generate();
            fail("expected differing periods to be rejected");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("same periods"));
        }
    }

    /** The mean, the coefficient of variation and the max spread of a set of maps. */
    @Test
    public void testStatistics() {
        // node 0: one run has no hazard, node 1: 0.1, 0.2, 0.3
        List<GriddedGeoDataSet> maps =
                maps(new double[] {0d, 0.1}, new double[] {0.2, 0.2}, new double[] {0.4, 0.3});

        GriddedGeoDataSet mean = HazardVariabilityReport.mean(maps);
        assertTrue("a run without hazard cannot be compared", Double.isNaN(mean.get(0)));
        assertEquals(0.2, mean.get(1), 1e-9);

        // standard deviation of 0.1, 0.2, 0.3 is 0.1, i.e. half the mean
        assertEquals(50d, HazardVariabilityReport.coefficientOfVariation(maps).get(1), 1e-9);
        // and the extremes are 0.2 apart, i.e. the whole mean
        assertEquals(100d, HazardVariabilityReport.spread(maps).get(1), 1e-9);
    }

    /** Runs that agree exactly have no variability, rather than a division by zero. */
    @Test
    public void testIdenticalRunsHaveNoVariability() {
        List<GriddedGeoDataSet> maps = maps(new double[] {0.3, 0.3}, new double[] {0.3, 0.3});
        assertEquals(0d, HazardVariabilityReport.coefficientOfVariation(maps).get(0), 1e-9);
        assertEquals(0d, HazardVariabilityReport.spread(maps).get(0), 1e-9);
    }

    /**
     * The variability colour ramp follows the data: small differences are not flattened, large ones
     * do not saturate the whole map.
     */
    @Test
    public void testVariabilityCPTScalesToTheData() throws Exception {
        assertEquals(5d, variabilityScale(3d), 1e-9);
        assertEquals(25d, variabilityScale(20d), 1e-9);
        assertEquals(100d, variabilityScale(80d), 1e-9);
    }

    /** The curve stats report the spread of the ground motion at each map return period. */
    @Test
    public void testCurveStats() {
        String stats = HazardVariabilityReport.curveStats(List.of(curve(2d), curve(1d)));
        assertTrue(stats, stats.contains("2% in 50 year"));
        assertTrue(stats, stats.contains("10% in 50 year"));
        assertTrue(stats, stats.contains("% cov"));
        assertTrue(stats, stats.contains("% spread"));
    }

    /** Runs are picked up from a directory of run directories, sorted and named after them. */
    @Test
    public void testJointRunsIn() throws Exception {
        File runsDir = tempFolder.newFolder("runs");
        for (String name : List.of("joint02", "joint01")) {
            File runDir = new File(runsDir, name);
            assertTrue(runDir.mkdirs());
            JointTestSolutions.makeSolution()
                    .write(new File(runDir, HazardVariabilityReport.SOLUTION_FILE));
        }
        // a directory without a solution is not a run
        assertTrue(new File(runsDir, "notARun").mkdirs());

        List<HazardConfig> configs = HazardVariabilityReport.jointRunsIn(runsDir);
        assertEquals(2, configs.size());
        assertEquals("joint01", configs.get(0).getName());
        assertEquals("joint02", configs.get(1).getName());
        assertEquals(
                JointHazardInput.GmmMode.JOINT_RUPTURE, configs.get(0).getInput().getGmmMode());
    }

    @Test
    public void testRejectsADirectoryWithoutRuns() throws Exception {
        try {
            HazardVariabilityReport.jointRunsIn(tempFolder.newFolder("empty"));
            fail("expected a directory without runs to be rejected");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("solution.zip"));
        }
    }

    /** A report over three runs, a small region, one period and one site, so tests stay quick. */
    private HazardVariabilityReport report(File outputDir) {
        List<HazardConfig> configs = new ArrayList<>();
        double[] rates = {1e-3, 2e-3, 3e-3};
        for (int i = 0; i < rates.length; i++) {
            configs.add(HazardConfig.joint("run " + (i + 1), solutionWithRate(rates[i])));
        }
        return new HazardVariabilityReport(configs, outputDir)
                .setNumThreads(1)
                .setRegion(mapRegion())
                .setPeriods(0d)
                .setSites(Map.of("Test Site", JointTestSolutions.SITE));
    }

    /** The shared test solution at a given rupture rate, i.e. a run that found more hazard. */
    private static FaultSystemSolution solutionWithRate(double rate) {
        FaultSystemRupSet rupSet = JointTestSolutions.makeRupSet(0d);
        double[] rates = new double[rupSet.getNumRuptures()];
        Arrays.fill(rates, rate);
        return new FaultSystemSolution(rupSet, rates);
    }

    /** One map per run, each holding the given values. */
    private static List<GriddedGeoDataSet> maps(double[]... runs) {
        GriddedRegion region = mapRegion();
        List<GriddedGeoDataSet> maps = new ArrayList<>();
        for (double[] run : runs) {
            GriddedGeoDataSet map = new GriddedGeoDataSet(region, false);
            for (int node = 0; node < region.getNodeCount(); node++) {
                map.set(node, run[node % run.length]);
            }
            maps.add(map);
        }
        return maps;
    }

    /** The scale the variability ramp picks for a map that is this variable everywhere. */
    private static double variabilityScale(double variability) throws Exception {
        GriddedRegion region = mapRegion();
        GriddedGeoDataSet map = new GriddedGeoDataSet(region, false);
        for (int node = 0; node < region.getNodeCount(); node++) {
            map.set(node, variability);
        }
        return HazardVariabilityReport.variabilityCPT(map).getMaxValue();
    }

    /** A hazard curve scaled by the given factor, so that runs differ by a known amount. */
    private static DiscretizedFunc curve(double factor) {
        DiscretizedFunc curve = new ArbitrarilyDiscretizedFunc();
        curve.set(0.1, 1e-1 * factor);
        curve.set(0.5, 1e-2 * factor);
        curve.set(1.0, 1e-3 * factor);
        return curve;
    }

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

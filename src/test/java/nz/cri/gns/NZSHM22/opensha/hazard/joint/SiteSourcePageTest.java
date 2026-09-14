package nz.cri.gns.NZSHM22.opensha.hazard.joint;

import static nz.cri.gns.NZSHM22.opensha.hazard.joint.JointTestSolutions.*;
import static org.junit.Assert.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc.ReturnPeriods;

/**
 * Tests for {@link SiteSourcePage}: the per-site page of a hazard comparison, including the sites
 * and solutions that leave something undrawable.
 */
public class SiteSourcePageTest {

    @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

    static final ReturnPeriods RETURN_PERIOD = ReturnPeriods.TEN_IN_50;

    /** Rate given to every rupture of the test solution by default. */
    static final double RATE = 1e-3;

    /** A rate so low the site's hazard never reaches the return period. */
    static final double NEGLIGIBLE_RATE = 1e-12;

    /** The shared test solution with every rupture at the given rate. */
    static FaultSystemSolution solutionWithRate(double rate) {
        FaultSystemRupSet rupSet = makeRupSet(0d);
        double[] rates = new double[rupSet.getNumRuptures()];
        Arrays.fill(rates, rate);
        return new FaultSystemSolution(rupSet, rates);
    }

    static SiteSourcePage page(double referenceRate, double comparisonRate) {
        return new SiteSourcePage(
                new SiteSourceExplorer(solutionWithRate(referenceRate)),
                new SiteSourceExplorer(solutionWithRate(comparisonRate)),
                "Reference",
                "Comparison");
    }

    static File siteDir(File reportDir) {
        return new File(new File(reportDir, SiteSourcePage.SOURCES_DIR), "test_site");
    }

    static List<String> imagesIn(File page) throws Exception {
        String html = Files.readString(page.toPath(), StandardCharsets.UTF_8);
        List<String> images = new ArrayList<>();
        Matcher matcher = Pattern.compile("<img src=\"([^\"]+)\"").matcher(html);
        while (matcher.find()) {
            images.add(matcher.group(1));
        }
        return images;
    }

    /** A site with hazard in both solutions gets the difference and a map per solution. */
    @Test
    public void testWritesPage() throws Exception {
        File reportDir = tempFolder.newFolder("report");

        SiteSourcePage.Result result =
                page(RATE, 2 * RATE).write(reportDir, "Test Site", SITE, 0d, RETURN_PERIOD);

        assertNotNull(result);
        File index = new File(siteDir(reportDir), ReportPage.INDEX_FILE);
        assertTrue(index.exists());
        assertEquals(3, imagesIn(index).size());
        assertTrue(new File(reportDir, result.mapPath).exists());
    }

    /** Two solutions that agree are a valid comparison, not a failure. */
    @Test
    public void testWritesPageForIdenticalSolutions() throws Exception {
        File reportDir = tempFolder.newFolder("same");

        SiteSourcePage.Result result =
                page(RATE, RATE).write(reportDir, "Test Site", SITE, 0d, RETURN_PERIOD);

        assertNotNull(result);
        assertEquals(3, imagesIn(new File(siteDir(reportDir), ReportPage.INDEX_FILE)).size());
    }

    /**
     * A reference whose hazard never reaches the return period has no level to disaggregate at. The
     * page says so by returning null, and writes nothing.
     */
    @Test
    public void testSkipsSiteBelowReturnPeriod() throws Exception {
        File reportDir = tempFolder.newFolder("quiet");

        SiteSourcePage.Result result =
                page(NEGLIGIBLE_RATE, RATE).write(reportDir, "Test Site", SITE, 0d, RETURN_PERIOD);

        assertNull(result);
        assertFalse(new File(reportDir, SiteSourcePage.SOURCES_DIR).exists());
    }

    /**
     * A comparison whose hazard collapsed is the biggest change there is, so the page is still
     * written. Only the comparison's own map is left off, and the page says why.
     */
    @Test
    public void testWritesPageForCollapsedComparison() throws Exception {
        File reportDir = tempFolder.newFolder("collapsed");

        SiteSourcePage.Result result =
                page(RATE, NEGLIGIBLE_RATE).write(reportDir, "Test Site", SITE, 0d, RETURN_PERIOD);

        assertNotNull(result);
        File index = new File(siteDir(reportDir), ReportPage.INDEX_FILE);
        List<String> images = imagesIn(index);
        assertEquals(2, images.size());
        assertTrue(images.stream().noneMatch(i -> i.contains("_comparison")));
        String html = Files.readString(index.toPath(), StandardCharsets.UTF_8);
        assertTrue(html.contains("so there is no map of it"));
    }

    /** A page that fails part way leaves no images behind for nothing to link to. */
    @Test
    public void testRemovesSiteDirectoryOnFailure() throws Exception {
        File reportDir = tempFolder.newFolder("failing");
        SiteSourcePage failing =
                new SiteSourcePage(
                        new SiteSourceExplorer(solutionWithRate(RATE)),
                        new SiteSourceExplorer(solutionWithRate(2 * RATE)),
                        "Reference",
                        "Comparison") {
                    @Override
                    protected ReportPage.Table changeTable(SiteSourceComparison comparison) {
                        throw new IllegalStateException("table failed");
                    }
                };

        try {
            failing.write(reportDir, "Test Site", SITE, 0d, RETURN_PERIOD);
            fail("expected the failure to propagate");
        } catch (IllegalStateException expected) {
            assertEquals("table failed", expected.getMessage());
        }
        assertFalse(siteDir(reportDir).exists());
    }
}

package nz.cri.gns.NZSHM22.opensha.hazard.joint;

import com.google.common.base.Preconditions;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.commons.gui.plot.GeographicMapMaker;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc.ReturnPeriods;
import org.opensha.sha.faultSurface.FaultSection;

/**
 * The per-site page of a {@link HazardComparisonReport}: every source map of one site, for two
 * solutions, on one page. The report itself shows only the influence difference map of each site
 * and links to the page for the rest.
 *
 * <p>Six maps, from one disaggregation of each solution at one intensity measure level:
 *
 * <ul>
 *   <li>the influence and participation difference maps, {@link SiteSourceDiffMapPlotter}, and
 *   <li>the influence and participation map of each solution on its own, {@link
 *       SiteSourceMapPlotter}.
 * </ul>
 *
 * <p>All three maps of a weighting share one region and one greying threshold, so that the two
 * solutions and their difference can be read against each other rather than each being framed on
 * its own sources. The region is a buffer around the sections that clear the threshold in either
 * solution — the sections the maps are actually about — because the sections that are a source for
 * a site at all reach most of the country once long multi-fault ruptures are involved.
 */
public class SiteSourcePage {

    /** Directory under the report that the per-site pages and their images live in. */
    public static final String SOURCES_DIR = "sources";

    /**
     * Share of a site's hazard a section has to carry to be worth showing. Sections below it are
     * left off every map, see {@link SiteSourceMapPlotter#setOmitBelowPercent} and {@link
     * SiteSourceDiffMapPlotter#setOmitBelowPercent}.
     */
    public static final double NEGLIGIBLE_PERCENT = 0.2;

    /** Padding, in km, around the sections the maps are about. */
    public static final double BUFFER_KM = 50d;

    /** What a finished page offers back to the report that links to it. */
    public static class Result {
        /** Path of the influence difference map, relative to the report directory. */
        public final String mapPath;

        /** Path of the per-site page, relative to the report directory. */
        public final String pagePath;

        /** One line describing the change in the site's hazard. */
        public final String stats;

        protected Result(String mapPath, String pagePath, String stats) {
            this.mapPath = mapPath;
            this.pagePath = pagePath;
            this.stats = stats;
        }
    }

    protected final SiteSourceExplorer reference;
    protected final SiteSourceExplorer comparison;
    protected final String referenceName;
    protected final String comparisonName;

    /**
     * @param reference explorer for the baseline solution, which also sets the intensity measure
     *     level the two are compared at
     * @param comparison explorer for the solution compared against it
     * @param referenceName display name of the baseline solution
     * @param comparisonName display name of the other solution
     */
    public SiteSourcePage(
            SiteSourceExplorer reference,
            SiteSourceExplorer comparison,
            String referenceName,
            String comparisonName) {
        this.reference = reference;
        this.comparison = comparison;
        this.referenceName = referenceName;
        this.comparisonName = comparisonName;
    }

    /**
     * Disaggregates both solutions at the site and writes the page and its six maps.
     *
     * @param reportDir the report's directory; the page goes in a subdirectory of it
     * @param siteName the site's name, which also names its subdirectory
     * @param location the site
     * @param period the calculation period, 0 for PGA
     * @param returnPeriod the return period that sets the intensity measure level
     * @return where the report should point, see {@link Result}
     */
    public Result write(
            File reportDir,
            String siteName,
            Location location,
            double period,
            ReturnPeriods returnPeriod)
            throws IOException {
        String slug = HazardLabels.slug(siteName);
        File siteDir = new File(new File(reportDir, SOURCES_DIR), slug);
        Preconditions.checkState(
                siteDir.exists() || siteDir.mkdirs(),
                "Could not create output directory %s",
                siteDir.getAbsolutePath());
        File imageDir = new File(siteDir, ReportPage.IMAGE_DIR);

        // one disaggregation per solution, shared by both weightings and by all six maps
        double iml = reference.imlForReturnPeriod(location, period, returnPeriod);
        SiteSourceContributions referenceContributions =
                reference.exploreAtIml(location, period, iml);
        SiteSourceContributions comparisonContributions =
                comparison.exploreAtIml(location, period, iml);

        SiteSourceComparison influence =
                new SiteSourceComparison(
                        referenceContributions,
                        comparisonContributions,
                        SectionWeighting.proximity());
        SiteSourceComparison participation =
                new SiteSourceComparison(
                        referenceContributions,
                        comparisonContributions,
                        SectionWeighting.participation());

        ReportPage.Section section = new ReportPage.Section("Source maps", "sources");
        File influenceDiff =
                addWeighting(section, imageDir, slug, influence, siteName, "influence");
        addWeighting(section, imageDir, slug, participation, siteName, "participation");

        ReportPage page =
                new ReportPage(siteName + " hazard sources", siteDir)
                        .setIntro(intro(siteName, period, returnPeriod, influence))
                        .setSummary(summary(location, period, influence))
                        .setBackLink("../../" + ReportPage.INDEX_FILE, "Back to the comparison")
                        .add(section);

        File influenceCsv = new File(siteDir, slug + "_influence_sections.csv");
        influence.writeCSV(influenceCsv, 0);
        page.addDownload(influenceCsv, "Section influence, both solutions (CSV)");
        File participationCsv = new File(siteDir, slug + "_participation_sections.csv");
        participation.writeCSV(participationCsv, 0);
        page.addDownload(participationCsv, "Hazard through section, both solutions (CSV)");

        page.write();

        String base = SOURCES_DIR + "/" + slug + "/";
        return new Result(
                base + ReportPage.IMAGE_DIR + "/" + influenceDiff.getName(),
                base + ReportPage.INDEX_FILE,
                stats(influence));
    }

    /**
     * Adds one weighting's row of three maps — the difference, then each solution on its own — and
     * returns the difference map.
     */
    protected File addWeighting(
            ReportPage.Section section,
            File imageDir,
            String slug,
            SiteSourceComparison comparison,
            String siteName,
            String weightingSlug)
            throws IOException {
        Region region = region(comparison);
        String prefix = slug + "_" + weightingSlug;

        File diff =
                new SiteSourceDiffMapPlotter()
                        .setOmitBelowPercent(NEGLIGIBLE_PERCENT)
                        .setRegion(region)
                        .plot(imageDir, prefix + "_diff", comparison, siteName);
        File referenceMap =
                plotOne(
                        imageDir,
                        prefix + "_reference",
                        comparison.getReference(),
                        comparison.getWeighting(),
                        region,
                        siteName + " - " + referenceName);
        File comparisonMap =
                plotOne(
                        imageDir,
                        prefix + "_comparison",
                        comparison.getComparison(),
                        comparison.getWeighting(),
                        region,
                        siteName + " - " + comparisonName);

        ReportPage.Row row = new ReportPage.Row(comparison.getWeighting().getLabel());
        row.add(diff, "Change, " + comparisonName + " vs " + referenceName, stats(comparison));
        row.add(referenceMap, referenceName, null);
        row.add(comparisonMap, comparisonName, null);
        section.add(row);
        return diff;
    }

    protected static File plotOne(
            File imageDir,
            String prefix,
            SiteSourceContributions contributions,
            SectionWeighting weighting,
            Region region,
            String siteName)
            throws IOException {
        return new SiteSourceMapPlotter()
                .setWeighting(weighting)
                .setOmitBelowPercent(NEGLIGIBLE_PERCENT)
                .setRegion(region)
                .plot(imageDir, prefix, contributions, siteName);
    }

    /**
     * A buffer around the sections that clear {@link #NEGLIGIBLE_PERCENT} in either solution, so
     * that all three maps of a weighting are framed on the same area and on the part of it the maps
     * are about.
     */
    protected static Region region(SiteSourceComparison comparison) {
        List<FaultSection> sections = comparison.getSections();
        double[] percentages = comparison.getMaxPercentages();
        List<FaultSection> coloured = new ArrayList<>();
        for (int i = 0; i < sections.size(); i++) {
            if (percentages[i] >= NEGLIGIBLE_PERCENT) {
                coloured.add(sections.get(i));
            }
        }
        Preconditions.checkState(
                !coloured.isEmpty(),
                "No section carries %s percent of the hazard at this site under %s",
                NEGLIGIBLE_PERCENT,
                comparison.getWeighting().getLabel());
        return GeographicMapMaker.buildBufferedRegion(coloured, BUFFER_KM, true);
    }

    /** The change in the site's total rate of exceedance, as one line. */
    protected static String stats(SiteSourceComparison comparison) {
        double referenceRate = comparison.getReference().getTotalRate();
        double comparisonRate = comparison.getComparison().getTotalRate();
        return String.format(
                "%.3g/yr to %.3g/yr, x%.2f",
                referenceRate, comparisonRate, comparisonRate / referenceRate);
    }

    protected String intro(
            String siteName,
            double period,
            ReturnPeriods returnPeriod,
            SiteSourceComparison comparison) {
        return "Where the hazard at "
                + siteName
                + " comes from, at the "
                + HazardLabels.periodLabel(period)
                + " level that "
                + referenceName
                + " reaches at "
                + returnPeriod.label
                + ". Both solutions are disaggregated at that one level, so their rates can be"
                + " compared. Sections carrying less than "
                + NEGLIGIBLE_PERCENT
                + "% of the site's hazard in both solutions are left off, as are sections that no"
                + " rupture reaching the level runs over.";
    }

    protected ReportPage.Table summary(
            Location location, double period, SiteSourceComparison comparison) {
        return new ReportPage.Table("", referenceName, comparisonName)
                .addRow(
                        "Site",
                        (float) location.getLatitude() + ", " + (float) location.getLongitude())
                .addRow(
                        "Level",
                        HazardLabels.periodLabel(period)
                                + " > "
                                + (float) comparison.getIml()
                                + " "
                                + HazardLabels.periodUnits(period))
                .addRow(
                        "Rate of exceedance",
                        (float) comparison.getReference().getTotalRate() + "/yr",
                        (float) comparison.getComparison().getTotalRate() + "/yr")
                .addRow(
                        "Contributing ruptures",
                        String.valueOf(comparison.getReference().getNumContributingRuptures()),
                        String.valueOf(comparison.getComparison().getNumContributingRuptures()));
    }
}

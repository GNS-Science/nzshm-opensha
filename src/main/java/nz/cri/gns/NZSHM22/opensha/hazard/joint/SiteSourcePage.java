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
 * The per-site page of a {@link HazardComparisonReport}: where one site's hazard comes from in two
 * solutions, and what changed. The report itself shows only the difference map of each site and
 * links here for the rest.
 *
 * <p>From one disaggregation of each solution at one intensity measure level: the difference map,
 * {@link SiteSourceDiffMapPlotter}, each solution's own map, {@link SiteSourceMapPlotter}, and
 * {@link #TABLE_ROWS} rows of the sections that changed most.
 *
 * <p>The three maps share one region and one threshold, so that the two solutions and their
 * difference can be read against each other rather than each being framed on its own sources. The
 * region is a buffer around the sections that clear the threshold in either solution — the sections
 * the maps are actually about — because the sections that are a source for a site at all reach most
 * of the country once long multi-fault ruptures are involved.
 */
public class SiteSourcePage {

    /** Directory under the report that the per-site pages and their images live in. */
    public static final String SOURCES_DIR = "sources";

    /**
     * Share of a site's hazard a section has to carry to be worth showing, as a percentage of the
     * <em>reference</em> solution's total. It is turned into an absolute rate once, by {@link
     * #negligibleRate}, and that one rate is the cut on all three maps.
     *
     * <p>Taking it from one solution matters. The two solutions have different totals, so a
     * threshold applied to each map's own share cuts the two sides at different amounts of hazard,
     * and sections drop out of one map that the map beside it still draws — which looks like a
     * finding and is not one.
     */
    public static final double NEGLIGIBLE_PERCENT = 0.2;

    /** Padding, in km, around the sections the maps are about. */
    public static final double BUFFER_KM = 50d;

    /** How many sections the page tabulates. The CSV download carries the rest. */
    public static final int TABLE_ROWS = 20;

    /** What a finished page offers back to the report that links to it. */
    public static class Result {
        /** Path of the difference map, relative to the report directory. */
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
     * Disaggregates both solutions at the site and writes the page, its three maps and its table.
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

        // one disaggregation per solution, shared by all three maps and the table
        double iml = reference.imlForReturnPeriod(location, period, returnPeriod);
        SiteSourceContributions referenceContributions =
                reference.exploreAtIml(location, period, iml);
        SiteSourceContributions comparisonContributions =
                comparison.exploreAtIml(location, period, iml);

        SiteSourceComparison changes =
                new SiteSourceComparison(referenceContributions, comparisonContributions);

        ReportPage.Section section = new ReportPage.Section("Source maps", "sources");
        File diff = addMaps(section, imageDir, slug, changes, siteName);
        section.add(
                new ReportPage.DataTable(
                        "The " + TABLE_ROWS + " sections whose hazard changed most",
                        changeTable(changes)));

        ReportPage page =
                new ReportPage(siteName + " hazard sources", siteDir)
                        .setIntro(intro(siteName, period, returnPeriod))
                        .setSummary(summary(location, period, changes))
                        .setBackLink("../../" + ReportPage.INDEX_FILE, "Back to the comparison")
                        .add(section);

        File csv = new File(siteDir, slug + "_sections.csv");
        changes.writeCSV(csv, 0);
        page.addDownload(csv, "Every section, both solutions (CSV)");

        page.write();

        String base = SOURCES_DIR + "/" + slug + "/";
        return new Result(
                base + ReportPage.IMAGE_DIR + "/" + diff.getName(),
                base + ReportPage.INDEX_FILE,
                stats(changes));
    }

    /**
     * Adds the row of three maps — the difference, then each solution on its own — and returns the
     * difference map.
     */
    protected File addMaps(
            ReportPage.Section section,
            File imageDir,
            String slug,
            SiteSourceComparison comparison,
            String siteName)
            throws IOException {
        Region region = region(comparison);
        double floor = negligibleRate(comparison);
        // one scale and one unit across both single solution maps, so that the same amount of
        // hazard is the same colour on each and the pair can be read against each other
        double top = max(comparison.getMaxRates());
        double years = HazardLabels.rateUnitYears(top);

        File diff =
                new SiteSourceDiffMapPlotter()
                        .setOmitBelowRate(floor)
                        .setRegion(region)
                        .plot(imageDir, slug + "_diff", comparison, siteName);
        File referenceMap =
                plotOne(
                        imageDir,
                        slug + "_reference",
                        comparison.getReference(),
                        region,
                        floor,
                        top,
                        years,
                        siteName + " - " + referenceName);
        File comparisonMap =
                plotOne(
                        imageDir,
                        slug + "_comparison",
                        comparison.getComparison(),
                        region,
                        floor,
                        top,
                        years,
                        siteName + " - " + comparisonName);

        ReportPage.Row row = new ReportPage.Row(HazardLabels.SECTION_HAZARD);
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
            Region region,
            double omitBelowRate,
            double maxRate,
            double unitYears,
            String siteName)
            throws IOException {
        return new SiteSourceMapPlotter()
                .setOmitBelowRate(omitBelowRate)
                .setMaxRate(maxRate)
                .setUnitYears(unitYears)
                .setRegion(region)
                .plot(imageDir, prefix, contributions, siteName);
    }

    /**
     * The contribution, in 1/yr, below which a section is left off every map: {@link
     * #NEGLIGIBLE_PERCENT} of the reference solution's total rate of exceedance.
     */
    protected static double negligibleRate(SiteSourceComparison comparison) {
        return NEGLIGIBLE_PERCENT / 100 * comparison.getReference().getTotalRate();
    }

    protected static double max(double[] values) {
        double max = 0;
        for (double value : values) {
            max = Math.max(max, value);
        }
        return max;
    }

    /**
     * A buffer around the sections that clear {@link #NEGLIGIBLE_PERCENT} in either solution, so
     * that all three maps of a weighting are framed on the same area and on the part of it the maps
     * are about.
     */
    protected static Region region(SiteSourceComparison comparison) {
        List<FaultSection> sections = comparison.getSections();
        double[] rates = comparison.getMaxRates();
        double floor = negligibleRate(comparison);
        List<FaultSection> coloured = new ArrayList<>();
        for (int i = 0; i < sections.size(); i++) {
            if (rates[i] >= floor) {
                coloured.add(sections.get(i));
            }
        }
        Preconditions.checkState(
                !coloured.isEmpty(),
                "No section carries %s /yr of the hazard at this site",
                floor);
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

    protected String intro(String siteName, double period, ReturnPeriods returnPeriod) {
        return "Where the hazard at "
                + siteName
                + " comes from, at the "
                + HazardLabels.periodLabel(period)
                + " level that "
                + referenceName
                + " reaches at "
                + returnPeriod.label
                + ". Both solutions are disaggregated at that one level, so their rates can be"
                + " compared. The maps colour each fault section by the hazard that reaches the"
                + " site through it: the annual rate at which ruptures running over that section"
                + " push the site over the level. Hazard is calculated per rupture, not per"
                + " section, so a rupture that breaks ten sections is counted in all ten — the"
                + " shares overlap and do not add up to the site's total, and one section can carry"
                + " more than 100% of it. That is deliberate: a long multi-fault rupture reaches"
                + " the site through every section it runs over, and colouring the whole footprint"
                + " is what shows which ruptures the hazard comes from. Sections carrying less than "
                + NEGLIGIBLE_PERCENT
                + "% of the site's hazard in both solutions are left off, as are sections that no"
                + " rupture reaching the level runs over.";
    }

    /**
     * The sections whose hazard changed most, as the page tabulates them: the difference map read
     * as numbers.
     *
     * <p>The three pairs of columns separate the ways a section's hazard can change. The rupture
     * rate says whether the section simply breaks more often. The magnitudes say whether the same
     * section is now carrying larger events, which is what happens when joint ruptures reach it.
     * The joint share says outright how much of the section's hazard now comes from ruptures
     * spanning crustal and interface sections.
     */
    protected ReportPage.Table changeTable(SiteSourceComparison comparison) {
        ReportPage.Table table =
                new ReportPage.Table(
                        "Section",
                        HazardLabels.SECTION_HAZARD + ", " + referenceName + " (1/yr)",
                        HazardLabels.SECTION_HAZARD + ", " + comparisonName + " (1/yr)",
                        "Change (1/yr)",
                        "Rupture rate, " + referenceName + " (1/yr)",
                        "Rupture rate, " + comparisonName + " (1/yr)",
                        "Mean M, " + referenceName,
                        "Mean M, " + comparisonName,
                        "Max M, " + referenceName,
                        "Max M, " + comparisonName,
                        "Joint share, " + comparisonName + " (%)");
        for (SiteSourceComparison.SectionChange change : comparison.topChanges(TABLE_ROWS)) {
            table.addRow(
                    change.name,
                    rate(change.referenceRate),
                    rate(change.comparisonRate),
                    signedRate(change.getChange()),
                    rate(change.referenceSolutionRate),
                    rate(change.comparisonSolutionRate),
                    mag(change.referenceMeanMag),
                    mag(change.comparisonMeanMag),
                    mag(change.referenceMaxMag),
                    mag(change.comparisonMaxMag),
                    String.format("%.0f%%", change.jointPercent));
        }
        return table;
    }

    /** A rate to three significant figures, or a dash where the section is not a source at all. */
    protected static String rate(double rate) {
        return rate > 0 ? String.format("%.3g", rate) : "-";
    }

    /** As {@link #rate}, always signed, so that a gain and a loss can be told apart at a glance. */
    protected static String signedRate(double rate) {
        return rate == 0 ? "-" : String.format("%+.3g", rate);
    }

    /** A magnitude, or a dash where the solution routes no hazard through the section. */
    protected static String mag(double mag) {
        return Double.isNaN(mag) ? "-" : String.format("%.2f", mag);
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

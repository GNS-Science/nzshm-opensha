package nz.cri.gns.NZSHM22.opensha.hazard.joint;

import com.google.common.base.Preconditions;
import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jfree.data.Range;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotUtils;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc.ReturnPeriods;

/**
 * Generates a standalone HTML report comparing the hazard of two {@link HazardReportSource hazard
 * sources}, typically the classic NZSHM22 model (separate crustal and subduction inversions
 * calculated together) against a joint inversion.
 *
 * <p>The report holds, side by side and each followed by a difference view:
 *
 * <ul>
 *   <li>a hazard map per period and return period, and
 *   <li>a hazard curve per site and period, for the sites of {@link #defaultSites()}.
 * </ul>
 *
 * <p>Map differences are drawn as the percentage change from the first config to the second, {@code
 * 100 * (second - first) / first}, so red means the second config gives stronger shaking. The scale
 * runs from the smallest to the largest change on the map, so nothing is clipped, and it colours
 * decreases and increases at the same rate, so a change of a given size looks the same whichever
 * way it went. Colour follows the logarithm of the change in both directions, so an outlier
 * somewhere does not flatten the rest of the map, and changes under {@link #NO_CHANGE_PERCENT}
 * count as none and are drawn in the no-change colour of {@link #setNoChangeColor}. See {@link
 * #percentDiffCPT}. Figure captions report the same numbers. Clicking any figure opens it full
 * size.
 *
 * <p>Both configs must be calculated over the same region and the same periods, otherwise the maps
 * cannot be differenced. Use {@link #setRegion} or {@link #setSpacing} to set them together.
 */
public class HazardComparisonReport {

    /** Sites that hazard curves are compared at. */
    public static final List<String> DEFAULT_SITE_NAMES =
            List.of(
                    "Napier",
                    "Taupo",
                    "Gisborne",
                    "Wellington",
                    "Kaikoura",
                    "Christchurch",
                    "Dunedin",
                    "Westport",
                    "Franz Josef(SRG 164)",
                    "Queenstown",
                    "Invercargill");

    /**
     * Sites that the sources of the hazard are mapped at, a spread down the country rather than the
     * full curve site list: over the northern Hikurangi interface (Gisborne), in the Taupo Volcanic
     * Zone (Taupo), where crustal and interface sources meet (Wellington), in the Marlborough fault
     * system (Kaikoura), on the Alpine Fault (Franz Josef) and at some distance from any major
     * fault (Christchurch).
     *
     * <p>Each site costs a full disaggregation of both solutions, so the list is deliberately
     * short. It also deliberately leaves out the sites whose hazard is almost all distributed
     * seismicity, Auckland above all: these calculations exclude the background, so such a site has
     * nothing to disaggregate and would be skipped anyway.
     */
    public static final List<String> DEFAULT_SOURCE_SITE_NAMES =
            List.of(
                    "Gisborne",
                    "Taupo",
                    "Wellington",
                    "Kaikoura",
                    "Franz Josef(SRG 164)",
                    "Christchurch");

    /** Return period that the source maps disaggregate at. */
    public static final ReturnPeriods SOURCE_RETURN_PERIOD = ReturnPeriods.TEN_IN_50;

    /** Directory that images are written to, relative to the report. See {@link ReportPage}. */
    public static final String IMAGE_DIR = ReportPage.IMAGE_DIR;

    public static final String INDEX_FILE = ReportPage.INDEX_FILE;

    /**
     * Half-width in percent of the difference colour ramp when the two configs agree everywhere. A
     * ramp needs a non-empty range even when there is no change to show. See {@link
     * #percentDiffCPT}.
     */
    protected static final double EMPTY_DIFF_SCALE = 1d;

    /**
     * Percentage change below which the difference maps call it no change: the floor of their log
     * colour ramp, and the half-width of the band drawn in the no-change colour. An absolute figure
     * rather than a share of the map's range, so that it means the same thing on every map in the
     * report. See {@link #percentDiffCPT}.
     */
    protected static final double NO_CHANGE_PERCENT = 1d;

    /**
     * The colour the no-change band is drawn in unless {@link #setNoChangeColor} says otherwise.
     * See {@link #NO_CHANGE_PERCENT}.
     */
    public static final Color DEFAULT_NO_CHANGE_COLOR = DivergingCPT.DEFAULT_ZERO_COLOR;

    /**
     * Annual exceedance probability below which curve values are ignored when comparing. Curves get
     * noisy and eventually drop to zero down there, and the ratio of two tiny numbers says nothing.
     */
    protected static final double MIN_COMPARABLE_PROBABILITY = 1e-7;

    protected static final Color FIRST_COLOR = new Color(0, 90, 181);
    protected static final Color SECOND_COLOR = new Color(200, 40, 30);

    protected final HazardReportSource first;
    protected final HazardReportSource second;
    protected final File outputDir;

    protected Map<String, Location> sites = defaultSites();
    protected Map<String, Location> sourceSites = defaultSourceSites();
    protected Color noChangeColor = DEFAULT_NO_CHANGE_COLOR;
    protected File imageDir;

    /**
     * Generates the report.
     *
     * @param first the reference hazard source, e.g. the classic NZSHM22 model
     * @param second the hazard source compared against it, e.g. a joint inversion
     * @param outputPath directory the report and its images are written to
     * @return the report's index.html
     */
    public static File generateReport(
            HazardReportSource first, HazardReportSource second, File outputPath)
            throws IOException {
        return new HazardComparisonReport(first, second, outputPath).generate();
    }

    public HazardComparisonReport(
            HazardReportSource first, HazardReportSource second, File outputDir) {
        this.first = Preconditions.checkNotNull(first, "need a first config");
        this.second = Preconditions.checkNotNull(second, "need a second config");
        this.outputDir = Preconditions.checkNotNull(outputDir, "need an output directory");
    }

    /** The sites of {@link #DEFAULT_SITE_NAMES}, in that order. */
    public static Map<String, Location> defaultSites() {
        return namedSites(DEFAULT_SITE_NAMES);
    }

    /** The sites of {@link #DEFAULT_SOURCE_SITE_NAMES}, in that order. */
    public static Map<String, Location> defaultSourceSites() {
        return namedSites(DEFAULT_SOURCE_SITE_NAMES);
    }

    /** Looks up named sites in {@link JointHazardInput#defaultSites()}, keeping the given order. */
    protected static Map<String, Location> namedSites(List<String> names) {
        Map<String, Location> all = JointHazardInput.defaultSites();
        Map<String, Location> sites = new LinkedHashMap<>();
        for (String name : names) {
            Location location = all.get(name);
            Preconditions.checkState(location != null, "Unknown location %s", name);
            sites.put(name, location);
        }
        return sites;
    }

    /** Sets the sites that curves are compared at. Defaults to {@link #defaultSites()}. */
    public HazardComparisonReport setSites(Map<String, Location> sites) {
        Preconditions.checkArgument(sites != null && !sites.isEmpty(), "need at least one site");
        this.sites = sites;
        return this;
    }

    /**
     * Sets the sites that the sources of the hazard are mapped at. Defaults to {@link
     * #defaultSourceSites()}. Each site costs a disaggregation of both solutions, so keep the list
     * short.
     */
    public HazardComparisonReport setSourceSites(Map<String, Location> sourceSites) {
        Preconditions.checkArgument(
                sourceSites != null && !sourceSites.isEmpty(), "need at least one source site");
        this.sourceSites = sourceSites;
        return this;
    }

    /**
     * Sets the colour that every difference map in the report draws its no-change band in, both the
     * hazard maps and the per-site source maps, so that the cells and sections which did not really
     * move read as their own thing rather than as a weak change. Defaults to {@link
     * #DEFAULT_NO_CHANGE_COLOR}.
     *
     * @param noChangeColor the colour, or null to leave the band in the palette's own neutral
     *     colour
     */
    public HazardComparisonReport setNoChangeColor(Color noChangeColor) {
        this.noChangeColor = noChangeColor;
        return this;
    }

    /** Sets the map region of both configs, so that the two maps can be differenced. */
    public HazardComparisonReport setRegion(GriddedRegion region) {
        first.getInput().setRegion(region);
        second.getInput().setRegion(region);
        return this;
    }

    /** Sets the map resolution of both configs in degrees. */
    public HazardComparisonReport setSpacing(double spacing) {
        first.getInput().setSpacing(spacing);
        second.getInput().setSpacing(spacing);
        return this;
    }

    /** Sets the periods of both configs. 0 is PGA, positive values are SA periods. */
    public HazardComparisonReport setPeriods(double... periods) {
        first.getInput().setPeriods(periods);
        second.getInput().setPeriods(periods);
        return this;
    }

    /**
     * Calculates both hazard sources, writes the plots and the report.
     *
     * @return the report's index.html
     * @throws IllegalArgumentException if the two configs disagree on the region or the periods
     * @throws IllegalStateException if either solution fails {@link JointHazardInput#validate()}
     */
    public File generate() throws IOException {
        double[] periods = first.getInput().getPeriods();
        Preconditions.checkArgument(
                Arrays.equals(periods, second.getInput().getPeriods()),
                "Both configs must use the same periods, got %s and %s",
                Arrays.toString(periods),
                Arrays.toString(second.getInput().getPeriods()));
        Preconditions.checkArgument(
                first.getInput().getRegion().equalsRegion(second.getInput().getRegion()),
                "Both configs must be calculated over the same region, otherwise their maps cannot"
                        + " be differenced. Use setRegion or setSpacing to set them together.");
        // ids name the figures, so two configs sharing one would silently overwrite each other's
        // images and the report would show the same figure under both captions
        Preconditions.checkArgument(
                !first.getId().equals(second.getId()),
                "Both configs reduce to the id %s, which is what their figures are named after."
                        + " Give them names that differ by more than punctuation: %s and %s.",
                first.getId(),
                first.getName(),
                second.getName());

        JointHazardInput.ValidationResult firstValidation = first.getInput().validate();
        JointHazardInput.ValidationResult secondValidation = second.getInput().validate();

        ReportPage page = new ReportPage(title(), outputDir);
        page.setIntro(intro());
        page.setSummary(summary(firstValidation, secondValidation));
        imageDir = page.imageDir();

        JointHazardMapCalculator firstCalc = calculate(first);
        JointHazardMapCalculator secondCalc = calculate(second);

        page.add(mapSection(firstCalc, secondCalc, periods));
        ReportPage.Section sourceSection = sourceSection(firstCalc, secondCalc, periods[0]);
        if (sourceSection != null) {
            page.add(sourceSection);
        }
        page.add(curveSection(firstCalc, secondCalc, periods));

        File index = page.write();
        System.out.println("Wrote hazard comparison report to " + index.getAbsolutePath());
        return index;
    }

    protected String intro() {
        return "Map differences are the percentage change from "
                + first.getName()
                + " to "
                + second.getName()
                + ".";
    }

    protected JointHazardMapCalculator calculate(HazardReportSource config) {
        System.out.println(
                "Calculating hazard for "
                        + config.getName()
                        + " at "
                        + config.getInput().getRegion().getNodeCount()
                        + " sites using "
                        + config.getInput().getGmmMode());
        JointHazardMapCalculator calculator = new JointHazardMapCalculator(config.getInput());
        calculator.calcHazardCurves();
        return calculator;
    }

    /** One hazard map per period and return period, for each config, plus their difference. */
    protected ReportPage.Section mapSection(
            JointHazardMapCalculator firstCalc,
            JointHazardMapCalculator secondCalc,
            double[] periods)
            throws IOException {
        ReportPage.Section section = new ReportPage.Section("Hazard maps", "maps");
        for (double period : periods) {
            for (ReturnPeriods rp : SolHazardMapCalc.MAP_RPS) {
                GriddedGeoDataSet firstMap = firstCalc.getCalc().buildMap(period, rp);
                GriddedGeoDataSet secondMap = secondCalc.getCalc().buildMap(period, rp);

                String periodLabel = HazardLabels.periodLabel(period);
                String units = HazardLabels.periodUnits(period);
                String prefix =
                        "map_"
                                + HazardLabels.periodPrefix(period)
                                + "_"
                                + HazardLabels.slug(rp.name());
                String zLabel = periodLabel + " (" + units + "), " + rp.label;

                CPT cpt = sharedLogCPT(firstMap, secondMap);
                ReportPage.Row row = new ReportPage.Row(periodLabel + ", " + rp.label);
                row.add(
                        firstCalc
                                .getCalc()
                                .plotMap(
                                        imageDir,
                                        prefix + "_" + first.getId(),
                                        firstMap,
                                        cpt,
                                        first.getName(),
                                        zLabel),
                        first.getName(),
                        mapStats(firstMap, units));
                row.add(
                        secondCalc
                                .getCalc()
                                .plotMap(
                                        imageDir,
                                        prefix + "_" + second.getId(),
                                        secondMap,
                                        cpt,
                                        second.getName(),
                                        zLabel),
                        second.getName(),
                        mapStats(secondMap, units));

                // the map and its caption show the same thing: percentage change, on a ramp
                // fitted to the extremes of this map with no change on the neutral colour
                GriddedGeoDataSet diffMap = percentDiff(firstMap, secondMap);
                row.add(
                        firstCalc
                                .getCalc()
                                .plotMap(
                                        imageDir,
                                        prefix + "_diff",
                                        diffMap,
                                        percentDiffCPT(diffMap, noChangeColor),
                                        differenceLabel(),
                                        "% change, " + periodLabel + ", " + rp.label),
                        "Difference",
                        diffStats(diffMap));
                section.add(row);
            }
        }
        return section;
    }

    /**
     * The difference map of each source site, each linking to a page holding that site's other
     * source maps and the sections that changed most. See {@link SiteSourcePage}.
     *
     * <p>Only the first period is mapped. A disaggregation is a pass over every rupture of both
     * solutions, so one per site is already the expensive part of the report; a second period would
     * double it for a view the curves already cover.
     *
     * @return the section, or null if no site could be disaggregated at all
     */
    protected ReportPage.Section sourceSection(
            JointHazardMapCalculator firstCalc, JointHazardMapCalculator secondCalc, double period)
            throws IOException {
        ReportPage.Section section = new ReportPage.Section("Hazard sources", "sources");
        SiteSourcePage pages =
                new SiteSourcePage(
                        new SiteSourceExplorer(firstCalc.getSetup()),
                        new SiteSourceExplorer(secondCalc.getSetup()),
                        first.getName(),
                        second.getName());
        pages.setNoChangeColor(noChangeColor);

        ReportPage.Row row =
                new ReportPage.Row(
                        HazardLabels.SECTION_HAZARD
                                + ", "
                                + HazardLabels.periodLabel(period)
                                + " at "
                                + SOURCE_RETURN_PERIOD.label
                                + "Each fault section is coloured by the change in hazard rate attributed to that section (i.e. the disaggregation by subsection)."
                                + " A rupture is credited to every section it breaks. Click a"
                                + " map for details.");
        List<String> skipped = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (Map.Entry<String, Location> site : sourceSites.entrySet()) {
            System.out.println("Mapping hazard sources at " + site.getKey());
            SiteSourcePage.Result result;
            try {
                result =
                        pages.write(
                                outputDir,
                                site.getKey(),
                                site.getValue(),
                                period,
                                SOURCE_RETURN_PERIOD);
            } catch (Exception e) {
                // both hazard map calculations are done by the time we get here, so one site that
                // cannot be drawn must not take the whole report with it
                System.out.println("  skipped: " + e);
                e.printStackTrace();
                failed.add(site.getKey());
                continue;
            }
            if (result == null) {
                // a site whose fault hazard never reaches the return period has no level to
                // disaggregate at; report it and carry on rather than losing the whole report
                System.out.println(
                        "  skipped: the hazard never reaches " + SOURCE_RETURN_PERIOD.label);
                skipped.add(site.getKey());
            } else {
                row.add(result.mapPath, site.getKey(), result.stats, result.pagePath);
            }
        }
        if (row.figures.isEmpty()) {
            return null;
        }
        if (!skipped.isEmpty()) {
            row.setTitle(row.title + " No fault hazard to disaggregate at " + join(skipped) + ".");
        }
        if (!failed.isEmpty()) {
            row.setTitle(row.title + " Could not be drawn at " + join(failed) + ".");
        }
        section.add(row);
        return section;
    }

    /** Names joined for a sentence, e.g. "Auckland, Dunedin and Invercargill". */
    protected static String join(List<String> names) {
        if (names.size() == 1) {
            return names.get(0);
        }
        return String.join(", ", names.subList(0, names.size() - 1))
                + " and "
                + names.get(names.size() - 1);
    }

    /** One hazard curve per site and period, for each config, plus their comparison. */
    protected ReportPage.Section curveSection(
            JointHazardMapCalculator firstCalc,
            JointHazardMapCalculator secondCalc,
            double[] periods)
            throws IOException {
        ReportPage.Section section = new ReportPage.Section("Hazard curves", "curves");
        for (Map.Entry<String, Location> site : sites.entrySet()) {
            String siteName = site.getKey();
            for (double period : periods) {
                DiscretizedFunc firstCurve = firstCalc.calcSiteCurve(site.getValue(), period);
                DiscretizedFunc secondCurve = secondCalc.calcSiteCurve(site.getValue(), period);

                String periodLabel = HazardLabels.periodLabel(period);
                String xLabel = periodLabel + " (" + HazardLabels.periodUnits(period) + ")";
                String prefix =
                        "curve_"
                                + HazardLabels.slug(siteName)
                                + "_"
                                + HazardLabels.periodPrefix(period);
                Range xRange = new Range(firstCurve.getMinX(), firstCurve.getMaxX());
                Range yRange = CurvePlots.yRange(List.of(firstCurve, secondCurve));

                ReportPage.Row row = new ReportPage.Row(siteName + ", " + periodLabel);
                row.add(
                        plotCurve(
                                prefix + "_" + first.getId(),
                                siteName + " - " + first.getName(),
                                first.getName(),
                                firstCurve,
                                FIRST_COLOR,
                                xLabel,
                                xRange,
                                yRange),
                        first.getName(),
                        null);
                row.add(
                        plotCurve(
                                prefix + "_" + second.getId(),
                                siteName + " - " + second.getName(),
                                second.getName(),
                                secondCurve,
                                SECOND_COLOR,
                                xLabel,
                                xRange,
                                yRange),
                        second.getName(),
                        null);
                row.add(
                        plotCurveComparison(
                                prefix + "_diff",
                                siteName + " - " + differenceLabel(),
                                firstCurve,
                                secondCurve,
                                xLabel,
                                xRange,
                                yRange),
                        "Difference",
                        curveStats(firstCurve, secondCurve));
                section.add(row);
            }
        }
        return section;
    }

    /** A single curve, with the return periods of the maps marked. */
    protected File plotCurve(
            String prefix,
            String title,
            String curveName,
            DiscretizedFunc curve,
            Color color,
            String xLabel,
            Range xRange,
            Range yRange)
            throws IOException {
        List<XY_DataSet> funcs = new ArrayList<>();
        List<PlotCurveCharacterstics> chars = new ArrayList<>();
        addCurve(funcs, chars, curve, curveName, color);
        CurvePlots.addReturnPeriodLines(funcs, chars, xRange);

        PlotSpec spec =
                new PlotSpec(funcs, chars, title, xLabel, "Annual Probability of Exceedance");
        spec.setLegendVisible(true);

        HeadlessGraphPanel gp = PlotUtils.initScreenHeadless();
        gp.drawGraphPanel(spec, true, true, xRange, yRange);
        PlotUtils.writePlots(imageDir, prefix, gp, 700, 650, true, false, false);
        return new File(imageDir, prefix + ".png");
    }

    /**
     * The difference view of two curves: both curves on top of each other, and below them the ratio
     * of the second to the first, sharing the x axis.
     */
    protected File plotCurveComparison(
            String prefix,
            String title,
            DiscretizedFunc firstCurve,
            DiscretizedFunc secondCurve,
            String xLabel,
            Range xRange,
            Range yRange)
            throws IOException {
        List<XY_DataSet> funcs = new ArrayList<>();
        List<PlotCurveCharacterstics> chars = new ArrayList<>();
        addCurve(funcs, chars, firstCurve, first.getName(), FIRST_COLOR);
        addCurve(funcs, chars, secondCurve, second.getName(), SECOND_COLOR);
        CurvePlots.addReturnPeriodLines(funcs, chars, xRange);
        PlotSpec curves =
                new PlotSpec(funcs, chars, title, xLabel, "Annual Probability of Exceedance");
        curves.setLegendVisible(true);

        DiscretizedFunc ratio = ratio(firstCurve, secondCurve);
        HeadlessGraphPanel gp = PlotUtils.initScreenHeadless();
        if (ratio.size() < 2) {
            // nothing worth comparing at this site, so just show the two curves
            gp.drawGraphPanel(curves, true, true, xRange, yRange);
        } else {
            List<XY_DataSet> ratioFuncs = new ArrayList<>();
            List<PlotCurveCharacterstics> ratioChars = new ArrayList<>();
            ratio.setName(second.getName() + " / " + first.getName());
            ratioFuncs.add(ratio);
            ratioChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.BLACK));
            DefaultXY_DataSet unity = new DefaultXY_DataSet();
            unity.set(xRange.getLowerBound(), 1d);
            unity.set(xRange.getUpperBound(), 1d);
            unity.setName("no change");
            ratioFuncs.add(unity);
            ratioChars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 1f, Color.GRAY));

            PlotSpec ratioSpec = new PlotSpec(ratioFuncs, ratioChars, title, xLabel, "Ratio");
            ratioSpec.setLegendVisible(true);
            gp.drawGraphPanel(
                    List.of(curves, ratioSpec),
                    List.of(true),
                    List.of(true, false),
                    List.of(xRange),
                    List.of(yRange, ratioRange(ratio)));
            PlotUtils.setSubPlotWeights(gp, 3, 2);
        }
        PlotUtils.writePlots(imageDir, prefix, gp, 700, 800, true, false, false);
        return new File(imageDir, prefix + ".png");
    }

    protected static void addCurve(
            List<XY_DataSet> funcs,
            List<PlotCurveCharacterstics> chars,
            DiscretizedFunc curve,
            String name,
            Color color) {
        DiscretizedFunc named = curve.deepClone();
        named.setName(name);
        funcs.add(named);
        chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, color));
    }

    /**
     * The ratio of the second curve to the first, over the x values where both curves still carry
     * meaningful probabilities. See {@link #MIN_COMPARABLE_PROBABILITY}.
     */
    protected static DiscretizedFunc ratio(
            DiscretizedFunc firstCurve, DiscretizedFunc secondCurve) {
        DiscretizedFunc ratio = new ArbitrarilyDiscretizedFunc();
        for (int i = 0; i < firstCurve.size(); i++) {
            double x = firstCurve.getX(i);
            double a = firstCurve.getY(i);
            double b = secondCurve.getY(i);
            if (a >= MIN_COMPARABLE_PROBABILITY && b >= MIN_COMPARABLE_PROBABILITY) {
                ratio.set(x, b / a);
            }
        }
        return ratio;
    }

    protected static Range ratioRange(DiscretizedFunc ratio) {
        double min = ratio.getMinY();
        double max = ratio.getMaxY();
        // always show no change, and keep the range from collapsing when the curves agree
        min = Math.max(0d, Math.min(min, 0.9));
        max = Math.max(max, 1.1);
        double pad = 0.05 * (max - min);
        return new Range(Math.max(0d, min - pad), max + pad);
    }

    /** A y range that both curves fit into, so the two panels can be compared by eye. */
    protected static Range curveYRange(DiscretizedFunc... curves) {
        double min = Double.POSITIVE_INFINITY;
        double max = 0;
        for (DiscretizedFunc curve : curves) {
            for (int i = 0; i < curve.size(); i++) {
                double y = curve.getY(i);
                if (y > 0) {
                    min = Math.min(min, y);
                    max = Math.max(max, y);
                }
            }
        }
        return new Range(
                Math.max(1e-8, Double.isFinite(min) ? min : 1e-8), Math.max(1e-7, max * 1.2));
    }

    /**
     * A colour ramp covering all the given maps, so that they are directly comparable. The ramp is
     * logarithmic, rounded away from zero to whole decades.
     *
     * <p>The CPT's own values are the logarithms — that is what it is rescaled onto — but {@link
     * CPT#setLog10} tells it that, so it takes the linear ground motions handed to it, logs them
     * itself, and OpenSHA labels the colour bar in g rather than in log10 g. See {@link
     * SiteSourceMapPlotter#logCPT}, which does the same thing.
     */
    protected static CPT sharedLogCPT(GriddedGeoDataSet... maps) throws IOException {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (GriddedGeoDataSet map : maps) {
            for (int i = 0; i < map.size(); i++) {
                double value = map.get(i);
                if (value > 0) {
                    min = Math.min(min, Math.log10(value));
                    max = Math.max(max, Math.log10(value));
                }
            }
        }
        if (!Double.isFinite(min) || !Double.isFinite(max)) {
            min = -3d;
            max = 1d;
        }
        min = Math.floor(min);
        max = Math.ceil(max);
        if (max <= min) {
            max = min + 1d;
        }
        CPT cpt = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(min, max);
        cpt.setLog10(true);
        cpt.setNanColor(Color.LIGHT_GRAY);
        // a site with no hazard at all is off the bottom of any log scale; clamp it to the end of
        // the ramp rather than leaving it uncoloured
        cpt.setBelowMinColor(cpt.getMinColor());
        cpt.setAboveMaxColor(cpt.getMaxColor());
        return cpt;
    }

    /**
     * A diverging colour ramp for a percentage change map, running from the largest decrease to the
     * largest increase on the map with no change on the palette's neutral colour.
     *
     * <p>The ends are the map's own extremes, not rounded, so the ramp covers exactly what the map
     * holds and the caption's min and max are the ends of the colour bar. Nothing is clipped, so
     * the below-min and above-max colours are never reached by real data; they are set anyway so
     * that rounding at the ends cannot leave a node uncoloured.
     *
     * <p>Colour is spent on the logarithm of the change, out from zero in both directions, so that
     * one node that moved by a couple of hundred percent does not push every ordinary change into
     * the first sliver of the palette and leave the map looking flat. The ramp bottoms out at
     * {@link #NO_CHANGE_PERCENT}: smaller changes count as none and are drawn in {@code
     * noChangeColor}, which says which cells did not really move rather than leaving them to fade
     * into the palette. That floor is an absolute percentage, so a cell is the no-change colour on
     * the same terms on every map in the report.
     *
     * <p>The two sides are coloured at the same rate, so a decrease and an increase of the same
     * size look equally strong and only the larger side reaches full saturation. A map where nearly
     * everything moved one way still uses the ramp's whole width, because the range itself stays
     * asymmetric; what it does not do is make the smaller side look bigger than it is.
     *
     * <p>Unrounded ends mean the colour bar's tick labels land on round numbers inside the range
     * rather than on the ends themselves. Zero is always one of them: the ticks are multiples of
     * the tick interval and the range straddles zero. The bar's axis stays linear in percent, so
     * the no-change band is drawn at its true width and the log spacing shows up as colour changing
     * fastest either side of it.
     *
     * <p>Nodes where the first config has no hazard are NaN, have no percentage change to show, and
     * are drawn in grey. See {@link #percentDiff}.
     *
     * @param percentMap the percentage change map the ramp is fitted to
     * @param noChangeColor the colour of the no-change band, or null for the palette's own neutral
     *     colour. See {@link #setNoChangeColor}.
     */
    protected static CPT percentDiffCPT(GriddedGeoDataSet percentMap, Color noChangeColor)
            throws IOException {
        double[] values = finiteValues(percentMap);
        // finiteValues sorts, so the extremes are the ends; clamped so that a map that moved only
        // one way still has zero at the end of the ramp rather than inside it
        double min = values.length == 0 ? 0d : Math.min(0d, values[0]);
        double max = values.length == 0 ? 0d : Math.max(0d, values[values.length - 1]);
        if (min == 0d && max == 0d) {
            // the two configs agree everywhere, so give the ramp somewhere to be
            max = EMPTY_DIFF_SCALE;
        }

        CPT cpt =
                DivergingCPT.ramp(GMT_CPT_Files.DIVERGING_VIK_UNIFORM.instance(), min, max)
                        .logFloor(NO_CHANGE_PERCENT)
                        .zeroColor(noChangeColor)
                        .build();
        cpt.setNanColor(Color.LIGHT_GRAY);
        cpt.setBelowMinColor(cpt.getMinColor());
        cpt.setAboveMaxColor(cpt.getMaxColor());
        return cpt;
    }

    /**
     * The percentage change from the first map to the second, which is what the difference map
     * draws and what its caption reports. Nodes where the first map has no hazard are left as NaN:
     * there is no meaningful percentage to report there.
     */
    protected static GriddedGeoDataSet percentDiff(
            GriddedGeoDataSet firstMap, GriddedGeoDataSet secondMap) {
        Preconditions.checkArgument(
                firstMap.size() == secondMap.size(), "maps must cover the same region");
        GriddedGeoDataSet diff =
                new GriddedGeoDataSet(firstMap.getRegion(), firstMap.isLatitudeX());
        for (int i = 0; i < firstMap.size(); i++) {
            double a = firstMap.get(i);
            double b = secondMap.get(i);
            diff.set(i, a > 0 ? 100d * (b - a) / a : Double.NaN);
        }
        return diff;
    }

    /** Min, median and max ground motion of a map, for the figure caption. */
    protected static String mapStats(GriddedGeoDataSet map, String units) {
        double[] values = finiteValues(map);
        if (values.length == 0) {
            return "no hazard in this region";
        }
        return "min "
                + format(values[0], units)
                + ", median "
                + format(values[values.length / 2], units)
                + ", max "
                + format(values[values.length - 1], units);
    }

    /** Min, median and max percentage change, for the figure caption. */
    protected static String diffStats(GriddedGeoDataSet diff) {
        double[] values = finiteValues(diff);
        if (values.length == 0) {
            return "nothing to compare";
        }
        return "min "
                + percent(values[0])
                + ", median "
                + percent(values[values.length / 2])
                + ", max "
                + percent(values[values.length - 1]);
    }

    /** The change in exceedance probability at the return periods that the maps are built for. */
    protected static String curveStats(DiscretizedFunc firstCurve, DiscretizedFunc secondCurve) {
        List<String> parts = new ArrayList<>();
        for (ReturnPeriods rp : SolHazardMapCalc.MAP_RPS) {
            double a = imlAt(firstCurve, rp.oneYearProb);
            double b = imlAt(secondCurve, rp.oneYearProb);
            parts.add(
                    rp.label
                            + ": "
                            + (a > 0 ? percent(100d * (b - a) / a) : "n/a")
                            + " ("
                            + (float) a
                            + " to "
                            + (float) b
                            + ")");
        }
        return String.join(", ", parts);
    }

    /**
     * The ground motion a curve reaches at a given annual probability of exceedance.
     *
     * <p>Both ends are clipped to the extent of the curve rather than extrapolated, so a return
     * period the curve never reaches is reported as a bound, not as the true value:
     *
     * <ul>
     *   <li>a probability above the whole curve, i.e. a site too quiet to reach the return period
     *       at all, gives 0. {@link #curveStats} reports "n/a" for those rather than a change
     *       against zero.
     *   <li>a probability below the whole curve, i.e. a site whose hazard runs off the top of the
     *       IML grid, gives the curve's largest x. The true ground motion is higher, so a change
     *       computed from it understates the difference between the two configs. Reaching this end
     *       means the curve's IML grid is too short for the site, not that the two agree.
     * </ul>
     */
    protected static double imlAt(DiscretizedFunc curve, double probability) {
        if (probability > curve.getMaxY()) {
            return 0d;
        }
        if (probability < curve.getMinY()) {
            return curve.getMaxX();
        }
        return curve.getFirstInterpolatedX_inLogXLogYDomain(probability);
    }

    protected static double[] finiteValues(GriddedGeoDataSet map) {
        double[] values = new double[map.size()];
        int count = 0;
        for (int i = 0; i < map.size(); i++) {
            if (Double.isFinite(map.get(i))) {
                values[count++] = map.get(i);
            }
        }
        values = Arrays.copyOf(values, count);
        Arrays.sort(values);
        return values;
    }

    protected static String format(double value, String units) {
        return (float) value + " " + units;
    }

    protected static String percent(double value) {
        return (value > 0 ? "+" : "") + Math.round(value) + "%";
    }

    protected String differenceLabel() {
        return second.getName() + " vs " + first.getName();
    }

    // ---------------------------------------------------------------- report

    /** The summary table at the top of the report, one column per config. */
    protected ReportPage.Table summary(
            JointHazardInput.ValidationResult firstValidation,
            JointHazardInput.ValidationResult secondValidation) {
        GriddedRegion region = first.getInput().getRegion();
        return new ReportPage.Table("", first.getName(), second.getName())
                .addRow(
                        "Ground motion models",
                        first.getInput().getGmmMode().toString(),
                        second.getInput().getGmmMode().toString())
                .addRow(
                        "Fault sections",
                        String.valueOf(sectionCount(first)),
                        String.valueOf(sectionCount(second)))
                .addRow(
                        "Ruptures",
                        String.valueOf(ruptureCount(first)),
                        String.valueOf(ruptureCount(second)))
                .addRow(
                        "Crustal / interface / joint ruptures",
                        ruptureMix(firstValidation),
                        ruptureMix(secondValidation))
                .addRow("Minimum rupture rate", rateCutoff(first), rateCutoff(second))
                .addRow(
                        "Region",
                        region.getNodeCount()
                                + " sites at "
                                + (float) region.getSpacing()
                                + " degrees")
                .addRow("Periods", periodLabels());
    }

    /**
     * The rupture rate cutoff a config was calculated with. See {@link JointSolutions#filterRates}.
     */
    protected static String rateCutoff(HazardReportSource config) {
        double rate = config.getInput().getMinRuptureRate();
        return rate > 0 ? (float) rate + " /yr" : "none";
    }

    protected static String ruptureMix(JointHazardInput.ValidationResult validation) {
        return validation.numCrustal
                + " / "
                + validation.numInterface
                + " / "
                + validation.numJoint;
    }

    protected static int sectionCount(HazardReportSource config) {
        return config.getSolution().getRupSet().getNumSections();
    }

    protected static int ruptureCount(HazardReportSource config) {
        FaultSystemRupSet rupSet = config.getSolution().getRupSet();
        return rupSet.getNumRuptures();
    }

    protected String periodLabels() {
        List<String> labels = new ArrayList<>();
        for (double period : first.getInput().getPeriods()) {
            labels.add(HazardLabels.periodLabel(period));
        }
        return String.join(", ", labels);
    }

    protected String title() {
        return "Hazard comparison: " + first.getName() + " vs " + second.getName();
    }
}

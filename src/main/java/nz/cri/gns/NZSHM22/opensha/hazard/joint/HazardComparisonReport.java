package nz.cri.gns.NZSHM22.opensha.hazard.joint;

import com.google.common.base.Preconditions;
import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
 * <p>Map differences are drawn as the ratio of the second config to the first on a logarithmic
 * scale, so red means the second config gives stronger shaking, and the scale always covers the
 * whole range of change rather than clipping the extremes. See {@link #ratioCPT}. Figure captions
 * report the same thing as a percentage change, {@code 100 * (second - first) / first}, which reads
 * more naturally in prose. Clicking any figure opens it full size.
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
            List.of("Gisborne", "Taupo", "Wellington", "Kaikoura", "Franz Josef", "Christchurch");

    /** Return period that the source maps disaggregate at. */
    public static final ReturnPeriods SOURCE_RETURN_PERIOD = ReturnPeriods.TEN_IN_50;

    /** Directory that images are written to, relative to the report. */
    public static final String IMAGE_DIR = "images";

    public static final String INDEX_FILE = "index.html";

    /**
     * Ratios that the difference colour ramp is scaled to, i.e. 1.1 means a scale running from a
     * tenth less to a tenth more. The smallest one that covers the whole map is used, so that a map
     * of small differences does not come out flat. See {@link #ratioCPT}.
     */
    protected static final double[] RATIO_SCALES = {
        1.1, 1.25, 1.5, 2d, 3d, 5d, 10d, 30d, 100d, 300d, 1000d
    };

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

        imageDir = new File(outputDir, IMAGE_DIR);
        Preconditions.checkState(
                imageDir.exists() || imageDir.mkdirs(),
                "Could not create output directory %s",
                imageDir.getAbsolutePath());

        JointHazardMapCalculator firstCalc = calculate(first);
        JointHazardMapCalculator secondCalc = calculate(second);

        List<Section> sections = new ArrayList<>();
        sections.add(mapSection(firstCalc, secondCalc, periods));
        Section sourceSection = sourceSection(firstCalc, secondCalc, periods[0]);
        if (sourceSection != null) {
            sections.add(sourceSection);
        }
        sections.add(curveSection(firstCalc, secondCalc, periods));

        File index = new File(outputDir, INDEX_FILE);
        writeHtml(index, sections, firstValidation, secondValidation);
        System.out.println("Wrote hazard comparison report to " + index.getAbsolutePath());
        return index;
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
    protected Section mapSection(
            JointHazardMapCalculator firstCalc,
            JointHazardMapCalculator secondCalc,
            double[] periods)
            throws IOException {
        Section section = new Section("Hazard maps", "maps");
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
                String zLabel = "Log10 " + periodLabel + " (" + units + "), " + rp.label;

                CPT cpt = sharedLogCPT(firstMap, secondMap);
                Row row = new Row(periodLabel + ", " + rp.label);
                row.add(
                        firstCalc
                                .getCalc()
                                .plotMap(
                                        imageDir,
                                        prefix + "_" + first.getId(),
                                        log10(firstMap),
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
                                        log10(secondMap),
                                        cpt,
                                        second.getName(),
                                        zLabel),
                        second.getName(),
                        mapStats(secondMap, units));

                // the map is a ratio on a log scale, which covers the whole range of changes;
                // the caption reports the same thing as percentages, which read more naturally
                GriddedGeoDataSet ratioMap = ratioMap(firstMap, secondMap);
                row.add(
                        firstCalc
                                .getCalc()
                                .plotMap(
                                        imageDir,
                                        prefix + "_diff",
                                        ratioMap,
                                        ratioCPT(ratioMap),
                                        differenceLabel(),
                                        "Ratio, " + periodLabel + ", " + rp.label),
                        "Difference",
                        diffStats(percentDiff(firstMap, secondMap)));
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
    protected Section sourceSection(
            JointHazardMapCalculator firstCalc, JointHazardMapCalculator secondCalc, double period)
            throws IOException {
        Section section = new Section("Hazard sources", "sources");
        SiteSourcePage pages =
                new SiteSourcePage(
                        new SiteSourceExplorer(firstCalc.getSetup()),
                        new SiteSourceExplorer(secondCalc.getSetup()),
                        first.getName(),
                        second.getName());

        Row row =
                new Row(
                        HazardLabels.SECTION_HAZARD
                                + ", "
                                + HazardLabels.periodLabel(period)
                                + " at "
                                + SOURCE_RETURN_PERIOD.label
                                + ". Each fault section is coloured by how much the hazard reaching"
                                + " the site through it changed: the annual rate at which ruptures"
                                + " running over that section push the site over the level. A"
                                + " rupture is credited to every section it breaks, so a long"
                                + " multi-fault rupture is drawn along its whole length. Click a"
                                + " map for that site's own page.");
        List<String> skipped = new ArrayList<>();
        for (Map.Entry<String, Location> site : sourceSites.entrySet()) {
            System.out.println("Mapping hazard sources at " + site.getKey());
            try {
                SiteSourcePage.Result result =
                        pages.write(
                                outputDir,
                                site.getKey(),
                                site.getValue(),
                                period,
                                SOURCE_RETURN_PERIOD);
                row.add(result.mapPath, site.getKey(), result.stats, result.pagePath);
            } catch (IllegalStateException e) {
                // a site whose fault hazard never reaches the return period has no level to
                // disaggregate at; report it and carry on rather than losing the whole report
                System.out.println("  skipped: " + e.getMessage());
                skipped.add(site.getKey());
            }
        }
        if (row.figures.isEmpty()) {
            return null;
        }
        if (!skipped.isEmpty()) {
            row.title = row.title + " No fault hazard to disaggregate at " + join(skipped) + ".";
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
    protected Section curveSection(
            JointHazardMapCalculator firstCalc,
            JointHazardMapCalculator secondCalc,
            double[] periods)
            throws IOException {
        Section section = new Section("Hazard curves", "curves");
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

                Row row = new Row(siteName + ", " + periodLabel);
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
     * A colour ramp covering all the given maps, so that they are directly comparable. Values are
     * log10 ground motions; the ramp is rounded outwards to whole decades.
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
        cpt.setNanColor(Color.LIGHT_GRAY);
        return cpt;
    }

    /**
     * A diverging colour ramp for a ratio map, logarithmic and symmetric about one, so that halving
     * and doubling are the same distance from the centre and no change sits on the neutral colour.
     *
     * <p>The scale always covers the whole map, each side rounded out on its own to the smallest of
     * {@link #RATIO_SCALES} that contains it. Percentage change is a poor thing to put a linear
     * scale on — it is bounded below by -100% and unbounded above, so a map with a tenfold increase
     * somewhere either saturates or squashes every decrease into a sliver of the ramp. On a log
     * ratio scale halving and doubling are the same distance from the centre and nothing has to be
     * clipped. See {@link #divergingRatioCPT} for why the two sides are scaled separately.
     *
     * <p>The values plotted are the ratios themselves rather than their logarithms, because {@link
     * CPT#setLog10} makes the palette do the logarithm and OpenSHA then labels the colour bar in
     * ratios. Ratios of zero, where the second model has no hazard at all, fall off the bottom and
     * take the ramp's end colour.
     */
    protected static CPT ratioCPT(GriddedGeoDataSet ratioMap) throws IOException {
        double smallest = 1d;
        double largest = 1d;
        for (int i = 0; i < ratioMap.size(); i++) {
            double ratio = ratioMap.get(i);
            if (Double.isFinite(ratio) && ratio > 0) {
                smallest = Math.min(smallest, ratio);
                largest = Math.max(largest, ratio);
            }
        }
        // each side is rounded outwards on its own, so the ramp is used across its whole width even
        // when every node moved the same way; a side with nothing on it gets no width at all
        double down = smallest < 1d ? ratioScale(1d / smallest) : 1d;
        double up = largest > 1d ? ratioScale(largest) : 1d;
        if (down == 1d && up == 1d) {
            // the two models agree everywhere, so give the ramp somewhere to be
            up = RATIO_SCALES[0];
        }

        CPT cpt = divergingRatioCPT(-Math.log10(down), Math.log10(up));
        cpt.setLog10(true);
        cpt.setNanColor(Color.LIGHT_GRAY);
        // a node where the second model has no hazard has a ratio of zero, which is off the bottom
        // of any log scale; clamp it to the end of the ramp rather than leaving it uncoloured
        cpt.setBelowMinColor(cpt.getMinColor());
        cpt.setAboveMaxColor(cpt.getMaxColor());
        return cpt;
    }

    /**
     * A diverging ramp over log ratios from {@code logMin} to {@code logMax}, with the palette's
     * neutral colour pinned to no change however lopsided those bounds are. See {@link
     * DivergingCPT}.
     *
     * @param logMin log10 of the smallest ratio on the map, at most zero
     * @param logMax log10 of the largest ratio on the map, at least zero
     */
    protected static CPT divergingRatioCPT(double logMin, double logMax) throws IOException {
        return DivergingCPT.centredOnZero(
                GMT_CPT_Files.DIVERGING_VIK_UNIFORM.instance(), logMin, logMax);
    }

    /**
     * The smallest of {@link #RATIO_SCALES} that covers the given extent, or the next power of ten
     * if none of them does. Never returns less than the extent, so a map is never clipped.
     */
    protected static double ratioScale(double extent) {
        for (double candidate : RATIO_SCALES) {
            if (extent <= candidate) {
                return candidate;
            }
        }
        return Math.pow(10, Math.ceil(Math.log10(extent)));
    }

    protected static GriddedGeoDataSet log10(GriddedGeoDataSet map) {
        GriddedGeoDataSet log = map.copy();
        log.log10();
        return log;
    }

    /**
     * The ratio of the second map to the first, which is what the difference map draws. Nodes where
     * the first map has no hazard are left as NaN: there is nothing to take a ratio against.
     */
    protected static GriddedGeoDataSet ratioMap(
            GriddedGeoDataSet firstMap, GriddedGeoDataSet secondMap) {
        Preconditions.checkArgument(
                firstMap.size() == secondMap.size(), "maps must cover the same region");
        GriddedGeoDataSet ratio =
                new GriddedGeoDataSet(firstMap.getRegion(), firstMap.isLatitudeX());
        for (int i = 0; i < firstMap.size(); i++) {
            double a = firstMap.get(i);
            ratio.set(i, a > 0 ? secondMap.get(i) / a : Double.NaN);
        }
        return ratio;
    }

    /**
     * The percentage change from the first map to the second, which the difference map is captioned
     * with. Nodes where the first map has no hazard are left as NaN: there is no meaningful
     * percentage to report there.
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

    // ---------------------------------------------------------------- HTML

    /**
     * A figure in the report: an image, its caption, an optional line of statistics and an optional
     * page it links to. A figure with no link opens full size in place instead.
     */
    protected static class Figure {
        protected final String path;
        protected final String caption;
        protected final String stats;
        protected final String link;

        protected Figure(String path, String caption, String stats, String link) {
            this.path = path;
            this.caption = caption;
            this.stats = stats;
            this.link = link;
        }
    }

    /** A row of figures shown side by side, e.g. the two maps and their difference. */
    protected static class Row {
        protected String title;
        protected final List<Figure> figures = new ArrayList<>();

        protected Row(String title) {
            this.title = title;
        }

        /** An image in the report's own image directory, which opens full size when clicked. */
        protected void add(File image, String caption, String stats) {
            figures.add(new Figure(IMAGE_DIR + "/" + image.getName(), caption, stats, null));
        }

        /**
         * An image anywhere below the report directory, which opens another page when clicked.
         *
         * @param path the image, relative to the report directory
         * @param link the page the figure links to, relative to the report directory
         */
        protected void add(String path, String caption, String stats, String link) {
            figures.add(new Figure(path, caption, stats, link));
        }
    }

    /** A section of the report, e.g. all the maps. */
    protected static class Section {
        protected final String title;
        protected final String id;
        protected final List<Row> rows = new ArrayList<>();

        protected Section(String title, String id) {
            this.title = title;
            this.id = id;
        }

        protected void add(Row row) {
            rows.add(row);
        }
    }

    protected void writeHtml(
            File index,
            List<Section> sections,
            JointHazardInput.ValidationResult firstValidation,
            JointHazardInput.ValidationResult secondValidation)
            throws IOException {
        try (Writer out = Files.newBufferedWriter(index.toPath(), StandardCharsets.UTF_8)) {
            out.write("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
            out.write("<meta charset=\"utf-8\">\n");
            out.write("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n");
            out.write("<title>" + escape(title()) + "</title>\n");
            out.write("<style>\n" + css() + "</style>\n");
            out.write("</head>\n<body>\n");

            out.write("<h1>" + escape(title()) + "</h1>\n");
            out.write(
                    "<p class=\"meta\">Generated "
                            + escape(
                                    LocalDateTime.now()
                                            .format(
                                                    DateTimeFormatter.ofPattern(
                                                            "yyyy-MM-dd HH:mm")))
                            + ". Map differences are the ratio of "
                            + escape(second.getName())
                            + " to "
                            + escape(first.getName())
                            + " on a logarithmic scale that always covers the whole range of"
                            + " change, so red means "
                            + escape(second.getName())
                            + " gives stronger shaking. Captions report the same change as a"
                            + " percentage.</p>\n");

            writeSummary(out, firstValidation, secondValidation);

            out.write("<nav><ul>\n");
            for (Section section : sections) {
                out.write(
                        "<li><a href=\"#"
                                + section.id
                                + "\">"
                                + escape(section.title)
                                + "</a></li>\n");
            }
            out.write("</ul></nav>\n");

            for (Section section : sections) {
                out.write("<section id=\"" + section.id + "\">\n");
                out.write("<h2>" + escape(section.title) + "</h2>\n");
                for (Row row : section.rows) {
                    out.write("<h3>" + escape(row.title) + "</h3>\n");
                    out.write("<div class=\"figures\">\n");
                    for (Figure figure : row.figures) {
                        out.write("<figure>\n");
                        // a figure with no link opens full size in the lightbox instead,
                        // which the script hooks up by the zoom class
                        out.write(
                                "<a "
                                        + (figure.link == null
                                                ? "class=\"zoom\" href=\"" + figure.path
                                                : "href=\"" + figure.link)
                                        + "\"><img src=\""
                                        + figure.path
                                        + "\" alt=\""
                                        + escape(figure.caption)
                                        + "\"></a>\n");
                        out.write("<figcaption>" + escape(figure.caption));
                        if (figure.stats != null) {
                            out.write("<span class=\"stats\">" + escape(figure.stats) + "</span>");
                        }
                        out.write("</figcaption>\n</figure>\n");
                    }
                    out.write("</div>\n");
                }
                out.write("</section>\n");
            }

            out.write("<div id=\"lightbox\"><img id=\"lightbox-image\" alt=\"\"></div>\n");
            out.write("<script>\n" + script() + "</script>\n");
            out.write("</body>\n</html>\n");
        }
    }

    protected void writeSummary(
            Writer out,
            JointHazardInput.ValidationResult firstValidation,
            JointHazardInput.ValidationResult secondValidation)
            throws IOException {
        GriddedRegion region = first.getInput().getRegion();
        out.write("<table class=\"summary\">\n<tr><th></th><th>");
        out.write(
                escape(first.getName()) + "</th><th>" + escape(second.getName()) + "</th></tr>\n");
        summaryRow(
                out,
                "Ground motion models",
                first.getInput().getGmmMode().toString(),
                second.getInput().getGmmMode().toString());
        summaryRow(
                out,
                "Fault sections",
                String.valueOf(sectionCount(first)),
                String.valueOf(sectionCount(second)));
        summaryRow(
                out,
                "Ruptures",
                String.valueOf(ruptureCount(first)),
                String.valueOf(ruptureCount(second)));
        summaryRow(
                out,
                "Crustal / interface / joint ruptures",
                ruptureMix(firstValidation),
                ruptureMix(secondValidation));
        out.write(
                "<tr><th>Region</th><td colspan=\"2\">"
                        + region.getNodeCount()
                        + " sites at "
                        + (float) region.getSpacing()
                        + " degrees</td></tr>\n");
        out.write(
                "<tr><th>Periods</th><td colspan=\"2\">" + escape(periodLabels()) + "</td></tr>\n");
        out.write("</table>\n");
    }

    protected static void summaryRow(
            Writer out, String label, String firstValue, String secondValue) throws IOException {
        out.write(
                "<tr><th>"
                        + escape(label)
                        + "</th><td>"
                        + escape(firstValue)
                        + "</td><td>"
                        + escape(secondValue)
                        + "</td></tr>\n");
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

    protected static String escape(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    protected static String css() {
        return "body { font-family: system-ui, Arial, sans-serif; margin: 0 auto; padding: 1.5rem;"
                + " max-width: 1600px; color: #222; }\n"
                + "h1 { font-size: 1.6rem; } h2 { font-size: 1.3rem; margin-top: 2.5rem;"
                + " border-bottom: 1px solid #ddd; padding-bottom: .3rem; }\n"
                + "h3 { font-size: 1.05rem; margin: 1.5rem 0 .5rem; color: #444; }\n"
                + ".meta { color: #666; }\n"
                + "table.summary { border-collapse: collapse; margin: 1rem 0; }\n"
                + "table.summary th, table.summary td { border: 1px solid #ddd; padding: .35rem"
                + " .7rem; text-align: left; font-weight: normal; }\n"
                + "table.summary tr:first-child th { font-weight: bold; background: #f4f4f4; }\n"
                + "table.summary th:first-child { font-weight: bold; }\n"
                + "nav ul { list-style: none; padding: 0; display: flex; gap: 1rem; }\n"
                + ".figures { display: flex; flex-wrap: wrap; gap: 1rem; }\n"
                + "figure { flex: 1 1 30%; min-width: 280px; margin: 0; }\n"
                + "figure img { width: 100%; height: auto; border: 1px solid #ddd; cursor:"
                + " zoom-in; }\n"
                + "figcaption { font-size: .85rem; color: #444; padding-top: .3rem; }\n"
                + "figcaption .stats { display: block; color: #777; }\n"
                + "#lightbox { display: none; position: fixed; inset: 0; background: rgba(0, 0, 0,"
                + " .85); align-items: center; justify-content: center; cursor: zoom-out; z-index:"
                + " 10; }\n"
                + "#lightbox.open { display: flex; }\n"
                + "#lightbox img { max-width: 96vw; max-height: 96vh; }\n";
    }

    protected static String script() {
        return "var box = document.getElementById('lightbox');\n"
                + "var boxImage = document.getElementById('lightbox-image');\n"
                + "document.querySelectorAll('figure a.zoom').forEach(function (link) {\n"
                + "  link.addEventListener('click', function (event) {\n"
                + "    event.preventDefault();\n"
                + "    boxImage.src = link.getAttribute('href');\n"
                + "    box.classList.add('open');\n"
                + "  });\n"
                + "});\n"
                + "box.addEventListener('click', function () { box.classList.remove('open'); });\n"
                + "document.addEventListener('keydown', function (event) {\n"
                + "  if (event.key === 'Escape') { box.classList.remove('open'); }\n"
                + "});\n";
    }
}

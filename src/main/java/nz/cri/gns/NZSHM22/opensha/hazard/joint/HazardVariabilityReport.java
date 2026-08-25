package nz.cri.gns.NZSHM22.opensha.hazard.joint;

import com.google.common.base.Preconditions;
import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import org.jfree.data.Range;
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
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc.ReturnPeriods;

/**
 * Generates a standalone HTML report on how much the hazard varies across a set of {@link
 * HazardConfig hazard sources} that are meant to be equivalent, typically repeat runs of the same
 * inversion. Where {@link HazardComparisonReport} answers "how do these two models differ", this
 * one answers "how repeatable is this model".
 *
 * <p>Per period and return period the report holds:
 *
 * <ul>
 *   <li>the mean hazard map across the runs,
 *   <li>the coefficient of variation, i.e. {@code 100 * standardDeviation / mean}, the everyday
 *       spread, and
 *   <li>the max spread, i.e. {@code 100 * (max - min) / mean}, the worst disagreement between any
 *       two runs.
 * </ul>
 *
 * <p>Both variability maps are percentages of the mean, so they can be read directly as "runs of
 * this model agree on the hazard here to within x%". A node is only reported where every run has
 * non-zero hazard; elsewhere there is no meaningful relative spread and the node is left blank.
 *
 * <p>The report also holds a hazard curve plot per site and period with every run drawn on top of
 * the others, plus the spread of the ground motion at the map return periods.
 *
 * <p>All configs must be calculated over the same region and the same periods. Use {@link
 * #setRegion} or {@link #setSpacing} to set them together. Runs are calculated one after the other
 * and each solution is released once its maps and curves have been extracted, so the report's
 * memory use does not grow with the number of runs.
 */
public class HazardVariabilityReport {

    /** Directory that images are written to, relative to the report. See {@link ReportPage}. */
    public static final String IMAGE_DIR = ReportPage.IMAGE_DIR;

    public static final String INDEX_FILE = ReportPage.INDEX_FILE;

    /**
     * Percentages that the variability colour ramp is scaled to. The smallest one that covers the
     * bulk of a map is used, so that a map of small differences does not come out flat and a map of
     * large ones does not saturate. See {@link #variabilityCPT}.
     */
    protected static final double[] VARIABILITY_SCALES = {5d, 10d, 25d, 50d, 100d, 200d};

    /** Name of the solution zip inside each run directory. See {@link #jointRunsIn}. */
    public static final String SOLUTION_FILE = "solution.zip";

    protected final List<HazardConfig> configs;
    protected final File outputDir;

    protected Map<String, Location> sites = HazardComparisonReport.defaultSites();
    protected File imageDir;

    /**
     * Generates the report.
     *
     * @param configs the runs to compare, at least two
     * @param outputPath directory the report and its images are written to
     * @return the report's index.html
     */
    public static File generateReport(List<HazardConfig> configs, File outputPath)
            throws IOException {
        return new HazardVariabilityReport(configs, outputPath).generate();
    }

    public HazardVariabilityReport(List<HazardConfig> configs, File outputDir) {
        Preconditions.checkArgument(
                configs != null && configs.size() > 1,
                "need at least two runs to say anything about variability");
        this.configs = new ArrayList<>(configs);
        this.outputDir = Preconditions.checkNotNull(outputDir, "need an output directory");
    }

    /**
     * The joint inversion runs in a directory of run directories, i.e. every {@code
     * <runsDir>/<run>/solution.zip}, sorted by run name and named after it. That is the layout the
     * batch inversion runner writes. Each solution is calculated with the experimental joint GMM,
     * see {@link HazardConfig#joint}.
     *
     * @throws IllegalArgumentException if the directory holds no such run
     */
    public static List<HazardConfig> jointRunsIn(File runsDir) throws IOException {
        return jointRunsIn(runsDir, name -> true);
    }

    /**
     * As {@link #jointRunsIn(File)}, but only the runs whose name the filter accepts. Runs are
     * filtered before their solutions are loaded, so a directory holding other, unrelated solutions
     * does not cost anything.
     */
    public static List<HazardConfig> jointRunsIn(File runsDir, Predicate<String> runNameFilter)
            throws IOException {
        Preconditions.checkArgument(
                runsDir.isDirectory(), "%s is not a directory", runsDir.getAbsolutePath());
        File[] runs =
                runsDir.listFiles(
                        f ->
                                runNameFilter.test(f.getName())
                                        && new File(f, SOLUTION_FILE).isFile());
        Preconditions.checkArgument(
                runs != null && runs.length > 0,
                "No <run>/%s below %s",
                SOLUTION_FILE,
                runsDir.getAbsolutePath());
        Arrays.sort(runs);
        List<HazardConfig> configs = new ArrayList<>();
        for (File run : runs) {
            configs.add(HazardConfig.joint(run.getName(), new File(run, SOLUTION_FILE)));
        }
        return configs;
    }

    /** Sets the sites that curves are compared at. Defaults to the report's default sites. */
    public HazardVariabilityReport setSites(Map<String, Location> sites) {
        Preconditions.checkArgument(sites != null && !sites.isEmpty(), "need at least one site");
        this.sites = sites;
        return this;
    }

    /** Sets the map region of every run, so that their maps can be compared. */
    public HazardVariabilityReport setRegion(GriddedRegion region) {
        for (HazardConfig config : configs) {
            config.getInput().setRegion(region);
        }
        return this;
    }

    /** Sets the map resolution of every run in degrees. */
    public HazardVariabilityReport setSpacing(double spacing) {
        for (HazardConfig config : configs) {
            config.getInput().setSpacing(spacing);
        }
        return this;
    }

    /** Sets the periods of every run. 0 is PGA, positive values are SA periods. */
    public HazardVariabilityReport setPeriods(double... periods) {
        for (HazardConfig config : configs) {
            config.getInput().setPeriods(periods);
        }
        return this;
    }

    /** Sets the thread count of every run. Runs are calculated one after the other. */
    public HazardVariabilityReport setNumThreads(int numThreads) {
        for (HazardConfig config : configs) {
            config.getInput().setNumThreads(numThreads);
        }
        return this;
    }

    /**
     * Calculates every run, writes the plots and the report.
     *
     * @return the report's index.html
     * @throws IllegalArgumentException if the runs disagree on the region or the periods
     * @throws IllegalStateException if a solution fails {@link JointHazardInput#validate()}
     */
    public File generate() throws IOException {
        HazardConfig reference = configs.get(0);
        double[] periods = reference.getInput().getPeriods();
        for (HazardConfig config : configs) {
            Preconditions.checkArgument(
                    Arrays.equals(periods, config.getInput().getPeriods()),
                    "All runs must use the same periods, got %s and %s",
                    Arrays.toString(periods),
                    Arrays.toString(config.getInput().getPeriods()));
            Preconditions.checkArgument(
                    reference.getInput().getRegion().equalsRegion(config.getInput().getRegion()),
                    "All runs must be calculated over the same region, otherwise their maps cannot"
                            + " be compared. Use setRegion or setSpacing to set them together.");
        }

        List<JointHazardInput.ValidationResult> validations = new ArrayList<>();
        for (HazardConfig config : configs) {
            validations.add(config.getInput().validate());
        }

        ReportPage page = new ReportPage(title(), outputDir);
        page.setIntro(
                "Variability across "
                        + configs.size()
                        + " runs of the same model. Both variability maps are percentages of the"
                        + " mean hazard, so warmer means the runs agree less well there.");
        page.setSummary(summary(validations));
        imageDir = page.imageDir();

        Results results = calculate(periods);

        page.add(mapSection(results, periods));
        page.add(curveSection(results, periods));

        File index = page.write();
        System.out.println("Wrote hazard variability report to " + index.getAbsolutePath());
        return index;
    }

    /**
     * The maps and curves of every run. Runs are calculated one at a time and only their results
     * are kept, so that a set of large solutions does not all have to be in memory at once. The
     * first run's calculator is held on to because plotting a map goes through it.
     */
    protected Results calculate(double[] periods) {
        Results results = new Results();
        for (HazardConfig config : configs) {
            System.out.println(
                    "Calculating hazard for "
                            + config.getName()
                            + " at "
                            + config.getInput().getRegion().getNodeCount()
                            + " sites using "
                            + config.getInput().getGmmMode());
            JointHazardMapCalculator calculator = new JointHazardMapCalculator(config.getInput());
            calculator.calcHazardCurves();

            for (double period : periods) {
                for (ReturnPeriods rp : SolHazardMapCalc.MAP_RPS) {
                    results.maps
                            .computeIfAbsent(mapKey(period, rp), key -> new ArrayList<>())
                            .add(calculator.getCalc().buildMap(period, rp));
                }
                for (Map.Entry<String, Location> site : sites.entrySet()) {
                    results.curves
                            .computeIfAbsent(
                                    curveKey(site.getKey(), period), key -> new ArrayList<>())
                            .add(calculator.calcSiteCurve(site.getValue(), period));
                }
            }

            if (results.plotter == null) {
                results.plotter = calculator;
            }
        }
        return results;
    }

    /** The mean map and the two variability maps, per period and return period. */
    protected ReportPage.Section mapSection(Results results, double[] periods) throws IOException {
        ReportPage.Section section = new ReportPage.Section("Hazard maps", "maps");
        SolHazardMapCalc calc = results.plotter.getCalc();
        for (double period : periods) {
            for (ReturnPeriods rp : SolHazardMapCalc.MAP_RPS) {
                List<GriddedGeoDataSet> maps = results.maps.get(mapKey(period, rp));
                String periodLabel = HazardLabels.periodLabel(period);
                String units = HazardLabels.periodUnits(period);
                String prefix =
                        "map_"
                                + HazardLabels.periodPrefix(period)
                                + "_"
                                + rp.name().toLowerCase(Locale.ROOT);

                GriddedGeoDataSet mean = mean(maps);
                GriddedGeoDataSet cov = coefficientOfVariation(maps);
                GriddedGeoDataSet spread = spread(maps);

                ReportPage.Row row = new ReportPage.Row(periodLabel + ", " + rp.label);
                row.add(
                        calc.plotMap(
                                imageDir,
                                prefix + "_mean",
                                HazardComparisonReport.log10(mean),
                                HazardComparisonReport.sharedLogCPT(mean),
                                "Mean of " + configs.size() + " runs",
                                "Log10 " + periodLabel + " (" + units + "), " + rp.label),
                        "Mean hazard",
                        HazardComparisonReport.mapStats(mean, units));
                row.add(
                        calc.plotMap(
                                imageDir,
                                prefix + "_cov",
                                cov,
                                variabilityCPT(cov),
                                "Coefficient of variation",
                                "% of mean, " + periodLabel + ", " + rp.label),
                        "Coefficient of variation (standard deviation / mean)",
                        variabilityStats(cov));
                row.add(
                        calc.plotMap(
                                imageDir,
                                prefix + "_spread",
                                spread,
                                variabilityCPT(spread),
                                "Max spread",
                                "% of mean, " + periodLabel + ", " + rp.label),
                        "Max spread (highest run - lowest run)",
                        variabilityStats(spread));
                section.add(row);
            }
        }
        return section;
    }

    /** Every run's hazard curve on top of the others, per site and period. */
    protected ReportPage.Section curveSection(Results results, double[] periods)
            throws IOException {
        ReportPage.Section section = new ReportPage.Section("Hazard curves", "curves");
        for (String siteName : sites.keySet()) {
            for (double period : periods) {
                List<DiscretizedFunc> curves = results.curves.get(curveKey(siteName, period));
                String periodLabel = HazardLabels.periodLabel(period);
                String prefix =
                        "curve_"
                                + HazardLabels.slug(siteName)
                                + "_"
                                + HazardLabels.periodPrefix(period);

                ReportPage.Row row = new ReportPage.Row(siteName + ", " + periodLabel);
                row.add(
                        plotCurves(
                                prefix,
                                siteName + " - " + configs.size() + " runs",
                                curves,
                                periodLabel
                                        + " ("
                                        + HazardLabels.periodUnits(period)
                                        + ")"),
                        siteName + ", " + periodLabel,
                        curveStats(curves));
                section.add(row);
            }
        }
        return section;
    }

    /** All the runs' curves for one site, drawn on top of each other. */
    protected File plotCurves(
            String prefix, String title, List<DiscretizedFunc> curves, String xLabel)
            throws IOException {
        List<XY_DataSet> funcs = new ArrayList<>();
        List<PlotCurveCharacterstics> chars = new ArrayList<>();

        // the runs are meant to be equivalent, so none of them gets a heavier line than the others
        CPT runCPT = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(0d, curves.size() - 1d);
        for (int i = 0; i < curves.size(); i++) {
            DiscretizedFunc curve = curves.get(i).deepClone();
            curve.setName(configs.get(i).getName());
            funcs.add(curve);
            chars.add(
                    new PlotCurveCharacterstics(
                            PlotLineType.SOLID, 1.5f, runCPT.getColor((float) i)));
        }

        Range xRange = new Range(curves.get(0).getMinX(), curves.get(0).getMaxX());
        Range yRange = HazardComparisonReport.curveYRange(curves.toArray(new DiscretizedFunc[0]));
        CurvePlots.addReturnPeriodLines(funcs, chars, xRange);

        PlotSpec spec =
                new PlotSpec(funcs, chars, title, xLabel, "Annual Probability of Exceedance");
        spec.setLegendVisible(true);

        HeadlessGraphPanel gp = PlotUtils.initScreenHeadless();
        gp.drawGraphPanel(spec, true, true, xRange, yRange);
        PlotUtils.writePlots(imageDir, prefix, gp, 700, 650, true, false, false);
        return new File(imageDir, prefix + ".png");
    }

    // ---------------------------------------------------------------- statistics

    /**
     * The mean of the given maps, node by node. Nodes where a run has no hazard are left as NaN:
     * the runs cannot be compared in relative terms there. See {@link #values}.
     */
    protected static GriddedGeoDataSet mean(List<GriddedGeoDataSet> maps) {
        GriddedGeoDataSet mean = emptyLike(maps);
        for (int node = 0; node < mean.size(); node++) {
            double[] values = values(maps, node);
            mean.set(node, values == null ? Double.NaN : mean(values));
        }
        return mean;
    }

    /**
     * The standard deviation across the runs as a percentage of their mean, node by node. This is
     * the everyday spread: two thirds of the runs sit within this much of the mean.
     */
    protected static GriddedGeoDataSet coefficientOfVariation(List<GriddedGeoDataSet> maps) {
        GriddedGeoDataSet cov = emptyLike(maps);
        for (int node = 0; node < cov.size(); node++) {
            double[] values = values(maps, node);
            cov.set(
                    node,
                    values == null ? Double.NaN : 100d * standardDeviation(values) / mean(values));
        }
        return cov;
    }

    /**
     * The difference between the highest and the lowest run as a percentage of their mean, node by
     * node. This is the worst disagreement between any two runs.
     */
    protected static GriddedGeoDataSet spread(List<GriddedGeoDataSet> maps) {
        GriddedGeoDataSet spread = emptyLike(maps);
        for (int node = 0; node < spread.size(); node++) {
            double[] values = values(maps, node);
            if (values == null) {
                spread.set(node, Double.NaN);
                continue;
            }
            double min = Double.POSITIVE_INFINITY;
            double max = Double.NEGATIVE_INFINITY;
            for (double value : values) {
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
            spread.set(node, 100d * (max - min) / mean(values));
        }
        return spread;
    }

    /**
     * The value every run has at a node, or null if any run has no hazard there. Relative spread is
     * undefined once a run sits at zero, and those nodes are on the edge of the map where the
     * numbers are noise anyway.
     */
    protected static double[] values(List<GriddedGeoDataSet> maps, int node) {
        double[] values = new double[maps.size()];
        for (int i = 0; i < maps.size(); i++) {
            double value = maps.get(i).get(node);
            if (!(value > 0) || !Double.isFinite(value)) {
                return null;
            }
            values[i] = value;
        }
        return values;
    }

    protected static double mean(double[] values) {
        double sum = 0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.length;
    }

    /** The sample standard deviation, i.e. normalised by n-1: the runs are a sample of a model. */
    protected static double standardDeviation(double[] values) {
        if (values.length < 2) {
            return 0d;
        }
        double mean = mean(values);
        double sum = 0;
        for (double value : values) {
            sum += (value - mean) * (value - mean);
        }
        return Math.sqrt(sum / (values.length - 1));
    }

    protected static GriddedGeoDataSet emptyLike(List<GriddedGeoDataSet> maps) {
        Preconditions.checkArgument(!maps.isEmpty(), "need at least one map");
        GriddedGeoDataSet reference = maps.get(0);
        for (GriddedGeoDataSet map : maps) {
            Preconditions.checkArgument(
                    map.size() == reference.size(), "maps must cover the same region");
        }
        return new GriddedGeoDataSet(reference.getRegion(), reference.isLatitudeX());
    }

    /**
     * A sequential colour ramp for a variability map, running from no variability to the smallest
     * of {@link #VARIABILITY_SCALES} that covers all but the most extreme 2.5% of the map. Outliers
     * are allowed to saturate rather than flattening the rest of the map.
     */
    protected static CPT variabilityCPT(GriddedGeoDataSet map) throws IOException {
        double[] values = HazardComparisonReport.finiteValues(map);
        double extent =
                values.length == 0 ? 0 : values[(int) Math.ceil(0.975 * (values.length - 1))];
        double scale = VARIABILITY_SCALES[VARIABILITY_SCALES.length - 1];
        for (double candidate : VARIABILITY_SCALES) {
            if (extent <= candidate) {
                scale = candidate;
                break;
            }
        }
        CPT cpt = GMT_CPT_Files.SEQUENTIAL_LAJOLLA_UNIFORM.instance().rescale(0d, scale);
        cpt.setNanColor(Color.LIGHT_GRAY);
        return cpt;
    }

    /** Median and max variability of a map, for the figure caption. */
    protected static String variabilityStats(GriddedGeoDataSet map) {
        double[] values = HazardComparisonReport.finiteValues(map);
        if (values.length == 0) {
            return "nothing to compare";
        }
        return "median "
                + Math.round(values[values.length / 2])
                + "%, 95th percentile "
                + Math.round(values[(int) Math.ceil(0.95 * (values.length - 1))])
                + "%, max "
                + Math.round(values[values.length - 1])
                + "%";
    }

    /** The spread of the ground motion at the return periods the maps are built for. */
    protected static String curveStats(List<DiscretizedFunc> curves) {
        List<String> parts = new ArrayList<>();
        for (ReturnPeriods rp : SolHazardMapCalc.MAP_RPS) {
            double[] imls = new double[curves.size()];
            boolean comparable = true;
            for (int i = 0; i < curves.size(); i++) {
                imls[i] = HazardComparisonReport.imlAt(curves.get(i), rp.oneYearProb);
                comparable = comparable && imls[i] > 0;
            }
            if (!comparable) {
                parts.add(rp.label + ": n/a");
                continue;
            }
            double mean = mean(imls);
            double min = Arrays.stream(imls).min().getAsDouble();
            double max = Arrays.stream(imls).max().getAsDouble();
            parts.add(
                    rp.label
                            + ": "
                            + Math.round(100d * standardDeviation(imls) / mean)
                            + "% cov, "
                            + Math.round(100d * (max - min) / mean)
                            + "% spread ("
                            + (float) min
                            + " to "
                            + (float) max
                            + ")");
        }
        return String.join(", ", parts);
    }

    // ---------------------------------------------------------------- plumbing

    /** What every run contributed, keyed by map and by curve. */
    protected static class Results {
        protected final Map<String, List<GriddedGeoDataSet>> maps = new LinkedHashMap<>();
        protected final Map<String, List<DiscretizedFunc>> curves = new LinkedHashMap<>();

        /** The first run's calculator, kept because plotting a map goes through one. */
        protected JointHazardMapCalculator plotter;
    }

    protected static String mapKey(double period, ReturnPeriods rp) {
        return period + "_" + rp.name();
    }

    protected static String curveKey(String siteName, double period) {
        return siteName + "_" + period;
    }

    protected ReportPage.Table summary(List<JointHazardInput.ValidationResult> validations) {
        GriddedRegion region = configs.get(0).getInput().getRegion();
        ReportPage.Table table = new ReportPage.Table();
        List<String> names = new ArrayList<>();
        for (HazardConfig config : configs) {
            names.add(config.getName());
        }
        table.addRow("Runs", configs.size() + ": " + String.join(", ", names));
        table.addRow("Ground motion models", configs.get(0).getInput().getGmmMode().toString());
        table.addRow("Fault sections", range(configs, HazardComparisonReport::sectionCount));
        table.addRow("Ruptures", range(configs, HazardComparisonReport::ruptureCount));
        table.addRow("Crustal / interface / joint ruptures", ruptureMix(validations));
        table.addRow(
                "Region",
                region.getNodeCount() + " sites at " + (float) region.getSpacing() + " degrees");
        table.addRow("Periods", periodLabels());
        return table;
    }

    /** A count that is the same for every run, or its range if the runs disagree. */
    protected static String range(
            List<HazardConfig> configs, java.util.function.ToIntFunction<HazardConfig> count) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (HazardConfig config : configs) {
            int value = count.applyAsInt(config);
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        return min == max ? String.valueOf(min) : min + " to " + max;
    }

    protected static String ruptureMix(List<JointHazardInput.ValidationResult> validations) {
        List<String> mixes = new ArrayList<>();
        for (JointHazardInput.ValidationResult validation : validations) {
            String mix = HazardComparisonReport.ruptureMix(validation);
            if (!mixes.contains(mix)) {
                mixes.add(mix);
            }
        }
        return String.join("; ", mixes);
    }

    protected String periodLabels() {
        List<String> labels = new ArrayList<>();
        for (double period : configs.get(0).getInput().getPeriods()) {
            labels.add(HazardLabels.periodLabel(period));
        }
        return String.join(", ", labels);
    }

    protected String title() {
        return "Hazard variability across " + configs.size() + " runs";
    }
}

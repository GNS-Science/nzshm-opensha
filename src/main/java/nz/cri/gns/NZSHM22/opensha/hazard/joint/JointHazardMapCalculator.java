package nz.cri.gns.NZSHM22.opensha.hazard.joint;

import com.google.common.base.Preconditions;
import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jfree.data.Range;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.geo.Location;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotUtils;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.sha.calc.HazardCurveCalculator;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysHazardCalcSettings;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc.ReturnPeriods;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.attenRelImpl.JointRuptureExperimentalIMR;
import org.opensha.sha.util.TectonicRegionType;

/**
 * Calculates hazard maps and hazard curves for a crustal + subduction inversion solution, either
 * with {@link JointRuptureExperimentalIMR} for solutions containing joint ruptures, or with a
 * crustal and an interface GMM dispatched per source. See {@link JointHazardInput.GmmMode}.
 *
 * <p>The inputs and their validation live in {@link JointHazardInput}, the GMMs and the underlying
 * OpenSHA calculator in {@link JointHazardCalcSetup}. This class turns those into maps, curves,
 * plots and CSVs.
 */
public class JointHazardMapCalculator {

    private final JointHazardCalcSetup setup;

    public JointHazardMapCalculator(JointHazardCalcSetup setup) {
        this.setup = setup;
    }

    public JointHazardMapCalculator(JointHazardInput input) {
        this(new JointHazardCalcSetup(input));
    }

    public JointHazardMapCalculator(FaultSystemSolution solution) {
        this(new JointHazardInput(solution));
    }

    public JointHazardCalcSetup getSetup() {
        return setup;
    }

    public JointHazardInput getInput() {
        return setup.getInput();
    }

    /** The underlying OpenSHA map calculator. */
    public SolHazardMapCalc getCalc() {
        return setup.getCalc();
    }

    protected double[] getPeriods() {
        return getInput().getPeriods();
    }

    /** Calculates the hazard curve at every node of the region. Idempotent. */
    public JointHazardMapCalculator calcHazardCurves() {
        getCalc().calcHazardCurves(getInput().getNumThreads());
        return this;
    }

    /**
     * Writes a hazard map png for every period and return period, plus the underlying curves as
     * CSV. Calculates the curves first if that has not happened yet.
     *
     * @param outputDir directory that the maps are written to
     * @return the map files that were written
     */
    public List<File> writeMaps(File outputDir) throws IOException {
        Preconditions.checkState(
                outputDir.exists() || outputDir.mkdirs(),
                "Could not create output directory %s",
                outputDir.getAbsolutePath());
        calcHazardCurves();

        CPT logCPT = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(-3d, 1d);

        List<File> maps = new ArrayList<>();
        for (double period : getPeriods()) {
            String periodLabel = HazardLabels.periodLabel(period);
            String periodPrefix = HazardLabels.periodPrefix(period);
            for (ReturnPeriods rp : SolHazardMapCalc.MAP_RPS) {
                GriddedGeoDataSet xyz = getCalc().buildMap(period, rp);
                GriddedGeoDataSet logXYZ = xyz.copy();
                logXYZ.log10();

                String zLabel =
                        "Log10 "
                                + periodLabel
                                + " ("
                                + HazardLabels.periodUnits(period)
                                + "), "
                                + rp.label;
                maps.add(
                        getCalc()
                                .plotMap(
                                        outputDir,
                                        "hazard_map_"
                                                + periodPrefix
                                                + "_"
                                                + HazardLabels.slug(rp.name()),
                                        logXYZ,
                                        logCPT,
                                        " ",
                                        zLabel));
            }
        }
        getCalc().writeCurvesCSVs(outputDir, "hazard_curves", true);
        return maps;
    }

    /**
     * Calculates a hazard curve at a single site, using the same ERF and GMM as the maps. The
     * returned curve has linear x values (IML) and annual probabilities of exceedance as y values.
     */
    public DiscretizedFunc calcSiteCurve(Location location, double period) {
        EnumMap<TectonicRegionType, ScalarIMR> gmms = setup.buildGmmMap(period);
        Site site = setup.buildSite(location);

        DiscretizedFunc xVals = FaultSysHazardCalcSettings.getDefaultXVals(period);
        DiscretizedFunc logCurve = new ArbitrarilyDiscretizedFunc();
        for (Point2D pt : xVals) {
            logCurve.set(Math.log(pt.getX()), 0d);
        }

        HazardCurveCalculator curveCalc =
                new HazardCurveCalculator(FaultSysHazardCalcSettings.getDefaultSourceFilters());
        curveCalc.getHazardCurve(logCurve, site, gmms, getCalc().getERF());

        DiscretizedFunc curve = new ArbitrarilyDiscretizedFunc();
        for (int i = 0; i < xVals.size(); i++) {
            curve.set(xVals.getX(i), logCurve.getY(i));
        }
        return curve;
    }

    /**
     * Calculates and plots hazard curves for a set of named sites.
     *
     * @param outputDir directory that the plot and the CSV are written to
     * @param sites named sites, in the order they should appear in the legend
     * @param period the period to plot, 0 for PGA
     * @return the png that was written
     */
    public File writeSiteCurves(File outputDir, Map<String, Location> sites, double period)
            throws IOException {
        Preconditions.checkState(
                outputDir.exists() || outputDir.mkdirs(),
                "Could not create output directory %s",
                outputDir.getAbsolutePath());

        Map<String, DiscretizedFunc> curves = new LinkedHashMap<>();
        for (Map.Entry<String, Location> entry : sites.entrySet()) {
            curves.put(entry.getKey(), calcSiteCurve(entry.getValue(), period));
        }

        String prefix = "site_hazard_curves_" + HazardLabels.periodPrefix(period);
        writeSiteCurvesCSV(new File(outputDir, prefix + ".csv"), curves);
        return plotSiteCurves(
                outputDir,
                prefix,
                curves,
                HazardLabels.periodLabel(period) + " (" + HazardLabels.periodUnits(period) + ")");
    }

    static void writeSiteCurvesCSV(File outputFile, Map<String, DiscretizedFunc> curves)
            throws IOException {
        DiscretizedFunc reference = curves.values().iterator().next();
        CSVFile<String> csv = new CSVFile<>(true);
        List<String> header = new ArrayList<>();
        header.add("Site");
        for (int i = 0; i < reference.size(); i++) {
            header.add(String.valueOf((float) reference.getX(i)));
        }
        csv.addLine(header);
        for (Map.Entry<String, DiscretizedFunc> entry : curves.entrySet()) {
            List<String> line = new ArrayList<>();
            line.add(entry.getKey());
            for (Point2D pt : entry.getValue()) {
                line.add(String.valueOf(pt.getY()));
            }
            csv.addLine(line);
        }
        csv.writeToFile(outputFile);
    }

    static File plotSiteCurves(
            File outputDir, String prefix, Map<String, DiscretizedFunc> curves, String xAxisLabel)
            throws IOException {
        List<XY_DataSet> funcs = new ArrayList<>();
        List<PlotCurveCharacterstics> chars = new ArrayList<>();

        // there are a few dozen sites, so spread them over a colour ramp and alternate the line
        // type to keep neighbouring colours apart
        CPT siteCPT =
                curves.size() > 1
                        ? GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(0d, curves.size() - 1d)
                        : null;
        PlotLineType[] lineTypes = {PlotLineType.SOLID, PlotLineType.DOTTED_AND_DASHED};
        int curveIndex = 0;
        for (Map.Entry<String, DiscretizedFunc> entry : curves.entrySet()) {
            DiscretizedFunc curve = entry.getValue().deepClone();
            curve.setName(entry.getKey());
            funcs.add(curve);
            chars.add(
                    new PlotCurveCharacterstics(
                            lineTypes[curveIndex % lineTypes.length],
                            2f,
                            siteCPT == null ? Color.BLACK : siteCPT.getColor((float) curveIndex)));
            curveIndex++;
        }
        Range yRange = CurvePlots.yRange(curves.values());

        DiscretizedFunc reference = curves.values().iterator().next();
        CurvePlots.addReturnPeriodLines(
                funcs, chars, new Range(reference.getMinX(), reference.getMaxX()));

        PlotSpec spec =
                new PlotSpec(
                        funcs,
                        chars,
                        "Joint Rupture Hazard Curves",
                        xAxisLabel,
                        "Annual Probability of Exceedance");
        spec.setLegendVisible(true);

        HeadlessGraphPanel gp = PlotUtils.initScreenHeadless();
        gp.drawGraphPanel(spec, true, true, null, yRange);
        PlotUtils.writePlots(outputDir, prefix, gp, 900, 900, true, false, false);
        return new File(outputDir, prefix + ".png");
    }
}

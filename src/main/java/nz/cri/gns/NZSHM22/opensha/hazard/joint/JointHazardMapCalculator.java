package nz.cri.gns.NZSHM22.opensha.hazard.joint;

import com.google.common.base.Preconditions;
import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jfree.data.Range;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DefaultXY_DataSet;
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
import org.opensha.commons.param.Parameter;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.sha.calc.HazardCurveCalculator;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysHazardCalcSettings;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc.ReturnPeriods;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.attenRelImpl.JointRuptureExperimentalIMR;

/**
 * Calculates hazard maps and hazard curves for a joint (crustal + subduction) inversion solution,
 * using {@link JointRuptureExperimentalIMR}.
 *
 * <p>The inputs and their validation live in {@link JointHazardInput}, the GMM and the underlying
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

    private double[] getPeriods() {
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
            String periodLabel = periodLabel(period);
            String periodPrefix = periodLabel.toLowerCase().replaceAll(" ", "_");
            for (ReturnPeriods rp : SolHazardMapCalc.MAP_RPS) {
                GriddedGeoDataSet xyz = getCalc().buildMap(period, rp);
                GriddedGeoDataSet logXYZ = xyz.copy();
                logXYZ.log10();

                String zLabel =
                        "Log10 " + periodLabel + " (" + periodUnits(period) + "), " + rp.label;
                maps.add(
                        getCalc()
                                .plotMap(
                                        outputDir,
                                        "hazard_map_"
                                                + periodPrefix
                                                + "_"
                                                + rp.name().toLowerCase(),
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
        ScalarIMR gmm = JointHazardCalcSetup.buildGmm();
        FaultSysHazardCalcSettings.setIMforPeriod(gmm, period);

        Site site = new Site(location);
        for (Parameter<?> siteParam : gmm.getSiteParams()) {
            site.addParameter((Parameter<?>) siteParam.clone());
        }

        DiscretizedFunc xVals = FaultSysHazardCalcSettings.getDefaultXVals(period);
        DiscretizedFunc logCurve = new ArbitrarilyDiscretizedFunc();
        for (Point2D pt : xVals) {
            logCurve.set(Math.log(pt.getX()), 0d);
        }

        HazardCurveCalculator curveCalc =
                new HazardCurveCalculator(FaultSysHazardCalcSettings.getDefaultSourceFilters());
        curveCalc.getHazardCurve(logCurve, site, gmm, getCalc().getERF());

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

        String periodLabel = periodLabel(period);
        String prefix = "site_hazard_curves_" + periodLabel.toLowerCase().replaceAll(" ", "_");
        writeSiteCurvesCSV(new File(outputDir, prefix + ".csv"), curves);
        return plotSiteCurves(
                outputDir, prefix, curves, periodLabel + " (" + periodUnits(period) + ")");
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
        double minY = Double.POSITIVE_INFINITY;
        double maxY = 0;
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
            for (Point2D pt : curve) {
                if (pt.getY() > 0) {
                    minY = Math.min(minY, pt.getY());
                    maxY = Math.max(maxY, pt.getY());
                }
            }
        }
        Range yRange =
                new Range(
                        Math.max(1e-8, Double.isFinite(minY) ? minY : 1e-8),
                        Math.max(1e-7, maxY * 1.2));

        // mark the return periods that the maps are built for
        PlotLineType[] rpLineTypes = {PlotLineType.DASHED, PlotLineType.DOTTED};
        for (int i = 0; i < SolHazardMapCalc.MAP_RPS.length; i++) {
            ReturnPeriods rp = SolHazardMapCalc.MAP_RPS[i];
            DefaultXY_DataSet line = new DefaultXY_DataSet();
            line.set(curves.values().iterator().next().getMinX(), rp.oneYearProb);
            line.set(curves.values().iterator().next().getMaxX(), rp.oneYearProb);
            line.setName(rp.label);
            funcs.add(line);
            chars.add(
                    new PlotCurveCharacterstics(
                            rpLineTypes[i % rpLineTypes.length], 1f, Color.GRAY));
        }

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

    static String periodLabel(double period) {
        if (period == -1d) {
            return "PGV";
        }
        if (period == 0d) {
            return "PGA";
        }
        Preconditions.checkArgument(period > 0, "Unexpected period %s", period);
        return (period == Math.rint(period) ? String.valueOf((int) period) : String.valueOf(period))
                + "s SA";
    }

    static String periodUnits(double period) {
        return period == -1d ? "cm/s" : "g";
    }
}

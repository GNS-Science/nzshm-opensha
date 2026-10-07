package nz.cri.gns.NZSHM22.opensha.hazard.joint;

import com.google.common.base.Preconditions;
import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.jfree.data.Range;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.LightFixedXFunc;
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
import org.opensha.nshmp.shaded.gmm.NshmpGmm;
import org.opensha.sha.calc.HazardCurveCalculator;
import org.opensha.sha.calc.sourceFilters.SourceFilter;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysHazardCalcSettings;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc.ReturnPeriods;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.gui.infoTools.IMT_Info;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.attenRelImpl.JointRuptureExperimentalIMR;
import org.opensha.sha.imr.attenRelImpl.nshmp.NSHMP_AttenRelSupplier;
import org.opensha.sha.util.TectonicRegionType;

/**
 * Calculates hazard maps and hazard curves for a crustal + subduction inversion solution, either
 * with {@link JointRuptureExperimentalIMR} for solutions containing joint ruptures, or with a
 * crustal and an interface GMM dispatched per source. See {@link JointHazardInput.GmmMode}. The
 * inputs and their validation live in {@link JointHazardInput}; this class sets up the GMMs and the
 * OpenSHA {@link SolHazardMapCalc} they are driven through, and turns the results into maps,
 * curves, plots and CSVs.
 *
 * <p>In {@link JointHazardInput.GmmMode#JOINT_RUPTURE} the joint GMM is registered for a single
 * tectonic region type only. OpenSHA's {@code TRTUtils.getIMRforTRT} applies a single-entry IMR map
 * to every source regardless of the source's own TRT, which is what we want there: the joint IMR
 * does its own crustal/interface dispatch per rupture, so it must see all sources.
 *
 * <p>In {@link JointHazardInput.GmmMode#PER_TECTONIC_REGION} the map has one GMM per tectonic
 * region type and OpenSHA dispatches on the source's TRT instead. That only works if the rupture
 * set says what each rupture's TRT is, so the constructor makes sure it does.
 *
 * <p>The constructor applies the tectonic region types in {@link
 * JointHazardInput.GmmMode#JOINT_RUPTURE} too, even though nothing dispatches on them there. The
 * calculator's default source filter is {@code TectonicRegionDistCutoffFilter}, a per-region
 * distance cutoff, and without the module every source reaches it as {@link
 * TectonicRegionType#ACTIVE_SHALLOW} and is dropped beyond 300km instead of the 1000km that {@link
 * TectonicRegionType#SUBDUCTION_INTERFACE} allows. Subduction sources would then be culled well
 * inside their range, zeroing the hazard at sites that depend on a distant interface. Joint
 * ruptures are given {@link TectonicRegionType#SUBDUCTION_INTERFACE} so that they get the wider
 * cutoff; see {@link JointSolutions#tectonicRegimes(FaultSystemRupSet, TectonicRegionType)}.
 *
 * <p>Creating a calculator locks its {@link JointHazardInput}.
 */
public class JointHazardMapCalculator {

    /** Background seismicity option of every calculation: fault sources only. */
    public static final IncludeBackgroundOption BACKGROUND = IncludeBackgroundOption.EXCLUDE;

    /** How far below the standard USGS grid the map curves are extended, as a factor on the IML. */
    static final double IML_EXTENSION_FACTOR = 0.1;

    private final JointHazardInput input;

    private SolHazardMapCalc calc;

    /**
     * The curves of each of the input's parts, one array per period. Null until {@link
     * #calcHazardCurves()} has run.
     */
    private List<List<DiscretizedFunc[]>> partCurves;

    public JointHazardMapCalculator(JointHazardInput input) {
        this.input = input;
        // the ERF reads source tectonic region types from this module, not from the sections, and
        // both the GMM dispatch and the source distance cutoffs are keyed off them
        TectonicRegionType jointRegime =
                input.getGmmMode() == JointHazardInput.GmmMode.PER_TECTONIC_REGION
                        ? null
                        : TectonicRegionType.SUBDUCTION_INTERFACE;
        JointSolutions.applyTectonicRegimes(input.getSolution().getRupSet(), jointRegime);
        for (FaultSystemSolution part : input.getParts()) {
            JointSolutions.applyTectonicRegimes(part.getRupSet(), jointRegime);
        }
        input.lock();
    }

    public JointHazardInput getInput() {
        return input;
    }

    /**
     * Creates a configured instance of the experimental joint GMM. Parameter defaults are applied;
     * the intensity measure is set later, per period, by the calculation.
     */
    public static ScalarIMR buildGmm() {
        JointRuptureExperimentalIMR gmm = new JointRuptureExperimentalIMR();
        gmm.setParamDefaults();
        return gmm;
    }

    /** The crustal component GMM of the joint GMM, on its own. */
    public static ScalarIMR buildCrustalGmm() {
        return buildComponentGmm(JointRuptureExperimentalIMR.DEFAULT_CRUSTAL_GMM);
    }

    /** The interface component GMM of the joint GMM, on its own. */
    public static ScalarIMR buildInterfaceGmm() {
        return buildComponentGmm(JointRuptureExperimentalIMR.DEFAULT_INTERFACE_GMM);
    }

    protected static ScalarIMR buildComponentGmm(NshmpGmm gmm) {
        ScalarIMR imr = new NSHMP_AttenRelSupplier(gmm).get();
        imr.setParamDefaults();
        return imr;
    }

    /**
     * The GMM map for {@link JointHazardInput.GmmMode#JOINT_RUPTURE}. It deliberately holds a
     * single entry: a single-entry map is applied to every source regardless of the source's
     * tectonic region type, so the joint GMM sees crustal, interface and joint ruptures alike and
     * dispatches internally.
     */
    public static Map<TectonicRegionType, Supplier<ScalarIMR>> gmmSupplierMap() {
        Map<TectonicRegionType, Supplier<ScalarIMR>> map = new EnumMap<>(TectonicRegionType.class);
        map.put(TectonicRegionType.ACTIVE_SHALLOW, JointHazardMapCalculator::buildGmm);
        return map;
    }

    /**
     * The GMM map for {@link JointHazardInput.GmmMode#PER_TECTONIC_REGION}: the crustal and the
     * interface component of the joint GMM, each under its own tectonic region type. Using the
     * joint GMM's own components keeps the two modes comparable.
     *
     * <p>Unlike the single-entry map above, a map with more than one entry is a strict lookup by
     * source tectonic region type, so it has to cover every type the ERF contains — and the ERF
     * only reports anything other than ACTIVE_SHALLOW if the rupture set carries a {@code
     * RupSetTectonicRegimes} module. See {@link JointSolutions#applyTectonicRegimes}.
     */
    public static Map<TectonicRegionType, Supplier<ScalarIMR>> perTrtGmmSupplierMap() {
        Map<TectonicRegionType, Supplier<ScalarIMR>> map = new EnumMap<>(TectonicRegionType.class);
        map.put(TectonicRegionType.ACTIVE_SHALLOW, JointHazardMapCalculator::buildCrustalGmm);
        map.put(
                TectonicRegionType.SUBDUCTION_INTERFACE,
                JointHazardMapCalculator::buildInterfaceGmm);
        return map;
    }

    /** The GMM map for this calculator's mode. */
    public Map<TectonicRegionType, Supplier<ScalarIMR>> gmmSuppliers() {
        return input.getGmmMode() == JointHazardInput.GmmMode.PER_TECTONIC_REGION
                ? perTrtGmmSupplierMap()
                : gmmSupplierMap();
    }

    /**
     * Instantiates this calculator's GMMs and sets them to the given period. The map has the same
     * shape as {@link #gmmSuppliers()}, so OpenSHA dispatches sources to GMMs exactly the way the
     * map calculation does.
     *
     * @param period 0 for PGA, -1 for PGV, a positive value for an SA period in seconds
     */
    public EnumMap<TectonicRegionType, ScalarIMR> buildGmmMap(double period) {
        EnumMap<TectonicRegionType, ScalarIMR> gmms = new EnumMap<>(TectonicRegionType.class);
        for (Map.Entry<TectonicRegionType, Supplier<ScalarIMR>> entry : gmmSuppliers().entrySet()) {
            gmms.put(entry.getKey(), entry.getValue().get());
        }
        FaultSysHazardCalcSettings.setIMforPeriod(gmms, period);
        return gmms;
    }

    /**
     * A site at the given location, carrying the default reference site parameters (Vs30 and the
     * like) of every GMM in this calculator. The parameters are the union over the GMMs, so the
     * same site can be handed to whichever GMM a source dispatches to.
     */
    public Site buildSite(Location location) {
        Site site = new Site(location);
        for (Parameter<?> siteParam :
                FaultSysHazardCalcSettings.getDefaultRefSiteParams(gmmSuppliers())) {
            site.addParameter((Parameter<?>) siteParam.clone());
        }
        return site;
    }

    /**
     * The source filters that hazard calculations use, i.e. the OpenSHA defaults. Chiefly {@code
     * TectonicRegionDistCutoffFilter}, which drops a source once it is further from the site than
     * the cutoff for its tectonic region type. Sources dropped by these filters contribute nothing
     * and are invisible to anything downstream, including {@link SiteSourceExplorer}.
     */
    public static List<SourceFilter> sourceFilters() {
        return FaultSysHazardCalcSettings.getDefaultSourceFilters().getEnabledFilters();
    }

    /**
     * The underlying map calculator, built on first use. It holds the map curves once {@link
     * #calcHazardCurves()} has run, and the ERF of the whole solution either way. Fault sources
     * only; the joint rupture solutions do not carry a grid source provider.
     *
     * <p>The ERF's per-thread distance cache wrapper is left on in every mode. It used to have to
     * be switched off for {@link JointHazardInput.GmmMode#JOINT_RUPTURE}, because it replaced every
     * rupture surface with an opaque {@code CustomCacheWrappedSurface} and {@link
     * JointRuptureExperimentalIMR} only splits a rupture into its crustal and interface parts when
     * it sees a {@code CompoundSurface} carrying section data. {@code DistCachedERFWrapper} now
     * leaves a compound surface compound, with its section list intact, so the split still happens
     * and the cache can be kept.
     */
    public synchronized SolHazardMapCalc getCalc() {
        if (calc == null) {
            calc = buildCalc(input.getSolution(), input.getPeriods());
        }
        return calc;
    }

    /**
     * A new map calculator for a solution and the given periods: the whole solution, or a part of
     * it, see {@link JointHazardInput#getParts()}.
     */
    protected SolHazardMapCalc buildCalc(FaultSystemSolution solution, double... periods) {
        SolHazardMapCalc calc =
                new SolHazardMapCalc(
                        solution, gmmSuppliers(), input.getRegion(), BACKGROUND, periods);
        calc.setXVals(mapXVals());
        return calc;
    }

    /**
     * Calculates the hazard curves at every node of the region, unless that has already happened.
     *
     * <p>The input is calculated part by part, see {@link JointHazardInput#getParts()}. Where a
     * part carries a {@link HazardMapCurves} module, the curves it has for these inputs are used
     * and only the periods it lacks are calculated. The module is not changed; see {@link
     * #attachCurves} to attach curves.
     *
     * <p>The curves of the parts are then combined. Every rupture is an independent Poisson source,
     * so the probability of exceedance of the whole is {@code 1 - prod(1 - P_i)} over the parts,
     * which is what a calculation of the merged solution gives.
     */
    public synchronized JointHazardMapCalculator calcHazardCurves() {
        if (partCurves != null) {
            return this;
        }
        List<List<DiscretizedFunc[]>> curvesPerPart = new ArrayList<>();
        for (FaultSystemSolution part : input.getParts()) {
            curvesPerPart.add(curvesFor(part));
        }
        List<DiscretizedFunc[]> combined = new ArrayList<>();
        for (int p = 0; p < input.getPeriods().length; p++) {
            List<DiscretizedFunc[]> periodCurves = new ArrayList<>();
            for (List<DiscretizedFunc[]> curves : curvesPerPart) {
                periodCurves.add(curves.get(p));
            }
            combined.add(combine(periodCurves));
        }
        // the ERF of the whole is built on demand, for site curves and source explorations
        useCurves(combined);
        partCurves = curvesPerPart;
        return this;
    }

    /**
     * The map curves of a solution for every period of the inputs: taken from its {@link
     * HazardMapCurves} module where it has them, calculated in one pass otherwise.
     *
     * @param solution the solution to calculate
     * @return one array of curves per period, in the order of the inputs
     */
    protected List<DiscretizedFunc[]> curvesFor(FaultSystemSolution solution) {
        double[] periods = input.getPeriods();
        List<DiscretizedFunc[]> curves = new ArrayList<>();
        HazardMapCurves module = solution.getModule(HazardMapCurves.class);
        List<Integer> missing = new ArrayList<>();
        if (module == null) {
            for (int i = 0; i < periods.length; i++) {
                curves.add(null);
                missing.add(i);
            }
        } else {
            List<HazardMapCurves.Key> keys = HazardMapCurves.Key.keys(input, solution, periods);
            for (int i = 0; i < periods.length; i++) {
                DiscretizedFunc[] found = module.getCurves(keys.get(i), input.getRegion());
                curves.add(found);
                if (found == null) {
                    missing.add(i);
                }
            }
            System.out.println(
                    "Using hazard map curves from the solution for "
                            + (periods.length - missing.size())
                            + " of "
                            + periods.length
                            + " periods");
        }
        if (missing.isEmpty()) {
            return curves;
        }
        double[] toCalc = missing.stream().mapToDouble(i -> periods[i]).toArray();
        SolHazardMapCalc calculated = buildCalc(solution, toCalc);
        calculated.calcHazardCurves(input.getNumThreads());
        for (int i : missing) {
            curves.set(i, calculated.getCurves(periods[i]));
        }
        return curves;
    }

    /**
     * Combines the curves of independent sources: at every node and intensity level, the
     * probability of exceedance is {@code 1 - prod(1 - P_i)}. A single source's curves are returned
     * as they are.
     *
     * @param parts the curves of each source, all indexed by the same region nodes and sharing
     *     their x values
     */
    protected static DiscretizedFunc[] combine(List<DiscretizedFunc[]> parts) {
        if (parts.size() == 1) {
            return parts.get(0);
        }
        DiscretizedFunc[] combined = new DiscretizedFunc[parts.get(0).length];
        for (int node = 0; node < combined.length; node++) {
            DiscretizedFunc first = parts.get(0)[node];
            double[] xVals = new double[first.size()];
            double[] nonExceedance = new double[first.size()];
            for (int j = 0; j < xVals.length; j++) {
                xVals[j] = first.getX(j);
                nonExceedance[j] = 1d;
            }
            for (DiscretizedFunc[] part : parts) {
                DiscretizedFunc curve = part[node];
                Preconditions.checkState(
                        curve.size() == xVals.length, "curves do not share their x values");
                for (int j = 0; j < xVals.length; j++) {
                    Preconditions.checkState(
                            (float) curve.getX(j) == (float) xVals[j],
                            "curves do not share their x values");
                    nonExceedance[j] *= 1d - curve.getY(j);
                }
            }
            double[] yVals = new double[xVals.length];
            for (int j = 0; j < xVals.length; j++) {
                yVals[j] = 1d - nonExceedance[j];
            }
            combined[node] = new LightFixedXFunc(xVals, yVals);
        }
        return combined;
    }

    /**
     * Replaces the map calculator with one holding the given curves. It still builds the ERF of the
     * whole solution on demand, so site curves and source explorations keep working.
     *
     * @param curves one array of curves per period of the inputs, in the same order, each indexed
     *     by region node
     */
    protected void useCurves(List<DiscretizedFunc[]> curves) {
        try {
            calc =
                    SolHazardMapCalc.forCurves(
                            input.getSolution(), input.getRegion(), input.getPeriods(), curves);
            calc.setBackSeisOption(BACKGROUND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Attaches the map curves to the parts of the input, each part getting its own as a {@link
     * HazardMapCurves} module, calculating them first if that has not happened yet. Write the parts
     * to keep the curves; later calculations on them then use the curves instead of recalculating,
     * alone or in other combinations. See {@link JointHazardInput#getParts()}.
     *
     * <p>A part is the solution the inputs were given unless that had to be backfilled, in which
     * case it is the backfilled copy. Writing such a part writes the backfilled solution.
     */
    public synchronized JointHazardMapCalculator attachCurves() {
        calcHazardCurves();
        List<FaultSystemSolution> parts = input.getParts();
        for (int i = 0; i < parts.size(); i++) {
            HazardMapCurves.addTo(parts.get(i), input, partCurves.get(i));
        }
        return this;
    }

    /**
     * x values for the map curves: the standard USGS SA function, extended one decade downwards so
     * that low hazard sites still have usable curves.
     *
     * <p>{@code SolHazardMapCalc.buildMap} reports no hazard at all for a site whose curve does not
     * reach the map's return period, i.e. where the probability of exceeding the lowest IML in the
     * grid is still below {@code ReturnPeriods.oneYearProb}. The extra decade lifts the top of the
     * curve above that threshold for marginal sites.
     *
     * <p>One decade is enough. A hazard curve tends to {@code 1 - exp(-totalRate)} as the IML tends
     * to zero, where {@code totalRate} is the rate of every rupture that passes the source distance
     * filter, and it reaches that asymptote within a decade of the standard grid's lowest IML.
     * Extending further cannot raise the top of the curve and so cannot rescue any further site.
     */
    static ArbitrarilyDiscretizedFunc mapXVals() {
        ArbitrarilyDiscretizedFunc xVals = new ArbitrarilyDiscretizedFunc();
        for (Point2D pt : IMT_Info.getUSGS_SA_Function()) {
            xVals.set(pt);
        }
        xVals.set(xVals.getMinX() * IML_EXTENSION_FACTOR, 1d);
        return xVals;
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
        for (double period : input.getPeriods()) {
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
        EnumMap<TectonicRegionType, ScalarIMR> gmms = buildGmmMap(period);
        Site site = buildSite(location);

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
     * @throws IllegalArgumentException if no site is given: there would be no curve to scale the
     *     plot to and nothing to write to the CSV
     */
    public File writeSiteCurves(File outputDir, Map<String, Location> sites, double period)
            throws IOException {
        Preconditions.checkArgument(sites != null && !sites.isEmpty(), "need at least one site");
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

    /**
     * Writes one row per site, all sharing the first curve's x values as the header.
     *
     * @throws IllegalArgumentException if there is no curve to take the header from
     */
    static void writeSiteCurvesCSV(File outputFile, Map<String, DiscretizedFunc> curves)
            throws IOException {
        Preconditions.checkArgument(!curves.isEmpty(), "need at least one curve");
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

    /**
     * Plots every site's curve on one pair of axes, scaled to the first curve's x range.
     *
     * @throws IllegalArgumentException if there is no curve to scale the plot to
     */
    static File plotSiteCurves(
            File outputDir, String prefix, Map<String, DiscretizedFunc> curves, String xAxisLabel)
            throws IOException {
        Preconditions.checkArgument(!curves.isEmpty(), "need at least one curve");
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

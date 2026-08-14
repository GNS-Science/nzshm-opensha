package nz.cri.gns.NZSHM22.opensha.hazard;

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
import java.util.function.Supplier;
import nz.cri.gns.NZSHM22.opensha.data.location.NzshmCommonLocations;
import nz.cri.gns.NZSHM22.opensha.data.region.NewZealandRegions;
import nz.cri.gns.NZSHM22.opensha.griddedSeismicity.NZSHM22_GriddedData;
import org.jfree.data.Range;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.geo.BorderType;
import org.opensha.commons.geo.GriddedRegion;
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
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysHazardCalcSettings;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc.ReturnPeriods;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.faultSurface.CompoundSurface;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.gui.infoTools.IMT_Info;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.attenRelImpl.JointRuptureExperimentalIMR;
import org.opensha.sha.util.TectonicRegionType;

/**
 * Calculates hazard maps and hazard curves for a joint (crustal + subduction) inversion solution,
 * using {@link JointRuptureExperimentalIMR}.
 *
 * <p>The experimental GMM splits every rupture surface into its crustal and its interface parts,
 * evaluates a crustal and an interface GMM separately for the two sub-ruptures, and combines the
 * two ground motions (SRSS of the medians, energy-weighted sigma). For that to be meaningful the
 * solution has to satisfy two assumptions, both checked by {@link #validate()}:
 *
 * <ol>
 *   <li>every fault section carries a tectonic region type of either {@link
 *       TectonicRegionType#ACTIVE_SHALLOW} or {@link TectonicRegionType#SUBDUCTION_INTERFACE}, and
 *   <li>rupture magnitudes follow the same area scaling the GMM assumes, i.e. {@code
 *       log10(crustalArea*10^4.2 + interfaceArea*10^4.0)}. That is the scaling of {@code
 *       EstimatedJointScalingRelationship} (config {@code scalingRelationshipName:
 *       "JOIN_ESTIMATE"}). The GMM itself bails out with an exception mid-calculation when a joint
 *       rupture magnitude disagrees by more than 5%, so it is worth checking up front.
 * </ol>
 *
 * <p>The GMM is registered for a single tectonic region type only. OpenSHA's {@code
 * TRTUtils.getIMRforTRT} applies a single-entry IMR map to every source regardless of the source's
 * own TRT, which is what we want here: the joint IMR does its own crustal/interface dispatch per
 * rupture, so it must see all sources.
 */
public class JointHazardMapCalculator {

    /** Classification of a rupture by the tectonic region types of its sections. */
    public enum RuptureType {
        CRUSTAL,
        INTERFACE,
        JOINT
    }

    /** Default map resolution in degrees, i.e. roughly 10km. */
    public static final double DEFAULT_SPACING = NZSHM22_GriddedData.GRID_SPACING;

    /** Default calculation periods: PGA and 3s SA. */
    public static final double[] DEFAULT_PERIODS = {0d, 3d};

    /**
     * Sites that hazard curves are calculated for: the nzshm-common "NZ" locations. See {@link
     * NzshmCommonLocations}.
     */
    public static Map<String, Location> defaultSites() {
        return NzshmCommonLocations.nzLocations();
    }

    /**
     * Largest fractional magnitude difference between the solution and the GMM's joint scaling that
     * we accept. Matches the tolerance hard-coded in {@link JointRuptureExperimentalIMR}.
     */
    public static final double MAX_FRACTIONAL_MAG_DIFF = 0.05;

    private final FaultSystemSolution solution;

    private GriddedRegion region;
    private double spacing = DEFAULT_SPACING;
    private double[] periods = DEFAULT_PERIODS;
    private int numThreads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);

    private SolHazardMapCalc calc;

    public JointHazardMapCalculator(FaultSystemSolution solution) {
        this.solution = solution;
    }

    /** Sets the gridded region that the map is calculated over. Overrides {@link #setSpacing}. */
    public JointHazardMapCalculator setRegion(GriddedRegion region) {
        checkNotStarted();
        this.region = region;
        return this;
    }

    /** Sets the map resolution in degrees. Ignored if a region has been set explicitly. */
    public JointHazardMapCalculator setSpacing(double spacing) {
        checkNotStarted();
        Preconditions.checkArgument(spacing > 0, "spacing must be positive");
        this.spacing = spacing;
        return this;
    }

    /** Sets the calculation periods. 0 is PGA, -1 is PGV, positive values are SA periods. */
    public JointHazardMapCalculator setPeriods(double... periods) {
        checkNotStarted();
        Preconditions.checkArgument(periods.length > 0, "need at least one period");
        this.periods = periods;
        return this;
    }

    public JointHazardMapCalculator setNumThreads(int numThreads) {
        checkNotStarted();
        Preconditions.checkArgument(numThreads > 0, "numThreads must be positive");
        this.numThreads = numThreads;
        return this;
    }

    private void checkNotStarted() {
        Preconditions.checkState(calc == null, "calculation has already been set up");
    }

    public double[] getPeriods() {
        return periods;
    }

    /**
     * The gridded region the map is calculated over. Defaults to {@link NewZealandRegions.NZ_TEST},
     * the NZSHM22 region covering all of New Zealand, at {@link #setSpacing} resolution. Pass
     * {@link NewZealandRegions.NZ_RECTANGLE} to {@link #setRegion} instead for the full NZ
     * graticule, which also covers the open ocean around the country and is six times as many sites
     * at 0.1 degrees.
     */
    public GriddedRegion getRegion() {
        if (region == null) {
            region =
                    new GriddedRegion(
                            new NewZealandRegions.NZ_TEST().getBorder(),
                            BorderType.MERCATOR_LINEAR,
                            spacing,
                            GriddedRegion.ANCHOR_0_0);
        }
        return region;
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

    /**
     * The GMM map handed to OpenSHA's hazard calculators. It deliberately holds a single entry: a
     * single-entry map is applied to every source regardless of the source's tectonic region type,
     * so the joint GMM sees crustal, interface and joint ruptures alike and dispatches internally.
     */
    public static Map<TectonicRegionType, Supplier<ScalarIMR>> gmmSupplierMap() {
        Map<TectonicRegionType, Supplier<ScalarIMR>> map = new EnumMap<>(TectonicRegionType.class);
        map.put(TectonicRegionType.ACTIVE_SHALLOW, JointHazardMapCalculator::buildGmm);
        return map;
    }

    /** Classifies a rupture by the tectonic region types of the sections it uses. */
    public static RuptureType typeOf(FaultSystemRupSet rupSet, int rupIndex) {
        boolean crustal = false;
        boolean interfce = false;
        for (int sectIndex : rupSet.getSectionsIndicesForRup(rupIndex)) {
            if (tectonicRegionType(rupSet.getFaultSectionData(sectIndex))
                    == TectonicRegionType.ACTIVE_SHALLOW) {
                crustal = true;
            } else {
                interfce = true;
            }
        }
        if (crustal && interfce) {
            return RuptureType.JOINT;
        }
        return crustal ? RuptureType.CRUSTAL : RuptureType.INTERFACE;
    }

    private static TectonicRegionType tectonicRegionType(FaultSection section) {
        TectonicRegionType trt = section.getTectonicRegionType();
        Preconditions.checkState(
                trt == TectonicRegionType.ACTIVE_SHALLOW
                        || trt == TectonicRegionType.SUBDUCTION_INTERFACE,
                "Section %s (%s) has tectonic region type %s; the joint GMM only supports"
                        + " ACTIVE_SHALLOW and SUBDUCTION_INTERFACE. Rupture sets built before"
                        + " tectonic region types were written out can be fixed with"
                        + " RupSetPropertyBackfill.",
                section.getSectionId(),
                section.getSectionName(),
                trt);
        return trt;
    }

    /** Summary of {@link #validate()}. */
    public static class ValidationResult {
        public final int numCrustal;
        public final int numInterface;
        public final int numJoint;
        public final int numJointWithRate;

        /** Largest |calculated - solution| joint magnitude difference found. */
        public final double maxJointMagDiff;

        /** Index of the rupture with the largest magnitude difference, or -1. */
        public final int worstJointRupture;

        /**
         * Number of single-section ruptures with a non-zero rate. Those reach the GMM as a plain
         * surface rather than a compound one, and the GMM then has to guess whether they are
         * crustal or interface from their magnitude alone. See {@link
         * #getNumSingleSectionWithRate()}.
         */
        private final int numSingleSectionWithRate;

        ValidationResult(
                int numCrustal,
                int numInterface,
                int numJoint,
                int numJointWithRate,
                double maxJointMagDiff,
                int worstJointRupture,
                int numSingleSectionWithRate) {
            this.numCrustal = numCrustal;
            this.numInterface = numInterface;
            this.numJoint = numJoint;
            this.numJointWithRate = numJointWithRate;
            this.maxJointMagDiff = maxJointMagDiff;
            this.worstJointRupture = worstJointRupture;
            this.numSingleSectionWithRate = numSingleSectionWithRate;
        }

        public boolean isJoint() {
            return numJoint > 0;
        }

        /**
         * Number of single-section ruptures that carry a rate and therefore end up in the ERF. The
         * GMM classifies such ruptures by comparing their magnitude against crustal and interface
         * area scaling, and as of writing that comparison in JointRuptureExperimentalIMR uses the
         * crustal scaling for both sides, so every single-section rupture is treated as interface.
         * A non-zero count here means part of the hazard is calculated with the wrong component
         * GMM.
         */
        public int getNumSingleSectionWithRate() {
            return numSingleSectionWithRate;
        }

        @Override
        public String toString() {
            return "crustal ruptures: "
                    + numCrustal
                    + ", interface ruptures: "
                    + numInterface
                    + ", joint ruptures: "
                    + numJoint
                    + " ("
                    + numJointWithRate
                    + " with a non-zero rate), max joint magnitude difference: "
                    + (float) maxJointMagDiff
                    + (worstJointRupture < 0 ? "" : " at rupture " + worstJointRupture)
                    + ", single-section ruptures with a rate: "
                    + numSingleSectionWithRate;
        }
    }

    /**
     * Checks that the solution matches the assumptions of {@link JointRuptureExperimentalIMR} and
     * returns a summary of its rupture composition.
     *
     * @throws IllegalStateException if a section has an unsupported tectonic region type, or if a
     *     joint rupture magnitude disagrees with the GMM's joint area scaling.
     */
    public ValidationResult validate() {
        FaultSystemRupSet rupSet = solution.getRupSet();

        // touches every section, so this also validates the tectonic region types
        for (int s = 0; s < rupSet.getNumSections(); s++) {
            tectonicRegionType(rupSet.getFaultSectionData(s));
        }

        int numCrustal = 0;
        int numInterface = 0;
        int numJoint = 0;
        int numJointWithRate = 0;
        double maxMagDiff = 0;
        int worst = -1;
        int numSingleSectionWithRate = 0;

        for (int r = 0; r < rupSet.getNumRuptures(); r++) {
            if (rupSet.getSectionsIndicesForRup(r).size() == 1 && solution.getRateForRup(r) > 0) {
                numSingleSectionWithRate++;
            }
            RuptureType type = typeOf(rupSet, r);
            if (type == RuptureType.CRUSTAL) {
                numCrustal++;
                continue;
            }
            if (type == RuptureType.INTERFACE) {
                numInterface++;
                continue;
            }
            numJoint++;
            if (solution.getRateForRup(r) > 0) {
                numJointWithRate++;
            }

            double solutionMag = rupSet.getMagForRup(r);
            double jointMag = jointMagForRupture(rupSet, r);
            double magDiff = Math.abs(jointMag - solutionMag);
            if (magDiff > maxMagDiff) {
                maxMagDiff = magDiff;
                worst = r;
            }
            Preconditions.checkState(
                    magDiff / solutionMag < MAX_FRACTIONAL_MAG_DIFF,
                    "Rupture %s has magnitude %s, but the joint area scaling assumed by the GMM"
                            + " gives %s. The GMM would refuse to calculate this rupture. Was the"
                            + " solution built with a different scaling relationship than"
                            + " JOIN_ESTIMATE?",
                    r,
                    solutionMag,
                    jointMag);
        }

        return new ValidationResult(
                numCrustal,
                numInterface,
                numJoint,
                numJointWithRate,
                maxMagDiff,
                worst,
                numSingleSectionWithRate);
    }

    /**
     * The magnitude the joint GMM's area scaling gives for a rupture, calculated the same way the
     * GMM does it: from the areas of the crustal and the interface parts of the rupture surface.
     */
    public static double jointMagForRupture(FaultSystemRupSet rupSet, int rupIndex) {
        RuptureSurface surface = rupSet.getSurfaceForRupture(rupIndex, 1d);
        Preconditions.checkState(
                surface instanceof CompoundSurface,
                "Rupture %s is not made up of multiple sections, so it cannot be joint",
                rupIndex);
        CompoundSurface compound = (CompoundSurface) surface;
        List<? extends RuptureSurface> surfaces = compound.getSurfaceList();
        List<? extends FaultSection> sections = compound.getSectionsList();
        Preconditions.checkNotNull(
                sections, "Rupture %s has a compound surface without section data", rupIndex);

        double crustalArea = 0;
        double interfaceArea = 0;
        for (int i = 0; i < surfaces.size(); i++) {
            if (tectonicRegionType(sections.get(i)) == TectonicRegionType.ACTIVE_SHALLOW) {
                crustalArea += surfaces.get(i).getArea();
            } else {
                interfaceArea += surfaces.get(i).getArea();
            }
        }
        return JointRuptureExperimentalIMR.getJointMag(crustalArea, interfaceArea);
    }

    /**
     * The underlying map calculator, built on first use. Fault sources only; the joint rupture
     * solutions do not carry a grid source provider.
     */
    public SolHazardMapCalc getCalc() {
        if (calc == null) {
            calc =
                    new SolHazardMapCalc(
                            solution,
                            gmmSupplierMap(),
                            getRegion(),
                            IncludeBackgroundOption.EXCLUDE,
                            periods);
            calc.setXVals(mapXVals());
        }
        return calc;
    }

    /**
     * x values for the map curves: the standard USGS SA function, extended downwards so that low
     * hazard sites still have usable curves.
     */
    static ArbitrarilyDiscretizedFunc mapXVals() {
        ArbitrarilyDiscretizedFunc xVals = new ArbitrarilyDiscretizedFunc();
        for (Point2D pt : IMT_Info.getUSGS_SA_Function()) {
            xVals.set(pt);
        }
        xVals.set(xVals.getMinX() * 0.1, 1d);
        xVals.set(xVals.getMinX() * 0.1, 1d);
        return xVals;
    }

    /** Calculates the hazard curve at every node of the region. Idempotent. */
    public JointHazardMapCalculator calcHazardCurves() {
        getCalc().calcHazardCurves(numThreads);
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
        for (double period : periods) {
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
        ScalarIMR gmm = buildGmm();
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

package nz.cri.gns.NZSHM22.opensha.inversion.joint.reporting;

import com.google.common.base.Preconditions;
import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import nz.cri.gns.NZSHM22.opensha.data.region.NewZealandRegions;
import nz.cri.gns.NZSHM22.opensha.hazard.joint.DivergingCPT;
import nz.cri.gns.NZSHM22.opensha.hazard.joint.JointHazardInput;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionPredicate;
import nz.cri.gns.NZSHM22.opensha.ruptures.FaultSectionProperties;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractRupSetPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.imr.ScalarIMR;

/**
 * Report plot rasterising section participation rates onto the hazard map grid, so that regional
 * shifts of rupture rate between two solutions are easy to spot.
 *
 * <p>Each section contributes its participation rate times its area (km² ruptured per year), spread
 * evenly over points sampled on its surface. With {@link Binning#RADIUS}, a grid node sums the
 * contributions of all sample points within {@link #DEFAULT_RADIUS} km, which smooths the
 * individual sections into a regional picture; neighbouring nodes share most of their points. With
 * {@link Binning#CELL}, a grid node sums only the sample points inside its own grid cell, so that
 * each point counts exactly once. Maps are made for all sections, crustal sections only, subduction
 * sections only and for the participation of joint ruptures only.
 *
 * <p>With a comparison solution that has the same sections, each map gets a comparison map and a
 * percentage change map (primary relative to comparison).
 *
 * <p>The maps cover {@link NewZealandRegions.NZ_RECTANGLE} and show the outline of the hazard map
 * region. Nodes without a value are not drawn.
 *
 * <p>For a rupture set without a solution, every rupture is given a rate of 1, so that the maps
 * show the number of ruptures a section participates in times its area.
 */
public class ParticipationChangeMapPlot extends AbstractRupSetPlot {

    /** Radius in km within which section sample points count towards a grid node. */
    public static final double DEFAULT_RADIUS = 40;

    /** Spacing in km of the points that a section surface is sampled at. */
    protected static final double SURFACE_SPACING = 2d;

    /**
     * Nodes where both maps are below this fraction of the median positive comparison value are NaN
     * on the change map, and smaller comparison values are raised to it before dividing. Keeps
     * sections that only carry the residual rates of the annealer from producing meaningless
     * ratios.
     */
    protected static final double FLOOR_FRACTION = 1e-3;

    /** Changes beyond this percentage are drawn in the colour of the end of the ramp. */
    protected static final double MAX_PERCENT = 200;

    /**
     * The log maps span at most this many decades below their maximum; smaller values are residual
     * annealer rates and are drawn in the colour of the bottom of the scale.
     */
    protected static final int MAX_DECADES = 5;

    /**
     * Default percentage below which changes are drawn as no change. See {@link
     * #setNoChangePercent}.
     */
    public static final double DEFAULT_NO_CHANGE_PERCENT = 1;

    protected static final String PREFIX = "partic_change_map";

    /** Colour of nodes without a value, so that they are not drawn. */
    protected static final Color NAN_COLOR = new Color(0, 0, 0, 0);

    /** How section sample points are assigned to grid nodes. */
    public enum Binning {
        /** Each node sums all points within the radius; points count for many nodes. */
        RADIUS,
        /** Each node sums the points inside its grid cell; points count for one node only. */
        CELL
    }

    /** The sections a map is made of. */
    public enum Scope {
        ALL("All sections"),
        CRUSTAL("Crustal sections"),
        SUBDUCTION("Subduction sections");

        public final String label;

        Scope(String label) {
            this.label = label;
        }

        /**
         * Whether a section belongs to this scope. Sections without a partition only belong to
         * {@link #ALL}.
         */
        public boolean contains(FaultSection section) {
            if (this == ALL) {
                return true;
            }
            PartitionPredicate partition = FaultSectionProperties.getPartition(section);
            if (partition == null) {
                return false;
            }
            return this == CRUSTAL ? partition.isCrustal() : partition.isSubduction();
        }

        /** The sections of the rupture set that belong to this scope. */
        public IntPredicate predicate(FaultSystemRupSet rupSet) {
            return s -> contains(rupSet.getFaultSectionData(s));
        }
    }

    protected final GriddedRegion region;
    protected final GriddedRegion outline;
    protected final double radius;
    protected final Binning binning;
    protected double noChangePercent = DEFAULT_NO_CHANGE_PERCENT;

    /**
     * Creates a plot on {@link NewZealandRegions.NZ_RECTANGLE} at the hazard map spacing, outlined
     * with the hazard map region, using {@link Binning#RADIUS} with {@link #DEFAULT_RADIUS}.
     */
    public ParticipationChangeMapPlot() {
        this(Binning.RADIUS);
    }

    /**
     * Creates a plot on {@link NewZealandRegions.NZ_RECTANGLE} at the hazard map spacing, outlined
     * with the hazard map region.
     *
     * @param binning how sample points are assigned to grid nodes; {@link Binning#RADIUS} uses
     *     {@link #DEFAULT_RADIUS}
     */
    public ParticipationChangeMapPlot(Binning binning) {
        this(
                new GriddedRegion(
                        new NewZealandRegions.NZ_RECTANGLE(),
                        JointHazardInput.DEFAULT_SPACING,
                        GriddedRegion.ANCHOR_0_0),
                JointHazardInput.defaultRegion(JointHazardInput.DEFAULT_SPACING),
                DEFAULT_RADIUS,
                binning);
    }

    /**
     * Creates a plot on a custom grid, outlined with the grid's own border.
     *
     * @param region the grid to map
     * @param radius the distance in km within which section sample points count for a node
     */
    public ParticipationChangeMapPlot(GriddedRegion region, double radius) {
        this(region, region, radius);
    }

    /**
     * Creates a plot on a custom grid with a custom outline.
     *
     * @param region the grid to map
     * @param outline the region whose border is drawn on the maps
     * @param radius the distance in km within which section sample points count for a node
     */
    public ParticipationChangeMapPlot(GriddedRegion region, GriddedRegion outline, double radius) {
        this(region, outline, radius, Binning.RADIUS);
    }

    /**
     * Creates a plot on a custom grid with a custom outline and binning.
     *
     * @param region the grid to map
     * @param outline the region whose border is drawn on the maps
     * @param radius the distance in km within which section sample points count for a node; only
     *     used with {@link Binning#RADIUS}
     * @param binning how sample points are assigned to grid nodes
     */
    public ParticipationChangeMapPlot(
            GriddedRegion region, GriddedRegion outline, double radius, Binning binning) {
        Preconditions.checkArgument(radius > 0, "radius must be positive");
        this.region = region;
        this.outline = outline;
        this.radius = radius;
        this.binning = binning;
    }

    /**
     * Sets the percentage change below which the change maps call it no change and draw it in
     * white. Defaults to {@link #DEFAULT_NO_CHANGE_PERCENT}.
     *
     * @param noChangePercent the threshold in percent, must be positive
     * @return this plot
     */
    public ParticipationChangeMapPlot setNoChangePercent(double noChangePercent) {
        Preconditions.checkArgument(
                noChangePercent > 0, "no-change percent must be positive: %s", noChangePercent);
        this.noChangePercent = noChangePercent;
        return this;
    }

    @Override
    public String getName() {
        return "Participation Change Map";
    }

    /**
     * Per section, the participation rate times the section area in km², i.e. the area of the
     * section ruptured per year.
     *
     * @param sol the solution
     * @return rate times area per section
     */
    public static double[] sectionRateArea(FaultSystemSolution sol) {
        return sectionRateArea(sol, null);
    }

    /**
     * Per section, the participation rate of the selected ruptures times the section area in km².
     *
     * @param sol the solution
     * @param ruptures per rupture, whether it counts; null for all ruptures
     * @return rate times area per section
     */
    public static double[] sectionRateArea(FaultSystemSolution sol, boolean[] ruptures) {
        FaultSystemRupSet rupSet = sol.getRupSet();
        double[] rates;
        if (ruptures == null) {
            rates = sol.calcParticRateForAllSects(0, Double.POSITIVE_INFINITY);
        } else {
            rates = new double[rupSet.getNumSections()];
            for (int r = 0; r < ruptures.length; r++) {
                if (ruptures[r]) {
                    double rate = sol.getRateForRup(r);
                    for (int s : rupSet.getSectionsIndicesForRup(r)) {
                        rates[s] += rate;
                    }
                }
            }
        }
        double[] result = new double[rates.length];
        for (int s = 0; s < rates.length; s++) {
            result[s] = rates[s] * rupSet.getAreaForSection(s) * 1e-6;
        }
        return result;
    }

    /**
     * A solution in which every rupture has a rate of 1, so that rupture sets can be mapped like
     * solutions.
     *
     * @param rupSet the rupture set
     * @return the solution
     */
    public static FaultSystemSolution uniformSolution(FaultSystemRupSet rupSet) {
        double[] rates = new double[rupSet.getNumRuptures()];
        Arrays.fill(rates, 1);
        return new FaultSystemSolution(rupSet, rates);
    }

    /** Whether any value is positive. */
    protected static boolean anyPositive(double[] values) {
        return values != null && Arrays.stream(values).anyMatch(v -> v > 0);
    }

    /** A section's surface sample points, its padded bounding box and the weight of each point. */
    protected static class SectionPoints {
        double weight;
        List<Location> points;
        double minLat, maxLat, minLon, maxLon;
    }

    /**
     * Samples the surfaces of the sections in the filter that have a positive value. Each point
     * carries an even share of the section's value. Bounding boxes are padded by maxDist.
     */
    protected static List<SectionPoints> sampleSections(
            FaultSystemRupSet rupSet, double[] values, IntPredicate filter, double maxDist) {
        List<SectionPoints> result = new ArrayList<>();
        for (FaultSection section : rupSet.getFaultSectionDataList()) {
            int s = section.getSectionId();
            if (!filter.test(s) || !(values[s] > 0)) {
                continue;
            }
            SectionPoints samples = new SectionPoints();
            samples.points =
                    section.getFaultSurface(SURFACE_SPACING, false, false)
                            .getEvenlyDiscritizedListOfLocsOnSurface();
            samples.weight = values[s] / samples.points.size();
            samples.minLat = Double.POSITIVE_INFINITY;
            samples.maxLat = Double.NEGATIVE_INFINITY;
            samples.minLon = Double.POSITIVE_INFINITY;
            samples.maxLon = Double.NEGATIVE_INFINITY;
            for (Location loc : samples.points) {
                samples.minLat = Math.min(samples.minLat, loc.getLatitude());
                samples.maxLat = Math.max(samples.maxLat, loc.getLatitude());
                samples.minLon = Math.min(samples.minLon, loc.getLongitude());
                samples.maxLon = Math.max(samples.maxLon, loc.getLongitude());
            }
            // a degree of longitude is never shorter than at the box's pole-most latitude
            double latPad = maxDist / 111d;
            double poleLat =
                    Math.min(89, Math.max(Math.abs(samples.minLat), Math.abs(samples.maxLat)));
            double lonPad = maxDist / (111d * Math.cos(Math.toRadians(poleLat + latPad)));
            samples.minLat -= latPad;
            samples.maxLat += latPad;
            samples.minLon -= lonPad;
            samples.maxLon += lonPad;
            result.add(samples);
        }
        return result;
    }

    /**
     * Rasterises section values onto a grid: each node sums the point weights of all section sample
     * points within radius km (horizontal distance). Nodes without any point in range are NaN.
     *
     * @param rupSet the rupture set the values belong to
     * @param values a value per section, see {@link #sectionRateArea}
     * @param filter the sections to include
     * @param region the grid
     * @param radius distance in km
     * @return the map
     */
    public static GriddedGeoDataSet rasterise(
            FaultSystemRupSet rupSet,
            double[] values,
            IntPredicate filter,
            GriddedRegion region,
            double radius) {
        List<SectionPoints> sections = sampleSections(rupSet, values, filter, radius);
        double[] result = new double[region.getNodeCount()];
        IntStream.range(0, result.length)
                .parallel()
                .forEach(
                        i -> {
                            Location loc = region.getLocation(i);
                            double lat = loc.getLatitude();
                            double lon = loc.getLongitude();
                            double sum = 0;
                            boolean any = false;
                            for (SectionPoints samples : sections) {
                                if (lat < samples.minLat
                                        || lat > samples.maxLat
                                        || lon < samples.minLon
                                        || lon > samples.maxLon) {
                                    continue;
                                }
                                for (Location point : samples.points) {
                                    if (LocationUtils.horzDistanceFast(point, loc) <= radius) {
                                        sum += samples.weight;
                                        any = true;
                                    }
                                }
                            }
                            result[i] = any ? sum : Double.NaN;
                        });
        GriddedGeoDataSet map = new GriddedGeoDataSet(region, false);
        for (int i = 0; i < result.length; i++) {
            map.set(i, result[i]);
        }
        return map;
    }

    /**
     * Bins section values onto a grid: each node sums the point weights of the section sample
     * points inside its grid cell, so that every point counts for at most one node. Points outside
     * the grid are dropped, and nodes without any point are NaN.
     *
     * @param rupSet the rupture set the values belong to
     * @param values a value per section, see {@link #sectionRateArea}
     * @param filter the sections to include
     * @param region the grid
     * @return the map
     */
    public static GriddedGeoDataSet binInCells(
            FaultSystemRupSet rupSet, double[] values, IntPredicate filter, GriddedRegion region) {
        double[] result = new double[region.getNodeCount()];
        Arrays.fill(result, Double.NaN);
        for (SectionPoints samples : sampleSections(rupSet, values, filter, 0)) {
            for (Location point : samples.points) {
                int i = region.indexForLocation(point);
                if (i >= 0) {
                    result[i] = (Double.isNaN(result[i]) ? 0 : result[i]) + samples.weight;
                }
            }
        }
        GriddedGeoDataSet map = new GriddedGeoDataSet(region, false);
        for (int i = 0; i < result.length; i++) {
            map.set(i, result[i]);
        }
        return map;
    }

    /** Maps section values onto {@link #region} using {@link #binning}. */
    protected GriddedGeoDataSet map(
            FaultSystemRupSet rupSet, double[] values, IntPredicate filter) {
        return binning == Binning.CELL
                ? binInCells(rupSet, values, filter, region)
                : rasterise(rupSet, values, filter, region, radius);
    }

    /**
     * The percentage change from comparison to primary. Comparison values are raised to a floor of
     * {@link #FLOOR_FRACTION} times the median positive comparison value before dividing, and nodes
     * where both maps are below the floor (or NaN) are NaN.
     *
     * @param primary the primary map
     * @param comparison the comparison map
     * @return the change map in percent
     */
    public static GriddedGeoDataSet percentChange(
            GriddedGeoDataSet primary, GriddedGeoDataSet comparison) {
        Preconditions.checkArgument(
                primary.size() == comparison.size(), "maps must cover the same region");
        double[] positive =
                IntStream.range(0, comparison.size())
                        .mapToDouble(comparison::get)
                        .filter(v -> v > 0)
                        .sorted()
                        .toArray();
        double floor = positive.length == 0 ? 0 : positive[positive.length / 2] * FLOOR_FRACTION;
        GriddedGeoDataSet result = new GriddedGeoDataSet(primary.getRegion(), false);
        for (int i = 0; i < primary.size(); i++) {
            double p = primary.get(i);
            double c = comparison.get(i);
            p = Double.isNaN(p) ? 0 : p;
            c = Double.isNaN(c) ? 0 : c;
            if ((p <= floor && c <= floor) || floor == 0) {
                result.set(i, Double.NaN);
            } else {
                result.set(i, 100 * (p / Math.max(c, floor) - 1));
            }
        }
        return result;
    }

    /** log10 of a map; non-positive values become NaN. */
    protected static GriddedGeoDataSet log10(GriddedGeoDataSet map) {
        GriddedGeoDataSet result = new GriddedGeoDataSet(map.getRegion(), false);
        for (int i = 0; i < map.size(); i++) {
            double v = map.get(i);
            result.set(i, v > 0 ? Math.log10(v) : Double.NaN);
        }
        return result;
    }

    /** Finite values of a map, sorted. */
    protected static double[] finiteValues(GriddedGeoDataSet map) {
        return IntStream.range(0, map.size())
                .mapToDouble(map::get)
                .filter(Double::isFinite)
                .sorted()
                .toArray();
    }

    /**
     * A rainbow CPT spanning the finite values of all log maps, rounded out to whole decades and
     * limited to {@link #MAX_DECADES}.
     */
    protected static CPT logCPT(GriddedGeoDataSet... logMaps) throws IOException {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (GriddedGeoDataSet map : logMaps) {
            double[] values = finiteValues(map);
            if (values.length > 0) {
                // the bottom percent is residual annealer rates and would stretch the scale
                min = Math.min(min, values[values.length / 100]);
                max = Math.max(max, values[values.length - 1]);
            }
        }
        if (!Double.isFinite(min)) {
            min = -6;
            max = 0;
        }
        min = Math.max(Math.floor(min), Math.ceil(max) - MAX_DECADES);
        max = Math.ceil(max);
        if (max <= min) {
            max = min + 1;
        }
        CPT cpt = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(min, max);
        cpt.setNanColor(NAN_COLOR);
        cpt.setBelowMinColor(cpt.getMinColor());
        cpt.setAboveMaxColor(cpt.getMaxColor());
        return cpt;
    }

    /**
     * A diverging CPT with log spacing out from zero, spanning the change map but clipped at {@link
     * #MAX_PERCENT}. Changes below -100% cannot happen.
     *
     * @param change the percentage change map
     * @param noChangePercent changes smaller than this percentage are drawn as no change
     */
    protected static CPT changeCPT(GriddedGeoDataSet change, double noChangePercent)
            throws IOException {
        double[] values = finiteValues(change);
        double min = values.length == 0 ? 0 : Math.min(-noChangePercent * 2, values[0]);
        double max =
                values.length == 0
                        ? 0
                        : Math.max(
                                noChangePercent * 2,
                                Math.min(MAX_PERCENT, values[values.length - 1]));
        if (values.length == 0) {
            min = -10;
            max = 10;
        }
        CPT cpt =
                DivergingCPT.ramp(GMT_CPT_Files.DIVERGING_VIK_UNIFORM.instance(), min, max)
                        .logFloor(noChangePercent)
                        .zeroColor(Color.WHITE)
                        .build();
        cpt.setNanColor(NAN_COLOR);
        cpt.setBelowMinColor(cpt.getMinColor());
        cpt.setAboveMaxColor(cpt.getMaxColor());
        return cpt;
    }

    /** Min, median and max of a map, formatted with the given format. */
    protected static String stats(GriddedGeoDataSet map, String format) {
        double[] values = finiteValues(map);
        if (values.length == 0) {
            return "no sections in range";
        }
        DoubleSummaryStatistics stats = Arrays.stream(values).summaryStatistics();
        return String.format(
                "min " + format + ", median " + format + ", max " + format,
                stats.getMin(),
                values[values.length / 2],
                stats.getMax());
    }

    /**
     * A map calculator used only for plotting, so that maps look like the hazard maps. It draws the
     * border of {@link #outline}.
     */
    protected SolHazardMapCalc plotter(FaultSystemSolution sol) {
        return new SolHazardMapCalc(
                sol, (Supplier<ScalarIMR>) null, outline, IncludeBackgroundOption.EXCLUDE, 0d);
    }

    @Override
    public List<String> plot(
            FaultSystemRupSet rupSet,
            FaultSystemSolution sol,
            ReportMetadata meta,
            File resourcesDir,
            String relPathToResources,
            String topLink)
            throws IOException {

        boolean uniform = sol == null;
        if (uniform) {
            sol = uniformSolution(rupSet);
        }
        FaultSystemSolution compSol = null;
        if (meta != null && meta.hasComparison() && meta.comparisonHasSameSects) {
            compSol = uniform ? uniformSolution(meta.comparison.rupSet) : meta.comparison.sol;
        }
        String unit = uniform ? "rupture count × km²" : "km² ruptured per yr";
        String primaryName = meta == null ? "Primary" : meta.primary.name;

        double[] values = sectionRateArea(sol);
        double[] compValues = compSol == null ? null : sectionRateArea(compSol);

        List<String> lines = new ArrayList<>();
        lines.add(
                (uniform
                                ? "Number of ruptures a section participates in times section area"
                                        + " (no solution, so every rupture has a rate of 1),"
                                : "Section participation rate times section area (km² ruptured per"
                                        + " year),")
                        + (binning == Binning.CELL
                                ? " summed over the section surface points inside each grid cell."
                                        + " Cells do not overlap, so each point counts once."
                                : " summed over section surface points within "
                                        + (int) radius
                                        + " km of each grid node."));
        if (compSol != null) {
            lines.add(
                    " The change map is the percentage change from "
                            + meta.comparison.name
                            + " to "
                            + primaryName
                            + "; changes above "
                            + (int) MAX_PERCENT
                            + "% are drawn in the darkest colour, and nodes with only negligible"
                            + " values in both are not drawn.");
        }
        lines.add("");

        TableBuilder table = MarkdownUtils.tableBuilder();
        table.initNewLine();
        table.addColumn("");
        table.addColumn(primaryName);
        if (compSol != null) {
            table.addColumn(meta.comparison.name);
            table.addColumn("Change");
        }
        table.finalizeLine();

        for (Scope scope : Scope.values()) {
            IntPredicate filter = scope.predicate(rupSet);
            if (IntStream.range(0, rupSet.getNumSections()).noneMatch(filter)) {
                continue;
            }
            addRow(
                    table,
                    scope.label,
                    PREFIX + "_" + scope.name().toLowerCase(),
                    sol,
                    values,
                    filter,
                    compSol,
                    compValues,
                    compSol == null ? null : scope.predicate(compSol.getRupSet()),
                    unit,
                    meta,
                    resourcesDir,
                    relPathToResources);
        }

        double[] jointValues =
                sectionRateArea(sol, JointRuptureProportionPlot.jointRuptures(rupSet));
        if (anyPositive(jointValues)) {
            double[] compJointValues =
                    compSol == null
                            ? null
                            : sectionRateArea(
                                    compSol,
                                    JointRuptureProportionPlot.jointRuptures(compSol.getRupSet()));
            // a comparison without joint ruptures would only give an empty map
            boolean withComp = anyPositive(compJointValues);
            addRow(
                    table,
                    "Joint ruptures",
                    PREFIX + "_joint",
                    sol,
                    jointValues,
                    s -> true,
                    withComp ? compSol : null,
                    withComp ? compJointValues : null,
                    s -> true,
                    unit,
                    meta,
                    resourcesDir,
                    relPathToResources);
        }

        lines.addAll(table.build());
        return lines;
    }

    /**
     * Rasterises one set of section values, and the comparison values if there is a comparison, and
     * adds the maps as a row of the table.
     */
    protected void addRow(
            TableBuilder table,
            String label,
            String prefix,
            FaultSystemSolution sol,
            double[] values,
            IntPredicate filter,
            FaultSystemSolution compSol,
            double[] compValues,
            IntPredicate compFilter,
            String unit,
            ReportMetadata meta,
            File resourcesDir,
            String relPathToResources)
            throws IOException {
        String primaryName = meta == null ? "Primary" : meta.primary.name;
        GriddedGeoDataSet map = map(sol.getRupSet(), values, filter);
        GriddedGeoDataSet compMap =
                compSol == null ? null : map(compSol.getRupSet(), compValues, compFilter);
        GriddedGeoDataSet logMap = log10(map);
        GriddedGeoDataSet compLogMap = compMap == null ? null : log10(compMap);
        CPT cpt = compLogMap == null ? logCPT(logMap) : logCPT(logMap, compLogMap);
        String zLabel = "Log10 " + unit + ", " + label.toLowerCase();

        List<String> images = new ArrayList<>();
        List<String> captions = new ArrayList<>();
        plotter(sol).plotMap(resourcesDir, prefix, logMap, cpt, primaryName, zLabel);
        images.add(prefix);
        captions.add(stats(map, "%.3g"));
        if (compMap != null) {
            String compPrefix = prefix + "_comparison";
            plotter(compSol)
                    .plotMap(
                            resourcesDir,
                            compPrefix,
                            compLogMap,
                            cpt,
                            meta.comparison.name,
                            zLabel);
            images.add(compPrefix);
            captions.add(stats(compMap, "%.3g"));

            GriddedGeoDataSet change = percentChange(map, compMap);
            String changePrefix = prefix + "_change";
            plotter(sol)
                    .plotMap(
                            resourcesDir,
                            changePrefix,
                            change,
                            changeCPT(change, noChangePercent),
                            primaryName + " vs " + meta.comparison.name,
                            "% change, " + label.toLowerCase());
            images.add(changePrefix);
            captions.add(stats(change, "%+.0f%%"));
        }

        table.initNewLine();
        table.addColumn("**" + label + "**");
        for (String image : images) {
            table.addColumn("![" + image + "](" + relPathToResources + "/" + image + ".png)");
        }
        table.finalizeLine();
        table.initNewLine();
        table.addColumn("");
        for (String caption : captions) {
            table.addColumn(caption);
        }
        table.finalizeLine();
    }

    @Override
    public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
        return null;
    }
}

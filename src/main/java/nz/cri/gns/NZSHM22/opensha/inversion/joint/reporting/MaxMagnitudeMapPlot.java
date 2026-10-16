package nz.cri.gns.NZSHM22.opensha.inversion.joint.reporting;

import com.google.common.base.Preconditions;
import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import nz.cri.gns.NZSHM22.opensha.hazard.joint.DivergingCPT;
import nz.cri.gns.NZSHM22.opensha.hazard.joint.JointHazardInput;
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
 * Report plot mapping, on the hazard map grid, the largest magnitude of any rupture with a non-zero
 * rate that comes within {@link #DEFAULT_MAX_DIST} of each grid node. Shows how magnitudes are
 * distributed spatially, e.g. how they change when joint ruptures are introduced.
 *
 * <p>A second map shows, per grid node, the distance to the largest magnitude rupture with a
 * non-zero rate within {@link #DEFAULT_DISTANCE_MAP_DIST}.
 *
 * <p>If the report has a comparison solution, each map gets a comparison map and a difference map
 * (primary minus comparison).
 */
public class MaxMagnitudeMapPlot extends AbstractRupSetPlot {

    /** Half of the hazard source cutoff distance for crustal sources, in km. */
    public static final double DEFAULT_MAX_DIST = 20;

    // TectonicRegionType.ACTIVE_SHALLOW.defaultCutoffDist() / 2;

    /** Search radius in km of the distance-to-largest-rupture map. */
    public static final double DEFAULT_DISTANCE_MAP_DIST = 50;

    /** Spacing in km of the points that a section surface is sampled at for distances. */
    protected static final double SURFACE_SPACING = 2d;

    /** Differences smaller than this are drawn as no change. */
    protected static final double NO_CHANGE_MAG = 0.01;

    /** Distance differences smaller than this, in km, are drawn as no change. */
    protected static final double NO_CHANGE_KM = 0.5;

    protected static final String PREFIX = "max_mag_map";
    protected static final String DISTANCE_PREFIX = "max_mag_distance_map";

    protected final GriddedRegion region;
    protected final double maxDist;
    protected final double distanceMapDist;

    /**
     * Creates a plot on the hazard map grid with {@link #DEFAULT_MAX_DIST} and {@link
     * #DEFAULT_DISTANCE_MAP_DIST}.
     */
    public MaxMagnitudeMapPlot() {
        this(
                JointHazardInput.defaultRegion(JointHazardInput.DEFAULT_SPACING),
                DEFAULT_MAX_DIST,
                DEFAULT_DISTANCE_MAP_DIST);
    }

    /**
     * Creates a plot on a custom grid.
     *
     * @param region the grid to map
     * @param maxDist the distance in km within which ruptures count for a node on the max magnitude
     *     map
     * @param distanceMapDist the distance in km within which ruptures count for a node on the
     *     distance-to-largest-rupture map
     */
    public MaxMagnitudeMapPlot(GriddedRegion region, double maxDist, double distanceMapDist) {
        this.region = region;
        this.maxDist = maxDist;
        this.distanceMapDist = distanceMapDist;
    }

    @Override
    public String getName() {
        return "Maximum Magnitude Map";
    }

    /** A section's sample points and padded bounding box, plus the largest magnitude it is in. */
    protected static class SectionSamples {
        double maxMag;
        List<Location> points;
        double minLat, maxLat, minLon, maxLon;
    }

    /**
     * Per section, the largest magnitude of a rupture with rate > 0 that includes it. Sections not
     * in any such rupture are NaN.
     *
     * @param sol the solution
     * @return max magnitude per section
     */
    protected static double[] sectionMaxMags(FaultSystemSolution sol) {
        FaultSystemRupSet rupSet = sol.getRupSet();
        double[] result = new double[rupSet.getNumSections()];
        Arrays.fill(result, Double.NaN);
        for (int r = 0; r < rupSet.getNumRuptures(); r++) {
            if (sol.getRateForRup(r) <= 0) {
                continue;
            }
            double mag = rupSet.getMagForRup(r);
            for (int s : rupSet.getSectionsIndicesForRup(r)) {
                if (Double.isNaN(result[s]) || mag > result[s]) {
                    result[s] = mag;
                }
            }
        }
        return result;
    }

    /**
     * Samples the surfaces of all sections that are in a rupture with rate > 0, sorted by
     * descending max magnitude so that the first section in range of a node gives its value.
     */
    protected static List<SectionSamples> sampleSections(FaultSystemSolution sol, double maxDist) {
        double[] maxMags = sectionMaxMags(sol);
        List<SectionSamples> result = new ArrayList<>();
        for (FaultSection section : sol.getRupSet().getFaultSectionDataList()) {
            double maxMag = maxMags[section.getSectionId()];
            if (Double.isNaN(maxMag)) {
                continue;
            }
            SectionSamples samples = new SectionSamples();
            samples.maxMag = maxMag;
            samples.points =
                    section.getFaultSurface(SURFACE_SPACING, false, false)
                            .getEvenlyDiscritizedListOfLocsOnSurface();
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
            // pad by maxDist; a degree of longitude is never shorter than at the box's pole-most
            // latitude
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
        result.sort(Comparator.comparingDouble((SectionSamples s) -> s.maxMag).reversed());
        return result;
    }

    /**
     * Whether any sample point is within maxDist (horizontally) of the location.
     *
     * @param samples the section samples
     * @param loc the location
     * @param maxDist distance in km
     * @return true if the section is in range
     */
    protected static boolean inRange(SectionSamples samples, Location loc, double maxDist) {
        double lat = loc.getLatitude();
        double lon = loc.getLongitude();
        if (lat < samples.minLat
                || lat > samples.maxLat
                || lon < samples.minLon
                || lon > samples.maxLon) {
            return false;
        }
        for (Location point : samples.points) {
            if (LocationUtils.horzDistanceFast(point, loc) <= maxDist) {
                return true;
            }
        }
        return false;
    }

    /**
     * The horizontal distance from the location to the closest sample point of the section, if it
     * is within maxDist.
     *
     * @param samples the section samples, padded by at least maxDist
     * @param loc the location
     * @param maxDist distance in km
     * @return the distance in km, or NaN if the section is further away than maxDist
     */
    protected static double distance(SectionSamples samples, Location loc, double maxDist) {
        double lat = loc.getLatitude();
        double lon = loc.getLongitude();
        if (lat < samples.minLat
                || lat > samples.maxLat
                || lon < samples.minLon
                || lon > samples.maxLon) {
            return Double.NaN;
        }
        double min = Double.POSITIVE_INFINITY;
        for (Location point : samples.points) {
            min = Math.min(min, LocationUtils.horzDistanceFast(point, loc));
        }
        return min <= maxDist ? min : Double.NaN;
    }

    /**
     * Maps the largest magnitude of any rupture with rate > 0 that has a section within maxDist
     * (horizontal distance to the section surface) of each grid node. Nodes without such a rupture
     * are NaN.
     *
     * @param sol the solution
     * @param region the grid
     * @param maxDist distance in km
     * @return the map
     */
    public static GriddedGeoDataSet maxMagMap(
            FaultSystemSolution sol, GriddedRegion region, double maxDist) {
        List<SectionSamples> sections = sampleSections(sol, maxDist);
        GriddedGeoDataSet map = new GriddedGeoDataSet(region, false);
        double[] values = new double[region.getNodeCount()];
        IntStream.range(0, values.length)
                .parallel()
                .forEach(
                        i -> {
                            Location loc = region.getLocation(i);
                            values[i] = Double.NaN;
                            for (SectionSamples samples : sections) {
                                if (inRange(samples, loc, maxDist)) {
                                    values[i] = samples.maxMag;
                                    break;
                                }
                            }
                        });
        for (int i = 0; i < values.length; i++) {
            map.set(i, values[i]);
        }
        return map;
    }

    /**
     * Maps the distance from each grid node to the largest magnitude rupture with rate > 0 that has
     * a section within maxDist (horizontal distance to the section surface). If several ruptures
     * share that magnitude, the closest one counts. Nodes without such a rupture are NaN.
     *
     * @param sol the solution
     * @param region the grid
     * @param maxDist distance in km
     * @return the map, in km
     */
    public static GriddedGeoDataSet maxMagDistanceMap(
            FaultSystemSolution sol, GriddedRegion region, double maxDist) {
        List<SectionSamples> sections = sampleSections(sol, maxDist);
        GriddedGeoDataSet map = new GriddedGeoDataSet(region, false);
        double[] values = new double[region.getNodeCount()];
        IntStream.range(0, values.length)
                .parallel()
                .forEach(
                        i -> {
                            Location loc = region.getLocation(i);
                            double topMag = Double.NaN;
                            double minDist = Double.NaN;
                            // sections are sorted by descending max mag; stop once below the
                            // largest magnitude in range
                            for (SectionSamples samples : sections) {
                                if (!Double.isNaN(topMag) && samples.maxMag < topMag) {
                                    break;
                                }
                                double dist = distance(samples, loc, maxDist);
                                if (Double.isNaN(dist)) {
                                    continue;
                                }
                                if (Double.isNaN(topMag)) {
                                    topMag = samples.maxMag;
                                    minDist = dist;
                                } else {
                                    minDist = Math.min(minDist, dist);
                                }
                            }
                            values[i] = minDist;
                        });
        for (int i = 0; i < values.length; i++) {
            map.set(i, values[i]);
        }
        return map;
    }

    /**
     * The difference first minus second. Nodes that are NaN in either map are NaN.
     *
     * @param first the first map
     * @param second the second map
     * @return the difference map
     */
    public static GriddedGeoDataSet difference(GriddedGeoDataSet first, GriddedGeoDataSet second) {
        Preconditions.checkArgument(
                first.size() == second.size(), "maps must cover the same region");
        GriddedGeoDataSet diff = new GriddedGeoDataSet(first.getRegion(), first.isLatitudeX());
        for (int i = 0; i < first.size(); i++) {
            diff.set(i, first.get(i) - second.get(i));
        }
        return diff;
    }

    /** Finite values of a map. */
    protected static double[] finiteValues(GriddedGeoDataSet map) {
        return IntStream.range(0, map.size())
                .mapToDouble(map::get)
                .filter(Double::isFinite)
                .toArray();
    }

    /** A rainbow CPT from 0 to maxDist for distance maps. */
    protected static CPT distanceCPT(double maxDist) throws IOException {
        CPT cpt = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(0, maxDist);
        cpt.setNanColor(Color.LIGHT_GRAY);
        cpt.setBelowMinColor(cpt.getMinColor());
        cpt.setAboveMaxColor(cpt.getMaxColor());
        return cpt;
    }

    /** A rainbow CPT spanning the finite values of all maps, rounded out to 0.1. */
    protected static CPT magCPT(GriddedGeoDataSet... maps) throws IOException {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (GriddedGeoDataSet map : maps) {
            for (double value : finiteValues(map)) {
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
        }
        if (!Double.isFinite(min)) {
            min = 5;
            max = 9;
        }
        min = Math.floor(min * 10) / 10;
        max = Math.ceil(max * 10) / 10;
        if (max <= min) {
            max = min + 0.1;
        }
        CPT cpt = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(min, max);
        cpt.setNanColor(Color.LIGHT_GRAY);
        cpt.setBelowMinColor(cpt.getMinColor());
        cpt.setAboveMaxColor(cpt.getMaxColor());
        return cpt;
    }

    /**
     * A diverging CPT symmetric around zero, fitted to the largest absolute difference.
     *
     * @param diff the difference map
     * @param noChange differences smaller than this are drawn as no change
     * @param emptyExtreme the range to use if nothing changed
     * @return the CPT
     */
    protected static CPT diffCPT(GriddedGeoDataSet diff, double noChange, double emptyExtreme)
            throws IOException {
        double extreme = 0;
        for (double value : finiteValues(diff)) {
            extreme = Math.max(extreme, Math.abs(value));
        }
        if (extreme <= noChange) {
            extreme = emptyExtreme;
        }
        CPT cpt =
                DivergingCPT.ramp(GMT_CPT_Files.DIVERGING_VIK_UNIFORM.instance(), -extreme, extreme)
                        .zeroBand(noChange)
                        .build();
        cpt.setNanColor(Color.LIGHT_GRAY);
        cpt.setBelowMinColor(cpt.getMinColor());
        cpt.setAboveMaxColor(cpt.getMaxColor());
        return cpt;
    }

    /** Min, mean and max of a map. */
    protected static String mapStats(GriddedGeoDataSet map) {
        double[] values = finiteValues(map);
        if (values.length == 0) {
            return "no ruptures in range";
        }
        DoubleSummaryStatistics stats = Arrays.stream(values).summaryStatistics();
        return String.format(
                "min %.2f, mean %.2f, max %.2f",
                stats.getMin(), stats.getAverage(), stats.getMax());
    }

    /**
     * Counts of nodes that increased and decreased by more than noChange, and the extremes of a
     * difference map.
     */
    protected static String diffStats(GriddedGeoDataSet diff, double noChange) {
        double[] values = finiteValues(diff);
        long up = Arrays.stream(values).filter(v -> v > noChange).count();
        long down = Arrays.stream(values).filter(v -> v < -noChange).count();
        DoubleSummaryStatistics stats = Arrays.stream(values).summaryStatistics();
        return String.format(
                "%d nodes up, %d down of %d; min %.2f, max %.2f",
                up,
                down,
                values.length,
                values.length == 0 ? 0 : stats.getMin(),
                values.length == 0 ? 0 : stats.getMax());
    }

    /** A map calculator used only for plotting, so that maps look like the hazard maps. */
    protected SolHazardMapCalc plotter(FaultSystemSolution sol) {
        return new SolHazardMapCalc(
                sol, (Supplier<ScalarIMR>) null, region, IncludeBackgroundOption.EXCLUDE, 0d);
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

        if (sol == null) return null;

        FaultSystemSolution compSol =
                meta != null && meta.hasComparison() ? meta.comparison.sol : null;

        List<String> lines = new ArrayList<>();
        lines.add(
                "Largest magnitude of any rupture with a non-zero rate that has a section within "
                        + (int) maxDist
                        + " km (horizontal distance to the section surface) of each grid node.");
        lines.add("");
        lines.addAll(
                mapTable(
                        sol,
                        compSol,
                        meta,
                        s -> maxMagMap(s, region, maxDist),
                        MaxMagnitudeMapPlot::magCPT,
                        diff -> diffCPT(diff, NO_CHANGE_MAG, 0.5),
                        NO_CHANGE_MAG,
                        PREFIX,
                        "Max magnitude within " + (int) maxDist + " km",
                        "Max magnitude difference",
                        resourcesDir,
                        relPathToResources));
        lines.add("");
        lines.add(
                "Distance (km) from each grid node to the largest magnitude rupture with a non-zero"
                        + " rate that has a section within "
                        + (int) distanceMapDist
                        + " km (horizontal distance to the section surface).");
        lines.add("");
        lines.addAll(
                mapTable(
                        sol,
                        compSol,
                        meta,
                        s -> maxMagDistanceMap(s, region, distanceMapDist),
                        maps -> distanceCPT(distanceMapDist),
                        diff -> diffCPT(diff, NO_CHANGE_KM, 10),
                        NO_CHANGE_KM,
                        DISTANCE_PREFIX,
                        "Distance (km) to largest rupture within " + (int) distanceMapDist + " km",
                        "Distance difference (km)",
                        resourcesDir,
                        relPathToResources));
        return lines;
    }

    /** A function that may throw an IOException. */
    protected interface IOFunction<T, R> {
        R apply(T t) throws IOException;
    }

    /**
     * Calculates and plots one kind of map for the primary solution and, if present, the comparison
     * solution plus a difference map.
     *
     * @param sol the primary solution
     * @param compSol the comparison solution, may be null
     * @param meta the report metadata, may be null
     * @param calc calculates the map for a solution
     * @param cpt creates the CPT shared by primary and comparison map
     * @param diffCpt creates the CPT for the difference map
     * @param noChange differences smaller than this count as no change in the stats
     * @param prefix file name prefix
     * @param zLabel colour bar label
     * @param diffLabel colour bar label of the difference map
     * @param resourcesDir output directory
     * @param relPathToResources relative path to the output directory
     * @return markdown table lines
     */
    protected List<String> mapTable(
            FaultSystemSolution sol,
            FaultSystemSolution compSol,
            ReportMetadata meta,
            Function<FaultSystemSolution, GriddedGeoDataSet> calc,
            IOFunction<GriddedGeoDataSet[], CPT> cpt,
            IOFunction<GriddedGeoDataSet, CPT> diffCpt,
            double noChange,
            String prefix,
            String zLabel,
            String diffLabel,
            File resourcesDir,
            String relPathToResources)
            throws IOException {
        GriddedGeoDataSet map = calc.apply(sol);
        GriddedGeoDataSet compMap = compSol == null ? null : calc.apply(compSol);
        CPT mapCpt =
                cpt.apply(
                        compMap == null
                                ? new GriddedGeoDataSet[] {map}
                                : new GriddedGeoDataSet[] {map, compMap});

        List<String> headers = new ArrayList<>();
        List<String> images = new ArrayList<>();
        List<String> stats = new ArrayList<>();

        String primaryName = meta == null ? "Primary" : meta.primary.name;
        plotter(sol).plotMap(resourcesDir, prefix, map, mapCpt, primaryName, zLabel);
        headers.add(primaryName);
        images.add(prefix);
        stats.add(mapStats(map));

        if (compMap != null) {
            String compPrefix = prefix + "_comparison";
            plotter(compSol)
                    .plotMap(
                            resourcesDir,
                            compPrefix,
                            compMap,
                            mapCpt,
                            meta.comparison.name,
                            zLabel);
            headers.add(meta.comparison.name);
            images.add(compPrefix);
            stats.add(mapStats(compMap));

            GriddedGeoDataSet diff = difference(map, compMap);
            String diffPrefix = prefix + "_diff";
            plotter(sol)
                    .plotMap(
                            resourcesDir,
                            diffPrefix,
                            diff,
                            diffCpt.apply(diff),
                            "Primary - Comparison",
                            diffLabel);
            headers.add("Difference");
            images.add(diffPrefix);
            stats.add(diffStats(diff, noChange));
        }

        TableBuilder table = MarkdownUtils.tableBuilder();
        table.addLine(headers);
        table.initNewLine();
        for (String image : images) {
            table.addColumn("![" + image + "](" + relPathToResources + "/" + image + ".png)");
        }
        table.finalizeLine();
        table.addLine(stats);
        return table.build();
    }

    @Override
    public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
        return null;
    }
}

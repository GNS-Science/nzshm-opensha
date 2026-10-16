package nz.cri.gns.NZSHM22.opensha.inversion.joint.reporting;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.RupSetMetadata;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;

/** Tests for {@link MaxMagnitudeMapPlot}. */
public class MaxMagnitudeMapPlotTest {

    static final double DELTA = 1e-9;
    static final double MAX_DIST = 50;

    @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

    protected static FaultSection makeSection(int id, double lon) {
        FaultTrace trace = new FaultTrace("trace");
        trace.add(new Location(-41, lon));
        trace.add(new Location(-41.2, lon));
        FaultSectionPrefData pref = new FaultSectionPrefData();
        pref.setSectionId(id);
        pref.setSectionName("Section " + id);
        pref.setFaultTrace(trace);
        pref.setAveDip(90);
        pref.setAveRake(90);
        pref.setAveUpperDepth(0);
        pref.setAveLowerDepth(10);
        pref.setDipDirection((float) trace.getDipDirection());
        return GeoJSONFaultSection.fromFaultSection(pref);
    }

    /** Two sections ~250 km apart; rupture 2 has no rate. */
    protected static FaultSystemSolution makeSolution(double[] rates) {
        List<FaultSection> sections = List.of(makeSection(0, 173), makeSection(1, 176));
        FaultSystemRupSet rupSet =
                FaultSystemRupSet.builder(sections, List.of(List.of(0), List.of(1), List.of(1)))
                        .rupMags(new double[] {6.5, 7.0, 8.0})
                        .build();
        return new FaultSystemSolution(rupSet, rates);
    }

    protected static GriddedRegion region() {
        return new GriddedRegion(
                new Location(-42, 172), new Location(-40, 177), 0.5, GriddedRegion.ANCHOR_0_0);
    }

    protected static double valueAt(GriddedGeoDataSet map, double lat, double lon) {
        return map.get(map.getRegion().indexForLocation(new Location(lat, lon)));
    }

    /** Returns null when no solution is provided. */
    @Test
    public void testNullSolution() throws Exception {
        FaultSystemRupSet rupSet = mock(FaultSystemRupSet.class);
        assertNull(
                new MaxMagnitudeMapPlot()
                        .plot(rupSet, null, (ReportMetadata) null, null, null, null));
    }

    /** Nodes get the max mag of ruptures with rate in range, NaN if there are none. */
    @Test
    public void testMaxMagMap() {
        FaultSystemSolution sol = makeSolution(new double[] {1e-3, 1e-4, 0});
        GriddedGeoDataSet map = MaxMagnitudeMapPlot.maxMagMap(sol, region(), MAX_DIST);

        assertEquals(6.5, valueAt(map, -41, 173), DELTA);
        // rupture 2 (M8) has no rate
        assertEquals(7.0, valueAt(map, -41, 176), DELTA);
        // ~125 km from both sections
        assertTrue(Double.isNaN(valueAt(map, -41, 174.5)));

        FaultSystemSolution withRate = makeSolution(new double[] {1e-3, 1e-4, 1e-5});
        map = MaxMagnitudeMapPlot.maxMagMap(withRate, region(), MAX_DIST);
        assertEquals(8.0, valueAt(map, -41, 176), DELTA);
    }

    /**
     * The distance map measures the distance to the largest rupture in range, even if a smaller one
     * is closer.
     */
    @Test
    public void testMaxMagDistanceMap() {
        List<FaultSection> sections =
                List.of(makeSection(0, 173), makeSection(1, 176), makeSection(2, 173.3));
        FaultSystemRupSet rupSet =
                FaultSystemRupSet.builder(
                                sections, List.of(List.of(0), List.of(1), List.of(2), List.of(2)))
                        .rupMags(new double[] {6.5, 7.0, 7.5, 8.0})
                        .build();
        // the M8 rupture has no rate
        FaultSystemSolution sol = new FaultSystemSolution(rupSet, new double[] {1, 1, 1, 0});
        GriddedGeoDataSet map = MaxMagnitudeMapPlot.maxMagDistanceMap(sol, region(), MAX_DIST);

        // 0.3 degrees of longitude at 41S to the M7.5 section, not 0 km to the M6.5 section
        double expected =
                LocationUtils.horzDistance(new Location(-41, 173), new Location(-41, 173.3));
        assertEquals(expected, valueAt(map, -41, 173), 0.5);
        assertEquals(0, valueAt(map, -41, 176), 0.5);
        assertTrue(Double.isNaN(valueAt(map, -41, 174.5)));
    }

    /** Difference is first minus second. */
    @Test
    public void testDifference() {
        GriddedGeoDataSet first =
                MaxMagnitudeMapPlot.maxMagMap(
                        makeSolution(new double[] {1e-3, 1e-4, 1e-5}), region(), MAX_DIST);
        GriddedGeoDataSet second =
                MaxMagnitudeMapPlot.maxMagMap(
                        makeSolution(new double[] {1e-3, 1e-4, 0}), region(), MAX_DIST);
        GriddedGeoDataSet diff = MaxMagnitudeMapPlot.difference(first, second);
        assertEquals(1.0, valueAt(diff, -41, 176), DELTA);
        assertEquals(0.0, valueAt(diff, -41, 173), DELTA);
        assertTrue(Double.isNaN(valueAt(diff, -41, 174.5)));
    }

    /** Writes primary, comparison and difference maps when there is a comparison. */
    @Test
    public void testPlotWithComparison() throws Exception {
        FaultSystemSolution sol = makeSolution(new double[] {1e-3, 1e-4, 1e-5});
        FaultSystemSolution comp = makeSolution(new double[] {1e-3, 1e-4, 0});
        ReportMetadata meta =
                new ReportMetadata(
                        new RupSetMetadata("primary", sol), new RupSetMetadata("comp", comp));

        File resourcesDir = tempFolder.newFolder("resources");
        List<String> lines =
                new MaxMagnitudeMapPlot(region(), MAX_DIST, MAX_DIST)
                        .plot(sol.getRupSet(), sol, meta, resourcesDir, "resources", "");

        String allText = String.join("\n", lines);
        for (String name :
                List.of(
                        "max_mag_map",
                        "max_mag_map_comparison",
                        "max_mag_map_diff",
                        "max_mag_distance_map",
                        "max_mag_distance_map_comparison",
                        "max_mag_distance_map_diff")) {
            assertTrue(name, allText.contains("resources/" + name + ".png"));
            assertTrue(name, new File(resourcesDir, name + ".png").exists());
        }
    }
}

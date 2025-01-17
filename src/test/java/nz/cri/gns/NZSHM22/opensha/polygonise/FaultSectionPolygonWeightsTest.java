package nz.cri.gns.NZSHM22.opensha.polygonise;

import static com.google.common.base.Preconditions.checkArgument;
import static nz.cri.gns.NZSHM22.opensha.griddedSeismicity.SectionPolygons.merge;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.geom.Area;
import java.util.List;
import nz.cri.gns.NZSHM22.opensha.griddedSeismicity.SectionPolygons;
import org.junit.Test;
import org.opensha.commons.geo.*;
import org.opensha.sha.earthquake.faultSysSolution.modules.PolygonFaultGridAssociations;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;

public class FaultSectionPolygonWeightsTest {

    public static Area buildPoly(LocationList trace, double dipDir, double bufA, double bufB) {
        checkArgument(trace.size() > 1);
        Area buffer = null;
        for (int i = 1; i < trace.size(); i++) {
            Location a = trace.get(i - 1);
            Location b = trace.get(i);
            LocationList points = new LocationList();
            LocationVector v = new LocationVector(dipDir, bufA, 0);

            points.add(a);
            points.add(LocationUtils.location(a, v));
            points.add(LocationUtils.location(b, v));
            points.add(b);
            v = new LocationVector(dipDir + 180, bufB, 0);
            points.add(LocationUtils.location(b, v));
            points.add(LocationUtils.location(a, v));
            buffer = merge(buffer, new Area(points.toPath()));
        }
        return buffer;
    }

    protected FaultSectionPolygonWeights mockWeights(
            FaultTrace trace, float dipDirection, double bufferSizeA, double bufferSizeB) {
        Area buffer = buildPoly(trace, dipDirection, bufferSizeA, bufferSizeB);
        LocationList locs = SectionPolygons.areaToLocLists(buffer).get(0);
        Region poly = new Region(locs, null);

        PolygonFaultGridAssociations polys = mock(PolygonFaultGridAssociations.class);
        when(polys.getPoly(1913)).thenReturn(poly);

        FaultSection section = mock(FaultSection.class);
        when(section.getSectionId()).thenReturn(1913);
        when(section.getFaultTrace()).thenReturn(trace);
        when(section.getDipDirection()).thenReturn(dipDirection);

        return new FaultSectionPolygonWeights(polys, section);
    }

    protected List<Location> createTestPoints(
            FaultTrace trace, float dipDirection, double bufferSizeA, double bufferSizeB) {

        double distance = LocationUtils.horzDistance(trace.first(), trace.last());
        double azimuth = LocationUtils.azimuth(trace.first(), trace.last());
        LocationVector v = new LocationVector(azimuth, distance / 2, 0);
        Location midPoint = LocationUtils.location(trace.first(), v);

        LocationList points = new LocationList();
        points.add(
                LocationUtils.location(
                        midPoint, new LocationVector(dipDirection, bufferSizeA + 10, 0)));
        // subtract a little to make sure we're inside the polygon and don't get a -1 result
        points.add(
                LocationUtils.location(
                        midPoint, new LocationVector(dipDirection, bufferSizeA - 0.01, 0)));
        points.add(
                LocationUtils.location(
                        midPoint, new LocationVector(dipDirection, bufferSizeA / 2, 0)));
        points.add(
                LocationUtils.location(
                        midPoint, new LocationVector(dipDirection, bufferSizeA / 4, 0)));
        points.add(LocationUtils.location(midPoint, new LocationVector(dipDirection, 0.01, 0)));
        // points.add(midPoint);
        if (bufferSizeB > 0) {
            points.add(
                    LocationUtils.location(
                            midPoint, new LocationVector(dipDirection - 180, bufferSizeB / 4, 0)));
            points.add(
                    LocationUtils.location(
                            midPoint, new LocationVector(dipDirection - 180, bufferSizeB / 2, 0)));
            // subtract a little to make sure we're inside the polygon and don't get a -1 result
            points.add(
                    LocationUtils.location(
                            midPoint,
                            new LocationVector(dipDirection - 180, bufferSizeB - 0.01, 0)));
            points.add(
                    LocationUtils.location(
                            midPoint, new LocationVector(dipDirection - 180, bufferSizeB + 10, 0)));
        }
        return points;
    }

    protected void testWeights(
            FaultTrace trace, float dipDirection, double bufferSizeA, double bufferSizeB) {
        FaultSectionPolygonWeights weights =
                mockWeights(trace, dipDirection, bufferSizeA, bufferSizeB);
        List<Location> testPoints = createTestPoints(trace, dipDirection, bufferSizeA, bufferSizeB);
        assertEquals(-1, weights.polygonWeight(testPoints.get(0)), 0);
        assertEquals(0.99, weights.polygonWeight(testPoints.get(1)), 0.01);
        assertEquals(0.5, weights.polygonWeight(testPoints.get(2)), 0.001);
        assertEquals(0.25, weights.polygonWeight(testPoints.get(3)), 0.001);
        assertEquals(0, weights.polygonWeight(testPoints.get(4)), 0.01);
        if (bufferSizeB > 0) {
            assertEquals(0.25, weights.polygonWeight(testPoints.get(5)), 0.001);
            assertEquals(0.5, weights.polygonWeight(testPoints.get(6)), 0.001);
            assertEquals(0.99, weights.polygonWeight(testPoints.get(7)), 0.01);
            assertEquals(-1, weights.polygonWeight(testPoints.get(8)), 0);
        }
    }

    @Test
    public void testWeights() {
        FaultTrace trace = new FaultTrace("Wairau, Subsection 5");
        trace.add(new Location(-41.550152904593155, 173.62635188232747));
        trace.add(new Location(-41.580351942653, 173.51523042970382));
        testWeights(trace, 0, 5, 5);
        testWeights(trace, 90, 5, 5);
        testWeights(trace, 270, 5, 5);

        trace = new FaultTrace("Test fault");
        trace.add(new Location(-41.8123, 173.1665));
        trace.add(new Location(-41.8696, 173.1638));
        testWeights(trace, 0, 5, 5);
        testWeights(trace, 60, 5, 5);
        testWeights(trace, 240, 5, 5);
    }

    @Test
    public void testWeightsEmptyBuffer() {
        // Test with trace being identical to one side of the polygon
        FaultTrace trace = new FaultTrace("Wairau, Subsection 5");
        trace.add(new Location(-41.550152904593155, 173.62635188232747));
        trace.add(new Location(-41.580351942653, 173.51523042970382));
        testWeights(trace, 0, 5, 0);
        testWeights(trace, 90, 5, 0);
        testWeights(trace, 270, 5, 0);

        trace = new FaultTrace("Test fault");
        trace.add(new Location(-41.8123, 173.1665));
        trace.add(new Location(-41.8696, 173.1638));
        testWeights(trace, 0, 5, 0);
        testWeights(trace, 60, 5, 0);
        testWeights(trace, 240, 5, 0);
    }

    @Test
    public void testAzimuthDifference() {
        assertEquals(0, FaultSectionPolygonWeights.azimuthDifference(0, 0), 0);
        assertEquals(0, FaultSectionPolygonWeights.azimuthDifference(0, 360), 0);
        assertEquals(180, FaultSectionPolygonWeights.azimuthDifference(0, 180), 0);
        assertEquals(180, FaultSectionPolygonWeights.azimuthDifference(360, 540), 0);
        assertEquals(90, FaultSectionPolygonWeights.azimuthDifference(0, 90), 0);
        assertEquals(90, FaultSectionPolygonWeights.azimuthDifference(270, 0), 0);
        assertEquals(180, FaultSectionPolygonWeights.azimuthDifference(270, 90), 0);
    }
}

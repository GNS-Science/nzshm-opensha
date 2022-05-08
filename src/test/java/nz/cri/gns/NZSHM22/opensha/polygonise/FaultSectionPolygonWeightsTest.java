package nz.cri.gns.NZSHM22.opensha.polygonise;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import nz.cri.gns.NZSHM22.opensha.griddedSeismicity.SectionPolygons;
import nz.cri.gns.NZSHM22.opensha.util.SimpleGeoJsonBuilder;
import org.junit.Test;
import org.opensha.commons.geo.*;
import org.opensha.sha.earthquake.faultSysSolution.modules.PolygonFaultGridAssociations;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;

import java.awt.geom.Area;
import java.util.List;

public class FaultSectionPolygonWeightsTest {

    protected FaultSectionPolygonWeights mockWeights(FaultTrace trace, float dipDirection, double bufferSize) {
        Area buffer = SectionPolygons.buildBufferPoly(trace, dipDirection, bufferSize);
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

    protected List<Location> createTestPoints(FaultTrace trace, float dipDirection, double bufferSize) {

        double distance = LocationUtils.horzDistance(trace.first(), trace.last());
        double azimuth = LocationUtils.azimuth(trace.first(), trace.last());
        LocationVector v = new LocationVector(azimuth, distance / 2, 0);
        Location midPoint = LocationUtils.location(trace.first(), v);

        LocationList points = new LocationList();
        points.add(LocationUtils.location(midPoint, new LocationVector(dipDirection, bufferSize + 10, 0)));
        // subtract a little to make sure we're inside the polygon and don't get a -1 result
        points.add(LocationUtils.location(midPoint, new LocationVector(dipDirection, bufferSize - 0.01, 0)));
        points.add(LocationUtils.location(midPoint, new LocationVector(dipDirection, bufferSize / 2, 0)));
        points.add(midPoint);
        points.add(LocationUtils.location(midPoint, new LocationVector(dipDirection - 180, bufferSize / 2, 0)));
        // subtract a little to make sure we're inside the polygon and don't get a -1 result
        points.add(LocationUtils.location(midPoint, new LocationVector(dipDirection - 180, bufferSize - 0.01, 0)));
        points.add(LocationUtils.location(midPoint, new LocationVector(dipDirection - 180, bufferSize + 10, 0)));

        return points;
    }

    protected void testWeights(FaultTrace trace, float dipDirection, double bufferSize) {
        FaultSectionPolygonWeights weights = mockWeights(trace, dipDirection, bufferSize);
        List<Location> testPoints = createTestPoints(trace, dipDirection, bufferSize);
        assertEquals(-1, weights.polygonWeight(testPoints.get(0)), 0);
        assertEquals(0.99, weights.polygonWeight(testPoints.get(1)), 0.01);
        assertEquals(0.5, weights.polygonWeight(testPoints.get(2)), 0.001);
        assertEquals(0, weights.polygonWeight(testPoints.get(3)), 0.001);
        assertEquals(0.5, weights.polygonWeight(testPoints.get(4)), 0.001);
        assertEquals(0.99, weights.polygonWeight(testPoints.get(5)), 0.01);
        assertEquals(-1, weights.polygonWeight(testPoints.get(6)), 0);
    }

    @Test
    public void testWeights() {
        FaultTrace trace = new FaultTrace("Wairau, Subsection 5");
        trace.add(new Location(-41.550152904593155, 173.62635188232747));
        trace.add(new Location(-41.580351942653, 173.51523042970382));
        testWeights(trace, 0, 5);
        testWeights(trace, 90, 5);
        testWeights(trace, 270, 5);

        trace = new FaultTrace("Test fault");
        trace.add(new Location(-41.8123, 173.1665));
        trace.add(new Location(-41.8696, 173.1638));
        testWeights(trace, 0, 5);
        testWeights(trace, 60, 5);
        testWeights(trace, 240, 5);
    }

}

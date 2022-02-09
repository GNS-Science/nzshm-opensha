package nz.cri.gns.NZSHM22.opensha.polygonise;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;
import org.opensha.sha.earthquake.faultSysSolution.modules.PolygonFaultGridAssociations;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;

public class FaultSectionPolygonWeightsTest {

    @Test
    public void testit() {
        LocationList border = new LocationList();
        border.add(new Location(-41.66812150917695, 173.56186777508947));
        border.add(new Location(-41.63806206362965, 173.67245409462691));
        border.add(new Location(-41.63792233637375, 173.67296666402535));
        border.add(new Location(-41.550152904593155, 173.62635188232747));
        border.add(new Location(-41.462090899528896, 173.58086841734828));
        border.add(new Location(-41.46222538215771, 173.58037489560454));
        border.add(new Location(-41.49229006982083, 173.4697264355234));
        border.add(new Location(-41.580351942653, 173.51523042970382));
        border.add(new Location(-41.59522260832782, 173.52302320164392));
        border.add(new Location(-41.59519674117919, 173.52311838020265));
        border.add(new Location(-41.59519674117919, 173.52311838020267));
        border.add(new Location(-41.66812150917695, 173.56186777508947));

        FaultTrace trace = new FaultTrace("Wairau, Subsection 5");
        trace.add(new Location(-41.550152904593155, 173.62635188232747));
        trace.add(new Location(-41.580351942653, 173.51523042970382));

        LocationList testLocations = new LocationList();
        testLocations.add(new Location(-41.4787, 173.5222));
        testLocations.add(new Location(-41.5178, 173.5466));
        testLocations.add(new Location(-41.5651, 173.5709));
        testLocations.add(new Location(-41.6075, 173.5967));
        testLocations.add(new Location(-41.6529, 173.6176));

        Region poly = mock(Region.class);
        when(poly.getBorder()).thenReturn(border);
        for (Location l : testLocations) {
            when(poly.contains(l)).thenReturn(true);
        }
        PolygonFaultGridAssociations polys = mock(PolygonFaultGridAssociations.class);
        when(polys.getPoly(1913)).thenReturn(poly);

        FaultSection section = mock(FaultSection.class);
        when(section.getSectionId()).thenReturn(1913);
        when(section.getFaultTrace()).thenReturn(trace);

        FaultSectionPolygonWeights weights = new FaultSectionPolygonWeights(polys, section);

        // points going from one edge of the polygon to the other, crossing the trace
        assertEquals(0.9935167072944777, weights.polygonWeight(testLocations.get(0)), 0.000001);
        assertEquals(0.538127023652279, weights.polygonWeight(testLocations.get(1)), 0.000001);
        assertEquals(0.0013914181232540716, weights.polygonWeight(testLocations.get(2)), 0.000001);
        assertEquals(0.4905767422892837, weights.polygonWeight(testLocations.get(3)), 0.000001);
        assertEquals(0.9991120580834484, weights.polygonWeight(testLocations.get(4)), 0.000001);
    }
}

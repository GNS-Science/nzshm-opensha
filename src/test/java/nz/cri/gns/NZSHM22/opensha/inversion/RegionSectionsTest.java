package nz.cri.gns.NZSHM22.opensha.inversion;

import org.junit.Test;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.faultSurface.FaultSection;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class RegionSectionsTest {

    public FaultSystemRupSet modularRupSet() throws URISyntaxException, IOException {
        URL alpineVernonRupturesUrl = Thread.currentThread().getContextClassLoader().getResource("ModularAlpineVernonInversionSolution.zip");
        return FaultSystemSolution.load(new File(alpineVernonRupturesUrl.toURI())).getRupSet();
    }

    static GriddedRegion testRegion = new GriddedRegion(
            new Location(-42.48019996901214, 172.496337890625),
            new Location(-41.186922422902946, 174.781494140625),
            0.1, 0.1, new Location(0, 0));

    static class TestRegionSections extends RegionSections {
        public TestRegionSections(FaultSystemRupSet rupSet) {
            super(rupSet, testRegion);
        }
    }

    @Test
    public void testFilter() throws URISyntaxException, IOException {
        FaultSystemRupSet rupSet = modularRupSet();
        assertEquals(86, rupSet.getNumSections());

        TestRegionSections regionSections = new TestRegionSections(rupSet);
        assertEquals(34, regionSections.getSections().size());
    }

}

package nz.cri.gns.NZSHM22.opensha.griddedSeismicity;

import static org.junit.Assert.*;

import nz.cri.gns.NZSHM22.opensha.data.region.NewZealandRegions;
import org.junit.Test;
import org.opensha.commons.geo.Location;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NZSHM22_GridSourceGeneratorTest {

    @Test
    public void testFocalMechs() {
        NZSHM22_GriddedData strikeGrid =
                NZSHM22_GriddedData.fromFile("seismicityGrids/strikeFocalHazMech.grid");
        NZSHM22_GriddedData reverseGrid =
                NZSHM22_GriddedData.fromFile("seismicityGrids/reverseFocalMech.grid");
        NZSHM22_GriddedData normalGrid =
                NZSHM22_GriddedData.fromFile("seismicityGrids/normalFocalMech.grid");

        List<Location> focalGridLocations = new ArrayList<>();
        // using NZ_RECTANGLE_GRIDDED here because it's larger than the data, so that we don't miss any
        for (Location location : new NewZealandRegions.NZ_RECTANGLE_GRIDDED()) {
            Double strike = strikeGrid.getValue(location);
            Double reverse = reverseGrid.getValue(location);
            Double normal = normalGrid.getValue(location);

            double value = strike == null ? 0 : strike;
            value += reverse == null ? 0 : reverse;
            value += normal == null ? 0 : normal;
            assertTrue(value == 1 || value == 0);
            if(value == 1) {
                focalGridLocations.add(location);
            }
        }
        Collections.sort(focalGridLocations);

        List<Location> nzRegion = new ArrayList<>(NewZealandRegions.NZ.getNodeList());
        Collections.sort(nzRegion);

        assertEquals(nzRegion.size(), focalGridLocations.size());

        // assert that the NZ region is exactly the region covered by the focal mech grids
        for(int i = 0 ; i < nzRegion.size(); i++) {
            assertEquals(nzRegion.get(i), focalGridLocations.get(i));
        }
    }
}

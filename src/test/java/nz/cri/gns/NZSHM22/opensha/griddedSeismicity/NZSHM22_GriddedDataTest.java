package nz.cri.gns.NZSHM22.opensha.griddedSeismicity;

import nz.cri.gns.NZSHM22.opensha.data.region.NewZealandRegions;
import org.junit.Test;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;

import static org.junit.Assert.*;

public class NZSHM22_GriddedDataTest {

    public static final double tolerance = 0.000000001;

    @Test
    public void testNormaliseRegion(){
        NZSHM22_GriddedData data = new NZSHM22_GriddedData("BEST2FLTOLDNC1246.txt");
        GriddedRegion region = new NewZealandRegions.NZ_TVZ_GRIDDED();

        Location testLocation = region.getLocation(5);
        double locationPdf = data.getValue(testLocation);
        double fraction = data.getFractionInRegion(region);

        assertEquals(5.553E-5, locationPdf, tolerance);
        assertEquals(0.03214175, fraction, tolerance);

        data.normaliseRegion(region);

        assertEquals( locationPdf / fraction, data.getValue(testLocation), tolerance );
        assertEquals( 1, data.getFractionInRegion(region), tolerance);
    }

    @Test
    public void testNormaliseMultipleRegions(){
        // note: the regions may not share nodes
        NZSHM22_GriddedData data = new NZSHM22_GriddedData("BEST2FLTOLDNC1246.txt");
        GriddedRegion tvz = new NewZealandRegions.NZ_TVZ_GRIDDED();
        GriddedRegion sansTvz = new NewZealandRegions.NZ_RECTANGLE_SANS_TVZ_GRIDDED();

        Location tvzLocation = tvz.getLocation(5);
        double tnzPdf = data.getValue(tvzLocation);
        double tvzFraction = data.getFractionInRegion(tvz);

        assertEquals(5.553E-5, tnzPdf, tolerance);
        assertEquals(0.03214175, tvzFraction, tolerance);

        Location sansLocation = sansTvz.getLocation(4624);
        double sansPdf = data.getValue(sansLocation);
        double sansFraction = data.getFractionInRegion(sansTvz);

        assertEquals(5.972E-6, sansPdf, tolerance);
        assertEquals(0.9678601200000014, sansFraction, tolerance);

        data.normaliseRegion(tvz);
        data.normaliseRegion(sansTvz);

        assertEquals( tnzPdf / tvzFraction, data.getValue(tvzLocation), tolerance );
        assertEquals( 1, data.getFractionInRegion(tvz), tolerance);

        assertEquals( sansPdf / sansFraction, data.getValue(sansLocation), tolerance );
        assertEquals( 1, data.getFractionInRegion(sansTvz), tolerance);

    }
}

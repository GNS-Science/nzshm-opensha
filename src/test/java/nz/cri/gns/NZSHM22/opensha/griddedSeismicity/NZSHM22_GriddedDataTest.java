package nz.cri.gns.NZSHM22.opensha.griddedSeismicity;

import nz.cri.gns.NZSHM22.opensha.data.region.NewZealandRegions;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_SpatialSeisPDF;
import org.junit.Test;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;

import java.io.*;

import static org.junit.Assert.*;

public class NZSHM22_GriddedDataTest {

    public static final double tolerance = 0.000000001;

    @Test
    public void testNormaliseRegion(){
        NZSHM22_GriddedData data = NZSHM22_GriddedData.fromFileNativeStep("seismicityGrids/BEST2FLTOLDNC1246.txt");
        // create a gridded region that matches the spacing of the grid
        GriddedRegion region = new GriddedRegion(new NewZealandRegions.NZ_TVZ(), 1.0 / data.getStep(), GriddedRegion.ANCHOR_0_0);

        Location testLocation = region.getLocation(5);
        double locationPdf = data.getValue(testLocation);
        double fraction = data.getFractionInRegion(region);

        assertEquals(5.089E-5, locationPdf, tolerance);
        assertEquals(0.03984544, fraction, tolerance);

        data.normaliseRegion(region);

        assertEquals( locationPdf / fraction, data.getValue(testLocation), tolerance );
        assertEquals( 1, data.getFractionInRegion(region), tolerance);
    }

    @Test
    public void testNormaliseMultipleRegions(){
        // note: the regions may not share nodes
        NZSHM22_GriddedData data = NZSHM22_GriddedData.fromFileNativeStep("seismicityGrids/BEST2FLTOLDNC1246.txt");
        GriddedRegion tvz = new GriddedRegion(new NewZealandRegions.NZ_TVZ(), 1.0 / data.getStep(), GriddedRegion.ANCHOR_0_0);
        GriddedRegion sansTvz = new GriddedRegion(new NewZealandRegions.NZ_RECTANGLE_SANS_TVZ(), 1.0 / data.getStep(), GriddedRegion.ANCHOR_0_0);

        Location tvzLocation = tvz.getLocation(5);
        double tnzPdf = data.getValue(tvzLocation);
        double tvzFraction = data.getFractionInRegion(tvz);

        assertEquals(5.089E-5, tnzPdf, tolerance);
        assertEquals(0.03984544, tvzFraction, tolerance);

        Location sansLocation = sansTvz.getLocation(4624);
        double sansPdf = data.getValue(sansLocation);
        double sansFraction = data.getFractionInRegion(sansTvz);

        assertEquals(5.972E-6, sansPdf, tolerance);
        assertEquals(0.9601564300000021, sansFraction, tolerance);

        data.normaliseRegion(tvz);
        data.normaliseRegion(sansTvz);

        assertEquals( tnzPdf / tvzFraction, data.getValue(tvzLocation), tolerance );
        assertEquals( 1, data.getFractionInRegion(tvz), tolerance);

        assertEquals( sansPdf / sansFraction, data.getValue(sansLocation), tolerance );
        assertEquals( 1, data.getFractionInRegion(sansTvz), tolerance);

    }

    @Test
    public void testSerialisation() throws IOException {
        NZSHM22_GriddedData original = NZSHM22_SpatialSeisPDF.NZSHM22_1346.getGriddedData();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        original.writeToStream(new BufferedOutputStream(out));
        out.close();
        NZSHM22_GriddedData actual = new NZSHM22_GriddedData();
        actual.initFromStream(new BufferedInputStream(new ByteArrayInputStream(out.toByteArray())));

        GriddedRegion region = new NewZealandRegions.NZ_RECTANGLE_GRIDDED();

        assertArrayEquals(original.getValues(region), actual.getValues(region), 0.000000000000001);
    }

    @Test
    public void testUpsampling(){
        NZSHM22_GriddedData data = NZSHM22_GriddedData.fromFileNativeStep("seismicityGrids/BEST2FLTOLDNC1246.txt");
        assertEquals(10, data.getStep(), 0);

        NZSHM22_GriddedData upSampled = new NZSHM22_GriddedData(data, 100);

        GriddedRegion nz100 = new GriddedRegion(new NewZealandRegions.NZ_RECTANGLE(), 1.0 / 100, GriddedRegion.ANCHOR_0_0);

        double[] values10 = data.getValues(nz100);
        double[] values100 = upSampled.getValues(nz100);

        assertArrayEquals(values10, values100, 0.000000000001);
    }
}

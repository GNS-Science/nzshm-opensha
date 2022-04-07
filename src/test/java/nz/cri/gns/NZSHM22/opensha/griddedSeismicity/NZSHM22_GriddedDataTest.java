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
        NZSHM22_GriddedData data = new NZSHM22_GriddedData("seismicityGrids/BEST2FLTOLDNC1246.txt");
        GriddedRegion region = NewZealandRegions.TVZ;

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
        NZSHM22_GriddedData data = new NZSHM22_GriddedData("seismicityGrids/BEST2FLTOLDNC1246.txt");
        GriddedRegion tvz = NewZealandRegions.TVZ;
        GriddedRegion sansTvz = NewZealandRegions.SANS_TVZ;

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
    public void transformTest() {
        NZSHM22_GriddedData basedata = NZSHM22_SpatialSeisPDF.NZSHM22_1346.getGriddedData();
        NZSHM22_GriddedData actual = basedata.transform(((location, value) -> value * 2.0));

        double[] expectedValues = basedata.getValues();
        double[] actualValues = actual.getValues();

        for (int i = 0; i < expectedValues.length; i++) {
            expectedValues[i] *= 2;
        }
        assertArrayEquals(expectedValues, actualValues, 0.000000000000001);
    }

    @Test
    public void serialisationTest() throws IOException {
        NZSHM22_GriddedData original = NZSHM22_SpatialSeisPDF.NZSHM22_1346.getGriddedData();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        original.writeToStream(new BufferedOutputStream(out));
        out.close();
        NZSHM22_GriddedData actual = new NZSHM22_GriddedData();
        actual.initFromStream(new BufferedInputStream(new ByteArrayInputStream(out.toByteArray())));

        assertArrayEquals(original.getValues(), actual.getValues(), 0.000000000000001);
    }
}

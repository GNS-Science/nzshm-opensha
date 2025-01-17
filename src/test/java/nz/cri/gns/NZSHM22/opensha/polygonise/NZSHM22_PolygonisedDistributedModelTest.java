package nz.cri.gns.NZSHM22.opensha.polygonise;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import nz.cri.gns.NZSHM22.opensha.data.region.NewZealandRegions;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_SpatialSeisPDF;
import nz.cri.gns.NZSHM22.opensha.griddedSeismicity.NZSHM22_GriddedData;
import nz.cri.gns.NZSHM22.opensha.util.TestHelpers;
import org.junit.Test;

public class NZSHM22_PolygonisedDistributedModelTest {

    @Test
    public void serialisationTest() throws IOException {
        NZSHM22_GriddedData basedata = NZSHM22_SpatialSeisPDF.NZSHM22_1346.getGriddedData();
        List<Double> mmins =
                basedata.getGridPoints().stream()
                        .map(basedata::getValue)
                        .collect(Collectors.toList());
        NZSHM22_PolygonisedDistributedModel expected =
                new NZSHM22_PolygonisedDistributedModel(basedata, basedata, mmins);
        NZSHM22_PolygonisedDistributedModel actual =
                (NZSHM22_PolygonisedDistributedModel) TestHelpers.serialiseDeserialise(expected);
        assertArrayEquals(
                expected.getGriddedData().getValues(NewZealandRegions.NZ),
                actual.getGriddedData().getValues(NewZealandRegions.NZ),
                0.000000000000001);
    }
}

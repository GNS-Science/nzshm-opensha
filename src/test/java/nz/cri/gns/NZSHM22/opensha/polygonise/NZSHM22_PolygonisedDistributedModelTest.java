package nz.cri.gns.NZSHM22.opensha.polygonise;

import static org.junit.Assert.*;

import com.google.common.io.Files;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_SpatialSeisPDF;
import nz.cri.gns.NZSHM22.opensha.griddedSeismicity.NZSHM22_GriddedData;
import nz.cri.gns.NZSHM22.opensha.inversion.NZSHM22_InversionFaultSystemSolution;
import org.dom4j.DocumentException;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

public class NZSHM22_PolygonisedDistributedModelTest {

    public static File tempDir;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        tempDir = Files.createTempDir();
    }

    public static NZSHM22_InversionFaultSystemSolution loadSolution() throws URISyntaxException, DocumentException, IOException {
        URL alpineVernonRupturesUrl = Thread.currentThread().getContextClassLoader().getResource("ModularAlpineVernonInversionSolution.zip");
        return NZSHM22_InversionFaultSystemSolution.fromCrustalFile(new File(alpineVernonRupturesUrl.toURI()));
    }


    @Test
    public void serialisationTest() throws DocumentException, URISyntaxException, IOException {
        NZSHM22_GriddedData basedata = NZSHM22_SpatialSeisPDF.NZSHM22_1346.getGriddedData();

        NZSHM22_PolygonisedDistributedModel expected = new NZSHM22_PolygonisedDistributedModel(basedata);

        NZSHM22_InversionFaultSystemSolution solution = loadSolution();
        solution.addModule(expected);

        File file = new File(tempDir, "testModule.zip");

        solution.getArchive().write(file);

        NZSHM22_InversionFaultSystemSolution actualSolution = NZSHM22_InversionFaultSystemSolution.fromFile(file);

        NZSHM22_PolygonisedDistributedModel actual = actualSolution.getModule(NZSHM22_PolygonisedDistributedModel.class);

        assertArrayEquals(expected.getGriddedData().getValues(), actual.getGriddedData().getValues(), 0.000000000000001);

    }
}

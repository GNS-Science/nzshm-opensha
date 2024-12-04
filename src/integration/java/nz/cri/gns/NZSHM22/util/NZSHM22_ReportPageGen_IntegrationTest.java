package nz.cri.gns.NZSHM22.util;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.util.Parameters;
import org.dom4j.DocumentException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;


public class NZSHM22_ReportPageGen_IntegrationTest {

    private static File tempFolder;

    @BeforeClass
    public static void setUp() throws IOException {
        tempFolder = Files.createTempDirectory("_NZSHM22_RupSetDiagnosticsReport").toFile();
        System.setProperty("java.awt.headless", "true");
    }

    @AfterClass
    public static void tearDown() throws IOException {
        //Clean up the temp folder

        File[] tmp_folders = {new File(tempFolder, "resources"),
                new File(tempFolder, "hist_rup_pages"),
                tempFolder}; //make this one last

        for (File folder : tmp_folders) {
            File[] files = folder.listFiles();
            if (files != null) {
                for (File f : files) {
                    f.delete();
                }
            }
            Files.deleteIfExists(folder.toPath());
        }
    }

    @Test
    public void testRunReportForInversionSolution() throws IOException, DocumentException {
        System.out.println("hello");
        try {
            Parameters param = Parameters.NZSHM22.INVERSION_CRUSTAL.getParameters();
        }catch(Exception x) {
            assertTrue(x.getMessage(), false);
        }

        FaultSystemSolution solution = null;

            solution = TestHelpers.createCrustalSolution(
                    TestHelpers.makeRupSet(NZSHM22_FaultModels.CFM_1_0A_DOM_SANSTVZ, ScalingRelationships.SHAW_2009_MOD));
        new NZSHM22_ReportPageGen().setOutputPath(tempFolder.toString())
                .setName("test")
                .setSolution(solution)
                .setPlotLevel(null)
                .addPlot("SolMFDPlot")
                .generatePage();

        File index_html = new File(tempFolder, "index.html");

        assertTrue(index_html.exists());
    }

}

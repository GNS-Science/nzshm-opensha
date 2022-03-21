package nz.cri.gns.NZSHM22.util;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;


public class NZSHM22_ReportPageGen_IntegrationTest {

    private static URL alpineVernonInversionSolutionUrl;
    private static File tempFolder;

    @BeforeClass
    public static void setUp() throws IOException {
        alpineVernonInversionSolutionUrl = Thread.currentThread().getContextClassLoader().getResource("AlpineVernonInversionSolution.zip");
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
    public void testRunReportForInversionSolution() throws IOException {
        new NZSHM22_ReportPageGen().setOutputPath(tempFolder.toString())
                .setName("test")
                .setSolution(alpineVernonInversionSolutionUrl.getFile().toString())
                .setPlotLevel(null)
                .addPlot("SolMFDPlot")
                .generatePage();

        File index_html = new File(tempFolder, "index.html");

        assertTrue(index_html.exists());
    }

}

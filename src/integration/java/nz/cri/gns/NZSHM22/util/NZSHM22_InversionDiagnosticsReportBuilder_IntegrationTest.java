package nz.cri.gns.NZSHM22.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;

import org.dom4j.DocumentException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;



public class NZSHM22_InversionDiagnosticsReportBuilder_IntegrationTest {

	private static URL alpineVernonInversionSolutionUrl;
	private static File tempFolder;

	@BeforeClass
	public static void setUp() throws IOException, DocumentException, URISyntaxException {
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
			for (File f : files) {
				f.delete();
			}		
			Files.deleteIfExists(folder.toPath());
		}
	}
	
	/**
	 * Run diagnostics report NZSHM22_RupSetDiagnosticsReport from an InversionSolution.
	 * 
	 * @throws IOException
	 * @throws DocumentException
	 * @throws URISyntaxException 
	 */
	@Test 
	public void testRunReportForInversionSolution() throws IOException, DocumentException {
		
		NZSHM22_InversionDiagnosticsReportBuilder builder = new NZSHM22_InversionDiagnosticsReportBuilder();
		builder.setOutputDir(tempFolder.toString())
			.setName("test")
			.setFaultFilter("Vernon")
			.setRuptureSetName(alpineVernonInversionSolutionUrl.getFile().toString());
		builder.generateFilteredInversionDiagnosticsReport();
		
		File index_html = new File(tempFolder, "index.html");
		
		assertTrue(index_html.exists());
		
	}	
	
	@Test 
	public void testGenerateRateDiagnosticsPlot() throws IOException, DocumentException {
		NZSHM22_InversionDiagnosticsReportBuilder builder = new NZSHM22_InversionDiagnosticsReportBuilder();
		builder.setOutputDir(tempFolder.toString())
			.setName("test")
			.setRuptureSetName(alpineVernonInversionSolutionUrl.getFile().toString());
		builder.generateRateDiagnosticsPlot();
		
		File image_file = new File(tempFolder, "MAG_rates_log_fixed_yscale.png");
		assertTrue(image_file.exists());

	}
}

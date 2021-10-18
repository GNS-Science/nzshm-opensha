package nz.cri.gns.NZSHM22.opensha.inversion;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;

import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_LogicTreeBranch;
import cern.colt.matrix.tdouble.DoubleMatrix2D;
import org.dom4j.DocumentException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.opensha.commons.data.CSVFile;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionInputGenerator;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import scratch.UCERF3.inversion.UCERF3InversionConfiguration.SlipRateConstraintWeightingType;

public class NZSHM22_InversionRunner_IntegrationTest {

    private static URL alpineVernonRupturesUrl;
    private static URL amatrixUrl;
    private static File tempFolder;

    @BeforeClass
    public static void setUp() throws IOException, DocumentException, URISyntaxException {
        alpineVernonRupturesUrl = Thread.currentThread().getContextClassLoader().getResource("AlpineVernonRuptureSet.zip");
        amatrixUrl = Thread.currentThread().getContextClassLoader().getResource("amatrix.csv");
        tempFolder = Files.createTempDirectory("_testNew").toFile();
    }

    @AfterClass
    public static void tearDown() throws IOException {
        //Clean up the temp folder
        File[] files = tempFolder.listFiles();
        for (File f : files) {
            f.delete();
        }
        Files.deleteIfExists(tempFolder.toPath());
    }

    /**
     * Test showing how we create a new NZSHM22_InversionFaultSystemRuptSet from an existing rupture set
     *
     * @throws IOException
     * @throws DocumentException
     * @throws URISyntaxException
     */
    @Test
    public void testLoadRuptureSetForInversion() throws IOException, DocumentException, URISyntaxException {
        NZSHM22_InversionFaultSystemRuptSet ruptureSet = NZSHM22_CrustalInversionRunner.loadRuptureSet(new File(alpineVernonRupturesUrl.toURI()), NZSHM22_LogicTreeBranch.crustal());
        assertEquals(3101, ruptureSet.getModule(ClusterRuptures.class).getAll().size());
    }

    /**
     * Verification that we are setting up the A matrix as expected.
     */
    @Test
    public void testRunCrustalInversion_AlpineVernon() throws IOException, DocumentException, URISyntaxException {

        NZSHM22_CrustalInversionRunner runner = (NZSHM22_CrustalInversionRunner) new NZSHM22_CrustalInversionRunner()
                .setMinMagForSeismogenicRups(7)
                .setGutenbergRichterMFD(3.6, 0.36,
                        1.05, 1.25, 7.85)
                .setRuptureSetFile(new File(alpineVernonRupturesUrl.toURI()))
                .setGutenbergRichterMFDWeights(1e3, 1e3)
                .configure();

        InversionInputGenerator inputGenerator = runner.getInversionInputGenerator();
        inputGenerator.generateInputs(true);
        inputGenerator.columnCompress();
        DoubleMatrix2D actual = inputGenerator.getA();
//        writeMatrix(actual);

        assertMatrixEquals(new File(amatrixUrl.toURI()), actual);

    }

    public void assertMatrixEquals(File expectedCsv, DoubleMatrix2D actual) throws IOException {
        CSVFile expected = CSVFile.readFileNumeric(expectedCsv, true, 0);
        for (int r = 0; r < expected.getNumRows(); r++) {
            for (int c = 0; c < expected.getNumCols(); c++) {
                assertEquals("matrix[" + r + ", " + c + "] ", expected.getDouble(r, c), actual.get(r, c), 0.00000001);
            }
        }
    }

    public void writeMatrix(DoubleMatrix2D matrix) throws IOException {
        double[][] a = matrix.toArray();
        try (PrintWriter out = new PrintWriter(new FileWriter("amatrix-actual.csv"))) {
            for (int r = 0; r < a.length; r++) {
                double[] row = a[r];
                for (int col = 0; col < row.length; col++) {
                    if (row[col] == 0) {
                        out.print("0");
                    } else {
                        out.print(row[col]);
                    }
                    if (col < row.length - 1) {
                        out.print(",");
                    }
                }
                out.println();
            }
        }
    }

    //TODO we should use junit>=4.13 and assertThrows instead
    // see https://stackoverflow.com/questions/156503/how-do-you-assert-that-a-certain-exception-is-thrown-in-junit-4-tests
    @Test(expected = IllegalArgumentException.class)
    public void testSetSlipRateConstraintThrowsWithInvalidArgument() {
        NZSHM22_CrustalInversionRunner runner = (NZSHM22_CrustalInversionRunner) new NZSHM22_CrustalInversionRunner()
                .setSlipRateConstraint(SlipRateConstraintWeightingType.UNCERTAINTY_ADJUSTED, 1, 2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetSlipRateUncertaintyConstraintThrowsWithInvalidArgument() {
        NZSHM22_CrustalInversionRunner runner = new NZSHM22_CrustalInversionRunner()
                .setSlipRateUncertaintyConstraint(SlipRateConstraintWeightingType.BOTH, 1, 2);
    }

}

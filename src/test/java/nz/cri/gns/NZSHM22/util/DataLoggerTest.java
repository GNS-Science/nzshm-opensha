package nz.cri.gns.NZSHM22.util;

import static org.junit.Assert.*;

import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.impl.SparseDoubleMatrix2D;
import java.io.*;
import java.nio.file.Files;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.Test;

public class DataLoggerTest {

    static String format(double value) {
        return DataLogger.format(value);
    }

    @Test
    public void testFormat() {

        assertEquals("0", format(0));
        assertEquals("1.0", format(1));
        assertEquals("10.0", format(10));
        assertEquals("1E6", format(1000000));
        assertEquals(".001", format(0.001));
        assertEquals("1E-4", format(0.0001));
        assertEquals("1E-6", format(0.000001));
        assertEquals("1.235", format(1.23456));
        assertEquals("1.235E3", format(1234.5678));
    }

    static String getFile(File path, String csvFile) throws IOException {
        ZipFile zip = new ZipFile(path);
        ZipEntry entry = zip.getEntry(csvFile);
        InputStream in = zip.getInputStream(entry);
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        String data = reader.lines().collect(Collectors.joining("\n"));
        zip.close();
        return data;
    }

    @Test
    public void testLog() throws IOException {
        String headerFile = "withHeader";
        String arrayFile = "arrayFile";

        File tempDir = Files.createTempDirectory("zipLog").toFile();

        DataLogger.MultiZipLog log =
                new DataLogger.MultiZipLog(tempDir.getAbsolutePath(), "testLog", 30);
        log.addHeader(headerFile, "a,b,c\n");

        log.nextIndex(0);
        log.log(headerFile, new double[] {1, 2, 3});
        log.log(arrayFile, new double[] {4, 5, 6});

        log.nextIndex(1);
        log.log(headerFile, "a,b,c\n");
        log.log(arrayFile, new double[] {7, 8, 9});

        log.nextIndex(2);
        log.log(headerFile, "d,e,f\n");
        log.log(arrayFile, new double[] {10, 11, 12});

        log.close();

        String actual = getFile(new File(tempDir, "testLog[0-1].zip"), headerFile + ".csv");
        assertEquals("a,b,c\n1.0,2.0,3.0\na,b,c", actual);
        actual = getFile(new File(tempDir, "testLog[2-2].zip"), headerFile + ".csv");
        assertEquals("a,b,c\nd,e,f", actual);

        actual = getFile(new File(tempDir, "testLog[0-1].zip"), arrayFile + ".csv");
        assertEquals("4.0,5.0,6.0\n7.0,8.0,9.0", actual);
        actual = getFile(new File(tempDir, "testLog[2-2].zip"), arrayFile + ".csv");
        assertEquals("10.0,11.0,12.0", actual);
    }

    @Test
    public void testDumpVector() throws IOException {

        File tempDir = Files.createTempDirectory("dump").toFile();
        String fileName = tempDir.getAbsolutePath() + "/testDumpVector.csv";
        double[] sourceValue =
                new double[] {
                    0, 1.0, 1.2, 3.1456789, Double.NaN, Double.POSITIVE_INFINITY, Double.MIN_VALUE
                };

        DataLogger.dump(fileName, sourceValue);

        String actual = "";

        try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
            actual += reader.lines().collect(Collectors.joining("\n"));
        }

        assertEquals("0,1.0,1.2,3.146,NaN,Infinity,4.9E-324", actual);
    }

    @Test
    public void testDumpMatrix() throws IOException {

        File tempDir = Files.createTempDirectory("dump").toFile();
        String fileName = tempDir.getAbsolutePath() + "/testDumpMatrix.csv";

        DoubleMatrix2D sourceValue = new SparseDoubleMatrix2D(5, 5);
        sourceValue.set(0, 0, 42);
        sourceValue.set(0, 1, Double.NEGATIVE_INFINITY);
        sourceValue.set(0, 2, 2);
        sourceValue.set(0, 3, 3);
        sourceValue.set(0, 4, 4);
        sourceValue.set(4, 4, 13);
        sourceValue.set(3, 2, 7);
        DataLogger.dump(fileName, sourceValue);

        String actual = "";

        try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
            actual += reader.lines().collect(Collectors.joining("\n"));
        }

        assertEquals(
                "42.0,-Infinity,2.0,3.0,4.0\n"
                        + "0,0,0,0,0\n"
                        + "0,0,0,0,0\n"
                        + "0,0,7.0,0,0\n"
                        + "0,0,0,0,13.0",
                actual);
    }
}

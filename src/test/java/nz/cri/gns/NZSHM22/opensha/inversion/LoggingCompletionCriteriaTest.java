package nz.cri.gns.NZSHM22.opensha.inversion;

import org.junit.Test;

import java.io.*;
import java.nio.file.Files;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.Assert.*;

public class LoggingCompletionCriteriaTest {

    static String format(double value) {
        return LoggingCompletionCriteria.MultiZipLog.format(value);
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

        LoggingCompletionCriteria.MultiZipLog log = new LoggingCompletionCriteria.MultiZipLog(
                tempDir.getAbsolutePath(),
                "testLog",
                30);
        log.addHeader(headerFile, "a,b,c\n");

        log.nextIndex(0);
        log.log(headerFile, new double[]{1, 2, 3});
        log.log(arrayFile, new double[]{4, 5, 6});

        log.nextIndex(1);
        log.log(headerFile, "a,b,c\n");
        log.log(arrayFile, new double[]{7, 8, 9});

        log.nextIndex(2);
        log.log(headerFile, "d,e,f\n");
        log.log(arrayFile, new double[]{10, 11, 12});

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
}

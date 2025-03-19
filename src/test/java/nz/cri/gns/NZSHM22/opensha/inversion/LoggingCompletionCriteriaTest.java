package nz.cri.gns.NZSHM22.opensha.inversion;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.*;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.ConstraintWeightingType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.ConstraintRange;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.InversionState;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.CompletionCriteria;

public class LoggingCompletionCriteriaTest {

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
    public void testLogging() throws IOException {

        File tempDir = Files.createTempDirectory("zipLog").toFile();

        CompletionCriteria innerCriteria = mock(CompletionCriteria.class);
        LoggingCompletionCriteria toTest =
                new LoggingCompletionCriteria(innerCriteria, tempDir.getAbsolutePath(), 1);
        ConstraintRange range =
                new ConstraintRange(
                        "range1", "r1", 0, 0, false, 0, ConstraintWeightingType.NORMALIZED);
        toTest.setConstraintRanges(List.of(range));

        InversionState state1 =
                new InversionState(
                        1,
                        2,
                        new double[] {3, 4, 5},
                        6,
                        7,
                        8,
                        new double[] {9, 10, 11},
                        new double[] {12, 13, 14},
                        new double[] {15, 16, 17},
                        null);
        InversionState state2 =
                new InversionState(
                        18,
                        19,
                        new double[] {20, 21, 22},
                        23,
                        24,
                        25,
                        new double[] {26, 27, 28},
                        new double[] {29, 30, 31},
                        new double[] {32, 33, 34},
                        null);
        when(innerCriteria.isSatisfied(state1)).thenReturn(true);
        when(innerCriteria.isSatisfied(state2)).thenReturn(false);

        // simulate inversion
        boolean result1 = toTest.isSatisfied(state1);
        boolean result2 = toTest.isSatisfied(state2);

        // inner result is passed through
        assertTrue(result1);
        assertFalse(result2);

        toTest.close();

        String energy = getFile(new File(tempDir, "inversionState[2-19].zip"), "energy.csv");
        assertEquals(
                "Total Energy,Equality Energy,Entropy Energy,Inequality Energy,range1\n3.0,4.0,5.0\n20.0,21.0,22.0",
                energy);

        String meta = getFile(new File(tempDir, "inversionState[2-19].zip"), "meta.csv");
        assertEquals(
                "iterations,elapsedTimeMillis,numPerturbsKept,numWorseValuesKept,numNonZero\n"
                        + "2,1,6,7,8\n"
                        + "19,18,23,24,25",
                meta);

        String misfits = getFile(new File(tempDir, "inversionState[2-19].zip"), "misfits.csv");
        assertEquals("12.0,13.0,14.0\n29.0,30.0,31.0", misfits);

        String misfitsIneq =
                getFile(new File(tempDir, "inversionState[2-19].zip"), "misfits_ineq.csv");
        assertEquals("15.0,16.0,17.0\n32.0,33.0,34.0", misfitsIneq);

        String solution = getFile(new File(tempDir, "inversionState[2-19].zip"), "solution.csv");
        assertEquals("9.0,10.0,11.0\n26.0,27.0,28.0", solution);
    }
}

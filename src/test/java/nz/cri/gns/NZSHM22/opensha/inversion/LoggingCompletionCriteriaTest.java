package nz.cri.gns.NZSHM22.opensha.inversion;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.parquet.hadoop.ParquetWriter;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
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
    public void testLogging() throws IOException, NoSuchFieldException, IllegalAccessException {

        File tempDir = Files.createTempDirectory("zipLog").toFile();

        CompletionCriteria innerCriteria = mock(CompletionCriteria.class);
        LoggingCompletionCriteria toTest =
                new LoggingCompletionCriteria(innerCriteria, tempDir.getAbsolutePath(), 1);
        ConstraintRange range =
                new ConstraintRange(
                        "range1", "r1", 0, 0, false, 0, ConstraintWeightingType.NORMALIZED);
        toTest.setConstraintRanges(List.of(range));

        ParquetWriter<?> metaWriter = mock(ParquetWriter.class);
        ParquetWriter<?> energyWriter = mock(ParquetWriter.class);
        ParquetWriter<GenericRecord> solutionWriter = mock(ParquetWriter.class);
        ParquetWriter<?> misfitsWriter = mock(ParquetWriter.class);
        ParquetWriter<?> misfitsIneqWriter = mock(ParquetWriter.class);

        Class<?> testClass = LoggingCompletionCriteria.class;
        Field field = testClass.getDeclaredField("metaWriter");
        field.setAccessible(true);
        field.set(toTest, metaWriter);
         field = testClass.getDeclaredField("energyWriter");
        field.setAccessible(true);
        field.set(toTest, energyWriter);
         field = testClass.getDeclaredField("solutionWriter");
        field.setAccessible(true);
        field.set(toTest, solutionWriter);
         field = testClass.getDeclaredField("misfitsWriter");
        field.setAccessible(true);
        field.set(toTest, misfitsWriter);
         field = testClass.getDeclaredField("misfitsIneqWriter");
        field.setAccessible(true);
        field.set(toTest, misfitsIneqWriter);

        InversionState state1 =
                new InversionState(
                        1,
                        2,
                        new double[] {3, 4, 5, 18, 19},
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
                        new double[] {20, 21, 22, 35, 36},
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
     //   boolean result2 = toTest.isSatisfied(state2);

        // inner result is passed through
        assertTrue(result1);
      //  assertFalse(result2);

        toTest.close();
        ArgumentCaptor<GenericRecord> argumentCaptor = ArgumentCaptor.forClass(GenericRecord.class);
        verify(solutionWriter).write(argumentCaptor.capture());
        GenericRecord actual = argumentCaptor.getValue();

        assertArrayEquals(new double[] {9, 10, 11}, (double[]) actual.get("solution"), 0.00000001);

 
    }
}

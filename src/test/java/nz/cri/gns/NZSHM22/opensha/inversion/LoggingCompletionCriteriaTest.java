package nz.cri.gns.NZSHM22.opensha.inversion;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.List;
import org.apache.avro.generic.GenericRecord;
import org.apache.parquet.hadoop.ParquetWriter;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.ConstraintWeightingType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.ConstraintRange;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.InversionState;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.CompletionCriteria;

public class LoggingCompletionCriteriaTest {

    double[] energy = new double[] {3, 4, 5, 18, 19};
    double[] solution = new double[] {9, 10, 11};
    double[] misfits = new double[] {12, 13, 14};
    double[] misfits_ineq = new double[] {15, 16, 17};

    InversionState state1 =
            new InversionState(1, 2, energy, 6, 7, 8, solution, misfits, misfits_ineq, null);

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

        ParquetWriter<GenericRecord> metaWriter = mock(ParquetWriter.class);
        ParquetWriter<GenericRecord> energyWriter = mock(ParquetWriter.class);
        ParquetWriter<GenericRecord> solutionWriter = mock(ParquetWriter.class);
        ParquetWriter<GenericRecord> misfitsWriter = mock(ParquetWriter.class);
        ParquetWriter<GenericRecord> misfitsIneqWriter = mock(ParquetWriter.class);

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

        when(innerCriteria.isSatisfied(state1)).thenReturn(true);

        // simulate inversion
        boolean result1 = toTest.isSatisfied(state1);
        toTest.close();

        // inner result is passed through
        assertTrue(result1);

        ArgumentCaptor<GenericRecord> argumentCaptor = ArgumentCaptor.forClass(GenericRecord.class);
        verify(metaWriter).write(argumentCaptor.capture());
        assertEquals(2L, argumentCaptor.getValue().get("iterations"));
        assertEquals(1L, argumentCaptor.getValue().get("elapsedTimeMillis"));
        assertEquals(6L, argumentCaptor.getValue().get("numPerturbsKept"));
        assertEquals(7L, argumentCaptor.getValue().get("numWorseValuesKept"));
        assertEquals(8, argumentCaptor.getValue().get("numNonZero"));

        argumentCaptor = ArgumentCaptor.forClass(GenericRecord.class);
        verify(solutionWriter).write(argumentCaptor.capture());
        assertArrayEquals(
                solution, (double[]) argumentCaptor.getValue().get("solution"), 0.00000001);

        argumentCaptor = ArgumentCaptor.forClass(GenericRecord.class);
        verify(energyWriter).write(argumentCaptor.capture());
        assertEquals(3.0, argumentCaptor.getValue().get("TotalEnergy"));
        assertEquals(4.0, argumentCaptor.getValue().get("EqualityEnergy"));
        assertEquals(5.0, argumentCaptor.getValue().get("EntropyEnergy"));
        assertEquals(18.0, argumentCaptor.getValue().get("InequalityEnergy"));
        assertEquals(19.0, argumentCaptor.getValue().get("range1"));

        argumentCaptor = ArgumentCaptor.forClass(GenericRecord.class);
        verify(misfitsWriter).write(argumentCaptor.capture());
        assertArrayEquals(misfits, (double[]) argumentCaptor.getValue().get("misfits"), 0.00000001);

        argumentCaptor = ArgumentCaptor.forClass(GenericRecord.class);
        verify(misfitsIneqWriter).write(argumentCaptor.capture());
        assertArrayEquals(
                misfits_ineq, (double[]) argumentCaptor.getValue().get("misfitsIneq"), 0.00000001);
    }
}

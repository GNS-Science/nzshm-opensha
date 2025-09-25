package nz.cri.gns.NZSHM22.opensha.inversion;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.avro.AvroWriteSupport;
import org.apache.parquet.conf.ParquetConfiguration;
import org.apache.parquet.conf.PlainParquetConfiguration;
import org.apache.parquet.hadoop.ParquetFileWriter;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.apache.parquet.io.LocalOutputFile;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.ConstraintRange;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.InversionState;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.AnnealingProgress;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.CompletionCriteria;

/**
 * Can be used to wrap a CompletionCriteria to log all InversionState instances that are passed in.
 * Note that this will not work as a sub CompletionCriteria if the inner criteria relies on
 * iteration count. Logs will be written as parquet files.
 *
 * <p>Implementation hints: How to use Avro
 * https://www.jeronimo.dev/working-with-parquet-files-in-java-using-avro/
 * https://avro.apache.org/docs/++version++/getting-started-java/ Minimal Avro:
 * https://blakesmith.me/2024/10/05/how-to-use-parquet-java-without-hadoop.html
 */
public class LoggingCompletionCriteria implements CompletionCriteria, Closeable {

    protected final CompletionCriteria innerCriteria;
    protected final String basePath;

    protected static Schema metaSchema =
            SchemaBuilder.record("meta")
                    .fields()
                    .requiredLong("iterations")
                    .requiredLong("elapsedTimeMillis")
                    .requiredLong("numPerturbsKept")
                    .requiredLong("numWorseValuesKept")
                    .requiredLong("numNonZero")
                    .endRecord();
    protected Schema energySchema;
    protected static Schema arraySchema = SchemaBuilder.array().items().doubleType();
    protected static Schema solutionSchema =
            SchemaBuilder.record("solution")
                    .fields()
                    .name("solution")
                    .type(arraySchema)
                    .noDefault()
                    .endRecord();
    protected static Schema misfitsSchema =
            SchemaBuilder.record("misfits")
                    .fields()
                    .name("misfits")
                    .type(arraySchema)
                    .noDefault()
                    .endRecord();
    protected static Schema misfitsIneqSchema =
            SchemaBuilder.record("misfitsIneq")
                    .fields()
                    .name("misfitsIneq")
                    .type(arraySchema)
                    .noDefault()
                    .endRecord();

    protected ParquetWriter<GenericRecord> metaWriter;
    protected ParquetWriter<GenericRecord> energyWriter;
    protected ParquetWriter<GenericRecord> solutionWriter;
    protected ParquetWriter<GenericRecord> misfitsWriter;
    protected ParquetWriter<GenericRecord> misfitsIneqWriter;

    public ParquetWriter<GenericRecord> openWriter(Schema schema, String fileName) {
        try {
            FileSystem fs = FileSystems.getFileSystem(new URI("file", null, "/", null, null));
            Path nioOutputPath = fs.getPath(basePath, fileName + ".parquet");
            LocalOutputFile outputFile = new LocalOutputFile(nioOutputPath);
            ParquetConfiguration parquetConf = new PlainParquetConfiguration();
            return AvroParquetWriter.<GenericRecord>builder(outputFile)
                    .withSchema(schema)
                    .withWriteMode(ParquetFileWriter.Mode.OVERWRITE)
                    // 8K recommended https://parquet.apache.org/docs/file-format/configurations/
                    .withPageSize(8 * 1024)
                    // smaller than recommended because we've got huge arrays in the column
                    .withRowGroupSize((long) 1024)
                    .withConf(parquetConf)
                    // better performance than SNAPPY
                    .withCompressionCodec(CompressionCodecName.GZIP)
                    .config(AvroWriteSupport.WRITE_PARQUET_UUID, "true")
                    .build();
        } catch (IOException | URISyntaxException x) {
            throw new RuntimeException(x);
        }
    }

    /**
     * Creates a new LoggingCompletionCriteria
     *
     * @param innerCriteria the isSatisfied() method will be forwarded to the innerCriteria
     * @param basePath a path to a folder. Does not need to exist. All log files will be stored in
     *     this folder.
     * @param maxMB log files will be split up if they reach this size (uncompressed)
     * @throws IOException
     */
    public LoggingCompletionCriteria(CompletionCriteria innerCriteria, String basePath, int maxMB)
            throws IOException {
        this.innerCriteria = innerCriteria;
        this.basePath = basePath;
        Files.createDirectories(new File(basePath).toPath());
    }

    @Override
    public boolean isSatisfied(InversionState state) {

        GenericRecord metaRecord = new GenericData.Record(metaSchema);
        metaRecord.put("iterations", state.iterations);
        metaRecord.put("elapsedTimeMillis", state.elapsedTimeMillis);
        metaRecord.put("numPerturbsKept", state.numPerturbsKept);
        metaRecord.put("numWorseValuesKept", state.numWorseValuesKept);
        metaRecord.put("numNonZero", state.numNonZero);

        GenericRecord energyRecord = new GenericData.Record(energySchema);
        for (int i = 0; i < state.energy.length; i++) {
            energyRecord.put(i, state.energy[i]);
        }

        GenericRecord solutionRecord = new GenericData.Record(solutionSchema);
        solutionRecord.put(
                "solution", state.bestSolution == null ? new double[] {} : state.bestSolution);

        GenericRecord misfitsRecord = new GenericData.Record(misfitsSchema);
        misfitsRecord.put("misfits", state.misfits == null ? new double[] {} : state.misfits);

        GenericRecord misfitsIneqRecord = new GenericData.Record(misfitsIneqSchema);
        misfitsIneqRecord.put(
                "misfitsIneq", state.misfits_ineq == null ? new double[] {} : state.misfits_ineq);

        try {
            synchronized (basePath) {
                metaWriter.write(metaRecord);
                energyWriter.write(energyRecord);
                solutionWriter.write(solutionRecord);
                misfitsWriter.write(misfitsRecord);
                misfitsIneqWriter.write(misfitsIneqRecord);
            }
        } catch (IOException x) {
            throw new RuntimeException(x);
        }

        return innerCriteria.isSatisfied(state);
    }

    public void open() {
        metaWriter = openWriter(metaSchema, "meta");
        energyWriter = openWriter(energySchema, "energy");
        solutionWriter = openWriter(solutionSchema, "solution");
        misfitsWriter = openWriter(misfitsSchema, "misfits");
        misfitsIneqWriter = openWriter(misfitsIneqSchema, "misfits_ineq");
    }

    @Override
    public void close() throws IOException {
        metaWriter.close();
        energyWriter.close();
        solutionWriter.close();
        misfitsWriter.close();
        misfitsIneqWriter.close();
    }

    public LoggingCompletionCriteria setConstraintRanges(List<ConstraintRange> constraintRanges) {
        AnnealingProgress progress = AnnealingProgress.forConstraintRanges(constraintRanges);

        SchemaBuilder.FieldAssembler<Schema> energyFields = SchemaBuilder.record("energy").fields();
        for (String energyType : progress.getEnergyTypes()) {
            // energyType names can have brackets etc in them, which are not legal for parquet names
            energyFields = energyFields.requiredDouble(energyType.replaceAll("[\\W]", ""));
        }
        energySchema = energyFields.endRecord();

        return this;
    }
}

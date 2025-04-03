package nz.cri.gns.NZSHM22.opensha.inversion;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.*;

import com.google.common.collect.ImmutableList;
import nz.cri.gns.NZSHM22.util.DataLogger;
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
import org.apache.hadoop.conf.Configuration;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.apache.parquet.hadoop.util.HadoopOutputFile;
import org.apache.parquet.io.LocalOutputFile;
import org.apache.parquet.io.OutputFile;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.ConstraintRange;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.InversionState;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.AnnealingProgress;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.CompletionCriteria;

/**
 * Can be used to wrap a CompletionCriteria to log all InversionState instances that are passed in.
 * Note that this will not work as a sub CompletionCriteria if the inner criteria relies on
 * iteration count. Logs will be broken up into zip files.
 */
public class LoggingCompletionCriteria implements CompletionCriteria, Closeable {

    protected final CompletionCriteria innerCriteria;

    protected InversionStateLog solutionLog;

    protected LocalOutputFile outputFile;
    protected static Schema metaSchema =
            SchemaBuilder.record("Meta")
                    .fields()
                    .requiredLong("iterations")
                    .requiredLong("elapsedTimeMillis")
                    .requiredLong("numPerturbsKept")
                    .requiredLong("numWorseValuesKept")
                    .requiredLong("numNonZero")
                    .endRecord();
    protected static Schema energySchema;
    protected static Schema schema;
    protected static ParquetWriter<GenericRecord> parquetWriter;

    public void setParquetSchema(ImmutableList<String> energyTypes) {
        SchemaBuilder.FieldAssembler<Schema> energyFields = SchemaBuilder.record("Energy").fields();
        for (String energyType : energyTypes) {
            energyFields = energyFields.requiredDouble(energyType.replaceAll("[\\W]", ""));

        }
        energySchema = energyFields.endRecord();

        schema =
                SchemaBuilder.record("IterationLog")
                        .fields()
                        .name("meta")
                        .type(metaSchema)
                        .noDefault()
                        .name("energy")
                        .type(energySchema)
                        .noDefault()
                        .name("solution")
                        .type()
                        .array()
                        .items()
                        .doubleType()
                        .noDefault()
                        .name("misfits")
                        .type()
                        .nullable()
                        .array()
                        .items()
                        .doubleType()
                        .noDefault()
                        .name("misfitsIneq")
                        .type()
                        .nullable()
                        .array()
                        .items()
                        .doubleType()
                        .noDefault()
                        .endRecord();

        try {
            ParquetConfiguration parquetConf = new PlainParquetConfiguration();
            parquetWriter =
                    AvroParquetWriter.<GenericRecord>builder(outputFile)
                            .withSchema(schema)
                            .withWriteMode(ParquetFileWriter.Mode.OVERWRITE)
                            .withPageRowCountLimit(1000)
                            .withConf(parquetConf)
                            .withCompressionCodec(CompressionCodecName.GZIP)
                            .withDictionaryEncoding(true)
                            .config(AvroWriteSupport.WRITE_PARQUET_UUID, "true")
                            .build();
        } catch (IOException x) {
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
        this.solutionLog = new InversionStateLog(basePath, maxMB);
        Files.createDirectories(new File(basePath).toPath());

        try{
        FileSystem fs = FileSystems.getFileSystem(new URI("file", null, "/", null, null));
        Path nioOutputPath = fs.getPath(basePath, "parquet");
        System.out.println(nioOutputPath.toAbsolutePath());
        this.outputFile = new LocalOutputFile(nioOutputPath);
        }catch (URISyntaxException x) {
            throw new RuntimeException(x);
        }

    }

    @Override
    public boolean isSatisfied(InversionState state) {

        solutionLog.log(state);

        return innerCriteria.isSatisfied(state);
    }

    @Override
    public void close() throws IOException {
        solutionLog.close();
    }

    public void setConstraintRanges(List<ConstraintRange> constraintRanges) {
        AnnealingProgress progress = AnnealingProgress.forConstraintRanges(constraintRanges);
        String energiesHeader = String.join(",", progress.getEnergyTypes());
        solutionLog.addHeader("energy", energiesHeader + "\n");
        setParquetSchema(progress.getEnergyTypes());
    }

    protected static class InversionStateLog implements Closeable {

        DataLogger.MultiZipLog log;

        public InversionStateLog(String basePath, int maxMB) {
            log =
                    new DataLogger.MultiZipLog(
                            basePath, "inversionState", ((long) maxMB) * 1024 * 1024);
            addHeader(
                    "meta",
                    "iterations,elapsedTimeMillis,numPerturbsKept,numWorseValuesKept,numNonZero\n");
        }

        public void addHeader(String file, String header) {
            log.addHeader(file, header);
        }

        public void log(InversionState state) {
            log.nextIndex(state.iterations);

            String meta =
                    state.iterations
                            + ","
                            + state.elapsedTimeMillis
                            + ","
                            + state.numPerturbsKept
                            + ","
                            + state.numWorseValuesKept
                            + ","
                            + state.numNonZero
                            + "\n";

            log.log("meta", meta);
            log.log("solution", state.bestSolution);
            log.log("energy", state.energy);
            log.log("misfits", state.misfits);
            log.log("misfits_ineq", state.misfits_ineq);

            GenericRecord energyRecord = new GenericData.Record(energySchema);
            for (int i = 0; i < state.energy.length; i++) {
                energyRecord.put(i, state.energy[i]);
            }
            GenericRecord metaRecord = new GenericData.Record(metaSchema);
            metaRecord.put("iterations", state.iterations);
            metaRecord.put("elapsedTimeMillis", state.elapsedTimeMillis);
            metaRecord.put("numPerturbsKept", state.numPerturbsKept);
            metaRecord.put("numWorseValuesKept", state.numWorseValuesKept);
            metaRecord.put("numNonZero", state.numNonZero);

            GenericRecord record = new GenericData.Record(schema);
            record.put("meta", metaRecord);
            record.put("energy", energyRecord);
            record.put("solution", state.bestSolution);
            if (state.misfits != null) {
                record.put("misfits", state.misfits);
            } else {
                record.put("misfits", new double[]{});
            }
            if (state.misfits_ineq != null) {
                record.put("misfitsIneq", state.misfits_ineq);
            } else {
                record.put("misfitsIneq", new double[]{});
            }


            try {
                parquetWriter.write(record);
            } catch (IOException x) {
                throw new RuntimeException(x);
            }
        }

        @Override
        public void close() throws IOException {
            log.close();
            parquetWriter.close();
        }
    }
}

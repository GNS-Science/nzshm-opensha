package nz.cri.gns.NZSHM22.opensha.inversion;

import java.io.*;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
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
    }

    protected static class InversionStateLog implements Closeable {

        MultiZipLog log;

        public InversionStateLog(String basePath, int maxMB) {
            log = new MultiZipLog(basePath, "inversionState", ((long) maxMB) * 1024 * 1024);
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
        }

        @Override
        public void close() throws IOException {
            log.close();
        }
    }

    /**
     * Can be used log multiple streams at once. Streams are synchronized by index and stored
     * together in a zip file. The zip file will be broken up if the uncompressed data takes up more
     * than a specified sized.
     */
    static class MultiZipLog implements Closeable {

        String fileName;
        long maxBytes;

        long bytes;
        long startLine;
        long currentLine;

        Map<String, List<byte[]>> cache;

        Map<String, String> headers = new HashMap<>();

        /**
         * Creates a new MultiZipLog
         *
         * @param basePath a folder that the log files will be stored in
         * @param fileName a file name prefix for the log files
         * @param maxBytes zip files will be split if their uncompressed size exceeds this limit
         */
        public MultiZipLog(String basePath, String fileName, long maxBytes) {
            try {
                Files.createDirectories(new File(basePath).toPath());
                this.fileName = new File(basePath, fileName).getAbsolutePath();
                this.maxBytes = maxBytes;
            } catch (IOException x) {
                throw new RuntimeException(x);
            }
        }

        public void addHeader(String file, String header) {
            headers.put(file, header);
        }

        static DecimalFormat fmt = new DecimalFormat("0.###E0");

        public static String format(double value) {

            if (value == 0) {
                return "0";
            }

            String a = value + "";

            if (Double.isInfinite(value) || Double.isNaN(value)) {
                return a;
            }

            // writing it like this because using regex functions here double the runtime for
            // inmversion
            if (a.charAt(0) == '0' && a.charAt(1) == '.') {
                a = a.substring(1);
            }

            if (a.length() < 5) {
                return a;
            }

            String b = fmt.format(value);
            if (b.charAt(b.length() - 2) == 'E' && b.charAt(b.length() - 1) == '0') {
                b = b.substring(0, b.length() - 2);
            }

            if (b.length() < a.length()) {
                return b;
            }
            return a;
        }

        static byte[] getBytes(double[] data) {
            String line = "\n";

            if (data != null) {
                line =
                        Arrays.stream(data)
                                        .mapToObj(MultiZipLog::format)
                                        .collect(Collectors.joining(","))
                                + "\n";
            }
            return line.getBytes();
        }

        public void log(String file, double[] data) {
            byte[] bytes = getBytes(data);
            log(file, bytes);
        }

        public void log(String file, String data) {
            byte[] bytes = data.getBytes();
            log(file, bytes);
        }

        public void nextIndex(long line) {
            if (bytes >= maxBytes) {
                writeToFile();
                cache = null;
            }

            if (cache == null) {
                cache = new HashMap<>();
                bytes = 0;
                startLine = line;
            }
            currentLine = line;
        }

        public void log(String file, byte[] data) {
            bytes += data.length;
            List<byte[]> list = cache.computeIfAbsent(file, k -> new ArrayList<>());
            list.add(data);
        }

        public void writeToFile() {
            try {
                FileOutputStream fout =
                        new FileOutputStream(
                                fileName + "[" + startLine + "-" + currentLine + "].zip");
                ZipOutputStream zout = new ZipOutputStream(fout);
                zout.setLevel(9);

                for (String file : cache.keySet()) {
                    List<byte[]> data = cache.get(file);
                    String header = headers.get(file);
                    zout.putNextEntry(new ZipEntry(file + ".csv"));
                    if (header != null) {
                        zout.write(header.getBytes());
                    }
                    for (byte[] line : data) {
                        zout.write(line);
                    }
                    zout.closeEntry();
                }

                zout.finish();
                zout.close();
            } catch (IOException x) {
                throw new RuntimeException(x);
            }
        }

        @Override
        public void close() throws IOException {
            if (cache != null) {
                writeToFile();
                cache = null;
            }
        }
    }
}

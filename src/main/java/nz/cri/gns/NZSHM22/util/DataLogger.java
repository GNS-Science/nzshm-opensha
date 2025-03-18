package nz.cri.gns.NZSHM22.util;

import cern.colt.matrix.tdouble.DoubleMatrix2D;
import java.io.*;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class DataLogger {

    static DecimalFormat fmt = new DecimalFormat("0.###E0");

    /**
     * Takes a double value and returns the smallest ASCII representation with 3 decimal digit
     * accuracy.
     *
     * @param value
     * @return
     */
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
                                    .mapToObj(DataLogger::format)
                                    .collect(Collectors.joining(","))
                            + "\n";
        }
        return line.getBytes();
    }

    public static void dump(String fileName, DoubleMatrix2D matrix) {
        try (OutputStream out = new FileOutputStream(fileName)) {
            for (double[] row : matrix.toArray()) {
                out.write(getBytes(row));
            }
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
    }

    public static void dump(String fileName, double[] vector) {
        try (OutputStream out = new FileOutputStream(fileName)) {
            out.write(getBytes(vector));
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
    }

    /**
     * Can be used log multiple streams at once. Streams are synchronized by index and stored
     * together in a zip file. The zip file will be broken up if the uncompressed data takes up more
     * than a specified sized.
     */
    public static class MultiZipLog implements Closeable {

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

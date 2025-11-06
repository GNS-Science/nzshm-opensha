package nz.earthsciences.jupyterlogger;

import com.google.common.base.Preconditions;
import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

/**
 * A facility to easily create a Jupyter notebook from debug data. JupyterLogger.initialise(); needs
 * to be called before logging anything.
 */
public class JupyterLogger implements Closeable {
    static final Object lock = new Object();
    static JupyterLogger instance;

    public final Path basePath;
    public final JupyterNotebook notebook;
    public final Set<String> prefixes;

    /** The default logger. Does not write anything to disk. */
    static class NoOpLogger extends JupyterLogger {

        /** Creates a new JupyterLogger that does not write anything to disk. */
        public NoOpLogger() {
            super();
        }

        @Override
        public String makeFile(String fileName, String data) {
            return "";
        }

        @Override
        public JupyterNotebook.CodeCell addCSV(
                String prefix, String indexCol, List<List<Object>> csv) {
            return new JupyterNotebook.CodeCell();
        }

        @Override
        public void close() throws IOException {}
    }

    /**
     * Initialises the logger. Cells added before this method is called will be lost.
     *
     * <p>Note: The notebook file will only be written when close() is called. This happens
     * automatically on exit. Close() can be called manually if writing on exit does not happen
     * reliably.
     *
     * @param basePath The base path for the logger files. Will be created is necessary.
     */
    public static void initialise(String basePath) {
        synchronized (lock) {
            if (instance != null && !(instance instanceof NoOpLogger)) {
                throw new IllegalStateException("JupyterLogger is already initialised");
            }
            try {
                SimpleDateFormat formatter = new SimpleDateFormat("yyyy-M-d_HH-mm-ss");
                String path = String.valueOf(Path.of(basePath, formatter.format(new Date())));
                instance = new JupyterLogger(path);
                Runtime.getRuntime().addShutdownHook(new Thread(JupyterLogger::shutdownHook));
            } catch (IOException x) {
                throw new RuntimeException(x);
            }
        }
    }

    static void shutdownHook() {
        if (instance != null) {
            try {
                instance.close();
            } catch (IOException x) {
                x.printStackTrace();
            }
        }
    }

    /**
     * Returns the logger.
     *
     * @return
     */
    public static JupyterLogger logger() {
        if (instance == null) {
            instance = new NoOpLogger();
        }
        return instance;
    }

    public String getResource(String path) {
        try {
            Path p = Paths.get(getClass().getClassLoader().getResource(path).toURI());
            return Files.readString(p);
        } catch (IOException | URISyntaxException x) {
            throw new RuntimeException(x);
        }
    }

    /**
     * Creates a new JupyterLogger.
     *
     * @param basePath
     * @throws IOException if basePath cannot be created
     */
    public JupyterLogger(String basePath) throws IOException {
        this.basePath = Path.of(basePath);
        if (!this.basePath.toFile().exists()) {
            Path path = Files.createDirectories(this.basePath);
            System.out.println(
                    "JupyterLogging enabled. Logging to " + path.toAbsolutePath().toString());
        }
        makeFile("loggerwidgets.py", getResource("jupyterLogger/loggerwidgets.py"));
        this.notebook = new JupyterNotebook();
        this.prefixes = new HashSet<>();
        addCode(
                        "import json\n"
                                + "import pandas as pd\n"
                                + "import matplotlib.pyplot as plt\n"
                                + "\n"
                                + "from loggerwidgets import LogMap\n")
                .hideSource();
    }

    // this only exists for the NoOpLogger
    protected JupyterLogger() {
        basePath = Path.of("");
        notebook = new JupyterNotebook();
        prefixes = new HashSet<>();
    }

    /**
     * Returns a guaranteed unique prefix for the current notebook. This prefix can be used for
     * Python variables in Code cells.
     *
     * @param prefix a prefix
     * @return the prefix if is already unique. Otherwise, a modified prefix.
     */
    public synchronized String uniquePrefix(String prefix) {
        String candidate = prefix.replace(" ", "_");
        int count = 0;
        while (prefixes.contains(candidate)) {
            candidate = prefix + "_" + count;
            count++;
        }
        prefixes.add(candidate);
        return candidate;
    }

    /**
     * Write the specified data to a file.
     *
     * @param fileName must be unique. Use uniquePrefix() to obtain a unique name.
     * @param data The text data to write to the file
     * @return the file name
     */
    public String makeFile(String fileName, String data) {
        Path path = basePath.resolve(fileName);
        Preconditions.checkArgument(!Files.exists(path));

        try (FileWriter writer = new FileWriter(path.toAbsolutePath().toString())) {
            writer.write(data);
        } catch (IOException x) {
            throw new RuntimeException("Could not write " + fileName, x);
        }
        return fileName;
    }

    /**
     * Adds a markdown cell to the logger with the specified text.
     *
     * @param markDown markdown formatted text
     * @return the markdown cell.
     */
    public JupyterNotebook.MarkdownCell addMarkDown(String markDown) {
        JupyterNotebook.MarkdownCell cell = new JupyterNotebook.MarkdownCell(markDown);
        notebook.add(cell);
        return cell;
    }

    /**
     * Adds a code cell with the specified source text.
     *
     * @param code Python code
     * @return the code cell.
     */
    public JupyterNotebook.CodeCell addCode(String code) {
        JupyterNotebook.CodeCell cell = new JupyterNotebook.CodeCell(code);
        notebook.add(cell);
        return cell;
    }

    /**
     * Adds an empty MFD plot to the notebook.
     *
     * @param prefix the prefix to use for Python variables
     * @return an MFDPlot that can be used to add MFDs
     */
    public MFDPlot addMFDPlot(String prefix) {
        MFDPlot cell = new MFDPlot(prefix);
        cell.hideSource();
        notebook.add(cell);
        return cell;
    }

    /** A cell representing an MFD plot. */
    public class MFDPlot extends CSVCell {
        List<Double> xValues;

        /**
         * Creates a new MFD plot.
         *
         * @param prefix
         */
        public MFDPlot(String prefix) {
            super(prefix, "magnitude", new ArrayList<>());
        }

        // TODO: ensure that all MFD buckets align.

        /**
         * Add an MFD to the plot.
         *
         * @param name The display name of the MFD
         * @param mfd The MFD data
         */
        public void addMFD(String name, IncrementalMagFreqDist mfd) {

            if (csv.isEmpty()) {
                // fill index with x values
                // first element is empty to allow for header row
                csv.add(new ArrayList<>(List.of("magnitude")));
                for (double x : mfd.xValues()) {
                    csv.add(new ArrayList<>(List.of(x)));
                }
            }
            // stick mfd y values into the next column
            // with name as the header
            csv.get(0).add(name);
            List<Double> ys = mfd.yValues();
            for (int i = 0; i < ys.size(); i++) {
                csv.get(i + 1).add(ys.get(i));
            }
        }

        /** Method for rendering the cell. */
        @Override
        public String getSource() {
            String source = super.getSource();
            source += "%prefix%.plot.line().set_yscale('log');".replace("%prefix%", prefix);
            return source;
        }
    }

    /**
     * Add an empty map that can display GeoJson layers.
     *
     * @param prefix Python variables prefix
     * @param lat map centre latitude
     * @param lon map centre longitude
     * @param zoom leaflet zoom level
     * @return an empty MapPlot that can be used to add GeoJSON layers
     */
    public MapCell addMap(String prefix, double lat, double lon, int zoom) {
        String uniquePrefix = uniquePrefix(prefix);
        MapCell mapCell = new MapCell(uniquePrefix, lat, lon, zoom);
        notebook.add(mapCell);
        return mapCell;
    }

    /**
     * Creates an empty map centred on NZ.
     *
     * @param prefix Python variables prefix
     * @return an empty MapPlot that can be used to add GeoJSON layers
     */
    public MapCell addMap(String prefix) {
        return addMap(prefix, -41.5, 175, 5);
    }

    /**
     * Adds data as a CSV
     *
     * @param prefix Python variables prefix
     * @param csv a list of rows of String objects
     * @return the cell.
     */
    public JupyterNotebook.CodeCell addCSV(String prefix, List<List<Object>> csv) {
        return addCSV(prefix, null, csv);
    }

    /**
     * Adds data as a CSV
     *
     * @param prefix Python variables prefix
     * @param indexCol index_col for pandas dataFrame
     * @param csv a list of rows of String objects
     * @return the cell.
     */
    public JupyterNotebook.CodeCell addCSV(String prefix, String indexCol, List<List<Object>> csv) {
        CSVCell cell = new CSVCell(prefix, indexCol, csv);
        cell.hideSource();
        notebook.add(cell);
        return cell;
    }

    /**
     * Closes the logger and writes all files to disk.
     *
     * @throws IOException
     */
    @Override
    public void close() throws IOException {
        String json = notebook.toJson();
        try (FileWriter writer = new FileWriter(basePath.resolve("notebook.ipynb").toString())) {
            writer.write(json);
        }
    }
}

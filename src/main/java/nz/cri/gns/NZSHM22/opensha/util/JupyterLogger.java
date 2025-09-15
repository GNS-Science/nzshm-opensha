package nz.cri.gns.NZSHM22.opensha.util;

import com.google.common.base.Preconditions;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import org.opensha.commons.data.CSVWriter;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

/**
 * A facility to easily create a Jupyter notebook from debug data. Jupyterlogger.initialise(); needs
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
        public JupyterNotebook.CodeCell addCSV(String prefix, List<List<Object>> csv) {
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

    /**
     * Creates a new JupyterLogger.
     *
     * @param basePath
     * @throws IOException if basePath cannot be created
     */
    public JupyterLogger(String basePath) throws IOException {
        this.basePath = Path.of(basePath);
        if (!this.basePath.toFile().exists()) {
            Files.createDirectories(this.basePath);
        }
        this.notebook = new JupyterNotebook();
        this.prefixes = new HashSet<>();
        addCode(
                        "import json\n"
                                + "import pandas as pd\n"
                                + "import matplotlib.pyplot as plt\n"
                                + "\n"
                                + "from ipyleaflet import Map, GeoJSON, LegendControl, FullScreenControl, Popup, ScaleControl, WidgetControl\n"
                                + "from ipywidgets import HTML")
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
        String candidate = prefix;
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
            super(prefix);
            csv = new ArrayList<>();
        }

        // TODO: ensure that all MFD buckets align.
        /**
         * Add an MFD to the plot.
         *
         * @param name The display name of the MFD
         * @param mfd The MFD data
         */
        public void addMFD(String name, IncrementalMagFreqDist mfd) {
            if (xValues == null) {
                xValues = mfd.xValues();
                List<Object> headerRow = new ArrayList<>(List.of("magnitude"));
                headerRow.addAll(xValues);
                csv.add(headerRow);
            } else {
                if (!xValues.equals(mfd.xValues())) {
                    return;
                }
            }
            List<Object> row = new ArrayList<>(List.of(name));
            row.addAll(mfd.yValues());
            csv.add(row);
        }

        /** Method for rendering the cell. */
        @Override
        public String getSource() {
            String source = super.getSource();
            source +=
                    ("xs = [float(x) for x in list(%prefix%.columns.values)[1:]]\n"
                                    + "fig, axs = plt.subplots()\n"
                                    + "axs.set_yscale('log')\n"
                                    + "for index, row in %prefix%.iterrows():\n"
                                    + "    axs.plot (xs, row[1:].to_numpy())\n"
                                    + "axs.legend(%prefix%['magnitude'])\n")
                            .replace("%prefix%", prefix);
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
    public MapPlot addMap(String prefix, double lat, double lon, int zoom) {
        String uniquePrefix = uniquePrefix(prefix);
        MapPlot mapCell = new MapPlot(uniquePrefix, lat, lon, zoom);
        notebook.add(mapCell);
        return mapCell;
    }

    /**
     * Creates an empty map centred on NZ.
     *
     * @param prefix Python variables prefix
     * @return an empty MapPlot that can be used to add GeoJSON layers
     */
    public MapPlot addMap(String prefix) {
        return addMap(prefix, -41.5, 175, 5);
    }

    /** A map plot that can display GeoJSON layers. */
    public class MapPlot extends JupyterNotebook.CodeCell {
        String prefix;
        double lat;
        double lon;
        int zoom;
        List<GeoJsonLayer> layers;
        List<String> palette = List.of("blue", "red", "green", "cyan", "orange");

        public MapPlot(String prefix, double lat, double lon, int zoom) {
            this.prefix = prefix;
            this.lat = lat;
            this.lon = lon;
            this.zoom = zoom;
            this.layers = new ArrayList<>();
            hideSource();
        }

        @Override
        public String getSource() {
            String layersCode =
                    layers.stream().map(GeoJsonLayer::getSource).collect(Collectors.joining("\n"));
            String legendContent =
                    layers.stream()
                            .map(layer -> "'" + layer.name + "':'" + layer.colour + "'")
                            .collect(Collectors.joining(","));
            return ("%name%_map = Map(center=[%lat%, %lon%], zoom=%zoom%)\n"
                            + "%name%_section_info = HTML()\n"
                            + "%name%_section_info.value = \"Hover over features for more details.\"\n"
                            + "%name%_widget_control = WidgetControl(widget=%name%_section_info, position='topright')\n"
                            + "%name%_map.add(%name%_widget_control)\n"
                            + "def %name%_callback(event, **kwargs):\n"
                            + "    %name%_section_info.value = \"<ul>\"\n"
                            + "    keys = kwargs[\"properties\"].keys()\n"
                            + "    for k,v in kwargs[\"properties\"].items():\n"
                            + "        %name%_section_info.value += (\"<li> \" + k + \": \" + str(v) +\"</li>\")\n"
                            + "    %name%_section_info.value += \"</ul>\"\n"
                            + "        \n"
                            + "%name%_map.add(LegendControl({"
                            + legendContent
                            + "}, title=''))\n"
                            + layersCode
                            + "%name%_map")
                    .replace("%name%", prefix)
                    .replace("%lat%", "" + lat)
                    .replace("%lon%", "" + lon)
                    .replace("%zoom%", "" + zoom);
        }

        /**
         * Returns the number of currently added layers.
         *
         * @return
         */
        public int getLayerCount() {
            return layers.size();
        }

        /**
         * Adds a GeoJSON layer to the map.
         *
         * @param name the name of the layer
         * @param geojsonData the GeoJSON data
         */
        public void addLayer(String name, String geojsonData) {
            layers.add(
                    new GeoJsonLayer(
                            name, geojsonData, palette.get(layers.size() % (palette.size() - 1))));
        }

        /** A GeoJSON layer */
        public class GeoJsonLayer {
            public String name;
            public String fileName;
            public String colour;

            public GeoJsonLayer(String name, String data, String colour) {
                this.name = MapPlot.this.prefix + "_" + name;
                this.fileName = makeFile(this.name + ".geojson", data);
                this.colour = colour;
            }

            public String getSource() {
                return ("with open('%fileName%') as json_file:\n"
                                + "    %layerName% = json.load(json_file)\n"
                                + "    json_file.close()\n"
                                + "%layerName%_g = GeoJSON(data=%layerName%, \n"
                                + "    style={'color': '%colour%', 'weight':4},\n"
                                + "    point_style={'color': '%colour%', 'weight':4},\n"
                                + "    hover_style={'color': 'white', 'weight':4})\n"
                                + "%layerName%_g.on_hover(%name%_callback)\n"
                                + "%layerName%_g.on_click(%name%_callback)\n"
                                + "\n"
                                + "%name%_map.add(%layerName%_g)\n")
                        .replace("%fileName%", fileName)
                        .replace("%layerName%", name)
                        .replace("%colour%", colour);
            }
        }
    }

    protected String writeCSV(String prefix, List<List<Object>> csv) {
        String fileName = prefix + ".csv";
        try {
            FileOutputStream out = new FileOutputStream(basePath.resolve(fileName).toString());
            CSVWriter csvWriter = new CSVWriter(out, false);
            for (List<Object> row : csv) {
                List<String> stringRow =
                        row.stream().map(String::valueOf).collect(Collectors.toList());
                csvWriter.write(stringRow);
            }
            csvWriter.flush();
            out.close();
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
        return fileName;
    }

    /**
     * Adds data as a CSV
     *
     * @param prefix Python variables prefix
     * @param csv a list of rows of String objects
     * @return the cell.
     */
    public JupyterNotebook.CodeCell addCSV(String prefix, List<List<Object>> csv) {
        CSVCell cell = new CSVCell(prefix, csv);
        cell.hideSource();
        notebook.add(cell);
        return cell;
    }

    /** A cell that can write a CSV file. Used by the addCSV() method. */
    public class CSVCell extends JupyterNotebook.CodeCell {
        List<List<Object>> csv;
        String prefix;

        protected CSVCell(String prefix) {
            this.prefix = uniquePrefix(prefix);
        }

        public CSVCell(String prefix, List<List<Object>> csv) {
            this(prefix);
            this.csv = csv;
        }

        @Override
        public String getSource() {
            String fileName = writeCSV(prefix, csv);
            String csvCode = "%name% = pd.read_csv('%fileName%')\n" + "%name%\n";
            return csvCode.replace("%name%", prefix).replace("%fileName%", fileName);
        }
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

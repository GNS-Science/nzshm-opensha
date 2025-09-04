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

/** A facility to easily create a Jupyter notebook from debug data. */
public class JupyterLogger implements Closeable {
    static final Object lock = new Object();
    static JupyterLogger instance;

    public final Path basePath;
    public final JupyterNotebook notebook;
    public final Set<String> prefixes;

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

    public JupyterNotebook.Cell addMarkDown(String markDown) {
        JupyterNotebook.Cell cell = new JupyterNotebook.MarkdownCell(markDown);
        notebook.add(cell);
        return cell;
    }

    public JupyterNotebook.Cell addCode(String code) {
        JupyterNotebook.Cell cell = new JupyterNotebook.CodeCell(code);
        notebook.add(cell);
        return cell;
    }

    public MFDCell addMFDPlot(String prefix) {
        MFDCell cell = new MFDCell(prefix);
        cell.hideSource();
        notebook.add(cell);
        return cell;
    }

    public class MFDCell extends CSVCell {
        List<Double> xValues;

        public MFDCell(String prefix) {
            super(prefix);
            csv = new ArrayList<>();
        }

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

    public MapCell addMap(String prefix, double lat, double lon, int zoom) {
        String uniquePrefix = uniquePrefix(prefix);
        MapCell mapCell = new MapCell(uniquePrefix, lat, lon, zoom);
        notebook.add(mapCell);
        return mapCell;
    }

    public MapCell addMap(String prefix) {
        return addMap(prefix, -41.5, 175, 5);
    }

    public class MapCell extends JupyterNotebook.CodeCell {
        String prefix;
        double lat;
        double lon;
        int zoom;
        List<GeoJsonLayer> layers;
        List<String> palette = List.of("blue", "red", "green", "cyan", "orange");

        public MapCell(String prefix, double lat, double lon, int zoom) {
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

        public int getLayerCount() {
            return layers.size();
        }

        public void addLayer(String name, String geojsonData) {
            layers.add(
                    new GeoJsonLayer(
                            name, geojsonData, palette.get(layers.size() % (palette.size() - 1))));
        }

        public class GeoJsonLayer {
            public String name;
            public String fileName;
            public String colour;

            public GeoJsonLayer(String name, String data, String colour) {
                this.name = MapCell.this.prefix + "_" + name;
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

    public JupyterNotebook.CodeCell addCSV(String prefix, List<List<Object>> csv) {
        CSVCell cell = new CSVCell(prefix, csv);
        cell.hideSource();
        notebook.add(cell);
        return cell;
    }

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

    @Override
    public void close() throws IOException {
        String json = notebook.toJson();
        try (FileWriter writer = new FileWriter(basePath.resolve("notebook.ipynb").toString())) {
            writer.write(json);
        }
    }
}

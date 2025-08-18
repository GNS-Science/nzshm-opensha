package nz.cri.gns.NZSHM22.opensha.util;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import com.google.common.base.Preconditions;
import org.opensha.commons.data.CSVWriter;

/** A facility to easily create a Jupyter notebook from debug data. */
public class JupyterLogger implements Closeable {
    static final Object lock = new Object();
    static String defaultBasePath = "jupyterLog/logs";
    static JupyterLogger instance;

    public final Path basePath;
    public final JupyterNotebook notebook;
    public final Set<String> prefixes;

    /**
     * Can be used if a base path other than "jupyterLog/logs" is desired.
     *
     * @param basePath the base path
     */
    public static void setBasePath(String basePath) {
        if (instance != null) {
            throw new IllegalStateException(
                    "The logger has already been created. Please set the base path before the logger is used.");
        }
        defaultBasePath = basePath;
    }

    protected static void setup() {
        synchronized (lock) {
            if (instance != null) {
                return;
            }
            try {
                SimpleDateFormat formatter = new SimpleDateFormat("yyyy-M-d_HH-mm-ss");
                String basePath = String.valueOf(Path.of(defaultBasePath, formatter.format(new Date())));
                instance = new JupyterLogger(basePath);
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
            setup();
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
                                + "\n"
                                + "from ipyleaflet import Map, GeoJSON, LegendControl, FullScreenControl, Popup, ScaleControl, WidgetControl\n"
                                + "from ipywidgets import HTML")
                .hideSource();
    }

    public synchronized String uniquePrefix(String prefix){
        String candidate = prefix;
        int count = 0;
        while(prefixes.contains(candidate)){
            candidate = prefix + "_"+count;
            count++;
        }
        prefixes.add(candidate);
        return candidate;
    }

    /**
     * Write the specified data to a file.
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
        JupyterNotebook.Cell cell = new JupyterNotebook.CodeCell().setSource(code);
        notebook.add(cell);
        return cell;
    }

    public MapCell addMap(String prefix, double lat, double lon, int zoom){
        String uniquePrefix = uniquePrefix(prefix);
        MapCell mapCell =  new MapCell(uniquePrefix, lat, lon, zoom);
        mapCell.hideSource();
        notebook.add(mapCell);
        return mapCell;
    }

    public class MapCell extends JupyterNotebook.CodeCell {
        String prefix;
        double lat;
        double lon;
        int zoom;
        List<GeoJsonLayer> layers;
        List<String> palette = List.of("blue", "red", "green", "cyan", "orange");

        public MapCell(String prefix, double lat, double lon, int zoom){
            this.prefix = prefix;
            this.lat = lat;
            this.lon = lon;
            this.zoom = zoom;
            this.layers = new ArrayList<>();
        }

        @Override
        public String getSource(){
            String layersCode = layers.stream().map(GeoJsonLayer::getSource).collect(Collectors.joining("\n"));
            String legendContent = layers.stream().map(layer -> "'" + layer.name +"':'"+layer.colour+"'").collect(Collectors.joining(","));
            return  (                            "%name%_map = Map(center=[%lat%, %lon%], zoom=%zoom%)\n"
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
                    + "%name%_map.add(LegendControl({"+legendContent+"}, title=''))\n"
                    + layersCode
                    + "%name%_map")
            .replace("%name%", prefix)
                            .replace("%lat%", "" + lat)
                            .replace("%lon%", "" + lon)
                            .replace("%zoom%", "" + zoom);
        }

        public int getLayerCount(){
            return layers.size();
        }

        public void addLayer(String name, String geojsonData){
            layers.add(new GeoJsonLayer(name, geojsonData, palette.get(layers.size() % (palette.size()-1))));
        }

        public class GeoJsonLayer{
            public String name;
            public String fileName;
            public String colour;
            public GeoJsonLayer(String name, String data, String colour) {
                this.name = MapCell.this.prefix + "_"+name;
                this.fileName = makeFile(this.name+"geojson", data);
                this.colour = colour;
            }

            public String getSource() {
                return ("with open('%fileName%') as json_file:\n"
                        + "    %layerName% = json.load(json_file)\n"
                        + "    json_file.close()\n"
                        + "%layerName%_g = GeoJSON(data=%layerName%, \n"
                        + "    style={'color': '%colour%', 'weight':4},\n"
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

    public JupyterNotebook.Cell addMap(
            String prefix, String geoJson, double lat, double lon, int zoom) {
        String uniquePrefix = uniquePrefix(prefix);
        String fileName = makeFile(uniquePrefix + ".geojson", geoJson);
        String mapCode =
                "with open('%fileName%') as json_file:\n"
                        + "    %name% = json.load(json_file)\n"
                        + "    json_file.close()\n"
                        + "%name%_map = Map(center=[%lat%, %lon%], zoom=%zoom%)\n"
                        + "%name%_section_info = HTML()\n"
                        + "%name%_section_info.value = \"Hover over features for more details.\"\n"
                        + "%name%_widget_control = WidgetControl(widget=%name%_section_info, position='topright')\n"
                        + "def %name%_callback(event, **kwargs):\n"
                        + "    %name%_section_info.value = \"<ul>\"\n"
                        + "    keys = kwargs[\"properties\"].keys()\n"
                        + "    for k,v in kwargs[\"properties\"].items():\n"
                        + "        %name%_section_info.value += (\"<li> \" + k + \": \" + str(v) +\"</li>\")\n"
                        + "    %name%_section_info.value += \"</ul>\"\n"
                        + "        \n"
                        + "%name%_g = GeoJSON(data=%name%, \n"
                        + "    hover_style={'color': 'white', 'dashArray': '0', 'fillOpacity': 0.1})\n"
                        + "%name%_g.on_hover(%name%_callback)\n"
                        + "%name%_g.on_click(%name%_callback)\n"
                        + "\n"
                        + "%name%_map.add(%name%_g)\n"
                        + "%name%_map.add(%name%_widget_control)\n"
                        + "%name%_map";
        mapCode =
                mapCode.replace("%name%", uniquePrefix)
                        .replace("%fileName%", fileName)
                        .replace("%lat%", "" + lat)
                        .replace("%lon%", "" + lon)
                        .replace("%zoom%", "" + zoom);

        JupyterNotebook.Cell cell = new JupyterNotebook.CodeCell().setSource(mapCode).hideSource();
        notebook.add(cell);
        return cell;
    }

    public MapCell addMap(String prefix) {
        return addMap(prefix, -41.5, 175, 5);
    }

    public JupyterNotebook.Cell addMap(String prefix, String geoJson) {
        return addMap(prefix, geoJson, -41.5, 175, 5);
    }

    public JupyterNotebook.Cell addCSV(String prefix, List<List<Object>> csv) {
        String uniquePrefix = uniquePrefix(prefix);
        try {
            FileOutputStream out = new FileOutputStream(basePath.resolve(uniquePrefix) + ".csv");
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
        String csvCode = "%name% = pd.read_csv('%fileName%')\n" + "%name%";
        csvCode = csvCode.replace("%name%", uniquePrefix).replace("%fileName%", uniquePrefix + ".csv");
        JupyterNotebook.Cell cell = new JupyterNotebook.CodeCell().setSource(csvCode).hideSource();
        notebook.add(cell);
        return cell;
    }

    @Override
    public void close() throws IOException {
        String json = notebook.toJson();
        try (FileWriter writer = new FileWriter(basePath.resolve("notebook.ipynb").toString())) {
            writer.write(json);
        }
    }
}

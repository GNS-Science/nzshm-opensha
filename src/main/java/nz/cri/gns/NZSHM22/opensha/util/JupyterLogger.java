package nz.cri.gns.NZSHM22.opensha.util;

import java.io.Closeable;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class JupyterLogger implements Closeable {

    static JupyterLogger instance;

    public final Path basePath;
    public final JupyterNotebook notebook;

    public static JupyterLogger setup(String basePath) {
        try {
            instance = new JupyterLogger(basePath);
            instance.addCode(
                            "import json\n"
                                    + "\n"
                                    + "from ipyleaflet import Map, GeoJSON, LegendControl, FullScreenControl, Popup, ScaleControl, WidgetControl\n"
                                    + "from ipywidgets import HTML")
                    .hideSource();

            Runtime.getRuntime().addShutdownHook(new Thread(JupyterLogger::shutdownHook));
            return instance;
        } catch (IOException x) {
            throw new RuntimeException(x);
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

    public static JupyterLogger logger() {
        if (instance == null) {
            setup("jupyterLog");
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
    }

    public String makeFile(String fileName, String data) {
        Path path = basePath.resolve(fileName);
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

    public JupyterNotebook.Cell addMap(
            String name, String geoJson, double lat, double lon, int zoom) {
        String fileName = makeFile(name + ".geojson", geoJson);
        String mapCode =
                "with open('%fileName%') as json_file:\n" +
                        "    %name% = json.load(json_file)\n" +
                        "    json_file.close()\n" +
                        "%name%_map = Map(center=[%lat%, %lon%], zoom=%zoom%)\n" +
                        "%name%_section_info = HTML()\n" +
                        "%name%_section_info.value = \"Hover over features for more details.\"\n" +
                        "%name%_widget_control = WidgetControl(widget=%name%_section_info, position='topright')\n" +
                        "def %name%_callback(event, **kwargs):\n" +
                        "    %name%_section_info.value = \"<ul>\"\n" +
                        "    keys = kwargs[\"properties\"].keys()\n" +
                        "    for k,v in kwargs[\"properties\"].items():\n" +
                        "        %name%_section_info.value += (\"<li> \" + k + \": \" + str(v) +\"</li>\")\n" +
                        "    %name%_section_info.value += \"</ul>\"\n" +
                        "        \n" +
                        "%name%_g = GeoJSON(data=%name%, \n" +
                        "    hover_style={'color': 'white', 'dashArray': '0', 'fillOpacity': 0.1})\n" +
                        "%name%_g.on_hover(%name%_callback)\n" +
                        "%name%_g.on_click(%name%_callback)\n" +
                        "\n" +
                        "%name%_map.add(%name%_g)\n" +
                        "%name%_map.add(%name%_widget_control)\n" +
                        "%name%_map";
        mapCode = mapCode.replace("%name%", name)
                .replace("%fileName%", fileName)
                .replace("%lat%", ""+lat)
                .replace("%lon%", ""+lon)
                .replace("%zoom%", ""+zoom);

        JupyterNotebook.Cell cell = new JupyterNotebook.CodeCell().setSource(mapCode).hideSource();
        notebook.add(cell);
        return cell;
    }

    public JupyterNotebook.Cell addMap(String name, String geoJson) {
        return addMap(name, geoJson, -41.5, 175, 5);
    }

    @Override
    public void close() throws IOException {
        String json = notebook.toJson();
        try (FileWriter writer = new FileWriter(basePath.resolve("notebook.ipynb").toString())) {
            writer.write(json);
        }
    }
}

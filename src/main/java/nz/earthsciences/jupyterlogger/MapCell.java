package nz.earthsciences.jupyterlogger;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A map plot that can display GeoJSON layers.
 */
public class MapCell extends JupyterNotebook.CodeCell {
    String prefix;
    double lat;
    double lon;
    int zoom;
    List<GeoJsonLayer> layers;

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
        return ("%name%_map = LogMap(center=[%lat%, %lon%], zoom=%zoom%)\n"
                + layersCode + "\n"
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
     * @param name        the name of the layer
     * @param geojsonData the GeoJSON data
     */
    public void addLayer(String name, String geojsonData) {
        layers.add(new GeoJsonLayer(name, geojsonData));
    }

    /**
     * A GeoJSON layer
     */
    public static class GeoJsonLayer {
        public String name;
        public String layerName;
        public String fileName;

        public GeoJsonLayer(String name, String data) {
            this.name = name;
            this.layerName = JupyterLogger.logger().uniquePrefix(name);
            this.fileName = JupyterLogger.logger().makeFile(this.layerName + ".geojson", data);
        }

        public String getSource() {
            return "(%layerName%, %layerName%_df) = %name%_map.add_layer('%humanName%', '%fileName%')"
                    .replace("%fileName%", fileName)
                    .replace("%layerName%", layerName)
                    .replace("%humanName%", name);
        }
    }
}


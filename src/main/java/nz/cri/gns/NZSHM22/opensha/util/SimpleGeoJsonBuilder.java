package nz.cri.gns.NZSHM22.opensha.util;

import com.google.common.base.Preconditions;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.geo.json.FeatureCollection;
import org.opensha.commons.geo.json.FeatureProperties;
import org.opensha.commons.geo.json.Geometry;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A utility to quickly write opensha geometry to a GeoJSON file.
 */

public class SimpleGeoJsonBuilder {

    List<Feature> features = new ArrayList<>();

    public FeatureProperties addLocation(Location location) {
        FeatureProperties properties = new FeatureProperties();
        features.add(new Feature(new Geometry.Point(location), properties));
        return properties;
    }

    public FeatureProperties addLocation(String name, Location location) {
        FeatureProperties properties = new FeatureProperties();
        properties.put("name", name);
        features.add(new Feature(new Geometry.Point(location), properties));
        return properties;
    }

    public FeatureProperties addLocation(Location location, String... properties) {
        Preconditions.checkArgument((properties.length % 2) == 0, "Properties must be key/value pairs");

        FeatureProperties props = new FeatureProperties();
        for (int i = 0; i < properties.length - 1; i += 2) {
            props.put(properties[i], properties[i + 1]);
        }

        features.add(new Feature(new Geometry.Point(location), props));
        return props;
    }

    /**
     * Automatically includes the properties of the section.
     *
     * @param section
     * @return
     */
    public FeatureProperties addFaultSection(FaultSection section) {
        Feature feature = GeoJSONFaultSection.toFeature(section);
        features.add(feature);
        return feature.properties;
    }

    public FeatureProperties addRegion(Region region) {
        Feature feature = region.toFeature();
        features.add(feature);
        return feature.properties;
    }

    public String toJSON() {
        FeatureCollection featureCollection = new FeatureCollection(features);
        try {
            return featureCollection.toJSON();
        } catch (IOException x) {
            x.printStackTrace();
        }
        return null;
    }

    public void toJSON(File file) {
        try (FileWriter out = new FileWriter(file)) {
            out.write(toJSON());
        } catch (Exception x) {
            x.printStackTrace();
            throw new RuntimeException(x);
        }
    }

    public void toJSON(String fileName) {
        toJSON(new File(fileName));
    }
}

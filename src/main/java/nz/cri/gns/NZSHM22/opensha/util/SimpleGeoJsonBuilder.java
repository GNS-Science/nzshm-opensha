package nz.cri.gns.NZSHM22.opensha.util;

import com.google.common.base.Preconditions;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
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
import java.util.Arrays;
import java.util.List;

/**
 * A utility to quickly write opensha geometry to a GeoJSON file.
 */

public class SimpleGeoJsonBuilder {

    List<Feature> features = new ArrayList<>();

    public FeatureProperties addFeature(Feature feature) {
        features.add(feature);
        return feature.properties;
    }

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

    public FeatureProperties addFaultSectionPerimeter(FaultSection section) {
        LocationList locations = section.getFaultSurface(1, false, false).getPerimeter();
        FeatureProperties props = new FeatureProperties();
        features.add(new Feature(new Geometry.LineString(locations), props));
        return props;
    }

    public FeatureProperties addFaultSectionPolygon(FaultSection section) {
        LocationList locations = section.getFaultSurface(1, false, false).getPerimeter();
        FeatureProperties props = new FeatureProperties(GeoJSONFaultSection.toFeature(section).properties);
        features.add(new Feature(new Geometry.Polygon(locations), props));
        return props;
    }

    public FeatureProperties addLine(Location... locations) {
        LocationList locs = new LocationList();
        locs.addAll(Arrays.asList(locations));
        return addLine(locs);
    }

    public FeatureProperties addLine(List<Location> locations) {
        FeatureProperties properties = new FeatureProperties();
        LocationList locs = new LocationList();
        locs.addAll(locations);
        Geometry geometry = new Geometry.LineString(locs);
        features.add(new Feature("", geometry, properties));
        return properties;
    }

    public FeatureProperties addRegion(Region region) {
        Feature feature = region.toFeature();
        features.add(feature);
        return feature.properties;
    }

    public FeatureProperties addRegion(Region region, String colour, double opacity){
        FeatureProperties props = addRegion(region);
        props.set(FeatureProperties.FILL_OPACITY_PROP, opacity);
        props.set(FeatureProperties.FILL_COLOR_PROP, colour);
        return props;
    }

    public FeatureProperties setLineColour(FeatureProperties props, String cssColour, double opacity){
        props.set(FeatureProperties.STROKE_COLOR_PROP, cssColour);
        props.set(FeatureProperties.STROKE_OPACITY_PROP, opacity);
        return props;
    }

    public FeatureProperties setPolygonColour(FeatureProperties props, String cssColour) {
        props.set(FeatureProperties.FILL_COLOR_PROP, cssColour);
        return props;
    }

    public FeatureProperties setLineColour(FeatureProperties props, String cssColour){
        props.set(FeatureProperties.STROKE_COLOR_PROP, cssColour);
        return props;
    }

    public FeatureProperties setLineWidth(FeatureProperties props, int width){
        props.set(FeatureProperties.STROKE_WIDTH_PROP, width);
        return props;
    }

    public String toJSON() {
        FeatureCollection featureCollection = new FeatureCollection(features);
        return featureCollection.toJSON();
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

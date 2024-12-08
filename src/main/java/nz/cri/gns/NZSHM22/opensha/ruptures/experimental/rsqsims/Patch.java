package nz.cri.gns.NZSHM22.opensha.ruptures.experimental.rsqsims;

import org.opensha.commons.geo.*;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.geo.json.FeatureProperties;
import org.opensha.commons.geo.json.Geometry;
import org.opensha.sha.faultSurface.FaultSection;

import java.util.ArrayList;
import java.util.List;

public class Patch {
    public final LocationList locations;
    public final int id;
    public final double rake;
    public final double slip;
    public final double area;
    public int sectionIdFromZname = -1;

    public String zname;

    public final String[] row;

    public List<FaultSection> sections = new ArrayList<>();

    public int getNameSectionId() {
        return sectionIdFromZname;
    }

    public double getMaxLat() {
        return locations.stream().mapToDouble(l -> l.lat).max().getAsDouble();
    }

    public Patch(int id, LocationList locations, double rake, double slip, double area, String[] row) {
        this.id = id;
        this.rake = rake;
        this.slip = slip;
        this.locations = locations;
        this.row = row;
        this.area = area;
    }

    public static Patch create(int id, Location a, Location b, Location c, double rake, double slip, double area, String[] row) {
        LocationList locs = new LocationList();
        locs.add(a);
        locs.add(b);
        locs.add(c);
        return new Patch(id, locs, rake, slip, area, row);
    }

    public Feature toFeature() {
        LocationList list = new LocationList();
        list.addAll(locations);
        list.add(locations.first());
        Geometry geometry = new Geometry.LineString(list);
        FeatureProperties properties = new FeatureProperties();
        properties.set("patch_id", id);
        properties.set("rake", rake);
        properties.set("slip", slip);
        return new Feature(id, geometry, properties);
    }

    public Feature toPolygonFeature() {
        LocationList list = new LocationList();
        list.addAll(locations);
        list.add(locations.first());
        Geometry geometry = new Geometry.Polygon(list);
        FeatureProperties properties = new FeatureProperties();
        properties.set("patch_id", id);
        properties.set("rake", rake);
        properties.set("slip", slip);
        return new Feature(id, geometry, properties);
    }

}

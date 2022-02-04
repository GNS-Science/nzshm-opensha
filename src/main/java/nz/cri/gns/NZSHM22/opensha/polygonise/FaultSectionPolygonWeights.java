package nz.cri.gns.NZSHM22.opensha.polygonise;

import com.bbn.openmap.geo.Geo;
import com.bbn.openmap.geo.Intersection;
import nz.cri.gns.NZSHM22.opensha.util.SimpleGeoJsonBuilder;
import org.opensha.commons.geo.*;
import org.opensha.commons.geo.json.FeatureProperties;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.PolygonFaultGridAssociations;
import org.opensha.sha.faultSurface.FaultSection;

import java.util.ArrayList;
import java.util.List;

public class FaultSectionPolygonWeights {

    protected FaultSystemSolution solution;
    protected List<Section> sectionCache;
    protected SimpleGeoJsonBuilder geoJsonBuilder;

    public FaultSectionPolygonWeights(FaultSystemSolution solution){
        this.solution = solution;
        geoJsonBuilder = new SimpleGeoJsonBuilder();
        buildCache();
    }

    public Geo geo(Location l) {
        return new Geo(l.lat, l.lon);
    }

    protected class Section {
        public FaultSection section;
        public List<Geo> trace;
        public List<Geo> polygon;
        public Region originalPoly;

        public Section(FaultSection section) {
            this.section = section;
            trace = new ArrayList<>();
            polygon = new ArrayList<>();
            for (Location l : section.getFaultTrace()) {
                trace.add(geo(l));
            }
            originalPoly = solution.getRupSet().getModule(PolygonFaultGridAssociations.class).getPoly(section.getSectionId());
            for (Location l : originalPoly.getBorder()) {
                polygon.add(geo(l));
            }
            polygon.add(geo(originalPoly.getBorder().first()));
        }

        public boolean contains(Location l) {
            return originalPoly.contains(l);
        }

        public double polygonWeight(Location gridPoint) {

            Geo gridP = geo(gridPoint);

            LocationVector vector = new LocationVector(section.getDipDirection(), 50, 0);
            Location a = LocationUtils.location(gridPoint, vector);
            vector.reverse();
            Location b = LocationUtils.location(gridPoint, vector);

            Geo traceIntersection = getSegIntersection(geo(a), geo(b), trace);

            if (traceIntersection == null) {
                return -1;
            }

            double azimuth = traceIntersection.azimuth(gridP);
            Geo polygonIntersectProbe = traceIntersection.offset(20 / GeoTools.EARTH_RADIUS_MEAN, azimuth);
            Geo polygonIntersection = getSegIntersection(polygonIntersectProbe, gridP, polygon);

//        geoJsonBuilder.addLocation("polygonIntersectProbe", new Location(polygonIntersectProbe.getLatitude(), polygonIntersectProbe.getLongitude()));
//        geoJsonBuilder.addRegion(section.originalPoly);
//        geoJsonBuilder.addFaultSection(section.section);
//        geoJsonBuilder.addLocation("gridPoint", gridPoint);
//        if (polygonIntersection != null)
//            geoJsonBuilder.addLocation("polygonIntersection", new Location(polygonIntersection.getLatitude(), polygonIntersection.getLongitude()));
//        if (traceIntersection != null)
//            geoJsonBuilder.addLocation("traceIntersection", new Location(traceIntersection.getLatitude(), traceIntersection.getLongitude()));

            if (polygonIntersection == null) {
                return -1;
            }

            double borderDistance = gridP.distanceKM(polygonIntersection);
            double traceDistance = gridP.distanceKM(traceIntersection);
            return borderDistance / (borderDistance + traceDistance);
        }
    }

    protected void buildCache() {
        sectionCache = new ArrayList<>();
        for (FaultSection section : solution.getRupSet().getFaultSectionDataList()) {
            sectionCache.add(new Section(section));
        }
    }

    public static Geo getSegIntersection(Geo p1, Geo p2, List<Geo> segments) {
        for (int i = 1; i < segments.size(); i++) {
            Geo[] result = Intersection.getSegIntersection(p1, p2, segments.get(i - 1), segments.get(i));
            if (result[0] != null) {
                return result[0];
            } else if (result[1] != null) {
                return result[1];
            }
        }
        return null;
    }

    /**
     * Returns how close the location is to a fault trace within section polygons.
     * Returns 1 if the location is exactly on the trace for each polygon it is in.
     * Returns 0 if the location is exactly on the polygon border for each polygon it is in.
     * Returns -1 the location is not inside a polygon.
     * If the location is in more than one polygon, the weights are averaged.
     * @param location the location
     * @return the weight for the location
     */
    public double getWeight(Location location){
        double value = 0;
        int count = 0;
        for (Section section : sectionCache) {
            if (section.contains(location)) {
                double weight = section.polygonWeight(location);
                if(weight != -1) {
                    value += weight;
                    count++;
                }
            }
        }

        if (count > 0) {
            return value / count;
        } else {
            return -1;
        }
    }



    // for testing. creates geojson objects that represent a weight gradient cross a fault section
    public void scanSection(int sectionId) {
        Section section = sectionCache.get(sectionId);

        geoJsonBuilder.addRegion(section.originalPoly);

        double minLat = 1000;
        double maxLat = -1000;
        double minLon = 1000;
        double maxLon = -1000;
        for (Location l : section.originalPoly.getBorder()) {
            if (l.lat < minLat) minLat = l.lat;
            if (l.lat > maxLat) maxLat = l.lat;
            if (l.lon < minLon) minLon = l.lon;
            if (l.lon > maxLon) maxLon = l.lon;
        }

        double dLat = (maxLat - minLat) / 30;
        double dLon = (maxLon - minLon) / 30;

        for (double lat = minLat; lat < maxLat; lat += dLat) {
            for (double lon = minLon; lon < maxLon; lon += dLon) {
                if (section.contains(new Location(lat, lon))) {
                    double weight = section.polygonWeight(new Location(lat, lon));
                    if (weight == -1)
                        geoJsonBuilder.addLocation("fault", new Location(lat, lon));
                    else {
                        FeatureProperties props = geoJsonBuilder.addRegion(new Region(new Location(lat - dLat / 2, lon - dLon / 2), new Location(lat + dLat / 2, lon + dLon / 2)), "red", weight);
                        props.set("weight", weight);
                    }
                }
            }
        }
        geoJsonBuilder.addFaultSection(section.section);
    }


//        scanSection(1184);
//        scanSection(58);
//        scanSection(1424);
//        scanSection(1549);
//        scanSection(1550);
//
//        Section s = sectionCache.get(1184);
//        Location l = new Location(-42.7954, 175.3452);
//        System.out.println(polygonWeight(l, s));
//        s = sectionCache.get(58);
//        l = new Location(-43.1140, 170.9665);
//        System.out.println(polygonWeight(l, s));
//        s = sectionCache.get(1424);
//        l = new Location(-42.8941, 171.8083);
//        System.out.println(polygonWeight(l, s));
//        s = sectionCache.get(1549);
//        l = new Location(-43.0799, 171.7520);
//        System.out.println(polygonWeight(l, s));

}

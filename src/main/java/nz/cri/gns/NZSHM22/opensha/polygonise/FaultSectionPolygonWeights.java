package nz.cri.gns.NZSHM22.opensha.polygonise;

import com.bbn.openmap.geo.Geo;
import com.bbn.openmap.geo.Intersection;
import org.opensha.commons.geo.*;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.PolygonFaultGridAssociations;
import org.opensha.sha.faultSurface.FaultSection;

import java.util.ArrayList;
import java.util.List;

/**
 * A FaultSection represented in OpenMap geometry so that we can use OpenMap operations on it
 */
public class FaultSectionPolygonWeights {

    public FaultSection section;
    public List<Geo> trace;
    public List<Geo> polygon;
    public Region originalPoly;

    public FaultSectionPolygonWeights(PolygonFaultGridAssociations polys, FaultSection section) {
        this.section = section;
        trace = new ArrayList<>();
        polygon = new ArrayList<>();
        for (Location l : section.getFaultTrace()) {
            trace.add(geo(l));
        }
        originalPoly = polys.getPoly(section.getSectionId());
        for (Location l : originalPoly.getBorder()) {
            polygon.add(geo(l));
        }
        // close the polygon if necessary
        if(!originalPoly.getBorder().first().equals(originalPoly.getBorder().last())) {
            polygon.add(geo(originalPoly.getBorder().first()));
        }
    }

    public boolean contains(Location l) {
        return originalPoly.contains(l);
    }

    public double polygonWeight(Location gridPoint) {

        if (!contains(gridPoint)) {
            return -1;
        }

        Geo gridP = geo(gridPoint);

        LocationVector vector = new LocationVector(section.getDipDirection(), 50, 0);
        Location a = LocationUtils.location(gridPoint, vector);
        vector.reverse();
        Location b = LocationUtils.location(gridPoint, vector);

        // get the intersection of a 100km long line centered on the gridPoint and aligned with the dipDirection
        // and the fault trace.
        Geo traceIntersection = getSegIntersection(geo(a), geo(b), trace);

        if (traceIntersection == null) {
            // this can happen when gridPoint is right on the edge of a section.
            // we use different libraries to check if a point is in a polygon and for intersections.
            return -1;
        }

        // Get a point projected out from th trace intersection in the direction of gridPoint, but about 50km away.
        // This allows us to create a line segment that intersects with the polygon edge on the correct side of the trace.
        double azimuth = traceIntersection.azimuth(gridP);
        Geo polygonIntersectProbe = traceIntersection.offset(50 / GeoTools.EARTH_RADIUS_MEAN, azimuth);
        Geo polygonIntersection = getSegIntersection(polygonIntersectProbe, gridP, polygon);

        if (polygonIntersection == null) {
            return -1;
        }

        double borderDistance = gridP.distanceKM(polygonIntersection);
        double traceDistance = gridP.distanceKM(traceIntersection);
        return traceDistance / (borderDistance + traceDistance);
    }

    /**
     * Convenience method: turns opensha Location into OpenMap Geo
     *
     * @param l
     * @return
     */
    protected Geo geo(Location l) {
        return new Geo(l.lat, l.lon);
    }

    /**
     * Returns the intersection of a line segment and a list of segments.
     * Used to calculate the intersection of a line segment with a fault trace.
     * returns null if no intersection.
     *
     * @param p1
     * @param p2
     * @param segments
     * @return
     */
    protected static Geo getSegIntersection(Geo p1, Geo p2, List<Geo> segments) {
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

    public static List<FaultSectionPolygonWeights> fromSolution(FaultSystemSolution solution) {
        List<FaultSectionPolygonWeights> result = new ArrayList<>();
        PolygonFaultGridAssociations polys = solution.getRupSet().getModule(PolygonFaultGridAssociations.class);
        for (FaultSection section : solution.getRupSet().getFaultSectionDataList()) {
            result.add(new FaultSectionPolygonWeights(polys, section));
        }
        return result;
    }

//    // for testing. creates geojson objects that represent a weight gradient cross a fault section
//    public void scanSection(int sectionId) {
//        Section section = sectionCache.get(sectionId);
//
//        geoJsonBuilder.addRegion(section.originalPoly);
//
//        double minLat = 1000;
//        double maxLat = -1000;
//        double minLon = 1000;
//        double maxLon = -1000;
//        for (Location l : section.originalPoly.getBorder()) {
//            if (l.lat < minLat) minLat = l.lat;
//            if (l.lat > maxLat) maxLat = l.lat;
//            if (l.lon < minLon) minLon = l.lon;
//            if (l.lon > maxLon) maxLon = l.lon;
//        }
//
//        double dLat = (maxLat - minLat) / 30;
//        double dLon = (maxLon - minLon) / 30;
//
//        for (double lat = minLat; lat < maxLat; lat += dLat) {
//            for (double lon = minLon; lon < maxLon; lon += dLon) {
//                if (section.contains(new Location(lat, lon))) {
//                    double weight = section.polygonWeight(new Location(lat, lon));
//                    if (weight == -1)
//                        geoJsonBuilder.addLocation("fault", new Location(lat, lon));
//                    else {
//                        FeatureProperties props = geoJsonBuilder.addRegion(new Region(new Location(lat - dLat / 2, lon - dLon / 2), new Location(lat + dLat / 2, lon + dLon / 2)), "red", weight);
//                        props.set("weight", weight);
//                    }
//                }
//            }
//        }
//        geoJsonBuilder.addFaultSection(section.section);
//    }


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

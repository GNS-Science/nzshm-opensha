package nz.cri.gns.NZSHM22.opensha.polygonise;

import com.bbn.openmap.geo.Geo;
import com.bbn.openmap.geo.Intersection;
import com.google.common.base.Preconditions;
import org.opensha.commons.geo.*;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
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
        if (!originalPoly.getBorder().first().equals(originalPoly.getBorder().last())) {
            polygon.add(geo(originalPoly.getBorder().first()));
        }
    }

    public boolean contains(Location l) {
        return originalPoly.contains(l);
    }

    /**
     * Returns the distance of the point to the fault trace along the dip direction.
     * 0 means the point is on the fault trace
     * 1 means the point is on the outer polygon border
     *
     * @param gridPoint
     * @return
     */
    public double polygonWeight(Location gridPoint) {

        if (!contains(gridPoint)) {
            return -1;
        }

        Geo gridP = geo(gridPoint);

        LocationVector vector = new LocationVector(section.getDipDirection(), 50, 0);
        Geo a = geo(LocationUtils.location(gridPoint, vector));
        vector.reverse();
        Geo b = geo(LocationUtils.location(gridPoint, vector));

        // get the intersection of a 100km long line centered on the gridPoint and aligned with the dipDirection
        // and the fault trace.
        Geo traceIntersection = getSegIntersection(a, b, trace);

        if (traceIntersection == null) {
            // this can happen when gridPoint is right on the edge of a section.
            return -1;
        }

        double targetAzimuth = LocationUtils.azimuth(loc(traceIntersection), gridPoint);

        // get intersections of the 100km long line with the edges of the polygon
        List<Geo> edgesIntersections = getAllSegIntersections(a, b, polygon);
        Geo polygonIntersection = nearestAzimuth(targetAzimuth, loc(traceIntersection), edgesIntersections);

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

    protected Location loc(Geo geo) {
        return new Location(geo.getLatitude(), geo.getLongitude());
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

    /**
     * Returns the intersection points of a line p1, p2 and the borderSegments.
     *
     * @param p1
     * @param p2
     * @param segments
     * @return
     */
    protected static List<Geo> getAllSegIntersections(Geo p1, Geo p2, List<Geo> segments) {
        List<Geo> result = new ArrayList<>();
        for (int i = 1; i < segments.size(); i++) {
            Geo[] ints = Intersection.getSegIntersection(p1, p2, segments.get(i - 1), segments.get(i));
            if (ints[0] != null) {
                result.add(ints[0]);
            } else if (ints[1] != null) {
                result.add(ints[1]);
            }
        }
        return result;
    }

    static double azimuthDifference(double a, double b) {
        double diff = Math.abs(a - b);
        while (diff > 180) {
            diff = 360 - diff;
        }
        return diff;
    }

    protected Geo nearestAzimuth(double targetAzimuth, Location origin, List<Geo> candidates) {
        double closestDist = Double.MAX_VALUE;
        Geo nearest = null;
        for (Geo candidate : candidates) {
            double azimuth = LocationUtils.azimuth(origin, loc(candidate));
            double azDist = azimuthDifference(targetAzimuth, azimuth);
            if (azDist < closestDist) {
                closestDist = azDist;
                nearest = candidate;
            }
        }
        return nearest;
    }

    public static List<FaultSectionPolygonWeights> fromRupSet(FaultSystemRupSet rupSet) {
        List<FaultSectionPolygonWeights> result = new ArrayList<>();
        PolygonFaultGridAssociations polys = rupSet.getModule(PolygonFaultGridAssociations.class);
        for (FaultSection section : rupSet.getFaultSectionDataList()) {
            result.add(new FaultSectionPolygonWeights(polys, section));
        }
        return result;
    }

}

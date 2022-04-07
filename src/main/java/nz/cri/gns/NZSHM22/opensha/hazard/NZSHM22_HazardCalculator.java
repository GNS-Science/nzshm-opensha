package nz.cri.gns.NZSHM22.opensha.hazard;

import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.geo.Location;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates everything needed to calculate a hazard.
 */
public abstract class NZSHM22_HazardCalculator {

    /**
     * Takes a site and returns the hazard.
     * @param lat site latitude
     * @param lon site longitude
     * @return the hazard curve.
     */
    public abstract DiscretizedFunc calc(double lat, double lon);

    public DiscretizedFunc calc(Location location){
        return calc(location.getLatitude(), location.getLongitude());
    }

    /**
     * Same as calc but returns a list of x/y pairs.
     * @param lat
     * @param lon
     * @return
     */
    public List<List<Double>> tabulariseCalc(double lat, double lon){
        DiscretizedFunc func = calc(lat, lon);
        List<List<Double>> result = new ArrayList<>();
        for(Point2D point : func){
            List<Double> row = new ArrayList<>();
            row.add(point.getX());
            row.add(point.getY());
            result.add(row);
        }
        return result;
    }
}
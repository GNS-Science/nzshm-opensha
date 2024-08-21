package nz.cri.gns.NZSHM22.opensha.ruptures.experimental.rsqsims;

import com.google.common.base.Preconditions;
import org.opengis.geometry.DirectPosition;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.CoordinateOperation;
import org.opengis.referencing.operation.TransformException;
import org.opengis.util.FactoryException;
import org.apache.sis.referencing.CRS;
import org.apache.sis.referencing.CommonCRS;
import org.apache.sis.geometry.DirectPosition2D;
import org.opensha.commons.geo.Location;

/**
 * Converts coordinates from a specific coordinate system into WGS84.
 * <p>
 * Implementing classes need to set the operation property and implement toSourcePosition().
 * toString() should be overwritten for better error messages
 */
public abstract class CoordinateConverter {

    static CoordinateReferenceSystem targetCRS = CommonCRS.WGS84.geographic();
    protected CoordinateOperation operation;

    /**
     * Returns the equivalent WGS84 location. Elevation is not modified.
     *
     * @param easting
     * @param northing
     * @param elevation
     * @return
     */
    public Location toWGS84(double easting, double northing, double elevation) {
        try {
            DirectPosition2D original = toSourcePosition(easting, northing);
            DirectPosition transformed = operation.getMathTransform().transform(original, null);
            return new Location(transformed.getOrdinate(0), transformed.getOrdinate(1), elevation);
        } catch (TransformException x) {
            throw new RuntimeException(this + " could not transform " + easting + ", " + northing, x);
        }
    }

    protected abstract DirectPosition2D toSourcePosition(double easting, double northing);

    /**
     * Transforms Universal Transverse Mercator coordinates into WGS84 coordinates.
     */
    public static class UTM extends CoordinateConverter {
        final int zone;
        final boolean north;

        /**
         * Creates a UTM transformer for a specific UTM zone.
         *
         * @param zone  the zone
         * @param north whether the zone is in the northern hemisphere
         * @throws FactoryException
         */
        public UTM(int zone, boolean north) throws FactoryException {
            Preconditions.checkArgument(zone < 62 && zone > 0);
            this.zone = zone;
            this.north = north;
            // https://docs.up42.com/data/reference/utm#utm-wgs84-north
            int epsCode = (north ? 32600 : 32700) + zone;
            CoordinateReferenceSystem sourceCRS = CRS.forCode("EPSG:" + epsCode);
            operation = CRS.findOperation(sourceCRS, targetCRS, null);
        }

        @Override
        protected DirectPosition2D toSourcePosition(double easting, double northing) {
            return new DirectPosition2D(easting, northing);
        }

        @Override
        public String toString() {
            return "UTM " + zone + (north ? "N" : "S") + " -> WGS84";
        }
    }

    /**
     * Converts New Zealand Transverse Mercator coordinates to WGS84
     * https://epsg.io/2193
     */
    public static class NZTM extends CoordinateConverter {

        /**
         * Creates an NZTM transformer.
         *
         * @throws FactoryException
         */
        public NZTM() throws FactoryException {
            CoordinateReferenceSystem sourceCRS = CRS.forCode("EPSG:2193");
            operation = CRS.findOperation(sourceCRS, targetCRS, null);
        }

        @Override
        protected DirectPosition2D toSourcePosition(double easting, double northing) {
            return new DirectPosition2D(northing, easting);
        }

        @Override
        public String toString() {
            return "NZTM -> WGS84";
        }
    }
}

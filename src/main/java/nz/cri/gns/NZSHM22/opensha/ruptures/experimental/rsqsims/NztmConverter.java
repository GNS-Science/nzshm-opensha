package nz.cri.gns.NZSHM22.opensha.ruptures.experimental.rsqsims;

import org.opengis.geometry.DirectPosition;
import org.opengis.referencing.crs.CRSAuthorityFactory;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.CoordinateOperation;
import org.opengis.referencing.operation.TransformException;
import org.opengis.util.FactoryException;
import org.apache.sis.referencing.CRS;
import org.apache.sis.referencing.CommonCRS;
import org.apache.sis.geometry.DirectPosition2D;


public class NztmConverter {
    final CoordinateReferenceSystem targetCRS ;
    final CoordinateReferenceSystem sourceCRS ;
    final CoordinateOperation operation;

    public NztmConverter() throws FactoryException {
        targetCRS = CommonCRS.WGS84.geographic();
        CRSAuthorityFactory authorityFactory = CRS.getAuthorityFactory("EPSG");
        sourceCRS = authorityFactory.createCoordinateReferenceSystem("EPSG:2193");
        operation = CRS.findOperation(sourceCRS, targetCRS, null);
        System.out.println(operation);
    }

    public DirectPosition toWGS84(double easting, double northing) throws TransformException {
        DirectPosition2D original = new DirectPosition2D(easting, northing);
        DirectPosition transformed = operation.getMathTransform().transform(original, null);
        return transformed;
    }
}

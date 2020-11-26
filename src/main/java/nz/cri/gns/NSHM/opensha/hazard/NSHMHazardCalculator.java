package nz.cri.gns.NSHM.opensha.hazard;

import org.opensha.commons.data.function.DiscretizedFunc;

/**
 * Encapsulates everything needed to calculate a hazard.
 */
public interface NSHMHazardCalculator {

    /**
     * Takes a site and returns the hazard.
     * @param lat site latitude
     * @param lon site longitude
     * @return the hazard curve.
     */
    public DiscretizedFunc calc(double lat, double lon);
}
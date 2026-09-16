package nz.cri.gns.NZSHM22.opensha.hazard.joint;

import java.awt.Color;
import java.util.List;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.commons.gui.plot.GeographicMapMaker;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.sha.faultSurface.FaultSection;

/**
 * The pieces {@link SiteSourceMapPlotter} and {@link SiteSourceDiffMapPlotter} draw the same way:
 * the rate unit the map is labelled in, the region it is framed on, and the marker on the site
 * itself. Kept here so that a single solution map and the difference map beside it cannot drift
 * apart in the ways a reader would notice.
 */
class SiteSourceMaps {

    private SiteSourceMaps() {}

    /**
     * The largest magnitude among the values, ignoring sign and anything that is not a finite
     * number. Zero for values that are all zero, or for no values at all.
     */
    static double maxAbs(double[] values) {
        double max = 0;
        for (double value : values) {
            if (Double.isFinite(value)) {
                max = Math.max(max, Math.abs(value));
            }
        }
        return max;
    }

    /** The rates converted from 1/yr to per {@code unitYears} years. */
    static double[] perUnit(double[] rates, double unitYears) {
        double[] scaled = new double[rates.length];
        for (int i = 0; i < rates.length; i++) {
            scaled[i] = rates[i] * unitYears;
        }
        return scaled;
    }

    /**
     * The region a map is framed on: the one the caller set, or a buffer around the sections the
     * map draws.
     *
     * <p>Falls back to a buffer around every section on the off chance that the site itself does
     * not land inside the buffer around the drawn ones, which is what happens when a site's hazard
     * comes entirely from faults some way off.
     *
     * @param set the region the caller set, or null to derive one
     * @param drawn the sections the map draws
     * @param bufferKm padding around those sections
     * @param site the site the map is about, which has to be on it
     * @param all every section that could be drawn, used for the fallback
     */
    static Region region(
            Region set,
            List<? extends FaultSection> drawn,
            double bufferKm,
            Location site,
            List<? extends FaultSection> all) {
        if (set != null) {
            return set;
        }
        Region region = GeographicMapMaker.buildBufferedRegion(drawn, bufferKm, true);
        return region.contains(site) ? region : GeographicMapMaker.buildBufferedRegion(all);
    }

    /** Marks the site with a white triangle, so that it is legible over any section colour. */
    static void markSite(GeographicMapMaker mapMaker, Location site) {
        mapMaker.setScatterSymbol(
                PlotSymbol.INV_TRIANGLE, 10f, PlotSymbol.INV_TRIANGLE, Color.BLACK);
        mapMaker.plotScatters(List.of(site), Color.WHITE);
    }
}

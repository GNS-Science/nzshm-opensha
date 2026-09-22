package nz.cri.gns.NZSHM22.opensha.hazard.joint;

import static nz.cri.gns.NZSHM22.opensha.hazard.joint.JointTestSolutions.*;
import static org.junit.Assert.*;

import java.util.List;
import org.junit.Test;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.sha.faultSurface.FaultSection;

/** Tests for {@link SiteSourceMaps}, the pieces the two site source maps draw the same way. */
public class SiteSourceMapsTest {

    /** The sections of the shared test solution, which is what a map is framed on. */
    protected static List<? extends FaultSection> sections() {
        return makeSolution().getRupSet().getFaultSectionDataList();
    }

    /** The unit a map is labelled in comes off the largest change, whichever way it went. */
    @Test
    public void testMaxAbs() {
        assertEquals(3d, SiteSourceMaps.maxAbs(new double[] {1d, 3d, 2d}), 1e-12);
        assertEquals(4d, SiteSourceMaps.maxAbs(new double[] {1d, -4d, 2d}), 1e-12);
        assertEquals(0d, SiteSourceMaps.maxAbs(new double[] {0d, 0d}), 1e-12);
        assertEquals(0d, SiteSourceMaps.maxAbs(new double[0]), 1e-12);
    }

    /** A section that is not a source for the site is NaN, and must not become the scale. */
    @Test
    public void testMaxAbsIgnoresNonFiniteValues() {
        assertEquals(
                2d,
                SiteSourceMaps.maxAbs(
                        new double[] {2d, Double.NaN, Double.POSITIVE_INFINITY, Double.NaN}),
                1e-12);
    }

    /** Converting to the map's unit is a plain scaling of the rates. */
    @Test
    public void testPerUnit() {
        double[] scaled = SiteSourceMaps.perUnit(new double[] {2e-3, -4e-4}, 1000d);
        assertEquals(2d, scaled[0], 1e-12);
        assertEquals(-0.4, scaled[1], 1e-12);
    }

    /** A region the caller set is used as it is, whatever the sections say. */
    @Test
    public void testRegionPrefersTheOneThatWasSet() {
        Region set = new Region(new Location(-41, 174), new Location(-40, 175));

        assertSame(
                set,
                SiteSourceMaps.region(
                        set, sections(), 50d, new Location(0, 0), List.<FaultSection>of()));
    }

    /** With no region set, the map is framed on a buffer around the sections it draws. */
    @Test
    public void testRegionBuffersTheDrawnSections() {
        List<? extends FaultSection> drawn = sections();

        Region region = SiteSourceMaps.region(null, drawn, 50d, SITE, drawn);

        assertTrue(region.contains(SITE));
        assertTrue(region.contains(drawn.get(0).getFaultTrace().first()));
    }

    /**
     * A site whose hazard comes entirely from faults some way off can fall outside that buffer, in
     * which case the map falls back to the region covering every section, which does hold the site.
     */
    @Test
    public void testRegionFallsBackWhenTheSiteIsOutside() {
        List<? extends FaultSection> all = sections();
        List<? extends FaultSection> drawn = List.of(all.get(all.size() - 1));

        Region region = SiteSourceMaps.region(null, drawn, 1d, SITE, all);

        assertTrue(region.contains(SITE));
    }
}

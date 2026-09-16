package nz.cri.gns.NZSHM22.opensha.hazard.joint;

import static org.junit.Assert.*;

import java.io.IOException;
import org.junit.Test;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.cpt.CPT;

/** Tests for {@link DivergingCPT}: where zero sits on the ramp, and where the ticks fall. */
public class DivergingCPTTest {

    protected CPT palette() throws IOException {
        return GMT_CPT_Files.DIVERGING_VIK_UNIFORM.instance();
    }

    @Test
    public void testCentredOnZeroCoversTheRange() throws IOException {
        CPT cpt = DivergingCPT.centredOnZero(palette(), -0.3, 0.7);
        assertEquals(-0.3, cpt.getMinValue(), 1e-9);
        assertEquals(0.7, cpt.getMaxValue(), 1e-9);
    }

    @Test
    public void testNeutralColourSitsOnZero() throws IOException {
        CPT scaled = palette().rescale(-1d, 1d);
        CPT cpt = DivergingCPT.centredOnZero(palette(), -0.1, 2d);
        assertEquals(scaled.getColor(0f), cpt.getColor(0f));
        // and not somewhere in the middle of the range
        assertNotEquals(scaled.getColor(0f), cpt.getColor(0.95f));
    }

    @Test
    public void testEmptySideLeavesZeroAtTheEnd() throws IOException {
        CPT cpt = DivergingCPT.centredOnZero(palette(), 0d, 1d);
        assertEquals(0d, cpt.getMinValue(), 1e-9);
        assertEquals(1d, cpt.getMaxValue(), 1e-9);
    }

    @Test
    public void testRejectsRangesWithoutZero() throws IOException {
        try {
            DivergingCPT.centredOnZero(palette(), 0.5, 1d);
            fail("expected a range that does not contain zero to be rejected");
        } catch (IllegalArgumentException expected) {
        }
        try {
            DivergingCPT.centredOnZero(palette(), 0d, 0d);
            fail("expected an empty range to be rejected");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testTickIntervalLandsOnBothBoundsAndZero() {
        assertTicksHit(-0.3, 0.7);
        assertTicksHit(-1d, 1d);
        assertTicksHit(-5d, 20d);
        assertTicksHit(-0.02, 0.1);
        assertTicksHit(0d, 1d);
        assertTicksHit(-2d, 0d);
    }

    @Test
    public void testTickIntervalIsTheLargestThatFits() {
        // the largest interval both bounds are a multiple of, not the smallest
        assertEquals(5d, DivergingCPT.tickInterval(-5d, 20d), 1e-6);
        assertEquals(0.1, DivergingCPT.tickInterval(-0.3, 0.7), 1e-7);
    }

    @Test
    public void testTickIntervalGivesUpOnAwkwardBounds() {
        // no round interval divides both bounds
        assertTrue(Double.isNaN(DivergingCPT.tickInterval(-0.37, 1.13)));
        // one exists, but it would crowd the bar with hundreds of ticks
        assertTrue(Double.isNaN(DivergingCPT.tickInterval(-0.01, 5d)));
    }

    @Test
    public void testCentredOnZeroCarriesTheTickInterval() throws IOException {
        CPT cpt = DivergingCPT.centredOnZero(palette(), -0.3, 0.7);
        assertEquals(0.1, cpt.getPreferredTickInterval(), 1e-7);

        CPT awkward = DivergingCPT.centredOnZero(palette(), -0.37, 1.13);
        assertTrue(Double.isNaN(awkward.getPreferredTickInterval()));
    }

    @Test
    public void testNiceCeiling() {
        assertEquals(0d, DivergingCPT.niceCeiling(0d), 1e-9);
        assertEquals(0d, DivergingCPT.niceCeiling(-3d), 1e-9);
        assertEquals(1d, DivergingCPT.niceCeiling(1d), 1e-9);
        assertEquals(2d, DivergingCPT.niceCeiling(1.1), 1e-9);
        assertEquals(5d, DivergingCPT.niceCeiling(2.01), 1e-9);
        assertEquals(10d, DivergingCPT.niceCeiling(5.5), 1e-9);
        assertEquals(0.02, DivergingCPT.niceCeiling(0.011), 1e-9);
        assertEquals(500d, DivergingCPT.niceCeiling(345d), 1e-9);
    }

    /**
     * Asserts that the interval chosen for the range puts a tick on each bound and on zero, the way
     * JFreeChart works them out: the first tick is the bound divided by the interval and rounded
     * inwards, and ticks run from there until they pass the other bound.
     */
    protected void assertTicksHit(double min, double max) {
        double interval = DivergingCPT.tickInterval(min, max);
        assertTrue("no tick interval for " + min + " to " + max, Double.isFinite(interval));

        double first = Math.ceil(min / interval) * interval;
        int count = (int) (Math.floor(max / interval) - Math.ceil(min / interval)) + 1;
        double tolerance = interval * 1e-6;

        assertEquals("no tick on the bottom of " + min + " to " + max, min, first, tolerance);
        assertEquals(
                "no tick on the top of " + min + " to " + max,
                max,
                first + (count - 1) * interval,
                tolerance);

        boolean onZero = false;
        for (int i = 0; i < count; i++) {
            onZero |= Math.abs(first + i * interval) <= tolerance;
        }
        assertTrue("no tick on zero for " + min + " to " + max, onZero);
    }
}

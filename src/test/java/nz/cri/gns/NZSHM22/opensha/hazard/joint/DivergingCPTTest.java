package nz.cri.gns.NZSHM22.opensha.hazard.joint;

import static org.junit.Assert.*;

import java.awt.Color;
import java.io.IOException;
import org.junit.Test;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.cpt.CPT;

/**
 * Tests for {@link DivergingCPT}: where zero sits on the ramp, how the palette is shared out
 * between the two sides, how a log ramp spaces its colours and bottoms out, and where the ticks
 * fall.
 */
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

    /**
     * Balanced scaling, the default, colours both sides at the same rate: on a ramp from -33 to 77
     * every value lands at its own size over 77 in the palette, so -33 is exactly as strong as +33.
     */
    @Test
    public void testBalancedScalingColoursBothSidesAtTheSameRate() throws IOException {
        CPT scaled = palette().rescale(-1d, 1d);
        CPT cpt = DivergingCPT.centredOnZero(palette(), -33d, 77d);

        // the shorter side reaches 33/77 of the way into its half of the palette, no further
        assertEquals(scaled.getColor((float) (-33d / 77d)), cpt.getColor(-33f));
        // and every value in between is the palette colour for its own share of the longer side
        for (double value : new double[] {-30d, -20d, -10d, 10d, 20d, 33d, 50d}) {
            assertColorNear(
                    "at " + value,
                    scaled.getColor((float) (value / 77d)),
                    cpt.getColor((float) value));
        }
        // the longer side still saturates
        assertEquals(scaled.getColor(1f), cpt.getColor(77f));
    }

    /** The default is balanced scaling. */
    @Test
    public void testBalancedIsTheDefault() throws IOException {
        assertEquals(DivergingCPT.Scaling.BALANCED, DivergingCPT.DEFAULT_SCALING);
        assertEquals(
                DivergingCPT.centredOnZero(palette(), -33d, 77d, DivergingCPT.Scaling.BALANCED)
                        .getColor(-33f),
                DivergingCPT.centredOnZero(palette(), -33d, 77d).getColor(-33f));
    }

    /** Independent scaling runs each side to the end of the palette, whatever its range. */
    @Test
    public void testIndependentScalingSaturatesBothEnds() throws IOException {
        CPT scaled = palette().rescale(-1d, 1d);
        CPT cpt =
                DivergingCPT.centredOnZero(palette(), -33d, 77d, DivergingCPT.Scaling.INDEPENDENT);

        assertEquals(scaled.getColor(-1f), cpt.getColor(-33f));
        assertEquals(scaled.getColor(1f), cpt.getColor(77f));
        // the shorter side is stretched, so the same value is a stronger colour than when balanced
        assertNotEquals(
                DivergingCPT.centredOnZero(palette(), -33d, 77d).getColor(-33f),
                cpt.getColor(-33f));
    }

    /** A symmetric range is coloured the same either way, so the two scalings agree there. */
    @Test
    public void testScalingsAgreeOnASymmetricRange() throws IOException {
        CPT balanced = DivergingCPT.centredOnZero(palette(), -2d, 2d);
        CPT independent =
                DivergingCPT.centredOnZero(palette(), -2d, 2d, DivergingCPT.Scaling.INDEPENDENT);

        for (float value : new float[] {-2f, -1f, 0f, 1f, 2f}) {
            assertEquals(
                    "differ at " + value, balanced.getColor(value), independent.getColor(value));
        }
    }

    /** Whichever scaling is used, the ramp still runs between the bounds it was given. */
    @Test
    public void testScalingDoesNotChangeTheBounds() throws IOException {
        for (DivergingCPT.Scaling scaling : DivergingCPT.Scaling.values()) {
            CPT cpt = DivergingCPT.centredOnZero(palette(), -33d, 77d, scaling);
            assertEquals(scaling.toString(), -33d, cpt.getMinValue(), 1e-9);
            assertEquals(scaling.toString(), 77d, cpt.getMaxValue(), 1e-9);
        }
    }

    /**
     * A one-sided ramp saturates at its far end under either scaling: it is its own longer side.
     */
    @Test
    public void testOneSidedRampSaturates() throws IOException {
        CPT scaled = palette().rescale(-1d, 1d);
        assertEquals(
                scaled.getColor(1f), DivergingCPT.centredOnZero(palette(), 0d, 5d).getColor(5f));
        assertEquals(
                scaled.getColor(-1f), DivergingCPT.centredOnZero(palette(), -5d, 0d).getColor(-5f));
    }

    /**
     * Asserts that two colours match up to the rounding of the ramp's colour steps, which is a unit
     * or so per channel. Colours taken from the ends of a step match exactly; ones from the middle
     * of a step are interpolated and can be a unit out.
     */
    protected static void assertColorNear(String message, Color expected, Color actual) {
        int tolerance = 2;
        assertTrue(
                message + ": expected " + expected + " but got " + actual,
                Math.abs(expected.getRed() - actual.getRed()) <= tolerance
                        && Math.abs(expected.getGreen() - actual.getGreen()) <= tolerance
                        && Math.abs(expected.getBlue() - actual.getBlue()) <= tolerance);
    }

    /** A log ramp still runs between the bounds it was given. */
    @Test
    public void testLogRampKeepsItsBounds() throws IOException {
        CPT cpt = DivergingCPT.ramp(palette(), -33d, 186d).logFloor(1d).build();
        assertEquals(-33d, cpt.getMinValue(), 1e-9);
        assertEquals(186d, cpt.getMaxValue(), 1e-9);
    }

    /**
     * A log ramp spends its colour on the logarithm of the change: a value sits at its share of the
     * decades the longer side spans, so small changes get far more of the palette than they would
     * on a linear ramp.
     */
    @Test
    public void testLogRampSpacesColoursByDecade() throws IOException {
        CPT scaled = palette().rescale(-1d, 1d);
        CPT cpt = DivergingCPT.ramp(palette(), -33d, 186d).logFloor(1d).build();

        double decades = Math.log10(186d);
        for (double value : new double[] {2d, 5d, 10d, 33d, 100d}) {
            double position = Math.log10(value) / decades;
            assertColorNear(
                    "at +" + value, scaled.getColor((float) position), cpt.getColor((float) value));
        }
        // the decrease side only runs to -33, and is the mirror of the increase side over it
        for (double value : new double[] {2d, 5d, 10d, 33d}) {
            double position = Math.log10(value) / decades;
            assertColorNear(
                    "at -" + value,
                    scaled.getColor((float) -position),
                    cpt.getColor((float) -value));
        }
        // the longer side still saturates at its end
        assertEquals(scaled.getColor(1f), cpt.getColor(186f));
    }

    /** Log spacing is what keeps an outlier from flattening everything else. */
    @Test
    public void testLogRampDoesNotWashOutSmallChanges() throws IOException {
        CPT scaled = palette().rescale(-1d, 1d);
        CPT log = DivergingCPT.ramp(palette(), -33d, 186d).logFloor(1d).build();
        CPT linear = DivergingCPT.centredOnZero(palette(), -33d, 186d);

        // on the linear ramp a 10% change is 10/186 of the way along, all but colourless
        assertColorNear("linear", scaled.getColor((float) (10d / 186d)), linear.getColor(10f));
        // the log ramp puts it most of the way to half saturation instead
        assertColorNear(
                "log",
                scaled.getColor((float) (Math.log10(10d) / Math.log10(186d))),
                log.getColor(10f));
        assertNotEquals(linear.getColor(10f), log.getColor(10f));
    }

    /** Changes smaller than the floor are one flat band of no change, in the colour given. */
    @Test
    public void testLogFloorGivesAFlatZeroBand() throws IOException {
        Color green = new Color(60, 160, 90);
        CPT cpt = DivergingCPT.ramp(palette(), -33d, 186d).logFloor(1d).zeroColor(green).build();

        for (float value : new float[] {-0.99f, -0.5f, -0.01f, 0f, 0.01f, 0.5f, 0.99f}) {
            assertEquals("at " + value, green, cpt.getColor(value));
        }
        // and just outside it the palette takes over again
        assertNotEquals(green, cpt.getColor(1.5f));
        assertNotEquals(green, cpt.getColor(-1.5f));
    }

    /** Without a colour of its own the zero band takes the palette's neutral colour. */
    @Test
    public void testZeroBandDefaultsToTheNeutralColour() throws IOException {
        CPT scaled = palette().rescale(-1d, 1d);
        CPT cpt = DivergingCPT.ramp(palette(), -33d, 186d).logFloor(1d).build();
        assertEquals(scaled.getColor(0f), cpt.getColor(0.5f));
    }

    /** A zero colour needs a band to give it its width, so on its own it is rejected. */
    @Test
    public void testZeroColourNeedsAZeroBand() throws IOException {
        try {
            DivergingCPT.ramp(palette(), -33d, 186d).zeroColor(Color.GREEN).build();
            fail("expected a zero colour without a zero band to be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("zero band"));
        }
    }

    /** Log spacing has no bottom of its own, so it needs a band too. */
    @Test
    public void testLogSpacingNeedsAZeroBand() throws IOException {
        try {
            DivergingCPT.ramp(palette(), -33d, 186d).spacing(DivergingCPT.Spacing.LOG).build();
            fail("expected log spacing without a zero band to be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("zero band"));
        }
    }

    /** A band on a linear ramp: flat across the middle, linear in the change outside it. */
    @Test
    public void testLinearRampWithAZeroBand() throws IOException {
        Color green = new Color(60, 160, 90);
        CPT scaled = palette().rescale(-1d, 1d);
        CPT cpt = DivergingCPT.ramp(palette(), -40d, 100d).zeroBand(10d).zeroColor(green).build();

        assertEquals(-40d, cpt.getMinValue(), 1e-9);
        assertEquals(100d, cpt.getMaxValue(), 1e-9);
        for (float value : new float[] {-9f, 0f, 9f}) {
            assertEquals("at " + value, green, cpt.getColor(value));
        }
        // outside the band the palette is linear in the change, measured out from the band edge
        assertColorNear("at +55", scaled.getColor((float) (45d / 90d)), cpt.getColor(55f));
        assertColorNear("at -35", scaled.getColor((float) (-25d / 90d)), cpt.getColor(-35f));
        // and still balanced: equal magnitudes are mirror images
        assertColorNear("at +30", scaled.getColor((float) (20d / 90d)), cpt.getColor(30f));
        assertColorNear("at -30", scaled.getColor((float) (-20d / 90d)), cpt.getColor(-30f));
        assertEquals(scaled.getColor(1f), cpt.getColor(100f));
    }

    /** A plain linear ramp with no band is still continuous through zero. */
    @Test
    public void testLinearRampWithoutABandIsUnchanged() throws IOException {
        CPT scaled = palette().rescale(-1d, 1d);
        CPT cpt = DivergingCPT.centredOnZero(palette(), -40d, 100d);
        assertEquals(scaled.getColor(0f), cpt.getColor(0f));
        assertColorNear("at +50", scaled.getColor(0.5f), cpt.getColor(50f));
        assertColorNear("at -20", scaled.getColor(-0.2f), cpt.getColor(-20f));
    }

    /** A negative floor is rejected outright. */
    @Test
    public void testLogFloorIsValidated() throws IOException {
        try {
            DivergingCPT.ramp(palette(), -33d, 186d).logFloor(-1d);
            fail("expected a negative log floor to be rejected");
        } catch (IllegalArgumentException expected) {
        }
    }

    /** A side that never reaches the floor has no decade to show and is left to the zero band. */
    @Test
    public void testSideShorterThanTheFloorIsAllZeroBand() throws IOException {
        Color green = new Color(60, 160, 90);
        CPT cpt = DivergingCPT.ramp(palette(), -0.4, 186d).logFloor(1d).zeroColor(green).build();

        assertEquals(-0.4, cpt.getMinValue(), 1e-9);
        assertEquals(green, cpt.getColor(-0.4f));
        assertEquals(green, cpt.getColor(-0.2f));
        // the increase side is unaffected
        assertNotEquals(green, cpt.getColor(50f));
    }

    /** Under independent scaling both sides of a log ramp run to the end of the palette. */
    @Test
    public void testLogRampSaturatesBothEndsWhenIndependent() throws IOException {
        CPT scaled = palette().rescale(-1d, 1d);
        CPT cpt =
                DivergingCPT.ramp(palette(), -33d, 186d)
                        .logFloor(1d)
                        .scaling(DivergingCPT.Scaling.INDEPENDENT)
                        .build();

        assertEquals(scaled.getColor(-1f), cpt.getColor(-33f));
        assertEquals(scaled.getColor(1f), cpt.getColor(186f));
    }

    /** The ramp is built in ascending order, so its end colours are the saturated ones. */
    @Test
    public void testLogRampIsInAscendingOrder() throws IOException {
        CPT cpt = DivergingCPT.ramp(palette(), -33d, 186d).logFloor(1d).build();
        assertEquals(cpt.getColor(-33f), cpt.getMinColor());
        assertEquals(cpt.getColor(186f), cpt.getMaxColor());
        for (int i = 1; i < cpt.size(); i++) {
            assertTrue("entry " + i + " is out of order", cpt.get(i).start >= cpt.get(i - 1).start);
        }
    }

    /** A ramp entirely inside its floor is all zero band, and still covers its range. */
    @Test
    public void testRampInsideTheFloorIsAllZeroBand() throws IOException {
        Color green = new Color(60, 160, 90);
        CPT cpt = DivergingCPT.ramp(palette(), -0.25, 0.5).logFloor(1d).zeroColor(green).build();

        assertEquals(-0.25, cpt.getMinValue(), 1e-9);
        assertEquals(0.5, cpt.getMaxValue(), 1e-9);
        for (float value : new float[] {-0.25f, -0.1f, 0f, 0.25f, 0.5f}) {
            assertEquals("at " + value, green, cpt.getColor(value));
        }
    }
}

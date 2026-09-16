package nz.cri.gns.NZSHM22.opensha.hazard.joint;

import com.google.common.base.Preconditions;
import java.awt.Color;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.commons.util.cpt.CPTVal;

/**
 * Builds the colour ramps that the difference maps use: a diverging palette laid out over a range
 * that need not be symmetric, with the palette's neutral colour pinned to zero.
 *
 * <p>Rescaling a diverging palette onto a lopsided range would slide its neutral colour off zero,
 * which is the one thing a difference map has to get right. So the two halves are laid out
 * separately: the lower half of the palette is stretched over the negative side and the upper half
 * over the positive side.
 *
 * <p>The cost is that a colour distance means a different amount on each side of the ramp. That is
 * a fair trade for a map where nearly everything moved one way — a scale symmetric enough to keep
 * the two sides comparable would spend half its width on changes that do not occur, and wash out
 * the ones that do. Both difference maps label their colour bars with real values, and the ramp
 * carries a tick interval that puts a label on each end and on zero (see {@link #tickInterval}), so
 * what a colour means stays legible.
 *
 * <p>Used by {@link HazardComparisonReport} for map ratios, where the range is in log space, and by
 * {@link SiteSourceDiffMapPlotter} for section rate changes, where it is linear.
 */
public class DivergingCPT {

    /** Number of colour steps either side of zero. */
    public static final int DEFAULT_STEPS = 64;

    /** Multipliers a bound is rounded up to, within its decade. See {@link #niceCeiling}. */
    protected static final double[] NICE_MULTIPLIERS = {1d, 2d, 5d, 10d};

    /** Multipliers a tick interval may take, within its decade, largest first. */
    protected static final double[] TICK_MULTIPLIERS = {5d, 2d, 1d};

    /** The most tick intervals a colour bar is asked to carry. See {@link #tickInterval}. */
    protected static final int MAX_TICK_INTERVALS = 20;

    /** How far a bound may be off a multiple of the tick interval and still count as on it. */
    protected static final double TICK_TOLERANCE = 1e-9;

    /**
     * How far tick intervals are shrunk to survive JFreeChart's rounding. See {@link
     * #tickInterval}.
     */
    protected static final double TICK_NUDGE = 1e-9;

    private DivergingCPT() {}

    /** {@link #centredOnZero(CPT, double, double, int)} with {@link #DEFAULT_STEPS}. */
    public static CPT centredOnZero(CPT palette, double min, double max) {
        return centredOnZero(palette, min, max, DEFAULT_STEPS);
    }

    /**
     * A ramp from {@code min} to {@code max} with the palette's middle colour at zero.
     *
     * @param palette a diverging palette; it is rescaled internally, so pass an unscaled instance
     * @param min the bottom of the ramp, at most zero
     * @param max the top of the ramp, at least zero
     * @param steps colour steps either side of zero
     * @throws IllegalArgumentException if the range does not contain zero, or is empty
     */
    public static CPT centredOnZero(CPT palette, double min, double max, int steps) {
        Preconditions.checkArgument(
                min <= 0 && max >= 0, "the ramp has to contain zero, got %s to %s", min, max);
        Preconditions.checkArgument(min < 0 || max > 0, "the ramp cannot be empty");
        Preconditions.checkArgument(steps > 0, "steps must be positive");

        CPT scaled = palette.rescale(-1d, 1d);
        CPT cpt = new CPT();
        // a side with no range at all is skipped, which leaves zero at that end of the ramp
        if (min < 0) {
            addSteps(cpt, scaled, min, 0d, -1d, 0d, steps);
        }
        if (max > 0) {
            addSteps(cpt, scaled, 0d, max, 0d, 1d, steps);
        }
        double tick = tickInterval(min, max);
        if (Double.isFinite(tick)) {
            cpt.setPreferredTickInterval(tick);
        }
        return cpt;
    }

    /**
     * A tick interval that puts a labelled tick on both ends of the ramp and on zero, so that a
     * reader can tell which way the map moved and by how much. Zero is a multiple of any interval,
     * so what this looks for is the largest interval that both bounds are a multiple of.
     *
     * <p>Bounds from {@link #niceCeiling} always have such an interval. Bounds that are not round
     * numbers, or that are so lopsided that a shared interval would crowd the bar with more than
     * {@link #MAX_TICK_INTERVALS} ticks, have none worth using; those return NaN, which leaves the
     * plot on automatic ticks.
     *
     * <p>The interval is returned a hair under the round value it was picked as. JFreeChart derives
     * its first and last tick by dividing the bounds by the interval and rounding inwards, so a
     * bound that is a multiple of the interval in decimal but a whisker over it in binary loses its
     * tick — exactly the tick this method exists to guarantee. Shrinking the interval moves the
     * rounding the safe way, by far less than a tick label's formatting can show.
     *
     * @param min the bottom of the ramp, at most zero
     * @param max the top of the ramp, at least zero
     * @return the tick interval, or NaN if no sensible one exists
     */
    public static double tickInterval(double min, double max) {
        double span = max - min;
        if (!(span > 0)) {
            return Double.NaN;
        }
        double smallest = span / MAX_TICK_INTERVALS;
        for (double decade = Math.pow(10, Math.floor(Math.log10(span)));
                decade * TICK_MULTIPLIERS[0] >= smallest;
                decade /= 10) {
            for (double multiplier : TICK_MULTIPLIERS) {
                double interval = multiplier * decade;
                if (interval > span || interval < smallest) {
                    continue;
                }
                if (isMultipleOf(-min, interval) && isMultipleOf(max, interval)) {
                    return interval * (1 - TICK_NUDGE);
                }
            }
        }
        return Double.NaN;
    }

    /** Whether the value is a whole number of intervals, up to {@link #TICK_TOLERANCE}. */
    protected static boolean isMultipleOf(double value, double interval) {
        double intervals = value / interval;
        return Math.abs(intervals - Math.rint(intervals))
                <= TICK_TOLERANCE * Math.max(1d, Math.abs(intervals));
    }

    /**
     * Lays one half of a diverging palette out over one half of a ramp.
     *
     * @param from start of the ramp segment
     * @param to end of the ramp segment
     * @param paletteFrom where in the palette, between -1 and 1, the segment starts
     * @param paletteTo where in the palette the segment ends
     */
    protected static void addSteps(
            CPT cpt,
            CPT palette,
            double from,
            double to,
            double paletteFrom,
            double paletteTo,
            int steps) {
        double previous = from;
        Color previousColor = palette.getColorRaw((float) paletteFrom);
        for (int i = 1; i <= steps; i++) {
            double fraction = (double) i / steps;
            double value = from + (to - from) * fraction;
            Color color =
                    palette.getColorRaw(
                            (float) (paletteFrom + (paletteTo - paletteFrom) * fraction));
            cpt.add(new CPTVal(previous, previousColor, value, color));
            previous = value;
            previousColor = color;
        }
    }

    /**
     * The given value rounded up to one, two, five or ten times a power of ten, so that a ramp
     * derived from the data still ends on a number a legend can label. Rounding outwards, so a ramp
     * bounded this way still covers everything it was fitted to.
     *
     * @return the rounded value, or zero for a value that is not positive
     */
    public static double niceCeiling(double value) {
        if (!(value > 0)) {
            return 0d;
        }
        double decade = Math.pow(10, Math.floor(Math.log10(value)));
        for (double multiplier : NICE_MULTIPLIERS) {
            // the tolerance keeps a value that is already nice from being rounded up a step by
            // floating point noise
            if (value <= multiplier * decade * (1 + 1e-12)) {
                return multiplier * decade;
            }
        }
        return 10 * decade;
    }
}

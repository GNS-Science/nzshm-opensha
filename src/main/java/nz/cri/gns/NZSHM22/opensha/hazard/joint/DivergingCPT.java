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
 * the ones that do. Both difference maps label their colour bars with real values, so what a colour
 * means stays legible.
 *
 * <p>Used by {@link HazardComparisonReport} for map ratios, where the range is in log space, and by
 * {@link SiteSourceDiffMapPlotter} for section rate changes, where it is linear.
 */
public class DivergingCPT {

    /** Number of colour steps either side of zero. */
    public static final int DEFAULT_STEPS = 64;

    /** Multipliers a bound is rounded up to, within its decade. See {@link #niceCeiling}. */
    protected static final double[] NICE_MULTIPLIERS = {1d, 2d, 5d, 10d};

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
        return cpt;
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

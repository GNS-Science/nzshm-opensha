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
 * separately: the lower half of the palette over the negative side, the upper half over the
 * positive side. Ramps are built through {@link #ramp}.
 *
 * <h2>Scaling</h2>
 *
 * <p>Both sides are laid out at the same rate, set by whichever side is longer: on a ramp from -33%
 * to 77%, -33% is exactly as strong a blue as +33% would be a red, and only +77% reaches the
 * palette's full saturation. Equal changes then look equal wherever they fall, so a map read by eye
 * is not biased towards the shorter side, and only the longer side saturates.
 *
 * <h2>The zero band</h2>
 *
 * <p>A ramp given a {@link Builder#zeroBand} treats changes smaller than it as none and draws that
 * band, from {@code -width} to {@code +width}, in one flat colour: the palette's own neutral unless
 * {@link Builder#zeroColor} gives it another. A distinct colour there says which cells did not
 * really move rather than leaving them to fade into the palette. Outside the band the palette picks
 * up from its neutral colour and runs to each end.
 *
 * <p>The width is an absolute value, not a fraction of the range, so it means the same thing on
 * every map in a report and two maps can be compared by eye.
 *
 * <h2>Log spacing</h2>
 *
 * <p>A ramp given a {@link Builder#logFloor} spends its colour on the logarithm of the change
 * rather than the change itself, in both directions out from zero. On a linear ramp a single large
 * outlier washes the map out: if one node moved by 200% and the rest by a few percent, the rest all
 * land in the first sliver of the palette and the map reads as flat. Log spacing gives each decade
 * of change the same share of the palette, so the small changes that make up most of a map stay
 * legible while the outlier still sits at the end of the ramp.
 *
 * <p>The logarithm has no bottom, so log spacing needs a zero band to bottom out at; {@link
 * Builder#logFloor} sets both at once. A band is worth colouring on a log ramp in particular,
 * because the band is a real and visible part of the map rather than an infinitesimal line.
 *
 * <h2>Bounds and ticks</h2>
 *
 * <p>Whatever the spacing, the ramp keeps the range it was given rather than padding out to a
 * symmetric one, so no width is spent on changes that do not occur, and the colour bar is labelled
 * with real values, so what a colour means stays legible.
 *
 * <p>A ramp whose bounds are round numbers, i.e. one fitted with {@link #niceCeiling}, is given a
 * tick interval that puts a label on each end and on zero; see {@link #tickInterval}. A ramp fitted
 * to raw data extremes has no such interval and is left on the plot's automatic ticks, which still
 * label zero — zero is a multiple of every tick interval and the range straddles it — but not the
 * ends. The colour bar's axis stays linear in the data either way: log spacing changes which colour
 * a value gets, not where on the bar it sits.
 *
 * <p>Used by {@link HazardComparisonReport} for percentage changes in ground motion, fitted to the
 * map's extremes, and by {@link SiteSourceDiffMapPlotter} for section rate changes, rounded with
 * {@link #niceCeiling}.
 */
public class DivergingCPT {

    /** Whether the palette follows the change itself or its logarithm. */
    public enum Spacing {
        /** Colour in proportion to the change, so that colour distance is change distance. */
        LINEAR,
        /**
         * Colour in proportion to the logarithm of the change, so that each decade gets the same
         * share of the palette and one large outlier cannot flatten everything else. Needs a {@link
         * Builder#zeroBand} to bottom out at.
         */
        LOG
    }

    /**
     * A green for the zero band of a log ramp, distinct from both ends of a red/blue diverging
     * palette so that the cells which did not really move read as their own thing rather than as a
     * weak change. Ramps do not use it unless a caller passes it to {@link Builder#zeroColor}.
     */
    public static final Color DEFAULT_ZERO_COLOR = new Color(60, 160, 90);

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

    /**
     * A ramp from {@code min} to {@code max} with the palette's neutral colour at zero, {@link
     * #DEFAULT_STEPS} colour steps and linear spacing. Set whatever else is wanted on the returned
     * builder, then call {@link Builder#build()}.
     *
     * @param palette a diverging palette; it is rescaled internally, so pass an unscaled instance
     * @param min the bottom of the ramp, at most zero
     * @param max the top of the ramp, at least zero
     */
    public static Builder ramp(CPT palette, double min, double max) {
        return new Builder(palette, min, max);
    }

    /** A linear ramp with the defaults, i.e. {@code ramp(palette, min, max).build()}. */
    public static CPT centredOnZero(CPT palette, double min, double max) {
        return ramp(palette, min, max).build();
    }

    /** Collects the choices a ramp is built from. See {@link DivergingCPT#ramp}. */
    public static class Builder {

        protected final CPT palette;
        protected final double min;
        protected final double max;

        protected int steps = DEFAULT_STEPS;

        /** Whether colour follows the change or its logarithm. */
        protected Spacing spacing = Spacing.LINEAR;

        /** Half-width of the flat no-change band, or zero for no band. */
        protected double zeroBand = 0d;

        /** The colour of the zero band, or null for the palette's own neutral colour. */
        protected Color zeroColor;

        protected Builder(CPT palette, double min, double max) {
            this.palette = Preconditions.checkNotNull(palette, "need a palette");
            this.min = min;
            this.max = max;
        }

        /**
         * Draws changes smaller than the given magnitude as no change, in one flat band running
         * from {@code -half} to {@code +half}, coloured by {@link #zeroColor}. Outside the band the
         * palette picks up from its neutral colour and runs to each end as {@link #spacing} says.
         * Pass zero for no band, which is the default.
         *
         * <p>The width is in the units of the ramp, not a fraction of its range, so the same width
         * means the same thing on every map it is used for.
         *
         * @throws IllegalArgumentException if the width is negative or not finite
         */
        public Builder zeroBand(double half) {
            Preconditions.checkArgument(
                    half >= 0 && Double.isFinite(half),
                    "the zero band has to be zero or a positive number, got %s",
                    half);
            this.zeroBand = half;
            return this;
        }

        /**
         * Whether the palette follows the change itself or its logarithm. Defaults to {@link
         * Spacing#LINEAR}; {@link Spacing#LOG} needs a {@link #zeroBand} to bottom out at.
         */
        public Builder spacing(Spacing spacing) {
            this.spacing = spacing;
            return this;
        }

        /**
         * Log spacing bottoming out at the given magnitude, i.e. {@code
         * spacing(Spacing.LOG).zeroBand(floor)}. The logarithm has no bottom of its own, so the two
         * always go together.
         */
        public Builder logFloor(double floor) {
            return spacing(Spacing.LOG).zeroBand(floor);
        }

        /**
         * Draws the zero band in the given colour rather than the palette's neutral one, so that
         * the cells which did not really move are obvious. Needs a {@link #zeroBand}, which is what
         * gives the band its width. Pass null for the palette's neutral colour.
         */
        public Builder zeroColor(Color zeroColor) {
            this.zeroColor = zeroColor;
            return this;
        }

        /**
         * Builds the ramp.
         *
         * @throws IllegalArgumentException if the range does not contain zero or is empty, if the
         *     step count is not positive, or if a zero colour was given without a log floor
         */
        public CPT build() {
            Preconditions.checkArgument(
                    min <= 0 && max >= 0, "the ramp has to contain zero, got %s to %s", min, max);
            Preconditions.checkArgument(min < 0 || max > 0, "the ramp cannot be empty");
            Preconditions.checkArgument(steps > 0, "steps must be positive");
            Preconditions.checkNotNull(spacing, "need a spacing");
            Preconditions.checkArgument(
                    zeroColor == null || zeroBand > 0,
                    "a zero colour needs a zero band to give it its width");
            Preconditions.checkArgument(
                    spacing == Spacing.LINEAR || zeroBand > 0,
                    "log spacing needs a zero band to bottom out at: the logarithm has no bottom of"
                            + " its own");

            CPT scaled = palette.rescale(-1d, 1d);
            CPT cpt = new CPT();
            if (spacing == Spacing.LOG) {
                addLogRamp(cpt, scaled);
            } else {
                addLinearRamp(cpt, scaled);
            }
            // CPT looks a value up in float and treats each entry as half-open, so a value sitting
            // exactly on the ramp's top bound matches no entry and is not above the maximum either;
            // it falls through to the gap colour, which is black by default. These ramps are
            // contiguous from end to end, so that crack is the only way to reach the gap colour and
            // the top of the ramp is the right thing to put there. Fitting a ramp to raw data
            // extremes makes it reachable: the node holding the maximum sits exactly on the bound.
            cpt.setGapColor(cpt.getMaxColor());
            double tick = tickInterval(min, max);
            if (Double.isFinite(tick)) {
                cpt.setPreferredTickInterval(tick);
            }
            return cpt;
        }

        /**
         * Lays the palette out linearly over each side, from the edge of the zero band out to the
         * end. Entries are added from the bottom of the ramp upwards, because a CPT takes its first
         * and last entry to be its ends.
         */
        protected void addLinearRamp(CPT cpt, CPT scaled) {
            double extent = Math.max(-min, max);

            // a side with no range outside the band is skipped, which leaves the band reaching
            // that end of the ramp
            if (-min > zeroBand) {
                addSteps(cpt, scaled, min, -zeroBand, -palettePosition(-min, extent), 0d, steps);
            }
            addZeroBand(cpt, scaled);
            if (max > zeroBand) {
                addSteps(cpt, scaled, zeroBand, max, 0d, palettePosition(max, extent), steps);
            }
        }

        /**
         * The flat band across zero, if there is one. Without a band the two sides already meet at
         * zero and nothing is added.
         */
        protected void addZeroBand(CPT cpt, CPT scaled) {
            if (!(zeroBand > 0)) {
                return;
            }
            Color bandColor = zeroColor != null ? zeroColor : scaled.getColorRaw(0f);
            cpt.add(
                    new CPTVal(
                            Math.max(min, -zeroBand),
                            bandColor,
                            Math.min(max, zeroBand),
                            bandColor));
        }

        /**
         * Lays the palette out over the logarithm of each side, with a flat band across the floor.
         * Entries are added from the bottom of the ramp upwards, because a CPT takes its first and
         * last entry to be its ends.
         */
        protected void addLogRamp(CPT cpt, CPT scaled) {
            double extent = Math.max(-min, max);

            // a side shorter than the floor has no decade to show and is left to the zero band,
            // which then reaches all the way to that end of the ramp
            if (-min > zeroBand) {
                addLogSteps(
                        cpt, scaled, zeroBand, -min, true, -palettePosition(-min, extent), steps);
            }
            addZeroBand(cpt, scaled);
            if (max > zeroBand) {
                addLogSteps(cpt, scaled, zeroBand, max, false, palettePosition(max, extent), steps);
            }
        }

        /**
         * How far into its half of the palette, between zero and one, the far end of a side sits:
         * its share of the longer side, measured out from the edge of the zero band, so that equal
         * magnitudes either side of zero get equal colours and only the longer side saturates.
         *
         * @param magnitude the far end of the side, greater than the zero band
         * @param extent the far end of the longer side
         */
        protected double palettePosition(double magnitude, double extent) {
            if (extent <= zeroBand) {
                return 0d;
            }
            if (spacing == Spacing.LOG) {
                return Math.log10(magnitude / zeroBand) / Math.log10(extent / zeroBand);
            }
            return (magnitude - zeroBand) / (extent - zeroBand);
        }
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
     * Lays one half of a diverging palette out over one half of a ramp, in even steps.
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
     * Lays one half of a diverging palette out over one side of a log ramp, from the floor out to
     * the far end. Steps are spaced geometrically, so that each covers the same slice of the
     * palette and the same ratio of change.
     *
     * @param floor the magnitude the side starts at, where it meets the zero band
     * @param end the magnitude at the far end of the side, greater than the floor
     * @param decrease whether this is the side below zero, whose values are the negated magnitudes
     * @param paletteEnd where in the palette, between -1 and 1, the far end sits
     */
    protected static void addLogSteps(
            CPT cpt,
            CPT palette,
            double floor,
            double end,
            boolean decrease,
            double paletteEnd,
            int steps) {
        double ratio = end / floor;
        double[] magnitudes = new double[steps + 1];
        Color[] colors = new Color[steps + 1];
        for (int i = 0; i <= steps; i++) {
            magnitudes[i] = floor * Math.pow(ratio, (double) i / steps);
            colors[i] = palette.getColorRaw((float) (paletteEnd * i / steps));
        }
        // the ends are pinned rather than left to the rounding of the powers above, so that the
        // ramp meets the zero band and reaches its bound exactly
        magnitudes[0] = floor;
        magnitudes[steps] = end;

        for (int i = 1; i <= steps; i++) {
            if (decrease) {
                // the decrease side is walked from its far end inwards, so that entries are added
                // in ascending order of value like every other side
                int j = steps - i + 1;
                cpt.add(new CPTVal(-magnitudes[j], colors[j], -magnitudes[j - 1], colors[j - 1]));
            } else {
                cpt.add(new CPTVal(magnitudes[i - 1], colors[i - 1], magnitudes[i], colors[i]));
            }
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

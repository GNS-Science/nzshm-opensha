package nz.cri.gns.NZSHM22.opensha.hazard.joint;

import com.google.common.base.Preconditions;
import java.util.Locale;

/**
 * How hazard outputs are named: the label of a calculation period, the units it is reported in, and
 * the reduction of any label to a file name fragment.
 *
 * <p>Kept in one place because the maps and site curves of {@link JointHazardMapCalculator}, the
 * figures of {@link HazardComparisonReport} and the ids of {@link HazardReportSource} all name
 * their files after the same labels. A file written by one has to be found by the other, so they
 * cannot afford to slugify differently.
 */
public class HazardLabels {

    private HazardLabels() {}

    /**
     * What the per-section source maps colour: the hazard that reaches the site through a section.
     * Shared by the single-solution map, the difference map and the report that lays them out, so
     * that the legend, the figure heading and the prose all name the same quantity.
     */
    public static final String SECTION_HAZARD = "Hazard Through Section";

    /**
     * The number of years a set of rates is best reported over: the smallest power of ten that puts
     * the largest of them at one or above.
     *
     * <p>Section contributions to a site's hazard are rates of the order of a thousandth per year,
     * and a colour bar labelled in those comes out as a row of zeroes, because the axis is
     * formatted to a few decimal places. Reporting the same numbers over a thousand or ten thousand
     * years puts them in a range a legend can print without saying anything different.
     *
     * @param largestRate the largest rate to be shown, in 1/yr; sign is ignored
     */
    public static double rateUnitYears(double largestRate) {
        if (!Double.isFinite(largestRate) || largestRate == 0) {
            return 1d;
        }
        return Math.pow(10, Math.max(0, Math.ceil(-Math.log10(Math.abs(largestRate)))));
    }

    /** How a rate over the given number of years is named on a colour bar. */
    public static String rateUnit(double unitYears) {
        return unitYears == 1d
                ? "1/yr"
                : "per " + String.format("%,d", (long) unitYears) + " years";
    }

    /**
     * The display label of a calculation period, e.g. "PGA", "PGV" or "3s SA". Whole periods lose
     * their decimal point, so 3.0 is "3s SA" rather than "3.0s SA".
     *
     * @param period 0 for PGA, -1 for PGV, a positive value for an SA period in seconds
     * @throws IllegalArgumentException if the period is negative but not -1
     */
    public static String periodLabel(double period) {
        if (period == -1d) {
            return "PGV";
        }
        if (period == 0d) {
            return "PGA";
        }
        Preconditions.checkArgument(period > 0, "Unexpected period %s", period);
        return (period == Math.rint(period) ? String.valueOf((int) period) : String.valueOf(period))
                + "s SA";
    }

    /** The units a period is reported in: cm/s for PGV, g for PGA and SA. */
    public static String periodUnits(double period) {
        return period == -1d ? "cm/s" : "g";
    }

    /**
     * The file name fragment of a period, e.g. "pga" or "3s_sa". This is {@link #periodLabel} put
     * through {@link #slug}, so a fractional period such as 0.5 becomes "0_5s_sa" rather than
     * carrying a dot into the middle of a file name.
     */
    public static String periodPrefix(double period) {
        return slug(periodLabel(period));
    }

    /**
     * A label reduced to lower case letters, digits and underscores, for use in a file name. Runs
     * of anything else collapse to a single underscore, and leading and trailing underscores are
     * dropped: "3s SA" becomes "3s_sa" and "Joint Inversion (v2)" becomes "joint_inversion_v2".
     *
     * <p>Deliberately locale independent. Under a Turkish locale a default {@code toLowerCase}
     * turns "I" into a dotless "ı", which is then stripped as a non-ascii character, so the same
     * label would name different files on different machines.
     */
    public static String slug(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
    }
}

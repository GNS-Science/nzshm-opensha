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

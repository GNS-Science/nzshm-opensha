package nz.cri.gns.NZSHM22.opensha.hazard.joint;

import com.google.common.base.Preconditions;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import nz.cri.gns.NZSHM22.opensha.hazard.joint.JointHazardInput.RuptureType;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.geo.Location;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.faultSurface.FaultSection;

/**
 * What each rupture of a solution contributes to the hazard at one site, at one intensity measure
 * level. The result of {@link SiteSourceExplorer#exploreAtIml}.
 *
 * <p>The contribution of a rupture is its annual rate of causing the site to exceed the intensity
 * measure level, {@code -ln(1 - P_rup) * P(exceed | rup)}, where {@code P_rup} is the rupture's
 * probability over the forecast's one year time span and {@code P(exceed | rup)} is the conditional
 * probability of exceedance that the GMM gives for it. Those contributions are additive: they sum
 * to {@link #getTotalRate()}, the total annual rate of exceedance, which is the hazard curve's y
 * value at the level (up to the {@code rate} versus {@code 1-exp(-rate)} difference, negligible at
 * the return periods hazard is reported at). This is why the decomposition is exact rather than
 * approximate: it is done in rate space, where the ruptures do not interact.
 *
 * <p>Ruptures dropped by the source filters, and ruptures whose ground motion never reaches the
 * level, contribute zero. See {@link JointHazardCalcSetup#sourceFilters()}.
 */
public class SiteSourceContributions {

    private final FaultSystemSolution solution;
    private final Location site;
    private final double period;
    private final double iml;
    private final double[] rupRates;

    private double totalRate = Double.NaN;
    private SectionStats sectionStats;

    /**
     * @param solution the solution the contributions were calculated from
     * @param site the site location
     * @param period the calculation period, 0 for PGA
     * @param iml the intensity measure level the contributions are for, in linear units (g, or cm/s
     *     for PGV)
     * @param rupRates contribution of each rupture, indexed by rupture index in the solution
     */
    public SiteSourceContributions(
            FaultSystemSolution solution,
            Location site,
            double period,
            double iml,
            double[] rupRates) {
        Preconditions.checkArgument(
                rupRates.length == solution.getRupSet().getNumRuptures(),
                "Expected one contribution per rupture (%s), got %s",
                solution.getRupSet().getNumRuptures(),
                rupRates.length);
        this.solution = solution;
        this.site = site;
        this.period = period;
        this.iml = iml;
        this.rupRates = rupRates;
    }

    public FaultSystemSolution getSolution() {
        return solution;
    }

    public FaultSystemRupSet getRupSet() {
        return solution.getRupSet();
    }

    public Location getSite() {
        return site;
    }

    public double getPeriod() {
        return period;
    }

    /** The intensity measure level the contributions are for, in linear units. */
    public double getIml() {
        return iml;
    }

    /** The annual rate at which a rupture causes the site to exceed {@link #getIml()}. */
    public double getRupRate(int rupIndex) {
        return rupRates[rupIndex];
    }

    /** The total annual rate of exceedance at the site, i.e. the sum over every rupture. */
    public double getTotalRate() {
        if (Double.isNaN(totalRate)) {
            double sum = 0;
            for (double rate : rupRates) {
                sum += rate;
            }
            totalRate = sum;
        }
        return totalRate;
    }

    /** A rupture's share of the total rate of exceedance, between 0 and 1. */
    public double getRupFraction(int rupIndex) {
        return rupRates[rupIndex] / getTotalRate();
    }

    /** Number of ruptures that contribute anything at all. */
    public int getNumContributingRuptures() {
        int count = 0;
        for (double rate : rupRates) {
            if (rate > 0) {
                count++;
            }
        }
        return count;
    }

    /**
     * The hazard that reaches the site through each fault section: the sum of {@link #getRupRate}
     * over every rupture that uses the section, indexed by section index. Cached.
     *
     * <p>Note that these do not sum to {@link #getTotalRate()}. Hazard is calculated per rupture,
     * and a multi-section rupture reaches the site through every section it runs over, so a rupture
     * spanning ten sections is counted in all ten. The quantity is per section — "how much of this
     * site's hazard passes through this section" — not a partition of the total, and a single
     * section's share of the total can exceed 100%.
     */
    public double[] getSectionRates() {
        return sectionStats().hazardRates;
    }

    /**
     * The part of {@link #getSectionRates()} that comes from {@link RuptureType#JOINT} ruptures,
     * i.e. ruptures spanning crustal and interface sections. Indexed by section index.
     *
     * <p>This is what says whether a change at a section is the joint ruptures: a section whose
     * hazard is mostly joint is one the joint rupture set reached.
     */
    public double[] getSectionJointRates() {
        return sectionStats().jointRates;
    }

    /**
     * The magnitude the hazard through each section comes from, as a mean over the contributing
     * ruptures that use it, weighted by what each contributes. {@link Double#NaN} for a section
     * that is not a source for the site.
     *
     * <p>Weighting by contribution rather than by rate is deliberate: the question is what size of
     * event is actually shaking the site through this section, not what the section's ruptures
     * average out at.
     */
    public double[] getSectionMeanMags() {
        return sectionStats().meanMags;
    }

    /**
     * The largest magnitude among the contributing ruptures that use each section, {@link
     * Double#NaN} for a section that is not a source for the site. Unlike {@link
     * #getSectionMeanMags()} a single rare rupture moves this as much as a dominant one.
     */
    public double[] getSectionMaxMags() {
        return sectionStats().maxMags;
    }

    /**
     * The solution's own rate of rupturing each section, in 1/yr: the sum of the solution rate of
     * every rupture that uses it, whatever that rupture does at the site. Indexed by section index.
     *
     * <p>Not a hazard quantity, and orders of magnitude larger than {@link #getSectionRates()},
     * which counts only the part of a rupture's rate that pushes the site over the level. It is
     * here to separate the two ways a section's hazard can change: the section ruptures more often,
     * or the same ruptures now matter more at the site.
     */
    public double[] getSectionSolutionRates() {
        return sectionStats().solutionRates;
    }

    /** Per-section quantities, all derived in one pass over the rupture set. Cached. */
    protected SectionStats sectionStats() {
        if (sectionStats == null) {
            sectionStats = new SectionStats();
        }
        return sectionStats;
    }

    /**
     * The per-section view of the contributions, built in one pass because every quantity here
     * needs the same walk over the ruptures and their section lists, which is the expensive part.
     */
    protected class SectionStats {
        protected final double[] hazardRates;
        protected final double[] jointRates;
        protected final double[] meanMags;
        protected final double[] maxMags;
        protected final double[] solutionRates;

        protected SectionStats() {
            FaultSystemRupSet rupSet = getRupSet();
            int numSections = rupSet.getNumSections();
            hazardRates = new double[numSections];
            jointRates = new double[numSections];
            meanMags = new double[numSections];
            maxMags = new double[numSections];
            solutionRates = new double[numSections];
            Arrays.fill(maxMags, Double.NaN);

            // magnitudes are accumulated as a contribution-weighted sum and divided through at the
            // end, so that a section's mean is over exactly the ruptures that reached the site
            double[] magSums = new double[numSections];

            for (int r = 0; r < rupRates.length; r++) {
                double solutionRate = solution.getRateForRup(r);
                double contribution = rupRates[r];
                if (solutionRate <= 0 && contribution <= 0) {
                    continue;
                }
                double mag = rupSet.getMagForRup(r);
                boolean joint =
                        contribution > 0 && JointHazardInput.typeOf(rupSet, r) == RuptureType.JOINT;
                for (int s : rupSet.getSectionsIndicesForRup(r)) {
                    solutionRates[s] += solutionRate;
                    if (contribution <= 0) {
                        continue;
                    }
                    hazardRates[s] += contribution;
                    magSums[s] += contribution * mag;
                    if (joint) {
                        jointRates[s] += contribution;
                    }
                    if (Double.isNaN(maxMags[s]) || mag > maxMags[s]) {
                        maxMags[s] = mag;
                    }
                }
            }

            for (int s = 0; s < numSections; s++) {
                meanMags[s] = hazardRates[s] > 0 ? magSums[s] / hazardRates[s] : Double.NaN;
            }
        }
    }

    /**
     * Rupture indices sorted by contribution, largest first, dropping ruptures that contribute
     * nothing.
     *
     * @param limit the most indices to return, or a non-positive value for all of them
     */
    public List<Integer> topRuptures(int limit) {
        List<Integer> indices = new ArrayList<>();
        for (int r = 0; r < rupRates.length; r++) {
            if (rupRates[r] > 0) {
                indices.add(r);
            }
        }
        indices.sort(Comparator.comparingDouble((Integer r) -> rupRates[r]).reversed());
        return limit > 0 && indices.size() > limit ? indices.subList(0, limit) : indices;
    }

    /**
     * The names of the parent faults a rupture runs over, in section order and without repeats.
     * Sections with no parent, which is what a rupture set built section by section rather than
     * from a fault model has, fall back to their own name.
     */
    public List<String> parentNames(int rupIndex) {
        Set<String> names = new LinkedHashSet<>();
        for (FaultSection section : getRupSet().getFaultSectionDataForRupture(rupIndex)) {
            String parent = section.getParentSectionName();
            names.add(parent == null ? section.getSectionName() : parent);
        }
        return new ArrayList<>(names);
    }

    /**
     * Writes the top contributing ruptures as a CSV: index, contribution, magnitude, rate, rupture
     * type and the parent faults involved.
     *
     * @param outputFile the file to write
     * @param limit the most ruptures to list, or a non-positive value for all of them
     */
    public void writeCSV(File outputFile, int limit) throws IOException {
        FaultSystemRupSet rupSet = getRupSet();
        CSVFile<String> csv = new CSVFile<>(true);
        csv.addLine(
                "Rupture Index",
                "Contribution Rate (1/yr)",
                "Contribution (%)",
                "Cumulative (%)",
                "Magnitude",
                "Rupture Rate (1/yr)",
                "Type",
                "Sections",
                "Parent Faults");
        double cumulative = 0;
        for (int r : topRuptures(limit)) {
            cumulative += 100 * getRupFraction(r);
            csv.addLine(
                    String.valueOf(r),
                    String.valueOf((float) rupRates[r]),
                    String.valueOf((float) (100 * getRupFraction(r))),
                    String.valueOf((float) cumulative),
                    String.valueOf((float) rupSet.getMagForRup(r)),
                    String.valueOf((float) solution.getRateForRup(r)),
                    JointHazardInput.typeOf(rupSet, r).name(),
                    String.valueOf(rupSet.getSectionsIndicesForRup(r).size()),
                    parentNames(r).stream().collect(Collectors.joining("; ")));
        }
        csv.writeToFile(outputFile);
    }

    @Override
    public String toString() {
        return "total rate of exceeding "
                + (float) iml
                + " "
                + HazardLabels.periodUnits(period)
                + " "
                + HazardLabels.periodLabel(period)
                + ": "
                + (float) getTotalRate()
                + "/yr (return period "
                + (float) (1 / getTotalRate())
                + " years), from "
                + getNumContributingRuptures()
                + " of "
                + rupRates.length
                + " ruptures";
    }
}

package nz.cri.gns.NZSHM22.opensha.hazard.joint;

import com.google.common.base.Preconditions;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
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

    /** Grid spacing in km of the section surfaces built to measure site distances. */
    public static final double SURFACE_GRID_SPACING = 1d;

    private double totalRate = Double.NaN;
    private double[] sectionRates;
    private double[] sectionDistances;

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
     * The hazard that reaches the site through each fault section, under {@link
     * SectionWeighting#participation()}: the sum of {@link #getRupRate} over every rupture that
     * uses the section, indexed by section index. Cached.
     *
     * <p>Note that these do not sum to {@link #getTotalRate()}. A multi-section rupture contributes
     * its full rate to each of its sections, so a rupture spanning ten sections is counted ten
     * times over. The quantity is per section — "how much of this site's hazard passes through this
     * section" — not a partition of the total. Use {@link #getSectionRates(SectionWeighting)} with
     * {@link SectionWeighting#proximity()} for a weighting that does partition it.
     */
    public double[] getSectionRates() {
        if (sectionRates == null) {
            sectionRates = getSectionRates(SectionWeighting.participation());
        }
        return sectionRates;
    }

    /**
     * The hazard credited to each fault section, indexed by section index, sharing each rupture's
     * contribution among its sections the way the given weighting says. Not cached.
     *
     * @param weighting how a rupture's contribution is shared among its sections; see {@link
     *     SectionWeighting} for what the choice means
     */
    public double[] getSectionRates(SectionWeighting weighting) {
        FaultSystemRupSet rupSet = getRupSet();
        double[] distances = getSectionDistances();
        double[] rates = new double[rupSet.getNumSections()];
        for (int r = 0; r < rupRates.length; r++) {
            if (rupRates[r] <= 0) {
                continue;
            }
            List<Integer> sections = rupSet.getSectionsIndicesForRup(r);
            double[] weights = weighting.weights(sections, distances);
            for (int i = 0; i < sections.size(); i++) {
                rates[sections.get(i)] += rupRates[r] * weights[i];
            }
        }
        return rates;
    }

    /**
     * Distance from the site to each fault section's surface, in km, indexed by section index.
     * Cached, because the section surfaces have to be built to measure it.
     *
     * <p>This is {@code rRup}, the closest distance to the section's rupture surface, which is what
     * a GMM sees. A rupture's own {@code rRup} is the smallest of these over its sections, so these
     * distances are what {@link SectionWeighting} uses to decide which part of a rupture actually
     * shook the site.
     */
    public double[] getSectionDistances() {
        if (sectionDistances == null) {
            FaultSystemRupSet rupSet = getRupSet();
            double[] distances = new double[rupSet.getNumSections()];
            for (int s = 0; s < distances.length; s++) {
                distances[s] =
                        rupSet.getFaultSectionData(s)
                                .getFaultSurface(SURFACE_GRID_SPACING, false, false)
                                .getDistanceRup(site);
            }
            sectionDistances = distances;
        }
        return sectionDistances;
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

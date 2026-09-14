package nz.cri.gns.NZSHM22.opensha.hazard.joint;

import com.google.common.base.Preconditions;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.geo.Location;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc.ReturnPeriods;
import org.opensha.sha.faultSurface.FaultSection;

/**
 * Two solutions' {@link SiteSourceContributions} at the same site, matched section by section, so
 * that the change in where a site's hazard comes from can be mapped. See {@link
 * SiteSourceDiffMapPlotter}.
 *
 * <p>Both sides are calculated at <em>one</em> intensity measure level, taken from the reference
 * solution's own hazard curve at a return period. That is the only framing under which the two sets
 * of section rates can be subtracted: a rate of exceeding 0.6g and a rate of exceeding 0.3g are not
 * comparable quantities. It also means the comparison answers "how does the hazard at this level
 * change, and which faults carry the change", not "how does the design level change" — the second
 * question is answered by the two totals, which the comparison also carries.
 *
 * <p>Sections are matched by name, because two solutions built from the same fault model number
 * their sections differently as soon as their rupture sets differ. A section present in only one
 * solution is treated as contributing zero in the other, which is what a fault that a new solution
 * adds or drops should look like.
 */
public class SiteSourceComparison {

    /** Column heading for the baseline solution in the CSV, which carries no display names. */
    protected static final String REFERENCE = "Reference";

    /** Column heading for the solution compared against it. */
    protected static final String COMPARISON = "Comparison";

    private final SiteSourceContributions reference;
    private final SiteSourceContributions comparison;

    private List<FaultSection> sections;
    private int[] referenceIndices;
    private int[] comparisonIndices;
    private double[] referenceRates;
    private double[] comparisonRates;

    /**
     * @param reference the baseline contributions
     * @param comparison the contributions to compare against the baseline
     * @throws IllegalArgumentException if the two were not calculated at the same site, period and
     *     intensity measure level
     */
    public SiteSourceComparison(
            SiteSourceContributions reference, SiteSourceContributions comparison) {
        Preconditions.checkArgument(
                reference.getSite().equals(comparison.getSite()),
                "The two sets of contributions are for different sites, %s and %s",
                reference.getSite(),
                comparison.getSite());
        Preconditions.checkArgument(
                reference.getPeriod() == comparison.getPeriod(),
                "The two sets of contributions are for different periods, %s and %s",
                reference.getPeriod(),
                comparison.getPeriod());
        Preconditions.checkArgument(
                reference.getIml() == comparison.getIml(),
                "The two sets of contributions are for different intensity measure levels, %s and"
                        + " %s. Rates of exceeding different levels cannot be compared; use"
                        + " SiteSourceComparison.compare to calculate both at one level.",
                reference.getIml(),
                comparison.getIml());
        this.reference = reference;
        this.comparison = comparison;
    }

    /**
     * Explores two solutions at one site and compares them, at the intensity measure level the
     * reference solution's hazard curve reaches at the given return period.
     *
     * @param reference explorer for the baseline solution, which also sets the level
     * @param comparison explorer for the solution being compared against it
     * @param location the site
     * @param period the calculation period, 0 for PGA
     * @param returnPeriod the return period that sets the level, read off the reference curve
     */
    public static SiteSourceComparison compare(
            SiteSourceExplorer reference,
            SiteSourceExplorer comparison,
            Location location,
            double period,
            ReturnPeriods returnPeriod) {
        double iml = reference.imlForReturnPeriod(location, period, returnPeriod);
        return new SiteSourceComparison(
                reference.exploreAtIml(location, period, iml),
                comparison.exploreAtIml(location, period, iml));
    }

    public SiteSourceContributions getReference() {
        return reference;
    }

    public SiteSourceContributions getComparison() {
        return comparison;
    }

    public Location getSite() {
        return reference.getSite();
    }

    public double getPeriod() {
        return reference.getPeriod();
    }

    /** The intensity measure level both sides were calculated at, in linear units. */
    public double getIml() {
        return reference.getIml();
    }

    /**
     * The sections that are a source for the site in either solution, in reference section order
     * followed by any the comparison adds. Sections that neither solution routes hazard through are
     * left out: there is nothing to compare, and drawing them would bury the sections that changed.
     */
    public List<FaultSection> getSections() {
        build();
        return sections;
    }

    /** Contribution of each {@link #getSections()} section in the reference solution, in 1/yr. */
    public double[] getReferenceRates() {
        build();
        return referenceRates;
    }

    /** Contribution of each {@link #getSections()} section in the comparison solution, in 1/yr. */
    public double[] getComparisonRates() {
        build();
        return comparisonRates;
    }

    /**
     * The ratio of comparison to reference contribution for each section: greater than one where
     * the new solution routes more of the site's hazard through the section, less where it routes
     * less.
     *
     * <p>A section that is a source in only one of the two gives zero or an infinite ratio. That is
     * the honest answer — the change is unbounded — and it is up to the plotter to clamp it onto a
     * scale.
     */
    public double[] getRatios() {
        build();
        double[] ratios = new double[sections.size()];
        for (int i = 0; i < ratios.length; i++) {
            ratios[i] = comparisonRates[i] / referenceRates[i];
        }
        return ratios;
    }

    /**
     * The change in each section's contribution, in 1/yr: how much more, or less, of the site's
     * rate of exceedance reaches it through that section in the comparison solution than in the
     * reference one. This is what {@link SiteSourceDiffMapPlotter} draws.
     *
     * <p>Unlike {@link #getRatios()} this is always finite, including for a section that only one
     * of the two solutions has, so a map of it never has to clamp anything.
     */
    public double[] getDifferences() {
        build();
        double[] differences = new double[sections.size()];
        for (int i = 0; i < differences.length; i++) {
            differences[i] = comparisonRates[i] - referenceRates[i];
        }
        return differences;
    }

    /**
     * The larger of a section's two contributions, in 1/yr. This is what decides whether a section
     * matters enough to draw: a section that carries nothing in either solution is not worth
     * drawing attention to however much it changed in relative terms.
     *
     * <p>The test is on a rate rather than on {@link #getMaxPercentages()} because the two
     * solutions have different totals. A share threshold cuts the two sides at different amounts of
     * hazard, so a section can pass on one map and fail on the one beside it purely because the
     * other solution's total moved.
     */
    public double[] getMaxRates() {
        build();
        double[] rates = new double[sections.size()];
        for (int i = 0; i < rates.length; i++) {
            rates[i] = Math.max(referenceRates[i], comparisonRates[i]);
        }
        return rates;
    }

    /**
     * The larger of a section's two shares of its own solution's total hazard, in percent. Reported
     * in the CSV; {@link #getMaxRates()} is what the maps cut on, and why.
     */
    public double[] getMaxPercentages() {
        build();
        double[] percentages = new double[sections.size()];
        for (int i = 0; i < percentages.length; i++) {
            percentages[i] =
                    Math.max(
                            100 * referenceRates[i] / reference.getTotalRate(),
                            100 * comparisonRates[i] / comparison.getTotalRate());
        }
        return percentages;
    }

    /**
     * A per-section quantity of the reference solution, re-indexed onto {@link #getSections()}.
     * Sections the reference solution does not have take {@code absent}.
     *
     * @param bySection the quantity, indexed by the reference solution's own section indices
     * @param absent the value for a section the reference solution does not have
     */
    public double[] referenceValues(double[] bySection, double absent) {
        build();
        return values(bySection, referenceIndices, absent);
    }

    /** As {@link #referenceValues}, for the comparison solution. */
    public double[] comparisonValues(double[] bySection, double absent) {
        build();
        return values(bySection, comparisonIndices, absent);
    }

    protected static double[] values(double[] bySection, int[] indices, double absent) {
        double[] values = new double[indices.length];
        for (int i = 0; i < indices.length; i++) {
            values[i] = indices[i] < 0 ? absent : bySection[indices[i]];
        }
        return values;
    }

    /**
     * Matches the two solutions' sections by name, giving the merged section list and, for each
     * merged section, where it sits in each solution's own section indexing or -1 if that solution
     * does not have it. Every other per-section quantity is read through those indices, so the two
     * solutions only ever have to be matched once. Idempotent.
     */
    protected void build() {
        if (sections != null) {
            return;
        }
        double[] referenceBySection = reference.getSectionRates();
        double[] comparisonBySection = comparison.getSectionRates();

        Map<String, Integer> comparisonByName =
                contributingByName(comparison.getRupSet(), comparisonBySection);

        // reference sections first, so the merged list keeps the reference solution's ordering
        Map<String, FaultSection> merged = new LinkedHashMap<>();
        Map<String, Integer> referenceByName = new HashMap<>();
        FaultSystemRupSet referenceRupSet = reference.getRupSet();
        for (int s = 0; s < referenceBySection.length; s++) {
            FaultSection section = referenceRupSet.getFaultSectionData(s);
            String name = section.getSectionName();
            if (referenceBySection[s] > 0 || comparisonByName.containsKey(name)) {
                Preconditions.checkState(
                        referenceByName.put(name, s) == null,
                        "Two sections are called '%s', so the two solutions cannot be matched by"
                                + " section name",
                        name);
                merged.put(name, section);
            }
        }
        FaultSystemRupSet comparisonRupSet = comparison.getRupSet();
        for (Map.Entry<String, Integer> entry : comparisonByName.entrySet()) {
            merged.putIfAbsent(
                    entry.getKey(), comparisonRupSet.getFaultSectionData(entry.getValue()));
        }

        sections = new ArrayList<>(merged.values());
        referenceIndices = new int[sections.size()];
        comparisonIndices = new int[sections.size()];
        for (int i = 0; i < sections.size(); i++) {
            String name = sections.get(i).getSectionName();
            referenceIndices[i] = referenceByName.getOrDefault(name, -1);
            comparisonIndices[i] = comparisonByName.getOrDefault(name, -1);
        }
        referenceRates = values(referenceBySection, referenceIndices, 0d);
        comparisonRates = values(comparisonBySection, comparisonIndices, 0d);
    }

    /**
     * The section index of every section that routes hazard to the site, keyed by section name.
     * Sections that contribute nothing are left out: they are not a source for the site, so there
     * is nothing to match.
     *
     * @throws IllegalStateException if two contributing sections share a name, which would make the
     *     match between the two solutions ambiguous
     */
    protected static Map<String, Integer> contributingByName(
            FaultSystemRupSet rupSet, double[] rates) {
        Map<String, Integer> byName = new LinkedHashMap<>();
        for (int s = 0; s < rates.length; s++) {
            if (rates[s] <= 0) {
                continue;
            }
            String name = rupSet.getFaultSectionData(s).getSectionName();
            Preconditions.checkState(
                    byName.put(name, s) == null,
                    "Two sections are called '%s', so the two solutions cannot be matched by section"
                            + " name",
                    name);
        }
        return byName;
    }

    /**
     * What changed at one section, as the per-site table and the CSV both list it. Every rate is in
     * 1/yr; a magnitude is {@link Double#NaN} where that solution routes no hazard through the
     * section at all.
     */
    public static class SectionChange {
        public final String name;
        public final double referenceRate;
        public final double comparisonRate;
        public final double referenceSolutionRate;
        public final double comparisonSolutionRate;
        public final double referenceMeanMag;
        public final double comparisonMeanMag;
        public final double referenceMaxMag;
        public final double comparisonMaxMag;

        /** The section's larger of its two shares of its own solution's total, in percent. */
        public final double maxPercent;

        /**
         * Share of the comparison solution's hazard through the section that is joint, in percent.
         */
        public final double jointPercent;

        protected SectionChange(
                String name,
                double referenceRate,
                double comparisonRate,
                double referenceSolutionRate,
                double comparisonSolutionRate,
                double referenceMeanMag,
                double comparisonMeanMag,
                double referenceMaxMag,
                double comparisonMaxMag,
                double maxPercent,
                double jointPercent) {
            this.name = name;
            this.referenceRate = referenceRate;
            this.comparisonRate = comparisonRate;
            this.referenceSolutionRate = referenceSolutionRate;
            this.comparisonSolutionRate = comparisonSolutionRate;
            this.referenceMeanMag = referenceMeanMag;
            this.comparisonMeanMag = comparisonMeanMag;
            this.referenceMaxMag = referenceMaxMag;
            this.comparisonMaxMag = comparisonMaxMag;
            this.maxPercent = maxPercent;
            this.jointPercent = jointPercent;
        }

        /** How much more of the site's hazard reaches it through the section, in 1/yr. */
        public double getChange() {
            return comparisonRate - referenceRate;
        }

        /** The change as a multiple, zero or infinite for a section only one solution has. */
        public double getRatio() {
            return comparisonRate / referenceRate;
        }
    }

    /**
     * The sections whose hazard changed most, largest absolute change first. This is the difference
     * map read as numbers, and the order is the map's own: the sections it draws furthest from
     * unchanged come first.
     *
     * @param limit the most sections to return, or a non-positive value for all of them
     */
    public List<SectionChange> topChanges(int limit) {
        build();
        double[] referenceSolutionRates = referenceValues(reference.getSectionSolutionRates(), 0d);
        double[] comparisonSolutionRates =
                comparisonValues(comparison.getSectionSolutionRates(), 0d);
        double[] referenceMeanMags = referenceValues(reference.getSectionMeanMags(), Double.NaN);
        double[] comparisonMeanMags = comparisonValues(comparison.getSectionMeanMags(), Double.NaN);
        double[] referenceMaxMags = referenceValues(reference.getSectionMaxMags(), Double.NaN);
        double[] comparisonMaxMags = comparisonValues(comparison.getSectionMaxMags(), Double.NaN);
        double[] comparisonJointRates = comparisonValues(comparison.getSectionJointRates(), 0d);
        double[] maxPercentages = getMaxPercentages();

        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < sections.size(); i++) {
            order.add(i);
        }
        order.sort(
                (a, b) ->
                        Double.compare(
                                Math.abs(comparisonRates[b] - referenceRates[b]),
                                Math.abs(comparisonRates[a] - referenceRates[a])));
        if (limit > 0 && order.size() > limit) {
            order = order.subList(0, limit);
        }

        List<SectionChange> changes = new ArrayList<>();
        for (int i : order) {
            changes.add(
                    new SectionChange(
                            sections.get(i).getSectionName(),
                            referenceRates[i],
                            comparisonRates[i],
                            referenceSolutionRates[i],
                            comparisonSolutionRates[i],
                            referenceMeanMags[i],
                            comparisonMeanMags[i],
                            referenceMaxMags[i],
                            comparisonMaxMags[i],
                            maxPercentages[i],
                            comparisonRates[i] > 0
                                    ? 100 * comparisonJointRates[i] / comparisonRates[i]
                                    : 0d));
        }
        return changes;
    }

    /**
     * Writes the per-section comparison as a CSV, largest absolute change first. The same rows the
     * per-site page tabulates, without its cut-off.
     *
     * @param outputFile the file to write
     * @param limit the most sections to list, or a non-positive value for all of them
     */
    public void writeCSV(File outputFile, int limit) throws IOException {
        CSVFile<String> csv = new CSVFile<>(true);
        csv.addLine(
                "Section",
                "Hazard Through Section, " + REFERENCE + " (1/yr)",
                "Hazard Through Section, " + COMPARISON + " (1/yr)",
                "Change (1/yr)",
                "Ratio",
                "Larger Share (%)",
                "Section Rupture Rate, " + REFERENCE + " (1/yr)",
                "Section Rupture Rate, " + COMPARISON + " (1/yr)",
                "Mean Magnitude, " + REFERENCE,
                "Mean Magnitude, " + COMPARISON,
                "Max Magnitude, " + REFERENCE,
                "Max Magnitude, " + COMPARISON,
                "Joint Share, " + COMPARISON + " (%)");
        for (SectionChange change : topChanges(limit)) {
            csv.addLine(
                    change.name,
                    String.valueOf((float) change.referenceRate),
                    String.valueOf((float) change.comparisonRate),
                    String.valueOf((float) change.getChange()),
                    String.valueOf((float) change.getRatio()),
                    String.valueOf((float) change.maxPercent),
                    String.valueOf((float) change.referenceSolutionRate),
                    String.valueOf((float) change.comparisonSolutionRate),
                    String.valueOf((float) change.referenceMeanMag),
                    String.valueOf((float) change.comparisonMeanMag),
                    String.valueOf((float) change.referenceMaxMag),
                    String.valueOf((float) change.comparisonMaxMag),
                    String.valueOf((float) change.jointPercent));
        }
        csv.writeToFile(outputFile);
    }

    @Override
    public String toString() {
        return "total rate of exceeding "
                + (float) getIml()
                + " "
                + HazardLabels.periodUnits(getPeriod())
                + " "
                + HazardLabels.periodLabel(getPeriod())
                + ": "
                + (float) reference.getTotalRate()
                + "/yr -> "
                + (float) comparison.getTotalRate()
                + "/yr (x"
                + (float) (comparison.getTotalRate() / reference.getTotalRate())
                + "), over "
                + getSections().size()
                + " sections that are a source in either solution";
    }
}

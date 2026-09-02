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

    private final SiteSourceContributions reference;
    private final SiteSourceContributions comparison;
    private final SectionWeighting weighting;

    private List<FaultSection> sections;
    private double[] referenceRates;
    private double[] comparisonRates;

    /**
     * @param reference the baseline contributions
     * @param comparison the contributions to compare against the baseline
     * @param weighting how each rupture's contribution is shared among its sections; both sides use
     *     the same one, or the difference would be an artefact of the weighting
     * @throws IllegalArgumentException if the two were not calculated at the same site, period and
     *     intensity measure level
     */
    public SiteSourceComparison(
            SiteSourceContributions reference,
            SiteSourceContributions comparison,
            SectionWeighting weighting) {
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
        this.weighting = weighting;
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
     * @param weighting how each rupture's contribution is shared among its sections
     */
    public static SiteSourceComparison compare(
            SiteSourceExplorer reference,
            SiteSourceExplorer comparison,
            Location location,
            double period,
            ReturnPeriods returnPeriod,
            SectionWeighting weighting) {
        double iml = reference.imlForReturnPeriod(location, period, returnPeriod);
        return new SiteSourceComparison(
                reference.exploreAtIml(location, period, iml),
                comparison.exploreAtIml(location, period, iml),
                weighting);
    }

    public SiteSourceContributions getReference() {
        return reference;
    }

    public SiteSourceContributions getComparison() {
        return comparison;
    }

    public SectionWeighting getWeighting() {
        return weighting;
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
     * The larger of a section's two shares of its own solution's total hazard, in percent. This is
     * what decides whether a section matters enough to colour: a section that is negligible in both
     * solutions is not worth drawing attention to however much it changed in relative terms.
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

    /** Matches the two solutions' sections by name. Idempotent. */
    protected void build() {
        if (sections != null) {
            return;
        }
        double[] referenceBySection = reference.getSectionRates(weighting);
        double[] comparisonBySection = comparison.getSectionRates(weighting);

        Map<String, Double> comparisonByName =
                ratesByName(comparison.getRupSet(), comparisonBySection);

        // reference sections first, so the merged list keeps the reference solution's ordering
        Map<String, FaultSection> merged = new LinkedHashMap<>();
        Map<String, Double> referenceByName = new HashMap<>();
        FaultSystemRupSet referenceRupSet = reference.getRupSet();
        for (int s = 0; s < referenceBySection.length; s++) {
            FaultSection section = referenceRupSet.getFaultSectionData(s);
            String name = section.getSectionName();
            if (referenceBySection[s] > 0 || comparisonByName.containsKey(name)) {
                merged.put(name, section);
                referenceByName.put(name, referenceBySection[s]);
            }
        }
        FaultSystemRupSet comparisonRupSet = comparison.getRupSet();
        for (int s = 0; s < comparisonBySection.length; s++) {
            if (comparisonBySection[s] <= 0) {
                continue;
            }
            FaultSection section = comparisonRupSet.getFaultSectionData(s);
            merged.putIfAbsent(section.getSectionName(), section);
        }

        sections = new ArrayList<>(merged.values());
        referenceRates = new double[sections.size()];
        comparisonRates = new double[sections.size()];
        for (int i = 0; i < sections.size(); i++) {
            String name = sections.get(i).getSectionName();
            referenceRates[i] = referenceByName.getOrDefault(name, 0d);
            comparisonRates[i] = comparisonByName.getOrDefault(name, 0d);
        }
    }

    /**
     * Section contributions keyed by section name, dropping sections that contribute nothing.
     *
     * @throws IllegalStateException if two contributing sections share a name, which would make the
     *     match between the two solutions ambiguous
     */
    protected static Map<String, Double> ratesByName(FaultSystemRupSet rupSet, double[] rates) {
        Map<String, Double> byName = new HashMap<>();
        for (int s = 0; s < rates.length; s++) {
            if (rates[s] <= 0) {
                continue;
            }
            String name = rupSet.getFaultSectionData(s).getSectionName();
            Preconditions.checkState(
                    byName.put(name, rates[s]) == null,
                    "Two sections are called '%s', so the two solutions cannot be matched by section"
                            + " name",
                    name);
        }
        return byName;
    }

    /**
     * Writes the per-section comparison as a CSV, largest absolute change first.
     *
     * @param outputFile the file to write
     * @param limit the most sections to list, or a non-positive value for all of them
     */
    public void writeCSV(File outputFile, int limit) throws IOException {
        build();
        double[] ratios = getRatios();
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

        CSVFile<String> csv = new CSVFile<>(true);
        csv.addLine(
                "Section",
                "Reference Rate (1/yr)",
                "Comparison Rate (1/yr)",
                "Change (1/yr)",
                "Ratio",
                "Larger Share (%)");
        for (int i : order) {
            csv.addLine(
                    sections.get(i).getSectionName(),
                    String.valueOf((float) referenceRates[i]),
                    String.valueOf((float) comparisonRates[i]),
                    String.valueOf((float) (comparisonRates[i] - referenceRates[i])),
                    String.valueOf((float) ratios[i]),
                    String.valueOf((float) maxPercentages[i]));
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

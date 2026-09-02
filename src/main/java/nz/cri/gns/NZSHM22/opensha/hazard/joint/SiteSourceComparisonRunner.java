package nz.cri.gns.NZSHM22.opensha.hazard.joint;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import nz.cri.gns.NZSHM22.opensha.data.location.NzshmCommonLocations;
import org.opensha.commons.geo.Location;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc.ReturnPeriods;

/**
 * Command line entry point for {@link SiteSourceComparison}: takes two solutions and draws how the
 * sources of one site's hazard change between them.
 *
 * <p>Usage: {@code SiteSourceComparisonRunner <reference.zip> <comparison.zip> [outputDir]
 * [siteName] [period]}. The reference solution sets the intensity measure level that both are
 * disaggregated at; see {@link SiteSourceComparison} for why there has to be only one.
 *
 * <p>It writes the diff of each of the two maps that {@link SiteSourceExplorerRunner} produces: the
 * participation diff, showing which faults carry more or less of the site's hazard than before, and
 * the influence diff, showing which stretches of fault do more or less of the shaking.
 */
public class SiteSourceComparisonRunner {

    public static final String DEFAULT_REFERENCE = "C:/tmp/InversionSolution.zip";
    public static final String DEFAULT_COMPARISON = "C:/tmp/testjoint4/testJointSOlution.zip";
    public static final String DEFAULT_OUTPUT_DIR = "C:/tmp/site-sources-diff";
    public static final String DEFAULT_SITE = "Wellington";
    public static final double DEFAULT_PERIOD = 0d;
    public static final ReturnPeriods DEFAULT_RETURN_PERIOD = ReturnPeriods.TEN_IN_50;

    /** Sections below this share of their own solution's hazard are left off the diff maps. */
    public static final double OMIT_BELOW_PERCENT =
            SiteSourceExplorerRunner.INFLUENCE_OMIT_BELOW_PERCENT;

    protected SiteSourceComparisonRunner() {}

    public static void main(String[] args) throws IOException {
        File reference = new File(args.length > 0 ? args[0] : DEFAULT_REFERENCE);
        File comparison = new File(args.length > 1 ? args[1] : DEFAULT_COMPARISON);
        File outputDir = new File(args.length > 2 ? args[2] : DEFAULT_OUTPUT_DIR);
        String siteName = args.length > 3 ? args[3] : DEFAULT_SITE;
        double period = args.length > 4 ? Double.parseDouble(args[4]) : DEFAULT_PERIOD;

        run(reference, comparison, outputDir, siteName, period, DEFAULT_RETURN_PERIOD);
    }

    /**
     * Compares two solutions at one site and writes a diff map per {@link SectionWeighting}, plus a
     * per-section CSV.
     *
     * @param referenceFile the baseline solution, which also sets the intensity measure level
     * @param comparisonFile the solution compared against it
     * @param outputDir directory that the maps and the CSV are written to
     * @param siteName one of the {@link NzshmCommonLocations#nzLocations()} names
     * @param period the calculation period, 0 for PGA
     * @param returnPeriod the return period that sets the level, read off the reference curve
     * @return the maps that were written, participation first
     */
    public static File[] run(
            File referenceFile,
            File comparisonFile,
            File outputDir,
            String siteName,
            double period,
            ReturnPeriods returnPeriod)
            throws IOException {
        Map<String, Location> locations = NzshmCommonLocations.nzLocations();
        Location location = locations.get(siteName);
        if (location == null) {
            throw new IllegalArgumentException(
                    "Unknown site \"" + siteName + "\"; known sites are " + locations.keySet());
        }

        SiteSourceExplorer reference = explorer(referenceFile, period);
        SiteSourceExplorer comparison = explorer(comparisonFile, period);

        String prefix =
                "site_sources_diff_"
                        + HazardLabels.slug(siteName)
                        + "_"
                        + HazardLabels.periodPrefix(period)
                        + "_"
                        + HazardLabels.slug(returnPeriod.name());

        File[] maps = new File[2];
        SectionWeighting[] weightings = {
            SectionWeighting.participation(), SectionWeighting.proximity()
        };
        String[] suffixes = {"_participation", "_influence"};
        for (int i = 0; i < weightings.length; i++) {
            System.out.println(
                    "Comparing "
                            + returnPeriod.label
                            + " "
                            + HazardLabels.periodLabel(period)
                            + " at "
                            + siteName
                            + ", "
                            + weightings[i].getLabel());
            SiteSourceComparison diff =
                    SiteSourceComparison.compare(
                            reference, comparison, location, period, returnPeriod, weightings[i]);
            System.out.println(diff);

            maps[i] =
                    new SiteSourceDiffMapPlotter()
                            .setOmitBelowPercent(OMIT_BELOW_PERCENT)
                            .plot(outputDir, prefix + suffixes[i], diff, siteName);
            System.out.println("Wrote " + maps[i]);

            File csv = new File(outputDir, prefix + suffixes[i] + "_sections.csv");
            diff.writeCSV(csv, 0);
            System.out.println("Wrote " + csv);
        }
        return maps;
    }

    /**
     * Loads a solution and builds an explorer for it, backfilling fault section properties on
     * solutions saved before they existed. See {@link JointSolutions#backfill}.
     */
    protected static SiteSourceExplorer explorer(File solutionFile, double period)
            throws IOException {
        System.out.println("Loading " + solutionFile);
        FaultSystemSolution solution =
                JointSolutions.backfill(FaultSystemSolution.load(solutionFile));
        return new SiteSourceExplorer(new JointHazardInput(solution).setPeriods(period));
    }
}

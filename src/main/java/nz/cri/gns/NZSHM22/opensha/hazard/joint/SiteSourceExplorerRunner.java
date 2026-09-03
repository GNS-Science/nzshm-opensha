package nz.cri.gns.NZSHM22.opensha.hazard.joint;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import nz.cri.gns.NZSHM22.opensha.data.location.NzshmCommonLocations;
import org.opensha.commons.geo.Location;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc.ReturnPeriods;

/**
 * Command line entry point for {@link SiteSourceExplorer}: works out which ruptures of a solution
 * drive the hazard at one named site and draws them on maps.
 *
 * <p>The map credits every section of a rupture with the whole of that rupture, so it shows which
 * faults the site's hazard reaches it through. A long multi-fault rupture is therefore drawn along
 * its whole length, which is what shows how far the ruptures behind the site's hazard run.
 *
 * <p>Usage: {@code SiteSourceExplorerRunner <solution.zip> [outputDir] [siteName] [period]}, where
 * the site name is one of {@link NzshmCommonLocations#nzLocations()} and the period is 0 for PGA.
 * Everything but the solution has a default; running it with no arguments at all uses {@link
 * #DEFAULT_SOLUTION}.
 */
public class SiteSourceExplorerRunner {

    /** Solution used when none is given on the command line. */
    public static final String DEFAULT_SOLUTION = "C:/tmp/testjoint4/testJointSOlution.zip";

    public static final String DEFAULT_OUTPUT_DIR = "C:/tmp/site-sources";
    public static final String DEFAULT_SITE = "Wellington";
    public static final double DEFAULT_PERIOD = 0d;
    public static final ReturnPeriods DEFAULT_RETURN_PERIOD = ReturnPeriods.TEN_IN_50;

    /**
     * How many ruptures the rupture CSV lists. A whole NZ rupture set has hundreds of thousands of
     * contributing ruptures and writing them all makes a file too big to open; the top few thousand
     * cover essentially all of the hazard.
     */
    public static final int RUPTURE_CSV_LIMIT = 5000;

    /**
     * Contribution below which a section is left off the map, as a percentage of the site's total
     * hazard. Sections under this are a source for the site, but a negligible one.
     */
    public static final double OMIT_BELOW_PERCENT = 0.2;

    protected SiteSourceExplorerRunner() {}

    public static void main(String[] args) throws IOException {
        File solutionFile = new File(args.length > 0 ? args[0] : DEFAULT_SOLUTION);
        File outputDir = new File(args.length > 1 ? args[1] : DEFAULT_OUTPUT_DIR);
        String siteName = args.length > 2 ? args[2] : DEFAULT_SITE;
        double period = args.length > 3 ? Double.parseDouble(args[3]) : DEFAULT_PERIOD;

        run(solutionFile, outputDir, siteName, period, DEFAULT_RETURN_PERIOD);
    }

    /**
     * Explores one site of one solution and writes the map and the rupture CSV.
     *
     * @param solutionFile the solution to explore
     * @param outputDir directory that the map and the CSV are written to
     * @param siteName one of the {@link NzshmCommonLocations#nzLocations()} names
     * @param period the calculation period, 0 for PGA
     * @param returnPeriod the return period whose intensity level is disaggregated
     * @return the map that was written
     */
    public static File run(
            File solutionFile,
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

        System.out.println("Loading " + solutionFile);
        // backfilled so that solutions saved before fault section properties existed, i.e. without
        // tectonic region types, can be explored without being converted by hand
        FaultSystemSolution solution =
                JointSolutions.backfill(FaultSystemSolution.load(solutionFile));

        JointHazardInput input = new JointHazardInput(solution).setPeriods(period);
        System.out.println(input.validate());

        SiteSourceExplorer explorer = new SiteSourceExplorer(input);
        System.out.println(
                "Disaggregating "
                        + returnPeriod.label
                        + " "
                        + HazardLabels.periodLabel(period)
                        + " at "
                        + siteName);
        SiteSourceContributions contributions = explorer.explore(location, period, returnPeriod);
        System.out.println(contributions);
        System.out.println(sectionSummary(contributions));

        String prefix =
                "site_sources_"
                        + HazardLabels.slug(siteName)
                        + "_"
                        + HazardLabels.periodPrefix(period)
                        + "_"
                        + HazardLabels.slug(returnPeriod.name());

        File map =
                new SiteSourceMapPlotter()
                        .setOmitBelowRate(OMIT_BELOW_PERCENT / 100 * contributions.getTotalRate())
                        .plot(outputDir, prefix + "_sections", contributions, siteName);
        System.out.println("Wrote " + map);

        File csv = new File(outputDir, prefix + "_ruptures.csv");
        contributions.writeCSV(csv, RUPTURE_CSV_LIMIT);
        System.out.println("Wrote " + csv);

        int shown = Math.min(20, contributions.getNumContributingRuptures());
        System.out.println("Top " + shown + " ruptures:");
        for (int r : contributions.topRuptures(shown)) {
            System.out.println(
                    String.format(
                            "  rup %7d  %5.2f%%  M%.2f  %s  %s",
                            r,
                            100 * contributions.getRupFraction(r),
                            contributions.getRupSet().getMagForRup(r),
                            JointHazardInput.typeOf(contributions.getRupSet(), r),
                            String.join(", ", contributions.parentNames(r))));
        }
        return map;
    }

    /**
     * How many sections are a source for the site and how many of those clear the map's threshold.
     * A section is a source if any rupture that runs over it reaches the site's intensity level;
     * with long multi-fault ruptures that is most of the rupture set, which is why the map leaves
     * the negligible ones off rather than colouring the whole country.
     */
    protected static String sectionSummary(SiteSourceContributions contributions) {
        double[] rates = contributions.getSectionRates();
        double total = contributions.getTotalRate();
        int sources = 0;
        int aboveThreshold = 0;
        for (double rate : rates) {
            if (rate <= 0) {
                continue;
            }
            sources++;
            if (100 * rate / total >= OMIT_BELOW_PERCENT) {
                aboveThreshold++;
            }
        }
        return "sections: "
                + rates.length
                + " in the solution, "
                + sources
                + " a source for this site, "
                + aboveThreshold
                + " above the "
                + OMIT_BELOW_PERCENT
                + "% threshold";
    }
}

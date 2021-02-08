package nz.cri.gns.NSHM.opensha.scripts;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import nz.cri.gns.NSHM.opensha.inversion.NSHMInversionRunner;
import nz.cri.gns.NSHM.opensha.ruptures.FaultIdFilter;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.dom4j.DocumentException;

import nz.cri.gns.NSHM.opensha.ruptures.NSHMRuptureSetBuilder;
import nz.cri.gns.NSHM.opensha.ruptures.NSHMRuptureSetBuilder.RupturePermutationStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.SlipAlongRuptureModelRupSet;
import scratch.UCERF3.utils.FaultSystemIO;

/**
 * Wrapper for building NSHM Rupture sets from CLI
 */
public class scriptCrustalInversionRunner {

    public static CommandLine parseCommandLine(String[] args) throws ParseException {

        Option faultIdInOption = new Option("n", "faultIdIn", true, "a list of faultSectionIDs to filter on");
        faultIdInOption.setArgs(Option.UNLIMITED_VALUES);
        faultIdInOption.setValueSeparator(',');

        Options options = new Options()
                .addRequiredOption("f", "fsdFile", true, "an opensha-xml Fault Source file")
                .addRequiredOption("o", "outputDir", true, "an existing directory to receive output file(s)")
                .addOption("g", "generateRuptureSet", false, "generate rupture set (flag only)")
                .addOption("x", "rupSetForInversion", true, "rupture set file for inversion")
                .addOption("l", "maxSubSectionLength", true, "maximum sub section length (in units of DDW) default")
                .addOption("d", "maxDistance", true, "max distance for linking multi fault ruptures, km")
                .addOption("d", "maxFaultSections", true, "(for testing) set number fault ruptures to process, default 1000")
                .addOption("k", "skipFaultSections", true, "(for testing) skip n fault ruptures, default 0")
                .addOption("i", "inversionMins", true, "run inversions for this many minutes")
                .addOption("y", "syncInterval", true, "seconds between inversion synchronisations")
                .addOption("r", "runInversion", false, "run inversion stage (flag only)")
                .addOption("p", "minSubSectsPerParent", true, "min number of subsections per parent fault, when building ruptures")
                .addOption("s", "ruptureStrategy", true, "rupture permutation strategy - one of `DOWNDIP`, `UCERF3`, `POINTS`")
                .addOption("t", "faultIdFilterType", true, "determines the behaviour of the filter set up by faultIdIn. One of ANY, ALL, EXACT. ANY is the default.")
                .addOption("h", "thinning", true, "crustal fault thinning factor")
                .addOption(faultIdInOption);
        return new DefaultParser().parse(options, args);
    }

    protected static void generateRuptures(CommandLine cmd) throws IOException, DocumentException {
        File outputDir = new File(cmd.getOptionValue("outputDir"));
        File rupSetFile = new File(outputDir, "CFM_crustal_rupture_set.zip");
        File fsdFile = new File(cmd.getOptionValue("fsdFile"));
        NSHMRuptureSetBuilder builder = new NSHMRuptureSetBuilder();
        builder.setFaultModelFile(fsdFile);

        //		.setFaultIdIn(Sets.newHashSet(89, 90, 91, 92, 93));
        //		.setMaxFaultSections(2000) 	// overide defauls like so
        //		.setPermutationStrategy(RupturePermutationStrategy.POINTS);

        System.out.println("Arguments");
        System.out.println("=========");
        System.out.println("fsdFile: " + cmd.getOptionValue("fsdFile"));
        System.out.println("outputDir: " + outputDir);

        if (cmd.hasOption("ruptureStrategy")) {
            System.out.println("set permutationStrategy to " + cmd.getOptionValue("ruptureStrategy"));
            RupturePermutationStrategy permutationStrategyClass = RupturePermutationStrategy.valueOf(cmd.getOptionValue("ruptureStrategy"));
            builder.setPermutationStrategy(permutationStrategyClass);
        }
        if (cmd.hasOption("maxSubSectionLength")) {
            System.out.println("set maxSubSectionLength to " + cmd.getOptionValue("maxSubSectionLength"));
            builder.setMaxSubSectionLength(Double.parseDouble(cmd.getOptionValue("maxSubSectionLength")));
        }
        if (cmd.hasOption("maxDistance")) {
            System.out.println("set maxDistance to " + cmd.getOptionValue("maxDistance"));
            builder.setMaxJumpDistance(Double.parseDouble(cmd.getOptionValue("maxDistance")));
        }
        if (cmd.hasOption("maxFaultSections")) {
            System.out.println("set maxFaultSections to " + cmd.getOptionValue("maxFaultSections"));
            builder.setMaxFaultSections(Integer.parseInt(cmd.getOptionValue("maxFaultSections")));
        }
        if (cmd.hasOption("skipFaultSections")) {
            System.out.println("set skipFaultSections to " + cmd.getOptionValue("skipFaultSections"));
            builder.setSkipFaultSections(Integer.parseInt(cmd.getOptionValue("skipFaultSections")));
        }
        if (cmd.hasOption("faultIdIn")) {
            String filterTypeString = cmd.hasOption("faultIdFilterType") ? cmd.getOptionValue("faultIdFilterType") : "ANY";
            System.out.println("set faultIds to " + filterTypeString + " : " + String.join(",", cmd.getOptionValues("faultIdIn")));
            FaultIdFilter.FilterType filterType = FaultIdFilter.FilterType.valueOf(filterTypeString);
            Set<Integer> faultIdIn = Stream.of(cmd.getOptionValues("faultIdIn")).map(Integer::parseInt).collect(Collectors.toSet());
            builder.setFaultIdFilter(filterType, faultIdIn);
        }
        if (cmd.hasOption("minSubSectsPerParent")) {
            System.out.println("set minSubSectsPerParent to " + cmd.getOptionValue("minSubSectsPerParent"));
            builder.setMinSubSectsPerParent(Integer.parseInt(cmd.getOptionValue("minSubSectsPerParent")));
        }
        if(cmd.hasOption("thinning")){
            builder.setThinningFactor(Double.parseDouble(cmd.getOptionValue("thinning")));
        }

        System.out.println("=========");

        builder.setDownDipMinFill(0.8d)
        	.setThinningFactor(0.1)
        	.setDownDipAspectRatio(2, 5, 7)
        	.setDownDipSizeCoarseness(0.01)
        	.setDownDipPositionCoarseness(0.01);

        builder.setSubductionFault("Hikurangi", new File("data/FaultModels/subduction_tile_parameters.csv"));
        SlipAlongRuptureModelRupSet rupSet = builder.buildRuptureSet();
        FaultSystemIO.writeRupSet(rupSet, rupSetFile);

        plotRuptureFrequency(rupSet, new File("data/output/histogram" + (new Date()).getTime() + ".csv"));
    }

    protected static int sectionCount(ClusterRupture rupture) {
        int count = 0;
        for (FaultSubsectionCluster cluster : rupture.getClustersIterable()) {
            count += cluster.subSects.size();
        }
        return count;
    }

    protected static HashMap<Integer, Integer> makeHistogram(List<ClusterRupture> ruptures) {
        HashMap<Integer, Integer> result = new HashMap<>();
        for (ClusterRupture rupture : ruptures) {
            result.compute(sectionCount(rupture), (k, v) -> v == null ? 1 : v + 1);
        }
        return result;
    }

    protected static void plotRuptureFrequency(SlipAlongRuptureModelRupSet rupSet, File output) throws IOException {
        PrintWriter writer = new PrintWriter(new FileWriter(output));
        HashMap<Integer, Integer> histogram = makeHistogram(rupSet.getClusterRuptures());
        histogram.keySet().stream().sorted().forEach(key -> {
            writer.println("" + key + "," + histogram.get(key));
        });
        writer.close();
    }

    protected static void runInversion(CommandLine cmd) throws IOException, DocumentException {
        long inversionMins = 1; // run it for this many minutes
        long syncInterval = 10; // seconds between inversion synchronisations
        File outputDir = new File(cmd.getOptionValue("outputDir"));
        File solFile = new File(outputDir, "CFM_crustal_solution.zip");
        File rupSetFile = null;

        if (cmd.hasOption("generateRuptureSet")) {
            rupSetFile = new File(outputDir, "CFM_crustal_rupture_set.zip");
        } else {
            rupSetFile = new File(cmd.getOptionValue("rupSetForInversion"));
        }
        if (!outputDir.exists()) {
            throw new IllegalArgumentException("outputDir " + outputDir + " does not exist");
        }

        if (cmd.hasOption("inversionMins")) {
            System.out.println("set inversionMins to " + cmd.getOptionValue("inversionMins"));
            inversionMins = Long.parseLong(cmd.getOptionValue("inversionMins"));
        }
        if (cmd.hasOption("syncInterval")) {
            System.out.println("set syncInterval to " + cmd.getOptionValue("syncInterval"));
            syncInterval = Long.parseLong(cmd.getOptionValue("syncInterval"));
        }

        NSHMInversionRunner runner = new NSHMInversionRunner()
                .setInversionMinutes(inversionMins)
                .setSyncInterval(syncInterval)
        		.setRuptureSetFile(rupSetFile);
        FaultSystemSolution solution = runner.runInversion();
        FaultSystemIO.writeSol(solution, solFile);
    }

    public static void main(String[] args) throws DocumentException, IOException, ParseException {
        CommandLine cmd = parseCommandLine(args);

        if (cmd.hasOption("generateRuptureSet")) {
            generateRuptures(cmd);
        }

        if (cmd.hasOption("runInversion")) {
            runInversion(cmd);
        }

        System.exit(0);
    }
}

package nz.cri.gns.NSHM.opensha.demo;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import nz.cri.gns.NSHM.opensha.util.FaultSectionList;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.opensha.sha.faultSurface.FaultSection;
import scratch.UCERF3.enumTreeBranches.FaultModels;

public class SectionXml2Csv {

	/**
	 * Turns a FaultSection into a CSV row
	 * 
	 * @param section
	 * @return a String representing the section as a CSV row
	 */
	private static String section2String(FaultSection section) {
		String locations = section.getFaultTrace().stream().map(
				location -> "" + location.getLatitude() + ";" + location.getLongitude() + ";" + location.getDepth())
				.collect(Collectors.joining(";"));

		return new StringBuilder().append(section.getParentSectionId()).append(",").append(section.getSectionId())
				.append(",").append(section.getAveDip()).append(",").append(section.getAveLowerDepth()).append(",")
				.append(section.getAveRake()).append(",").append(section.getAseismicSlipFactor()).append(",")
				.append(section.getCouplingCoeff()).append(",").append(section.getDipDirection()).append(",")
				.append(section.getDateOfLastEvent()).append(",").append(section.getOrigAveSlipRate()).append(",")
				.append(section.getOrigAveUpperDepth()).append(",").append(section.getOrigSlipRateStdDev()).append(",")
				.append(section.getSlipInLastEvent()).append(",").append(locations).toString();
	}

	/**
	 * prints subSections to out as CSV rows
	 * 
	 * @param out
	 * @param subSections
	 * @throws IOException
	 */
	private static void printCSVRows(PrintStream out, List<? extends FaultSection> subSections) throws IOException {
		for (FaultSection section : subSections) {
			out.println(section2String(section));
		}
	}

	/**
	 * Returns a Predicate<FaultSection> that returns true iff the FaultSection is
	 * required.
	 * 
	 * @param cmd parsed command line args
	 * @return a Predicate
	 */
	private static Predicate<FaultSection> makeSectionFilter(CommandLine cmd) {
		if (cmd.hasOption("ids")) {
			System.err.println("Only importing IDs " + String.join(", ", cmd.getOptionValues("ids")));

			Set<Integer> filter = Stream.of(cmd.getOptionValues("ids")).map(id -> Integer.parseInt(id))
					.collect(Collectors.toSet());
			return section -> filter.contains(section.getSectionId());
		} else {
			return section -> true;
		}
	}

	/**
	 * parses the command line args
	 * 
	 * @param args
	 * @return
	 * @throws ParseException
	 */
	private static CommandLine parseCommandLine(String[] args) throws ParseException {
		Option filterOption = new Option("i", "ids", true, "a list of IDs to filter");
		filterOption.setArgs(Option.UNLIMITED_VALUES);
		filterOption.setValueSeparator(',');
		Options options = new Options().addRequiredOption("f", "file", true, "an opensha-xml file")
				.addOption("l", "maxLength", true,
						"maxSubSectionLength, will be multiplied with OrigDownDipWidth before use")
				.addOption("s", "minSections", true, "minSections").addOption(filterOption)
				.addOption("o", "out", true, "output file").addOption("h", "help", false, "prints this help screen");
		CommandLine cmd = null;
		try {
			cmd = new DefaultParser().parse(options, args);
		} catch (ParseException x) {
			System.err.println(x.getMessage());
			cmd = null;
		}
		if (null == cmd || cmd.hasOption("help")) {
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("SectionXml2Csv", "Takes an opensha fault model file and returns a CSV of subsections.",
					options, "", true);
			System.exit(0);
		}
		return cmd;
	}

	public static void main(String[] args) throws Exception {

		double maxSubSectionLength = 0.5;
		int minSections = 2;
		PrintStream out = System.out;

		CommandLine cmd = parseCommandLine(args);

		if (cmd.hasOption("maxLength")) {
			maxSubSectionLength = Double.parseDouble(cmd.getOptionValue("maxLength"));
		}

		if (cmd.hasOption("minSections")) {
			minSections = Integer.parseInt(cmd.getOptionValue("minSections"));
		}

		if (cmd.hasOption("out")) {
			out = new PrintStream(new File(cmd.getOptionValue("out")));
			System.err.println("Writing output to " + cmd.getOptionValue("out"));
		} else {
			System.err.println("Writing output to stdout");
		}

		System.err.println("Importing file " + cmd.getOptionValue("file") + " with maxSubSectionLength "
				+ maxSubSectionLength + " minSections " + minSections);

		Predicate<FaultSection> filter = makeSectionFilter(cmd);

		File fsdFile = new File(cmd.getOptionValue("file"));
		FaultSectionList sections = FaultSectionList.fromList(FaultModels.loadStoredFaultSections(fsdFile));

		out.println(
				"parent id, id, ave dip, ave lower depth, ave rake, aseismic slip factor, coupling coeff, dip direction,"
						+ "DateOfLastEvent, OrigAveSlipRate, OrigAveUpperDepth, OrigSlipRateStdDev, SlipInLastEvent,"
						+ "locations as lat;lon;depth triples");

		FaultSectionList allSections = new FaultSectionList();
		for (FaultSection section : sections) {
			if (filter.test(section)) {
				double ddw = section.getOrigDownDipWidth();
				double maxSectLength = ddw * maxSubSectionLength;
				List<? extends FaultSection> subSections = section.getSubSectionsList(maxSectLength, allSections.getSafeId(),
						minSections);
				printCSVRows(out, subSections);
				allSections.addAll(subSections);
			}
		}
	}
}

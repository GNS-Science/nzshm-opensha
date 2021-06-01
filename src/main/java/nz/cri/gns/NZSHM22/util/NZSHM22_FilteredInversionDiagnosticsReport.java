package nz.cri.gns.NZSHM22.util;

import java.io.File;
import java.io.IOException;
import java.util.*;

import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.dom4j.DocumentException;
import org.opensha.commons.util.ClassUtils;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.*;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

import nz.cri.gns.NZSHM22.opensha.inversion.FilteredInversionFaultSystemSolution;
import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.inversion.InversionFaultSystemSolution;
import scratch.UCERF3.utils.FaultSystemIO;

public class NZSHM22_FilteredInversionDiagnosticsReport {
	
	private static FaultSystemRupSet inputRupSet, filtRupSet;
	private static FaultSystemSolution inputSol, filtSol;
	private static String inputName;
	private static File outputDir;
	private static String filterExpression;
	private static NZSHM22_FaultModels faultModel;
	
	public static void main(String[] args) throws IOException, DocumentException {
		System.setProperty("java.awt.headless", "true");
		parseArgs(args);
		RupSetDiagnosticsPageGen builder = getReportBuilder();

		builder.setSkipPlausibility(true);
		builder.setSkipBiasiWesnousky(true);
		builder.setSkipConnectivity(true);
		builder.setSkipSegmentation(true);
		builder.generatePage();
		
//		//try again
//		filterExpression = "Alpine Kaniere to Springs Junction";
//		getReportBuilder().generatePage();
		
		System.out.println("Done!");
	}

	public static RupSetDiagnosticsPageGen getReportBuilder() throws IOException, DocumentException {
		//		Preconditions.checkState(filtRupSet != null,
		//				"filtRupSet is null, please applyFilter() before this method");
		if(faultModel == null) {
			applySimpleFilter(filterExpression);
		}else
		{
			applyNamedFaultFilter(filterExpression, faultModel);
		}
		return new RupSetDiagnosticsPageGen(filtRupSet, filtSol, inputName, outputDir);
	}
	
	static void parseArgs(String [] args) throws IOException, DocumentException {
		Options options = RupSetDiagnosticsPageGen.createOptions();
		
		Option faultNameOption = new Option("fn", "fault-name", true,
				"fault name (or portion thereof) to filter");
		options.addOption(faultNameOption);
		options.addOption("fm", "faultModel", true, "Fault Model name (for Fault name filtering)");
		options.addOption("nf", "namedFault", true, "The name of a named fault to filter by. Requires a fault model.");

		CommandLineParser parser = new DefaultParser();

		CommandLine cmd = null;
		try {
			cmd = parser.parse(options, args);
			System.out.println("args " + args );
		} catch (ParseException e) {
			e.printStackTrace();
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp(ClassUtils.getClassNameWithoutPackage(RupSetDiagnosticsPageGen.class), options, true);
			System.exit(2);
		}

		if(!cmd.hasOption("fault-name") && !cmd.hasOption("namedFault")){
			throw new IllegalArgumentException("One of fault-name or namedFault is required.");
		}
		if (cmd.hasOption("fault-name") && cmd.hasOption("namedFault")){
			throw new IllegalArgumentException("Cannot have both fault-name and namedFault arguments");
		}
		if(cmd.hasOption("namedFault") && !cmd.hasOption("faultModel")){
			throw new IllegalArgumentException("faultModel is required for namedFault argument");
		}

		inputSol = FaultSystemIO.loadSol(new File(cmd.getOptionValue("rupture-set")));
		inputRupSet = inputSol.getRupSet();
		inputName = cmd.getOptionValue("name");
		outputDir = new File(cmd.getOptionValue("output-dir"));

		if(cmd.hasOption("faultModel")){
			faultModel = NZSHM22_FaultModels.valueOf(cmd.getOptionValue("faultModel"));
			filterExpression = cmd.getOptionValue("namedFault");
		}else
		{
			filterExpression = cmd.getOptionValue("fault-name");
		}
				
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir(),
				"Output dir doesn't exist and could not be created: %s", outputDir.getAbsolutePath());	
	}

	private static void applySimpleFilter(String faultName) { //

		//build the filter section list
		List<? extends FaultSection> subSects = inputRupSet.getFaultSectionDataList();
		List<FaultSection> selectedSubSects = new ArrayList<FaultSection>();
		
		for (FaultSection f : subSects) {
			//"Wellington Hutt Valley"
			//"Alpine Kaniere to Springs Junction"
			//"Cape Egmont Central"
			//"Wellington Pahiatua"
			//"Napier 1931"
			if (f.getParentSectionName().contains(faultName)) {
				System.out.println(f.getParentSectionName());
				selectedSubSects.add(f);
			}
		}
		
		FilteredInversionFaultSystemSolution builder = new FilteredInversionFaultSystemSolution();	
		filtSol = builder.createFilteredSolution((InversionFaultSystemSolution) inputSol, selectedSubSects);
		filtRupSet = builder.getFilteredRupSet();
	}

	private static void applyNamedFaultFilter(String faultName, NZSHM22_FaultModels faultModel) { //

		Set<Integer> sectionIds = new HashSet<>();

		faultName = faultName.toLowerCase();

		Map<String,List<Integer>> namedFaultsMap = faultModel.getNamedFaultsMapAlt();

		for(String key : namedFaultsMap.keySet()){
			if(key.toLowerCase().contains(faultName)){
				sectionIds.addAll(namedFaultsMap.get(key));
			}
		}

		//build the filter section list
		List<? extends FaultSection> subSects = inputRupSet.getFaultSectionDataList();
		List<FaultSection> selectedSubSects = new ArrayList<FaultSection>();

		for (FaultSection faultSection : subSects) {
			if (sectionIds.contains(faultSection.getParentSectionId())) {
				System.out.println(faultSection.getParentSectionName());
				selectedSubSects.add(faultSection);
			}
		}

		FilteredInversionFaultSystemSolution builder = new FilteredInversionFaultSystemSolution();
		filtSol = builder.createFilteredSolution((InversionFaultSystemSolution) inputSol, selectedSubSects);
		filtRupSet = builder.getFilteredRupSet();
	}

}


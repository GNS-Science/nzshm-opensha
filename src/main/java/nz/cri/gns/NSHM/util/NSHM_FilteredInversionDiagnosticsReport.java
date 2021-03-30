package nz.cri.gns.NSHM.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.dom4j.DocumentException;
import org.opensha.commons.util.ClassUtils;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.*;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupSetDiagnosticsPageGen.DiagnosticSummary;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupSetDiagnosticsPageGen.RupSetMetadata;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

import nz.cri.gns.NSHM.opensha.inversion.FilteredInversionFaultSystemSolution;
import nz.cri.gns.NSHM.opensha.ruptures.FilteredFaultSystemRuptureSet;
import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.inversion.InversionFaultSystemSolution;
import scratch.UCERF3.utils.FaultSystemIO;

public class NSHM_FilteredInversionDiagnosticsReport extends RupSetDiagnosticsPageGen {

	public static void main(String[] args) throws IOException, DocumentException {
		System.setProperty("java.awt.headless", "true");
		create(args).generatePage();
		System.out.println("Done!");
	}

	public static NSHM_FilteredInversionDiagnosticsReport create(String [] args) throws IOException, DocumentException {
		Options options = createOptions();
		
		Option faultNameOption = new Option("fn", "fault-name", true,
				"fault name (or portion thereof) to filter");
		faultNameOption.setRequired(true);
		options.addOption(faultNameOption);
		
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
		return new NSHM_FilteredInversionDiagnosticsReport(cmd);
	}

	public NSHM_FilteredInversionDiagnosticsReport(CommandLine cmd) throws IOException, DocumentException {
		super(cmd);
		//build the filter section list
		List<? extends FaultSection> subSects = inputRupSet.getFaultSectionDataList();
		List<FaultSection> selectedSubSects = new ArrayList<FaultSection>();
		
		String faultName = cmd.getOptionValue("fault-name");
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
		
		//execute the filter, extracting the ruptures etc
		FilteredInversionFaultSystemSolution builder = new FilteredInversionFaultSystemSolution();	
		this.inputSol = builder.createFilteredSolution((InversionFaultSystemSolution) this.inputSol, selectedSubSects);
		this.inputRupSet = builder.getFilteredRupSet();
		this.inputRups = builder.getFilteredRups();
	};

}


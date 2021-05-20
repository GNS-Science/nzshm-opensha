package nz.cri.gns.NZSHM22.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
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
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.*;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

import nz.cri.gns.NZSHM22.opensha.inversion.FilteredInversionFaultSystemSolution;
import nz.cri.gns.NZSHM22.opensha.ruptures.FilteredFaultSystemRuptureSet;
import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.inversion.InversionFaultSystemSolution;
import scratch.UCERF3.utils.FaultSystemIO;

class FileMeta {
	protected String filename;
	protected String depth;
	protected String connection_strategy;
	protected String rupture_growing_strategy;
	
	public FileMeta (String filename, String depth, String connection_strategy, String rupture_growing_strategy) {
		this.filename = filename;
		this.depth = depth;
		this.connection_strategy = connection_strategy;
		this.rupture_growing_strategy = rupture_growing_strategy;
	}
	
	public String folderName() {
		String retval = depth + "_depth_" + connection_strategy + "_" +  rupture_growing_strategy;
		return retval;
	}
}

class FaultMeta {
	protected String faultname;
	protected String shortname;
	
	public FaultMeta (String faultname, String shortname) {
		this.faultname = faultname;
		this.shortname = shortname;
	}
	
	public String folderName() {
		String retval = shortname + "_filtered";
		return retval;
	}
}

public class NZSHM22_RupSetDiagnosticsReport {
	
	private static FaultSystemRupSet inputRupSet, filtRupSet;
	private static FaultSystemSolution inputSol, filtSol;
	private static String inputName;
	private static File outputDir;
	private static File inputDir;
	private static String filterExpression;
	private static RupSetDiagnosticsPageGen builder;
	
	public static void main(String[] args) throws IOException, DocumentException {
		System.setProperty("java.awt.headless", "true");

		ArrayList<FileMeta> metadataList = new ArrayList<FileMeta>();
		
		inputDir = new File("../DATA/2022-05-19-02");
		File outputRoot = new File("../DATA/2022-05-19-02");
				
		//Set up metadata
//		metadataList.add(new FileMeta(
//				"nz_demo5_crustal_10km_direct_cmlRake360_jumpP0.001_slipP0.05incr_cff0.75IntsPos_comb2Paths_cffFavP0.01_cffFavRatioN2P0.5_sectFractGrow0.05.zip",
//				"CFM", "DistCutoffClosestSect", "Unilateral"));
//		metadataList.add( new FileMeta(
//				"ruptset_ddw0.5_jump5.0_SANS_TVZ2_580.0_2_UCERF3_thin0.0.zip", 
//				"CFM", "UCERF3-Az580", "NZSHM22"));
//		metadataList.add(new FileMeta(
//				"nz_demo5_crustal_adapt5_10km_sMax1_direct_cmlRake360_jumpP0.001_slipP0.05incr_cff0.75IntsPos_comb2Paths_cffFavP0.01_cffFavRatioN2P0.5_sectFractGrow0.05.zip",
//				"CFM", "AdaptiveDistCutoffClosestSect", "Unilateral"));
//		metadataList.add(new FileMeta(
//				"nz_demo5_crustal_adapt5_10km_sMax1_direct_cmlRake360_jumpP0.001_slipP0.05incr_cff0.75IntsPos_comb2Paths_cffFavP0.01_cffFavRatioN2P0.5_bilateral_sectFractGrow0.05.zip",
//				"CFM", "AdaptiveDistCutoffClosestSect", "Bilateral"));
//		metadataList.add(new FileMeta(
//				"nz_demo5_crustal_DEPTH30__10km_direct_cmlRake360_jumpP0.001_slipP0.05incr_cff0.75IntsPos_comb2Paths_cffFavP0.01_cffFavRatioN2P0.5_sectFractGrow0.05.zip",
//				"30km", "DistCutoffClosestSect", "Unilateral"));
//		metadataList.add(new FileMeta(
//				"nz_demo5_crustal_DEPTH30__adapt5_10km_sMax1_direct_cmlRake360_jumpP0.001_slipP0.05incr_cff0.75IntsPos_comb2Paths_cffFavP0.01_cffFavRatioN2P0.5_sectFractGrow0.05.zip",
//				"30km", "AdaptiveDistCutoffClosestSect", "Unilateral"));
//		metadataList.add(new FileMeta(
//				"nz_demo5_crustal_DEPTH30__adapt5_10km_sMax1_direct_cmlRake360_jumpP0.001_slipP0.05incr_cff0.75IntsPos_comb2Paths_cffFavP0.01_cffFavRatioN2P0.5_bilateral_sectFractGrow0.05.zip",
//				"30km", "AdaptiveDistCutoffClosestSect", "Bilateral"));
	
		metadataList.add(new FileMeta(
				"RupSet_Az_FM(CFM_0_9_SANSTVZ_D90)_mxSbScLn(0.5)_mxAzCh(60.0)_mxCmAzCh(560.0)_mxJpDs(5.0)_mxTtAzCh(60.0)_thFc(0.0).zip",
				"CFM0.9", "UCERF3", "dflt"));
		metadataList.add(new FileMeta(
				"RupSet_Az_FM(CFM_0_3_SANSTVZ)_mxSbScLn(0.5)_mxAzCh(60.0)_mxCmAzCh(560.0)_mxJpDs(5.0)_mxTtAzCh(60.0)_thFc(0.0).zip",
				"CFM0.3", "UCERF3", "dflt"));		
		metadataList.add(new FileMeta(
				"RupSet_Az_FM(CFM_0_9_SANSTVZ_D90)_mxSbScLn(0.5)_mxAzCh(60.0)_mxCmAzCh(560.0)_mxJpDs(5.0)_mxTtAzCh(60.0)_thFc(0.1).zip",
				"CFM0.9", "UCERF3", "thin 0.1"));
		metadataList.add(new FileMeta(
				"RupSet_Az_FM(CFM_0_3_SANSTVZ)_mxSbScLn(0.5)_mxAzCh(60.0)_mxCmAzCh(560.0)_mxJpDs(5.0)_mxTtAzCh(60.0)_thFc(0.1).zip",
				"CFM0.3", "UCERF3", "thin 0.1"));		
		
		ArrayList<FaultMeta> faultList = new ArrayList<FaultMeta>();		
//		faultList.add(new FaultMeta("Wellington Hutt Valley", "WHV"));
//		faultList.add(new FaultMeta("Alpine Kaniere to Springs Junction", "AKSJ"));
//		faultList.add(new FaultMeta("Kekerengu", "KKR"));
		
		for (FileMeta metadata : metadataList) {

			System.out.println("Building report for: " +  metadata.folderName());
		
			outputDir = new File(outputRoot, metadata.folderName());
			Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
			
			inputRupSet = FaultSystemIO.loadRupSet(new File(inputDir, metadata.filename));
			inputSol = null;
			inputName = "May 19th, 2021 #2 TMG_CRU_2017 " + metadata.folderName();
		
			builder = new RupSetDiagnosticsPageGen(inputRupSet, 
					inputSol, inputName, outputDir);
			builder.setSkipPlausibility(true);
			builder.setSkipBiasiWesnousky(true);
			builder.setSkipConnectivity(true);
			builder.setSkipSegmentation(true);
			builder.generatePage();

			for (FaultMeta faultmeta : faultList) {
				System.out.println("Building report for: " +  metadata.folderName() + " " + faultmeta.faultname);
				File outputSubDir = new File(outputDir.toString() + "_" +  faultmeta.folderName());
				Preconditions.checkState(outputSubDir.exists() || outputSubDir.mkdir());
				applyFilter(faultmeta.faultname);

				builder = new RupSetDiagnosticsPageGen(
						filtRupSet, 
						inputSol, inputName + " filter: " + faultmeta.faultname, 
						outputSubDir);
				
				builder.setSkipPlausibility(true);
				builder.setSkipBiasiWesnousky(true);
				builder.setSkipConnectivity(true);
				builder.setSkipSegmentation(true);
				builder.generatePage();							
			}
			
		}
	
		System.out.println("Done!");
	}
	
//	static void parseArgs(String [] args) throws IOException, DocumentException {
//		Options options = RupSetDiagnosticsPageGen.createOptions();
//		
//		Option faultNameOption = new Option("fn", "fault-name", true,
//				"fault name (or portion thereof) to filter");
//		faultNameOption.setRequired(true);
//		options.addOption(faultNameOption);
//		
//		CommandLineParser parser = new DefaultParser();
//
//		CommandLine cmd = null;
//		try {
//			cmd = parser.parse(options, args);
//			System.out.println("args " + args );
//		} catch (ParseException e) {
//			e.printStackTrace();
//			HelpFormatter formatter = new HelpFormatter();
//			formatter.printHelp(ClassUtils.getClassNameWithoutPackage(RupSetDiagnosticsPageGen.class), options, true);
//			System.exit(2);
//		}
//			
//		inputSol = FaultSystemIO.loadSol(new File(cmd.getOptionValue("rupture-set")));
//		inputRupSet = inputSol.getRupSet();
//		inputName = cmd.getOptionValue("name");
//		outputDir = new File(cmd.getOptionValue("output-dir"));
//		filterExpression = cmd.getOptionValue("fault-name");
//				
//		Preconditions.checkState(outputDir.exists() || outputDir.mkdir(),
//				"Output dir doesn't exist and could not be created: %s", outputDir.getAbsolutePath());	
//	}

	private static void applyFilter(String faultName) { // 

		//build the filter section list
		List<? extends FaultSection> subSects = inputRupSet.getFaultSectionDataList();
		List<FaultSection> selectedSubSects = new ArrayList<FaultSection>();
		
		for (FaultSection f : subSects) {

			if (f.getParentSectionName().contains(faultName)) {
				System.out.println(f.getParentSectionName());
				selectedSubSects.add(f);
			}
		}
		
		// Build filtered Rupture set
		FilteredFaultSystemRuptureSet builder = new FilteredFaultSystemRuptureSet();
		filtRupSet = builder.create(inputRupSet, selectedSubSects);
		
		//FilteredInversionFaultSystemSolution builder = new FilteredInversionFaultSystemSolution();	
		//filtSol = builder.createFilteredSolution((InversionFaultSystemSolution) inputSol, selectedSubSects);
		//filtRupSet = builder.getFilteredRupSet();
	};

}


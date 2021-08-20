package nz.cri.gns.NZSHM22.opensha.scripts;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;

import javax.swing.text.DateFormatter;

import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.RuptureSets;
import org.opensha.sha.earthquake.faultSysSolution.RuptureSets.CoulombRupSetConfig;
import org.opensha.sha.earthquake.faultSysSolution.RuptureSets.RupSetConfig;
import org.opensha.sha.earthquake.faultSysSolution.modules.FaultGridAssociations;
import org.opensha.sha.earthquake.faultSysSolution.modules.InitialSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.SubSeismoOnFaultMFDs;
import org.opensha.sha.earthquake.faultSysSolution.modules.WaterLevelRates;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportPageGen;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportPageGen.PlotLevel;
import org.opensha.sha.earthquake.faultSysSolution.reports.RupSetMetadata;

import com.google.common.base.Preconditions;

import nz.cri.gns.NZSHM22.opensha.inversion.NZSHM22_InversionFaultSystemRuptSet;
import nz.cri.gns.NZSHM22.opensha.inversion.NZSHM22_SubductionInversionConfiguration;
import nz.cri.gns.NZSHM22.opensha.inversion.NZSHM22_SubductionInversionInputGenerator;
import nz.cri.gns.NZSHM22.opensha.inversion.NZSHM22_SubductionInversionTargetMFDs;
import scratch.UCERF3.U3FaultSystemRupSet;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.InversionModels;
import scratch.UCERF3.enumTreeBranches.MaxMagOffFault;
import scratch.UCERF3.enumTreeBranches.MomentRateFixes;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;
import scratch.UCERF3.enumTreeBranches.SpatialSeisPDF;
import scratch.UCERF3.griddedSeismicity.UCERF3_GridSourceGenerator;
import scratch.UCERF3.inversion.CommandLineInversionRunner;
import scratch.UCERF3.inversion.InversionTargetMFDs;
import scratch.UCERF3.inversion.UCERF3InversionConfiguration;
import scratch.UCERF3.inversion.UCERF3InversionInputGenerator;
import scratch.UCERF3.inversion.UCERF3InversionConfiguration.SlipRateConstraintWeightingType;
import scratch.UCERF3.logicTree.U3LogicTreeBranch;
import scratch.UCERF3.simulatedAnnealing.SerialSimulatedAnnealing;
import scratch.UCERF3.simulatedAnnealing.SimulatedAnnealing;
import scratch.UCERF3.simulatedAnnealing.ThreadedSimulatedAnnealing;
import scratch.UCERF3.simulatedAnnealing.completion.CompletionCriteria;
import scratch.UCERF3.simulatedAnnealing.completion.ProgressTrackingCompletionCriteria;
import scratch.UCERF3.simulatedAnnealing.completion.TimeCompletionCriteria;
import scratch.UCERF3.simulatedAnnealing.params.GenerationFunctionType;
import scratch.UCERF3.simulatedAnnealing.params.NonnegativityConstraintType;
import scratch.UCERF3.utils.U3FaultSystemIO;
import scratch.UCERF3.utils.aveSlip.AveSlipConstraint;
import scratch.UCERF3.utils.paleoRateConstraints.PaleoProbabilityModel;
import scratch.UCERF3.utils.paleoRateConstraints.PaleoRateConstraint;

class FullPipelineDemo {

	public static void main(String[] args) throws Exception {
		System.out.println("Yawn...");
//		long minute = 1000l*60l;
//		long hour = minute*60l;
//		Thread.sleep(5l*hour + 30l*minute);
//		System.out.println("Im awake! "+new Date());
		
		File markdownDirDir = new File("./TEST/kevin_markdown_inversions");
		Preconditions.checkState(markdownDirDir.exists() || markdownDirDir.mkdir());
		int threads = 4;
		
		U3LogicTreeBranch branch = U3LogicTreeBranch.DEFAULT;
		branch.setValue(SlipAlongRuptureModels.UNIFORM);
		
		FaultModels fm = branch.getValue(FaultModels.class);
		ScalingRelationships scale = branch.getValue(ScalingRelationships.class);
		
//		String dirName = new SimpleDateFormat("yyyy_MM_dd").format(new Date());
		String dirName = "2021_08_20";
		
		String newName = "Subduction test, like SW52ZXJzaW9uU29sdXRpb246NjQ3NC41NGtBSFg= ";
//		dirName += "-SBD_0_2A_HKR_LR_30_";
		SerialSimulatedAnnealing.exp_orders_of_mag = 10;
		String minScaleStr = new DecimalFormat("0E0").format(
				Math.pow(10, SerialSimulatedAnnealing.max_exp-SerialSimulatedAnnealing.exp_orders_of_mag)).toLowerCase();
		
		
		String scaleStr = "perturb_exp_scale_1e-2_to_"+minScaleStr;
//		dirName += "-"+scaleStr+"-avg_anneal_20m-noWL-zeroRates-1hr";
		
		dirName += "perturb(UNIFORM)_nonNeg(LIMIT)_noAveraging_water(NONE)_expOrd(10)_U3PERTURBHACK_time(10m)";
		
		System.out.println(dirName);
		CoulombRupSetConfig rsConfig = new RuptureSets.CoulombRupSetConfig(fm, scale);
//		rsConfig.setAdaptiveSectFract(0.3f);
//		String newName = "U3 Reproduction";
//		dirName += "-u3_ref-quick-test";
//		RupSetConfig rsConfig = new RuptureSets.U3RupSetConfig(fm , scale);
//		FaultSystemSolution compSol = FaultSystemSolution.load(new File("/home/kevin/OpenSHA/UCERF4/rup_sets/fm3_1_ucerf3.zip"));

		FaultSystemSolution compSol;
//		compsol = FaultSystemSolution.load(new File("/home/kevin/OpenSHA/UCERF3/rup_sets/modular/"
//				+ "FM3_1_ZENGBB_Shaw09Mod_DsrTap_CharConst_M5Rate7.9_MMaxOff7.6_NoFix_SpatSeisU3.zip"));
		String compName =  "UCERF3";
		
//		double wlFract = 1e-3;
		double wlFract = 0d;
		boolean wlAsStarting = false;
//		double wlFract = 1e-2;
//		boolean wlAsStarting = true;
		
//		GenerationFunctionType perturb = GenerationFunctionType.EXPONENTIAL_SCALE;
		GenerationFunctionType perturb = GenerationFunctionType.UNIFORM_NO_TEMP_DEPENDENCE;
		NonnegativityConstraintType nonNeg = NonnegativityConstraintType.LIMIT_ZERO_RATES; // default
//		NonnegativityConstraintType nonNeg = NonnegativityConstraintType.TRY_ZERO_RATES_OFTEN;

//		CompletionCriteria completion = TimeCompletionCriteria.getInHours(9);
		CompletionCriteria completion = TimeCompletionCriteria.getInMinutes(10);
//		CompletionCriteria avgSubCompletion = TimeCompletionCriteria.getInMinutes(1); //getInSeconds(1200); 
//		CompletionCriteria avgSubCompletion = TimeCompletionCriteria.getInSeconds(15);
		CompletionCriteria avgSubCompletion = null;
//		int subCompletionSeconds = 1; //CBC added
		int subCompletionSeconds = 30; //CBC added

		int threadsPerAvg = 4;
		
		int numRuns = 1;
		
		boolean rebuildRupSet = false;
		boolean rerunInversion = true;
		boolean doRupSetReport = true;
		
		FaultSystemRupSet rupSet = null;
		
		for (int run=0; run<numRuns; run++) {
			String myDirName = dirName;
			if (numRuns > 1)
				myDirName += "-run"+run;
			File outputDir = new File(markdownDirDir, myDirName);
			Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
			
//			File rupSetFile = new File(outputDir, "rupture_set.zip");

			File outputRoot = new File("./TEST");
			File rupSetFile = new File(outputRoot,
					"RupSet_Sub_FM(SBD_0_2A_HKR_LR_30)_mnSbS(2)_mnSSPP(2)_mxSSL(0.5)_ddAsRa(2.0,5.0,5)_ddMnFl(0.1)_ddPsCo(0.0)_ddSzCo(0.0)_thFc(0.0).zip");		
						
			if (rupSet == null) {
				if (!rebuildRupSet && rupSetFile.exists()) {
					// CBC: orginal rupSet = FaultSystemRupSet.load(rupSetFile);
					// CBC: from Oakleys 
					U3FaultSystemRupSet rupSetA = U3FaultSystemIO.loadRupSet(rupSetFile);
					rupSet = new NZSHM22_InversionFaultSystemRuptSet(rupSetA, branch);
					// CBC: we can remove this as we don't need fault grid with subduction 
					rupSet.removeModuleInstances(FaultGridAssociations.class);
					
				} else {
					rupSet = rsConfig.build(threads);
					// configure as UCERF3
					rupSet = FaultSystemRupSet.buildFromExisting(rupSet).forU3Branch(branch).build();
				}
			}
			// write it out
			if (rebuildRupSet || !rupSetFile.exists())
				rupSet.write(rupSetFile);
						
			File solFile = new File(outputDir, "solution.zip");
			
			FaultSystemSolution sol;
			if (rerunInversion || !solFile.exists()) {
//				// now build inputs
//				InversionTargetMFDs targetMFDs = rupSet.requireModule(InversionTargetMFDs.class);
//				UCERF3InversionConfiguration config = UCERF3InversionConfiguration.forModel(
//						branch.getValue(InversionModels.class), rupSet, fm, targetMFDs);
//				config.setMinimumRuptureRateFraction(wlFract);
//				if (wlAsStarting && wlFract > 0) {
//					double[] initial = Arrays.copyOf(config.getMinimumRuptureRateBasis(), rupSet.getNumRuptures());
//					for (int i=0; i<initial.length; i++)
//						initial[i] *= wlFract;
//					config.setInitialRupModel(initial);
//					config.setMinimumRuptureRateFraction(0d);
//				}
//				
//				// get the paleo rate constraints
//				List<PaleoRateConstraint> paleoRateConstraints = CommandLineInversionRunner.getPaleoConstraints(
//						fm, rupSet);
//
//				// get the improbability constraints
//				double[] improbabilityConstraint = null; // null for now
//
//				// paleo probability model
//				PaleoProbabilityModel paleoProbabilityModel = UCERF3InversionInputGenerator.loadDefaultPaleoProbabilityModel();
//
//				List<AveSlipConstraint> aveSlipConstraints = AveSlipConstraint.load(rupSet.getFaultSectionDataList());
				
//				UCERF3InversionInputGenerator inputGen = new UCERF3InversionInputGenerator(
//						rupSet, config, paleoRateConstraints, aveSlipConstraints, improbabilityConstraint, paleoProbabilityModel);


						
				// CBC: 
				// BEGIN from NZSHM22_SubductionInversionRunner.configure
				// U3LogicTreeBranch logicTreeBranch = rupSet.getLogicTreeBranch();
				InversionModels inversionModel = branch.getValue(InversionModels.class);

				NZSHM22_SubductionInversionConfiguration inversionConfiguration = NZSHM22_SubductionInversionConfiguration
						.forModel(inversionModel, (NZSHM22_InversionFaultSystemRuptSet) rupSet, 1000, 10000, 29, 1.05, 7.85);


				inversionConfiguration.setSlipRateWeightingType(SlipRateConstraintWeightingType.BOTH);
				inversionConfiguration.setSlipRateConstraintWt_normalized(1000);
				inversionConfiguration.setSlipRateConstraintWt_unnormalized(10000);
				
				NZSHM22_SubductionInversionInputGenerator inputGen = new NZSHM22_SubductionInversionInputGenerator(
						(NZSHM22_InversionFaultSystemRuptSet) rupSet, inversionConfiguration);
						
				//InversionTargetMFDs targetMFDs = inversionConfiguration.getInversionTargetMfds();
				InversionTargetMFDs targetMFDs = rupSet.requireModule(NZSHM22_SubductionInversionTargetMFDs.class);
				
				// CBC
				// END configure
				
				
				System.out.println("Generating inputs");
				inputGen.generateInputs();
				inputGen.columnCompress();
				
				ProgressTrackingCompletionCriteria progress = new ProgressTrackingCompletionCriteria(completion);
				TimeCompletionCriteria subCompletion = TimeCompletionCriteria.getInSeconds(subCompletionSeconds);
				
				ThreadedSimulatedAnnealing tsa;
				if (avgSubCompletion != null) {
					Preconditions.checkState(threadsPerAvg < threads);
					
					int threadsLeft = threads;
					
					// arrange lower-level (actual worker) SAs
					List<SimulatedAnnealing> tsas = new ArrayList<>();
					while (threadsLeft > 0) {
						int myThreads = Integer.min(threadsLeft, threadsPerAvg);
						if (myThreads > 1)
							tsas.add(new ThreadedSimulatedAnnealing(inputGen.getA(), inputGen.getD(),
									inputGen.getInitialSolution(), 0d, inputGen.getA_ineq(), inputGen.getD_ineq(), myThreads, subCompletion));
						else
							tsas.add(new SerialSimulatedAnnealing(inputGen.getA(), inputGen.getD(),
									inputGen.getInitialSolution(), 0d, inputGen.getA_ineq(), inputGen.getD_ineq()));
						threadsLeft -= myThreads;
					}
					tsa = new ThreadedSimulatedAnnealing(tsas, avgSubCompletion);
					tsa.setAverage(true);
				} else {
					tsa = new ThreadedSimulatedAnnealing(inputGen.getA(), inputGen.getD(),
							inputGen.getInitialSolution(), 0d, inputGen.getA_ineq(), inputGen.getD_ineq(), threads, subCompletion);
				}
				
				progress.setConstraintRanges(inputGen.getConstraintRowRanges());
				tsa.setConstraintRanges(inputGen.getConstraintRowRanges());
				tsa.setPerturbationFunc(perturb);
				tsa.setNonnegativeityConstraintAlgorithm(nonNeg);
				tsa.iterate(progress);
				
				double[] rawSol = tsa.getBestSolution();
				double[] rates = inputGen.adjustSolutionForWaterLevel(rawSol);
				
				sol = new FaultSystemSolution(rupSet, rates);

				// CBC: no gridded wanted for subduction 		
//				// add sub-seismo MFDs
//				sol.addModule(targetMFDs.getOnFaultSubSeisMFDs());
//				// add grid source provider
//				sol.setGridSourceProvider(new UCERF3_GridSourceGenerator(sol, branch.getValue(SpatialSeisPDF.class),
//						branch.getValue(MomentRateFixes.class), targetMFDs,
//						sol.requireModule(SubSeismoOnFaultMFDs.class),
//						branch.getValue(MaxMagOffFault.class).getMaxMagOffFault(),
//						rupSet.requireModule(FaultGridAssociations.class)));
				// CBC: 
				
				// add inversion progress
				sol.addModule(progress.getProgress());
				// add water level rates
				if (inputGen.getWaterLevelRates() != null)
					sol.addModule(new WaterLevelRates(inputGen.getWaterLevelRates()));
				if (inputGen.hasInitialSolution())
					sol.addModule(new InitialSolution(inputGen.getInitialSolution()));
				
				// write solution
				sol.write(solFile);
			} else {
				sol = FaultSystemSolution.load(solFile);
			}

			Thread reportThread = null;
			if (doRupSetReport) {
				// write a full rupture set report in the background
				ReportMetadata rupMeta = new ReportMetadata(new RupSetMetadata(newName, rupSet));//, new RupSetMetadata("UCERF3", compSol));
				ReportPageGen rupSetReport = new ReportPageGen(rupMeta, new File(outputDir, "rup_set_report"),
						ReportPageGen.getDefaultRupSetPlots(PlotLevel.LIGHT));
				reportThread = new Thread(new Runnable() {
					
					@Override
					public void run() {
						try {
							rupSetReport.generatePage();
							System.out.println("Done with rupture set report");
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				});
				reportThread.start();
			}		
			
			if (reportThread != null) {
				try {
					reportThread.join();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			
			// write solution report
			String myName = newName;
			if (run > 0)
				myName += " Run #"+(run+1);
			ReportMetadata solMeta = new ReportMetadata(new RupSetMetadata(myName, sol)); //, new RupSetMetadata(compName, compSol));
			ReportPageGen solReport = new ReportPageGen(solMeta, new File(outputDir, "sol_report"),
					ReportPageGen.getDefaultSolutionPlots(PlotLevel.LIGHT));
			solReport.generatePage();
			
			if (run == 0) {
				compSol = sol;
				compName = "Run #1";
			}
		}
		System.out.println("DONE");
		System.exit(0);
	}
	
	
}
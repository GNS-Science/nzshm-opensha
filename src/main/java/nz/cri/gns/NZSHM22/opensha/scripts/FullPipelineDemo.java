package nz.cri.gns.NZSHM22.opensha.scripts;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_LogicTreeBranch;
import nz.cri.gns.NZSHM22.opensha.inversion.*;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.RuptureSets;
import org.opensha.sha.earthquake.faultSysSolution.RuptureSets.CoulombRupSetConfig;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.SerialSimulatedAnnealing;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.SimulatedAnnealing;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.ThreadedSimulatedAnnealing;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.CompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.ProgressTrackingCompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.TimeCompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.params.GenerationFunctionType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.params.NonnegativityConstraintType;
import org.opensha.sha.earthquake.faultSysSolution.modules.InitialSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.WaterLevelRates;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportPageGen;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportPageGen.PlotLevel;
import org.opensha.sha.earthquake.faultSysSolution.reports.RupSetMetadata;

import com.google.common.base.Preconditions;

import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.InversionModels;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionTargetMFDs;

class FullPipelineDemo {

	public static void main(String[] args) throws Exception {
		System.out.println("Yawn...");
		
		File markdownDirDir = new File("./TEST/kevin_markdown_inversions");
		Preconditions.checkState(markdownDirDir.exists() || markdownDirDir.mkdir());

		NZSHM22_LogicTreeBranch branch = NZSHM22_LogicTreeBranch.subductionInversion();
		
		FaultModels fm = branch.getValue(FaultModels.class);
		ScalingRelationships scale = branch.getValue(ScalingRelationships.class);
		
		String dirName = "2021_08_24_";	
		String newName = "Subduction test, like SW52ZXJzaW9uU29sdXRpb246NjQ3NC41NGtBSFg= ";

//		SerialSimulatedAnnealing.exp_orders_of_mag = 10;
//		String minScaleStr = new DecimalFormat("0E0").format(
//				Math.pow(10, SerialSimulatedAnnealing.max_exp-SerialSimulatedAnnealing.exp_orders_of_mag)).toLowerCase();
//
//		String scaleStr = "perturb_exp_scale_1e-2_to_"+minScaleStr;
//		dirName += "perturb(EXP_SCA)_nonNeg(TRY_OFTEN)_Averaging(16,1)_water(1e-2)_expOrd(10)_U3PERTURBHACK(NA)_time(15m,15s,15s)";
		
		System.out.println(dirName);
		CoulombRupSetConfig rsConfig = new RuptureSets.CoulombRupSetConfig(fm, scale);

		FaultSystemSolution compSol;
		String compName =  "UCERF3";
		
//		double wlFract = 1e-3;
//		double wlFract = 0d;
//		boolean wlAsStarting = false;
		double wlFract = 1e-2;
		boolean wlAsStarting = true;
		
		GenerationFunctionType perturb = GenerationFunctionType.EXPONENTIAL_SCALE;
//		GenerationFunctionType perturb = GenerationFunctionType.UNIFORM_NO_TEMP_DEPENDENCE;
//		NonnegativityConstraintType nonNeg = NonnegativityConstraintType.LIMIT_ZERO_RATES; // default
//		NonnegativityConstraintType nonNeg = NonnegativityConstraintType.PREVENT_ZERO_RATES;
		NonnegativityConstraintType nonNeg = NonnegativityConstraintType.TRY_ZERO_RATES_OFTEN;
		

//		CompletionCriteria completion = TimeCompletionCriteria.getInHours(6);
		CompletionCriteria completion = TimeCompletionCriteria.getInMinutes(1);
		CompletionCriteria avgSubCompletion = TimeCompletionCriteria.getInSeconds(15); //getInSeconds(1200); 
//		CompletionCriteria avgSubCompletion = null;
//		TimeCompletionCriteria subCompletion = new TimeCompletionCriteria(250);		
		CompletionCriteria subCompletion = TimeCompletionCriteria.getInSeconds(15); //.getInMinutes(15);
		
		int threads = 16;
		int threadsPerAvg = 1;	

		int numRuns = 1;
		
		boolean rebuildRupSet = false;
		boolean rerunInversion = true;
		boolean doRupSetReport = false;
		
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
					rupSet = NZSHM22_InversionFaultSystemRuptSet.loadRuptureSet(rupSetFile, branch);
				} else {
					rupSet = rsConfig.build(threads);
					// configure as UCERF3
					rupSet = FaultSystemRupSet.buildFromExisting(rupSet).forU3Branch(branch.getU3Branch()).build();
				}
			}
			// write it out
			if (rebuildRupSet || !rupSetFile.exists())
				rupSet.write(rupSetFile);
						
			File solFile = new File(outputDir, "solution.zip");
			
			FaultSystemSolution sol;
			if (rerunInversion || !solFile.exists()) {

				// CBC: 
				// BEGIN from NZSHM22_SubductionInversionRunner.configure
				// U3LogicTreeBranch logicTreeBranch = rupSet.getLogicTreeBranch();
				InversionModels inversionModel = branch.getValue(InversionModels.class);

				NZSHM22_SubductionInversionConfiguration inversionConfiguration = NZSHM22_SubductionInversionConfiguration
						.forModel(inversionModel, (NZSHM22_InversionFaultSystemRuptSet) rupSet, 1000, 10000, 29, 1.05, 9.15, 1000, 0.0);

				// CBC: Water level test
				if (wlAsStarting && wlFract > 0) {
					inversionConfiguration.setMinimumRuptureRateFraction(wlFract);
					double[] initial = Arrays.copyOf(inversionConfiguration.getMinimumRuptureRateBasis(), rupSet.getNumRuptures());
					for (int i=0; i<initial.length; i++)
						initial[i] *= wlFract;
					inversionConfiguration.setInitialRupModel(initial);
					inversionConfiguration.setMinimumRuptureRateFraction(0d);				
				}

				inversionConfiguration.setSlipRateWeightingType(AbstractInversionConfiguration.NZSlipRateConstraintWeightingType.BOTH);
				inversionConfiguration.setSlipRateConstraintWt_normalized(1000);
				inversionConfiguration.setSlipRateConstraintWt_unnormalized(10000);
				
				NZSHM22_SubductionInversionInputGenerator inputGen = new NZSHM22_SubductionInversionInputGenerator(
						(NZSHM22_InversionFaultSystemRuptSet) rupSet, inversionConfiguration);
						
				//InversionTargetMFDs targetMFDs = inversionConfiguration.getInversionTargetMfds();
				InversionTargetMFDs targetMFDs = rupSet.requireModule(NZSHM22_SubductionInversionTargetMFDs.class);
				
				// CBC
				// END configure
				// XXX oakley
				
				System.out.println("Generating inputs");
				inputGen.generateInputs();
				inputGen.columnCompress();
				
				ProgressTrackingCompletionCriteria progress = new ProgressTrackingCompletionCriteria(completion);
			
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
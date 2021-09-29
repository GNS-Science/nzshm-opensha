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

import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_LogicTreeBranch;
import nz.cri.gns.NZSHM22.opensha.inversion.*;
import org.opensha.commons.logicTree.LogicTreeBranch;
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
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;

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

class FullPipelineCrustal {

	public static void main(String[] args) throws Exception {
		System.out.println("Yawn...");
		
		File markdownDirDir = new File("./TEST/crustal_inversions");
		Preconditions.checkState(markdownDirDir.exists() || markdownDirDir.mkdir());

		U3LogicTreeBranch branch = NZSHM22_LogicTreeBranch.crustal();
		
		FaultModels fm = branch.getValue(FaultModels.class);
		ScalingRelationships scale = branch.getValue(ScalingRelationships.class);
		
		String dirName = "2021_08_23";
		
		String newName = "Crustal test, like SW52ZXJzaW9uU29sdXRpb246NjEwMC41UlhaTm8= ";
		SerialSimulatedAnnealing.exp_orders_of_mag = 10;
		String minScaleStr = new DecimalFormat("0E0").format(
				Math.pow(10, SerialSimulatedAnnealing.max_exp-SerialSimulatedAnnealing.exp_orders_of_mag)).toLowerCase();
		
		String scaleStr = "perturb_exp_scale_1e-2_to_"+minScaleStr;
		dirName += "perturb(EXP_SCA)_nonNeg(RY_OFTEN)_Averaging(16,1)_water(1e-2)_expOrd(10)_U3PERTURBHACK(NA)_time(15m,15s,15s)";
		
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
		
		int threads = 4;
		int threadsPerAvg = 1;	
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
			
			File outputRoot = new File("./TEST");
			File rupSetFile = new File(outputRoot,
					"RupSet_Cl_FM(CFM_0_9_SANSTVZ_D90)_noInP(T)_slRtP(0.05)_slInL(F)_cfFr(0.75)_cfRN(2)_cfRTh(0.5)_cfRP(0.01)_fvJm(T)_jmPTh(0.001)_cmRkTh(360)_mxJmD(15)_plCn(T)_adMnD(6)_adScFr(0)_bi(F)_stGrSp(2)_coFr(0.5).zip");
							
			if (rupSet == null) {
				if (!rebuildRupSet && rupSetFile.exists()) {
					rupSet = NZSHM22_CrustalInversionRunner.loadRuptureSet(rupSetFile, branch);
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
					
				// CBC: 
				// BEGIN from NZSHM22_CrustalInversionRunner.configure
			
				InversionModels inversionModel = (InversionModels) branch.getValue(InversionModels.class);

				// this contains all inversion weights
				double mfdEqualityConstraintWt = 1000;
				double mfdInequalityConstraintWt = 1000;
				
				NZSHM22_CrustalInversionConfiguration inversionConfiguration = NZSHM22_CrustalInversionConfiguration
						.forModel(inversionModel, (NZSHM22_InversionFaultSystemRuptSet) rupSet, mfdEqualityConstraintWt, mfdInequalityConstraintWt);

//				List<IncrementalMagFreqDist> solutionMfds = ((NZSHM22_CrustalInversionTargetMFDs) inversionConfiguration.getInversionTargetMfds()).getMFDConstraintComponents();
			
				// set up slip rate config
				inversionConfiguration.setSlipRateWeightingType(SlipRateConstraintWeightingType.BOTH);
				double slipRateConstraintWt_normalized = 1000;
				double slipRateConstraintWt_unnormalized = 1000;
				
				inversionConfiguration.setSlipRateConstraintWt_normalized(slipRateConstraintWt_normalized);
				inversionConfiguration.setSlipRateConstraintWt_unnormalized(slipRateConstraintWt_unnormalized);

				/*
				 * Build inversion inputs
				 */
				List<AveSlipConstraint> aveSlipConstraints = null;
				NZSHM22_CrustalInversionInputGenerator inputGen = new NZSHM22_CrustalInversionInputGenerator(
						(NZSHM22_InversionFaultSystemRuptSet) rupSet, inversionConfiguration, null, aveSlipConstraints, null, null);				
				
//				InversionTargetMFDs targetMFDs = rupSet.requireModule(NZSHM22_CrustalInversionTargetMFDs.class);
				
				// CBC
				// END configure		
				
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
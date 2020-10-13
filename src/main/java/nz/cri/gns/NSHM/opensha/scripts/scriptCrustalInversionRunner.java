package nz.cri.gns.NSHM.opensha.scripts;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.dom4j.DocumentException;

import nz.cri.gns.NSHM.opensha.ruptures.NSHMRuptureSetBuilder;
import nz.cri.gns.NSHM.opensha.ruptures.NSHMRuptureSetBuilder.RupturePermutationStrategy;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.SlipAlongRuptureModelRupSet;
import scratch.UCERF3.inversion.UCERF3InversionConfiguration.SlipRateConstraintWeightingType;
import scratch.UCERF3.simulatedAnnealing.ConstraintRange;
import scratch.UCERF3.simulatedAnnealing.ThreadedSimulatedAnnealing;
import scratch.UCERF3.simulatedAnnealing.completion.CompletionCriteria;
import scratch.UCERF3.simulatedAnnealing.completion.TimeCompletionCriteria;
import scratch.UCERF3.utils.FaultSystemIO;
import scratch.UCERF3.utils.MFD_InversionConstraint;

import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionInputGenerator;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.MFDEqualityInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.MFDInequalityInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.SlipRateInversionConstraint;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

/**
 *
 * Wrapper for building NSHM Rupture sets from CLI
 * 
 */
public class scriptCrustalInversionRunner {

		public static CommandLine parseCommandLine(String[] args) throws ParseException {
		
		Option faultIdInOption = new Option("n", "faultIdIn", true, "a list of faultSectionIDs for plausability filter");
		faultIdInOption.setArgs(Option.UNLIMITED_VALUES);
		faultIdInOption.setValueSeparator(',');
		
		Options options = new Options()
				.addRequiredOption("f", "fsdFile", true, "an opensha-xml Fault Source file")
				.addRequiredOption("o", "outputDir", true, "an existing directory to receive output file(s)")
				.addOption("l", "maxSubSectionLength", true, "maximum sub section length (in units of DDW) default")
				.addOption("d", "maxDistance", true, "max distance for linking multi fault ruptures, km")
				.addOption("d", "maxFaultSections", true, "(for testing) set number fault ruptures to process, default 1000")
				.addOption("k", "skipFaultSections", true, "(for testing) skip n fault ruptures, default 0")
				.addOption("i", "inversionMins", true, "run inversions for this many minutes")
				.addOption("y", "syncInterval", true, "seconds between inversion synchronisations")
				.addOption("r", "runInversion", true, "run inversion stage")
				.addOption("p", "minSubSectsPerParent", true, "min number of subsections per parent fault, when building ruptures")
				.addOption("s", "ruptureStrategy", true, "rupture permutation strategy - one of `DOWNDIP`, `UCERF3`, `POINTS`")
				.addOption(faultIdInOption);				
		return new DefaultParser().parse(options, args);
	}
	
	/**
	 * @param args
	 * @throws DocumentException, IOException, ParseException 
	 */
	public static void main(String[] args) throws DocumentException, IOException, ParseException {
		CommandLine cmd = parseCommandLine(args);

		boolean runInversion = true; // run inversion stage
		long inversionMins = 1; // run it for this many minutes
		long syncInterval = 10; // seconds between inversion synchronisations		
		int numThreads = Runtime.getRuntime().availableProcessors(); // use all available processors
		
		File outputDir = new File(cmd.getOptionValue("outputDir")); 
		File rupSetFile = new File(outputDir, "CFM_crustal_rupture_set.zip");
		File solFile = new File(outputDir, "CFM_crustal_solution.zip");
		File fsdFile = new File(cmd.getOptionValue("fsdFile")); 

		NSHMRuptureSetBuilder builder = new NSHMRuptureSetBuilder();
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
			builder.setMaxSubSectionLength( Double.parseDouble(cmd.getOptionValue("maxSubSectionLength")));
		}
		if (cmd.hasOption("maxDistance")) {
			System.out.println("set maxDistance to " + cmd.getOptionValue("maxDistance"));
			builder.setMaxJumpDistance( Double.parseDouble(cmd.getOptionValue("maxDistance")));
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
			System.out.println("set faultIdIn to " + cmd.getOptionValue("faultIdIn"));
			Set<Integer> faultIdIn = Stream.of(cmd.getOptionValues("faultIdIn")).map(id -> Integer.parseInt(id)).collect(Collectors.toSet());			
			builder.setFaultIdIn(faultIdIn);
		}			
		if (cmd.hasOption("minSubSectsPerParent")) {
			System.out.println("set minSubSectsPerParent to " + cmd.getOptionValue("minSubSectsPerParent"));
			builder.setMinSubSectsPerParent(Integer.parseInt(cmd.getOptionValue("minSubSectsPerParent")));
		}			
			
		System.out.println("=========");

		SlipAlongRuptureModelRupSet rupSet = builder.buildRuptureSet(fsdFile);
		FaultSystemIO.writeRupSet(rupSet, rupSetFile);
		
		if (cmd.hasOption("runInversion")) {
			System.out.println("set runInversion to " + cmd.getOptionValue("runInversion"));
			runInversion = Boolean.parseBoolean(cmd.getOptionValue("runInversion"));
		}
		if (cmd.hasOption("inversionMins")) {
			System.out.println("set inversionMins to " + cmd.getOptionValue("inversionMins"));
			inversionMins = Long.parseLong(cmd.getOptionValue("inversionMins"));
		}
		if (cmd.hasOption("syncInterval")) {
			System.out.println("set syncInterval to " + cmd.getOptionValue("syncInterval"));
			syncInterval = Long.parseLong(cmd.getOptionValue("syncInterval"));
		}
			
		if (runInversion) {
			List<InversionConstraint> constraints = new ArrayList<>();
			
			/*
			 * Slip rate constraints
			 */
			// For SlipRateConstraintWeightingType.NORMALIZED (also used for SlipRateConstraintWeightingType.BOTH) -- NOT USED if UNNORMALIZED!
			double slipRateConstraintWt_normalized = 1;
			// For SlipRateConstraintWeightingType.UNNORMALIZED (also used for SlipRateConstraintWeightingType.BOTH) -- NOT USED if NORMALIZED!
			double slipRateConstraintWt_unnormalized = 100;
			// If normalized, slip rate misfit is % difference for each section (recommended since it helps fit slow-moving faults).
			// If unnormalized, misfit is absolute difference.
			// BOTH includes both normalized and unnormalized constraints.
			SlipRateConstraintWeightingType slipRateWeighting = SlipRateConstraintWeightingType.BOTH; // (recommended: BOTH)
			constraints.add(new SlipRateInversionConstraint(slipRateConstraintWt_normalized, slipRateConstraintWt_unnormalized,
					slipRateWeighting, rupSet, rupSet.getSlipRateForAllSections()));
			
			/*
			 * MFD constraints
			 */
			double totalRateM5 = 10d; // expected number of M>=5's per year
			double bValue = 1d; // G-R b-value
			// magnitude to switch from MFD equality to MFD inequality
			double mfdTransitionMag = 7.85;
			double mfdEqualityConstraintWt = 10;
			double mfdInequalityConstraintWt = 1000;
			int mfdNum = 40;
			double mfdMin = 5.05d;
			double mfdMax = 8.95;
			GutenbergRichterMagFreqDist mfd = new GutenbergRichterMagFreqDist(
					bValue, totalRateM5, mfdMin, mfdMax, mfdNum);
			int transitionIndex = mfd.getClosestXIndex(mfdTransitionMag);
			// snap it to the discretization if it wasn't already
			mfdTransitionMag = mfd.getX(transitionIndex);
			Preconditions.checkState(transitionIndex >= 0);
			GutenbergRichterMagFreqDist equalityMFD = new GutenbergRichterMagFreqDist(
					bValue, totalRateM5, mfdMin, mfdTransitionMag, transitionIndex);
			MFD_InversionConstraint equalityConstr = new MFD_InversionConstraint(equalityMFD, null);
			GutenbergRichterMagFreqDist inequalityMFD = new GutenbergRichterMagFreqDist(
					bValue, totalRateM5, mfdTransitionMag, mfdMax, mfd.size()-equalityMFD.size());
			MFD_InversionConstraint inequalityConstr = new MFD_InversionConstraint(inequalityMFD, null);
			
			constraints.add(new MFDEqualityInversionConstraint(rupSet, mfdEqualityConstraintWt,
					Lists.newArrayList(equalityConstr), null));
			constraints.add(new MFDInequalityInversionConstraint(rupSet, mfdInequalityConstraintWt,
					Lists.newArrayList(inequalityConstr)));
			
			// weight of entropy-maximization constraint (not used in UCERF3)
			double smoothnessWt = 0;
			
			/*
			 * Build inversion inputs
			 */
			InversionInputGenerator inputGen = new InversionInputGenerator(rupSet, constraints);
			
			inputGen.generateInputs(true);
			// column compress it for fast annealing
			inputGen.columnCompress();
			
			// inversion completion criteria (how long it will run)
			CompletionCriteria criteria = TimeCompletionCriteria.getInMinutes(inversionMins);
			
			// Bring up window to track progress
			// criteria = new ProgressTrackingCompletionCriteria(criteria, progressReport, 0.1d);
			
			// this is the "sub completion criteria" - the amount of time (or iterations) between synchronization
			CompletionCriteria subCompletionCriteria = TimeCompletionCriteria.getInSeconds(syncInterval); // 1 second;
			
			ThreadedSimulatedAnnealing tsa = new ThreadedSimulatedAnnealing(inputGen.getA(), inputGen.getD(),
					inputGen.getInitialSolution(), smoothnessWt, inputGen.getA_ineq(), inputGen.getD_ineq(),
					inputGen.getWaterLevelRates(), numThreads, subCompletionCriteria);
			tsa.setConstraintRanges(inputGen.getConstraintRowRanges());
			
			tsa.iterate(criteria);
			
			// now assemble the solution
			double[] solution_raw = tsa.getBestSolution();
			
			// adjust for minimum rates if applicable
			double[] solution_adjusted = inputGen.adjustSolutionForWaterLevel(solution_raw);
			
			Map<ConstraintRange, Double> energies = tsa.getEnergies();
			if (energies != null) {
				System.out.println("Final energies:");
				for (ConstraintRange range : energies.keySet())
					System.out.println("\t"+range.name+": "+energies.get(range).floatValue());
			}
			
			// now write out the solution
			FaultSystemSolution sol = new FaultSystemSolution(rupSet, solution_adjusted);
			FaultSystemIO.writeSol(sol, solFile);
		}		
	
		System.exit(0);
	
	}
	


}

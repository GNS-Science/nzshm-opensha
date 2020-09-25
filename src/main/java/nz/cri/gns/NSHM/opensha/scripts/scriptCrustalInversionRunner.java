/**
 * 
 */
package nz.cri.gns.NSHM.opensha.scripts;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.dom4j.DocumentException;
import org.opensha.commons.util.FaultUtils;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionInputGenerator;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.MFDEqualityInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.MFDInequalityInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.SlipRateInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRuptureBuilder;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.CumulativeAzimuthChangeFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.JumpAzimuthChangeFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.TotalAzimuthChangeFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterPermutationStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.DistCutoffClosestSectClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import nz.cri.gns.NSHM.opensha.ruptures.downDipSubSectTest.DownDipSubSectBuilder;
//import nz.cri.gns.NSHM.opensha.ruptures.downDipSubSectTest.DownDipSubSectBuilder;
import nz.cri.gns.NSHM.opensha.ruptures.downDipSubSectTest.DownDipTestPermutationStrategy;
import nz.cri.gns.NSHM.opensha.ruptures.downDipSubSectTest.RectangularityFilter;
import nz.cri.gns.NSHM.opensha.ruptures.downDipSubSectTest.SubSectionParentFilter;

import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.SlipAlongRuptureModelRupSet;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;
import scratch.UCERF3.inversion.UCERF3InversionConfiguration.SlipRateConstraintWeightingType;
import scratch.UCERF3.simulatedAnnealing.ConstraintRange;
import scratch.UCERF3.simulatedAnnealing.ThreadedSimulatedAnnealing;
import scratch.UCERF3.simulatedAnnealing.completion.CompletionCriteria;
import scratch.UCERF3.simulatedAnnealing.completion.TimeCompletionCriteria;
import scratch.UCERF3.utils.FaultSystemIO;
import scratch.UCERF3.utils.MFD_InversionConstraint;

/**
 * @author chrisbc
 *
 * initial testing of crustal inversions using the NZ CFM 0.3 XML
 * 
 */
public class scriptCrustalInversionRunner {

	static DownDipSubSectBuilder downDipBuilder;

	public static CommandLine parseCommandLine(String[] args) throws ParseException {
		
		Option faultIdInOption = new Option("n", "faultIdIn", true, "a list of faultSectionIDs for plausability filter");
		faultIdInOption.setArgs(Option.UNLIMITED_VALUES);
		faultIdInOption.setValueSeparator(',');
		
		Options options = new Options()
				.addRequiredOption("f", "fsdFile", true, "an opensha-xml Fault Source file")
				.addRequiredOption("o", "outputDir", true, "an existing directory to receive output file(s)")
				.addOption("l", "maxLength", true, "maximum sub section length (in units of DDW) default")
				.addOption("d", "maxDistance", true, "max distance for linking multi fault ruptures, km")
				.addOption("d", "maxFaultSections", true, "(for testing) set number fault ruptures to process, default 1000")
				.addOption("k", "skipFaultSections", true, "(for testing) skip n fault ruptures, default 0")
				.addOption("i", "inversionMins", true, "run inversions for this many minutes")
				.addOption("y", "syncInterval", true, "seconds between inversion synchronisations")
				.addOption("r", "runInversion", true, "run inversion stage")
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
		double maxSubSectionLength = 0.5; // maximum sub section length (in units of DDW)
		double maxDistance = 0.25; // max distance for linking multi fault ruptures, km
		long maxFaultSections = 1000; // maximum fault ruptures to process
		long skipFaultSections = 0; // skip n fault ruptures, default 0"
		Set<Integer> faultIdIn = Collections.emptySet();
				
		File outputDir = new File(cmd.getOptionValue("outputDir")); 
		File rupSetFile = new File(outputDir, "CFM_crustal_rupture_set.zip");
		File solFile = new File(outputDir, "CFM_crustal_solution.zip");
		File fsdFile = new File(cmd.getOptionValue("fsdFile")); 
		System.out.println("Arguments");
		System.out.println("=========");
		System.out.println("fsdFile: " + cmd.getOptionValue("fsdFile"));
		System.out.println("outputDir: " + outputDir);
				
		if (cmd.hasOption("maxLength")) {
			System.out.println("set maxSubSectionLength to " + cmd.getOptionValue("maxLength"));
			maxSubSectionLength = Double.parseDouble(cmd.getOptionValue("maxLength"));
		}
		if (cmd.hasOption("maxDistance")) {
			System.out.println("set maxDistance to " + cmd.getOptionValue("maxDistance"));
			maxDistance = Double.parseDouble(cmd.getOptionValue("maxDistance"));
		}
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
		if (cmd.hasOption("maxFaultSections")) {
			System.out.println("set maxFaultSections to " + cmd.getOptionValue("maxFaultSections"));
			maxFaultSections = Long.parseLong(cmd.getOptionValue("maxFaultSections"));
		}			
		if (cmd.hasOption("skipFaultSections")) {
			System.out.println("set skipFaultSections to " + cmd.getOptionValue("skipFaultSections"));
			skipFaultSections = Long.parseLong(cmd.getOptionValue("skipFaultSections"));
		}
		if (cmd.hasOption("faultIdIn")) {
//			System.out.println("set skipFaultSections to " + cmd.getOptionValue("skipFaultSections"));
//			faultNameContains = Set();
			faultIdIn = Stream.of(cmd.getOptionValues("faultIdIn")).map(id -> Integer.parseInt(id)).collect(Collectors.toSet());			
		}			
//		if (cmd.hasOption("faultNmedContains")) {
////			System.out.println("set skipFaultSections to " + cmd.getOptionValue("skipFaultSections"));
////			faultNameContains = Set();
//			faultIdContains = Stream.of(cmd.getOptionValues("faultNameContains")).map(name -> name).collect(Collectors.toSet());			
//		}
		System.out.println("=========");

		// load in the fault section data ("parent sections")
		List<FaultSection> fsd = FaultModels.loadStoredFaultSections(fsdFile);
		System.out.println("Fault model has "+fsd.size()+" fault sections");
		
		if (maxFaultSections < 1000 || skipFaultSections > 0) {
		 	// iterate backwards as we will be removing from the list
			long currSectionId; 
		 	for (int i=fsd.size(); --i>=0;) {
		 		currSectionId = fsd.get(i).getSectionId();
		 		if ( currSectionId >= (maxFaultSections + skipFaultSections) || currSectionId < skipFaultSections)
		 			fsd.remove(i);
		 	}
			System.out.println("Fault model now has "+fsd.size()+" fault sections");
		}
		
		// build the subsections
		List<FaultSection> subSections = new ArrayList<>();
		int sectIndex = 0;
		for (FaultSection parentSect : fsd) {
			double ddw = parentSect.getOrigDownDipWidth();
			double maxSectLength = ddw*maxSubSectionLength;
			System.out.println("Get subSections in "+parentSect.getName());
			// the "2" here sets a minimum number of sub sections
			List<? extends FaultSection> newSubSects = parentSect.getSubSectionsList(maxSectLength, sectIndex, 2);
			subSections.addAll(newSubSects);
			sectIndex += newSubSects.size();
			System.out.println("Produced "+newSubSects.size()+" subSections in "+parentSect.getName());
		}
		
		System.out.println(subSections.size()+" Sub Sections");

		for (int s=0; s<subSections.size(); s++)
			Preconditions.checkState(subSections.get(s).getSectionId() == s,
				"section at index %s has ID %s", s, subSections.get(s).getSectionId());

		FaultSection interfaceParentSection = new FaultSectionPrefData();
		interfaceParentSection.setSectionId(10000);
		
		downDipBuilder = new DownDipSubSectBuilder(interfaceParentSection);
		
		// instantiate plausibility filters
		List<PlausibilityFilter> filters = new ArrayList<>();
//		int minDimension = 1; // minimum numer of rows or columns
//		double maxAspectRatio = 3d; // max aspect ratio of rows/cols or cols/rows
//		filters.add(new RectangularityFilter(downDipBuilder, minDimension, maxAspectRatio));
			
		Predicate<FaultSubsectionCluster> pFilter = new SubSectionParentFilter().makeParentIdFilter(faultIdIn);
		PlausibilityFilter idFilter = new SubSectionParentFilter(pFilter);
		filters.add(idFilter);
		
		SectionDistanceAzimuthCalculator distAzCalc = new SectionDistanceAzimuthCalculator(subSections);
		JumpAzimuthChangeFilter.AzimuthCalc azimuthCalc = new JumpAzimuthChangeFilter.SimpleAzimuthCalc(distAzCalc);
		filters.add(new JumpAzimuthChangeFilter(azimuthCalc, 60f));
		filters.add(new TotalAzimuthChangeFilter(azimuthCalc, 60f, true, true));
		filters.add(new CumulativeAzimuthChangeFilter(azimuthCalc, 580f));

		// this creates rectangular permutations only for our down-dip fault to speed up rupture building
		ClusterPermutationStrategy permutationStrategy = new DownDipTestPermutationStrategy(downDipBuilder);
		// connection strategy: parent faults connect at closest point, and only when dist <=5 km
		ClusterConnectionStrategy connectionStrategy = new DistCutoffClosestSectClusterConnectionStrategy(maxDistance);
		int maxNumSplays = 0; // don't allow any splays
		
		ClusterRuptureBuilder builder = new ClusterRuptureBuilder(subSections, connectionStrategy,
				distAzCalc, filters, maxNumSplays);
		
		List<ClusterRupture> ruptures = builder.build(permutationStrategy);
		
		System.out.println("Built "+ruptures.size()+" total ruptures");
			
		MySlipEnabledRupSet rupSet = new MySlipEnabledRupSet(ruptures, subSections,
				ScalingRelationships.SHAW_2009_MOD, SlipAlongRuptureModels.UNIFORM);
		
		FaultSystemIO.writeRupSet(rupSet, rupSetFile);

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
			
			// this will use all available processors
			int numThreads = Runtime.getRuntime().availableProcessors();

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
		
		
		
	}
	
	private static Object SubSectionParentFilter(Predicate<FaultSubsectionCluster> pFilter) {
		// TODO Auto-generated method stub
		return null;
	}

	private static class MySlipEnabledRupSet extends SlipAlongRuptureModelRupSet {
	
		/**
		 * 
		 */
		private static final long serialVersionUID = -4984738430816137976L;
		private double[] rupAveSlips;
		
		public MySlipEnabledRupSet(List<ClusterRupture> ruptures, List<FaultSection> subSections,
				ScalingRelationships scale, SlipAlongRuptureModels slipAlongModel) {
			super(slipAlongModel);
			
			// build a rupture set (doing this manually instead of creating an inversion fault system rup set,
			// mostly as a demonstration)
			double[] sectSlipRates = new double[subSections.size()];
			double[] sectAreasReduced = new double[subSections.size()];
			double[] sectAreasOrig = new double[subSections.size()];
			for (int s=0; s<sectSlipRates.length; s++) {
				FaultSection sect = subSections.get(s);
				sectAreasReduced[s] = sect.getArea(true);
				sectAreasOrig[s] = sect.getArea(false);
				sectSlipRates[s] = sect.getReducedAveSlipRate()*1e-3; // mm/yr => m/yr
			}
			
			double[] rupMags = new double[ruptures.size()];
			double[] rupRakes = new double[ruptures.size()];
			double[] rupAreas = new double[ruptures.size()];
			double[] rupLengths = new double[ruptures.size()];
			rupAveSlips = new double[ruptures.size()];
			List<List<Integer>> rupsIDsList = new ArrayList<>();
			for (int r=0; r<ruptures.size(); r++) {
				ClusterRupture rup = ruptures.get(r);
				List<FaultSection> rupSects = rup.buildOrderedSectionList();
				List<Integer> sectIDs = new ArrayList<>();
				double totLength = 0d;
				double totArea = 0d;
				double totOrigArea = 0d; // not reduced for aseismicity
				List<Double> sectAreas = new ArrayList<>();
				List<Double> sectRakes = new ArrayList<>();
				for (FaultSection sect : rupSects) {
					sectIDs.add(sect.getSectionId());
					double length = sect.getTraceLength()*1e3;	// km --> m
					totLength += length;
					double area = sectAreasReduced[sect.getSectionId()];	// sq-m
					totArea += area;
					totOrigArea += sectAreasOrig[sect.getSectionId()];	// sq-m
					sectAreas.add(area);
					sectRakes.add(sect.getAveRake());
				}
				rupAreas[r] = totArea;
				rupLengths[r] = totLength;
				rupRakes[r] = FaultUtils.getInRakeRange(FaultUtils.getScaledAngleAverage(sectAreas, sectRakes));
				double origDDW = totOrigArea/totLength;
				rupMags[r] = scale.getMag(totArea, origDDW);
				rupsIDsList.add(sectIDs);
				rupAveSlips[r] = scale.getAveSlip(totArea, totLength, origDDW);
			}
			
			String info = "Test down-dip subsectioning rup set";
			
			init(subSections, sectSlipRates, null, sectAreasReduced,
					rupsIDsList, rupMags, rupRakes, rupAreas, rupLengths, info);
		}
	
		@Override
		public double getAveSlipForRup(int rupIndex) {
			return rupAveSlips[rupIndex];
		}
	
		@Override
		public double[] getAveSlipForAllRups() {
			return rupAveSlips;
		}
	}
}

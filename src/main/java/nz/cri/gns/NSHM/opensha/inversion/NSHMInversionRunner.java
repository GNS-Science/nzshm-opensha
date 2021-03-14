package nz.cri.gns.NSHM.opensha.inversion;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import nz.cri.gns.NSHM.opensha.ruptures.NSHMSlipEnabledRuptureSet;
import nz.cri.gns.NSHM.opensha.inversion.NSHM_InversionFaultSystemSolution;

import org.dom4j.DocumentException;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionInputGenerator;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.MFDEqualityInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.MFDInequalityInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.SlipRateInversionConstraint;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.SlipAlongRuptureModelRupSet;
import scratch.UCERF3.SlipEnabledSolution;
import scratch.UCERF3.analysis.FaultSystemRupSetCalc;
import scratch.UCERF3.enumTreeBranches.InversionModels;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;
import scratch.UCERF3.griddedSeismicity.GridSourceProvider;
import scratch.UCERF3.inversion.InversionFaultSystemSolution;
import scratch.UCERF3.inversion.UCERF3InversionConfiguration;
import scratch.UCERF3.inversion.laughTest.OldPlausibilityConfiguration;
import scratch.UCERF3.logicTree.LogicTreeBranch;
import scratch.UCERF3.inversion.CommandLineInversionRunner;
import scratch.UCERF3.inversion.InversionFaultSystemRupSet;
//import scratch.UCERF3.inversion.CommandLineInversionRunner.getSectionMoments;
import scratch.UCERF3.simulatedAnnealing.ConstraintRange;
import scratch.UCERF3.simulatedAnnealing.ThreadedSimulatedAnnealing;
import scratch.UCERF3.simulatedAnnealing.completion.*;
import scratch.UCERF3.utils.FaultSystemIO;
import scratch.UCERF3.utils.MFD_InversionConstraint;
import scratch.UCERF3.utils.aveSlip.AveSlipConstraint;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;

/**
 * Runs the standard NSHM inversion on a rupture set.
 */
public class NSHMInversionRunner {

	protected long inversionMins = 1;
	protected long syncInterval = 10;
	protected int numThreads = Runtime.getRuntime().availableProcessors();
	protected NSHM_InversionFaultSystemRuptSet rupSet = null;
	protected List<InversionConstraint> constraints = new ArrayList<>();
	protected List<CompletionCriteria> completionCriterias = new ArrayList<>();
	private EnergyChangeCompletionCriteria energyChangeCompletionCriteria = null;

	private CompletionCriteria completionCriteria;
	private ThreadedSimulatedAnnealing tsa;
	private double[] initialState;
	private NSHM_InversionFaultSystemSolution solution;

	/*
	 * Sliprate constraint default settings
	 */
	// If normalized, slip rate misfit is % difference for each section (recommended
	// since it helps fit slow-moving faults).
	// If unnormalized, misfit is absolute difference.
	// BOTH includes both normalized and unnormalized constraints.
	protected UCERF3InversionConfiguration.SlipRateConstraintWeightingType slipRateWeighting = UCERF3InversionConfiguration.SlipRateConstraintWeightingType.BOTH; // (recommended:
																																									// BOTH)
	// For SlipRateConstraintWeightingType.NORMALIZED (also used for
	// SlipRateConstraintWeightingType.BOTH) -- NOT USED if UNNORMALIZED!
	protected double slipRateConstraintWt_normalized = 1;
	// For SlipRateConstraintWeightingType.UNNORMALIZED (also used for
	// SlipRateConstraintWeightingType.BOTH) -- NOT USED if NORMALIZED!
	protected double slipRateConstraintWt_unnormalized = 100;

	/*
	 * MFD constraint default settings
	 */
	protected double totalRateM5 = 5d;
	protected double bValue = 1d;
	protected double mfdTransitionMag = 7.85; // TODO: how to validate this number for NZ? (ref Morgan Page in
												// USGS/UCERF3) [KKS, CBC]
	protected int mfdNum = 40;
	protected double mfdMin = 5.05d;
	protected double mfdMax = 8.95;

	protected double mfdEqualityConstraintWt = 10;
	protected double mfdInequalityConstraintWt = 1000;

	private NSHM_InversionConfiguration inversionConfiguration;

	/**
	 * Creates a new NSHMInversionRunner with defaults.
	 */
	public NSHMInversionRunner() {
	}

	/**
	 * Sets how many minutes the inversion runs for. Default is 1 minute.
	 * 
	 * @param inversionMinutes the duration of the inversion in minutes.
	 * @return this runner.
	 */
	public NSHMInversionRunner setInversionMinutes(long inversionMinutes) {
		this.inversionMins = inversionMinutes;
		return this;
	}

	/**
	 * @param energyDelta
	 * @param energyPercentDelta
	 * @param lookBackMins
	 * @return
	 */
	public NSHMInversionRunner setEnergyChangeCompletionCriteria(double energyDelta, double energyPercentDelta,
			double lookBackMins) {
		this.energyChangeCompletionCriteria = new EnergyChangeCompletionCriteria(energyDelta, energyPercentDelta,
				lookBackMins);
		return this;
	}

	/**
	 * Sets the length of time between syncs in seconds. Default is 10 seconds.
	 * 
	 * @param syncInterval the interval in seconds.
	 * @return this runner.
	 */
	public NSHMInversionRunner setSyncInterval(long syncInterval) {
		this.syncInterval = syncInterval;
		return this;
	}

	/**
	 * Sets how many threads the inversion will try to use. Default is all available
	 * processors / cores.
	 * 
	 * @param numThreads the number of threads.
	 * @return this runner.
	 */
	public NSHMInversionRunner setNumThreads(int numThreads) {
		this.numThreads = numThreads;
		return this;
	}

	/**
	 * Sets the FaultModel file f
	 *
	 * @param ruptureSetFileName the rupture file name
	 * @return this builder
	 * @throws DocumentException
	 * @throws IOException
	 */
	public NSHMInversionRunner setRuptureSetFile(String ruptureSetFileName) throws IOException, DocumentException {
		File rupSetFile = new File(ruptureSetFileName);
		this.setRuptureSetFile(rupSetFile);
		return this;
	}

	/**
	 * Sets the FaultModel file
	 *
	 * @param ruptureSetFile the rupture file
	 * @return this builder
	 * @throws DocumentException
	 * @throws IOException
	 */
	public NSHMInversionRunner setRuptureSetFile(File ruptureSetFile) throws IOException, DocumentException {
		FaultSystemRupSet rupSetA = loadRupSet(ruptureSetFile);
		
		// convert rupture set to NSHM_InversionFaultSystemRuptSet for logicTree etc
		LogicTreeBranch branch = (LogicTreeBranch) LogicTreeBranch.DEFAULT;
		//		OldPlausibilityConfiguration filter = null;
		
		this.rupSet = new NSHM_InversionFaultSystemRuptSet(rupSetA, branch); 
		//, filter,
//				null, //rupSetA.getSlipRateForAllSections(),
//				null. rupSetA.getSectionIndicesForAllRups(),
//				null,
//				null);
//		NSHM_InversionFaultSystemRuptSet rupSet2 = new NSHM_InversionFaultSystemRuptSet(
//				rupSet1, branch, null,
//				rupSet1.getAveSlipForAllRups(),rupSet1.getCloseSectionsListList(),
//				rupSet1.getRupturesForClusters(), rupSet1.getSectionsForClusters());
		return this;
	}

	/**
	 * Sets GutenbergRichterMFD arguments
	 * 
	 * @param totalRateM5      the number of M>=5's per year. TODO: ref David
	 *                         Rhodes/Chris Roland? [KKS, CBC]
	 * @param bValue
	 * @param mfdTransitionMag magnitude to switch from MFD equality to MFD
	 *                         inequality TODO: how to validate this number for NZ?
	 *                         (ref Morgan Page in USGS/UCERF3) [KKS, CBC]
	 * @param mfdNum
	 * @param mfdMin
	 * @param mfdMax
	 * @return
	 */
	public NSHMInversionRunner setGutenbergRichterMFD(double totalRateM5, double bValue, double mfdTransitionMag,
			int mfdNum, double mfdMin, double mfdMax) {
		this.totalRateM5 = totalRateM5;
		this.bValue = bValue;
		this.mfdTransitionMag = mfdTransitionMag;
		this.mfdNum = mfdNum;
		this.mfdMin = mfdMin;
		this.mfdMax = mfdMax;
		return this;
	}

	/**
	 * @param mfdEqualityConstraintWt
	 * @param mfdInequalityConstraintWt
	 * @return
	 */
	public NSHMInversionRunner setGutenbergRichterMFDWeights(double mfdEqualityConstraintWt,
			double mfdInequalityConstraintWt) {
		this.mfdEqualityConstraintWt = mfdEqualityConstraintWt;
		this.mfdInequalityConstraintWt = mfdInequalityConstraintWt;
		return this;
	}

	/**
	 * If normalized, slip rate misfit is % difference for each section (recommended
	 * since it helps fit slow-moving faults). If unnormalized, misfit is absolute
	 * difference. BOTH includes both normalized and unnormalized constraints.
	 * 
	 * @param weightingType  a value
	 *                       fromUCERF3InversionConfiguration.SlipRateConstraintWeightingType
	 * @param normalizedWt
	 * @param unnormalizedWt
	 * @return
	 */
	public NSHMInversionRunner setSlipRateConstraint(
			UCERF3InversionConfiguration.SlipRateConstraintWeightingType weightingType, double normalizedWt,
			double unnormalizedWt) {
		this.slipRateWeighting = weightingType;
		this.slipRateConstraintWt_normalized = normalizedWt;
		this.slipRateConstraintWt_unnormalized = unnormalizedWt;
		return this;
	}

	public NSHMInversionRunner setInversionConfiguration(NSHM_InversionConfiguration config) {
		System.out.println("Building Inversion Configuration");
		inversionConfiguration = config;
		return this;
	}

	public NSHMInversionRunner configure() {
		LogicTreeBranch logicTreeBranch = this.rupSet.getLogicTreeBranch();
		InversionModels inversionModel = logicTreeBranch.getValue(InversionModels.class);

		// this contains all inversion weights
//		inversionConfiguration = NSHM_InversionConfiguration.forModel(inversionModel, rupSet, mfdEqualityConstraintWt,
//				mfdInequalityConstraintWt);
//		
		inversionConfiguration = NSHM_SubductionInversionConfiguration.forModel(inversionModel, rupSet, 
				mfdEqualityConstraintWt, mfdInequalityConstraintWt);
		return this;
	}

	@SuppressWarnings("unchecked")
	protected FaultSystemRupSet loadRupSet(File file) throws IOException, DocumentException {
		FaultSystemRupSet fsRupSet = FaultSystemIO.loadRupSet(file);
		return fsRupSet;

	}

	/**
	 * Runs the inversion on the specified rupture set. make sure to call
	 * .configure() first.
	 * 
	 * @return the FaultSystemSolution.
	 * @throws IOException
	 * @throws DocumentException
	 */
	public FaultSystemSolution runInversion() throws IOException, DocumentException {

		// weight of entropy-maximization constraint (not used in UCERF3)
		double smoothnessWt = 0;

		/*
		 * Build inversion inputs
		 */
		List<AveSlipConstraint> aveSlipConstraints = null;
		// try {
		// aveSlipConstraints =
		// AveSlipConstraint.load(rupSet.getFaultSectionDataList());
		// } catch (IOException e) {
		// e.printStackTrace();
		// System.exit(1);
		// }

		NSHM_InversionInputGenerator inputGen = new NSHM_InversionInputGenerator(rupSet, inversionConfiguration, null,
				aveSlipConstraints, null, null);

		inputGen.generateInputs(true);
		// column compress it for fast annealing
		inputGen.columnCompress();

		// inversion completion criteria (how long it will run)
		this.completionCriterias.add(TimeCompletionCriteria.getInMinutes(inversionMins));
		if (!(this.energyChangeCompletionCriteria == null))
			this.completionCriterias.add(this.energyChangeCompletionCriteria);

		completionCriteria = new CompoundCompletionCriteria(this.completionCriterias);

		// Bring up window to track progress
		// criteria = new ProgressTrackingCompletionCriteria(criteria, progressReport,
		// 0.1d);
		// ....
		completionCriteria = new ProgressTrackingCompletionCriteria(completionCriteria);

		// this is the "sub completion criteria" - the amount of time (or iterations)
		// between synchronization
		CompletionCriteria subCompletionCriteria = TimeCompletionCriteria.getInSeconds(syncInterval); // 1 second;

		initialState = inputGen.getInitialSolution();

		tsa = new ThreadedSimulatedAnnealing(inputGen.getA(), inputGen.getD(), initialState, smoothnessWt,
				inputGen.getA_ineq(), inputGen.getD_ineq(), inputGen.getWaterLevelRates(), numThreads,
				subCompletionCriteria);
		tsa.setConstraintRanges(inputGen.getConstraintRowRanges());

		// From CLI metadata Analysis
		initialState = Arrays.copyOf(initialState, initialState.length);

		tsa.iterate(completionCriteria);

		// now assemble the solution
		double[] solution_raw = tsa.getBestSolution();

		// adjust for minimum rates if applicable
		double[] solution_adjusted = inputGen.adjustSolutionForWaterLevel(solution_raw);

		Map<ConstraintRange, Double> energies = tsa.getEnergies();
		if (energies != null) {
			System.out.println("Final energies:");
			for (ConstraintRange range : energies.keySet())
				System.out.println("\t" + range.name + ": " + energies.get(range).floatValue());
		}

		solution = new NSHM_InversionFaultSystemSolution(rupSet, solution_adjusted);
		return solution;
	}

	public String completionCriteriaMetrics() {
		String info = "";
		ProgressTrackingCompletionCriteria pComp = (ProgressTrackingCompletionCriteria) completionCriteria;
		long numPerturbs = pComp.getPerturbs().get(pComp.getPerturbs().size() - 1);
		int numRups = initialState.length;
		info += "\nAvg Perturbs Per Rup: " + numPerturbs + "/" + numRups + " = "
				+ ((double) numPerturbs / (double) numRups);
		int rupsPerturbed = 0;
		double[] solution_no_min_rates = tsa.getBestSolution();
		int numAboveWaterlevel = 0;
		for (int i = 0; i < numRups; i++) {
			if ((float) solution_no_min_rates[i] != (float) initialState[i])
				rupsPerturbed++;
			if (solution_no_min_rates[i] > 0)
				numAboveWaterlevel++;
		}
		info += "\nNum rups actually perturbed: " + rupsPerturbed + "/" + numRups + " ("
				+ (float) (100d * ((double) rupsPerturbed / (double) numRups)) + " %)";
		info += "\nAvg Perturbs Per Perturbed Rup: " + numPerturbs + "/" + rupsPerturbed + " = "
				+ ((double) numPerturbs / (double) rupsPerturbed);
		info += "\nNum rups above waterlevel: " + numAboveWaterlevel + "/" + numRups + " ("
				+ (float) (100d * ((double) numAboveWaterlevel / (double) numRups)) + " %)";
		info += "\n";
		return info;
	}

	public String momentAndRateMetrics() {
		String info = "";
		// add moments to info string
		info += "\n\n****** Moment and Rupture Rate Metatdata ******";
		info += "\nNum Ruptures: " + rupSet.getNumRuptures();
		int numNonZeros = 0;
		for (double rate : solution.getRateForAllRups())
			if (rate != 0)
				numNonZeros++;

		float percent = (float) numNonZeros / rupSet.getNumRuptures() * 100f;
		info += "\nNum Non-Zero Rups: " + numNonZeros + "/" + rupSet.getNumRuptures() + " (" + percent + " %)";
		info += "\nOrig (creep reduced) Fault Moment Rate: " + rupSet.getTotalOrigMomentRate();

		double momRed = rupSet.getTotalMomentRateReduction();
		info += "\nMoment Reduction (for subseismogenic ruptures only): " + momRed;
		info += "\nSubseismo Moment Reduction Fraction (relative to creep reduced): "
				+ rupSet.getTotalMomentRateReductionFraction();
		info += "\nFault Target Supra Seis Moment Rate (subseismo and creep reduced): "
				+ rupSet.getTotalReducedMomentRate();

		double totalSolutionMoment = solution.getTotalFaultSolutionMomentRate();
		info += "\nFault Solution Supra Seis Moment Rate: " + totalSolutionMoment;

		/*
		 * TODO : Matt, are these useful in NSHM ?? Priority ??
		 */
//		info += "\nFault Target Sub Seis Moment Rate: "
//				+rupSet.getInversionTargetMFDs().getTotalSubSeismoOnFaultMFD().getTotalMomentRate();
//		info += "\nFault Solution Sub Seis Moment Rate: "
//				+solution.getFinalTotalSubSeismoOnFaultMFD().getTotalMomentRate();
//		info += "\nTruly Off Fault Target Moment Rate: "
//				+rupSet.getInversionTargetMFDs().getTrulyOffFaultMFD().getTotalMomentRate();
//		info += "\nTruly Off Fault Solution Moment Rate: "
//				+solution.getFinalTrulyOffFaultMFD().getTotalMomentRate();
//		
//		try {
//			//					double totalOffFaultMomentRate = invSol.getTotalOffFaultSeisMomentRate(); // TODO replace - what is off fault moment rate now?
//			//					info += "\nTotal Off Fault Seis Moment Rate (excluding subseismogenic): "
//			//							+(totalOffFaultMomentRate-momRed);
//			//					info += "\nTotal Off Fault Seis Moment Rate (inluding subseismogenic): "
//			//							+totalOffFaultMomentRate;
//			info += "\nTotal Moment Rate From Off Fault MFD: "+solution.getFinalTotalGriddedSeisMFD().getTotalMomentRate();
//			//					info += "\nTotal Model Seis Moment Rate: "
//			//							+(totalOffFaultMomentRate+totalSolutionMoment);
//		} catch (Exception e1) {
//			e1.printStackTrace();
//			System.out.println("WARNING: InversionFaultSystemSolution could not be instantiated!");
//		}		

		info += "\n";
		return info;
	}

	public String byFaultNameMetrics() {
		String info = "";
		info += "\n\n****** byFaultNameMetrics Metadata ******";
//		double totalMultiplyNamedM7Rate = FaultSystemRupSetCalc.calcTotRateMultiplyNamedFaults((InversionFaultSystemSolution) solution, 7d, null);
//		double totalMultiplyNamedPaleoVisibleRate = FaultSystemRupSetCalc.calcTotRateMultiplyNamedFaults((InversionFaultSystemSolution) solution, 0d, paleoProbabilityModel);

		double totalM7Rate = FaultSystemRupSetCalc.calcTotRateAboveMag(solution, 7d, null);
//		double totalPaleoVisibleRate = FaultSystemRupSetCalc.calcTotRateAboveMag(sol, 0d, paleoProbabilityModel);

		info += "\n\nTotal rupture rate (M7+): " + totalM7Rate;
//		info += "\nTotal multiply named rupture rate (M7+): "+totalMultiplyNamedM7Rate;
//		info += "\n% of M7+ rate that are multiply named: "
//			+(100d * totalMultiplyNamedM7Rate / totalM7Rate)+" %";
//		info += "\nTotal paleo visible rupture rate: "+totalPaleoVisibleRate;
//		info += "\nTotal multiply named paleo visible rupture rate: "+totalMultiplyNamedPaleoVisibleRate;
//		info += "\n% of paleo visible rate that are multiply named: "
//			+(100d * totalMultiplyNamedPaleoVisibleRate / totalPaleoVisibleRate)+" %";
		info += "\n";
		return info;
	}

	public String parentFaultMomentRates() {
		// parent fault moment rates
		String info = "";
		ArrayList<CommandLineInversionRunner.ParentMomentRecord> parentMoRates = CommandLineInversionRunner
				.getSectionMoments((SlipEnabledSolution) solution);
		info += "\n\n****** Larges Moment Rate Discrepancies ******";
		for (int i = 0; i < 10 && i < parentMoRates.size(); i++) {
			CommandLineInversionRunner.ParentMomentRecord p = parentMoRates.get(i);
			info += "\n" + p.parentID + ". " + p.name + "\ttarget: " + p.targetMoment + "\tsolution: "
					+ p.solutionMoment + "\tdiff: " + p.getDiff();
		}
		info += "\n";
		return info;
	}

}

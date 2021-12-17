package nz.cri.gns.NZSHM22.opensha.inversion;

import java.io.File;
import java.io.IOException;
import java.util.*;

import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.*;
import org.apache.commons.math3.util.Precision;
import org.dom4j.DocumentException;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.RupSetScalingRelationship;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionInputGenerator;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.RupHistogramPlots.HistScalar;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.RupHistogramPlots.HistScalarValues;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;


import scratch.UCERF3.analysis.FaultSystemRupSetCalc;

import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.SerialSimulatedAnnealing;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.SimulatedAnnealing;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.ThreadedSimulatedAnnealing;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.CompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.CompoundCompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.EnergyChangeCompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.ProgressTrackingCompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.TimeCompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.params.GenerationFunctionType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.params.NonnegativityConstraintType;

/**
 * @author chrisbc
 *
 */
public abstract class NZSHM22_AbstractInversionRunner {

	protected long inversionSecs = 60;
	protected long selectionInterval = 10;

	private Integer inversionNumSolutionAverages = 1; // 1 means no averaging
	private Integer inversionThreadsPerSelector = 1;
	private Integer inversionAveragingIntervalSecs = null;
	private boolean inversionAveragingEnabled = false;
	private GenerationFunctionType perturbationFunction = GenerationFunctionType.UNIFORM_0p001; // FIXME: we should choose a better one
	private NonnegativityConstraintType nonNegAlgorithm = NonnegativityConstraintType.LIMIT_ZERO_RATES;
	private NZSHM22_SpatialSeisPDF spatialSeisPDF = null;

	protected File rupSetFile;
	protected NZSHM22_InversionFaultSystemRuptSet rupSet = null;
	protected NZSHM22_DeformationModel deformationModel = null;
	protected List<InversionConstraint> constraints = new ArrayList<>();
	protected List<CompletionCriteria> completionCriterias = new ArrayList<>();
	private EnergyChangeCompletionCriteria energyChangeCompletionCriteria = null;

	private ThreadedSimulatedAnnealing tsa;
	private double[] initialState;
	private FaultSystemSolution solution;

	private Map<String, Double> finalEnergies = new HashMap<String, Double>();
	private InversionInputGenerator inversionInputGenerator;

	protected List<IncrementalMagFreqDist> solutionMfds;

	protected AbstractInversionConfiguration.NZSlipRateConstraintWeightingType slipRateWeightingType;
	protected double slipRateConstraintWt_normalized;
	protected double slipRateConstraintWt_unnormalized;
	protected double mfdEqualityConstraintWt;
	protected double mfdInequalityConstraintWt;
	protected double mfdUncertaintyWeightedConstraintWt;
	protected double mfdUncertaintyWeightedConstraintPower; //typically 0.5

	protected abstract NZSHM22_AbstractInversionRunner configure() throws DocumentException, IOException;

	protected double totalRateM5; // = 5d;
	protected double bValue; // = 1d;
	protected double mfdTransitionMag = 7.85; // TODO: how to validate this number for NZ? (ref Morgan Page in
	// // USGS/UCERF3) [KKS, CBC]

	protected NZSHM22_ScalingRelationshipNode scalingRelationship;

	/**
	 * Sets how many minutes the inversion runs for in minutes. Default is 1 minute.
	 * 
	 * @param inversionMinutes the duration of the inversion in minutes.
	 * @return this runner.
	 */
	public NZSHM22_AbstractInversionRunner setInversionMinutes(long inversionMinutes) {
		this.inversionSecs = inversionMinutes * 60;
		return this;
	}

	/**
	 * Sets how many seconds the inversion runs for. Default is 60 seconds.
	 * 
	 * @param inversionSeconds the duration of the inversion in seconds.
	 * @return this runner.
	 */
	public NZSHM22_AbstractInversionRunner setInversionSeconds(long inversionSeconds) {
		this.inversionSecs = inversionSeconds;
		return this;
	}

	/**
	 * @param energyDelta        may be set to 0 to noop this method
	 * @param energyPercentDelta
	 * @param lookBackMins
	 * @return
	 */
	public NZSHM22_AbstractInversionRunner setEnergyChangeCompletionCriteria(double energyDelta,
			double energyPercentDelta, double lookBackMins) {
		if (energyDelta == 0.0d)
			return this;
		this.energyChangeCompletionCriteria = new EnergyChangeCompletionCriteria(energyDelta, energyPercentDelta,
				lookBackMins);
		return this;
	}

	/**
	 * Sets the length of time between inversion selections (syncs) in seconds. Default is 10 seconds.
	 * 
	 * @param syncInterval the interval in seconds.
	 * @return this runner.
	 */
	@Deprecated
	public NZSHM22_AbstractInversionRunner setSyncInterval(long syncInterval) {
		return setSelectionInterval(syncInterval);
	}

	/**
	 * Sets the number of threads per selector;
	 *
	 * NB total threads allocated  = (numSolutionAverages * numThreadsPerAvg)
	 *
	 * @param numThreads the number of threads per solution selector (which might also be an averaging thread).
	 * @return this runner.
	 */
	public NZSHM22_AbstractInversionRunner setNumThreadsPerSelector(Integer numThreads) {
		this.inversionThreadsPerSelector = numThreads;
		return this;
	}

	/**
	 * Sets the length of time between sub-solution selections. Default is 10 seconds.
	 *
	 * @param interval the interval in seconds.
	 * @return this runner.
	 */
	public NZSHM22_AbstractInversionRunner setSelectionInterval(long interval) {
		this.selectionInterval = interval;
		return this;
	}

	/**
	 * @param numSolutionAverages the number of inversionNumSolutionAverages
	 * @return
	 */
	public NZSHM22_AbstractInversionRunner setNumSolutionAverages(Integer numSolutionAverages) {
		this.inversionNumSolutionAverages = numSolutionAverages;
		return this;
	}

	/**
	 * Sets how long each averaging interval will be.
	 * 
	 * @param seconds the duration of the averaging period in seconds.
	 * @return this runner.
	 */
	public NZSHM22_AbstractInversionRunner setInversionAveragingIntervalSecs(Integer seconds) {
		this.inversionAveragingIntervalSecs = seconds;
		return this;
	}

	/**
	 * Set up inversion averaging with one method call;
	 *
	 * This will also determine the total threads allocated = (numSolutionAverages * numThreadsPerAvg)
	 *
	 * @param numSolutionAverages the number of parallel selectors to average over
	 * @param averagingIntervalSecs
	 * @return
	 */
	public NZSHM22_AbstractInversionRunner setInversionAveraging(Integer numSolutionAverages,  Integer averagingIntervalSecs) {
		this.inversionAveragingEnabled = true;
		this.setNumSolutionAverages(numSolutionAverages);
		this.setInversionAveragingIntervalSecs(averagingIntervalSecs);
		return this;
	}

	/**
	 * Enable/disable inversion averaging behaviour.
	 *
	 * @param enabled
	 * @return
	 */
	public NZSHM22_AbstractInversionRunner setInversionAveraging(boolean enabled) {
		this.inversionAveragingEnabled = enabled;
		return this;
	}

	/**
	 * @param perturbationFunction
	 * @return
	 */
	public NZSHM22_AbstractInversionRunner setPerturbationFunction(String perturbationFunction) {
		return setPerturbationFunction(GenerationFunctionType.valueOf(perturbationFunction));
	}

	/**
	 * configure the perturbation function
	 *
	 * @param perturbationFunction
	 * @return
	 */
	public NZSHM22_AbstractInversionRunner setPerturbationFunction(GenerationFunctionType perturbationFunction) {
		this.perturbationFunction = perturbationFunction;
		return this;
	}

	/**
	 * configure how Inversion treats values when they perturb < 0
	 *
	 * @param nonNegAlgorithm
	 * @return
	 */
	public NZSHM22_AbstractInversionRunner setNonnegativityConstraintType(String nonNegAlgorithm) {
		return this.setNonnegativityConstraintType(NonnegativityConstraintType.valueOf(nonNegAlgorithm));
	}

	/**
	 * @param nonNegAlgorithm
	 * @return
	 */
	public NZSHM22_AbstractInversionRunner setNonnegativityConstraintType(NonnegativityConstraintType nonNegAlgorithm) {
		this.nonNegAlgorithm = nonNegAlgorithm;
		return this;
	}

	public NZSHM22_AbstractInversionRunner setSpatialSeisPDF(NZSHM22_SpatialSeisPDF spatialSeisPDF){
		this.spatialSeisPDF = spatialSeisPDF;
		return this;
	}

	/**
	 * @param inputGen
	 * @return
	 */
	public NZSHM22_AbstractInversionRunner setInversionInputGenerator(InversionInputGenerator inputGen) {
		this.inversionInputGenerator = inputGen;
		return this;
	}

	public InversionInputGenerator getInversionInputGenerator(){
		return inversionInputGenerator;
	}

	public NZSHM22_AbstractInversionRunner setRuptureSetFile(String ruptureSetFileName)
			throws IOException, DocumentException {
		File rupSetFile = new File(ruptureSetFileName);
		this.setRuptureSetFile(rupSetFile);
		return this;
	}

	/**
	 * Sets the FaultModel file
	 *
	 * @param ruptureSetFile the rupture file
	 * @return this builder
	 */
	public NZSHM22_AbstractInversionRunner setRuptureSetFile(File ruptureSetFile) {
		this.rupSetFile = ruptureSetFile;
		return this;
	}

	protected void setupLTB(NZSHM22_LogicTreeBranch branch){
		if (scalingRelationship != null) {
			branch.clearValue(NZSHM22_ScalingRelationshipNode.class);
			branch.setValue(scalingRelationship);
		}
		if (deformationModel != null) {
			branch.setValue(deformationModel);
		}
		if(spatialSeisPDF != null){
			branch.clearValue(NZSHM22_SpatialSeisPDF.class);
			branch.setValue(spatialSeisPDF);
		}
	}

	public NZSHM22_AbstractInversionRunner setDeformationModel(String modelName){
		this.deformationModel = NZSHM22_DeformationModel.valueOf(modelName);
		return this;
	}

	/**
	 * @param mfdEqualityConstraintWt
	 * @param mfdInequalityConstraintWt
	 * @return
	 */
	public NZSHM22_AbstractInversionRunner setGutenbergRichterMFDWeights(double mfdEqualityConstraintWt,
			double mfdInequalityConstraintWt) {
		this.mfdEqualityConstraintWt = mfdEqualityConstraintWt;
		this.mfdInequalityConstraintWt = mfdInequalityConstraintWt;
		return this;
	}

	public NZSHM22_AbstractInversionRunner setUncertaintyWeightedMFDWeights(double mfdUncertaintyWeightedConstraintWt,
			double mfdUncertaintyWeightedConstraintPower) {
		this.mfdUncertaintyWeightedConstraintWt = mfdUncertaintyWeightedConstraintWt;
		this.mfdUncertaintyWeightedConstraintPower = mfdUncertaintyWeightedConstraintPower;
		return this;
	}	
	
	/**
	 * UCERF3-style Slip rate constraint
	 * 
	 * If normalized, slip rate misfit is % difference for each section (recommended
	 * since it helps fit slow-moving faults). If unnormalized, misfit is absolute
	 * difference. BOTH includes both normalized and unnormalized constraints.
	 * 
	 * @param weightingType  a value from
	 *                       UCERF3InversionConfiguration.SlipRateConstraintWeightingType
	 * @param normalizedWt
	 * @param unnormalizedWt
	 * @throws IllegalArgumentException if the weighting types is not supported by
	 *                                  this constraint
	 * @return
	 */
	public NZSHM22_AbstractInversionRunner setSlipRateConstraint(AbstractInversionConfiguration.NZSlipRateConstraintWeightingType weightingType,
																 double normalizedWt, double unnormalizedWt) {
		Preconditions.checkArgument(weightingType != AbstractInversionConfiguration.NZSlipRateConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY,
				"setSlipRateConstraint() using  %s is not supported. Use setSlipRateUncertaintyConstraint() instead.",
				weightingType);
		this.slipRateWeightingType = weightingType;
		this.slipRateConstraintWt_normalized = normalizedWt;
		this.slipRateConstraintWt_unnormalized = unnormalizedWt;
		return this;
	}

	public NZSHM22_AbstractInversionRunner setScalingRelationship(String scalingRelationship, boolean recalcMags){
		return setScalingRelationship(NZSHM22_ScalingRelationshipNode.createRelationShip(scalingRelationship), recalcMags);
	}

	public NZSHM22_AbstractInversionRunner setScalingRelationship(RupSetScalingRelationship scalingRelationship, boolean recalcMags){
		this.scalingRelationship = new NZSHM22_ScalingRelationshipNode();
		this.scalingRelationship.setScalingRelationship(scalingRelationship);
		this.scalingRelationship.setRecalc(recalcMags);
		return this;
	}

	/**
	 * UCERF3-style Slip rate constraint
	 * 
	 * @param weightingType  a string value from
	 *                       UCERF3InversionConfiguration.SlipRateConstraintWeightingType
	 * @param normalizedWt
	 * @param unnormalizedWt
	 * @throws IllegalArgumentException if the weighting types is not supported by
	 *                                  this constraint
	 * @return
	 */
	public NZSHM22_AbstractInversionRunner setSlipRateConstraint(String weightingType, double normalizedWt,
			double unnormalizedWt) {
		AbstractInversionConfiguration.NZSlipRateConstraintWeightingType weighting;
		if(weightingType.equalsIgnoreCase("UNCERTAINTY_ADJUSTED")){ // backwards compatibility
			weighting = AbstractInversionConfiguration.NZSlipRateConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY;
		} else {
			weighting = AbstractInversionConfiguration.NZSlipRateConstraintWeightingType.valueOf(weightingType);
		}
		return setSlipRateConstraint(weighting, normalizedWt,
				unnormalizedWt);
	}

	public void validateConfig() {
		Preconditions.checkState(scalingRelationship.getScalingRelationship() != null, "ScalingRelationship must be set");

		FaultRegime regime = rupSet.getModule(NZSHM22_LogicTreeBranch.class).getValue(FaultRegime.class);
		FaultRegime scalingRegime = scalingRelationship.getRegime();
		Preconditions.checkState(regime == scalingRegime, "Regime of rupture set and scaling relationship do not match.");
	}

	/**
	 * Runs the inversion on the specified rupture set.
	 * 
	 * @return the FaultSystemSolution.
	 * @throws IOException
	 * @throws DocumentException
	 */
	public FaultSystemSolution runInversion() throws IOException, DocumentException {

		configure();
		validateConfig();

		// weight of entropy-maximization constraint (not used in UCERF3)
//		double smoothnessWt = 0;

		inversionInputGenerator.generateInputs(true);
		// column compress it for fast annealing
		inversionInputGenerator.columnCompress();

		// inversion completion criteria (how long it will run)
		this.completionCriterias.add(TimeCompletionCriteria.getInSeconds(inversionSecs));
		if (!(this.energyChangeCompletionCriteria == null))
			this.completionCriterias.add(this.energyChangeCompletionCriteria);

		CompletionCriteria completionCriteria = new CompoundCompletionCriteria(this.completionCriterias);

		// Bring up window to track progress
		// criteria = new ProgressTrackingCompletionCriteria(criteria, progressReport,
		// 0.1d);
		// ....
		ProgressTrackingCompletionCriteria progress = new ProgressTrackingCompletionCriteria(completionCriteria);

		// this is the "sub completion criteria" - the amount of time (or iterations)
		// between solution selection/synchronization
		CompletionCriteria subCompletionCriteria = TimeCompletionCriteria.getInSeconds(selectionInterval);

		initialState = inversionInputGenerator.getInitialSolution();

		int numThreads = this.inversionNumSolutionAverages * this.inversionThreadsPerSelector;

		if (this.inversionAveragingEnabled) {
			Preconditions.checkState(inversionThreadsPerSelector <= numThreads);

			CompletionCriteria avgSubCompletionCriteria = TimeCompletionCriteria.getInSeconds(this.inversionAveragingIntervalSecs);

			int threadsLeft = numThreads;

			// arrange lower-level (actual worker) SAs
			List<SimulatedAnnealing> tsas = new ArrayList<>();
			while (threadsLeft > 0) {
				int myThreads = Integer.min(threadsLeft, inversionThreadsPerSelector);
				if (myThreads > 1)
					tsas.add(new ThreadedSimulatedAnnealing(inversionInputGenerator.getA(), inversionInputGenerator.getD(),
							inversionInputGenerator.getInitialSolution(), 0d, inversionInputGenerator.getA_ineq(), inversionInputGenerator.getD_ineq(),
							myThreads, subCompletionCriteria));
				else
					tsas.add(new SerialSimulatedAnnealing(inversionInputGenerator.getA(), inversionInputGenerator.getD(),
							inversionInputGenerator.getInitialSolution(), 0d, inversionInputGenerator.getA_ineq(), inversionInputGenerator.getD_ineq()));
				threadsLeft -= myThreads;
			}
			tsa = new ThreadedSimulatedAnnealing(tsas, avgSubCompletionCriteria);
			tsa.setAverage(true);
		} else {
			tsa = new ThreadedSimulatedAnnealing(inversionInputGenerator.getA(), inversionInputGenerator.getD(),
					inversionInputGenerator.getInitialSolution(), 0d, inversionInputGenerator.getA_ineq(), inversionInputGenerator.getD_ineq(),
					numThreads, subCompletionCriteria);
		}
		progress.setConstraintRanges(inversionInputGenerator.getConstraintRowRanges());
		tsa.setConstraintRanges(inversionInputGenerator.getConstraintRowRanges());
		tsa.setRandom(new Random(1)); // this removes non-repeatable randomness
//		tsa.setRuptureSampler(null);
		tsa.setPerturbationFunc(perturbationFunction);
		tsa.setNonnegativeityConstraintAlgorithm(nonNegAlgorithm);

		// From CLI metadata Analysis
		initialState = Arrays.copyOf(initialState, initialState.length);

		tsa.iterate(progress);

		tsa.shutdown();

		// now assemble the solution
		double[] solution_raw = tsa.getBestSolution();

		// adjust for minimum rates if applicable
		double[] solution_adjusted = inversionInputGenerator.adjustSolutionForWaterLevel(solution_raw);

//		Map<ConstraintRange, Double> energies = tsa.getEnergies();
//		if (energies != null) {
//			System.out.println("Final energies:");
//			for (ConstraintRange range : energies.keySet()) {
//				finalEnergies.put(range.name, (double) energies.get(range).floatValue());
//				System.out.println("\t" + range.name + ": " + energies.get(range).floatValue());
//			}
//		}

		solution = new FaultSystemSolution(rupSet, solution_adjusted);
		solution.addModule(progress.getProgress());
		return solution;
	}

	@SuppressWarnings("deprecation")
	public Map<String, String> getSolutionMetrics() {
		Map<String, String> metrics = new HashMap<String, String>();

		// Completion
//		long numPerturbs = pComp.getPerturbs().get(pComp.getPerturbs().size() - 1);
		int numRups = initialState.length;

//		metrics.put("total_perturbations", Long.toString(numPerturbs));
		metrics.put("total_ruptures", Integer.toString(numRups));

		int rupsPerturbed = 0;
		double[] solution_no_min_rates = tsa.getBestSolution();
		int numAboveWaterlevel = 0;
		for (int i = 0; i < numRups; i++) {
			if ((float) solution_no_min_rates[i] != (float) initialState[i])
				rupsPerturbed++;
			if (solution_no_min_rates[i] > 0)
				numAboveWaterlevel++;
		}

		metrics.put("perturbed_ruptures", Integer.toString(rupsPerturbed));
//		metrics.put("avg_perturbs_per_pertubed_rupture",
//				new Double((double) numPerturbs / (double) rupsPerturbed).toString());
//		metrics.put("ruptures_above_water_level_ratio",
//				new Double((double) numAboveWaterlevel / (double) numRups).toString());

		for (String range : finalEnergies.keySet()) {
			String metric_name = "final_energy_" + range.replaceAll("\\s+", "_").toLowerCase();
			System.out.println(metric_name + " : " + finalEnergies.get(range).toString());
			metrics.put(metric_name, finalEnergies.get(range).toString());
		}

		return metrics;
	}

//	public String completionCriteriaMetrics() {
//		String info = "";
//		ProgressTrackingCompletionCriteria pComp = (ProgressTrackingCompletionCriteria) completionCriteria;
//		long numPerturbs = pComp.getPerturbs().get(pComp.getPerturbs().size() - 1);
//		int numRups = initialState.length;
//		info += "\nAvg Perturbs Per Rup: " + numPerturbs + "/" + numRups + " = "
//				+ ((double) numPerturbs / (double) numRups);
//		int rupsPerturbed = 0;
//		double[] solution_no_min_rates = tsa.getBestSolution();
//		int numAboveWaterlevel = 0;
//		for (int i = 0; i < numRups; i++) {
//			if ((float) solution_no_min_rates[i] != (float) initialState[i])
//				rupsPerturbed++;
//			if (solution_no_min_rates[i] > 0)
//				numAboveWaterlevel++;
//		}
//		info += "\nNum rups actually perturbed: " + rupsPerturbed + "/" + numRups + " ("
//				+ (float) (100d * ((double) rupsPerturbed / (double) numRups)) + " %)";
//		info += "\nAvg Perturbs Per Perturbed Rup: " + numPerturbs + "/" + rupsPerturbed + " = "
//				+ ((double) numPerturbs / (double) rupsPerturbed);
//		info += "\nNum rups above waterlevel: " + numAboveWaterlevel + "/" + numRups + " ("
//				+ (float) (100d * ((double) numAboveWaterlevel / (double) numRups)) + " %)";
//		info += "\n";
//		return info;
//	}

//	public String momentAndRateMetrics() {
//		String info = "";
//		// add moments to info string
//		info += "\n\n****** Moment and Rupture Rate Metatdata ******";
//		info += "\nNum Ruptures: " + rupSet.getNumRuptures();
//		int numNonZeros = 0;
//		for (double rate : solution.getRateForAllRups())
//			if (rate != 0)
//				numNonZeros++;
//
//		float percent = (float) numNonZeros / rupSet.getNumRuptures() * 100f;
//		info += "\nNum Non-Zero Rups: " + numNonZeros + "/" + rupSet.getNumRuptures() + " (" + percent + " %)";
//		info += "\nOrig (creep reduced) Fault Moment Rate: " + rupSet.getTotalOrigMomentRate();
//
//		double momRed = rupSet.getTotalMomentRateReduction();
//		info += "\nMoment Reduction (for subseismogenic ruptures only): " + momRed;
//		info += "\nSubseismo Moment Reduction Fraction (relative to creep reduced): "
//				+ rupSet.getTotalMomentRateReductionFraction();
//		info += "\nFault Target Supra Seis Moment Rate (subseismo and creep reduced): "
//				+ rupSet.getTotalReducedMomentRate();
//
//		double totalSolutionMoment = solution.getTotalFaultSolutionMomentRate();
//		info += "\nFault Solution Supra Seis Moment Rate: " + totalSolutionMoment;
//
//		/*
//		 * TODO : Matt, are these useful in NSHM ?? Priority ??
//		 */
////		info += "\nFault Target Sub Seis Moment Rate: "
////				+rupSet.getInversionTargetMFDs().getTotalSubSeismoOnFaultMFD().getTotalMomentRate();
////		info += "\nFault Solution Sub Seis Moment Rate: "
////				+solution.getFinalTotalSubSeismoOnFaultMFD().getTotalMomentRate();
////		info += "\nTruly Off Fault Target Moment Rate: "
////				+rupSet.getInversionTargetMFDs().getTrulyOffFaultMFD().getTotalMomentRate();
////		info += "\nTruly Off Fault Solution Moment Rate: "
////				+solution.getFinalTrulyOffFaultMFD().getTotalMomentRate();
////		
////		try {
////			//					double totalOffFaultMomentRate = invSol.getTotalOffFaultSeisMomentRate(); // TODO replace - what is off fault moment rate now?
////			//					info += "\nTotal Off Fault Seis Moment Rate (excluding subseismogenic): "
////			//							+(totalOffFaultMomentRate-momRed);
////			//					info += "\nTotal Off Fault Seis Moment Rate (inluding subseismogenic): "
////			//							+totalOffFaultMomentRate;
////			info += "\nTotal Moment Rate From Off Fault MFD: "+solution.getFinalTotalGriddedSeisMFD().getTotalMomentRate();
////			//					info += "\nTotal Model Seis Moment Rate: "
////			//							+(totalOffFaultMomentRate+totalSolutionMoment);
////		} catch (Exception e1) {
////			e1.printStackTrace();
////			System.out.println("WARNING: InversionFaultSystemSolution could not be instantiated!");
////		}		
//
//		info += "\n";
//		return info;
//	}

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

//	public String parentFaultMomentRates() {
//		// parent fault moment rates
//		String info = "";
//		ArrayList<CommandLineInversionRunner.ParentMomentRecord> parentMoRates = CommandLineInversionRunner
//				.getSectionMoments((SlipEnabledSolution) solution);
//		info += "\n\n****** Larges Moment Rate Discrepancies ******";
//		for (int i = 0; i < 10 && i < parentMoRates.size(); i++) {
//			CommandLineInversionRunner.ParentMomentRecord p = parentMoRates.get(i);
//			info += "\n" + p.parentID + ". " + p.name + "\ttarget: " + p.targetMoment + "\tsolution: "
//					+ p.solutionMoment + "\tdiff: " + p.getDiff();
//		}
//		info += "\n";
//		return info;
//	}

	public List<IncrementalMagFreqDist> getSolutionMfds() {
		return solutionMfds;
	}

	/**
	 * build an MFD from the inversion solution
	 * 
	 * @param rateWeighted if false, returns the count of ruptures by magnitude,
	 *                     irrespective of rate.
	 * @return
	 */
	public HistogramFunction solutionMagFreqHistogram( boolean rateWeighted ) {
	
		ClusterRuptures cRups = solution.getRupSet().getModule(ClusterRuptures.class);

		HistScalarValues scalarVals = new HistScalarValues(HistScalar.MAG,
				solution.getRupSet(), solution, cRups.getAll(), null);
	
		MinMaxAveTracker track = new MinMaxAveTracker();
		List<Integer> includeIndexes = new ArrayList<>();
		for (int r = 0; r < scalarVals.getRupSet().getNumRuptures(); r++)
			includeIndexes.add(r);
		for (int r : includeIndexes)
			track.addValue(scalarVals.getValues().get(r));

		HistScalar histScalar = scalarVals.getScalar();
		HistogramFunction histogram = histScalar.getHistogram(track);
		boolean logX = histScalar.isLogX();

		for (int i = 0; i < includeIndexes.size(); i++) {
			int rupIndex = includeIndexes.get(i);
			double scalar = scalarVals.getValues().get(i);
			double y = rateWeighted ? scalarVals.getSol().getRateForRup(rupIndex) : 1;
			int index;
			if (logX)
				index = scalar <= 0 ? 0 : histogram.getClosestXIndex(Math.log10(scalar));
			else
				index = histogram.getClosestXIndex(scalar);
			histogram.add(index, y);
		}
		return histogram;
	}

	private void appendMfdRows(EvenlyDiscretizedFunc mfd, ArrayList<ArrayList<String>> rows, int series) {
		ArrayList<String> row;
		for (int i = 0; i < mfd.size(); i++) {
			row = new ArrayList<String>();
			if (mfd.getY(i) > 0) {
				row.add(Integer.toString(series));
				row.add(mfd.getName());
				row.add(Double.toString(Precision.round(mfd.getX(i), 2)));
				row.add(Double.toString(mfd.getY(i)));
				rows.add(row);
			}
		}
	}

	public ArrayList<ArrayList<String>> getTabularSolutionMfds() {
		ArrayList<ArrayList<String>> rows = new ArrayList<ArrayList<String>>();

		int series = 0;
		for (IncrementalMagFreqDist mfd : getSolutionMfds()) {
			appendMfdRows(mfd, rows, series);
			series++;
		}

		HistogramFunction magHist = solutionMagFreqHistogram(true);
		magHist.setName("solutionMFD_rateWeighted");
		appendMfdRows(magHist, rows, series);
		series++;

		magHist = solutionMagFreqHistogram(false);
		magHist.setName("solutionMFD_unweighted");
		appendMfdRows(magHist, rows, series);

		return rows;

	}

}

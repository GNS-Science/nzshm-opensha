package nz.cri.gns.NZSHM22.opensha.inversion;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;

import org.apache.commons.math3.util.Precision;
import org.dom4j.DocumentException;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionInputGenerator;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupSetDiagnosticsPageGen.HistScalar;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupSetDiagnosticsPageGen.HistScalarValues;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;


import scratch.UCERF3.U3FaultSystemRupSet;
import scratch.UCERF3.analysis.FaultSystemRupSetCalc;
import scratch.UCERF3.inversion.UCERF3InversionConfiguration.SlipRateConstraintWeightingType;

import scratch.UCERF3.logicTree.U3LogicTreeBranch;
import scratch.UCERF3.simulatedAnnealing.SerialSimulatedAnnealing;
import scratch.UCERF3.simulatedAnnealing.SimulatedAnnealing;
import scratch.UCERF3.simulatedAnnealing.ThreadedSimulatedAnnealing;
import scratch.UCERF3.simulatedAnnealing.completion.CompletionCriteria;
import scratch.UCERF3.simulatedAnnealing.completion.CompoundCompletionCriteria;
import scratch.UCERF3.simulatedAnnealing.completion.EnergyChangeCompletionCriteria;
import scratch.UCERF3.simulatedAnnealing.completion.ProgressTrackingCompletionCriteria;
import scratch.UCERF3.simulatedAnnealing.completion.TimeCompletionCriteria;
import scratch.UCERF3.simulatedAnnealing.params.GenerationFunctionType;
import scratch.UCERF3.utils.U3FaultSystemIO;

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
	
	protected NZSHM22_InversionFaultSystemRuptSet rupSet = null;
	protected List<InversionConstraint> constraints = new ArrayList<>();
	protected List<CompletionCriteria> completionCriterias = new ArrayList<>();
	private EnergyChangeCompletionCriteria energyChangeCompletionCriteria = null;

	private CompletionCriteria completionCriteria;
	private ThreadedSimulatedAnnealing tsa;
	private double[] initialState;
	private FaultSystemSolution solution;

	private Map<String, Double> finalEnergies = new HashMap<String, Double>();
	private InversionInputGenerator inversionInputGenerator;

	protected List<IncrementalMagFreqDist> solutionMfds;

	protected SlipRateConstraintWeightingType slipRateWeightingType;
	protected double slipRateConstraintWt_normalized;
	protected double slipRateConstraintWt_unnormalized;
	protected double mfdEqualityConstraintWt;
	protected double mfdInequalityConstraintWt;


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
	 * @param selectionInterval the interval in seconds.
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
	 * @param selectionInterval the interval in seconds.
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
	 * @param inputGen
	 * @return
	 */
	public NZSHM22_AbstractInversionRunner setInversionInputGenerator(InversionInputGenerator inputGen) {
		this.inversionInputGenerator = inputGen;
		return this;
	}

	public NZSHM22_AbstractInversionRunner setRuptureSetFile(String ruptureSetFileName)
			throws IOException, DocumentException {
		File rupSetFile = new File(ruptureSetFileName);
		this.setRuptureSetFile(rupSetFile);
		return this;
	}

	public static NZSHM22_InversionFaultSystemRuptSet loadRupSet(File ruptureSetFile) throws DocumentException, IOException {
		U3FaultSystemRupSet rupSetA = U3FaultSystemIO.loadRupSet(ruptureSetFile);
		U3LogicTreeBranch branch = U3LogicTreeBranch.DEFAULT;

		return new NZSHM22_InversionFaultSystemRuptSet(rupSetA, branch);
	}

	/**
	 * Sets the FaultModel file
	 *
	 * @param ruptureSetFile the rupture file
	 * @return this builder
	 * @throws DocumentException
	 * @throws IOException
	 */
	public NZSHM22_AbstractInversionRunner setRuptureSetFile(File ruptureSetFile)
			throws IOException, DocumentException {
		this.rupSet = loadRupSet(ruptureSetFile);
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
	public NZSHM22_AbstractInversionRunner setSlipRateConstraint(SlipRateConstraintWeightingType weightingType,
			double normalizedWt, double unnormalizedWt) {
		Preconditions.checkArgument(weightingType != SlipRateConstraintWeightingType.UNCERTAINTY_ADJUSTED,
				"setSlipRateConstraint() using  %s is not supported. Use setSlipRateUncertaintyConstraint() instead.",
				weightingType);
		this.slipRateWeightingType = weightingType;
		this.slipRateConstraintWt_normalized = normalizedWt;
		this.slipRateConstraintWt_unnormalized = unnormalizedWt;
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
		return setSlipRateConstraint(SlipRateConstraintWeightingType.valueOf(weightingType), normalizedWt, unnormalizedWt);
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

		configure();

		// weight of entropy-maximization constraint (not used in UCERF3)
//		double smoothnessWt = 0;

		inversionInputGenerator.generateInputs(true);
		// column compress it for fast annealing
		inversionInputGenerator.columnCompress();

		// inversion completion criteria (how long it will run)
		this.completionCriterias.add(TimeCompletionCriteria.getInSeconds(inversionSecs));
		if (!(this.energyChangeCompletionCriteria == null))
			this.completionCriterias.add(this.energyChangeCompletionCriteria);

		completionCriteria = new CompoundCompletionCriteria(this.completionCriterias);

		// Bring up window to track progress
		// criteria = new ProgressTrackingCompletionCriteria(criteria, progressReport,
		// 0.1d);
		// ....
		completionCriteria = new ProgressTrackingCompletionCriteria(completionCriteria);

		// this is the "sub completion criteria" - the amount of time (or iterations)
		// between solution selection/synchronization
		CompletionCriteria subCompletionCriteria = TimeCompletionCriteria.getInSeconds(selectionInterval);

		initialState = inversionInputGenerator.getInitialSolution();

		int numThreads = this.inversionNumSolutionAverages * this.inversionThreadsPerSelector;
		
		if (this.inversionAveragingEnabled) {
			Preconditions.checkState(inversionThreadsPerSelector < numThreads);
			
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
			
		tsa.setConstraintRanges(inversionInputGenerator.getConstraintRowRanges());
		tsa.setRandom(new Random(1));
		tsa.setRuptureSampler(null);
		tsa.setPerturbationFunc(GenerationFunctionType.UNIFORM_NO_TEMP_DEPENDENCE);

		// From CLI metadata Analysis
		initialState = Arrays.copyOf(initialState, initialState.length);

		tsa.iterate(completionCriteria);

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
		return solution;
	}

	@SuppressWarnings("deprecation")
	public Map<String, String> getSolutionMetrics() {
		Map<String, String> metrics = new HashMap<String, String>();

		// Completion
		ProgressTrackingCompletionCriteria pComp = (ProgressTrackingCompletionCriteria) completionCriteria;
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

	protected abstract NZSHM22_AbstractInversionRunner configure();

	public List<IncrementalMagFreqDist> getSolutionMfds() {
		return solutionMfds;
	}


	/**
	 * build an MFD from the inversion solution
	 * 
	 * @param rateWeighted if false, returns the count of ruptures by magnitude, irrespective of rate.
	 * @return
	 */
	public HistogramFunction solutionMagFreqHistogram( boolean rateWeighted ) {
	
		ClusterRuptures cRups = solution.getRupSet().getModule(ClusterRuptures.class);
		
		HistScalarValues scalarVals = new HistScalarValues(HistScalar.MAG, 
				solution.getRupSet(), solution, cRups.getAll(), null);
	
		MinMaxAveTracker track = new MinMaxAveTracker();
		List<Integer> includeIndexes = new ArrayList<>();
		for (int r=0; r<scalarVals.getRupSet().getNumRuptures(); r++)
			includeIndexes.add(r);
		for (int r : includeIndexes)
			track.addValue(scalarVals.getValues().get(r)); 

		HistScalar histScalar = scalarVals.getScalar();
		HistogramFunction histogram = histScalar.getHistogram(track);
		boolean logX = histScalar.isLogX();

		for (int i=0; i<includeIndexes.size(); i++) {
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
		for (int i=0; i<mfd.size(); i++ ) {
			row = new ArrayList<String>();
			if (mfd.getY(i) > 0)  {
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
	
//		// CBC: HACK ALERT 
//		// stole this from ReportPageGen.attachDefaultModules method
//		FaultSystemRupSet rupSet = solution.getRupSet();
//		SectionDistanceAzimuthCalculator distAzCalc = rupSet.getModule(SectionDistanceAzimuthCalculator.class);
////		PlausibilityConfiguration config = rupSet.getModule(PlausibilityConfiguration.class);
//
//		if (distAzCalc == null) {
//			distAzCalc = new SectionDistanceAzimuthCalculator(rupSet.getFaultSectionDataList());
//			rupSet.addModule(distAzCalc);
//		}
//		
//		if (!rupSet.hasModule(RuptureConnectionSearch.class))
//			rupSet.addModule(new RuptureConnectionSearch(rupSet, distAzCalc, 100d, false));
//
//		if (!rupSet.hasAvailableModule(ClusterRuptures.class)) {
//			rupSet.addAvailableModule(new Callable<ClusterRuptures>() {
//
//				@Override
//				public ClusterRuptures call() throws Exception {
//					return ClusterRuptures.instance(rupSet, rupSet.requireModule(RuptureConnectionSearch.class));
//				}
//				
//			}, ClusterRuptures.class);
//		}		
		
		int series = 0;
		for(IncrementalMagFreqDist mfd : getSolutionMfds()) {
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

		
//		for (int i=0; i<magHist.size(); i++ ) {
//			row = new ArrayList<String>();
//			if (magHist.getY(i) > 0) { 
////				System.out.println(series + ", " + magHist.getName() + ", " +  Precision.round(magHist.getX(i), 2) + ", " + magHist.getY(i));
////	
////				row.add(Integer.toString(series));
////				row.add(magHist.getName());
////				row.add(Double.toString(Precision.round(magHist.getX(i), 2)));
////				row.add(Double.toString(magHist.getY(i)));
////				rows.add(row);
//
//			}		
//		}
		
		return rows;
	
	}
	
}

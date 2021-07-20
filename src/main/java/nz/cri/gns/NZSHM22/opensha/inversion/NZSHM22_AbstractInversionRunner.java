package nz.cri.gns.NZSHM22.opensha.inversion;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.util.Precision;
import org.dom4j.DocumentException;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionInputGenerator;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupSetDiagnosticsPageGen.HistScalar;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupSetDiagnosticsPageGen.HistScalarValues;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.SlipEnabledSolution;
import scratch.UCERF3.analysis.FaultSystemRupSetCalc;
import scratch.UCERF3.inversion.CommandLineInversionRunner;
import scratch.UCERF3.inversion.UCERF3InversionConfiguration.SlipRateConstraintWeightingType;
import scratch.UCERF3.logicTree.LogicTreeBranch;
import scratch.UCERF3.simulatedAnnealing.ConstraintRange;
import scratch.UCERF3.simulatedAnnealing.ThreadedSimulatedAnnealing;
import scratch.UCERF3.simulatedAnnealing.completion.CompletionCriteria;
import scratch.UCERF3.simulatedAnnealing.completion.CompoundCompletionCriteria;
import scratch.UCERF3.simulatedAnnealing.completion.EnergyChangeCompletionCriteria;
import scratch.UCERF3.simulatedAnnealing.completion.ProgressTrackingCompletionCriteria;
import scratch.UCERF3.simulatedAnnealing.completion.TimeCompletionCriteria;
import scratch.UCERF3.utils.FaultSystemIO;

/**
 * @author chrisbc
 *
 */
public abstract class NZSHM22_AbstractInversionRunner {

	protected long inversionSecs = 60;
	protected long syncInterval = 10;
	protected int numThreads = Runtime.getRuntime().availableProcessors();
	protected NZSHM22_InversionFaultSystemRuptSet rupSet = null;
	protected List<InversionConstraint> constraints = new ArrayList<>();
	protected List<CompletionCriteria> completionCriterias = new ArrayList<>();
	private EnergyChangeCompletionCriteria energyChangeCompletionCriteria = null;

	private CompletionCriteria completionCriteria;
	private ThreadedSimulatedAnnealing tsa;
	private double[] initialState;
	private NZSHM22_InversionFaultSystemSolution solution;

	private Map<String, Double> finalEnergies = new HashMap<String, Double>();
	private InversionInputGenerator inversionInputGenerator;

	protected List<IncrementalMagFreqDist> solutionMfds;
	/*
	 * Sliprate constraint default settings
	 */
	// If normalized, slip rate misfit is % difference for each section (recommended
	// since it helps fit slow-moving faults).
	// If unnormalized, misfit is absolute difference.
	// BOTH includes both normalized and unnormalized constraints.
	protected SlipRateConstraintWeightingType slipRateWeightingType;// = SlipRateConstraintWeightingType.BOTH; //
																	// (recommended:
																	// BOTH)
	// For SlipRateConstraintWeightingType.NORMALIZED (also used for
	// SlipRateConstraintWeightingType.BOTH) -- NOT USED if UNNORMALIZED!
	protected double slipRateConstraintWt_normalized;
	// For SlipRateConstraintWeightingType.UNNORMALIZED (also used for
	// SlipRateConstraintWeightingType.BOTH) -- NOT USED if NORMALIZED!
	protected double slipRateConstraintWt_unnormalized;

	protected double mfdEqualityConstraintWt; // = 10;
	protected double mfdInequalityConstraintWt;// = 1000;

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
	 * Sets how many minutes the inversion runs for. Default is 60 seconds.
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
	 * Sets the length of time between syncs in seconds. Default is 10 seconds.
	 * 
	 * @param syncInterval the interval in seconds.
	 * @return this runner.
	 */
	public NZSHM22_AbstractInversionRunner setSyncInterval(long syncInterval) {
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
	public NZSHM22_AbstractInversionRunner setNumThreads(int numThreads) {
		this.numThreads = numThreads;
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
		FaultSystemRupSet rupSetA = FaultSystemIO.loadRupSet(ruptureSetFile);
		LogicTreeBranch branch = (LogicTreeBranch) LogicTreeBranch.DEFAULT;

		this.rupSet = new NZSHM22_InversionFaultSystemRuptSet(rupSetA, branch);
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
	public NZSHM22_InversionFaultSystemSolution runInversion() throws IOException, DocumentException {

		// weight of entropy-maximization constraint (not used in UCERF3)
		double smoothnessWt = 0;

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
		// between synchronization
		CompletionCriteria subCompletionCriteria = TimeCompletionCriteria.getInSeconds(syncInterval); // 1 second;

		initialState = inversionInputGenerator.getInitialSolution();

		tsa = new ThreadedSimulatedAnnealing(inversionInputGenerator.getA(), inversionInputGenerator.getD(),
				initialState, smoothnessWt, inversionInputGenerator.getA_ineq(), inversionInputGenerator.getD_ineq(),
				inversionInputGenerator.getWaterLevelRates(), numThreads, subCompletionCriteria);
		tsa.setConstraintRanges(inversionInputGenerator.getConstraintRowRanges());

		// From CLI metadata Analysis
		initialState = Arrays.copyOf(initialState, initialState.length);

		tsa.iterate(completionCriteria);

		// now assemble the solution
		double[] solution_raw = tsa.getBestSolution();

		// adjust for minimum rates if applicable
		double[] solution_adjusted = inversionInputGenerator.adjustSolutionForWaterLevel(solution_raw);

		Map<ConstraintRange, Double> energies = tsa.getEnergies();
		if (energies != null) {
			System.out.println("Final energies:");
			for (ConstraintRange range : energies.keySet()) {
				finalEnergies.put(range.name, (double) energies.get(range).floatValue());
				System.out.println("\t" + range.name + ": " + energies.get(range).floatValue());
			}
		}

		// TODO, do we really do want to store the config and energies now?
		solution = new NZSHM22_InversionFaultSystemSolution(rupSet, solution_adjusted, finalEnergies); // , null,
																										// energies);
		return solution;
	}

	@SuppressWarnings("deprecation")
	public Map<String, String> getSolutionMetrics() {
		Map<String, String> metrics = new HashMap<String, String>();

		// Completion
		ProgressTrackingCompletionCriteria pComp = (ProgressTrackingCompletionCriteria) completionCriteria;
		long numPerturbs = pComp.getPerturbs().get(pComp.getPerturbs().size() - 1);
		int numRups = initialState.length;

		metrics.put("total_perturbations", Long.toString(numPerturbs));
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
		metrics.put("avg_perturbs_per_pertubed_rupture",
				new Double((double) numPerturbs / (double) rupsPerturbed).toString());
		metrics.put("ruptures_above_water_level_ratio",
				new Double((double) numAboveWaterlevel / (double) numRups).toString());

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
	
		HistScalarValues scalarVals = new HistScalarValues(HistScalar.MAG, 
				solution.getRupSet(), solution, solution.getRupSet().getClusterRuptures(), null);
	
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

/*
 * RATE weighted
 * 
[4, solutionMFD_rateWeighted, 6.05, 0.035982715383508626]
[4, solutionMFD_rateWeighted, 6.15, 0.029641386422769787]
[4, solutionMFD_rateWeighted, 6.25, 0.030254034701153225]
[4, solutionMFD_rateWeighted, 6.35, 0.0306043216243599]
[4, solutionMFD_rateWeighted, 6.45, 0.024322684650372385]
[4, solutionMFD_rateWeighted, 6.55, 0.022158879000868686]
[4, solutionMFD_rateWeighted, 6.65, 0.021379133503583305]
[4, solutionMFD_rateWeighted, 6.75, 0.018568259862133084]
[4, solutionMFD_rateWeighted, 6.85, 0.014105723560756614]
[4, solutionMFD_rateWeighted, 6.95, 0.011291153244683508]
[4, solutionMFD_rateWeighted, 7.05, 0.008976383196834173]
[4, solutionMFD_rateWeighted, 7.15, 0.005540971154343004]
[4, solutionMFD_rateWeighted, 7.25, 0.00379557117014126]
[4, solutionMFD_rateWeighted, 7.35, 0.0028662651520705035]
[4, solutionMFD_rateWeighted, 7.45, 0.002246617621018292]
[4, solutionMFD_rateWeighted, 7.55, 0.0017322019125362471]
[4, solutionMFD_rateWeighted, 7.65, 0.0011151028046624063]
[4, solutionMFD_rateWeighted, 7.75, 7.926473172138736E-4]
[4, solutionMFD_rateWeighted, 7.85, 7.949262044675706E-4]
[4, solutionMFD_rateWeighted, 7.95, 8.087707235396194E-4]
[4, solutionMFD_rateWeighted, 8.05, 5.323763934074708E-4]

[4, solutionMFD_rateWeighted, 6.05, 402.0]
[4, solutionMFD_rateWeighted, 6.15, 352.0]
[4, solutionMFD_rateWeighted, 6.25, 438.0]
[4, solutionMFD_rateWeighted, 6.35, 536.0]
[4, solutionMFD_rateWeighted, 6.45, 667.0]
[4, solutionMFD_rateWeighted, 6.55, 839.0]
[4, solutionMFD_rateWeighted, 6.65, 1068.0]
[4, solutionMFD_rateWeighted, 6.75, 1345.0]
[4, solutionMFD_rateWeighted, 6.85, 1758.0]
[4, solutionMFD_rateWeighted, 6.95, 2352.0]
[4, solutionMFD_rateWeighted, 7.05, 3167.0]
[4, solutionMFD_rateWeighted, 7.15, 4347.0]
[4, solutionMFD_rateWeighted, 7.25, 5794.0]
[4, solutionMFD_rateWeighted, 7.35, 7122.0]
[4, solutionMFD_rateWeighted, 7.45, 8605.0]
[4, solutionMFD_rateWeighted, 7.55, 10163.0]
[4, solutionMFD_rateWeighted, 7.65, 10703.0]
[4, solutionMFD_rateWeighted, 7.75, 11386.0]
[4, solutionMFD_rateWeighted, 7.85, 9986.0]
[4, solutionMFD_rateWeighted, 7.95, 5470.0]
[4, solutionMFD_rateWeighted, 8.05, 362.0]


 */

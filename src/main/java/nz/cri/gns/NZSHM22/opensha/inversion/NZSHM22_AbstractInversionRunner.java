package nz.cri.gns.NZSHM22.opensha.inversion;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.*;

import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.*;
import nz.cri.gns.NZSHM22.opensha.util.SimpleGeoJsonBuilder;
import org.apache.commons.math3.util.Precision;
import org.dom4j.DocumentException;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.IntegerSampler;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.geo.json.FeatureProperties;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.io.archive.ArchiveInput;
import org.opensha.commons.util.modules.helpers.CSV_BackedModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.RupSetScalingRelationship;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionInputGenerator;
import org.opensha.sha.earthquake.faultSysSolution.inversion.Inversions;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.ReweightEvenFitSimulatedAnnealing;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionMisfitStats;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.RupHistogramPlots.HistScalar;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.RupHistogramPlots.HistScalarValues;
import org.opensha.sha.faultSurface.FaultSection;
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
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.IterationCompletionCriteria;

import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.params.GenerationFunctionType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.params.NonnegativityConstraintType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.params.CoolingScheduleType;
import scratch.UCERF3.inversion.UCERF3InversionConfiguration;

/**
 * @author chrisbc
 *
 */
public abstract class NZSHM22_AbstractInversionRunner {

	protected long inversionSecs = 60;
	protected long selectionInterval = 10;
	protected Long selectionIterations = null;

	protected String logStates = null;

	private Integer inversionNumSolutionAverages = 1; // 1 means no averaging
	private Integer inversionThreadsPerSelector = 1;
	private Integer inversionAveragingIntervalSecs = null;
	private Integer inversionAveragingIterations = null;
	private boolean inversionAveragingEnabled = false;
	private GenerationFunctionType perturbationFunction = GenerationFunctionType.UNIFORM_0p001;
	private NonnegativityConstraintType nonNegAlgorithm = NonnegativityConstraintType.LIMIT_ZERO_RATES;
	private CoolingScheduleType coolingSchedule = null;
	
	private NZSHM22_SpatialSeisPDF spatialSeisPDF = null;

	protected File rupSetFile;
	protected ArchiveInput rupSetInput;
	protected NZSHM22_InversionFaultSystemRuptSet rupSet = null;
	protected NZSHM22_DeformationModel deformationModel = null;
	protected List<InversionConstraint> constraints = new ArrayList<>();
	private EnergyChangeCompletionCriteria energyChangeCompletionCriteria = null;
	private IterationCompletionCriteria iterationCompletionCriteria = null;
	
	private ThreadedSimulatedAnnealing tsa;
	private double[] initialState;
	private FaultSystemSolution solution;

	private Map<String, Double> finalEnergies = new HashMap<String, Double>();
	private InversionInputGenerator inversionInputGenerator;

	protected List<IncrementalMagFreqDist> solutionMfds;
	protected List<IncrementalMagFreqDist> solutionMfdsV2;

	protected AbstractInversionConfiguration.NZSlipRateConstraintWeightingType slipRateWeightingType;
	protected double slipRateConstraintWt_normalized;
	protected double slipRateConstraintWt_unnormalized;
	protected double slipRateUncertaintyWeight;
	protected double slipRateUncertaintyScalingFactor;
	protected double mfdEqualityConstraintWt;
	protected double mfdInequalityConstraintWt;
	protected double mfdUncertWtdConstraintWt;
	protected double mfdUncertWtdConstraintPower; //typically 0.5
	protected double mfdUncertWtdConstraintScalar; //typically 0.4

	protected abstract NZSHM22_AbstractInversionRunner configure() throws DocumentException, IOException;

	protected double totalRateM5; // = 5d;
	protected double bValue; // = 1d;
	protected double mfdTransitionMag = 7.85; // TODO: how to validate this number for NZ? (ref Morgan Page in
	// // USGS/UCERF3) [KKS, CBC]

	protected NZSHM22_ScalingRelationshipNode scalingRelationship;
	protected double[] initialSolution;
	protected double[] variablePerturbationBasis;
	protected boolean excludeRupturesBelowMinMag = false;
	protected boolean unmodifiedSlipRateStdvs = false;

	protected InversionMisfitStats.Quantity reweightTargetQuantity = null;

	protected double bufferSize = 12;
	protected double minBufferSize = 0;

	public double getPolyBufferSize() {
		return bufferSize;
	}

	public NZSHM22_AbstractInversionRunner setPolyBufferSize(double bufferSize, double minBuffersize) {
		this.bufferSize = bufferSize;
		this.minBufferSize = minBuffersize;
		return this;
	}

	public double getMinPolyBufferSize() {
		return minBufferSize;
	}

	protected boolean repeatable = false;


	/**
	 * Enables logging of all inversion state values.
	 * To log at each step, set the following values:
	 *         runner.setIterationCompletionCriteria(1000);               // 1000 iterations in total
	 *         runner.setSelectionIterations(1);                          // log at each iteration
	 *         runner.setRepeatable(true);                                // make repeatable and single-threaded
	 *         runner.setEnableInversionStateLogging("/tmp/stateLog/");   // enable logging to the specified directory
	 *         runner.setInversionAveraging(false);                       // disable averaging
	 *
	 * Logs will be broken up into zip files that contain up to 500MB of data each when uncompressed. Data will be in
	 * headerless CSV files apart from meta.csv which has a header in each CSV file. See zip file names for the
	 * iteration range contained. See meta.csv for exact iteration for each row. Each CSV file will have a row for each
	 * iteration - unless empty.
	 * @param basePath where to log to
	 * @return this runner
	 */
	public NZSHM22_AbstractInversionRunner setEnableInversionStateLogging(String basePath) {
		this.logStates = basePath;
		return this;
	}

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

	public NZSHM22_AbstractInversionRunner setReweightTargetQuantity(String quantity){
		this.reweightTargetQuantity = InversionMisfitStats.Quantity.valueOf(quantity);
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
	 * @param minIterations        may be set to 0 to noop this method
	 * @return
	 */
	public NZSHM22_AbstractInversionRunner setIterationCompletionCriteria(long minIterations) {
		if (minIterations == 0)
			this.iterationCompletionCriteria = null;
		else
			this.iterationCompletionCriteria = new IterationCompletionCriteria(minIterations);
		return this;
	}

	public NZSHM22_AbstractInversionRunner setRepeatable(boolean repeatable){
		this.repeatable = repeatable;
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
	 * Sets the iterations between sub-solution selections.
	 *
	 * @param iterations
	 * @return this runner.
	 */
	public NZSHM22_AbstractInversionRunner setSelectionIterations(long iterations) {
		this.selectionIterations = iterations;
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
	 * Sets how long each averaging interval will be.
	 *
	 * @param iterations the duration of the averaging period
	 * @return this runner.
	 */
	public NZSHM22_AbstractInversionRunner setInversionAveragingIterations(Integer iterations) {
		this.inversionAveragingIterations = iterations;
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
	 * @param coolingSchedule (from CLASSICAL_SA, FAST_SA (default), VERYFAST_SA, LINEAR )
	 * @return
	 */
	public NZSHM22_AbstractInversionRunner setCoolingSchedule(String coolingSchedule) {
		return setCoolingSchedule(CoolingScheduleType.valueOf(coolingSchedule));
	}

	/**
	 * configure the cooling schedule
	 *
	 * @param coolingSchedule
	 * @return
	 */
	public NZSHM22_AbstractInversionRunner setCoolingSchedule(CoolingScheduleType coolingSchedule) {
		this.coolingSchedule = coolingSchedule;
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

	public NZSHM22_AbstractInversionRunner setSpatialSeisPDF(String spatialSeisPDF){
		this.spatialSeisPDF = NZSHM22_SpatialSeisPDF.valueOf(spatialSeisPDF);
		return this;
	}

	/**
	 * Exclude ruptures that are below MinMag. false by default.
	 * @param excludeRupturesBelowMinMag
	 * @return
	 */
	public NZSHM22_AbstractInversionRunner setExcludeRupturesBelowMinMag(boolean excludeRupturesBelowMinMag){
		this.excludeRupturesBelowMinMag = excludeRupturesBelowMinMag;
		return this;
	}

	/**
	 * Sets whether slip rate stddevs should be normalised for the SlipRateInversionConstraint
	 * @param unmodifiedSlipRateStdvs
	 * @return
	 */
	public NZSHM22_AbstractInversionRunner setUnmodifiedSlipRateStdvs(boolean unmodifiedSlipRateStdvs) {
		this.unmodifiedSlipRateStdvs = unmodifiedSlipRateStdvs;
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

	public NZSHM22_AbstractInversionRunner setRuptureSetFile(String ruptureSetFileName) {
		this.rupSetFile = new File(ruptureSetFileName);
		return this;
	}

	/**
	 * Sets the rupture set file
	 *
	 * @param ruptureSetFile the rupture file
	 * @return this builder
	 */
	public NZSHM22_AbstractInversionRunner setRuptureSetFile(File ruptureSetFile) {
		this.rupSetFile = ruptureSetFile;
		return this;
	}

	public NZSHM22_AbstractInversionRunner setRuptureSetArchiveInput(ArchiveInput archiveInput) {
		this.rupSetInput = archiveInput;
		return this;
	}

	public ArchiveInput getRupSetInput() throws IOException {
		if (rupSetInput != null) {
			return rupSetInput;
		}
		if (rupSetFile != null) {
			return new ArchiveInput.ZipFileInput(rupSetFile);
		}
		throw new IllegalStateException("no rupture set specified");
	}

	public double[] loadRates(String path) throws IOException {
		File file = new File(path);
		CSVFile<String> ratesCSV;
		if (path.endsWith(".zip")) {
			ArchiveInput.ZipFileInput zipFile = new ArchiveInput.ZipFileInput(file);
			ratesCSV = CSV_BackedModule.loadFromArchive(zipFile, "solution/", "rates.csv");
		} else {
			ratesCSV = CSVFile.readFile(file, false);
		}
		return FaultSystemSolution.loadRatesCSV(ratesCSV);
	}

	/**
	 * Sets an initial solution. path can point to a solution zip file or a CSV of the same format as the rates
	 * on a solution archive.
	 * The initial solution must have exactly one rate for each rupture in the rupture set file.
	 * @param path a solution archive or rates CSV
	 * @return this runner
	 * @throws IOException
	 */
	public NZSHM22_AbstractInversionRunner setInitialSolution(String path) throws IOException {
		initialSolution = loadRates(path);
		return this;
	}

	/**
	 * Takes an existing solution file and uses the rates as the variablePerturbationBasis.
	 * If a variable perturbation function is used and this array is not set, one will be
	 * calculated.
	 * @param path a solution archive or rates CSV
	 * @return this runner
	 * @throws IOException
	 */
	public NZSHM22_AbstractInversionRunner setVariablePerturbationBasis(String path) throws IOException {
		variablePerturbationBasis = loadRates(path);
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
		NZSHM22_FaultPolyParameters polyParams = new NZSHM22_FaultPolyParameters();
		polyParams.setMinBufferSize(minBufferSize);
		polyParams.setBufferSize(bufferSize);
		branch.clearValue(NZSHM22_FaultPolyParameters.class);
		branch.setValue(polyParams);
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
		Preconditions.checkState(mfdUncertWtdConstraintWt == 0);
		this.mfdEqualityConstraintWt = mfdEqualityConstraintWt;
		this.mfdInequalityConstraintWt = mfdInequalityConstraintWt;
		return this;
	}

	public NZSHM22_AbstractInversionRunner setUncertaintyWeightedMFDWeights(double mfdUncertaintyWeightedConstraintWt,
			double mfdUncertaintyWeightedConstraintPower, double mfdUncertaintyWeightedConstraintScalar) {
		Preconditions.checkState(this.mfdEqualityConstraintWt == 0);
		Preconditions.checkState(this.mfdInequalityConstraintWt == 0);
		Preconditions.checkArgument(
				0 <= mfdUncertaintyWeightedConstraintPower && mfdUncertaintyWeightedConstraintPower <= 1,
				"mfdUncertWtdConstraintPower must be not less than 0 and not greater than 1.");
		this.mfdUncertWtdConstraintWt = mfdUncertaintyWeightedConstraintWt;
		this.mfdUncertWtdConstraintPower = mfdUncertaintyWeightedConstraintPower;
		this.mfdUncertWtdConstraintScalar = mfdUncertaintyWeightedConstraintScalar;
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
		Preconditions.checkState(this.slipRateWeightingType == null);
		this.slipRateWeightingType = weightingType;
		this.slipRateConstraintWt_normalized = normalizedWt;
		this.slipRateConstraintWt_unnormalized = unnormalizedWt;
		return this;
	}

	/**
	 * Slip rate uncertainty constraint
	 *
	 * @param uncertaintyWeight
	 * @param scalingFactor
	 * @return
	 * @throws IllegalArgumentException if the weighting types is not supported by
	 *                                  this constraint
	 */
	public NZSHM22_AbstractInversionRunner setSlipRateUncertaintyConstraint(double uncertaintyWeight, double scalingFactor) {
		Preconditions.checkState(this.slipRateWeightingType == null);
		this.slipRateWeightingType = AbstractInversionConfiguration.NZSlipRateConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY;
		this.slipRateUncertaintyWeight = uncertaintyWeight;
		this.slipRateUncertaintyScalingFactor = scalingFactor;
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

	protected Set<Integer> createSamplerExclusions() {
		Set<Integer> exclusions = new HashSet<>();
		if (excludeRupturesBelowMinMag) {
			for (int r = 0; r < rupSet.getNumRuptures(); r++) {
				if (rupSet.isRuptureBelowSectMinMag(r)) {
					exclusions.add(r);
				}
			}
		}
		return exclusions;
	}

	protected void printRuptureExclusionStats(Set<Integer> exclusions, String prefix) {

		if(false) {

			System.out.println("Excluded " + exclusions.size() + " ruptures: " + exclusions);

			Set<Integer> includedSections = new HashSet<>();
			for (int i = 0; i < rupSet.getNumRuptures(); i++) {
				if (!exclusions.contains(i)) {
					includedSections.addAll(rupSet.getSectionsIndicesForRup(i));
				}
			}
			Set<Integer> excludedSections = new HashSet<>();
			for (int s = 0; s < rupSet.getNumSections(); s++) {
				if (!includedSections.contains(s)) {
					excludedSections.add(s);
				}
			}

			System.out.println("Completely excluded " + excludedSections.size() + " sections.");

			SimpleGeoJsonBuilder excludedGeoJsonBuilder = new SimpleGeoJsonBuilder();
			SimpleGeoJsonBuilder includedGeoJsonBuilder = new SimpleGeoJsonBuilder();
			for (FaultSection section : rupSet.getFaultSectionDataList()) {
				if (excludedSections.contains(section.getSectionId())) {
					FeatureProperties props = excludedGeoJsonBuilder.addFaultSection(section);
					props.set(FeatureProperties.STROKE_COLOR_PROP, "red");
					props.set(FeatureProperties.STROKE_WIDTH_PROP, 4);
				} else {
					FeatureProperties props = includedGeoJsonBuilder.addFaultSection(section);
					props.set(FeatureProperties.STROKE_COLOR_PROP, "green");
					props.set(FeatureProperties.STROKE_WIDTH_PROP, 4);
				}
			}
			excludedGeoJsonBuilder.toJSON(prefix + "excludedSections.geoJson");
			includedGeoJsonBuilder.toJSON(prefix + "includedSections.geoJson");

			Map<String, Set<Integer>> parents = new HashMap<>();
			for (FaultSection section : rupSet.getFaultSectionDataList()) {
				if (!parents.containsKey(section.getParentSectionName())) {
					parents.put(section.getParentSectionName(), new HashSet<>());
				}
				parents.get(section.getParentSectionName()).add(section.getSectionId());
			}
			Set<String> excludedParents = new HashSet<>();
			for (String p : parents.keySet()) {
				boolean included = false;
				for (int s : parents.get(p)) {
					if (includedSections.contains(s)) {
						included = true;
						break;
					}
				}
				if (!included) {
					excludedParents.add(p);
				}
			}

			System.out.println("Completely excluded " + excludedParents.size() + " faults: " + excludedParents);
		}
	}

	protected IntegerSampler createSampler() {
		Set<Integer> exclusions = createSamplerExclusions();
		if (!exclusions.isEmpty()) {
			printRuptureExclusionStats(exclusions, "sampler_");
			return new IntegerSampler.ExclusionIntegerSampler(0, rupSet.getNumRuptures(), exclusions);
		} else {
			return null;
		}
	}

	/**
	 * Runs the inversion on the specified rupture set.
	 * 
	 * @return the FaultSystemSolution.
	 * @throws IOException
	 * @throws DocumentException
	 */
	public FaultSystemSolution runInversion() throws IOException, DocumentException {

		//UCERF3InversionConfiguration.setMagNorm(8.1);

		configure();
		validateConfig();

		if(repeatable){
			Preconditions.checkState(iterationCompletionCriteria != null || energyChangeCompletionCriteria != null);
			Preconditions.checkState(selectionIterations != null);
		}

		// weight of entropy-maximization constraint (not used in UCERF3)
//		double smoothnessWt = 0;

		inversionInputGenerator.generateInputs(true);
		// column compress it for fast annealing
		inversionInputGenerator.columnCompress();

		List<CompletionCriteria> completionCriterias = new ArrayList<>();
		// inversion completion criteria (how long it will run)
		if (!repeatable)
			completionCriterias.add(TimeCompletionCriteria.getInSeconds(inversionSecs));
		if (!(this.energyChangeCompletionCriteria == null))
			completionCriterias.add(this.energyChangeCompletionCriteria);
		if (!(this.iterationCompletionCriteria == null))
			completionCriterias.add(this.iterationCompletionCriteria);
		
		CompletionCriteria completionCriteria = new CompoundCompletionCriteria(completionCriterias);

		if (logStates != null) {
			completionCriteria = new LoggingCompletionCriteria(completionCriteria, logStates, 500);
		}

		// Bring up window to track progress
		// criteria = new ProgressTrackingCompletionCriteria(criteria, progressReport,
		// 0.1d);
		// ....
		ProgressTrackingCompletionCriteria progress = new ProgressTrackingCompletionCriteria(completionCriteria);

		List<CompletionCriteria> subCompletionCriteriaList = new ArrayList<>();
		if (selectionIterations != null) {
			subCompletionCriteriaList.add(new IterationCompletionCriteria(selectionIterations));
		}
		if (!repeatable){
			subCompletionCriteriaList.add(TimeCompletionCriteria.getInSeconds(selectionInterval));
		}
		// this is the "sub completion criteria" - the amount of time and/or iterations
		// between solution selection/synchronization
		CompletionCriteria subCompletionCriteria = new CompoundCompletionCriteria(subCompletionCriteriaList);

		initialState = inversionInputGenerator.getInitialSolution();

		if (repeatable){
			inversionThreadsPerSelector = 1;
			inversionNumSolutionAverages = 1;
		}

		if (inversionAveragingEnabled) {

			List<CompletionCriteria> criteriaList = new ArrayList<>();
			if (inversionAveragingIterations != null) {
				criteriaList.add(new IterationCompletionCriteria(inversionAveragingIterations));
			}
			if (inversionAveragingIntervalSecs != null && !repeatable) {
				criteriaList.add(TimeCompletionCriteria.getInSeconds(this.inversionAveragingIntervalSecs));
			}
			CompletionCriteria avgSubCompletionCriteria = new CompoundCompletionCriteria(criteriaList);

			// arrange lower-level (actual worker) SAs
			List<SimulatedAnnealing> tsas = new ArrayList<>();
			for (int i = 0; i < inversionNumSolutionAverages; i++) {
				tsas.add(new ThreadedSimulatedAnnealing(inversionInputGenerator.getA(), inversionInputGenerator.getD(),
						inversionInputGenerator.getInitialSolution(), 0d, inversionInputGenerator.getA_ineq(), inversionInputGenerator.getD_ineq(),
						inversionThreadsPerSelector, subCompletionCriteria));
			}
			tsa = new ThreadedSimulatedAnnealing(tsas, avgSubCompletionCriteria);
			tsa.setAverage(true);
		} else {
			tsa = new ThreadedSimulatedAnnealing(inversionInputGenerator.getA(), inversionInputGenerator.getD(),
					inversionInputGenerator.getInitialSolution(), 0d, inversionInputGenerator.getA_ineq(), inversionInputGenerator.getD_ineq(),
					inversionThreadsPerSelector, subCompletionCriteria);
		}
		progress.setConstraintRanges(inversionInputGenerator.getConstraintRowRanges());

		if(completionCriteria instanceof LoggingCompletionCriteria) {
			((LoggingCompletionCriteria) completionCriteria).setConstraintRanges(inversionInputGenerator.getConstraintRowRanges());
		}

		tsa.setConstraintRanges(inversionInputGenerator.getConstraintRowRanges());
		if (reweightTargetQuantity != null) {
			tsa = new ReweightEvenFitSimulatedAnnealing(tsa, reweightTargetQuantity);
		}

		if (repeatable) {
			tsa.setRandom(new Random(1));
		}

		tsa.setPerturbationFunc(perturbationFunction);
		if (perturbationFunction.isVariable()){
			double[] basis = variablePerturbationBasis;
			if (basis == null) {
				basis = Inversions.getDefaultVariablePerturbationBasis(rupSet);
			}
			tsa.setVariablePerturbationBasis(basis);
		}

		tsa.setNonnegativeityConstraintAlgorithm(nonNegAlgorithm);
		if (!(this.coolingSchedule == null))
			tsa.setCoolingFunc(this.coolingSchedule);

		IntegerSampler sampler = createSampler();
		if (sampler != null) {
			tsa.setRuptureSampler(sampler);
		}

		// From CLI metadata Analysis
		initialState = Arrays.copyOf(initialState, initialState.length);

	//	tsa.setCheckPointCriteria(new TimeCompletionCriteria(1),new File("/tmp/checkpoint/"));

		tsa.iterate(progress);

		tsa.shutdown();

		if (completionCriteria instanceof Closeable) {
			((Closeable)completionCriteria).close();
		}

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

		Set<Integer> zeroRates = new HashSet<>();
		for (int r = 0; r < solution_adjusted.length; r++) {
			if (solution_adjusted[r] == 0) {
				zeroRates.add(r);
			}
		}
		printRuptureExclusionStats(zeroRates, "rates_");

		solution = new FaultSystemSolution(rupSet, solution_adjusted);
		solution.addModule(progress.getProgress());
		if (tsa instanceof ReweightEvenFitSimulatedAnnealing) {
			solution.addModule(((ReweightEvenFitSimulatedAnnealing) tsa).getMisfitProgress());
		}
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

	public List<IncrementalMagFreqDist> getSolutionMfdsV2() {
		return solutionMfdsV2;
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


	public ArrayList<ArrayList<String>> getTabularSolutionMfdsV2() {
		ArrayList<ArrayList<String>> rows = new ArrayList<ArrayList<String>>();

		int series = 0;
		for (IncrementalMagFreqDist mfd : getSolutionMfdsV2()) {
			appendMfdRows(mfd, rows, series);
			series++;
		}

		HistogramFunction magHist = solutionMagFreqHistogram(true);
		magHist.setName("solutionMFD");
		appendMfdRows(magHist, rows, series);
		series++;

		return rows;

	}

}

package nz.cri.gns.NZSHM22.opensha.inversion.joint;

import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionInputGenerator;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.EnergyChangeCompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.params.CoolingScheduleType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.params.GenerationFunctionType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.params.NonnegativityConstraintType;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionMisfitStats;

public class AnnealingConfig {

    public long inversionSecs = 60;
    protected long selectionInterval = 10;
    protected long selectionIterations = 0;

    protected String logStates = null;

    protected Integer inversionNumSolutionAverages = 1; // 1 means no averaging
    protected Integer inversionThreadsPerSelector = 1;
    protected Integer inversionAveragingIntervalSecs = null;
    protected Integer inversionAveragingIterations = null;
    protected boolean inversionAveragingEnabled = false;
    protected GenerationFunctionType perturbationFunction = GenerationFunctionType.UNIFORM_0p001;
    protected NonnegativityConstraintType nonNegAlgorithm =
            NonnegativityConstraintType.LIMIT_ZERO_RATES;
    protected CoolingScheduleType coolingSchedule = null;

    protected transient EnergyChangeCompletionCriteria energyChangeCompletionCriteria = null;
    protected double completionEenergy;
    protected double energyDelta;
    protected long iterationCompletionCriteria;

    protected double[] variablePerturbationBasis;
    protected boolean excludeRupturesBelowMinMag = false;

    protected InversionMisfitStats.Quantity reweightTargetQuantity = null;

    protected boolean repeatable = false;

    public transient InversionInputGenerator inversionInputGenerator;

    public void init() {
        if (energyDelta != 0) {
            energyChangeCompletionCriteria =
                    new EnergyChangeCompletionCriteria(0, completionEenergy, 1);
        }
    }

    /**
     * Enables logging of all inversion state values. To log at each step, set the following values:
     * runner.setIterationCompletionCriteria(1000); // 1000 iterations in total
     * runner.setSelectionIterations(1); // log at each iteration runner.setRepeatable(true); //
     * make repeatable and single-threaded runner.setEnableInversionStateLogging("/tmp/stateLog/");
     * // enable logging to the specified directory runner.setInversionAveraging(false); // disable
     * averaging
     *
     * <p>Logs will be broken up into zip files that contain up to 500MB of data each when
     * uncompressed. Data will be in headerless CSV files apart from meta.csv which has a header in
     * each CSV file. See zip file names for the iteration range contained. See meta.csv for exact
     * iteration for each row. Each CSV file will have a row for each iteration - unless empty.
     *
     * @param basePath where to log to
     * @return this config
     */
    public AnnealingConfig setEnableInversionStateLogging(String basePath) {
        this.logStates = basePath;
        return this;
    }

    /**
     * Sets how many minutes the inversion runs for in minutes. Default is 1 minute.
     *
     * @param inversionMinutes the duration of the inversion in minutes.
     * @return this config.
     */
    public AnnealingConfig setInversionMinutes(long inversionMinutes) {
        this.inversionSecs = inversionMinutes * 60;
        return this;
    }

    /**
     * Sets how many seconds the inversion runs for. Default is 60 seconds.
     *
     * @param inversionSeconds the duration of the inversion in seconds.
     * @return this config.
     */
    public AnnealingConfig setInversionSeconds(long inversionSeconds) {
        this.inversionSecs = inversionSeconds;
        return this;
    }

    public AnnealingConfig setReweightTargetQuantity(String quantity) {
        this.reweightTargetQuantity = InversionMisfitStats.Quantity.valueOf(quantity);
        return this;
    }

    /**
     * @param energyDelta may be set to 0 to noop this method
     * @param energyPercentDelta
     * @param lookBackMins
     * @return
     */
    public AnnealingConfig setEnergyChangeCompletionCriteria(
            double energyDelta, double energyPercentDelta, double lookBackMins) {
        if (energyDelta == 0.0d) return this;
        this.energyChangeCompletionCriteria =
                new EnergyChangeCompletionCriteria(energyDelta, energyPercentDelta, lookBackMins);
        return this;
    }

    /**
     * @param minIterations may be set to 0 to noop this method
     * @return
     */
    public AnnealingConfig setIterationCompletionCriteria(long minIterations) {
        this.iterationCompletionCriteria = minIterations;
        return this;
    }

    public AnnealingConfig setRepeatable(boolean repeatable) {
        this.repeatable = repeatable;
        return this;
    }

    /**
     * Sets the length of time between inversion selections (syncs) in seconds. Default is 10
     * seconds.
     *
     * @param syncInterval the interval in seconds.
     * @return this config.
     */
    @Deprecated
    public AnnealingConfig setSyncInterval(long syncInterval) {
        return setSelectionInterval(syncInterval);
    }

    /**
     * Sets the number of threads per selector;
     *
     * <p>NB total threads allocated = (numSolutionAverages * numThreadsPerAvg)
     *
     * @param numThreads the number of threads per solution selector (which might also be an
     *     averaging thread).
     * @return this config.
     */
    public AnnealingConfig setNumThreadsPerSelector(Integer numThreads) {
        this.inversionThreadsPerSelector = numThreads;
        return this;
    }

    /**
     * Sets the length of time between sub-solution selections. Default is 10 seconds.
     *
     * @param interval the interval in seconds.
     * @return this config.
     */
    public AnnealingConfig setSelectionInterval(long interval) {
        this.selectionInterval = interval;
        return this;
    }

    /**
     * Sets the iterations between sub-solution selections.
     *
     * @param iterations
     * @return this config.
     */
    public AnnealingConfig setSelectionIterations(long iterations) {
        this.selectionIterations = iterations;
        return this;
    }

    /**
     * @param numSolutionAverages the number of inversionNumSolutionAverages
     * @return
     */
    public AnnealingConfig setNumSolutionAverages(Integer numSolutionAverages) {
        this.inversionNumSolutionAverages = numSolutionAverages;
        return this;
    }

    /**
     * Sets how long each averaging interval will be.
     *
     * @param seconds the duration of the averaging period in seconds.
     * @return this config.
     */
    public AnnealingConfig setInversionAveragingIntervalSecs(Integer seconds) {
        this.inversionAveragingIntervalSecs = seconds;
        return this;
    }

    /**
     * Sets how long each averaging interval will be.
     *
     * @param iterations the duration of the averaging period
     * @return this config.
     */
    public AnnealingConfig setInversionAveragingIterations(Integer iterations) {
        this.inversionAveragingIterations = iterations;
        return this;
    }

    /**
     * Set up inversion averaging with one method call;
     *
     * <p>This will also determine the total threads allocated = (numSolutionAverages *
     * numThreadsPerAvg)
     *
     * @param numSolutionAverages the number of parallel selectors to average over
     * @param averagingIntervalSecs
     * @return
     */
    public AnnealingConfig setInversionAveraging(
            Integer numSolutionAverages, Integer averagingIntervalSecs) {
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
    public AnnealingConfig setInversionAveraging(boolean enabled) {
        this.inversionAveragingEnabled = enabled;
        return this;
    }

    /**
     * @param coolingSchedule (from CLASSICAL_SA, FAST_SA (default), VERYFAST_SA, LINEAR )
     * @return
     */
    public AnnealingConfig setCoolingSchedule(String coolingSchedule) {
        return setCoolingSchedule(CoolingScheduleType.valueOf(coolingSchedule));
    }

    /**
     * configure the cooling schedule
     *
     * @param coolingSchedule
     * @return
     */
    public AnnealingConfig setCoolingSchedule(CoolingScheduleType coolingSchedule) {
        this.coolingSchedule = coolingSchedule;
        return this;
    }

    /**
     * @param perturbationFunction
     * @return
     */
    public AnnealingConfig setPerturbationFunction(String perturbationFunction) {
        return setPerturbationFunction(GenerationFunctionType.valueOf(perturbationFunction));
    }

    /**
     * configure the perturbation function
     *
     * @param perturbationFunction
     * @return
     */
    public AnnealingConfig setPerturbationFunction(GenerationFunctionType perturbationFunction) {
        this.perturbationFunction = perturbationFunction;
        return this;
    }

    /**
     * configure how Inversion treats values when they perturb < 0
     *
     * @param nonNegAlgorithm
     * @return
     */
    public AnnealingConfig setNonnegativityConstraintType(String nonNegAlgorithm) {
        return this.setNonnegativityConstraintType(
                NonnegativityConstraintType.valueOf(nonNegAlgorithm));
    }

    /**
     * @param nonNegAlgorithm
     * @return
     */
    public AnnealingConfig setNonnegativityConstraintType(
            NonnegativityConstraintType nonNegAlgorithm) {
        this.nonNegAlgorithm = nonNegAlgorithm;
        return this;
    }

    /**
     * Exclude ruptures that are below MinMag. false by default.
     *
     * @param excludeRupturesBelowMinMag
     * @return
     */
    public AnnealingConfig setExcludeRupturesBelowMinMag(boolean excludeRupturesBelowMinMag) {
        this.excludeRupturesBelowMinMag = excludeRupturesBelowMinMag;
        return this;
    }
}

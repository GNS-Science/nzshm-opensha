package nz.cri.gns.NZSHM22.opensha.inversion.joint;

import com.google.common.base.Preconditions;
import com.google.gson.annotations.SerializedName;
import java.io.IOException;
import nz.cri.gns.NZSHM22.opensha.inversion.BaseInversionInputGenerator;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionInputGenerator;
import org.opensha.sha.earthquake.faultSysSolution.inversion.Inversions;
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
    protected Integer inversionThreadsPerSelector = 0; // 0 means use all available cores
    protected Integer inversionAveragingIntervalSecs = null;
    protected Integer inversionAveragingIterations = null;
    protected boolean inversionAveragingEnabled = false;
    protected GenerationFunctionType perturbationFunction = GenerationFunctionType.UNIFORM_0p001;
    protected NonnegativityConstraintType nonNegAlgorithm =
            NonnegativityConstraintType.LIMIT_ZERO_RATES;
    protected CoolingScheduleType coolingSchedule = null;

    protected transient EnergyChangeCompletionCriteria energyChangeCompletionCriteria = null;

    /**
     * The energy change completion criteria is only created when {@link #energyDelta} is non-zero;
     * see {@link #init()}. This field is misspelled as "completionEenergy" in configs written
     * before the spelling was fixed, so that name is still accepted when reading.
     */
    @SerializedName(
            value = "completionEnergy",
            alternate = {"completionEenergy"})
    protected double completionEnergy;

    /**
     * Acts purely as an on/off switch for the energy change completion criteria: any non-zero value
     * enables it. The threshold itself is {@link #completionEnergy}.
     */
    protected double energyDelta;

    protected long iterationCompletionCriteria;

    protected double[] variablePerturbationBasis;

    /**
     * The basis actually in use, which may have been computed from the rupture set. Kept out of the
     * serialised form: it holds one value per rupture, which would bloat every config we write.
     */
    protected transient double[] resolvedVariablePerturbationBasis;

    /**
     * Path to a solution zip or a rates CSV to start the inversion from, instead of all zeroes.
     * Mutually exclusive with {@link #varPertBasisAsInitialSolution}.
     */
    protected String initialSolutionPath;

    /**
     * If true, the variable perturbation basis is used as the initial solution. The basis gives
     * every rupture a physically motivated starting rate, so the inversion adjusts a smooth model
     * rather than annealing up from zero.
     */
    protected boolean varPertBasisAsInitialSolution = false;

    protected boolean excludeRupturesBelowMinMag = false;

    protected InversionMisfitStats.Quantity reweightTargetQuantity = null;

    protected boolean repeatable = false;

    public transient InversionInputGenerator inversionInputGenerator;

    /**
     * Hydrates the transient energy change completion criteria from the deserialised fields.
     *
     * <p>{@link #completionEnergy} is passed as the <em>percent</em> energy change threshold over a
     * one minute look-back window, matching how the legacy runner is driven from ParameterRunner.
     * Note that a value of 0 is treated by {@link EnergyChangeCompletionCriteria} as "no
     * threshold", which makes the criteria satisfied as soon as the look-back window has elapsed.
     */
    public void init() {
        if (energyDelta != 0) {
            energyChangeCompletionCriteria =
                    new EnergyChangeCompletionCriteria(0, completionEnergy, 1);
        }
    }

    /**
     * Returns the per-rupture basis used by the variable perturbation functions, computing the
     * default from the rupture set on first use and caching it.
     *
     * @param rupSet the rupture set being inverted
     * @return one perturbation basis value per rupture
     */
    public double[] getVariablePerturbationBasis(FaultSystemRupSet rupSet) {
        if (resolvedVariablePerturbationBasis == null) {
            resolvedVariablePerturbationBasis =
                    variablePerturbationBasis != null
                            ? variablePerturbationBasis
                            : Inversions.getDefaultVariablePerturbationBasis(rupSet);
        }
        return resolvedVariablePerturbationBasis;
    }

    /**
     * Resolves the initial solution for the inversion.
     *
     * @param rupSet the rupture set being inverted
     * @return one rate per rupture, or null to start from all zeroes
     * @throws IOException if initialSolutionPath cannot be read
     */
    public double[] getInitialSolution(FaultSystemRupSet rupSet) throws IOException {
        Preconditions.checkState(
                initialSolutionPath == null || !varPertBasisAsInitialSolution,
                "Only one of initialSolutionPath and varPertBasisAsInitialSolution may be set.");
        double[] initialSolution = null;
        if (initialSolutionPath != null) {
            initialSolution = BaseInversionInputGenerator.loadRates(initialSolutionPath);
        } else if (varPertBasisAsInitialSolution) {
            initialSolution = getVariablePerturbationBasis(rupSet).clone();
        }
        Preconditions.checkState(
                initialSolution == null || initialSolution.length == rupSet.getNumRuptures(),
                "Initial solution has %s rates but the rupture set has %s ruptures.",
                initialSolution == null ? 0 : initialSolution.length,
                rupSet.getNumRuptures());
        return initialSolution;
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

package nz.cri.gns.NZSHM22.opensha.inversion.joint;

import com.google.common.base.Preconditions;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.*;
import nz.cri.gns.NZSHM22.opensha.inversion.AbstractInversionConfiguration;
import nz.cri.gns.NZSHM22.opensha.inversion.LoggingCompletionCriteria;
import nz.cri.gns.NZSHM22.opensha.inversion.NZSHM22_InversionFaultSystemRuptSet;
import nz.cri.gns.NZSHM22.opensha.ruptures.NZSHM22_AbstractRuptureSetBuilder;
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
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.SimulatedAnnealing;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.ThreadedSimulatedAnnealing;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.*;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.params.CoolingScheduleType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.params.GenerationFunctionType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.params.NonnegativityConstraintType;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionMisfitStats;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.RupHistogramPlots.HistScalar;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.RupHistogramPlots.HistScalarValues;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import scratch.UCERF3.analysis.FaultSystemRupSetCalc;
import scratch.UCERF3.inversion.UCERF3InversionConfiguration;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author chrisbc
 */
public abstract class InversionRunner {

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
    private NonnegativityConstraintType nonNegAlgorithm =
            NonnegativityConstraintType.LIMIT_ZERO_RATES;
    private CoolingScheduleType coolingSchedule = null;

    private EnergyChangeCompletionCriteria energyChangeCompletionCriteria = null;
    private IterationCompletionCriteria iterationCompletionCriteria = null;

    private ThreadedSimulatedAnnealing tsa;

    private InversionInputGenerator inversionInputGenerator;

    protected double[] variablePerturbationBasis;
    protected boolean excludeRupturesBelowMinMag = false;

    protected NZSHM22_InversionFaultSystemRuptSet rupSet = null;

    protected InversionMisfitStats.Quantity reweightTargetQuantity = null;

    protected boolean repeatable = false;

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
     * @return this runner
     */
    public InversionRunner setEnableInversionStateLogging(String basePath) {
        this.logStates = basePath;
        return this;
    }

    /**
     * Sets how many minutes the inversion runs for in minutes. Default is 1 minute.
     *
     * @param inversionMinutes the duration of the inversion in minutes.
     * @return this runner.
     */
    public InversionRunner setInversionMinutes(long inversionMinutes) {
        this.inversionSecs = inversionMinutes * 60;
        return this;
    }

    /**
     * Sets how many seconds the inversion runs for. Default is 60 seconds.
     *
     * @param inversionSeconds the duration of the inversion in seconds.
     * @return this runner.
     */
    public InversionRunner setInversionSeconds(long inversionSeconds) {
        this.inversionSecs = inversionSeconds;
        return this;
    }

    public InversionRunner setReweightTargetQuantity(String quantity) {
        this.reweightTargetQuantity = InversionMisfitStats.Quantity.valueOf(quantity);
        return this;
    }

    /**
     * @param energyDelta may be set to 0 to noop this method
     * @param energyPercentDelta
     * @param lookBackMins
     * @return
     */
    public InversionRunner setEnergyChangeCompletionCriteria(
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
    public InversionRunner setIterationCompletionCriteria(long minIterations) {
        if (minIterations == 0) this.iterationCompletionCriteria = null;
        else this.iterationCompletionCriteria = new IterationCompletionCriteria(minIterations);
        return this;
    }

    public InversionRunner setRepeatable(boolean repeatable) {
        this.repeatable = repeatable;
        return this;
    }

    /**
     * Sets the length of time between inversion selections (syncs) in seconds. Default is 10
     * seconds.
     *
     * @param syncInterval the interval in seconds.
     * @return this runner.
     */
    @Deprecated
    public InversionRunner setSyncInterval(long syncInterval) {
        return setSelectionInterval(syncInterval);
    }

    /**
     * Sets the number of threads per selector;
     *
     * <p>NB total threads allocated = (numSolutionAverages * numThreadsPerAvg)
     *
     * @param numThreads the number of threads per solution selector (which might also be an
     *     averaging thread).
     * @return this runner.
     */
    public InversionRunner setNumThreadsPerSelector(Integer numThreads) {
        this.inversionThreadsPerSelector = numThreads;
        return this;
    }

    /**
     * Sets the length of time between sub-solution selections. Default is 10 seconds.
     *
     * @param interval the interval in seconds.
     * @return this runner.
     */
    public InversionRunner setSelectionInterval(long interval) {
        this.selectionInterval = interval;
        return this;
    }

    /**
     * Sets the iterations between sub-solution selections.
     *
     * @param iterations
     * @return this runner.
     */
    public InversionRunner setSelectionIterations(long iterations) {
        this.selectionIterations = iterations;
        return this;
    }

    /**
     * @param numSolutionAverages the number of inversionNumSolutionAverages
     * @return
     */
    public InversionRunner setNumSolutionAverages(Integer numSolutionAverages) {
        this.inversionNumSolutionAverages = numSolutionAverages;
        return this;
    }

    /**
     * Sets how long each averaging interval will be.
     *
     * @param seconds the duration of the averaging period in seconds.
     * @return this runner.
     */
    public InversionRunner setInversionAveragingIntervalSecs(Integer seconds) {
        this.inversionAveragingIntervalSecs = seconds;
        return this;
    }

    /**
     * Sets how long each averaging interval will be.
     *
     * @param iterations the duration of the averaging period
     * @return this runner.
     */
    public InversionRunner setInversionAveragingIterations(Integer iterations) {
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
    public InversionRunner setInversionAveraging(
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
    public InversionRunner setInversionAveraging(boolean enabled) {
        this.inversionAveragingEnabled = enabled;
        return this;
    }

    /**
     * @param coolingSchedule (from CLASSICAL_SA, FAST_SA (default), VERYFAST_SA, LINEAR )
     * @return
     */
    public InversionRunner setCoolingSchedule(String coolingSchedule) {
        return setCoolingSchedule(CoolingScheduleType.valueOf(coolingSchedule));
    }

    /**
     * configure the cooling schedule
     *
     * @param coolingSchedule
     * @return
     */
    public InversionRunner setCoolingSchedule(CoolingScheduleType coolingSchedule) {
        this.coolingSchedule = coolingSchedule;
        return this;
    }

    /**
     * @param perturbationFunction
     * @return
     */
    public InversionRunner setPerturbationFunction(String perturbationFunction) {
        return setPerturbationFunction(GenerationFunctionType.valueOf(perturbationFunction));
    }

    /**
     * configure the perturbation function
     *
     * @param perturbationFunction
     * @return
     */
    public InversionRunner setPerturbationFunction(
            GenerationFunctionType perturbationFunction) {
        this.perturbationFunction = perturbationFunction;
        return this;
    }

    /**
     * configure how Inversion treats values when they perturb < 0
     *
     * @param nonNegAlgorithm
     * @return
     */
    public InversionRunner setNonnegativityConstraintType(String nonNegAlgorithm) {
        return this.setNonnegativityConstraintType(
                NonnegativityConstraintType.valueOf(nonNegAlgorithm));
    }

    /**
     * @param nonNegAlgorithm
     * @return
     */
    public InversionRunner setNonnegativityConstraintType(
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
    public InversionRunner setExcludeRupturesBelowMinMag(
            boolean excludeRupturesBelowMinMag) {
        this.excludeRupturesBelowMinMag = excludeRupturesBelowMinMag;
        return this;
    }

    /**
     * @param inputGen
     * @return
     */
    public InversionRunner setInversionInputGenerator(
            InversionInputGenerator inputGen) {
        this.inversionInputGenerator = inputGen;
        return this;
    }

    public InversionInputGenerator getInversionInputGenerator() {
        return inversionInputGenerator;
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

    protected IntegerSampler createSampler() {
        Set<Integer> exclusions = createSamplerExclusions();
        if (!exclusions.isEmpty()) {
            return new IntegerSampler.ExclusionIntegerSampler(
                    0, rupSet.getNumRuptures(), exclusions);
        } else {
            return null;
        }
    }

    protected CompletionCriteria createCompletionCriteria() throws IOException {

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
        return completionCriteria;
    }

    protected CompletionCriteria createSubCompletionCriteria() {
        // this is the "sub completion criteria" - the amount of time and/or iterations
        // between solution selection/synchronization
        // Note: since OpenSHA breaks if the subcompletionCriteria is a compound completion
        // criteria, we only allow
        // one criteria here. See https://github.com/GNS-Science/nzshm-opensha/issues/360
        CompletionCriteria subCompletionCriteria;
        if (selectionIterations != null) {
            subCompletionCriteria = new IterationCompletionCriteria(selectionIterations);
        } else {
            subCompletionCriteria = TimeCompletionCriteria.getInSeconds(selectionInterval);

        }
        return subCompletionCriteria;
    }

    protected CompletionCriteria createAvgSubCompletionCriteria() {
        List<CompletionCriteria> criteriaList = new ArrayList<>();
        if (inversionAveragingIterations != null) {
            criteriaList.add(new IterationCompletionCriteria(inversionAveragingIterations));
        }
        if (inversionAveragingIntervalSecs != null && !repeatable) {
            criteriaList.add(
                    TimeCompletionCriteria.getInSeconds(this.inversionAveragingIntervalSecs));
        }
        return        new CompoundCompletionCriteria(criteriaList);

    }

    /**
     * Runs the inversion on the specified rupture set.
     *
     * @return the FaultSystemSolution.
     * @throws IOException
     * @throws DocumentException
     */
    public FaultSystemSolution runInversion() throws IOException, DocumentException {

        UCERF3InversionConfiguration.setMagNorm(8.1);

        if (repeatable) {
            Preconditions.checkState(
                    iterationCompletionCriteria != null || energyChangeCompletionCriteria != null);
            Preconditions.checkState(selectionIterations != null);
        }

        inversionInputGenerator.generateInputs(true);
        // column compress it for fast annealing
        inversionInputGenerator.columnCompress();

        CompletionCriteria completionCriteria = createCompletionCriteria();

        ProgressTrackingCompletionCriteria progress =
                new ProgressTrackingCompletionCriteria(completionCriteria);

        CompletionCriteria subCompletionCriteria = createSubCompletionCriteria();

        if (repeatable) {
            inversionThreadsPerSelector = 1;
            inversionNumSolutionAverages = 1;
        }

        if (inversionAveragingEnabled) {

            CompletionCriteria avgSubCompletionCriteria = createAvgSubCompletionCriteria();

            // arrange lower-level (actual worker) SAs
            List<SimulatedAnnealing> tsas = new ArrayList<>();
            for (int i = 0; i < inversionNumSolutionAverages; i++) {
                tsas.add(
                        new ThreadedSimulatedAnnealing(
                                inversionInputGenerator.getA(),
                                inversionInputGenerator.getD(),
                                inversionInputGenerator.getInitialSolution(),
                                0d,
                                inversionInputGenerator.getA_ineq(),
                                inversionInputGenerator.getD_ineq(),
                                inversionThreadsPerSelector,
                                subCompletionCriteria));
            }
            tsa = new ThreadedSimulatedAnnealing(tsas, avgSubCompletionCriteria);
            tsa.setAverage(true);
        } else {
            tsa =
                    new ThreadedSimulatedAnnealing(
                            inversionInputGenerator.getA(),
                            inversionInputGenerator.getD(),
                            inversionInputGenerator.getInitialSolution(),
                            0d,
                            inversionInputGenerator.getA_ineq(),
                            inversionInputGenerator.getD_ineq(),
                            inversionThreadsPerSelector,
                            subCompletionCriteria);
        }
        progress.setConstraintRanges(inversionInputGenerator.getConstraintRowRanges());

        if (completionCriteria instanceof LoggingCompletionCriteria) {
            ((LoggingCompletionCriteria) completionCriteria)
                    .setConstraintRanges(inversionInputGenerator.getConstraintRowRanges())
                    .open();
        }

        tsa.setConstraintRanges(inversionInputGenerator.getConstraintRowRanges());
        if (reweightTargetQuantity != null) {
            tsa = new ReweightEvenFitSimulatedAnnealing(tsa, reweightTargetQuantity);
        }

        if (repeatable) {
            tsa.setRandom(new Random(1));
        }

        tsa.setPerturbationFunc(perturbationFunction);
        if (perturbationFunction.isVariable()) {
            double[] basis = variablePerturbationBasis;
            if (basis == null) {
                basis = Inversions.getDefaultVariablePerturbationBasis(rupSet);
            }
            tsa.setVariablePerturbationBasis(basis);
        }

        tsa.setNonnegativeityConstraintAlgorithm(nonNegAlgorithm);
        if (!(this.coolingSchedule == null)) tsa.setCoolingFunc(this.coolingSchedule);

        IntegerSampler sampler = createSampler();
        if (sampler != null) {
            tsa.setRuptureSampler(sampler);
        }

        tsa.iterate(progress);
        tsa.shutdown();

        if (completionCriteria instanceof Closeable) {
            ((Closeable) completionCriteria).close();
        }

       return createSolution(progress);
    }

    protected FaultSystemSolution createSolution(ProgressTrackingCompletionCriteria progress) throws IOException {
        // now assemble the solution
        double[] solution_raw = tsa.getBestSolution();

        // adjust for minimum rates if applicable
        double[] solution_adjusted =
                inversionInputGenerator.adjustSolutionForWaterLevel(solution_raw);

        FaultSystemSolution solution = new FaultSystemSolution(rupSet, solution_adjusted);
        solution.addModule(progress.getProgress());
        solution.addModule(NZSHM22_AbstractRuptureSetBuilder.createBuildInfo());
        if (tsa instanceof ReweightEvenFitSimulatedAnnealing) {
            solution.addModule(((ReweightEvenFitSimulatedAnnealing) tsa).getMisfitProgress());
        }
        return solution;
    }

}

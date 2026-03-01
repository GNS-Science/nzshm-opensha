package nz.cri.gns.NZSHM22.opensha.inversion;

import static nz.cri.gns.NZSHM22.opensha.inversion.BaseInversionInputGenerator.LOG_MATRIX_ONLY;

import com.google.common.base.Preconditions;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.*;
import nz.cri.gns.NZSHM22.opensha.reports.TabularMfds;
import nz.cri.gns.NZSHM22.opensha.ruptures.NZSHM22_AbstractRuptureSetBuilder;
import nz.cri.gns.NZSHM22.opensha.util.SimpleGeoJsonBuilder;
import org.dom4j.DocumentException;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.IntegerSampler;
import org.opensha.commons.geo.json.FeatureProperties;
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
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.CompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.CompoundCompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.EnergyChangeCompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.IterationCompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.ProgressTrackingCompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.TimeCompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.params.CoolingScheduleType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.params.GenerationFunctionType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.params.NonnegativityConstraintType;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionMisfitStats;
import org.opensha.sha.faultSurface.FaultSection;
import scratch.UCERF3.inversion.UCERF3InversionConfiguration;

/**
 * @author chrisbc
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
    private NonnegativityConstraintType nonNegAlgorithm =
            NonnegativityConstraintType.LIMIT_ZERO_RATES;
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

    private InversionInputGenerator inversionInputGenerator;

    protected AbstractInversionConfiguration.NZSlipRateConstraintWeightingType
            slipRateWeightingType;
    protected double slipRateConstraintWt_normalized;
    protected double slipRateConstraintWt_unnormalized;
    protected double slipRateUncertaintyWeight;
    protected double slipRateUncertaintyScalingFactor;
    protected double mfdEqualityConstraintWt;
    protected double mfdInequalityConstraintWt;
    protected double mfdUncertWtdConstraintWt;
    protected double mfdUncertWtdConstraintPower; // typically 0.5
    protected double mfdUncertWtdConstraintScalar; // typically 0.4

    protected abstract NZSHM22_AbstractInversionRunner configure()
            throws DocumentException, IOException;

    protected double totalRateM5; // = 5d;
    protected double bValue; // = 1d;
    protected double mfdTransitionMag =
            7.85; // TODO: how to validate this number for NZ? (ref Morgan Page in
    // // USGS/UCERF3) [KKS, CBC]

    protected NZSHM22_ScalingRelationshipNode scalingRelationship;
    protected double[] initialSolution;
    protected double[] variablePerturbationBasis;
    protected boolean varPertBasisAsInititalSolution;
    protected boolean excludeRupturesBelowMinMag = false;
    protected boolean unmodifiedSlipRateStdvs = false;

    protected InversionMisfitStats.Quantity reweightTargetQuantity = null;

    protected double bufferSize = 12;
    protected double minBufferSize = 0;

    public double getPolyBufferSize() {
        return bufferSize;
    }

    public NZSHM22_AbstractInversionRunner setPolyBufferSize(
            double bufferSize, double minBuffersize) {
        this.bufferSize = bufferSize;
        this.minBufferSize = minBuffersize;
        return this;
    }

    public double getMinPolyBufferSize() {
        return minBufferSize;
    }

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

    public NZSHM22_AbstractInversionRunner setReweightTargetQuantity(String quantity) {
        this.reweightTargetQuantity = InversionMisfitStats.Quantity.valueOf(quantity);
        return this;
    }

    /**
     * @param energyDelta may be set to 0 to noop this method
     * @param energyPercentDelta
     * @param lookBackMins
     * @return
     */
    public NZSHM22_AbstractInversionRunner setEnergyChangeCompletionCriteria(
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
    public NZSHM22_AbstractInversionRunner setIterationCompletionCriteria(long minIterations) {
        if (minIterations == 0) this.iterationCompletionCriteria = null;
        else this.iterationCompletionCriteria = new IterationCompletionCriteria(minIterations);
        return this;
    }

    public NZSHM22_AbstractInversionRunner setRepeatable(boolean repeatable) {
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
    public NZSHM22_AbstractInversionRunner setSyncInterval(long syncInterval) {
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
     * <p>This will also determine the total threads allocated = (numSolutionAverages *
     * numThreadsPerAvg)
     *
     * @param numSolutionAverages the number of parallel selectors to average over
     * @param averagingIntervalSecs
     * @return
     */
    public NZSHM22_AbstractInversionRunner setInversionAveraging(
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
    public NZSHM22_AbstractInversionRunner setPerturbationFunction(
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
    public NZSHM22_AbstractInversionRunner setNonnegativityConstraintType(String nonNegAlgorithm) {
        return this.setNonnegativityConstraintType(
                NonnegativityConstraintType.valueOf(nonNegAlgorithm));
    }

    /**
     * @param nonNegAlgorithm
     * @return
     */
    public NZSHM22_AbstractInversionRunner setNonnegativityConstraintType(
            NonnegativityConstraintType nonNegAlgorithm) {
        this.nonNegAlgorithm = nonNegAlgorithm;
        return this;
    }

    public NZSHM22_AbstractInversionRunner setSpatialSeisPDF(
            NZSHM22_SpatialSeisPDF spatialSeisPDF) {
        this.spatialSeisPDF = spatialSeisPDF;
        return this;
    }

    public NZSHM22_AbstractInversionRunner setSpatialSeisPDF(String spatialSeisPDF) {
        this.spatialSeisPDF = NZSHM22_SpatialSeisPDF.valueOf(spatialSeisPDF);
        return this;
    }

    /**
     * Exclude ruptures that are below MinMag. false by default.
     *
     * @param excludeRupturesBelowMinMag
     * @return
     */
    public NZSHM22_AbstractInversionRunner setExcludeRupturesBelowMinMag(
            boolean excludeRupturesBelowMinMag) {
        this.excludeRupturesBelowMinMag = excludeRupturesBelowMinMag;
        return this;
    }

    /**
     * Sets whether slip rate stddevs should be normalised for the SlipRateInversionConstraint
     *
     * @param unmodifiedSlipRateStdvs
     * @return
     */
    public NZSHM22_AbstractInversionRunner setUnmodifiedSlipRateStdvs(
            boolean unmodifiedSlipRateStdvs) {
        this.unmodifiedSlipRateStdvs = unmodifiedSlipRateStdvs;
        return this;
    }

    /**
     * @param inputGen
     * @return
     */
    public NZSHM22_AbstractInversionRunner setInversionInputGenerator(
            InversionInputGenerator inputGen) {
        this.inversionInputGenerator = inputGen;
        return this;
    }

    public InversionInputGenerator getInversionInputGenerator() {
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
     * Sets an initial solution. path can point to a solution zip file or a CSV of the same format
     * as the rates on a solution archive. The initial solution must have exactly one rate for each
     * rupture in the rupture set file.
     *
     * @param path a solution archive or rates CSV
     * @return this runner
     * @throws IOException
     */
    public NZSHM22_AbstractInversionRunner setInitialSolution(String path) throws IOException {
        initialSolution = loadRates(path);
        return this;
    }

    /**
     * If set to true, the variablePerturbationBasis is used as the initial solution. If the
     * variablePerturbationBasis has not been set, it will be set to the
     * defaultVariablePerturbationBasis first.
     *
     * @param value
     * @return
     */
    public NZSHM22_AbstractInversionRunner setVarPertBasisAsInititalSolution(boolean value) {
        varPertBasisAsInititalSolution = value;
        return this;
    }

    /**
     * Takes an existing solution file and uses the rates as the variablePerturbationBasis. If a
     * variable perturbation function is used and this array is not set, one will be calculated.
     *
     * @param path a solution archive or rates CSV
     * @return this runner
     * @throws IOException
     */
    public NZSHM22_AbstractInversionRunner setVariablePerturbationBasis(String path)
            throws IOException {
        variablePerturbationBasis = loadRates(path);
        return this;
    }

    protected void setupLTB(NZSHM22_LogicTreeBranch branch) {
        if (scalingRelationship != null) {
            branch.clearValue(NZSHM22_ScalingRelationshipNode.class);
            branch.setValue(scalingRelationship);
        }
        if (deformationModel != null) {
            branch.setValue(deformationModel);
        }
        if (spatialSeisPDF != null) {
            branch.clearValue(NZSHM22_SpatialSeisPDF.class);
            branch.setValue(spatialSeisPDF);
        }
        NZSHM22_FaultPolyParameters polyParams = new NZSHM22_FaultPolyParameters();
        polyParams.setMinBufferSize(minBufferSize);
        polyParams.setBufferSize(bufferSize);
        branch.clearValue(NZSHM22_FaultPolyParameters.class);
        branch.setValue(polyParams);
    }

    public NZSHM22_AbstractInversionRunner setDeformationModel(String modelName) {
        this.deformationModel = NZSHM22_DeformationModel.valueOf(modelName);
        return this;
    }

    /**
     * @param mfdEqualityConstraintWt
     * @param mfdInequalityConstraintWt
     * @return
     */
    public NZSHM22_AbstractInversionRunner setGutenbergRichterMFDWeights(
            double mfdEqualityConstraintWt, double mfdInequalityConstraintWt) {
        Preconditions.checkState(mfdUncertWtdConstraintWt == 0);
        this.mfdEqualityConstraintWt = mfdEqualityConstraintWt;
        this.mfdInequalityConstraintWt = mfdInequalityConstraintWt;
        return this;
    }

    public NZSHM22_AbstractInversionRunner setUncertaintyWeightedMFDWeights(
            double mfdUncertaintyWeightedConstraintWt,
            double mfdUncertaintyWeightedConstraintPower,
            double mfdUncertaintyWeightedConstraintScalar) {
        Preconditions.checkState(this.mfdEqualityConstraintWt == 0);
        Preconditions.checkState(this.mfdInequalityConstraintWt == 0);
        Preconditions.checkArgument(
                0 <= mfdUncertaintyWeightedConstraintPower
                        && mfdUncertaintyWeightedConstraintPower <= 1,
                "mfdUncertWtdConstraintPower must be not less than 0 and not greater than 1.");
        this.mfdUncertWtdConstraintWt = mfdUncertaintyWeightedConstraintWt;
        this.mfdUncertWtdConstraintPower = mfdUncertaintyWeightedConstraintPower;
        this.mfdUncertWtdConstraintScalar = mfdUncertaintyWeightedConstraintScalar;
        return this;
    }

    /**
     * UCERF3-style Slip rate constraint
     *
     * <p>If normalized, slip rate misfit is % difference for each section (recommended since it
     * helps fit slow-moving faults). If unnormalized, misfit is absolute difference. BOTH includes
     * both normalized and unnormalized constraints.
     *
     * @param weightingType a value from
     *     UCERF3InversionConfiguration.SlipRateConstraintWeightingType
     * @param normalizedWt
     * @param unnormalizedWt
     * @throws IllegalArgumentException if the weighting types is not supported by this constraint
     * @return
     */
    public NZSHM22_AbstractInversionRunner setSlipRateConstraint(
            AbstractInversionConfiguration.NZSlipRateConstraintWeightingType weightingType,
            double normalizedWt,
            double unnormalizedWt) {
        Preconditions.checkArgument(
                weightingType
                        != AbstractInversionConfiguration.NZSlipRateConstraintWeightingType
                                .NORMALIZED_BY_UNCERTAINTY,
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
     * @throws IllegalArgumentException if the weighting types is not supported by this constraint
     */
    public NZSHM22_AbstractInversionRunner setSlipRateUncertaintyConstraint(
            double uncertaintyWeight, double scalingFactor) {
        Preconditions.checkState(this.slipRateWeightingType == null);
        this.slipRateWeightingType =
                AbstractInversionConfiguration.NZSlipRateConstraintWeightingType
                        .NORMALIZED_BY_UNCERTAINTY;
        this.slipRateUncertaintyWeight = uncertaintyWeight;
        this.slipRateUncertaintyScalingFactor = scalingFactor;
        return this;
    }

    public NZSHM22_AbstractInversionRunner setScalingRelationship(
            String scalingRelationship, boolean recalcMags) {
        return setScalingRelationship(
                NZSHM22_ScalingRelationshipNode.createRelationShip(scalingRelationship),
                recalcMags);
    }

    public NZSHM22_AbstractInversionRunner setScalingRelationship(
            RupSetScalingRelationship scalingRelationship, boolean recalcMags) {
        this.scalingRelationship = new NZSHM22_ScalingRelationshipNode();
        this.scalingRelationship.setScalingRelationship(scalingRelationship);
        this.scalingRelationship.setRecalc(recalcMags);
        return this;
    }

    /**
     * UCERF3-style Slip rate constraint
     *
     * @param weightingType a string value from
     *     UCERF3InversionConfiguration.SlipRateConstraintWeightingType
     * @param normalizedWt
     * @param unnormalizedWt
     * @throws IllegalArgumentException if the weighting types is not supported by this constraint
     * @return
     */
    public NZSHM22_AbstractInversionRunner setSlipRateConstraint(
            String weightingType, double normalizedWt, double unnormalizedWt) {
        AbstractInversionConfiguration.NZSlipRateConstraintWeightingType weighting;
        if (weightingType.equalsIgnoreCase("UNCERTAINTY_ADJUSTED")) { // backwards compatibility
            weighting =
                    AbstractInversionConfiguration.NZSlipRateConstraintWeightingType
                            .NORMALIZED_BY_UNCERTAINTY;
        } else {
            weighting =
                    AbstractInversionConfiguration.NZSlipRateConstraintWeightingType.valueOf(
                            weightingType);
        }
        return setSlipRateConstraint(weighting, normalizedWt, unnormalizedWt);
    }

    public void validateConfig() {
        Preconditions.checkState(
                scalingRelationship.getScalingRelationship() != null,
                "ScalingRelationship must be set");

        FaultRegime regime =
                rupSet.getModule(NZSHM22_LogicTreeBranch.class).getValue(FaultRegime.class);
        FaultRegime scalingRegime = scalingRelationship.getRegime();
        Preconditions.checkState(
                regime == scalingRegime,
                "Regime of rupture set and scaling relationship do not match.");
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

        if (false) {

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

            System.out.println(
                    "Completely excluded "
                            + excludedParents.size()
                            + " faults: "
                            + excludedParents);
        }
    }

    protected IntegerSampler createSampler() {
        Set<Integer> exclusions = createSamplerExclusions();
        if (!exclusions.isEmpty()) {
            System.out.println(
                    "Excluding " + exclusions.size() + " ruptures that are below section minMag.");
            printRuptureExclusionStats(exclusions, "sampler_");
            return new IntegerSampler.ExclusionIntegerSampler(
                    0, rupSet.getNumRuptures(), exclusions);
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

        UCERF3InversionConfiguration.setMagNorm(8.1);

        configure();
        validateConfig();

        if (repeatable) {
            Preconditions.checkState(
                    iterationCompletionCriteria != null || energyChangeCompletionCriteria != null);
            Preconditions.checkState(selectionIterations != null);
        }

        // weight of entropy-maximization constraint (not used in UCERF3)
        //		double smoothnessWt = 0;

        inversionInputGenerator.generateInputs(true);
        // column compress it for fast annealing
        inversionInputGenerator.columnCompress();

        if (LOG_MATRIX_ONLY) {
            NZSHM22_LogicTreeBranch branch = rupSet.getModule(NZSHM22_LogicTreeBranch.class);
            FaultRegime regime = branch.getValue(FaultRegime.class);

            String prefix = regime == FaultRegime.CRUSTAL ? "cru" : "sbd";

            Files.writeString(
                    Path.of(prefix + "_A.txt"), inversionInputGenerator.getA().toString());
            Files.writeString(
                    Path.of(prefix + "_D.txt"), Arrays.toString(inversionInputGenerator.getD()));
            if (inversionInputGenerator.getA_ineq() != null) {
                Files.writeString(
                        Path.of(prefix + "_A_ineq.txt"),
                        inversionInputGenerator.getA_ineq().toString());
            }
            if (inversionInputGenerator.getD_ineq() != null) {
                Files.writeString(
                        Path.of(prefix + "_D_ineq.txt"),
                        Arrays.toString(inversionInputGenerator.getD_ineq()));
            }

            System.exit(0);
        }

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
        ProgressTrackingCompletionCriteria progress =
                new ProgressTrackingCompletionCriteria(completionCriteria);

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

        initialState = inversionInputGenerator.getInitialSolution();

        if (repeatable) {
            inversionThreadsPerSelector = 1;
            inversionNumSolutionAverages = 1;
        }

        if (inversionAveragingEnabled) {

            List<CompletionCriteria> criteriaList = new ArrayList<>();
            if (inversionAveragingIterations != null) {
                criteriaList.add(new IterationCompletionCriteria(inversionAveragingIterations));
            }
            if (inversionAveragingIntervalSecs != null && !repeatable) {
                criteriaList.add(
                        TimeCompletionCriteria.getInSeconds(this.inversionAveragingIntervalSecs));
            }
            CompletionCriteria avgSubCompletionCriteria =
                    new CompoundCompletionCriteria(criteriaList);

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

        // From CLI metadata Analysis
        initialState = Arrays.copyOf(initialState, initialState.length);

        //	tsa.setCheckPointCriteria(new TimeCompletionCriteria(1),new File("/tmp/checkpoint/"));

        tsa.iterate(progress);

        tsa.shutdown();

        if (completionCriteria instanceof Closeable) {
            ((Closeable) completionCriteria).close();
        }

        // now assemble the solution
        double[] solution_raw = tsa.getBestSolution();

        // adjust for minimum rates if applicable
        double[] solution_adjusted =
                inversionInputGenerator.adjustSolutionForWaterLevel(solution_raw);

        Set<Integer> zeroRates = new HashSet<>();
        for (int r = 0; r < solution_adjusted.length; r++) {
            if (solution_adjusted[r] == 0) {
                zeroRates.add(r);
            }
        }
        printRuptureExclusionStats(zeroRates, "rates_");

        solution = new FaultSystemSolution(rupSet, solution_adjusted);
        solution.addModule(progress.getProgress());
        solution.addModule(NZSHM22_AbstractRuptureSetBuilder.createBuildInfo());
        if (tsa instanceof ReweightEvenFitSimulatedAnnealing) {
            solution.addModule(((ReweightEvenFitSimulatedAnnealing) tsa).getMisfitProgress());
        }
        return solution;
    }

    public List<List<String>> getTabularSolutionMfds() {
        return TabularMfds.getTabularSolutionMfds(
                solution, scalingRelationship.getRegime() == FaultRegime.CRUSTAL);
    }

    public List<List<String>> getTabularSolutionMfdsV2() {
        return TabularMfds.getTabularSolutionMfdsV2(solution);
    }
}

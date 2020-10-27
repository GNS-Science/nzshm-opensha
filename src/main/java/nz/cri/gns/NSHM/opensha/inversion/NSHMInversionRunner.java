package nz.cri.gns.NSHM.opensha.inversion;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import nz.cri.gns.NSHM.opensha.ruptures.NSHMSlipEnabledRuptureSet;
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
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;
import scratch.UCERF3.inversion.UCERF3InversionConfiguration;
import scratch.UCERF3.simulatedAnnealing.ConstraintRange;
import scratch.UCERF3.simulatedAnnealing.ThreadedSimulatedAnnealing;
import scratch.UCERF3.simulatedAnnealing.completion.CompletionCriteria;
import scratch.UCERF3.simulatedAnnealing.completion.TimeCompletionCriteria;
import scratch.UCERF3.utils.FaultSystemIO;
import scratch.UCERF3.utils.MFD_InversionConstraint;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Runs the standard NSHM inversion on a rupture set.
 */
public class NSHMInversionRunner {

    protected long inversionMins = 1;
    protected long syncInterval = 10;
    protected int numThreads = Runtime.getRuntime().availableProcessors();

    /**
     * Creates a new NSHMInversionRunner with defaults.
     */
    public NSHMInversionRunner() {
    }

    /**
     * Sets how many minutes the inversion runs for.
     * Default is 1 minute.
     * @param inversionMinutes the duration of the inversion in minutes.
     * @return this runner.
     */
    public NSHMInversionRunner setInversionMinutes(long inversionMinutes) {
        this.inversionMins = inversionMinutes;
        return this;
    }

    /**
     * Sets the length of time between syncs in seconds.
     * Default is 10 seconds.
     * @param syncInterval the interval in seconds.
     * @return this runner.
     */
    public NSHMInversionRunner setSyncInterval(long syncInterval) {
        this.syncInterval = syncInterval;
        return this;
    }

    /**
     * Sets how many threads the inversion will try to use.
     * Default is all available processors / cores.
     * @param numThreads the number of threads.
     * @return this runner.
     */
    public NSHMInversionRunner setNumThreads(int numThreads) {
        this.numThreads = numThreads;
        return this;
    }

    @SuppressWarnings("unchecked")
    protected NSHMSlipEnabledRuptureSet loadRupSet(File file) throws IOException, DocumentException {
        FaultSystemRupSet fsRupSet = FaultSystemIO.loadRupSet(file);
        return new NSHMSlipEnabledRuptureSet(
                fsRupSet.getClusterRuptures(),
                (List<FaultSection>) fsRupSet.getFaultSectionDataList(),
                ScalingRelationships.SHAW_2009_MOD,
                SlipAlongRuptureModels.UNIFORM);
    }

    /**
     * Runs the inversion on the specified rupture set.
     * @param ruptureSetFile a rupture set file.
     * @return the FaultSystemSolution.
     * @throws IOException
     * @throws DocumentException
     */
    public FaultSystemSolution run(File ruptureSetFile) throws IOException, DocumentException {

        NSHMSlipEnabledRuptureSet rupSet = loadRupSet(ruptureSetFile);

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
        UCERF3InversionConfiguration.SlipRateConstraintWeightingType slipRateWeighting = UCERF3InversionConfiguration.SlipRateConstraintWeightingType.BOTH; // (recommended: BOTH)
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
                bValue, totalRateM5, mfdTransitionMag, mfdMax, mfd.size() - equalityMFD.size());
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
                System.out.println("\t" + range.name + ": " + energies.get(range).floatValue());
        }

        return new FaultSystemSolution(rupSet, solution_adjusted);
    }
}

package nz.cri.gns.NZSHM22.opensha.inversion.joint;

import com.google.common.base.Preconditions;
import java.io.Closeable;
import java.io.IOException;
import java.util.*;
import nz.cri.gns.NZSHM22.opensha.inversion.LoggingCompletionCriteria;
import nz.cri.gns.NZSHM22.opensha.ruptures.NZSHM22_AbstractRuptureSetBuilder;
import org.dom4j.DocumentException;
import org.opensha.commons.data.IntegerSampler;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionInputGenerator;
import org.opensha.sha.earthquake.faultSysSolution.inversion.Inversions;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.ReweightEvenFitSimulatedAnnealing;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.SimulatedAnnealing;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.ThreadedSimulatedAnnealing;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.*;
import scratch.UCERF3.inversion.UCERF3InversionConfiguration;

public class Annealer {

    AnnealingConfig config;
    FaultSystemRupSet rupSet;

    public Annealer(AnnealingConfig config, FaultSystemRupSet rupSet) {
        this.config = config;
        this.rupSet = rupSet;
    }

    protected IntegerSampler createSampler() {
        Set<Integer> exclusions = new HashSet<>();
        for (int r = 0; r < rupSet.getNumRuptures(); r++) {
            // FIXME work out what to do
            //            if (rupSet.isRuptureBelowSectMinMag(r)) {
            //                exclusions.add(r);
            //            }
        }
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
        if (!config.repeatable)
            completionCriterias.add(TimeCompletionCriteria.getInSeconds(config.inversionSecs));
        if (!(config.energyChangeCompletionCriteria == null))
            completionCriterias.add(config.energyChangeCompletionCriteria);
        if (!(config.iterationCompletionCriteria == null))
            completionCriterias.add(config.iterationCompletionCriteria);

        CompletionCriteria completionCriteria = new CompoundCompletionCriteria(completionCriterias);

        if (config.logStates != null) {
            completionCriteria =
                    new LoggingCompletionCriteria(completionCriteria, config.logStates, 500);
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
        if (config.selectionIterations != null) {
            subCompletionCriteria = new IterationCompletionCriteria(config.selectionIterations);
        } else {
            subCompletionCriteria = TimeCompletionCriteria.getInSeconds(config.selectionInterval);
        }
        return subCompletionCriteria;
    }

    protected CompletionCriteria createAvgSubCompletionCriteria() {
        List<CompletionCriteria> criteriaList = new ArrayList<>();
        if (config.inversionAveragingIterations != null) {
            criteriaList.add(new IterationCompletionCriteria(config.inversionAveragingIterations));
        }
        if (config.inversionAveragingIntervalSecs != null && !config.repeatable) {
            criteriaList.add(
                    TimeCompletionCriteria.getInSeconds(config.inversionAveragingIntervalSecs));
        }
        return new CompoundCompletionCriteria(criteriaList);
    }

    /**
     * Runs the inversion on the specified rupture set.
     *
     * @return the FaultSystemSolution.
     * @throws IOException
     * @throws DocumentException
     */
    public FaultSystemSolution runInversion(InversionInputGenerator inversionInputGenerator)
            throws IOException, DocumentException {
        config.inversionInputGenerator = inversionInputGenerator;
        UCERF3InversionConfiguration.setMagNorm(8.1);

        if (config.repeatable) {
            Preconditions.checkState(
                    config.iterationCompletionCriteria != null
                            || config.energyChangeCompletionCriteria != null);
            Preconditions.checkState(config.selectionIterations != null);
        }

        inversionInputGenerator.generateInputs(true);
        // column compress it for fast annealing
        inversionInputGenerator.columnCompress();

        CompletionCriteria completionCriteria = createCompletionCriteria();

        ProgressTrackingCompletionCriteria progress =
                new ProgressTrackingCompletionCriteria(completionCriteria);

        CompletionCriteria subCompletionCriteria = createSubCompletionCriteria();

        if (config.repeatable) {
            config.inversionThreadsPerSelector = 1;
            config.inversionNumSolutionAverages = 1;
        }

        // Files.writeString(Path.of("A.txt"), inversionInputGenerator.getA().toString());
        // Files.writeString(Path.of("D.txt"), Arrays.toString(inversionInputGenerator.getD()));
        //        Files.writeString(Path.of("A_ineq.txt"),
        // inversionInputGenerator.getA_ineq().toString());
        //        Files.writeString(Path.of("D_ineq.txt"),
        // Arrays.toString(inversionInputGenerator.getD_ineq()));

        ThreadedSimulatedAnnealing tsa;

        if (config.inversionAveragingEnabled) {

            CompletionCriteria avgSubCompletionCriteria = createAvgSubCompletionCriteria();

            // arrange lower-level (actual worker) SAs
            List<SimulatedAnnealing> tsas = new ArrayList<>();
            for (int i = 0; i < config.inversionNumSolutionAverages; i++) {
                tsas.add(
                        new ThreadedSimulatedAnnealing(
                                inversionInputGenerator.getA(),
                                inversionInputGenerator.getD(),
                                inversionInputGenerator.getInitialSolution(),
                                0d,
                                inversionInputGenerator.getA_ineq(),
                                inversionInputGenerator.getD_ineq(),
                                config.inversionThreadsPerSelector,
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
                            config.inversionThreadsPerSelector,
                            subCompletionCriteria);
        }
        progress.setConstraintRanges(inversionInputGenerator.getConstraintRowRanges());

        if (completionCriteria instanceof LoggingCompletionCriteria) {
            ((LoggingCompletionCriteria) completionCriteria)
                    .setConstraintRanges(inversionInputGenerator.getConstraintRowRanges())
                    .open();
        }

        tsa.setConstraintRanges(inversionInputGenerator.getConstraintRowRanges());
        if (config.reweightTargetQuantity != null) {
            tsa = new ReweightEvenFitSimulatedAnnealing(tsa, config.reweightTargetQuantity);
        }

        if (config.repeatable) {
            tsa.setRandom(new Random(1));
        }

        tsa.setPerturbationFunc(config.perturbationFunction);
        if (config.perturbationFunction.isVariable()) {
            double[] basis = config.variablePerturbationBasis;
            if (basis == null) {
                basis = Inversions.getDefaultVariablePerturbationBasis(rupSet);
            }
            tsa.setVariablePerturbationBasis(basis);
        }

        tsa.setNonnegativeityConstraintAlgorithm(config.nonNegAlgorithm);
        if (!(config.coolingSchedule == null)) tsa.setCoolingFunc(config.coolingSchedule);

        if (config.excludeRupturesBelowMinMag) {

            IntegerSampler sampler = createSampler();
            if (sampler != null) {
                tsa.setRuptureSampler(sampler);
            }
        }
        tsa.iterate(progress);
        tsa.shutdown();

        if (completionCriteria instanceof Closeable) {
            ((Closeable) completionCriteria).close();
        }

        return createSolution(tsa, progress);
    }

    protected FaultSystemSolution createSolution(
            ThreadedSimulatedAnnealing tsa, ProgressTrackingCompletionCriteria progress)
            throws IOException {
        // now assemble the solution
        double[] solution_raw = tsa.getBestSolution();

        // adjust for minimum rates if applicable
        double[] solution_adjusted =
                config.inversionInputGenerator.adjustSolutionForWaterLevel(solution_raw);

        FaultSystemSolution solution = new FaultSystemSolution(rupSet, solution_adjusted);
        solution.addModule(progress.getProgress());
        solution.addModule(NZSHM22_AbstractRuptureSetBuilder.createBuildInfo());
        if (tsa instanceof ReweightEvenFitSimulatedAnnealing) {
            solution.addModule(((ReweightEvenFitSimulatedAnnealing) tsa).getMisfitProgress());
        }
        return solution;
    }
}

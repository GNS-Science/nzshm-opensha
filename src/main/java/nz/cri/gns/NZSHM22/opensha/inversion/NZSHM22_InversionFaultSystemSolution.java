package nz.cri.gns.NZSHM22.opensha.inversion;

import org.dom4j.DocumentException;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.sha.magdist.ArbIncrementalMagFreqDist;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;

import scratch.UCERF3.griddedSeismicity.GridSourceProvider;
import scratch.UCERF3.griddedSeismicity.UCERF3_GridSourceGenerator;
import scratch.UCERF3.inversion.*;
import scratch.UCERF3.simulatedAnnealing.ConstraintRange;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;

import nz.cri.gns.NZSHM22.opensha.griddedSeismicity.NZSHM22_GridSourceGenerator;
import scratch.UCERF3.utils.U3FaultSystemIO;

public class NZSHM22_InversionFaultSystemSolution extends InversionFaultSystemSolution {

    private NZSHM22_InversionFaultSystemSolution(NZSHM22_InversionFaultSystemRuptSet rupSet, double[] rates,
                                                 UCERF3InversionConfiguration config, Map<String, Double> energies) {
        super(rupSet, rates, config, energies);

        removeAvailableModuleInstances(GridSourceProvider.class);
        addAvailableModule(new Callable<NZSHM22_GridSourceGenerator>() {
            @Override
            public NZSHM22_GridSourceGenerator call() throws Exception {
                return new NZSHM22_GridSourceGenerator(NZSHM22_InversionFaultSystemSolution.this);
            }
        }, GridSourceProvider.class);
    }

    /**
     * Can be used on the fly for when InversionConfiguration is not available/relevant/fit for use
     *
     * @param rupSet
     * @param rates
     * @param energies
     */
    private NZSHM22_InversionFaultSystemSolution(NZSHM22_InversionFaultSystemRuptSet rupSet,
                                                 double[] rates, Map<String, Double> energies) {
        this(rupSet, rates, null, energies);
    }

    public NZSHM22_InversionFaultSystemRuptSet getNZRuptSet() {
        return (NZSHM22_InversionFaultSystemRuptSet) getRupSet();
    }

    public static NZSHM22_InversionFaultSystemSolution fromCrustalSolution(InversionFaultSystemSolution solution) {

        NZSHM22_InversionFaultSystemSolution ifss = new NZSHM22_InversionFaultSystemSolution(
                NZSHM22_InversionFaultSystemRuptSet.fromCrustal(solution.getRupSet(), solution.getLogicTreeBranch()),
                solution.getRateForAllRups(),
                solution.getInversionConfiguration(),
                solution.getEnergies());

        return ifss;
    }

    public static NZSHM22_InversionFaultSystemSolution fromSubductionSolution(InversionFaultSystemSolution solution) {

        NZSHM22_InversionFaultSystemSolution ifss = new NZSHM22_InversionFaultSystemSolution(
                NZSHM22_InversionFaultSystemRuptSet.fromSubduction(solution.getRupSet(), solution.getLogicTreeBranch()),
                solution.getRateForAllRups(),
                solution.getInversionConfiguration(),
                solution.getEnergies());

        return ifss;
    }

    public static NZSHM22_InversionFaultSystemSolution fromCrustalFile(File file) throws DocumentException, IOException {
        // FIXME old style loading
        return fromCrustalSolution(U3FaultSystemIO.loadInvSol(file));
    }

    public static NZSHM22_InversionFaultSystemSolution fromSubductionFile(File file) throws DocumentException, IOException {
        // FIXME old style loading
        return fromSubductionSolution(U3FaultSystemIO.loadInvSol(file));
    }

    public SummedMagFreqDist calcNucleationMFD_forParentSect(Set<Integer> parentSectionIDs, double minMag, double maxMag, int numMag) {
        SummedMagFreqDist mfd = new SummedMagFreqDist(minMag, maxMag, numMag);
        InversionFaultSystemRupSet rupSet = getRupSet();
        for (int sectIndex = 0; sectIndex < rupSet.getNumSections(); sectIndex++) {
            if (parentSectionIDs.contains(rupSet.getFaultSectionData(sectIndex).getParentSectionId())) {
                IncrementalMagFreqDist subMFD = calcNucleationMFD_forSect(sectIndex, minMag, maxMag, numMag);
                mfd.addIncrementalMagFreqDist(subMFD);
            }
        }
        return mfd;
    }

    public IncrementalMagFreqDist calcParticipationMFD_forParentSect(Set<Integer> parentSectionIDs, double minMag, double maxMag, int numMag) {
        ArbIncrementalMagFreqDist mfd = new ArbIncrementalMagFreqDist(minMag, maxMag, numMag);
        InversionFaultSystemRupSet rupSet = getRupSet();
        Set<Integer> rups = new HashSet<>();
        for (int parentSectionID : parentSectionIDs) {
//            System.out.println(parentSectionID);
//            System.out.println(rupSet);
            List<Integer> ruptures = rupSet.getRupturesForParentSection(parentSectionID);
            if (ruptures != null) {
                rups.addAll(ruptures);
            } else {
                System.out.println("nothing for " + parentSectionID);
            }
        }
        for (int r : rups) {
            DiscretizedFunc rupMagDist = getRupMagDist(r);
            if (rupMagDist == null)
                mfd.addResampledMagRate(rupSet.getMagForRup(r), getRateForRup(r), true);
            else
                for (Point2D pt : rupMagDist)
                    mfd.addResampledMagRate(pt.getX(), pt.getY(), true);
        }
        return mfd;
    }

    public SummedMagFreqDist getFinalSubSeismoOnFaultMFDForSects(Set<Integer> parentSectionIDs) {

        SummedMagFreqDist mfd = new SummedMagFreqDist(U3InversionTargetMFDs.MIN_MAG, U3InversionTargetMFDs.NUM_MAG, U3InversionTargetMFDs.DELTA_MAG);
        InversionFaultSystemRupSet rupSet = getRupSet();

        List<? extends IncrementalMagFreqDist> subSeismoMFDs = getFinalSubSeismoOnFaultMFD_List();

        for (int sectIndex = 0; sectIndex < rupSet.getNumSections(); sectIndex++) {
            if (parentSectionIDs.contains(rupSet.getFaultSectionData(sectIndex).getParentSectionId())) {
                mfd.addIncrementalMagFreqDist(subSeismoMFDs.get(sectIndex));
            }
        }

        return mfd;
    }

}

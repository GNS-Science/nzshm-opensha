package nz.cri.gns.NZSHM22.opensha.inversion;

import com.google.common.base.Preconditions;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.FaultRegime;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_LogicTreeBranch;
import org.dom4j.DocumentException;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.magdist.ArbIncrementalMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionTargetMFDs;

import scratch.UCERF3.inversion.*;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;

import nz.cri.gns.NZSHM22.opensha.griddedSeismicity.NZSHM22_GridSourceGenerator;

public class NZSHM22_InversionFaultSystemSolution extends InversionFaultSystemSolution {

    private NZSHM22_InversionFaultSystemSolution(FaultSystemSolution solution,
                                                 NZSHM22_InversionFaultSystemRuptSet rupSet,
                                                 UCERF3InversionConfiguration config, Map<String, Double> energies) {
        super(rupSet, solution.getRateForAllRups(), config, energies);

        for (OpenSHA_Module module : solution.getModules(true))
            addModule(module);

        removeAvailableModuleInstances(GridSourceProvider.class);
        addAvailableModule(new Callable<NZSHM22_GridSourceGenerator>() {
            @Override
            public NZSHM22_GridSourceGenerator call() throws Exception {
                return new NZSHM22_GridSourceGenerator(NZSHM22_InversionFaultSystemSolution.this);
            }
        }, GridSourceProvider.class);

    }

    /**
     * Loads a crustal or subduction solution file. Must be modular.
     * If file is not modular, will attempt to load it as crustal.
     * @param solutionFile
     * @return
     * @throws IOException
     */
    public static NZSHM22_InversionFaultSystemSolution fromFile(File solutionFile) throws IOException {
        FaultSystemSolution solution = FaultSystemSolution.load(solutionFile);
        NZSHM22_LogicTreeBranch branch = solution.getRupSet().getModule(NZSHM22_LogicTreeBranch.class);
        if (branch == null) {
            // fallback to crustal for pre-modular solutions
            return fromCrustalSolution(solution);
        } else {
            FaultRegime regime = branch.getValue(FaultRegime.class);
            Preconditions.checkArgument(regime != null);
            if (regime == FaultRegime.SUBDUCTION) {
                return fromSubductionSolution(solution);
            } else {
                return fromCrustalSolution(solution);
            }
        }
    }

    public static NZSHM22_InversionFaultSystemSolution fromCrustalSolution(FaultSystemSolution solution) {
        FaultSystemRupSet rupSet = solution.getRupSet();
        NZSHM22_LogicTreeBranch branch = NZSHM22_LogicTreeBranch.crustalFromModuleContainer(rupSet);
        NZSHM22_InversionFaultSystemRuptSet nzRupSet = new NZSHM22_InversionFaultSystemRuptSet(rupSet, branch);

        NZSHM22_InversionFaultSystemSolution ifss = new NZSHM22_InversionFaultSystemSolution(
                solution,
                nzRupSet,
                null,
                null);

        return ifss;
    }

    public static NZSHM22_InversionFaultSystemSolution fromSubductionSolution(FaultSystemSolution solution) {

        FaultSystemRupSet rupSet = solution.getRupSet();
        NZSHM22_LogicTreeBranch branch = NZSHM22_LogicTreeBranch.subductionFromModuleContainer(rupSet);
        NZSHM22_InversionFaultSystemRuptSet nzRupSet = new NZSHM22_InversionFaultSystemRuptSet(rupSet, branch);

        NZSHM22_InversionFaultSystemSolution ifss = new NZSHM22_InversionFaultSystemSolution(
                solution,
                nzRupSet,
                null,
                null);

        return ifss;
    }

    public static NZSHM22_InversionFaultSystemSolution fromCrustalFile(File file) throws DocumentException, IOException {
        return fromCrustalSolution(FaultSystemSolution.load(file));
    }

    public static NZSHM22_InversionFaultSystemSolution fromSubductionFile(File file) throws DocumentException, IOException {
        return fromSubductionSolution(FaultSystemSolution.load(file));
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

        List<? extends IncrementalMagFreqDist> subSeismoMFDs = rupSet.getModule(InversionTargetMFDs.class).getOnFaultSubSeisMFDs().getAll();

        for (int sectIndex = 0; sectIndex < rupSet.getNumSections(); sectIndex++) {
            if (parentSectionIDs.contains(rupSet.getFaultSectionData(sectIndex).getParentSectionId())) {
                mfd.addIncrementalMagFreqDist(subSeismoMFDs.get(sectIndex));
            }
        }

        return mfd;
    }

    @Override
    public IncrementalMagFreqDist getFinalTrulyOffFaultMFD() {
        //FIXME, not sure this is the right behaviour, see superclass
        IncrementalMagFreqDist finalTrulyOffMFD = getRupSet().getModule(InversionTargetMFDs.class).getTrulyOffFaultMFD().deepClone();
        finalTrulyOffMFD.setName("InversionFaultSystemSolution.getFinalTrulyOffFaultMFD()");
        finalTrulyOffMFD.setInfo("identical to inversionTargetMFDs.getTrulyOffFaultMFD() in this case");
        return finalTrulyOffMFD;
    }

    @Override
    public List<? extends IncrementalMagFreqDist> getFinalSubSeismoOnFaultMFD_List() {
        //FIXME, not sure this is the right behaviour, see superclass
        return getRupSet().getModule(InversionTargetMFDs.class).getOnFaultSubSeisMFDs().getAll();
    }

}

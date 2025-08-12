package nz.cri.gns.NZSHM22.util;

import java.awt.geom.Point2D;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import nz.cri.gns.NZSHM22.opensha.inversion.NZSHM22_InversionFaultSystemRuptSet;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionTargetMFDs;
import org.opensha.sha.magdist.ArbIncrementalMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;
import scratch.UCERF3.inversion.U3InversionTargetMFDs;

public class MFDPlotCalc {

    public static SummedMagFreqDist calcNucleationMFD_forParentSect(
            FaultSystemSolution solution,
            Set<Integer> parentSectionIDs,
            double minMag,
            double maxMag,
            int numMag) {
        FaultSystemRupSet rupSet = solution.getRupSet();

        SummedMagFreqDist mfd = new SummedMagFreqDist(minMag, maxMag, numMag);
        for (int sectIndex = 0; sectIndex < rupSet.getNumSections(); sectIndex++) {
            if (parentSectionIDs.contains(
                    rupSet.getFaultSectionData(sectIndex).getParentSectionId())) {
                IncrementalMagFreqDist subMFD =
                        calcNucleationMFD_forSect(solution, sectIndex, minMag, maxMag, numMag);
                mfd.addIncrementalMagFreqDist(subMFD);
            }
        }
        return mfd;
    }

    /**
     * This give a Nucleation Mag Freq Dist (MFD) for the specified section. Nucleation probability
     * is defined as the area of the section divided by the area of the rupture. This preserves
     * rates rather than moRates (can't have both)
     *
     * @param sectIndex
     * @param minMag - lowest mag in MFD
     * @param maxMag - highest mag in MFD
     * @param numMag - number of mags in MFD
     * @return IncrementalMagFreqDist
     */
    public static IncrementalMagFreqDist calcNucleationMFD_forSect(
            FaultSystemSolution solution, int sectIndex, double minMag, double maxMag, int numMag) {
        FaultSystemRupSet rupSet = solution.getRupSet();
        ArbIncrementalMagFreqDist mfd = new ArbIncrementalMagFreqDist(minMag, maxMag, numMag);
        List<Integer> rups = rupSet.getRupturesForSection(sectIndex);
        if (rups != null) {
            for (int r : rups) {
                double nucleationScalar =
                        rupSet.getAreaForSection(sectIndex) / rupSet.getAreaForRup(r);
                DiscretizedFunc rupMagDist = null; // getRupMagDist(r);
                if (rupMagDist == null)
                    mfd.addResampledMagRate(
                            rupSet.getMagForRup(r),
                            solution.getRateForRup(r) * nucleationScalar,
                            true);
                else
                    for (Point2D pt : rupMagDist)
                        mfd.addResampledMagRate(pt.getX(), pt.getY() * nucleationScalar, true);
            }
        }
        return mfd;
    }

    public static IncrementalMagFreqDist calcParticipationMFD_forParentSect(
            FaultSystemSolution solution,
            Set<Integer> parentSectionIDs,
            double minMag,
            double maxMag,
            int numMag) {
        ArbIncrementalMagFreqDist mfd = new ArbIncrementalMagFreqDist(minMag, maxMag, numMag);
        FaultSystemRupSet rupSet = solution.getRupSet();
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
            DiscretizedFunc rupMagDist = null; // getRupMagDist(r);
            if (rupMagDist == null)
                mfd.addResampledMagRate(rupSet.getMagForRup(r), solution.getRateForRup(r), true);
            else for (Point2D pt : rupMagDist) mfd.addResampledMagRate(pt.getX(), pt.getY(), true);
        }
        return mfd;
    }

    public static SummedMagFreqDist getFinalSubSeismoOnFaultMFDForSects(
            FaultSystemSolution solution, Set<Integer> parentSectionIDs) {

        NZSHM22_InversionFaultSystemRuptSet.checkMFDConsistency(solution.getRupSet());

        SummedMagFreqDist mfd =
                new SummedMagFreqDist(
                        U3InversionTargetMFDs.MIN_MAG,
                        U3InversionTargetMFDs.NUM_MAG,
                        U3InversionTargetMFDs.DELTA_MAG);
        FaultSystemRupSet rupSet = solution.getRupSet();

        List<? extends IncrementalMagFreqDist> subSeismoMFDs =
                rupSet.getModule(InversionTargetMFDs.class).getOnFaultSubSeisMFDs().getAll();

        for (int sectIndex = 0; sectIndex < rupSet.getNumSections(); sectIndex++) {
            if (parentSectionIDs.contains(
                    rupSet.getFaultSectionData(sectIndex).getParentSectionId())) {
                mfd.addIncrementalMagFreqDist(subSeismoMFDs.get(sectIndex));
            }
        }

        return mfd;
    }
}

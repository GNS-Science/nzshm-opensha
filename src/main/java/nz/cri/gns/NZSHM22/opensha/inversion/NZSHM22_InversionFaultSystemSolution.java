package nz.cri.gns.NZSHM22.opensha.inversion;

import org.dom4j.DocumentException;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.sha.magdist.ArbIncrementalMagFreqDist;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;

import scratch.UCERF3.griddedSeismicity.GridSourceProvider;
import scratch.UCERF3.griddedSeismicity.UCERF3_GridSourceGenerator;
import scratch.UCERF3.inversion.InversionFaultSystemRupSet;
import scratch.UCERF3.inversion.InversionFaultSystemSolution;
import scratch.UCERF3.inversion.InversionTargetMFDs;
import scratch.UCERF3.inversion.UCERF3InversionConfiguration;
import scratch.UCERF3.simulatedAnnealing.ConstraintRange;
import scratch.UCERF3.utils.FaultSystemIO;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.*;

import nz.cri.gns.NZSHM22.opensha.griddedSeismicity.NZSHM22_GridSourceGenerator;

public class NZSHM22_InversionFaultSystemSolution extends InversionFaultSystemSolution {

    private NZSHM22_InversionFaultSystemSolution(InversionFaultSystemRupSet rupSet, double[] rates,
                                                 UCERF3InversionConfiguration config, Map<String, Double> energies) {
        super(new NZSHM22_InversionFaultSystemRuptSet(rupSet, rupSet.getLogicTreeBranch()), rates, config, energies);
    }

	/**
	 * Can be used on the fly for when InversionConfiguration is not available/relevant/fit for use
     * @param rupSet
     * @param rates
     * @param energies
     */
    public NZSHM22_InversionFaultSystemSolution(NZSHM22_InversionFaultSystemRuptSet rupSet,
			double[] rates, Map<String, Double> energies) {
    	this(rupSet, rates, null, energies);
	}   
    
	public static NZSHM22_InversionFaultSystemSolution fromSolution(InversionFaultSystemSolution solution) {

        NZSHM22_InversionFaultSystemSolution ifss = new NZSHM22_InversionFaultSystemSolution(
                solution.getRupSet(),
                solution.getRateForAllRups(),
                solution.getInversionConfiguration(),
                solution.getEnergies());

        ifss.setGridSourceProvider(new NZSHM22_GridSourceGenerator(ifss));
        return ifss;
    }

    public static NZSHM22_InversionFaultSystemSolution fromFile(File file) throws DocumentException, IOException {
        return fromSolution(FaultSystemIO.loadInvSol(file));
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

        SummedMagFreqDist mfd = new SummedMagFreqDist(InversionTargetMFDs.MIN_MAG, InversionTargetMFDs.NUM_MAG, InversionTargetMFDs.DELTA_MAG);
        InversionFaultSystemRupSet rupSet = getRupSet();

        List<GutenbergRichterMagFreqDist> subSeismoMFDs = getFinalSubSeismoOnFaultMFD_List();

        for (int sectIndex = 0; sectIndex < rupSet.getNumSections(); sectIndex++) {
            if (parentSectionIDs.contains(rupSet.getFaultSectionData(sectIndex).getParentSectionId())) {
                mfd.addIncrementalMagFreqDist(subSeismoMFDs.get(sectIndex));
            }
        }

        return mfd;
    }
    
	/**
	 * Returns GridSourceProvider - unlike UCERf3 this does not create a provider if it's not set, 
	 * as Subduction has no GridSource Provider
	 * 
	 * @return
	 */
	public GridSourceProvider getGridSourceProvider() {
		return gridSourceProvider;
	}    
    
}

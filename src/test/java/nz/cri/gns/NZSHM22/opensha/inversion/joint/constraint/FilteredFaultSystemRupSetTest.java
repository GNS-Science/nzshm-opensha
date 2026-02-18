package nz.cri.gns.NZSHM22.opensha.inversion.joint.constraint;

import static nz.cri.gns.NZSHM22.opensha.util.TestHelpers.createRupSet;
import static org.junit.Assert.*;

import java.io.IOException;
import java.util.List;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionPredicate;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.constraints.FilteredFaultSystemRupSet;
import nz.cri.gns.NZSHM22.opensha.ruptures.FaultSectionProperties2;
import org.dom4j.DocumentException;
import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.AveSlipModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;

public class FilteredFaultSystemRupSetTest {

    static final double DELTA = 0.00000001;

    static final int CRU_SECTION = 0;
    static final int SUB_SECTION = 1;

    /**
     * Create rupture set with one crustal and one subduction fault section, and three ruptures:
     * crustal, subduction, and joint.
     */
    public static FaultSystemRupSet makeRupSet() throws DocumentException, IOException {
        FaultSystemRupSet rupSet =
                createRupSet(
                        NZSHM22_FaultModels.CFM_1_0A_DOM_ALL,
                        // shimmying crustalised joint scaling relationship in here so that we have
                        // simpler assertions
                        ScalingRelationships.SHAW_2009_MOD,
                        List.of(
                                List.of(CRU_SECTION),
                                List.of(SUB_SECTION),
                                List.of(CRU_SECTION, SUB_SECTION)));

        rupSet.getFaultSectionDataList().removeIf((s) -> s.getSectionId() > 1);

        FaultSectionProperties2 props =
                new FaultSectionProperties2(rupSet.getFaultSectionData(CRU_SECTION));
        props.setPartition(PartitionPredicate.CRUSTAL);
        props = new FaultSectionProperties2(rupSet.getFaultSectionData(SUB_SECTION));
        props.setPartition(PartitionPredicate.HIKURANGI);

        double[] aveSlipData = new double[rupSet.getNumRuptures()];
        aveSlipData[0] = 1;
        aveSlipData[1] = 2;
        aveSlipData[2] = 3;
        double[] slipRateData = new double[rupSet.getNumSections()];
        slipRateData[0] = 1;
        slipRateData[1] = 2;
        double[] slipStdvData = new double[rupSet.getNumSections()];
        slipStdvData[0] = 1;
        slipStdvData[1] = 2;
        AveSlipModule aveSlip = AveSlipModule.precomputed(rupSet, aveSlipData);
        rupSet.addModule(aveSlip);
        rupSet.addModule(SlipAlongRuptureModels.UNIFORM.getModel());
        SectSlipRates targets = SectSlipRates.precomputed(rupSet, slipRateData, slipStdvData);
        rupSet.addModule(targets);

        return rupSet;
    }

    @Test
    public void magTest() throws DocumentException, IOException {
        FaultSystemRupSet original = makeRupSet();
        FaultSystemRupSet rupSet =
                FilteredFaultSystemRupSet.forIntPredicate(
                        original,
                        PartitionPredicate.CRUSTAL.getPredicate(original),
                        ScalingRelationships.SHAW_2009_MOD);

        // magnitudes are only calculated for crustal parts of ruptures
        assertEquals(original.getMagForRup(0), rupSet.getMagForRup(0), DELTA);
        assertEquals(original.getMagForRup(0), rupSet.getMagForRup(1), DELTA);

        // minMag ignores zero magnitudes
        assertEquals(original.getMagForRup(0), rupSet.getMinMag(), DELTA);
    }

    public static int[] toArray(List<Integer> values) {
        return values.stream().mapToInt(i -> i).toArray();
    }

    @Test
    public void filterTest() throws DocumentException, IOException {
        FaultSystemRupSet original = makeRupSet();
        FilteredFaultSystemRupSet rupSet =
                FilteredFaultSystemRupSet.forIntPredicate(
                        original,
                        PartitionPredicate.CRUSTAL.getPredicate(original),
                        ScalingRelationships.SHAW_2009_MOD);

        assertEquals(1, rupSet.getNumSections());
        assertEquals(0, rupSet.getFaultSectionData(0).getSectionId());
        assertEquals(2, rupSet.getNumRuptures());
        assertArrayEquals(new int[] {0}, toArray(rupSet.getSectionsIndicesForRup(0)));
        assertArrayEquals(new int[] {0}, toArray(rupSet.getSectionsIndicesForRup(1)));

        assertEquals(0, rupSet.getOldRuptureId(0));
        assertEquals(2, rupSet.getOldRuptureId(1));
    }
}

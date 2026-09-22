package nz.cri.gns.NZSHM22.opensha.inversion;

import static nz.cri.gns.NZSHM22.opensha.util.TestHelpers.makeRupSet;
import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import nz.cri.gns.NZSHM22.opensha.calc.SimplifiedScalingRelationship;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_LogicTreeBranch;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_ScalingRelationshipNode;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_SlipRateFactors;
import nz.cri.gns.NZSHM22.opensha.faults.FaultSectionList;
import nz.cri.gns.NZSHM22.opensha.ruptures.FaultSectionProperties;
import nz.cri.gns.NZSHM22.opensha.util.TestHelpers;
import org.dom4j.DocumentException;
import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.RuptureSubSetMappings;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import org.opensha.sha.faultSurface.FaultSection;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;

public class NZSHM22_InversionFaultSystemRuptSetTest {

    public static FaultSystemRupSet modularRupSet() throws DocumentException, IOException {
        NZSHM22_LogicTreeBranch branch = NZSHM22_LogicTreeBranch.crustalInversion();
        NZSHM22_ScalingRelationshipNode scalingNode =
                branch.getValue(NZSHM22_ScalingRelationshipNode.class);
        FaultSystemRupSet rupSet =
                TestHelpers.makeRupSet(NZSHM22_FaultModels.CFM_1_0A_DOM_ALL, scalingNode);
        return NZSHM22_InversionFaultSystemRuptSet.fromExistingCrustalSet(
                rupSet,
                branch,
                NZSHM22_AbstractInversionRunner.MIN_MAG,
                NZSHM22_AbstractInversionRunner.MAX_MAG);
    }

    @Test
    public void testPreserveLTB() throws IOException, DocumentException {
        FaultSystemRupSet rupSet =
                makeRupSet(
                        NZSHM22_FaultModels.CFM_1_0A_DOM_ALL, ScalingRelationships.SHAW_2009_MOD);
        NZSHM22_LogicTreeBranch branch = new NZSHM22_LogicTreeBranch();
        NZSHM22_ScalingRelationshipNode scalingRelationship = new NZSHM22_ScalingRelationshipNode();
        SimplifiedScalingRelationship scaling = new SimplifiedScalingRelationship();
        scaling.setupCrustal(4.0, 4.0);
        scalingRelationship.setScalingRelationship(scaling);

        branch.setValue(NZSHM22_FaultModels.CFM_1_0A_DOM_ALL);
        branch.setValue(scalingRelationship);
        rupSet.addModule(branch);

        NZSHM22_InversionFaultSystemRuptSet actual =
                NZSHM22_InversionFaultSystemRuptSet.fromExistingCrustalSet(
                        rupSet,
                        NZSHM22_LogicTreeBranch.crustalInversion(),
                        NZSHM22_AbstractInversionRunner.MIN_MAG,
                        NZSHM22_AbstractInversionRunner.MAX_MAG);

        NZSHM22_LogicTreeBranch actualBranch = actual.getModule(NZSHM22_LogicTreeBranch.class);

        assertEquals(
                NZSHM22_FaultModels.CFM_1_0A_DOM_ALL,
                actualBranch.getValue(NZSHM22_FaultModels.class));
        assertEquals(
                scaling,
                actualBranch
                        .getValue(NZSHM22_ScalingRelationshipNode.class)
                        .getScalingRelationship());
    }

    @Test
    public void recalcMagsTest() throws IOException, DocumentException {
        FaultSystemRupSet rupSet = modularRupSet();

        // orgMags were calculated with SimplifiedScalingRelationship: crustal, 4.0, 4.1
        double[] origMags =
                Arrays.copyOf(rupSet.getMagForAllRups(), rupSet.getMagForAllRups().length);

        NZSHM22_LogicTreeBranch branch = new NZSHM22_LogicTreeBranch();
        NZSHM22_ScalingRelationshipNode scalingNode = new NZSHM22_ScalingRelationshipNode();
        scalingNode.setScalingRelationship(ScalingRelationships.TMG_CRU_2017);
        scalingNode.setRecalc(true);
        branch.setValue(scalingNode);

        rupSet = NZSHM22_InversionFaultSystemRuptSet.recalcMags(rupSet, branch);
        assertNotEquals(origMags[0], rupSet.getMagForAllRups()[0], 0.00000001);

        // to recreate original values
        SimplifiedScalingRelationship scaling = new SimplifiedScalingRelationship();
        scaling.setupCrustal(4.0, 4.0);
        scalingNode.setScalingRelationship(scaling);
        branch.setValue(scalingNode);
        rupSet = NZSHM22_InversionFaultSystemRuptSet.recalcMags(rupSet, branch);
        assertArrayEquals(origMags, rupSet.getMagForAllRups(), 0.00000001);
    }

    @Test
    public void testRegionSlipScaling() throws IOException, DocumentException {
        FaultSystemRupSet rupSet =
                makeRupSet(
                        NZSHM22_FaultModels.CFM_1_0A_DOM_ALL, ScalingRelationships.SHAW_2009_MOD);

        double[] expected = rupSet.getModule(SectSlipRates.class).getSlipRates().clone();
        NZSHM22_SlipRateFactors factors = new NZSHM22_SlipRateFactors(0.3, 0.4);
        NZSHM22_LogicTreeBranch branch = new NZSHM22_LogicTreeBranch();
        branch.setValue(factors);
        branch.setValue(NZSHM22_FaultModels.CFM_1_0A_DOM_ALL);

        // applySlipRateFactor expects this module to be present
        rupSet.addModule(new TvzDomainSections(rupSet));

        NZSHM22_InversionFaultSystemRuptSet.applySlipRateFactor(rupSet, branch);
        double[] actual = rupSet.getModule(SectSlipRates.class).getSlipRates().clone();

        FaultSectionList parents = new FaultSectionList();
        NZSHM22_FaultModels.CFM_1_0A_DOM_ALL.fetchFaultSections(parents);
        List<? extends FaultSection> sections = rupSet.getFaultSectionDataList();

        for (int i = 0; i < expected.length; i++) {
            if (FaultSectionProperties.getTvz(sections.get(i))) {
                expected[i] *= 0.4;
            } else {
                expected[i] *= 0.3;
            }
        }

        assertArrayEquals(expected, actual, 0);
    }

    /**
     * Creates a crustal rupture set of four ruptures with increasing magnitude, calculated with
     * SHAW_2009_MOD.
     */
    public static FaultSystemRupSet magRangeRupSet() throws DocumentException, IOException {
        return TestHelpers.createRupSet(
                NZSHM22_FaultModels.CFM_1_0A_DOM_ALL,
                ScalingRelationships.SHAW_2009_MOD,
                List.of(List.of(0), List.of(0, 1), List.of(0, 1, 2), List.of(0, 1, 2, 3)));
    }

    /** Creates a branch that recalculates magnitudes with the specified scaling relationship. */
    public static NZSHM22_LogicTreeBranch recalcBranch(ScalingRelationships scaling) {
        NZSHM22_LogicTreeBranch branch = NZSHM22_LogicTreeBranch.crustalInversion();
        NZSHM22_ScalingRelationshipNode scalingNode = new NZSHM22_ScalingRelationshipNode();
        scalingNode.setScalingRelationship(scaling);
        scalingNode.setRecalc(true);
        branch.setValue(scalingNode);
        branch.setValue(NZSHM22_FaultModels.CFM_1_0A_DOM_ALL);
        return branch;
    }

    @Test
    public void testCrustalMagRangeFilterIsAppliedAfterRecalc()
            throws IOException, DocumentException {
        // mags of the rupture set as built: 7.114, 7.204, 7.350, 7.676
        FaultSystemRupSet original = magRangeRupSet();
        NZSHM22_LogicTreeBranch branch = recalcBranch(ScalingRelationships.TMG_CRU_2017);
        // mags after recalculation: 7.145, 7.207, 7.295, 7.543
        double[] recalcMags =
                NZSHM22_InversionFaultSystemRuptSet.recalcMags(magRangeRupSet(), branch)
                        .getMagForAllRups();

        NZSHM22_InversionFaultSystemRuptSet actual =
                NZSHM22_InversionFaultSystemRuptSet.fromExistingCrustalSet(
                        original, branch, 7.3, 7.7);

        // only the recalculated mags fall into the range, the original mags would have kept two
        // ruptures
        assertEquals(1, actual.getNumRuptures());
        assertEquals(recalcMags[3], actual.getMagForRup(0), 0.00000001);

        RuptureSubSetMappings mappings = actual.requireModule(RuptureSubSetMappings.class);
        assertEquals(3, mappings.getOrigRupID(0));
        assertEquals(original.getNumSections(), actual.getNumSections());
    }

    @Test
    public void testCrustalWithFullMagRangeKeepsAllRuptures()
            throws IOException, DocumentException {
        FaultSystemRupSet original = magRangeRupSet();
        NZSHM22_InversionFaultSystemRuptSet actual =
                NZSHM22_InversionFaultSystemRuptSet.fromExistingCrustalSet(
                        original,
                        recalcBranch(ScalingRelationships.TMG_CRU_2017),
                        NZSHM22_AbstractInversionRunner.MIN_MAG,
                        NZSHM22_AbstractInversionRunner.MAX_MAG);
        assertEquals(original.getNumRuptures(), actual.getNumRuptures());
        // nothing was filtered, so the rupture set has not been rebuilt
        assertNull(actual.getModule(RuptureSubSetMappings.class));
    }

    @Test
    public void testSubductionMagRangeFilter() throws IOException, DocumentException {
        FaultSystemRupSet original =
                TestHelpers.createRupSet(
                        NZSHM22_FaultModels.SBD_0_2_HKR_LR_30,
                        ScalingRelationships.TMG_SUB_2017,
                        List.of(
                                List.of(1),
                                List.of(1, 2, 3, 4, 5, 6, 7, 8, 9),
                                List.of(4, 5, 6, 10, 11, 12)));
        // mags of the rupture set: 6.583, 7.589, 7.403
        double[] mags = original.getMagForAllRups();

        NZSHM22_InversionFaultSystemRuptSet actual =
                NZSHM22_InversionFaultSystemRuptSet.fromExistingSubductionRuptureSet(
                        original, NZSHM22_LogicTreeBranch.subductionInversion(), 7.0, 7.5);

        // the smallest and the largest rupture are outside of the range
        assertEquals(1, actual.getNumRuptures());
        assertEquals(mags[2], actual.getMagForRup(0), 0.00000001);
        assertEquals(2, actual.requireModule(RuptureSubSetMappings.class).getOrigRupID(0));
    }

    @Test
    public void testFilterMagsReturnsOriginalWhenNothingIsOutOfRange()
            throws IOException, DocumentException {
        FaultSystemRupSet original = magRangeRupSet();
        assertSame(
                original,
                NZSHM22_InversionFaultSystemRuptSet.filterMags(
                        original,
                        NZSHM22_AbstractInversionRunner.MIN_MAG,
                        NZSHM22_AbstractInversionRunner.MAX_MAG));
        // the range excludes the largest rupture, so the rupture set is rebuilt
        assertNotSame(original, NZSHM22_InversionFaultSystemRuptSet.filterMags(original, 7.0, 7.4));
    }

    @Test
    public void testFilterMagsValidatesRange() throws IOException, DocumentException {
        FaultSystemRupSet original = magRangeRupSet();
        try {
            NZSHM22_InversionFaultSystemRuptSet.filterMags(original, 7.25, 7.7);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("minMag must be a multiple of"));
        }
    }
}

package nz.cri.gns.NZSHM22.opensha.hazard.joint;

import static nz.cri.gns.NZSHM22.opensha.hazard.joint.JointTestSolutions.*;
import static org.junit.Assert.*;

import java.util.List;
import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.RupSetTectonicRegimes;
import org.opensha.sha.util.TectonicRegionType;

/**
 * Tests for {@link JointSolutions}: merging two solutions and per-rupture tectonic region types.
 */
public class JointSolutionsTest {

    /** Merging concatenates sections, ruptures and rates, and keeps the section region types. */
    @Test
    public void testMerge() {
        FaultSystemSolution crustal = makeCrustalSolution();
        FaultSystemSolution subduction = makeSubductionSolution();

        FaultSystemSolution merged = JointSolutions.merge(crustal, subduction);
        FaultSystemRupSet rupSet = merged.getRupSet();

        assertEquals(4, rupSet.getNumSections());
        assertEquals(2, rupSet.getNumRuptures());
        assertEquals(SINGLE_RUPTURE_RATE, merged.getRateForRup(0), 1e-12);
        assertEquals(SINGLE_RUPTURE_RATE, merged.getRateForRup(1), 1e-12);

        // the second solution's sections were renumbered, and its ruptures follow them
        assertEquals(
                TectonicRegionType.ACTIVE_SHALLOW,
                rupSet.getFaultSectionData(0).getTectonicRegionType());
        assertEquals(
                TectonicRegionType.SUBDUCTION_INTERFACE,
                rupSet.getFaultSectionData(3).getTectonicRegionType());
        assertEquals(List.of(0, 1), rupSet.getSectionsIndicesForRup(0));
        assertEquals(List.of(2, 3), rupSet.getSectionsIndicesForRup(1));
    }

    /** Any number of solutions can be merged, not just a crustal and a subduction one. */
    @Test
    public void testMergeThreeSolutions() {
        FaultSystemSolution merged =
                JointSolutions.merge(
                        makeCrustalSolution(), makeSubductionSolution(), makeCrustalSolution());
        FaultSystemRupSet rupSet = merged.getRupSet();

        assertEquals(6, rupSet.getNumSections());
        assertEquals(3, rupSet.getNumRuptures());
        assertEquals(List.of(4, 5), rupSet.getSectionsIndicesForRup(2));

        RupSetTectonicRegimes regimes = rupSet.getModule(RupSetTectonicRegimes.class);
        assertEquals(TectonicRegionType.ACTIVE_SHALLOW, regimes.get(0));
        assertEquals(TectonicRegionType.SUBDUCTION_INTERFACE, regimes.get(1));
        assertEquals(TectonicRegionType.ACTIVE_SHALLOW, regimes.get(2));
    }

    /** A single solution has nothing to be merged with, so it is passed through as it is. */
    @Test
    public void testMergeSingleSolution() {
        FaultSystemSolution crustal = makeCrustalSolution();
        FaultSystemSolution merged = JointSolutions.merge(crustal);

        assertSame(crustal, merged);
        assertNotNull(merged.getRupSet().getModule(RupSetTectonicRegimes.class));
    }

    /** Merging nothing has no sensible result. */
    @Test
    public void testMergeRejectsNoSolutions() {
        try {
            JointSolutions.merge();
            fail("expected merging nothing to be rejected");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("at least one solution"));
        }
    }

    /**
     * The merged rupture set carries the tectonic regimes module. Without it the ERF reports every
     * source as ACTIVE_SHALLOW and the subduction sources would be calculated with the crustal GMM.
     */
    @Test
    public void testMergeAppliesTectonicRegimes() {
        FaultSystemSolution merged =
                JointSolutions.merge(makeCrustalSolution(), makeSubductionSolution());

        RupSetTectonicRegimes regimes = merged.getRupSet().getModule(RupSetTectonicRegimes.class);
        assertNotNull("merged rupture set should carry tectonic regimes", regimes);
        assertEquals(TectonicRegionType.ACTIVE_SHALLOW, regimes.get(0));
        assertEquals(TectonicRegionType.SUBDUCTION_INTERFACE, regimes.get(1));
    }

    /** A mixed solution gets one region type per rupture, derived from its sections. */
    @Test
    public void testTectonicRegimesForMixedSolution() {
        FaultSystemRupSet rupSet = makeMixedSolution().getRupSet();
        TectonicRegionType[] regimes = JointSolutions.tectonicRegimes(rupSet);

        assertArrayEquals(
                new TectonicRegionType[] {
                    TectonicRegionType.ACTIVE_SHALLOW, TectonicRegionType.SUBDUCTION_INTERFACE
                },
                regimes);
    }

    /** Applying the module twice is a no-op, so setting up a calculation stays idempotent. */
    @Test
    public void testApplyTectonicRegimesIsIdempotent() {
        FaultSystemRupSet rupSet = makeMixedSolution().getRupSet();
        JointSolutions.applyTectonicRegimes(rupSet);
        RupSetTectonicRegimes first = rupSet.getModule(RupSetTectonicRegimes.class);
        JointSolutions.applyTectonicRegimes(rupSet);
        assertSame(first, rupSet.getModule(RupSetTectonicRegimes.class));
    }

    /** A joint rupture has no single tectonic region type, so it cannot be given one. */
    @Test
    public void testTectonicRegimesRejectsJointRuptures() {
        try {
            JointSolutions.tectonicRegimes(makeRupSet(0d));
            fail("expected joint ruptures to be rejected");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("JOINT_RUPTURE"));
        }
    }
}

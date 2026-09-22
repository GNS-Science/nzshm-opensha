package nz.cri.gns.NZSHM22.opensha.inversion;

import static nz.cri.gns.NZSHM22.opensha.util.TestHelpers.createRupSet;
import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_LogicTreeBranch;
import org.dom4j.DocumentException;
import org.junit.Before;
import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.AveSlipModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModSectMinMags;
import org.opensha.sha.earthquake.faultSysSolution.modules.RuptureSubSetMappings;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;

/** Tests filtering a rupture set by section minimum magnitude. */
public class MagFilteredRupSetTest {

    static final double DELTA = 0.00000001;

    FaultSystemRupSet original;

    /**
     * Creates a rupture set with four ruptures of increasing size, and thus increasing magnitude.
     * Rupture r uses sections 0 to r, so section 0 is used by all ruptures and section 3 only by
     * the largest rupture.
     */
    @Before
    public void setUp() throws DocumentException, IOException {
        original =
                createRupSet(
                        NZSHM22_FaultModels.CFM_1_0A_DOM_ALL,
                        ScalingRelationships.SHAW_2009_MOD,
                        List.of(List.of(0), List.of(0, 1), List.of(0, 1, 2), List.of(0, 1, 2, 3)));
        double[] slipRates = new double[original.getNumSections()];
        double[] slipRateStdDevs = new double[original.getNumSections()];
        for (int s = 0; s < slipRates.length; s++) {
            slipRates[s] = s + 1;
            slipRateStdDevs[s] = s + 1;
        }
        original.addModule(SectSlipRates.precomputed(original, slipRates, slipRateStdDevs));
        original.addModule(AveSlipModule.precomputed(original, new double[] {10, 20, 30, 40}));
        original.addModule(new TvzDomainSections(original));
    }

    /** Magnitudes of the test rupture set, in rupture id order. */
    double[] originalMags() {
        double[] mags = new double[original.getNumRuptures()];
        for (int r = 0; r < mags.length; r++) {
            mags[r] = original.getMagForRup(r);
        }
        return mags;
    }

    /**
     * Section minimum magnitudes that only constrain one section. All other sections get a minimum
     * magnitude low enough to never exclude a rupture.
     */
    protected ModSectMinMags sectMinMags(int section, double minMag) {
        double[] minMags = new double[original.getNumSections()];
        Arrays.fill(minMags, 0);
        minMags[section] = minMag;
        return ModSectMinMags.instance(original, minMags);
    }

    /** A minimum magnitude on section 0 applies to every rupture. */
    @Test
    public void dropsRupturesBelowSectionMinMag() {
        double[] mags = originalMags();
        // magnitudes increase with rupture size
        assertTrue(mags[0] < mags[1]);
        assertTrue(mags[1] < mags[2]);
        assertTrue(mags[2] < mags[3]);

        FaultSystemRupSet rupSet = MagFilteredRupSet.filter(original, sectMinMags(0, mags[2]));

        assertEquals(2, rupSet.getNumRuptures());
        assertEquals(mags[2], rupSet.getMagForRup(0), DELTA);
        assertEquals(mags[3], rupSet.getMagForRup(1), DELTA);
    }

    /** A rupture is only tested against the minimum magnitudes of the sections it uses. */
    @Test
    public void ignoresSectionsNotUsedByARupture() {
        double[] mags = originalMags();
        // section 3 is only used by the largest rupture, which is not below its own magnitude
        FaultSystemRupSet rupSet = MagFilteredRupSet.filter(original, sectMinMags(3, mags[3]));
        assertEquals(4, rupSet.getNumRuptures());

        // a min mag above the largest rupture drops that rupture only
        rupSet = MagFilteredRupSet.filter(original, sectMinMags(3, mags[3] + 1));
        assertEquals(3, rupSet.getNumRuptures());
        assertEquals(mags[2], rupSet.getMagForRup(2), DELTA);
    }

    /** Without an explicit argument the min mags come from the rupture set's own module. */
    @Test
    public void usesAttachedModSectMinMagsModule() {
        double[] mags = originalMags();
        original.addModule(sectMinMags(0, mags[2]));

        FaultSystemRupSet rupSet = MagFilteredRupSet.filter(original);

        assertEquals(2, rupSet.getNumRuptures());
        assertEquals(mags[2], rupSet.getMagForRup(0), DELTA);
    }

    @Test
    public void keepsAllSectionsAndRuptureProperties() {
        double[] mags = originalMags();
        FaultSystemRupSet rupSet = MagFilteredRupSet.filter(original, sectMinMags(0, mags[2]));

        assertEquals(original.getNumSections(), rupSet.getNumSections());
        assertEquals(original.getSectionsIndicesForRup(2), rupSet.getSectionsIndicesForRup(0));
        assertEquals(original.getAreaForRup(2), rupSet.getAreaForRup(0), DELTA);
        assertEquals(original.getAveRakeForRup(2), rupSet.getAveRakeForRup(0), DELTA);
        assertEquals(original.getLengthForRup(2), rupSet.getLengthForRup(0), DELTA);
    }

    @Test
    public void mapsRuptureIds() {
        double[] mags = originalMags();
        FaultSystemRupSet rupSet = MagFilteredRupSet.filter(original, sectMinMags(0, mags[2]));

        RuptureSubSetMappings mappings = rupSet.requireModule(RuptureSubSetMappings.class);
        assertEquals(2, mappings.getNumRetainedRuptures());
        assertEquals(original.getNumSections(), mappings.getNumRetainedSects());

        assertEquals(2, mappings.getOrigRupID(0));
        assertEquals(3, mappings.getOrigRupID(1));

        assertFalse(mappings.isRupRetained(0));
        assertFalse(mappings.isRupRetained(1));
        assertEquals(0, mappings.getNewRupID(2));
        assertEquals(1, mappings.getNewRupID(3));
    }

    @Test
    public void filtersSplittableModules() {
        double[] mags = originalMags();
        FaultSystemRupSet rupSet = MagFilteredRupSet.filter(original, sectMinMags(0, mags[2]));

        AveSlipModule aveSlip = rupSet.requireModule(AveSlipModule.class);
        assertEquals(30, aveSlip.getAveSlip(0), DELTA);
        assertEquals(40, aveSlip.getAveSlip(1), DELTA);

        // section based modules are unchanged
        SectSlipRates slipRates = rupSet.requireModule(SectSlipRates.class);
        assertEquals(original.getNumSections(), slipRates.size());
        assertEquals(1, slipRates.getSlipRate(0), DELTA);
        assertEquals(2, slipRates.getSlipRate(1), DELTA);

        assertNotNull(rupSet.getModule(NZSHM22_LogicTreeBranch.class));
    }

    /** Rupture count agnostic modules are not splittable and must be carried over explicitly. */
    @Test
    public void keepsRuptureCountAgnosticModules() {
        double[] mags = originalMags();
        FaultSystemRupSet rupSet = MagFilteredRupSet.filter(original, sectMinMags(0, mags[2]));

        assertSame(
                original.getModule(TvzDomainSections.class),
                rupSet.getModule(TvzDomainSections.class));
    }

    @Test
    public void rejectsRupSetWithoutRetainedRuptures() {
        double[] mags = originalMags();
        try {
            MagFilteredRupSet.filter(original, sectMinMags(0, mags[3] + 1));
            fail("expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("below section minimum magnitude"));
        }
    }

    @Test
    public void rejectsRupSetWithoutModSectMinMags() {
        assertNull(original.getModule(ModSectMinMags.class));
        try {
            MagFilteredRupSet.filter(original);
            fail("expected IllegalStateException");
        } catch (IllegalStateException e) {
            // expected
        }
    }
}

package nz.cri.gns.NZSHM22.opensha.inversion;

import static nz.cri.gns.NZSHM22.opensha.util.TestHelpers.createRupSet;
import static org.junit.Assert.*;

import java.io.IOException;
import java.util.List;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_LogicTreeBranch;
import org.dom4j.DocumentException;
import org.junit.Before;
import org.junit.Test;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.AveSlipModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.RuptureSubSetMappings;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;

/** Tests filtering a rupture set by magnitude. */
public class MagFilteredRupSetTest {

    static final double DELTA = 0.00000001;

    FaultSystemRupSet original;

    /**
     * Creates a rupture set with four ruptures of increasing size, and thus increasing magnitude.
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
    }

    /** Magnitudes of the test rupture set, in rupture id order. */
    double[] originalMags() {
        double[] mags = new double[original.getNumRuptures()];
        for (int r = 0; r < mags.length; r++) {
            mags[r] = original.getMagForRup(r);
        }
        return mags;
    }

    @Test
    public void keepsOnlyRupturesInRange() {
        double[] mags = originalMags();
        // magnitudes increase with rupture size
        assertTrue(mags[0] < mags[1]);
        assertTrue(mags[1] < mags[2]);
        assertTrue(mags[2] < mags[3]);

        MagFilteredRupSet rupSet = new MagFilteredRupSet(original, mags[1], mags[2]);

        assertEquals(2, rupSet.getNumRuptures());
        assertEquals(mags[1], rupSet.getMagForRup(0), DELTA);
        assertEquals(mags[2], rupSet.getMagForRup(1), DELTA);
    }

    @Test
    public void boundsAreInclusive() {
        double[] mags = originalMags();
        MagFilteredRupSet rupSet = new MagFilteredRupSet(original, mags[0], mags[3]);
        assertEquals(4, rupSet.getNumRuptures());
    }

    @Test
    public void keepsAllSectionsAndRuptureProperties() {
        double[] mags = originalMags();
        MagFilteredRupSet rupSet = new MagFilteredRupSet(original, mags[2], mags[3]);

        assertEquals(original.getNumSections(), rupSet.getNumSections());
        assertEquals(original.getSectionsIndicesForRup(2), rupSet.getSectionsIndicesForRup(0));
        assertEquals(original.getAreaForRup(2), rupSet.getAreaForRup(0), DELTA);
        assertEquals(original.getAveRakeForRup(2), rupSet.getAveRakeForRup(0), DELTA);
        assertEquals(original.getLengthForRup(2), rupSet.getLengthForRup(0), DELTA);
    }

    @Test
    public void mapsRuptureIds() {
        double[] mags = originalMags();
        MagFilteredRupSet rupSet = new MagFilteredRupSet(original, mags[1], mags[2]);

        assertEquals(1, rupSet.getOriginalRuptureId(0));
        assertEquals(2, rupSet.getOriginalRuptureId(1));

        assertNull(rupSet.getRuptureId(0));
        assertEquals(Integer.valueOf(0), rupSet.getRuptureId(1));
        assertEquals(Integer.valueOf(1), rupSet.getRuptureId(2));
        assertNull(rupSet.getRuptureId(3));

        RuptureSubSetMappings mappings = rupSet.requireModule(RuptureSubSetMappings.class);
        assertEquals(2, mappings.getNumRetainedRuptures());
        assertEquals(original.getNumSections(), mappings.getNumRetainedSects());
    }

    @Test
    public void filtersSplittableModules() {
        double[] mags = originalMags();
        MagFilteredRupSet rupSet = new MagFilteredRupSet(original, mags[1], mags[2]);

        AveSlipModule aveSlip = rupSet.requireModule(AveSlipModule.class);
        assertEquals(20, aveSlip.getAveSlip(0), DELTA);
        assertEquals(30, aveSlip.getAveSlip(1), DELTA);

        // section based modules are unchanged
        SectSlipRates slipRates = rupSet.requireModule(SectSlipRates.class);
        assertEquals(original.getNumSections(), slipRates.size());
        assertEquals(1, slipRates.getSlipRate(0), DELTA);
        assertEquals(2, slipRates.getSlipRate(1), DELTA);

        assertNotNull(rupSet.getModule(NZSHM22_LogicTreeBranch.class));
    }

    /** Asserts that the range is rejected and returns the message of the resulting exception. */
    protected String assertRejected(
            Class<? extends RuntimeException> type, double min, double max) {
        try {
            new MagFilteredRupSet(original, min, max);
            fail("expected " + type.getSimpleName() + " for " + min + ".." + max);
            return null;
        } catch (RuntimeException e) {
            assertTrue(type.isInstance(e));
            return e.getMessage();
        }
    }

    @Test
    public void rejectsEmptyRange() {
        double[] mags = originalMags();
        assertTrue(
                assertRejected(IllegalArgumentException.class, mags[2], mags[1])
                        .contains("maxMag must be greater than minMag"));
        assertTrue(
                assertRejected(IllegalArgumentException.class, mags[1], mags[1])
                        .contains("maxMag must be greater than minMag"));
    }

    @Test
    public void rejectsRangeWithoutRuptures() {
        double[] mags = originalMags();
        assertTrue(
                assertRejected(IllegalStateException.class, mags[3] + 1, mags[3] + 2)
                        .contains("No rupture"));
    }

    @Test
    public void carriesOverRuptureCountAgnosticModules() {
        double[] mags = originalMags();
        SectionDistanceAzimuthCalculator distAzCalc =
                new SectionDistanceAzimuthCalculator(original.getFaultSectionDataList());
        original.addModule(distAzCalc);

        MagFilteredRupSet rupSet = new MagFilteredRupSet(original, mags[1], mags[2]);

        assertSame(distAzCalc, rupSet.getModule(SectionDistanceAzimuthCalculator.class));
        assertFalse(rupSet.getDroppedModules().contains("SectionDistanceAzimuthCalculator"));
    }

    @Test
    public void reportsDroppedModules() {
        double[] mags = originalMags();
        original.addModule(new UnfilterableModule());

        MagFilteredRupSet rupSet = new MagFilteredRupSet(original, mags[1], mags[2]);

        assertNull(rupSet.getModule(UnfilterableModule.class));
        assertTrue(rupSet.getDroppedModules().contains("MagFilteredRupSetTest.UnfilterableModule"));
    }

    /** A module that can neither be filtered nor carried over. */
    public static class UnfilterableModule implements OpenSHA_Module {
        @Override
        public String getName() {
            return "Unfilterable Module";
        }
    }
}

package nz.cri.gns.NZSHM22.opensha.inversion;

import static nz.cri.gns.NZSHM22.opensha.util.TestHelpers.createRupSet;
import static org.junit.Assert.*;

import java.io.IOException;
import java.util.List;
import java.util.function.IntPredicate;
import nz.cri.gns.NZSHM22.opensha.analysis.NZSHM22_FaultSystemRupSetCalc;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_LogicTreeBranch;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.Config;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionConfig;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionPredicate;
import org.dom4j.DocumentException;
import org.junit.Before;
import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.AveSlipModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.RuptureSubSetMappings;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;

/** Tests filtering a rupture set by the magnitude bounds of its partitions. */
public class MagFilteredRupSetTest {

    static final double DELTA = 0.00000001;

    FaultSystemRupSet original;
    double[] mags;

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

        mags = new double[original.getNumRuptures()];
        for (int r = 0; r < mags.length; r++) {
            mags[r] = original.getMagForRup(r);
        }
        // magnitudes increase with rupture size
        assertTrue(mags[0] < mags[1]);
        assertTrue(mags[1] < mags[2]);
        assertTrue(mags[2] < mags[3]);
    }

    /** Creates a partition config for the sections that match the predicate. */
    protected static PartitionConfig partition(
            IntPredicate sections, double minMag, double maxMag) {
        PartitionConfig partitionConfig = new PartitionConfig(PartitionPredicate.CRUSTAL);
        partitionConfig.partitionPredicate = sections;
        partitionConfig.minMag = minMag;
        partitionConfig.maxMag = maxMag;
        return partitionConfig;
    }

    /** Creates a partition config that covers all sections. */
    protected static PartitionConfig partition(double minMag, double maxMag) {
        return partition(s -> true, minMag, maxMag);
    }

    /** Filters the test rupture set with the specified partitions. */
    protected FaultSystemRupSet filter(PartitionConfig... partitions) {
        Config config = new Config();
        config.partitions = List.of(partitions);
        return MagFilteredRupSet.filter(original, config);
    }

    /** The magnitude of the part of the rupture that is in sections matching the predicate. */
    protected double partitionMag(int rupture, IntPredicate sections) {
        double partitionArea =
                original.getSectionsIndicesForRup(rupture).stream()
                        .filter(sections::test)
                        .mapToDouble(original::getAreaForSection)
                        .sum();
        return Math.log10(partitionArea / original.getAreaForRup(rupture)) / 1.5 + mags[rupture];
    }

    protected static int bin(double mag) {
        return NZSHM22_FaultSystemRupSetCalc.MAG_BINS.getClosestXIndex(mag);
    }

    @Test
    public void dropsRupturesBelowMinMag() {
        FaultSystemRupSet rupSet = filter(partition(mags[2], MagFilteredRupSet.NO_MAX_MAG));

        assertEquals(2, rupSet.getNumRuptures());
        assertEquals(mags[2], rupSet.getMagForRup(0), DELTA);
        assertEquals(mags[3], rupSet.getMagForRup(1), DELTA);
    }

    @Test
    public void dropsRupturesAboveMaxMag() {
        FaultSystemRupSet rupSet = filter(partition(0, mags[1]));

        assertEquals(2, rupSet.getNumRuptures());
        assertEquals(mags[0], rupSet.getMagForRup(0), DELTA);
        assertEquals(mags[1], rupSet.getMagForRup(1), DELTA);
    }

    /** Both bounds apply at the same time. */
    @Test
    public void appliesMinAndMaxMagTogether() {
        FaultSystemRupSet rupSet = filter(partition(mags[1], mags[2]));

        assertEquals(2, rupSet.getNumRuptures());
        assertEquals(mags[1], rupSet.getMagForRup(0), DELTA);
        assertEquals(mags[2], rupSet.getMagForRup(1), DELTA);
    }

    /** Ruptures are compared to the bounds by magnitude bin, not by magnitude. */
    @Test
    public void comparesBinsRatherThanMagnitudes() {
        IncrementalMagFreqDist bins = NZSHM22_FaultSystemRupSetCalc.MAG_BINS;
        int bin = bin(mags[1]);
        double lowerBinEdge = bins.getX(bin) - (bins.getDelta() / 2);
        double upperBinEdge = bins.getX(bin) + (bins.getDelta() / 2);
        // a max mag in the same bin as rupture 1, but below its magnitude
        double maxMag = (lowerBinEdge + mags[1]) / 2;
        // a min mag in the same bin as rupture 1, but above its magnitude
        double minMag = (upperBinEdge + mags[1]) / 2;
        assertTrue(maxMag < mags[1]);
        assertTrue(minMag > mags[1]);
        assertEquals(bin, bin(maxMag));
        assertEquals(bin, bin(minMag));

        FaultSystemRupSet rupSet = filter(partition(minMag, maxMag));

        assertEquals(1, rupSet.getNumRuptures());
        assertEquals(mags[1], rupSet.getMagForRup(0), DELTA);
    }

    /** A max mag at or above the highest bin does not drop anything. */
    @Test
    public void ignoresMaxMagAboveTheHighestBin() {
        FaultSystemRupSet rupSet = filter(partition(0, MagFilteredRupSet.NO_MAX_MAG));
        assertEquals(original.getNumRuptures(), rupSet.getNumRuptures());

        // the crustal default of 20 is in the highest bin and behaves the same way
        rupSet = filter(partition(0, 20.0));
        assertEquals(original.getNumRuptures(), rupSet.getNumRuptures());
    }

    /** A rupture that does not use any section of a partition is not tested by that partition. */
    @Test
    public void ignoresPartitionsNotUsedByARupture() {
        IntPredicate section3 = s -> s == 3;
        for (int r = 0; r < 3; r++) {
            assertTrue(
                    MagFilteredRupSet.isWithinMagBounds(
                            original, partition(section3, mags[3] + 1, mags[3] + 1), r));
        }

        // section 3 is only used by the largest rupture
        FaultSystemRupSet rupSet = filter(partition(section3, mags[3] + 1, mags[3] + 1));

        assertEquals(3, rupSet.getNumRuptures());
        assertEquals(mags[2], rupSet.getMagForRup(2), DELTA);
    }

    /**
     * The part of a joint rupture inside a partition is tested with its partition magnitude, which
     * is lower than the magnitude of the whole rupture.
     */
    @Test
    public void testsPartitionMagnitude() {
        IntPredicate section3 = s -> s == 3;
        double partitionMag = partitionMag(3, section3);
        assertTrue(bin(partitionMag) < bin(mags[3]));

        // the partition magnitude is below the whole rupture's magnitude bin
        assertFalse(
                MagFilteredRupSet.isWithinMagBounds(
                        original, partition(section3, mags[3], MagFilteredRupSet.NO_MAX_MAG), 3));
        assertTrue(
                MagFilteredRupSet.isWithinMagBounds(
                        original,
                        partition(section3, partitionMag, MagFilteredRupSet.NO_MAX_MAG),
                        3));

        // the whole rupture's magnitude would be above a max mag in the partition magnitude bin
        assertTrue(
                MagFilteredRupSet.isWithinMagBounds(
                        original, partition(section3, 0, partitionMag), 3));
        assertFalse(
                MagFilteredRupSet.isWithinMagBounds(
                        original, partition(s -> true, 0, partitionMag), 3));
    }

    /** A rupture that is entirely inside a partition is tested with its own magnitude. */
    @Test
    public void testsWholeRuptureWithItsOwnMagnitude() {
        IntPredicate firstSections = s -> s < 3;
        assertEquals(mags[2], partitionMag(2, firstSections), DELTA);

        assertTrue(
                MagFilteredRupSet.isWithinMagBounds(
                        original, partition(firstSections, mags[2], mags[2]), 2));
        assertFalse(
                MagFilteredRupSet.isWithinMagBounds(
                        original, partition(firstSections, mags[3], mags[3]), 2));
    }

    /**
     * A rupture that is entirely inside a partition is tested with its own magnitude even if its
     * area is not the sum of its section areas.
     */
    @Test
    public void testsWholeRuptureWithItsOwnMagnitudeIfAreasDiffer() {
        original.getAreaForAllRups()[2] *= 4;
        IntPredicate firstSections = s -> s < 3;
        // the partition magnitude would be in a lower bin
        assertTrue(bin(partitionMag(2, firstSections)) < bin(mags[2]));

        assertTrue(
                MagFilteredRupSet.isWithinMagBounds(
                        original, partition(firstSections, mags[2], mags[2]), 2));
    }

    /** A rupture must be within the bounds of every partition it uses. */
    @Test
    public void requiresAllPartitionsToBeWithinBounds() {
        IntPredicate firstSections = s -> s < 3;
        IntPredicate section3 = s -> s == 3;
        double partitionMag = partitionMag(3, section3);

        // rupture 3 is within the bounds of the first partition, but not of the second
        PartitionConfig first = partition(firstSections, 0, MagFilteredRupSet.NO_MAX_MAG);
        PartitionConfig second = partition(section3, partitionMag + 1, partitionMag + 1);
        assertTrue(MagFilteredRupSet.isWithinMagBounds(original, first, 3));
        assertFalse(MagFilteredRupSet.isWithinMagBounds(original, second, 3));

        FaultSystemRupSet rupSet = filter(first, second);

        assertEquals(3, rupSet.getNumRuptures());
        assertEquals(mags[2], rupSet.getMagForRup(2), DELTA);

        // the order of the partitions does not matter
        rupSet = filter(second, first);
        assertEquals(3, rupSet.getNumRuptures());
    }

    @Test
    public void rejectsRupSetWithAllRupturesBelowMinMag() {
        try {
            filter(partition(mags[3] + 1, MagFilteredRupSet.NO_MAX_MAG));
            fail("expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("outside the magnitude bounds"));
        }
    }

    @Test
    public void rejectsRupSetWithAllRupturesAboveMaxMag() {
        try {
            filter(partition(0, mags[0] - 1));
            fail("expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("outside the magnitude bounds"));
        }
    }

    @Test
    public void legacyFilterDropsRupturesOutsideBounds() {
        FaultSystemRupSet rupSet = MagFilteredRupSet.filter(original, mags[1], mags[2]);

        assertEquals(2, rupSet.getNumRuptures());
        assertEquals(mags[1], rupSet.getMagForRup(0), DELTA);
        assertEquals(mags[2], rupSet.getMagForRup(1), DELTA);

        rupSet = MagFilteredRupSet.filter(original, 0, MagFilteredRupSet.NO_MAX_MAG);
        assertEquals(original.getNumRuptures(), rupSet.getNumRuptures());
    }

    @Test
    public void legacyFilterRejectsRupSetWithoutRetainedRuptures() {
        try {
            MagFilteredRupSet.filter(original, mags[3] + 1, MagFilteredRupSet.NO_MAX_MAG);
            fail("expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("outside the magnitude bounds"));
        }
    }

    @Test
    public void keepsAllSectionsAndRuptureProperties() {
        FaultSystemRupSet rupSet = filter(partition(mags[2], MagFilteredRupSet.NO_MAX_MAG));

        assertEquals(original.getNumSections(), rupSet.getNumSections());
        assertEquals(original.getSectionsIndicesForRup(2), rupSet.getSectionsIndicesForRup(0));
        assertEquals(original.getAreaForRup(2), rupSet.getAreaForRup(0), DELTA);
        assertEquals(original.getAveRakeForRup(2), rupSet.getAveRakeForRup(0), DELTA);
        assertEquals(original.getLengthForRup(2), rupSet.getLengthForRup(0), DELTA);
    }

    @Test
    public void mapsRuptureIds() {
        FaultSystemRupSet rupSet = filter(partition(mags[2], MagFilteredRupSet.NO_MAX_MAG));

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
        FaultSystemRupSet rupSet = filter(partition(mags[2], MagFilteredRupSet.NO_MAX_MAG));

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
        FaultSystemRupSet rupSet = filter(partition(mags[2], MagFilteredRupSet.NO_MAX_MAG));

        assertSame(
                original.getModule(TvzDomainSections.class),
                rupSet.getModule(TvzDomainSections.class));
    }

    /** Only ruptures marked to be dropped are removed. */
    @Test
    public void dropsMarkedRuptures() {
        FaultSystemRupSet rupSet =
                MagFilteredRupSet.filter(original, new boolean[] {true, false, true, false});

        assertEquals(2, rupSet.getNumRuptures());
        assertEquals(mags[1], rupSet.getMagForRup(0), DELTA);
        assertEquals(mags[3], rupSet.getMagForRup(1), DELTA);
    }
}

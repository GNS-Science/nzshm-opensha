package nz.cri.gns.NZSHM22.opensha.inversion.joint;

import static nz.cri.gns.NZSHM22.opensha.util.TestHelpers.createRupSet;
import static org.junit.Assert.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_DeformationModel;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.constraint.FilteredFaultSystemRupSetTest;
import org.dom4j.DocumentException;
import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.AveSlipModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModSectMinMags;
import org.opensha.sha.earthquake.faultSysSolution.modules.RuptureSubSetMappings;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;

/** Tests that the joint rupture set modules are set up in the right order. */
public class RuptureSetSetupTest {

    static final double DELTA = 0.00000001;

    /** Creates a small crustal rupture set with two ruptures. */
    public static FaultSystemRupSet makeRupSet() throws DocumentException, IOException {
        return createRupSet(
                NZSHM22_FaultModels.CFM_1_0A_DOM_ALL,
                ScalingRelationships.SHAW_2009_MOD,
                List.of(List.of(0, 1), List.of(5, 6, 7, 8, 9)));
    }

    /**
     * Creates a minimal crustal config with a single partition.
     *
     * @param slipRateFactor the factor to apply to the slip rates, or -1 for no factor
     */
    public static Config makeConfig(double slipRateFactor) throws DocumentException, IOException {
        Config config = new Config();
        config.setRuptureSet(makeRupSet());
        config.scalingRelationshipName = "SIMPLE_CRUSTAL";
        config.recalcMags = true;
        config.sansSlipRateFactor = slipRateFactor;
        config.tvzSlipRateFactor = slipRateFactor;

        PartitionConfig partition = new PartitionConfig(PartitionPredicate.CRUSTAL);
        partition.deformationModel = NZSHM22_DeformationModel.FAULT_MODEL;
        partition.minMag = 6.8;
        config.partitions.add(partition);

        return config;
    }

    public static Config setUpConfig(double slipRateFactor) throws DocumentException, IOException {
        Config config = makeConfig(slipRateFactor);
        config.init();
        RuptureSetSetup.setup(config);
        return config;
    }

    /**
     * The partition rupture set has to be built after the slip rate factor has been applied to the
     * joint rupture set, otherwise it holds stale slip rates.
     */
    @Test
    public void partitionSlipRatesAreBuiltAfterSlipRateFactorTest()
            throws DocumentException, IOException {
        Config unscaled = setUpConfig(-1);
        Config scaled = setUpConfig(0.5);

        double[] unscaledRates =
                unscaled.partitions.get(0).partitionRuptureSet.getSlipRateForAllSections();
        double[] scaledRates =
                scaled.partitions.get(0).partitionRuptureSet.getSlipRateForAllSections();

        // the partition covers all sections, so it sees exactly the joint slip rates
        assertArrayEquals(scaled.ruptureSet.getSlipRateForAllSections(), scaledRates, DELTA);

        assertEquals(unscaledRates.length, scaledRates.length);
        assertTrue(unscaledRates.length > 0);
        for (int s = 0; s < unscaledRates.length; s++) {
            assertTrue("expected a non zero slip rate for section " + s, unscaledRates[s] > 0);
            assertEquals(0.5 * unscaledRates[s], scaledRates[s], DELTA);
        }
    }

    /**
     * The partition rupture set has to be built after the scaling relationship has been applied,
     * otherwise its average slips are derived from the truncated partition geometry rather than
     * from the joint rupture set.
     */
    @Test
    public void partitionAveSlipsAreBuiltAfterScalingRelationshipTest()
            throws DocumentException, IOException {
        Config config = setUpConfig(-1);

        AveSlipModule jointAveSlip = config.ruptureSet.requireModule(AveSlipModule.class);
        AveSlipModule partitionAveSlip =
                config.partitions.get(0).partitionRuptureSet.requireModule(AveSlipModule.class);

        // the partition covers all sections, so rupture ids are unchanged
        assertEquals(
                config.ruptureSet.getNumRuptures(),
                config.partitions.get(0).partitionRuptureSet.getNumRuptures());
        for (int r = 0; r < config.ruptureSet.getNumRuptures(); r++) {
            assertEquals(jointAveSlip.getAveSlip(r), partitionAveSlip.getAveSlip(r), DELTA);
        }
    }

    /**
     * The rupture ids of the joint fixture, see {@link FilteredFaultSystemRupSetTest#makeRupSet}.
     */
    static final int CRUSTAL_RUP = 0;

    static final int SUBDUCTION_RUP = 1;

    static final int JOINT_RUP = 2;

    /**
     * Creates a config for a rupture set with one crustal and one subduction section, and a
     * crustal, a subduction and a joint rupture. Neither partition bounds its magnitudes.
     */
    public static Config makeJointConfig() throws DocumentException, IOException {
        Config config = new Config();
        config.setRuptureSet(FilteredFaultSystemRupSetTest.makeRupSet());
        config.partitions.add(new PartitionConfig(PartitionPredicate.CRUSTAL));
        config.partitions.add(new PartitionConfig(PartitionPredicate.HIKURANGI));
        return config;
    }

    /**
     * Applies the magnitude filter to the config and returns the ids that the retained ruptures had
     * in the original rupture set.
     */
    protected static List<Integer> filteredRuptureIds(Config config) throws IOException {
        config.init();
        RuptureSetSetup.createModSectMinMags(config);
        RuptureSetSetup.filterByMagnitude(config);

        RuptureSubSetMappings mappings =
                config.ruptureSet.requireModule(RuptureSubSetMappings.class);
        List<Integer> originalIds = new ArrayList<>();
        for (int r = 0; r < config.ruptureSet.getNumRuptures(); r++) {
            originalIds.add(mappings.getOrigRupID(r));
        }
        return originalIds;
    }

    /** Partitions without magnitude bounds do not drop anything. */
    @Test
    public void keepsEveryRuptureWithinBoundsTest() throws DocumentException, IOException {
        assertEquals(
                List.of(CRUSTAL_RUP, SUBDUCTION_RUP, JOINT_RUP),
                filteredRuptureIds(makeJointConfig()));
    }

    /** A joint rupture has to satisfy the maxMag of the crustal partition it belongs to. */
    @Test
    public void jointRuptureSatisfiesTheCrustalMaxMagTest() throws DocumentException, IOException {
        Config config = makeJointConfig();
        // the crustal rupture is smaller than the joint rupture
        config.partitions.get(0).maxMag = config.ruptureSet.getMagForRup(CRUSTAL_RUP);

        assertEquals(List.of(CRUSTAL_RUP, SUBDUCTION_RUP), filteredRuptureIds(config));
    }

    /** A joint rupture has to satisfy the maxMag of the subduction partition it belongs to. */
    @Test
    public void jointRuptureSatisfiesTheSubductionMaxMagTest()
            throws DocumentException, IOException {
        Config config = makeJointConfig();
        // the subduction rupture is smaller than the joint rupture
        config.partitions.get(1).maxMag = config.ruptureSet.getMagForRup(SUBDUCTION_RUP);

        assertEquals(List.of(CRUSTAL_RUP, SUBDUCTION_RUP), filteredRuptureIds(config));
    }

    /** Each partition applies its own minMag to the sections it covers. */
    @Test
    public void eachPartitionAppliesItsOwnMinMagTest() throws DocumentException, IOException {
        Config config = makeJointConfig();
        // above the subduction rupture, but below the joint rupture
        config.partitions.get(1).minMag = config.ruptureSet.getMagForRup(CRUSTAL_RUP);

        assertEquals(List.of(CRUSTAL_RUP, JOINT_RUP), filteredRuptureIds(config));
    }

    /**
     * The rupture set is filtered before the partition rupture sets are built, so that the
     * constraint matrix only covers ruptures that are within bounds.
     */
    @Test
    public void setupFiltersBeforeBuildingPartitionRuptureSetsTest()
            throws DocumentException, IOException {
        Config config = makeJointConfig();
        config.partitions.get(0).maxMag = config.ruptureSet.getMagForRup(CRUSTAL_RUP);

        config.init();
        RuptureSetSetup.setup(config);

        // the joint rupture is above the crustal maxMag and is gone
        assertEquals(2, config.ruptureSet.getNumRuptures());
        assertNotNull(config.ruptureSet.getModule(ModSectMinMags.class));
        // each partition is left with its own rupture only
        assertEquals(1, config.partitions.get(0).partitionRuptureSet.getNumRuptures());
        assertEquals(1, config.partitions.get(1).partitionRuptureSet.getNumRuptures());
    }
}

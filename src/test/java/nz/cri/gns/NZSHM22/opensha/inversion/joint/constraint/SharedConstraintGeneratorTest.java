package nz.cri.gns.NZSHM22.opensha.inversion.joint.constraint;

import static org.junit.Assert.*;

import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.impl.SparseDoubleMatrix2D;
import java.io.IOException;
import java.util.List;
import nz.cri.gns.NZSHM22.opensha.inversion.AbstractInversionConfiguration;
import nz.cri.gns.NZSHM22.opensha.inversion.NZSHM22_SlipRateInversionConstraintBuilder;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.Config;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionConfig;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.RuptureSetSetupTest;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.constraints.FilteredInversionConstraint;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.constraints.SharedConstraintGenerator;
import org.dom4j.DocumentException;
import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;

/** Tests the constraints that are shared between partitions. */
public class SharedConstraintGeneratorTest {

    static final double DELTA = 0.00000001;

    static final double UNCERTAINTY_WEIGHT = 3;
    static final double SCALING_FACTOR = 2;

    /**
     * Creates a config with a single crustal partition that uses an uncertainty weighted slip rate
     * constraint.
     */
    public static Config makeConfig() throws DocumentException, IOException {
        Config config = RuptureSetSetupTest.setUpConfig(-1);
        PartitionConfig partition = config.partitions.get(0);
        partition.slipRateWeightingType =
                AbstractInversionConfiguration.NZSlipRateConstraintWeightingType
                        .NORMALIZED_BY_UNCERTAINTY;
        partition.slipRateUncertaintyConstraintWt = UNCERTAINTY_WEIGHT;
        partition.slipRateUncertaintyConstraintScalingFactor = SCALING_FACTOR;
        partition.unmodifiedSlipRateStdvs = false;
        return config;
    }

    @Test
    public void allSharedConstraintsAreFilteredTest() throws DocumentException, IOException {
        Config config = makeConfig();

        List<InversionConstraint> constraints =
                SharedConstraintGenerator.buildSharedConstraints(config.partitions.get(0));

        assertFalse(constraints.isEmpty());
        for (InversionConstraint constraint : constraints) {
            assertTrue(
                    constraint.getShortName() + " is not filtered",
                    constraint instanceof FilteredInversionConstraint);
        }
    }

    /**
     * The uncertainty weighted slip rate constraint replaces the slip rate standard deviations with
     * a normalised weight table. Building the constraint against the partition rupture set makes
     * sure that this custom module is not overwritten again by a call to setRuptureSet.
     */
    @Test
    public void uncertaintyConstraintKeepsNormalisedWeightsTest()
            throws DocumentException, IOException {
        Config config = makeConfig();
        PartitionConfig partition = config.partitions.get(0);

        // going through buildSharedConstraints because that is where the constraints used to be
        // rebound to the partition rupture set, which discarded the custom module
        List<InversionConstraint> constraints =
                SharedConstraintGenerator.buildSharedConstraints(partition);
        assertEquals(1, constraints.size());
        InversionConstraint constraint = constraints.get(0);

        FaultSystemRupSet partitionRupSet = partition.partitionRuptureSet;
        SectSlipRates slipRates = partitionRupSet.requireModule(SectSlipRates.class);
        double[] rates = slipRates.getSlipRates();
        double[] normalisedWeights =
                NZSHM22_SlipRateInversionConstraintBuilder.buildNormalisedWeightTable(
                        rates, slipRates.getSlipRateStdDevs(), SCALING_FACTOR);

        // the weight table has to actually differ from the model standard deviations, otherwise
        // this test cannot tell the two apart
        assertFalse(
                "expected the normalised weights to differ from the model standard deviations",
                java.util.Arrays.equals(normalisedWeights, slipRates.getSlipRateStdDevs()));

        int numSections = partitionRupSet.getNumSections();
        DoubleMatrix2D a =
                new SparseDoubleMatrix2D(numSections, config.ruptureSet.getNumRuptures());
        double[] d = new double[numSections];
        constraint.encode(a, d, 0);

        for (int s = 0; s < numSections; s++) {
            assertEquals(
                    "d mismatch for section " + s,
                    rates[s] * UNCERTAINTY_WEIGHT / normalisedWeights[s],
                    d[s],
                    DELTA);
        }
    }
}

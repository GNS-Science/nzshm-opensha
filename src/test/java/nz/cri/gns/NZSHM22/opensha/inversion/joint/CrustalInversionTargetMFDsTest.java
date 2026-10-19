package nz.cri.gns.NZSHM22.opensha.inversion.joint;

import static org.junit.Assert.*;

import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.impl.SparseDoubleMatrix2D;
import java.io.IOException;
import java.util.List;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_SpatialSeisPDF;
import nz.cri.gns.NZSHM22.opensha.inversion.MFDManipulation;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.constraints.SharedConstraintGenerator;
import org.dom4j.DocumentException;
import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

/** Tests the target MFDs of a crustal partition in a joint inversion. */
public class CrustalInversionTargetMFDsTest {

    /**
     * Creates a crustal joint config whose ruptures are all larger than the default crustal MFD
     * range, which ends at the 8.95 bin.
     */
    public static Config makeLargeMagConfig() throws DocumentException, IOException {
        Config config = RuptureSetSetupTest.makeConfig(-1);
        // a large C value pushes the magnitudes of the small test ruptures above 9
        config.scalingCValDipSlip = 8;
        config.scalingCValStrikeSlip = 8;
        PartitionConfig partition = config.partitions.get(0);
        partition.totalRateM5 = 3.4;
        partition.bValue = 0.959;
        partition.mfdEqualityConstraintWt = 1;
        partition.mfdUncertaintyWeight = 1;
        partition.mfdUncertaintyPower = 0.25;
        partition.mfdUncertaintyScalar = 0.1;
        partition.spatialSeisPDF = NZSHM22_SpatialSeisPDF.FLOOR_ADDOPTIEEPASCOMB_CRU;
        partition.polygonBufferSize = 12;
        config.init();
        RuptureSetSetup.setup(config);
        return config;
    }

    @Test
    public void mfdCoversLargeRupturesTest() throws DocumentException, IOException {
        Config config = makeLargeMagConfig();
        PartitionConfig partition = config.partitions.get(0);
        FaultSystemRupSet rupSet = partition.partitionRuptureSet;
        assertTrue(
                "test ruptures must be larger than the default MFD range",
                rupSet.getMinMag() > 9.0);

        CrustalInversionTargetMFDs targetMFDs = new CrustalInversionTargetMFDs(rupSet, partition);

        IncrementalMagFreqDist constraint = targetMFDs.getMFD_Constraints().get(0);
        assertTrue(MFDManipulation.containsMag(constraint, rupSet.getMaxMag()));
        IncrementalMagFreqDist uncertainty = targetMFDs.getMfdUncertaintyConstraints().get(0);
        assertEquals(constraint.getMaxX(), uncertainty.getMaxX(), 1e-9);
    }

    /** Every rupture must be encoded in the MFD constraints, none can be silently left out. */
    @Test
    public void mfdConstraintsEncodeLargeRupturesTest() throws DocumentException, IOException {
        Config config = makeLargeMagConfig();
        PartitionConfig partition = config.partitions.get(0);
        CrustalInversionTargetMFDs targetMFDs =
                new CrustalInversionTargetMFDs(partition.partitionRuptureSet, partition);
        partition.mfdConstraints = targetMFDs.getMFD_Constraints();
        partition.mfdUncertaintyWeightedConstraints = targetMFDs.getMfdUncertaintyConstraints();

        List<InversionConstraint> constraints =
                SharedConstraintGenerator.buildMfdConstraints(partition);
        assertEquals(2, constraints.size());

        int numRuptures = partition.partitionRuptureSet.getNumRuptures();
        for (InversionConstraint constraint : constraints) {
            int rows = constraint.getNumRows();
            DoubleMatrix2D a = new SparseDoubleMatrix2D(rows, numRuptures);
            constraint.encode(a, new double[rows], 0);
            for (int r = 0; r < numRuptures; r++) {
                assertTrue(
                        constraint.getShortName() + " does not encode rupture " + r,
                        a.viewColumn(r).cardinality() > 0);
            }
        }
    }
}

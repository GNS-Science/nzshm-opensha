package nz.cri.gns.NZSHM22.opensha.inversion.joint.constraint;

import static nz.cri.gns.NZSHM22.opensha.inversion.joint.CrustalInversionTargetMFDs.NZ_MIN_MAG;
import static nz.cri.gns.NZSHM22.opensha.inversion.joint.CrustalInversionTargetMFDs.NZ_NUM_BINS;
import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;
import static scratch.UCERF3.inversion.U3InversionTargetMFDs.DELTA_MAG;

import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.impl.SparseDoubleMatrix2D;
import java.io.IOException;
import java.util.List;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionPredicate;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.constraints.FilteredFaultSystemRupSet;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.constraints.FilteredInversionConstraint;
import org.dom4j.DocumentException;
import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.ConstraintWeightingType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.MFDInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.SlipRateInversionConstraint;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;

public class FilteredInversionConstraintTest {

    static final double DELTA = 0.00000001;

    @Test
    public void MFDEncodeTest() throws DocumentException, IOException {
        FaultSystemRupSet original = FilteredFaultSystemRupSetTest.makeRupSet();
        FilteredFaultSystemRupSet rupSet =
                FilteredFaultSystemRupSet.forIntPredicate(
                        original,
                        PartitionPredicate.CRUSTAL.getPredicate(original),
                        ScalingRelationships.SHAW_2009_MOD);
        IncrementalMagFreqDist mfd =
                new GutenbergRichterMagFreqDist(NZ_MIN_MAG, NZ_NUM_BINS, DELTA_MAG);
        for (int bin = 0; bin < mfd.getClosestXIndex(mfd.getMaxX()); bin++) {
            mfd.set(bin, bin);
        }
        List<IncrementalMagFreqDist> mfds = List.of(mfd);
        MFDInversionConstraint mfdConstraint = new MFDInversionConstraint(rupSet, 1, false, mfds);
        FilteredInversionConstraint constraint =
                new FilteredInversionConstraint(mfdConstraint, rupSet);

        // we only have a single magnitude, so we expect a single bucket
        assertEquals(1, constraint.getNumRows());

        DoubleMatrix2D matrix = new SparseDoubleMatrix2D(2, 3);
        double[] d = new double[2];
        constraint.encode(matrix, d, 1);

        assertArrayEquals(new double[] {0, 1.0}, d, DELTA);
        assertTrue(matrix.get(1, 0) > 0);
        // rupture completely outside of partition is not encoded
        assertEquals(0, matrix.get(1, 1), DELTA);
        assertEquals(matrix.get(1, 0), matrix.get(1, 2), DELTA);
    }

    @Test
    public void SlipRateEncodeTest() throws DocumentException, IOException {
        FaultSystemRupSet original = FilteredFaultSystemRupSetTest.makeRupSet();
        FilteredFaultSystemRupSet rupSet =
                FilteredFaultSystemRupSet.forIntPredicate(
                        original,
                        PartitionPredicate.CRUSTAL.getPredicate(original),
                        ScalingRelationships.SHAW_2009_MOD);

        SlipRateInversionConstraint slipConstraint =
                new SlipRateInversionConstraint(1, ConstraintWeightingType.NORMALIZED, rupSet);
        FilteredInversionConstraint constraint =
                new FilteredInversionConstraint(slipConstraint, rupSet);

        assertEquals(1, constraint.getNumRows());

        DoubleMatrix2D matrix = new SparseDoubleMatrix2D(1, 3);
        double[] d = new double[1];
        constraint.encode(matrix, d, 0);

        assertTrue(d[0] > 0);
        assertTrue(matrix.get(0, 0) > 0);
        // rupture does not have this fault section
        assertEquals(0, matrix.get(0, 1), DELTA);
        assertEquals(matrix.get(0, 0), matrix.get(0, 2), DELTA);

        // trying the same for hikurangi

        rupSet =
                FilteredFaultSystemRupSet.forIntPredicate(
                        original,
                        PartitionPredicate.HIKURANGI.getPredicate(original),
                        ScalingRelationships.SHAW_2009_MOD);

        slipConstraint =
                new SlipRateInversionConstraint(1, ConstraintWeightingType.NORMALIZED, rupSet);
        constraint = new FilteredInversionConstraint(slipConstraint, rupSet);

        assertEquals(1, constraint.getNumRows());

        matrix = new SparseDoubleMatrix2D(1, 3);
        d = new double[1];
        constraint.encode(matrix, d, 0);

        assertTrue(d[0] > 0);
        // rupture does not have this fault section
        assertEquals(0, matrix.get(0, 0), DELTA);
        assertTrue(matrix.get(0, 1) > 0);
        assertEquals(matrix.get(0, 1), matrix.get(0, 2), DELTA);
    }
}

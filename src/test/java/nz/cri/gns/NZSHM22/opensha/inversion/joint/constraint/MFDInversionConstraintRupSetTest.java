package nz.cri.gns.NZSHM22.opensha.inversion.joint.constraint;

import static nz.cri.gns.NZSHM22.opensha.inversion.joint.CrustalInversionTargetMFDs.NZ_MIN_MAG;
import static nz.cri.gns.NZSHM22.opensha.inversion.joint.CrustalInversionTargetMFDs.NZ_NUM_BINS;
import static nz.cri.gns.NZSHM22.opensha.util.TestHelpers.createRupSet;
import static org.junit.Assert.*;
import static scratch.UCERF3.inversion.U3InversionTargetMFDs.DELTA_MAG;

import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.impl.SparseDoubleMatrix2D;
import java.io.IOException;
import java.util.List;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionPredicate;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.constraints.MFDInversionConstraintRupSet;
import nz.cri.gns.NZSHM22.opensha.ruptures.FaultSectionProperties;
import org.dom4j.DocumentException;
import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.MFDInversionConstraint;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;

public class MFDInversionConstraintRupSetTest {

    static final double DELTA = 0.00000001;

    static final int CRU_SECTION = 0;
    static final int SUB_SECTION = 1;

    /**
     * Create rupture set with one crustal and one subduction fault section, and three ruptures:
     * crustal, subduction, and joint.
     */
    public FaultSystemRupSet makeRupSet() throws DocumentException, IOException {
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

        FaultSectionProperties props = new FaultSectionProperties();
        props.set(CRU_SECTION, PartitionPredicate.CRUSTAL.name(), true);
        props.set(SUB_SECTION, PartitionPredicate.HIKURANGI.name(), true);
        rupSet.addModule(props);

        return rupSet;
    }

    @Test
    public void magTest() throws DocumentException, IOException {
        FaultSystemRupSet original = makeRupSet();
        FaultSystemRupSet rupSet =
                MFDInversionConstraintRupSet.create(
                        original,
                        PartitionPredicate.CRUSTAL.getPredicate(original),
                        ScalingRelationships.SHAW_2009_MOD);

        // magnitudes are only calculated for crustal parts of ruptures
        assertEquals(original.getMagForRup(0), rupSet.getMagForRup(0), DELTA);
        assertEquals(0, rupSet.getMagForRup(1), DELTA);
        assertEquals(original.getMagForRup(0), rupSet.getMagForRup(2), DELTA);

        // minMag ignores zero magnitudes
        assertEquals(original.getMagForRup(0), rupSet.getMinMag(), DELTA);
    }

    // This test ensures that the rupture set is able to serve its purpose.
    @Test
    public void MFDEncodeTest() throws DocumentException, IOException {
        FaultSystemRupSet original = makeRupSet();
        FaultSystemRupSet rupSet =
                MFDInversionConstraintRupSet.create(
                        original,
                        PartitionPredicate.CRUSTAL.getPredicate(original),
                        ScalingRelationships.SHAW_2009_MOD);
        IncrementalMagFreqDist mfd =
                new GutenbergRichterMagFreqDist(NZ_MIN_MAG, NZ_NUM_BINS, DELTA_MAG);
        for (int bin = 0; bin < mfd.getClosestXIndex(mfd.getMaxX()); bin++) {
            mfd.set(bin, bin);
        }
        List<IncrementalMagFreqDist> mfds = List.of(mfd);
        MFDInversionConstraint constraint = new MFDInversionConstraint(rupSet, 1, false, mfds);

        // we only have a single magnitude, so we expect a single bucket
        assertEquals(1, constraint.getNumRows());

        DoubleMatrix2D matrix = new SparseDoubleMatrix2D(1, 3);
        double[] d = new double[1];
        constraint.encode(matrix, d, 0);

        assertArrayEquals(new double[] {1.0}, d, DELTA);
        assertTrue(matrix.get(0, 0) > 0);
        // rupture completely outside of partition is not encoded
        assertEquals(0, matrix.get(0, 1), DELTA);
        assertEquals(matrix.get(0, 0), matrix.get(0, 2), DELTA);
    }

    @Test
    public void SlipRateEncodeTest() {}
}

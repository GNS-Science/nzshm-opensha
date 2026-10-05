package nz.cri.gns.NZSHM22.opensha.inversion.joint;

import static nz.cri.gns.NZSHM22.opensha.inversion.NZSHM22_CrustalInversionTargetMFDs.NZ_MIN_MAG;
import static nz.cri.gns.NZSHM22.opensha.inversion.NZSHM22_CrustalInversionTargetMFDs.NZ_NUM_BINS;
import static org.junit.Assert.*;
import static scratch.UCERF3.inversion.U3InversionTargetMFDs.DELTA_MAG;

import java.util.List;
import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionTargetMFDs;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

/** Tests for {@link PartitionMfds}. */
public class PartitionMfdsTest {

    static final double DELTA = 0.00000001;

    /** An MFD with the same rate in every bin. */
    protected static IncrementalMagFreqDist mfd(double rate) {
        IncrementalMagFreqDist mfd = new IncrementalMagFreqDist(NZ_MIN_MAG, NZ_NUM_BINS, DELTA_MAG);
        for (int i = 0; i < mfd.size(); i++) {
            mfd.set(i, rate);
        }
        return mfd;
    }

    /** Target MFDs with distinct rates per MFD type, scaled by factor. */
    protected static InversionTargetMFDs targets(double factor, IncrementalMagFreqDist constraint) {
        return new InversionTargetMFDs.Precomputed(
                null,
                mfd(1 * factor),
                mfd(2 * factor),
                mfd(3 * factor),
                mfd(4 * factor),
                List.of(constraint),
                null,
                null);
    }

    @Test
    public void synthesizeSumsPartitionMfds() {
        PartitionMfds partitionMfds = new PartitionMfds();
        partitionMfds.mfds.put(PartitionPredicate.CRUSTAL, targets(1, mfd(5)));
        partitionMfds.mfds.put(PartitionPredicate.HIKURANGI, targets(10, mfd(50)));

        InversionTargetMFDs synthesized = partitionMfds.synthesize(null);

        assertEquals(11, synthesized.getTotalRegionalMFD().getY(0), DELTA);
        assertEquals(22, synthesized.getTotalOnFaultSupraSeisMFD().getY(0), DELTA);
        assertEquals(33, synthesized.getTotalOnFaultSubSeisMFD().getY(0), DELTA);
        assertEquals(44, synthesized.getTrulyOffFaultMFD().getY(0), DELTA);
        assertEquals(11, synthesized.getTotalRegionalMFD().getY(NZ_NUM_BINS - 1), DELTA);
    }

    /** Partition constraints only apply to their partition and are not part of the result. */
    @Test
    public void synthesizeHasNoConstraintsAndDoesNotModifyPartitions() {
        IncrementalMagFreqDist constraint = mfd(5);
        PartitionMfds partitionMfds = new PartitionMfds();
        partitionMfds.mfds.put(PartitionPredicate.CRUSTAL, targets(1, constraint));

        InversionTargetMFDs synthesized = partitionMfds.synthesize(null);

        assertTrue(synthesized.getMFD_Constraints().isEmpty());
        assertNull(constraint.getRegion());
        assertSame(
                constraint,
                partitionMfds.get(PartitionPredicate.CRUSTAL).getMFD_Constraints().get(0));
    }
}

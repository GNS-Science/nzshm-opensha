package nz.cri.gns.NZSHM22.opensha.inversion.joint;

import static nz.cri.gns.NZSHM22.opensha.inversion.NZSHM22_CrustalInversionTargetMFDs.NZ_MAX_MAG;
import static nz.cri.gns.NZSHM22.opensha.inversion.NZSHM22_CrustalInversionTargetMFDs.NZ_MIN_MAG;
import static org.junit.Assert.*;
import static scratch.UCERF3.inversion.U3InversionTargetMFDs.DELTA_MAG;

import java.util.List;
import nz.cri.gns.NZSHM22.opensha.inversion.MFDManipulation;
import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionTargetMFDs;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

/** Tests for {@link PartitionMfds}. */
public class PartitionMfdsTest {

    static final double DELTA = 0.00000001;

    /** An MFD with the same rate in every bin. */
    protected static IncrementalMagFreqDist mfd(double rate) {
        IncrementalMagFreqDist mfd = MFDManipulation.createIncrementalMFD(NZ_MIN_MAG, NZ_MAX_MAG);
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
    public void synthesizeSumsPartitionSupraSeisMfds() {
        PartitionMfds partitionMfds = new PartitionMfds();
        partitionMfds.mfds.put(PartitionPredicate.CRUSTAL, targets(1, mfd(5)));
        partitionMfds.mfds.put(PartitionPredicate.HIKURANGI, targets(10, mfd(50)));

        InversionTargetMFDs synthesized = partitionMfds.synthesize(null);

        assertEquals(22, synthesized.getTotalOnFaultSupraSeisMFD().getY(0), DELTA);
        assertEquals(22, synthesized.getTotalOnFaultSupraSeisMFD().getY(NZ_MAX_MAG), DELTA);
    }

    /** Subduction MFDs reach higher magnitudes than the default range and must not be cut off. */
    @Test
    public void synthesizeKeepsFullMagnitudeRange() {
        IncrementalMagFreqDist subduction = new IncrementalMagFreqDist(5.05, 48, DELTA_MAG);
        subduction.set(47, 7); // M9.75
        PartitionMfds partitionMfds = new PartitionMfds();
        partitionMfds.mfds.put(PartitionPredicate.CRUSTAL, targets(1, mfd(5)));
        partitionMfds.mfds.put(
                PartitionPredicate.HIKURANGI,
                new InversionTargetMFDs.Precomputed(
                        null, null, subduction, null, null, List.of(), null, null));

        IncrementalMagFreqDist supra = partitionMfds.synthesize(null).getTotalOnFaultSupraSeisMFD();

        assertEquals(9.75, supra.getMaxX(), DELTA);
        assertEquals(7, supra.getY(9.75), DELTA);
        assertEquals(2, supra.getY(5.05), DELTA);
    }

    /** Only the crustal partition has these, so they don't apply to the joint rupture set. */
    @Test
    public void synthesizeOmitsCrustalOnlyMfds() {
        PartitionMfds partitionMfds = new PartitionMfds();
        partitionMfds.mfds.put(PartitionPredicate.CRUSTAL, targets(1, mfd(5)));
        partitionMfds.mfds.put(PartitionPredicate.HIKURANGI, targets(10, mfd(50)));

        InversionTargetMFDs synthesized = partitionMfds.synthesize(null);

        assertNull(synthesized.getTotalRegionalMFD());
        assertNull(synthesized.getTotalOnFaultSubSeisMFD());
        assertNull(synthesized.getTrulyOffFaultMFD());
        assertNull(synthesized.getTotalGriddedSeisMFD());
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

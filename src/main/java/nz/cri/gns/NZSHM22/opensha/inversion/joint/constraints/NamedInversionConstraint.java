package nz.cri.gns.NZSHM22.opensha.inversion.joint.constraints;

import cern.colt.matrix.tdouble.DoubleMatrix2D;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionPredicate;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;

/**
 * Wraps an {@link InversionConstraint} to append the partition name to the constraint name, e.g.
 * "Normalized Slip Rate (HIKURANGI)".
 */
public class NamedInversionConstraint extends InversionConstraint {

    private final InversionConstraint inner;

    public NamedInversionConstraint(InversionConstraint inner, PartitionPredicate partition) {
        super(
                inner.getName() + " (" + partition.name() + ")",
                inner.getShortName() + "_" + partition.name(),
                inner.getWeight(),
                inner.isInequality(),
                inner.getWeightingType());
        this.inner = inner;
    }

    @Override
    public int getNumRows() {
        return inner.getNumRows();
    }

    @Override
    public void setRuptureSet(FaultSystemRupSet rupSet) {
        inner.setRuptureSet(rupSet);
    }

    @Override
    public long encode(DoubleMatrix2D A, double[] d, int startRow) {
        return inner.encode(A, d, startRow);
    }
}

package nz.cri.gns.NZSHM22.opensha.inversion.joint.constraints;

import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.impl.SparseDoubleMatrix2D;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;

/// Wraps an InversionConstraint that may rely on a rupture set with filtered out ruptures.
/// The encode() function provides an accordingly filtered A matrix to the original constraint, and
// then
/// translates the encoded values to the proper matrix.
public class FilteredInversionConstraint extends InversionConstraint {

    final InversionConstraint inner;
    final FaultSystemRupSet rupSet;

    protected FilteredInversionConstraint(InversionConstraint inner, FaultSystemRupSet rupSet) {
        super(
                inner.getName(),
                inner.getShortName(),
                inner.getWeight(),
                inner.isInequality(),
                inner.getWeightingType());
        this.inner = inner;
        this.rupSet = rupSet;
    }

    @Override
    public int getNumRows() {
        return inner.getNumRows();
    }

    @Override
    public long encode(DoubleMatrix2D A, double[] d, int startRow) {
        if (rupSet instanceof FilteredFaultSystemRupSet) {
            FilteredFaultSystemRupSet filteredRupSet = (FilteredFaultSystemRupSet) rupSet;
            SparseDoubleMatrix2D innerA =
                    new SparseDoubleMatrix2D(getNumRows() + startRow, rupSet.getNumRuptures());
            long nonZero = inner.encode(innerA, d, startRow);
            for (int row = 0; row < innerA.rows(); ++row) {
                for (int col = 0; col < innerA.columns(); ++col) {
                    double value = innerA.getQuick(row, col);
                    if (value != (double) 0.0F) {
                        A.set(row + startRow, filteredRupSet.getOldRuptureId(col), value);
                    }
                }
            }
            return nonZero;
        }
        return inner.encode(A, d, startRow);
    }
}

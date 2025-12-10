package nz.cri.gns.NZSHM22.opensha.inversion.joint.constraints;

import cern.colt.matrix.tdouble.DoubleMatrix2D;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;

public class JointConstraintWrapper extends InversionConstraint {

    final ConstraintRegionConfig config;
    final InversionConstraint constraint;

    int startRow;

    public JointConstraintWrapper(ConstraintRegionConfig config, InversionConstraint constraint) {
        super(
                constraint.getName(),
                constraint.getShortName(),
                constraint.getWeight(),
                constraint.isInequality());
        this.config = config;
        this.constraint = constraint;
    }

    @Override
    public int getNumRows() {
        return config.sectionIds.size();
    }

    @Override
    public long encode(DoubleMatrix2D A, double[] d, int startRow) {
        this.startRow = startRow;
        double[] buffer = new double[startRow + constraint.getNumRows()];

        // let original constraint encode itself,
        // and filter out the rows for fault sections that the config does not cover.
        // writing to A matrix is modified through overridden setA(),
        // writing to D vector is modified through buffer
        long result = constraint.encode(A, buffer, startRow);

        for (int sectionId = 0; sectionId < constraint.getNumRows(); sectionId++) {
            if (config.covers(sectionId)) {
                d[startRow + config.mapToRow(sectionId)] = buffer[startRow + sectionId];
            }
        }

        return result;
    }

    @Override
    protected void setA(DoubleMatrix2D A, int row, int col, double val) {
        int sectionId = col - startRow;
        if (config.covers(sectionId)) {
            int adjustedRow = config.mapToRow(sectionId) + startRow;
            super.setA(A, adjustedRow, col, val);
        }
    }
}

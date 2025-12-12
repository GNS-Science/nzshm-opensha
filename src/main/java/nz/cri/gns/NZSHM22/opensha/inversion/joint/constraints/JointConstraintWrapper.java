package nz.cri.gns.NZSHM22.opensha.inversion.joint.constraints;

import cern.colt.matrix.tdouble.DoubleMatrix1D;
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

        FilteredMatrix filteredA = new FilteredMatrix(A);

        // let original constraint encode itself,
        // and filter out the rows for fault sections that the config does not cover.
        // writing to A matrix is modified through overridden setA(),
        // writing to D vector is modified through buffer
        constraint.encode(filteredA, buffer, startRow);

        for (int sectionId = 0; sectionId < constraint.getNumRows(); sectionId++) {
            if (config.covers(sectionId)) {
                d[startRow + config.mapToRow(sectionId)] = buffer[startRow + sectionId];
            }
        }

        return filteredA.getNonZeroElements();
    }

    class FilteredMatrix extends DoubleMatrix2D {
        final DoubleMatrix2D original;
        int nonZero = 0;

        public FilteredMatrix(DoubleMatrix2D original) {
            this.original = original;
        }

        public void set(int row, int col, double value) {
            int sectionId = row - startRow;
            if (config.covers(sectionId)) {
                int adjustedRow = config.mapToRow(sectionId) + startRow;
                original.set(adjustedRow, col, value);
                if (value != 0) {
                    nonZero++;
                }
            }
        }

        public void setQuick(int row, int col, double value) {
            int sectionId = row - startRow;
            if (config.covers(sectionId)) {
                int adjustedRow = config.mapToRow(sectionId) + startRow;
                original.setQuick(adjustedRow, col, value);
                if (value != 0) {
                    nonZero++;
                }
            }
        }

        public int getNonZeroElements() {
            return nonZero;
        }

        @Override
        public Object elements() {
            return null;
        }

        @Override
        public double getQuick(int i, int i1) {
            return 0;
        }

        @Override
        public DoubleMatrix2D like(int i, int i1) {
            return null;
        }

        @Override
        public DoubleMatrix1D like1D(int i) {
            return null;
        }

        @Override
        public DoubleMatrix1D vectorize() {
            return null;
        }

        @Override
        protected DoubleMatrix1D like1D(int i, int i1, int i2) {
            return null;
        }

        @Override
        protected DoubleMatrix2D viewSelectionLike(int[] ints, int[] ints1) {
            return null;
        }
    }
}

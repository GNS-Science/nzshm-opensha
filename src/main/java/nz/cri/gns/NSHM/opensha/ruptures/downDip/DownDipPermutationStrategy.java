package nz.cri.gns.NSHM.opensha.ruptures.downDip;

import java.util.ArrayList;
import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterPermutationStrategy;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;

public class DownDipPermutationStrategy implements ClusterPermutationStrategy {

    public interface Constraint {
        boolean apply(DownDipSubSectBuilder builder, int startRow, int startCol, int rowCount, int colCount);
    }

    private Constraint constraint;

    private final DownDipRegistry registry;
    private final ClusterPermutationStrategy crustalStrategy;
    private static final boolean D = false;

    public DownDipPermutationStrategy(DownDipRegistry registry, ClusterPermutationStrategy crustalStrategy) {
        this.registry = registry;
        this.crustalStrategy = crustalStrategy;
    }

    public DownDipPermutationStrategy addConstraint(Constraint constraint) {
        if (null == this.constraint) {
            this.constraint = constraint;
        } else {
            Constraint oldConstraint = this.constraint;
            this.constraint = (builder, startRow, startCol, rowCount, colCount) ->
                    oldConstraint.apply(builder, startRow, startCol, rowCount, colCount) &&
                            constraint.apply(builder, startRow, startCol, rowCount, colCount);
        }
        return this;
    }

    public DownDipPermutationStrategy addAspectRatioConstraint(double minRatio, double maxRatio) {
        return addAspectRatioConstraint(minRatio, maxRatio, Integer.MAX_VALUE);
    }

    /**
     * Adds an aspect ratio constraint that ensures that ruptures are within the specified minRatio and maxRatio (incl).
     * If a rupture starts at row 0 and has at least depthThreshold rows, then maxRatio can be exceeded.
     *
     * @param minRatio       the minimum required ratio (incl)
     * @param maxRatio       the max required ratio (incl)
     * @param depthThreshold from this depth on, maxRatio can be exceeded
     * @return this strategy
     */
    public DownDipPermutationStrategy addAspectRatioConstraint(double minRatio, double maxRatio, int depthThreshold) {
        return addConstraint((builder, startRow, startCol, rowCount, colCount) -> {
            double ratio = (double) colCount / (double) rowCount;
            if ((startRow == 0) && (rowCount >= depthThreshold)) {
                return minRatio <= ratio;
            } else {
                return minRatio <= ratio && ratio <= maxRatio;
            }
        });
    }

    private static boolean isDivisibleBy(double dividend, double potentialDivisor) {
        return ((dividend % potentialDivisor) == 0);
    }

    public DownDipPermutationStrategy addPositionCoarsenessConstraint(double epsilon) {
        if (epsilon > 0) {
            return addConstraint((builder, startRow, startCol, rowCount, colCount) -> {
                int coarseness = Math.max(1, (int) Math.round(epsilon * rowCount * colCount));
                return isDivisibleBy(startCol, coarseness) && isDivisibleBy(startRow, coarseness);
            });
        } else {
            return this;
        }
    }

    public DownDipPermutationStrategy addSizeCoarsenessConstraint(double epsilon) {
        if (epsilon > 0) {
            return addConstraint((builder, startRow, startCol, rowCount, colCount) -> {
                int coarseness = Math.max(1, (int) Math.round(epsilon * rowCount * colCount));
                return isDivisibleBy(rowCount, coarseness) && isDivisibleBy(colCount, coarseness);
            });
        } else {
            return this;
        }
    }

    public DownDipPermutationStrategy addMinFillConstraint(double minFill) {
        Preconditions.checkArgument(0 < minFill && minFill <= 1);
        return addConstraint((builder, startRow, startCol, rowCount, colCount) -> {
            int count = 0;
            for (int r = startRow; r < (startRow + rowCount); r++) {
                for (int c = startCol; c < (startCol + colCount); c++) {
                    if (null != builder.getSubSect(r, c)) {
                        count++;
                    }
                }
            }
            return count / ((double) rowCount * colCount) >= minFill;
        });
    }

    private boolean applyConstraint(DownDipSubSectBuilder builder, int startRow, int startCol, int endRow, int endCol) {
        if (constraint == null) {
            return true;
        } else {
            // normalise coordinates
            int colCount = 1 + Math.abs(endCol - startCol);
            int rowCount = 1 + Math.abs(endRow - startRow);
            startRow = Math.min(startRow, endRow);
            startCol = Math.min(startCol, endCol);
            return constraint.apply(builder, startRow, startCol, rowCount, colCount);
        }
    }

    @Override
    public List<FaultSubsectionCluster> getPermutations(
            FaultSubsectionCluster fullCluster, FaultSection firstSection) {
        if (constraint == null) {
            constraint = (builder, a, b, c, d) -> true;
        }
        List<FaultSection> clusterSects = fullCluster.subSects;
        int myInd = fullCluster.subSects.indexOf(firstSection);
        Preconditions.checkState(myInd >= 0, "first section not found in cluster");

        List<FaultSubsectionCluster> permutations = new ArrayList<>();

        DownDipSubSectBuilder downDipBuilder = registry.getBuilder(fullCluster.parentSectionID);
        if (downDipBuilder == null) {
            permutations = crustalStrategy.getPermutations(fullCluster, firstSection);
        } else {
            // this is a down-dip fault section, only build rectangular permutations
            int startCol = downDipBuilder.getColumn(firstSection);
            int startRow = downDipBuilder.getRow(firstSection);

            if (D) System.out.println("Building permutations from " + startRow + ", " + startCol);

            int rows = downDipBuilder.getNumRows();
            int cols = downDipBuilder.getNumCols();

            // build down-dip first, starting with single row
            if (D) System.out.println("\tbuilding down-dip");
            for (int endRow = startRow; endRow < rows; endRow++) {
                // build to the right first (including single column)
                for (int endCol = startCol; endCol < cols; endCol++)
                    if (applyConstraint(downDipBuilder, startRow, startCol, endRow, endCol)) {
                        permutations.add(buildRectangularPermutation(
                                downDipBuilder, fullCluster, startRow, startCol, endRow, endCol));
                    }
                // build to the left
                for (int endCol = startCol; --endCol >= 0; )
                    if (applyConstraint(downDipBuilder, startRow, startCol, endRow, endCol)) {
                        permutations.add(buildRectangularPermutation(
                                downDipBuilder, fullCluster, startRow, startCol, endRow, endCol));
                    }
            }

            // build up-dip
            if (D) System.out.println("\tbuilding up-dip");
            for (int endRow = startRow; --endRow >= 0; ) {
                // build to the right first (including single column)
                for (int endCol = startCol; endCol < cols; endCol++)
                    if (applyConstraint(downDipBuilder, startRow, startCol, endRow, endCol)) {
                        permutations.add(buildRectangularPermutation(
                                downDipBuilder, fullCluster, startRow, startCol, endRow, endCol));
                    }
                // build to the left
                for (int endCol = startCol; --endCol >= 0; )
                    if (applyConstraint(downDipBuilder, startRow, startCol, endRow, endCol)) {
                        permutations.add(buildRectangularPermutation(
                                downDipBuilder, fullCluster, startRow, startCol, endRow, endCol));
                    }
            }
        }
        return permutations;
    }

    private static FaultSubsectionCluster buildCopyJumps(FaultSubsectionCluster fullCluster,
                                                         List<FaultSection> subsetSects) {
        FaultSubsectionCluster permutation = new FaultSubsectionCluster(new ArrayList<>(subsetSects));
        for (FaultSection sect : subsetSects)
            for (Jump jump : fullCluster.getConnections(sect))
                permutation.addConnection(new Jump(sect, permutation,
                        jump.toSection, jump.toCluster, jump.distance));
        return permutation;
    }

    private FaultSubsectionCluster buildRectangularPermutation(DownDipSubSectBuilder downDipBuilder, FaultSubsectionCluster fullCluster,
                                                               int startRow, int startCol, int endRow, int endCol) {
        List<FaultSection> subsetSects = new ArrayList<>();

        // this is a list of all exit points from this rupture (sections from which we can jump to another
        // fault without it being considered a splay jump). we'll define that now as the far edges from the
        // starting point, though other strategies likely exist
        List<FaultSection> exitPoints = new ArrayList<>();

//		if (D) System.out.println("\t\trow span: "+startRow+" => "+endRow+": "+printIndexes(startRow, endRow));
//		if (D) System.out.println("\t\tcol span: "+startCol+" => "+endCol+": "+printIndexes(startCol, endCol));
//		
        // build with rows in the outer loop, as this should make azimuth calculations work
        for (int row : indexes(startRow, endRow)) {
            for (int col : indexes(startCol, endCol)) {
//				if (D) System.out.println("row="+row+", col="+col);
                FaultSection sect = downDipBuilder.getSubSect(row, col);
                if (sect != null) {
                    subsetSects.add(sect);
                    exitPoints.add(sect);
                }
            }
        }
        Preconditions.checkState(subsetSects.get(0).equals(downDipBuilder.getSubSect(startRow, startCol)));
        FaultSubsectionCluster permutation = new FaultSubsectionCluster(subsetSects, exitPoints);
        // add possible jumps out of this permutation
        for (FaultSection sect : subsetSects)
            for (Jump jump : fullCluster.getConnections(sect))
                permutation.addConnection(new Jump(sect, permutation,
                        jump.toSection, jump.toCluster, jump.distance));
        return permutation;
    }

    private static int[] indexes(int start, int end) {
        if (start <= end) {
            int[] ret = new int[1 + end - start];
            for (int i = 0; i < ret.length; i++)
                ret[i] = start + i;
            return ret;
        }
        int[] ret = new int[1 + start - end];
        for (int i = 0; i < ret.length; i++)
            ret[i] = start - i;
        return ret;
    }

    private static String printIndexes(int start, int end) {
        int[] indexes = indexes(start, end);
        return Joiner.on(",").join(Ints.asList(indexes));
    }

	@Override
	public String getName() {
		return new String("DownDip Permutation Strategy");
	}

}

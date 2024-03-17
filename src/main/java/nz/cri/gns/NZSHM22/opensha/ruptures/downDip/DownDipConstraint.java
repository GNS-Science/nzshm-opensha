package nz.cri.gns.NZSHM22.opensha.ruptures.downDip;

import com.google.common.base.Preconditions;
import org.opensha.sha.faultSurface.FaultSection;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

/**
 * Verifies that there are no gaps in a rupture.
 */
public interface DownDipConstraint {

    boolean apply(DownDipSubSectBuilder builder, int startRow, int startCol, int rowCount, int colCount);

    DownDipConstraint ALWAYS = (DownDipSubSectBuilder builder, int startRow, int startCol, int rowCount, int colCount) -> true;

    default DownDipConstraint and(DownDipConstraint other) {
        if (other == null) {
            return this;
        }
        return (builder, startRow, startCol, rowCount, colCount) ->
                this.apply(builder, startRow, startCol, rowCount, colCount) &&
                        other.apply(builder, startRow, startCol, rowCount, colCount);
    }

    /**
     * Verifies that there are no gaps in a rupture.
     * The algorithm recursively visits a section and all of its neighbours. Once it has visited all sections it can reach
     * from the first subsection, we assert that all sections of the rupture have been visited.
     * Neighbours are only visited orthogonally, not diagonally.
     *
     * @return whether all sections can be reached from any other section in the rupture.
     */
    static DownDipConstraint connectednessConstraint() {
        return (DownDipSubSectBuilder builder, int startRow, int startCol, int rowCount, int colCount) ->
        {
            Set<FaultSection> seen = new HashSet<>();
            Stack<FaultSection> toVisit = new Stack<>();

            FaultSection initialSection = findFirstSection(builder, startRow, startCol, rowCount, colCount);
            Preconditions.checkState(initialSection != null);
            toVisit.push(initialSection);
            seen.add(initialSection);

            while (!toVisit.empty()) {
                // pop a section off the stack and look at its neighbours
                FaultSection current = toVisit.pop();
                List<FaultSection> neighbours = builder.getNeighbors(current);
                for (FaultSection section : neighbours) {
                    // if we haven't seen the section before, put it on the stack
                    if (!seen.contains(section)) {
                        int col = builder.getColumn(section);
                        int row = builder.getRow(section);
                        // only put it on the stack if the section is actually inside the area that we are considering for this rupture.
                        if (col >= startCol && col < startCol + colCount &&
                                row >= startRow && row < startRow + rowCount) {
                            toVisit.push(section);
                            seen.add(section);
                        }
                    }
                }
            }

            int count = countSections(builder, startRow, startCol, rowCount, colCount);
            return count == seen.size();
        };
    }

    /**
     * Returns an aspect ratio constraint that ensures that ruptures are within the specified minRatio and maxRatio (incl).
     * If a rupture starts at row 0 and has at least depthThreshold rows, then maxRatio can be exceeded.
     *
     * @param minRatio       the minimum required ratio (incl)
     * @param maxRatio       the max required ratio (incl)
     * @param depthThreshold from this depth on, maxRatio can be exceeded
     * @return the constraint
     */
    static DownDipConstraint aspectRatioConstraint(double minRatio, double maxRatio, int depthThreshold) {
        return (DownDipSubSectBuilder builder, int startRow, int startCol, int rowCount, int colCount) -> {
            double ratio = (double) colCount / (double) rowCount;
            if ((startRow == 0) && (rowCount >= depthThreshold)) {
                return minRatio <= ratio;
            } else {
                return minRatio <= ratio && ratio <= maxRatio;
            }
        };
    }

    static DownDipConstraint aspectRatioConstraint(double minRatio, double maxRatio) {
        return aspectRatioConstraint(minRatio, maxRatio, Integer.MAX_VALUE);
    }

    /**
     * Returns a constraint that ensures that the rectangular area that contains the rupture has enough
     * non-null subSections.
     * @param minFill the ratio of subsections to the rectangle size (measured in the number of subSections)
     * @return the constraint
     */
    static DownDipConstraint minFillConstraint(double minFill) {
        return (builder, startRow, startCol, rowCount, colCount) -> {
            int count = countSections(builder, startRow, startCol, rowCount, colCount);
            return count / ((double) rowCount * colCount) >= minFill;
        };
    }

    /**
     * Returns the first non-null sub section in the search area.
     */
    private static FaultSection findFirstSection(DownDipSubSectBuilder builder, int startRow, int startCol, int rowCount, int colCount) {
        for (int r = startRow; r < startRow + rowCount; r++) {
            for (int c = startCol; c < startCol + colCount; c++) {
                FaultSection section = builder.getSubSect(r, c);
                if (section != null) {
                    return section;
                }
            }
        }
        return null;
    }

    /**
     * Returns the number of non-null sections in the search area.
     */
    private static int countSections(DownDipSubSectBuilder builder, int startRow, int startCol, int rowCount, int colCount) {
        int count = 0;
        for (int r = startRow; r < (startRow + rowCount); r++) {
            for (int c = startCol; c < (startCol + colCount); c++) {
                if (null != builder.getSubSect(r, c)) {
                    count++;
                }
            }
        }
        return count;
    }

    // These two constraints were implemented but not used in NZSHM22 and they look unfinished.
    // I believe sizeCoarsenessConstraint is meant to reduce the number of allowed sizes up until a certain size,
    // and positionCoarsenessConstraint is meant to reduce the number of starting locations up until a certain size.
    // We should test these thoroughly if we deicde to use them.

//    static DownDipConstraint sizeCoarsenessConstraint(double epsilon) {
//        return (builder, startRow, startCol, rowCount, colCount) -> {
//            int coarseness = Math.max(1, (int) Math.round(epsilon * rowCount * colCount));
//            return (rowCount % coarseness == 0) && (colCount % coarseness == 0);
//        };
//    }
//
//    static DownDipConstraint positionCoarsenessConstraint(double epsilon) {
//        if (epsilon > 0) {
//            return (builder, startRow, startCol, rowCount, colCount) -> {
//                int coarseness = Math.max(1, (int) Math.round(epsilon * rowCount * colCount));
//                return isDivisibleBy(startCol, coarseness) && isDivisibleBy(startRow, coarseness);
//            };
//        }
//        return ALWAYS;
//    }
//    private static boolean isDivisibleBy(double dividend, double potentialDivisor) {
//        return ((dividend % potentialDivisor) == 0);
//    }
}

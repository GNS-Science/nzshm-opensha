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
public class ConnectednessConstraint {
    /**
     * Verifies that there are no gaps in a rupture.
     * The algorithm recursively visits a section and all of its neighbours. Once it has visited all sections it can reach
     * from the first subsection, we assert that all sections of the rupture have been visited.
     * Neighbours are only visited orthogonally, not diagonally.
     *
     * @param builder  the DownDipSubSectBuilder
     * @param startRow the starting row of the rupture area
     * @param startCol the starting column of the rupture area
     * @param rowCount the height of the rupture area
     * @param colCount the width of the rupture area
     * @return whether all sections can be reached from any other section in the rupture.
     */
    protected static boolean connectednessConstraint(DownDipSubSectBuilder builder, int startRow, int startCol, int rowCount, int colCount) {
        Set<FaultSection> seen = new HashSet<>();
        Stack<FaultSection> toVisit = new Stack<>();

        // find an initial section
        for (int i = startCol; i < startCol + colCount; i++) {
            FaultSection s = builder.getSubSect(startRow, i);
            if (s != null) {
                toVisit.push(s);
                seen.add(s);
                break;
            }
        }
        Preconditions.checkState(!toVisit.empty());

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

        int count = DownDipPermutationStrategy.countSections(builder, startRow, startCol, rowCount, colCount);
        return count == seen.size();
    }
}

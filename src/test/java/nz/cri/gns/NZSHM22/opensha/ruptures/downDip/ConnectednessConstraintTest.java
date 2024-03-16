package nz.cri.gns.NZSHM22.opensha.ruptures.downDip;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConnectednessConstraintTest {

    /**
     * Simulates a rupture based on the tile grid passed and runs the ConnectednessConstraint over it
     * @param sectionGrid a 1/0 grid of sections where 1 denotes a section and 0 denotes no section
     * @return whether the rupture is connected
     */
    protected static boolean isRuptureConnected(int[][] sectionGrid) {
        int rowCount = sectionGrid.length;
        int colCount = Arrays.stream(sectionGrid).map(col -> col.length).max(Integer::compareTo).get();
        DownDipSubSectBuilder builder = DownDipTestPermutationStrategyTest.mockDownDipBuilder(0, sectionGrid);
        return ConnectednessConstraint.connectednessConstraint(builder, 0, 0, rowCount, colCount);
    }

    @Test
    public void testMinimalRupture() {
        // all connected
        int[][] ruptureGrid = {{1, 1}};
        assertTrue(isRuptureConnected(ruptureGrid));
    }

    @Test
    public void testWithFirstSectionEmpty() {
        // all connected
        int[][] ruptureGrid = {{0, 1, 1}};
        assertTrue(isRuptureConnected(ruptureGrid));
    }

    @Test
    public void testWithGap() {
        int[][] ruptureGrid = {{1, 0, 1}};
        assertFalse(isRuptureConnected(ruptureGrid));
    }

    @Test
    public void testWithHole() {
        int[][] ruptureGrid = {
                {1, 1, 1},
                {1, 0, 1},
                {1, 1, 1},
        };
        assertTrue(isRuptureConnected(ruptureGrid));
    }

    @Test
    public void testWithSpiral() {
        int[][] ruptureGrid = {
                {1, 1, 1, 1},
                {0, 0, 0, 1},
                {1, 1, 0, 1},
                {1, 0, 0, 1},
                {1, 1, 1, 1},
        };
        assertTrue(isRuptureConnected(ruptureGrid));
    }

    @Test
    public void testWithCutout() {
        int[][] ruptureGrid = {
                {1, 1, 1},
                {1, 1, 0},
                {1, 1, 0},
                {1, 1, 1},
        };
        assertTrue(isRuptureConnected(ruptureGrid));
    }
}

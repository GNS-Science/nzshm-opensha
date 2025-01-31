package nz.cri.gns.NZSHM22.opensha.ruptures.downDip;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import nz.cri.gns.NZSHM22.opensha.ruptures.DownDipFaultSection;
import org.junit.Test;

public class DownDipConstraintTest {

    /**
     * Simulates a rupture based on the tile grid passed and runs the ConnectednessConstraint over
     * it
     *
     * @param ruptureGrid a 1/0 grid of sections where 1 denotes a section and 0 denotes no section
     * @return whether the rupture is connected
     */
    protected static boolean apply(DownDipConstraint constraint, int[][] ruptureGrid) {
        int rowCount = ruptureGrid.length;
        int colCount =
                Arrays.stream(ruptureGrid).map(col -> col.length).max(Integer::compareTo).get();
        DownDipSubSectBuilder builder = mockDownDipBuilder(0, ruptureGrid);
        return constraint.apply(builder, 0, 0, rowCount, colCount);
    }

    public static DownDipSubSectBuilder mockDownDipBuilder(int parentId, int[][] sectionPositions) {
        List<DownDipFaultSection> sections = new ArrayList<>();
        int sectionId = 0;
        for (int r = 0; r < sectionPositions.length; r++) {
            for (int c = 0; c < sectionPositions[r].length; c++) {
                if (sectionPositions[r][c] == 1) {
                    DownDipFaultSection section = mock(DownDipFaultSection.class);
                    when(section.getSectionId()).thenReturn(sectionId);
                    when(section.getColIndex()).thenReturn(c);
                    when(section.getRowIndex()).thenReturn(r);
                    sections.add(section);
                }
                sectionId++;
            }
        }
        return DownDipSubSectBuilder.fromList(sections, "puyrangi", parentId);
    }

    @Test
    public void testConnectednessMinimalRupture() {
        // all connected
        int[][] ruptureGrid = {{1, 1}};
        assertTrue(apply(DownDipConstraint.connectednessConstraint(), ruptureGrid));
    }

    @Test
    public void testConnectednessWithFirstSectionEmpty() {
        // all connected
        int[][] ruptureGrid = {{0, 1, 1}};
        assertTrue(apply(DownDipConstraint.connectednessConstraint(), ruptureGrid));
    }

    @Test
    public void testConnectednessWithGap() {
        int[][] ruptureGrid = {{1, 0, 1}};
        assertFalse(apply(DownDipConstraint.connectednessConstraint(), ruptureGrid));
    }

    @Test
    public void testConnectednessWithHole() {
        int[][] ruptureGrid = {
            {1, 1, 1},
            {1, 0, 1},
            {1, 1, 1},
        };
        assertTrue(apply(DownDipConstraint.connectednessConstraint(), ruptureGrid));
    }

    @Test
    public void testConnectednessWithSpiral() {
        int[][] ruptureGrid = {
            {1, 1, 1, 1},
            {0, 0, 0, 1},
            {1, 1, 0, 1},
            {1, 0, 0, 1},
            {1, 1, 1, 1},
        };
        assertTrue(apply(DownDipConstraint.connectednessConstraint(), ruptureGrid));
    }

    @Test
    public void testConnectednessWithCutout() {
        int[][] ruptureGrid = {
            {1, 1, 1},
            {1, 1, 0},
            {1, 1, 0},
            {1, 1, 1},
        };
        assertTrue(apply(DownDipConstraint.connectednessConstraint(), ruptureGrid));
    }

    @Test
    public void testConnectednessIsland() {
        int[][] ruptureGrid = {
            {0, 0, 0},
            {0, 1, 0},
            {0, 0, 0},
        };
        assertTrue(apply(DownDipConstraint.connectednessConstraint(), ruptureGrid));
    }

    @Test
    public void testConnectednessConfineToRupture() {
        int[][] builderGrid = {
            {1, 0, 1},
            {1, 1, 1},
        };

        DownDipSubSectBuilder builder = mockDownDipBuilder(0, builderGrid);
        // tell the constraint to only look at the first row: not connected
        boolean actual = DownDipConstraint.connectednessConstraint().apply(builder, 0, 0, 1, 3);
        assertFalse(actual);
        // tell the constraint to look at both rows: connected
        actual = DownDipConstraint.connectednessConstraint().apply(builder, 0, 0, 2, 3);
        assertTrue(actual);
    }

    @Test
    public void testAspectRatioExactly() {
        int[][] ruptureGrid = {{1}};
        assertTrue(apply(DownDipConstraint.aspectRatioConstraint(1, 1), ruptureGrid));
        assertFalse(apply(DownDipConstraint.aspectRatioConstraint(2, 2), ruptureGrid));

        int[][] ruptureGrid2 = {{1, 1}, {1, 1}};
        assertTrue(apply(DownDipConstraint.aspectRatioConstraint(1, 1), ruptureGrid2));
        assertFalse(apply(DownDipConstraint.aspectRatioConstraint(2, 2), ruptureGrid2));

        int[][] ruptureGrid3 = {{1, 1}};
        assertTrue(apply(DownDipConstraint.aspectRatioConstraint(2, 2), ruptureGrid3));
        assertFalse(apply(DownDipConstraint.aspectRatioConstraint(1, 1), ruptureGrid3));
    }

    @Test
    public void testAspectRatioMinMax() {
        int[][] ruptureGrid = {{1, 1}};

        // min
        assertFalse(apply(DownDipConstraint.aspectRatioConstraint(2.1, 5), ruptureGrid));
        assertTrue(apply(DownDipConstraint.aspectRatioConstraint(2.0, 5), ruptureGrid));
        assertTrue(apply(DownDipConstraint.aspectRatioConstraint(1.9, 5), ruptureGrid));

        // max
        assertFalse(apply(DownDipConstraint.aspectRatioConstraint(1, 1.9), ruptureGrid));
        assertTrue(apply(DownDipConstraint.aspectRatioConstraint(1, 2), ruptureGrid));
        assertTrue(apply(DownDipConstraint.aspectRatioConstraint(1, 2.1), ruptureGrid));
    }

    @Test
    public void testAspectRatioThreshold() {
        int[][] builderGrid = {
            {1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
            {1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
            {1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
            {1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
            {1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
        };

        DownDipSubSectBuilder builder = mockDownDipBuilder(0, builderGrid);
        // an aspect ratio of 1:1 until we've reached a depth of 4
        DownDipConstraint one = DownDipConstraint.aspectRatioConstraint(1, 1, 4);

        // max works normally until threshold is met
        assertTrue(one.apply(builder, 0, 0, 3, 3));
        assertFalse(one.apply(builder, 0, 0, 3, 4));
        // once rowCount hits the threshold, colCount can grow without penalty
        assertTrue(one.apply(builder, 0, 0, 4, 5));
        assertTrue(one.apply(builder, 0, 0, 5, 10));

        // The threshold only has an effect if startRow is 0
        assertTrue(one.apply(builder, 1, 0, 3, 3));
        assertFalse(one.apply(builder, 1, 0, 4, 5));
    }

    @Test
    public void testAspectRatioOrientation() {
        int[][] horizontalRupture = {{1, 1}};
        int[][] verticalRupture = {{1}, {1}};

        assertTrue(apply(DownDipConstraint.aspectRatioConstraint(2, 2), horizontalRupture));
        assertFalse(apply(DownDipConstraint.aspectRatioConstraint(2, 2), verticalRupture));

        assertTrue(apply(DownDipConstraint.aspectRatioConstraint(0.5, 0.5), verticalRupture));
        assertFalse(apply(DownDipConstraint.aspectRatioConstraint(0.5, 0.5), horizontalRupture));
    }

    @Test
    public void testMinFillExtremes() {
        int[][] completeRupture = {{1}};
        int[][] emptyRupture = {{0}};
        assertTrue(apply(DownDipConstraint.minFillConstraint(1), completeRupture));
        assertFalse(apply(DownDipConstraint.minFillConstraint(1), emptyRupture));

        assertTrue(apply(DownDipConstraint.minFillConstraint(0), completeRupture));
        assertTrue(apply(DownDipConstraint.minFillConstraint(0), emptyRupture));
    }

    @Test
    public void testMinFillRatios() {
        int[][] full = {{1, 1, 1, 1, 1, 1}};
        int[][] half = {
            {1, 1, 1, 1, 1, 1},
            {0, 0, 0, 0, 0, 0}
        };
        int[][] third = {
            {1, 1, 1, 1, 1, 1},
            {0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0}
        };

        assertTrue(apply(DownDipConstraint.minFillConstraint(1), full));
        assertFalse(apply(DownDipConstraint.minFillConstraint(1), half));
        assertFalse(apply(DownDipConstraint.minFillConstraint(1), third));

        assertTrue(apply(DownDipConstraint.minFillConstraint(0.5), full));
        assertTrue(apply(DownDipConstraint.minFillConstraint(0.5), half));
        assertFalse(apply(DownDipConstraint.minFillConstraint(0.5), third));

        assertTrue(apply(DownDipConstraint.minFillConstraint(0.4), full));
        assertTrue(apply(DownDipConstraint.minFillConstraint(0.4), half));
        assertFalse(apply(DownDipConstraint.minFillConstraint(0.4), third));

        assertTrue(apply(DownDipConstraint.minFillConstraint(0.3), full));
        assertTrue(apply(DownDipConstraint.minFillConstraint(0.3), half));
        assertTrue(apply(DownDipConstraint.minFillConstraint(0.3), third));
    }

    // for future reference

    //    @Test
    //    public void testSizeCoarsenessConstraint() {
    //
    //        ExhaustiveUnilateralRuptureGrowingStrategy ucerf3Strategy = new
    // ExhaustiveUnilateralRuptureGrowingStrategy();
    //
    //        // single section, too large coarseness
    //        DownDipSubSectBuilder builder = mockDownDipBuilder(0, 1, 1);
    //        FaultSubsectionCluster cluster = new
    // FaultSubsectionCluster(builder.getSubSectsList());
    //        DownDipPermutationStrategy strategy = new DownDipPermutationStrategy(ucerf3Strategy);
    //        strategy.addSizeCoarsenessConstraint(4);
    //        List<FaultSubsectionCluster> actual = strategy.getVariations(cluster,
    // builder.getSubSect(0, 0));
    //        assertEquals(0, actual.size());
    //
    //        // single section, small coarseness
    //        strategy = new DownDipPermutationStrategy(ucerf3Strategy);
    //        strategy.addSizeCoarsenessConstraint(1);
    //        actual = strategy.getVariations(cluster, builder.getSubSect(0, 0));
    //        List<List<Integer>> expected = new ArrayList<>();
    //        expected.add(Lists.newArrayList(0));
    //        assertEquals(expected, simplifyPermutations(actual));
    //
    //        // 3x3 downDip
    //        builder = mockDownDipBuilder(0, 3, 3);
    //        cluster = new FaultSubsectionCluster(builder.getSubSectsList());
    //
    //        // size coarseness so permissive that we take everything
    //        strategy = new DownDipPermutationStrategy(ucerf3Strategy);
    //        strategy.addSizeCoarsenessConstraint(0.1);
    //        actual = strategy.getVariations(cluster, builder.getSubSect(0, 1));
    //        expected = new ArrayList<>();
    //        expected.add(Lists.newArrayList(1));
    //        expected.add(Lists.newArrayList(1, 2));
    //        expected.add(Lists.newArrayList(1, 0));
    //        expected.add(Lists.newArrayList(1, 4));
    //        expected.add(Lists.newArrayList(1, 2, 4, 5));
    //        expected.add(Lists.newArrayList(1, 0, 4, 3));
    //        expected.add(Lists.newArrayList(1, 4, 7));
    //        expected.add(Lists.newArrayList(1, 2, 4, 5, 7, 8));
    //        expected.add(Lists.newArrayList(1, 0, 4, 3, 7, 6));
    //        assertEquals(expected, simplifyPermutations(actual));
    //
    //        // size coarseness weeds out ruptures that don't fit
    //        strategy = new DownDipPermutationStrategy(ucerf3Strategy);
    //        strategy.addSizeCoarsenessConstraint(0.5);
    //        actual = strategy.getVariations(cluster, builder.getSubSect(0, 1));
    //        expected = new ArrayList<>();
    //        expected.add(Lists.newArrayList(1));
    //        expected.add(Lists.newArrayList(1, 2));
    //        expected.add(Lists.newArrayList(1, 0));
    //        expected.add(Lists.newArrayList(1, 4));
    //        expected.add(Lists.newArrayList(1, 2, 4, 5));
    //        expected.add(Lists.newArrayList(1, 0, 4, 3));
    //        // expected.add(Lists.newArrayList(1, 4, 7));
    //        //  expected.add(Lists.newArrayList(1, 2, 4, 5, 7, 8));
    //        // expected.add(Lists.newArrayList(1, 0, 4, 3, 7, 6));
    //        assertEquals(expected, simplifyPermutations(actual));
    //    }
}

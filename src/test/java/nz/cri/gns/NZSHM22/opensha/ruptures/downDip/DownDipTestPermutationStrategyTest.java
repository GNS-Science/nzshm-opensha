package nz.cri.gns.NZSHM22.opensha.ruptures.downDip;

import static org.junit.Assert.*;

import com.google.common.collect.Lists;

import nz.cri.gns.NZSHM22.opensha.ruptures.downDip.DownDipPermutationStrategy;
import nz.cri.gns.NZSHM22.opensha.ruptures.downDip.DownDipRegistry;
import nz.cri.gns.NZSHM22.opensha.ruptures.downDip.DownDipSubSectBuilder;
import nz.cri.gns.NZSHM22.opensha.util.FaultSectionList;

import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ExhaustiveUnilateralRuptureGrowingStrategy;
import org.opensha.sha.faultSurface.FaultSection;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DownDipTestPermutationStrategyTest {

    @Test
    public void testUnconstrainedPermutations() {

        ExhaustiveUnilateralRuptureGrowingStrategy ucerf3Strategy = new ExhaustiveUnilateralRuptureGrowingStrategy();

        // single section
        DownDipSubSectBuilder builder = mockDownDipBuilder(0, 1, 1);
        FaultSubsectionCluster cluster = new FaultSubsectionCluster(builder.getSubSectsList());
        DownDipRegistry registry = mockDownDipRegistry(builder);
        DownDipPermutationStrategy strategy = new DownDipPermutationStrategy(registry, ucerf3Strategy);

        List<FaultSubsectionCluster> actual = strategy.getVariations(cluster, builder.getSubSect(0, 0));
        List<List<Integer>> expected = new ArrayList<>();
        expected.add(Lists.newArrayList(0));
        assertEquals(expected, simplifyPermutations(actual));

        // two columns
        builder = mockDownDipBuilder(0, 1, 2);
        registry = mockDownDipRegistry(builder);
        cluster = new FaultSubsectionCluster(builder.getSubSectsList());
        strategy = new DownDipPermutationStrategy(registry, ucerf3Strategy);

        actual = strategy.getVariations(cluster, builder.getSubSect(0, 0));
        expected = new ArrayList<>();
        expected.add(Lists.newArrayList(0));
        expected.add(Lists.newArrayList(0, 1));
        assertEquals(expected, simplifyPermutations(actual));

        // three columns: go up and down columns
        builder = mockDownDipBuilder(0, 1, 3);
        registry = mockDownDipRegistry(builder);
        cluster = new FaultSubsectionCluster(builder.getSubSectsList());
        strategy = new DownDipPermutationStrategy(registry, ucerf3Strategy);

        actual = strategy.getVariations(cluster, builder.getSubSect(0, 1));
        expected = new ArrayList<>();
        expected.add(Lists.newArrayList(1));
        expected.add(Lists.newArrayList(1, 2));
        expected.add(Lists.newArrayList(1, 0));
        assertEquals(expected, simplifyPermutations(actual));

        // two rows
        builder = mockDownDipBuilder(0, 2, 1);
        registry = mockDownDipRegistry(builder);
        cluster = new FaultSubsectionCluster(builder.getSubSectsList());
        strategy = new DownDipPermutationStrategy(registry, ucerf3Strategy);

        actual = strategy.getVariations(cluster, builder.getSubSect(0, 0));
        expected = new ArrayList<>();
        expected.add(Lists.newArrayList(0));
        expected.add(Lists.newArrayList(0, 1));
        assertEquals(expected, simplifyPermutations(actual));

        // three rows: go up and down rows
        builder = mockDownDipBuilder(0, 3, 1);
        registry = mockDownDipRegistry(builder);
        cluster = new FaultSubsectionCluster(builder.getSubSectsList());
        strategy = new DownDipPermutationStrategy(registry, ucerf3Strategy);

        actual = strategy.getVariations(cluster, builder.getSubSect(1, 0));
        expected = new ArrayList<>();
        expected.add(Lists.newArrayList(1));
        expected.add(Lists.newArrayList(1, 2));
        expected.add(Lists.newArrayList(1, 0));
        assertEquals(expected, simplifyPermutations(actual));

        // three rows and columns: go up and down rows and columns
        builder = mockDownDipBuilder(0, 3, 3);
        registry = mockDownDipRegistry(builder);
        cluster = new FaultSubsectionCluster(builder.getSubSectsList());
        strategy = new DownDipPermutationStrategy(registry, ucerf3Strategy);

        actual = strategy.getVariations(cluster, builder.getSubSect(1, 1));
        expected = new ArrayList<>();
        expected.add(Lists.newArrayList(4));
        expected.add(Lists.newArrayList(4, 5));
        expected.add(Lists.newArrayList(4, 3));
        expected.add(Lists.newArrayList(4, 7));
        expected.add(Lists.newArrayList(4, 5, 7, 8));
        expected.add(Lists.newArrayList(4, 3, 7, 6));
        expected.add(Lists.newArrayList(4, 1));
        expected.add(Lists.newArrayList(4, 5, 1, 2));
        expected.add(Lists.newArrayList(4, 3, 1, 0));
        assertEquals(expected, simplifyPermutations(actual));

        // same cluster, different start subSect for more variety of shapes
        actual = strategy.getVariations(cluster, builder.getSubSect(0, 1));
        expected = new ArrayList<>();
        expected.add(Lists.newArrayList(1));
        expected.add(Lists.newArrayList(1, 2));
        expected.add(Lists.newArrayList(1, 0));
        expected.add(Lists.newArrayList(1, 4));
        expected.add(Lists.newArrayList(1, 2, 4, 5));
        expected.add(Lists.newArrayList(1, 0, 4, 3));
        expected.add(Lists.newArrayList(1, 4, 7));
        expected.add(Lists.newArrayList(1, 2, 4, 5, 7, 8));
        expected.add(Lists.newArrayList(1, 0, 4, 3, 7, 6));
        assertEquals(expected, simplifyPermutations(actual));
    }

    @Test
    public void testAspectRatioConstraint() {
        ExhaustiveUnilateralRuptureGrowingStrategy ucerf3Strategy = new ExhaustiveUnilateralRuptureGrowingStrategy();

        // single section
        DownDipSubSectBuilder builder = mockDownDipBuilder(0, 1, 1);
        FaultSubsectionCluster cluster = new FaultSubsectionCluster(builder.getSubSectsList());
        DownDipRegistry registry = mockDownDipRegistry(builder);
        DownDipPermutationStrategy strategy = new DownDipPermutationStrategy(registry, ucerf3Strategy);
        strategy.addAspectRatioConstraint(2, 3);

        List<FaultSubsectionCluster> actual = strategy.getVariations(cluster, builder.getSubSect(0, 0));
        assertEquals(0, actual.size());

        // still single section, but with better aspect ratio
        strategy = new DownDipPermutationStrategy(registry, ucerf3Strategy);

        strategy.addAspectRatioConstraint(1, 1);

        actual = strategy.getVariations(cluster, builder.getSubSect(0, 0));
        List<List<Integer>> expected = new ArrayList<>();
        expected.add(Lists.newArrayList(0));
        assertEquals(expected, simplifyPermutations(actual));

        // 3x3 downDip
        builder = mockDownDipBuilder(0, 3, 3);
        registry = mockDownDipRegistry(builder);
        cluster = new FaultSubsectionCluster(builder.getSubSectsList());

        // aspect ratio so permissive that we take everything
        strategy = new DownDipPermutationStrategy(registry, ucerf3Strategy);
        strategy.addAspectRatioConstraint(0, 100);
        actual = strategy.getVariations(cluster, builder.getSubSect(0, 1));
        expected = new ArrayList<>();
        expected.add(Lists.newArrayList(1));
        expected.add(Lists.newArrayList(1, 2));
        expected.add(Lists.newArrayList(1, 0));
        expected.add(Lists.newArrayList(1, 4));
        expected.add(Lists.newArrayList(1, 2, 4, 5));
        expected.add(Lists.newArrayList(1, 0, 4, 3));
        expected.add(Lists.newArrayList(1, 4, 7));
        expected.add(Lists.newArrayList(1, 2, 4, 5, 7, 8));
        expected.add(Lists.newArrayList(1, 0, 4, 3, 7, 6));
        assertEquals(expected, simplifyPermutations(actual));

        // aspect ratio 1 to 2
        strategy = new DownDipPermutationStrategy(registry, ucerf3Strategy);
        strategy.addAspectRatioConstraint(1, 2);
        actual = strategy.getVariations(cluster, builder.getSubSect(0, 1));
        expected = new ArrayList<>();
        expected.add(Lists.newArrayList(1));
        expected.add(Lists.newArrayList(1, 2));
        expected.add(Lists.newArrayList(1, 0));
        // expected.add(Lists.newArrayList(1, 4)); // wrong orientation for our aspect ratio
        expected.add(Lists.newArrayList(1, 2, 4, 5));
        expected.add(Lists.newArrayList(1, 0, 4, 3));
        // expected.add(Lists.newArrayList(1, 4, 7));
        // expected.add(Lists.newArrayList(1, 2, 4, 5, 7, 8));
        // expected.add(Lists.newArrayList(1, 0, 4, 3, 7, 6));
        assertEquals(expected, simplifyPermutations(actual));

        // aspect ratio 2 to 2
        strategy = new DownDipPermutationStrategy(registry, ucerf3Strategy);
        strategy.addAspectRatioConstraint(2, 2);
        actual = strategy.getVariations(cluster, builder.getSubSect(0, 1));
        expected = new ArrayList<>();
        //expected.add(Lists.newArrayList(1));
        expected.add(Lists.newArrayList(1, 2));
        expected.add(Lists.newArrayList(1, 0));
        // expected.add(Lists.newArrayList(1, 4)); // wrong orientation for our aspect ratio
        // expected.add(Lists.newArrayList(1, 2, 4, 5));
        // expected.add(Lists.newArrayList(1, 0, 4, 3));
        // expected.add(Lists.newArrayList(1, 4, 7));
        // expected.add(Lists.newArrayList(1, 2, 4, 5, 7, 8));
        // expected.add(Lists.newArrayList(1, 0, 4, 3, 7, 6));
        assertEquals(expected, simplifyPermutations(actual));
    }

    @Test
    public void testStretchyAspectRatioConstraint() {

        ExhaustiveUnilateralRuptureGrowingStrategy ucerf3Strategy = new ExhaustiveUnilateralRuptureGrowingStrategy();

        // single section
        DownDipSubSectBuilder builder = mockDownDipBuilder(0, 2, 3);
        FaultSubsectionCluster cluster = new FaultSubsectionCluster(builder.getSubSectsList());
        DownDipRegistry registry = mockDownDipRegistry(builder);

        // depth threshold too large, no stretching
        DownDipPermutationStrategy strategy = new DownDipPermutationStrategy(registry, ucerf3Strategy);
        strategy.addAspectRatioConstraint(1, 1, 3);

        List<FaultSubsectionCluster> actual = strategy.getVariations(cluster, builder.getSubSect(0, 0));
        List<List<Integer>> expected = new ArrayList<>();
        expected.add(Lists.newArrayList(0));
        expected.add(Lists.newArrayList(0, 1, 3, 4));
        assertEquals(expected, simplifyPermutations(actual));

        // when depth threshold is hit, the aspect ratio can stretch larger, but not smaller
        strategy = new DownDipPermutationStrategy(registry, ucerf3Strategy);
        strategy.addAspectRatioConstraint(1, 1, 2);

        actual = strategy.getVariations(cluster, builder.getSubSect(0, 0));
        expected = new ArrayList<>();
        expected.add(Lists.newArrayList(0));
        expected.add(Lists.newArrayList(0, 1, 3, 4));
        expected.add(Lists.newArrayList(0, 1, 2, 3, 4, 5));
        assertEquals(expected, simplifyPermutations(actual));
    }

    @Test
    public void testSizeCoarsenessConstraint() {

        ExhaustiveUnilateralRuptureGrowingStrategy ucerf3Strategy = new ExhaustiveUnilateralRuptureGrowingStrategy();

        // single section, too large coarseness
        DownDipSubSectBuilder builder = mockDownDipBuilder(0, 1, 1);
        FaultSubsectionCluster cluster = new FaultSubsectionCluster(builder.getSubSectsList());
        DownDipRegistry registry = mockDownDipRegistry(builder);
        DownDipPermutationStrategy strategy = new DownDipPermutationStrategy(registry, ucerf3Strategy);
        strategy.addSizeCoarsenessConstraint(4);
        List<FaultSubsectionCluster> actual = strategy.getVariations(cluster, builder.getSubSect(0, 0));
        assertEquals(0, actual.size());

        // single section, small coarseness
        strategy = new DownDipPermutationStrategy(registry, ucerf3Strategy);
        strategy.addSizeCoarsenessConstraint(1);
        actual = strategy.getVariations(cluster, builder.getSubSect(0, 0));
        List<List<Integer>> expected = new ArrayList<>();
        expected.add(Lists.newArrayList(0));
        assertEquals(expected, simplifyPermutations(actual));

        // 3x3 downDip
        builder = mockDownDipBuilder(0, 3, 3);
        registry = mockDownDipRegistry(builder);
        cluster = new FaultSubsectionCluster(builder.getSubSectsList());

        // size coarseness so permissive that we take everything
        strategy = new DownDipPermutationStrategy(registry, ucerf3Strategy);
        strategy.addSizeCoarsenessConstraint(0.1);
        actual = strategy.getVariations(cluster, builder.getSubSect(0, 1));
        expected = new ArrayList<>();
        expected.add(Lists.newArrayList(1));
        expected.add(Lists.newArrayList(1, 2));
        expected.add(Lists.newArrayList(1, 0));
        expected.add(Lists.newArrayList(1, 4));
        expected.add(Lists.newArrayList(1, 2, 4, 5));
        expected.add(Lists.newArrayList(1, 0, 4, 3));
        expected.add(Lists.newArrayList(1, 4, 7));
        expected.add(Lists.newArrayList(1, 2, 4, 5, 7, 8));
        expected.add(Lists.newArrayList(1, 0, 4, 3, 7, 6));
        assertEquals(expected, simplifyPermutations(actual));

        // size coarseness weeds out ruptures that don't fit
        strategy = new DownDipPermutationStrategy(registry, ucerf3Strategy);
        strategy.addSizeCoarsenessConstraint(0.5);
        actual = strategy.getVariations(cluster, builder.getSubSect(0, 1));
        expected = new ArrayList<>();
        expected.add(Lists.newArrayList(1));
        expected.add(Lists.newArrayList(1, 2));
        expected.add(Lists.newArrayList(1, 0));
        expected.add(Lists.newArrayList(1, 4));
        expected.add(Lists.newArrayList(1, 2, 4, 5));
        expected.add(Lists.newArrayList(1, 0, 4, 3));
        // expected.add(Lists.newArrayList(1, 4, 7));
        //  expected.add(Lists.newArrayList(1, 2, 4, 5, 7, 8));
        // expected.add(Lists.newArrayList(1, 0, 4, 3, 7, 6));
        assertEquals(expected, simplifyPermutations(actual));
    }

    @Test
    public void testMinFillConstraint() {

        ExhaustiveUnilateralRuptureGrowingStrategy ucerf3Strategy = new ExhaustiveUnilateralRuptureGrowingStrategy();

        // single section
        DownDipSubSectBuilder builder = mockDownDipBuilder(0, 1, 1);
        FaultSubsectionCluster cluster = new FaultSubsectionCluster(builder.getSubSectsList());
        DownDipRegistry registry = mockDownDipRegistry(builder);
        DownDipPermutationStrategy strategy = new DownDipPermutationStrategy(registry, ucerf3Strategy);
        strategy.addMinFillConstraint(1);
        List<FaultSubsectionCluster> actual = strategy.getVariations(cluster, builder.getSubSect(0, 0));
        List<List<Integer>> expected = new ArrayList<>();
        expected.add(Lists.newArrayList(0));
        assertEquals(expected, simplifyPermutations(actual));

        // 3x3 downDip with hole
        builder = mockDownDipBuilder(0, 3, 3, 0, 2);
        registry = mockDownDipRegistry(builder);
        cluster = new FaultSubsectionCluster(builder.getSubSectsList());

        // minFill is so permissive that we take everything
        strategy = new DownDipPermutationStrategy(registry, ucerf3Strategy);
        strategy.addMinFillConstraint(0.1);
        actual = strategy.getVariations(cluster, builder.getSubSect(0, 1));
        expected = new ArrayList<>();
        expected.add(Lists.newArrayList(1));
        expected.add(Lists.newArrayList(1));
        expected.add(Lists.newArrayList(1, 0));
        expected.add(Lists.newArrayList(1, 3));
        expected.add(Lists.newArrayList(1, 3, 4));
        expected.add(Lists.newArrayList(1, 0, 3, 2));
        expected.add(Lists.newArrayList(1, 3, 6));
        expected.add(Lists.newArrayList(1, 3, 4, 6, 7));
        expected.add(Lists.newArrayList(1, 0, 3, 2, 6, 5));
        assertEquals(expected, simplifyPermutations(actual));

        // minFill is very strict and weeds out all holes
        strategy = new DownDipPermutationStrategy(registry, ucerf3Strategy);
        strategy.addMinFillConstraint(1);
        actual = strategy.getVariations(cluster, builder.getSubSect(0, 1));
        expected = new ArrayList<>();
        expected.add(Lists.newArrayList(1));
        // expected.add(Lists.newArrayList(1));
        expected.add(Lists.newArrayList(1, 0));
        expected.add(Lists.newArrayList(1, 3));
        //   expected.add(Lists.newArrayList(1, 3, 4));
        expected.add(Lists.newArrayList(1, 0, 3, 2));
        expected.add(Lists.newArrayList(1, 3, 6));
        //  expected.add(Lists.newArrayList(1, 3, 4, 6, 7));
        expected.add(Lists.newArrayList(1, 0, 3, 2, 6, 5));
        assertEquals(expected, simplifyPermutations(actual));
    }

    public DownDipSubSectBuilder mockDownDipBuilder(int parentId, int numRows, int numCols) {
        return mockDownDipBuilder(parentId, numRows, numCols, Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    public DownDipSubSectBuilder mockDownDipBuilder(int parentId, int numRows, int numCols, int holeRow, int holeCol) {
        DownDipSubSectBuilder builder = mock(DownDipSubSectBuilder.class);
        when(builder.getParentID()).thenReturn(parentId);
        when(builder.getNumRows()).thenReturn(numRows);
        when(builder.getNumCols()).thenReturn(numCols);
        FaultSectionList sections = new FaultSectionList();
        when(builder.getSubSectsList()).thenReturn(sections);
        for (int r = 0; r < numRows; r++) {
            for (int c = 0; c < numCols; c++) {
                if (holeRow == r && holeCol == c) {
                    when(builder.getSubSect(r, c)).thenReturn(null);
                } else {
                    FaultSection section = mockSection(parentId, sections.getSafeId());
                    sections.add(section);
                    when(builder.getRow(section)).thenReturn(r);
                    when(builder.getColumn(section)).thenReturn(c);
                    when(builder.getSubSect(r, c)).thenReturn(section);
                }
            }
        }
        return builder;
    }

    public List<List<Integer>> simplifyPermutations(List<FaultSubsectionCluster> permutations) {
        List<List<Integer>> result = new ArrayList<>();
        for (FaultSubsectionCluster cluster : permutations) {
            List<Integer> rupture = new ArrayList<>();
            result.add(rupture);
            for (FaultSection section : cluster.subSects) {
                rupture.add(section.getSectionId());
            }
        }
        return result;
    }

    public FaultSection mockSection(int parentID, int id) {
        FaultSection section = mock(FaultSection.class);
        when(section.getParentSectionId()).thenReturn(parentID);
        when(section.getSectionId()).thenReturn(id);
        when(section.getAveDip()).thenReturn(10.0);
        return section;
    }

    public DownDipRegistry mockDownDipRegistry(DownDipSubSectBuilder builder) {
        DownDipRegistry registry = mock(DownDipRegistry.class);
        when(registry.getBuilder(builder.getParentID())).thenReturn(builder);
        return registry;
    }
}

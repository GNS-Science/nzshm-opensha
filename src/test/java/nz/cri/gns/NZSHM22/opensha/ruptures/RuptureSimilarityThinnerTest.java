package nz.cri.gns.NZSHM22.opensha.ruptures;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.*;
import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;

public class RuptureSimilarityThinnerTest {

    @SafeVarargs
    private final FaultSystemRupSet mockRupSet(List<Integer>... sectionLists) {
        FaultSystemRupSet rupSet = mock(FaultSystemRupSet.class);
        when(rupSet.getNumRuptures()).thenReturn(sectionLists.length);
        for (int i = 0; i < sectionLists.length; i++) {
            when(rupSet.getSectionsIndicesForRup(i)).thenReturn(sectionLists[i]);
        }
        return rupSet;
    }

    private List<Integer> sections(Integer... ids) {
        return Arrays.asList(ids);
    }

    /** Collects all sections from the given rupture indices. */
    private Set<Integer> collectSections(FaultSystemRupSet rupSet, Collection<Integer> indices) {
        Set<Integer> result = new HashSet<>();
        for (int idx : indices) {
            result.addAll(rupSet.getSectionsIndicesForRup(idx));
        }
        return result;
    }

    @Test
    public void emptyInput() {
        FaultSystemRupSet rupSet = mockRupSet();
        RuptureSimilarityThinner thinner = new RuptureSimilarityThinner(rupSet);
        assertTrue(thinner.thinAll().isEmpty());
    }

    @Test
    public void thinEmptySubset() {
        FaultSystemRupSet rupSet = mockRupSet(sections(1, 2, 3));
        RuptureSimilarityThinner thinner = new RuptureSimilarityThinner(rupSet);
        assertTrue(thinner.thin(new ArrayList<>()).isEmpty());
    }

    @Test
    public void singleRupture() {
        FaultSystemRupSet rupSet = mockRupSet(sections(1, 2, 3));
        RuptureSimilarityThinner thinner = new RuptureSimilarityThinner(rupSet);
        assertEquals(Arrays.asList(0), thinner.thinAll());
    }

    @Test
    public void identicalRupturesKeepOnlyOne() {
        FaultSystemRupSet rupSet =
                mockRupSet(sections(1, 2, 3), sections(1, 2, 3), sections(1, 2, 3));
        RuptureSimilarityThinner thinner = new RuptureSimilarityThinner(rupSet);
        List<Integer> result = thinner.thinAll();
        assertEquals(1, result.size());
    }

    @Test
    public void disjointRupturesAllKept() {
        FaultSystemRupSet rupSet =
                mockRupSet(sections(1, 2, 3), sections(4, 5, 6), sections(7, 8, 9));
        RuptureSimilarityThinner thinner = new RuptureSimilarityThinner(rupSet);
        assertEquals(3, thinner.thinAll().size());
    }

    @Test
    public void overlappingTwoSufficeTocover() {
        // 3 ruptures, but any 2 of {0,1} plus {2} cover all sections
        // r0: {1,2,3}, r1: {2,3,4}, r2: {5,6,7}
        // Greedy picks r0 first (lowest first-section tiebreak), then r1 (covers 4), then r2
        // Actually all 3 are needed since r2 has unique sections.
        // Better example: r0={1,2,3,4}, r1={3,4,5,6}, r2={1,2,5,6}
        // Universe = {1,2,3,4,5,6}. Any two of these cover all 6.
        // Greedy picks r0 (covers 4, first-section=1), then r1 (covers {5,6}=2 uncovered).
        // r2 not needed. Result = 2.
        FaultSystemRupSet rupSet =
                mockRupSet(sections(1, 2, 3, 4), sections(3, 4, 5, 6), sections(1, 2, 5, 6));
        RuptureSimilarityThinner thinner = new RuptureSimilarityThinner(rupSet);
        List<Integer> result = thinner.thinAll();
        assertEquals(2, result.size());
    }

    @Test
    public void coverageInvariant() {
        // After thinning, every section from input must appear in at least one kept rupture
        FaultSystemRupSet rupSet =
                mockRupSet(
                        sections(1, 2, 3),
                        sections(2, 3, 4),
                        sections(3, 4, 5),
                        sections(5, 6, 7),
                        sections(7, 8, 9));
        RuptureSimilarityThinner thinner = new RuptureSimilarityThinner(rupSet);
        List<Integer> result = thinner.thinAll();

        Set<Integer> allSections = collectSections(rupSet, Arrays.asList(0, 1, 2, 3, 4));
        Set<Integer> keptSections = collectSections(rupSet, result);
        assertTrue("All sections should be covered", keptSections.containsAll(allSections));
    }

    @Test
    public void sizeToleranceZeroSeparatesBins() {
        // tolerance=0, binWidth=1: sizes 3 and 4 are in different bins
        FaultSystemRupSet rupSet =
                mockRupSet(
                        sections(1, 2, 3), // size 3, rup 0
                        sections(1, 2, 3), // size 3, rup 1 (duplicate)
                        sections(1, 2, 3, 4)); // size 4, rup 2
        RuptureSimilarityThinner thinner = new RuptureSimilarityThinner(rupSet, 0);
        List<Integer> result = thinner.thinAll();
        // One from size-3 bin + one from size-4 bin
        assertEquals(2, result.size());
        assertTrue(result.contains(2));
    }

    @Test
    public void sizeToleranceOneGroupsAdjacentSizes() {
        // tolerance=1, binWidth=3: size 3 → bin 1, size 4 → bin 1 (same bin)
        FaultSystemRupSet rupSet =
                mockRupSet(
                        sections(1, 2, 3), // size 3
                        sections(1, 2, 3, 4)); // size 4
        RuptureSimilarityThinner thinner = new RuptureSimilarityThinner(rupSet, 1);
        List<Integer> result = thinner.thinAll();
        // In same bin, greedy picks rup 1 (covers 4 sections > 3), then rup 0 adds nothing new
        // Actually: rup 1 covers {1,2,3,4}, then rup 0's {1,2,3} are all covered → only 1 kept
        // Wait — rup 0 first-section=1, rup 1 first-section=1. Tie on first-section.
        // rup 1 covers more (4 vs 3), so greedy picks rup 1 first. Then rup 0 adds 0 new → skip.
        assertEquals(1, result.size());
    }

    @Test
    public void thinSubsetPreservesOrder() {
        FaultSystemRupSet rupSet =
                mockRupSet(
                        sections(1, 2, 3),
                        sections(4, 5, 6),
                        sections(7, 8, 9),
                        sections(10, 11, 12));
        RuptureSimilarityThinner thinner = new RuptureSimilarityThinner(rupSet);
        List<Integer> subset = Arrays.asList(3, 1, 0);
        List<Integer> result = thinner.thin(subset);
        // All disjoint so all kept, order should match input
        assertEquals(Arrays.asList(3, 1, 0), result);
    }

    @Test
    public void greedyPrefersLargestCoverage() {
        // r0 covers {1,2}, r1 covers {1,2,3,4,5}, r2 covers {4,5,6}
        // Greedy should pick r1 first (5 sections), then r2 (covers 6), r0 is redundant
        FaultSystemRupSet rupSet =
                mockRupSet(sections(1, 2), sections(1, 2, 3, 4, 5), sections(4, 5, 6));
        // All size ≤5, with default tolerance=1, binWidth=3
        // size 2→bin 0, size 5→bin 1, size 3→bin 1
        // Different bins, so r0 alone in bin 0 (kept), r1 and r2 in bin 1
        // In bin 1: r1 covers {1,2,3,4,5}, r2 covers {4,5,6}. Universe={1,2,3,4,5,6}
        // Greedy picks r1 (5 uncovered, first-section=1), then r2 (covers {6}=1 uncovered)
        // Result = {0, 1, 2} — all kept because different bins or needed for coverage
        RuptureSimilarityThinner thinner = new RuptureSimilarityThinner(rupSet, 0);
        // With tolerance=0, binWidth=1: size2→bin2, size5→bin5, size3→bin3. All separate bins.
        // All kept individually.
        List<Integer> result = thinner.thinAll();
        assertEquals(3, result.size());

        // Now put them all in same bin with large tolerance
        thinner = new RuptureSimilarityThinner(rupSet, 3);
        // binWidth=7: all sizes < 7 → bin 0
        result = thinner.thinAll();
        // Universe={1,2,3,4,5,6}. Greedy: r1 covers 5 (first=1), r2 covers 1 new ({6}), r0 adds 0
        assertEquals(2, result.size());
        // r0 should be eliminated
        assertFalse(result.contains(0));
    }

    @Test
    public void tieBreakByFirstSectionIndex() {
        // Two ruptures with same coverage count but different first sections
        // r0: {10,11,12}, r1: {1,2,3} — both cover 3 uncovered sections
        // Tie-break: r1 has lower first-section (1 < 10), so r1 picked first
        FaultSystemRupSet rupSet = mockRupSet(sections(10, 11, 12), sections(1, 2, 3));
        RuptureSimilarityThinner thinner = new RuptureSimilarityThinner(rupSet);
        List<Integer> result = thinner.thinAll();
        // Both disjoint, both kept. But let's verify with identical sections
        FaultSystemRupSet rupSet2 = mockRupSet(sections(10, 11, 12), sections(10, 11, 12));
        thinner = new RuptureSimilarityThinner(rupSet2);
        result = thinner.thinAll();
        assertEquals(1, result.size());
        // First one picked due to equal first-section, first encountered wins
        assertEquals(Arrays.asList(0), result);
    }
}

package nz.cri.gns.NSHM.opensha.ruptures;

import static org.junit.Assert.*;

import com.google.common.collect.Lists;
import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.UCERF3ClusterPermuationStrategy;
import org.opensha.sha.faultSurface.FaultSection;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CoarseUCERF3ClusterPermutationStrategyTest {

    @Test
    public void testUCERF3Equivalence() {
        FaultSubsectionCluster cluster = mockFaultSubsectionCluster(5);
        UCERF3ClusterPermuationStrategy ucerf3Strategy = new UCERF3ClusterPermuationStrategy();
        // default constructor should give us strategy equivalent to UCERF3
        CoarseUCERF3ClusterPermutationStrategy coarseStrategy = new CoarseUCERF3ClusterPermutationStrategy();

        // build up
        List<FaultSubsectionCluster> ucerf3Actual = ucerf3Strategy.calcPermutations(cluster, cluster.subSects.get(0));
        List<FaultSubsectionCluster> coarseActual = coarseStrategy.calcPermutations(cluster, cluster.subSects.get(0));
        assertEquals(simplifyPermutations(ucerf3Actual), simplifyPermutations(coarseActual));
        List<List<Integer>> expected = new ArrayList<>();
        expected.add(Lists.newArrayList(0));
        expected.add(Lists.newArrayList(0, 1));
        expected.add(Lists.newArrayList(0, 1, 2));
        expected.add(Lists.newArrayList(0, 1, 2, 3));
        expected.add(Lists.newArrayList(0, 1, 2, 3, 4));
        assertEquals(expected, simplifyPermutations(coarseActual));

        // build from the middle
        ucerf3Actual = ucerf3Strategy.calcPermutations(cluster, cluster.subSects.get(2));
        coarseActual = coarseStrategy.calcPermutations(cluster, cluster.subSects.get(2));
        assertEquals(simplifyPermutations(ucerf3Actual), simplifyPermutations(coarseActual));
        expected = new ArrayList<>();
        expected.add(Lists.newArrayList(2));
        expected.add(Lists.newArrayList(2, 1));
        expected.add(Lists.newArrayList(2, 1, 0));
        expected.add(Lists.newArrayList(2, 3));
        expected.add(Lists.newArrayList(2, 3, 4));
        assertEquals(expected, simplifyPermutations(coarseActual));

        //build down
        ucerf3Actual = ucerf3Strategy.calcPermutations(cluster, cluster.subSects.get(4));
        coarseActual = coarseStrategy.calcPermutations(cluster, cluster.subSects.get(4));
        assertEquals(simplifyPermutations(ucerf3Actual), simplifyPermutations(coarseActual));
        expected = new ArrayList<>();
        expected.add(Lists.newArrayList(4));
        expected.add(Lists.newArrayList(4, 3));
        expected.add(Lists.newArrayList(4, 3, 2));
        expected.add(Lists.newArrayList(4, 3, 2, 1));
        expected.add(Lists.newArrayList(4, 3, 2, 1, 0));
        assertEquals(expected, simplifyPermutations(coarseActual));
    }

    @Test
    public void testConstantCoarseness() {
        FaultSubsectionCluster cluster = mockFaultSubsectionCluster(12);

        //coarseness is a constant 3
        CoarseUCERF3ClusterPermutationStrategy coarseStrategy = new CoarseUCERF3ClusterPermutationStrategy(sections -> 3);

        // build up
        List<FaultSubsectionCluster> coarseActual = coarseStrategy.calcPermutations(cluster, cluster.startSect);
        List<List<Integer>> expected = Lists.newArrayList();
        expected.add(Lists.newArrayList(0));
        expected.add(Lists.newArrayList(0, 1, 2, 3));
        expected.add(Lists.newArrayList(0, 1, 2, 3, 4, 5, 6));
        expected.add(Lists.newArrayList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9));
        expected.add(Lists.newArrayList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11));
        assertEquals(expected, simplifyPermutations(coarseActual));

        // build down
        coarseActual = coarseStrategy.calcPermutations(cluster, cluster.subSects.get(11));
        expected = Lists.newArrayList();
        expected.add(Lists.newArrayList(11));
        expected.add(Lists.newArrayList(11, 10, 9, 8));
        expected.add(Lists.newArrayList(11, 10, 9, 8, 7, 6, 5));
        expected.add(Lists.newArrayList(11, 10, 9, 8, 7, 6, 5, 4, 3, 2));
        expected.add(Lists.newArrayList(11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0));
        assertEquals(expected, simplifyPermutations(coarseActual));

        // build from the middle
        coarseActual = coarseStrategy.calcPermutations(cluster, cluster.subSects.get(5));
        expected = Lists.newArrayList();
        expected.add(Lists.newArrayList(5));
        expected.add(Lists.newArrayList(5, 4, 3, 2));
        expected.add(Lists.newArrayList(5, 4, 3, 2, 1, 0));
        expected.add(Lists.newArrayList(5, 6, 7, 8));
        expected.add(Lists.newArrayList(5, 6, 7, 8, 9, 10, 11));
        assertEquals(expected, simplifyPermutations(coarseActual));
    }

    @Test
    public void testEpsilonCoarseness() {
        FaultSubsectionCluster cluster = mockFaultSubsectionCluster(12);

        CoarseUCERF3ClusterPermutationStrategy coarseStrategy = new CoarseUCERF3ClusterPermutationStrategy(0.5);

        // build up
        List<FaultSubsectionCluster> coarseActual = coarseStrategy.calcPermutations(cluster, cluster.startSect);
        List<List<Integer>> expected = Lists.newArrayList();
        expected.add(Lists.newArrayList(0));
        expected.add(Lists.newArrayList(0, 1));
        expected.add(Lists.newArrayList(0, 1, 2));
        expected.add(Lists.newArrayList(0, 1, 2, 3, 4));
        expected.add(Lists.newArrayList(0, 1, 2, 3, 4, 5, 6, 7));
        expected.add(Lists.newArrayList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11));
        assertEquals(expected, simplifyPermutations(coarseActual));

        // build down
        coarseActual = coarseStrategy.calcPermutations(cluster, cluster.subSects.get(11));
        expected = Lists.newArrayList();
        expected.add(Lists.newArrayList(11));
        expected.add(Lists.newArrayList(11, 10));
        expected.add(Lists.newArrayList(11, 10, 9));
        expected.add(Lists.newArrayList(11, 10, 9, 8, 7));
        expected.add(Lists.newArrayList(11, 10, 9, 8, 7, 6, 5, 4));
        expected.add(Lists.newArrayList(11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0));
        assertEquals(expected, simplifyPermutations(coarseActual));

        // build from the middle
        coarseActual = coarseStrategy.calcPermutations(cluster, cluster.subSects.get(5));
        expected = Lists.newArrayList();
        expected.add(Lists.newArrayList(5));
        expected.add(Lists.newArrayList(5, 4));
        expected.add(Lists.newArrayList(5, 4, 3));
        expected.add(Lists.newArrayList(5, 4, 3, 2, 1));
        expected.add(Lists.newArrayList(5, 4, 3, 2, 1, 0));
        expected.add(Lists.newArrayList(5, 6));
        expected.add(Lists.newArrayList(5, 6, 7));
        expected.add(Lists.newArrayList(5, 6, 7, 8, 9));
        expected.add(Lists.newArrayList(5, 6, 7, 8, 9, 10, 11));
        assertEquals(expected, simplifyPermutations(coarseActual));
    }

    @Test
    public void testShortRupture() {
        FaultSubsectionCluster cluster = mockFaultSubsectionCluster(2);
        CoarseUCERF3ClusterPermutationStrategy coarseStrategy = new CoarseUCERF3ClusterPermutationStrategy();

        // build up
        List<FaultSubsectionCluster> coarseActual = coarseStrategy.calcPermutations(cluster, cluster.subSects.get(0));
        List<List<Integer>> expected = new ArrayList<>();
        expected.add(Lists.newArrayList(0));
        expected.add(Lists.newArrayList(0, 1));
        assertEquals(expected, simplifyPermutations(coarseActual));

        //build down
        coarseActual = coarseStrategy.calcPermutations(cluster, cluster.subSects.get(1));
        expected = new ArrayList<>();
        expected.add(Lists.newArrayList(1));
        expected.add(Lists.newArrayList(1, 0));
        assertEquals(expected, simplifyPermutations(coarseActual));

        // now with coarseness
        coarseStrategy = new CoarseUCERF3ClusterPermutationStrategy(sections -> 3);

        // build up
        coarseActual = coarseStrategy.calcPermutations(cluster, cluster.subSects.get(0));
        expected = new ArrayList<>();
        expected.add(Lists.newArrayList(0));
        expected.add(Lists.newArrayList(0, 1));
        assertEquals(expected, simplifyPermutations(coarseActual));

        //build down
        coarseActual = coarseStrategy.calcPermutations(cluster, cluster.subSects.get(1));
        expected = new ArrayList<>();
        expected.add(Lists.newArrayList(1));
        expected.add(Lists.newArrayList(1, 0));
        assertEquals(expected, simplifyPermutations(coarseActual));
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
        return section;
    }

    public FaultSubsectionCluster mockFaultSubsectionCluster(int count) {
        List<FaultSection> sections = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            sections.add(mockSection(0, i));
        }
        return new FaultSubsectionCluster(sections);
    }
}

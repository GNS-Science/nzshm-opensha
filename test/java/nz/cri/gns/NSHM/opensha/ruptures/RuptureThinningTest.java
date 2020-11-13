package nz.cri.gns.NSHM.opensha.ruptures;

import static org.junit.Assert.*;

import com.google.common.collect.Lists;
import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.faultSurface.FaultSection;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class RuptureThinningTest {

    @Test
    public void filterRupturesTest() {
        List<ClusterRupture> actual = RuptureThinning.filterRuptures(Lists.newArrayList(), r -> true);
        assertEquals(0, actual.size());

        ClusterRupture rupture = mockRupture(4, mockSection(1, 1), mockSection(1, 2));
        List<ClusterRupture> ruptures = Lists.newArrayList(rupture);

        actual = RuptureThinning.filterRuptures(ruptures, r -> true);
        assertEquals(rupture, actual.get(0));
        assertEquals(1, actual.size());

        actual = RuptureThinning.filterRuptures(ruptures, r -> false);
        assertEquals(0, actual.size());

        ClusterRupture r1 = mockRupture(1);
        ClusterRupture r2 = mockRupture(2);
        ClusterRupture r3 = mockRupture(3);
        ClusterRupture r4 = mockRupture(4);
        ruptures = Lists.newArrayList(r1, r2, r3, r4);
        actual = RuptureThinning.filterRuptures(ruptures, r -> (r.getTotalNumSects() % 2) == 0);
        assertEquals(2, actual.size());
        assertEquals(2, actual.get(0).getTotalNumSects());
        assertEquals(4, actual.get(1).getTotalNumSects());
    }

    @Test
    public void coarsenessPredicateTest() {
        // accepts sizes 1, 2, 4, 7, 13, ...
        Predicate<ClusterRupture> predicate = RuptureThinning.coarsenessPredicate(0.8);

        assertTrue(predicate.test(mockRupture(1)));
        assertTrue(predicate.test(mockRupture(2)));
        assertFalse(predicate.test(mockRupture(3)));
        assertTrue(predicate.test(mockRupture(4)));
        assertFalse(predicate.test(mockRupture(5)));
        assertFalse(predicate.test(mockRupture(6)));
        assertTrue(predicate.test(mockRupture(7)));
    }

    @Test
    public void endToEndPredicateTest() {
        FaultSubsectionCluster cluster1 = mockFaultSubsectionCluster(0, 1, 5);
        FaultSubsectionCluster cluster2 = mockFaultSubsectionCluster(1, 7, 5);
        List<FaultSubsectionCluster> clusters = Lists.newArrayList(cluster1, cluster2);
        ClusterConnectionStrategy connectionStrategy = mock(ClusterConnectionStrategy.class);
        when(connectionStrategy.getClusters()).thenReturn(clusters);

        Predicate<ClusterRupture> predicate = RuptureThinning.endToEndPredicate(connectionStrategy);

        assertFalse(
                "two non-end sections",
                predicate.test(mockRupture(2, cluster1.subSects.get(1), cluster1.subSects.get(2))));
        assertFalse(
                "two non-end sections across clusters",
                predicate.test(mockRupture(2, cluster1.subSects.get(1), cluster2.subSects.get(2))));
        assertFalse(
                "one end section",
                predicate.test(mockRupture(2, cluster1.subSects.get(0), cluster2.subSects.get(2))));
        assertTrue(
                "two end sections on the same cluster",
                predicate.test(mockRupture(2, cluster1.subSects.get(0), cluster1.subSects.get(4))));
        assertTrue(
                "two end sections on the same cluster",
                predicate.test(mockRupture(2, cluster2.subSects.get(0), cluster2.subSects.get(4))));
        assertTrue(
                "two end sections on different clusters",
                predicate.test(mockRupture(2, cluster1.subSects.get(0), cluster2.subSects.get(4))));
        assertTrue(
                "two end sections on different clusters",
                predicate.test(mockRupture(2, cluster1.subSects.get(4), cluster2.subSects.get(4))));
    }

    public ClusterRupture mockRupture(int numSects) {
        return mockRupture(numSects, null, null);
    }

    public ClusterRupture mockRupture(int numSects, FaultSection startSection, FaultSection endSection) {
        ClusterRupture rupture = mock(ClusterRupture.class);
        when(rupture.getTotalNumSects()).thenReturn(numSects);
        List<FaultSection> sections = Lists.newArrayList(startSection, null, null, endSection);
        when(rupture.buildOrderedSectionList()).thenReturn(sections);
        return rupture;
    }

    public FaultSection mockSection(int parentID, int id) {
        FaultSection section = mock(FaultSection.class);
        when(section.getParentSectionId()).thenReturn(parentID);
        when(section.getSectionId()).thenReturn(id);
        return section;
    }

    public FaultSubsectionCluster mockFaultSubsectionCluster(int parentId, int startId, int count) {
        List<FaultSection> sections = new ArrayList<>();
        for (int i = startId; i < startId + count; i++) {
            sections.add(mockSection(parentId, i));
        }
        return new FaultSubsectionCluster(sections);
    }
}

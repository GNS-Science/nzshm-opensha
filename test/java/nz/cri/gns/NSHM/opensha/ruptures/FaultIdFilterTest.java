package nz.cri.gns.NSHM.opensha.ruptures;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import nz.cri.gns.NSHM.opensha.ruptures.FaultIdFilter;
import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.UniqueRupture;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FaultIdFilterTest {

    @Test
    public void withAnyTest() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {

        FaultIdFilter filter = FaultIdFilter.create(FaultIdFilter.FilterType.ANY, Sets.newHashSet(1, 41));

        ClusterRupture rupture_oneMatch = mockClusterRupture(1, 2, 3);
        ClusterRupture rupture_noMatch = mockClusterRupture(5, 6);
        ClusterRupture rupture_bothMatch = mockClusterRupture(1, 41);
        Jump jumpToNoMatch = mockJump(2);
        Jump jumpToMatch = mockJump(41);

        assertEquals(PlausibilityResult.FAIL_FUTURE_POSSIBLE,
                filter.apply(rupture_noMatch, false));
        assertEquals(PlausibilityResult.PASS,
                filter.apply(rupture_oneMatch, false));
        assertEquals(PlausibilityResult.PASS,
                filter.apply(rupture_bothMatch, false));

    }

    @Test
    public void withAllTest() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {

        FaultIdFilter filter = FaultIdFilter.create(FaultIdFilter.FilterType.ALL, Sets.newHashSet(1, 41));

        ClusterRupture rupture_partialMatch = mockClusterRupture(1, 2, 3);
        ClusterRupture rupture_noMatch = mockClusterRupture(5, 6);
        ClusterRupture rupture_fullMatch = mockClusterRupture(1, 41);
        Jump jumpToNoMatch = mockJump(2);
        Jump jumpToMatch = mockJump(41);

        assertEquals(PlausibilityResult.FAIL_FUTURE_POSSIBLE,
                filter.apply(rupture_noMatch, false));
        assertEquals(PlausibilityResult.FAIL_FUTURE_POSSIBLE,
                filter.apply(rupture_partialMatch, false));
        assertEquals(PlausibilityResult.PASS,
                filter.apply(rupture_fullMatch, false));

    }

    @Test
    public void withExactMatchTest() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {

        FaultIdFilter filter = FaultIdFilter.create(FaultIdFilter.FilterType.EXACT, Sets.newHashSet(1, 41));

        ClusterRupture rupture_partialMatch = mockClusterRupture(1, 2, 3);
        ClusterRupture rupture_noMatch = mockClusterRupture(5, 6);
        ClusterRupture rupture_possibleMatch = mockClusterRupture(1);
        ClusterRupture rupture_fullMatch = mockClusterRupture(1, 41);
        ClusterRupture rupture_notExactMatch = mockClusterRupture(1, 41, 5);
        Jump jumpToNoMatch = mockJump(2);
        Jump jumpToMatch = mockJump(41);

        assertEquals(PlausibilityResult.FAIL_HARD_STOP,
                filter.apply(rupture_noMatch, false));
        assertEquals(PlausibilityResult.FAIL_HARD_STOP,
                filter.apply(rupture_partialMatch, false));
        assertEquals(PlausibilityResult.FAIL_HARD_STOP,
                filter.apply(rupture_notExactMatch, false));
        assertEquals(PlausibilityResult.FAIL_FUTURE_POSSIBLE,
                filter.apply(rupture_possibleMatch, false));
        assertEquals(PlausibilityResult.PASS,
                filter.apply(rupture_fullMatch, false));

    }

    public FaultSubsectionCluster mockFaultSubsectionCluster(int parentId) {
        List<FaultSection> sections = new ArrayList<>();
        FaultSection section = mock(FaultSection.class);
        when(section.getParentSectionId()).thenReturn(parentId);
        sections.add(section);
        return new FaultSubsectionCluster(sections);
    }

    public ClusterRupture mockClusterRupture(int... parentIds) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        FaultSubsectionCluster[] clusters = new FaultSubsectionCluster[parentIds.length];
        for (int i = 0; i < clusters.length; i++) {
            clusters[i] = mockFaultSubsectionCluster(parentIds[i]);
        }
        Set<Integer> jumps = new HashSet<>();
        for (int i = 0; i < parentIds.length - 1; i++) {
            jumps.add(i);
        }
        UniqueRupture u1 = mock(UniqueRupture.class);
        when(u1.size()).thenReturn(parentIds.length - 1);
        UniqueRupture u2 = mock(UniqueRupture.class);
        when(u2.size()).thenReturn(parentIds.length - 1);

        Constructor<ClusterRupture> con = ClusterRupture.class.getDeclaredConstructor(
                FaultSubsectionCluster[].class, ImmutableSet.class, ImmutableMap.class, UniqueRupture.class, UniqueRupture.class, Boolean.TYPE);
        con.setAccessible(true);
        return con.newInstance(clusters, ImmutableSet.copyOf(jumps), ImmutableMap.of(), u1, u2, true);
    }

    public Jump mockJump(int toParentId) {
        FaultSubsectionCluster to = mockFaultSubsectionCluster(toParentId);
        FaultSubsectionCluster from = mockFaultSubsectionCluster(0); // unused, only there for assertion in constructor
        return new Jump(from.startSect, from, to.startSect, to, 0);
    }
}

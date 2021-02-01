package nz.cri.gns.NSHM.opensha.ruptures.downDip;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import nz.cri.gns.NSHM.opensha.util.FaultSectionList;
import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.UniqueRupture;
import org.opensha.sha.faultSurface.FaultSection;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FaultTypeSeparationFilterTest {

//    @Test
//    public void testApply(){
//DownDipRegistry registry = mock(DownDipRegistry.class);
//when(registry.isDownDip())
//    }

    public DownDipRegistry mockDownDipRegistry(DownDipSubSectBuilder builder) {
        DownDipRegistry registry = mock(DownDipRegistry.class);
        when(registry.getBuilder(builder.getParentID())).thenReturn(builder);
        return registry;
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
                FaultSubsectionCluster[].class, ImmutableSet.class, ImmutableMap.class, UniqueRupture.class, UniqueRupture.class);
        con.setAccessible(true);
        return con.newInstance(clusters, ImmutableSet.copyOf(jumps), ImmutableMap.of(), u1, u2);
    }
}

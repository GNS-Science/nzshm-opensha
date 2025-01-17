package nz.cri.gns.NZSHM22.opensha.ruptures.experimental.joint;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import nz.cri.gns.NZSHM22.opensha.ruptures.downDip.DownDipFaultSubSectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.UniqueRupture;
import org.opensha.sha.faultSurface.FaultSection;

/** Provides functionality to combine ruptures into a new rupture using join() and splay() */
public class ManipulatedClusterRupture extends ClusterRupture {

    /**
     * We need this class and this constructor because the super constructor it calls is protected.
     *
     * @param clusters
     * @param internalJumps
     * @param unique
     */
    public ManipulatedClusterRupture(
            FaultSubsectionCluster[] clusters,
            ImmutableList<Jump> internalJumps,
            UniqueRupture unique) {
        super(clusters, internalJumps, ImmutableMap.of(), unique, unique, true);
    }

    public ManipulatedClusterRupture(
            FaultSubsectionCluster[] clusters,
            ImmutableList<Jump> internalJumps,
            ImmutableMap<Jump, ClusterRupture> splays,
            UniqueRupture unique,
            UniqueRupture internalUnique) {
        super(
                clusters,
                internalJumps,
                splays,
                unique,
                internalUnique,
                splays == null || splays.isEmpty());
    }

    static FaultSection first(FaultSubsectionCluster cluster) {
        return cluster.startSect;
    }

    static FaultSection last(FaultSubsectionCluster cluster) {
        if (cluster instanceof DownDipFaultSubSectionCluster) {
            return ((DownDipFaultSubSectionCluster) cluster).last();
        }
        return cluster.subSects.get(cluster.subSects.size() - 1);
    }

    static ImmutableList<Jump> makeInternalJumps(
            ClusterRupture ruptureA,
            ClusterRupture ruptureB,
            SectionDistanceAzimuthCalculator disAzCalc) {

        List<Jump> jumps = new ArrayList<>();
        jumps.addAll(ruptureA.internalJumps);

        FaultSubsectionCluster fromCluster = ruptureA.clusters[ruptureA.clusters.length - 1];
        FaultSection from = last(fromCluster);
        FaultSubsectionCluster toCluster = ruptureB.clusters[0];
        FaultSection to = first(toCluster);
        double distance = disAzCalc.getDistance(from, to);
        jumps.add(new Jump(from, fromCluster, to, toCluster, distance));

        jumps.addAll(ruptureB.internalJumps);
        return ImmutableList.copyOf(jumps);
    }

    /**
     * Creates a new rupture by jumping from the last section of ruptureA to the first section of
     * ruptureB
     *
     * @param ruptureA
     * @param ruptureB
     * @param disAzCalc
     * @return
     */
    public static ManipulatedClusterRupture join(
            ClusterRupture ruptureA,
            ClusterRupture ruptureB,
            SectionDistanceAzimuthCalculator disAzCalc) {

        List<FaultSubsectionCluster> clusters = new ArrayList<>();
        clusters.addAll(Arrays.asList(ruptureA.clusters));
        clusters.addAll(Arrays.asList(ruptureB.clusters));
        FaultSubsectionCluster[] clusterArray = clusters.toArray(new FaultSubsectionCluster[0]);

        UniqueRupture unique = UniqueRupture.forClusters(clusterArray);
        ImmutableList<Jump> internalJumps = makeInternalJumps(ruptureA, ruptureB, disAzCalc);

        return new ManipulatedClusterRupture(clusterArray, internalJumps, unique);
    }

    /**
     * Creates a new rupture by taking a splay jump OpenSHA only allows taking a jump to a cluster,
     * not to a complete rupture.
     *
     * @param ruptureA
     * @param ruptureB
     * @param jump
     * @return
     */
    public static ManipulatedClusterRupture splay(
            ClusterRupture ruptureA, ClusterRupture ruptureB, Jump jump) {
        ImmutableMap.Builder<Jump, ClusterRupture> splayBuilder = ImmutableMap.builder();
        splayBuilder.putAll(ruptureA.splays);
        ClusterRupture targetRupture =
                new ManipulatedClusterRupture(
                        ruptureB.clusters, ruptureB.internalJumps, ruptureB.unique);
        splayBuilder.put(jump, targetRupture);
        ImmutableMap<Jump, ClusterRupture> newSplays = splayBuilder.build();
        UniqueRupture newUnique = UniqueRupture.add(ruptureA.unique, ruptureB.unique);
        return new ManipulatedClusterRupture(
                ruptureA.clusters,
                ruptureA.internalJumps,
                newSplays,
                newUnique,
                ruptureA.internalUnique);
    }

    /**
     * Safely reverses ruptures that may have a subduction cluster.
     *
     * @param rupture
     * @return
     */
    public static ClusterRupture reverse(ClusterRupture rupture) {
        Preconditions.checkState(rupture.singleStrand, "Can only reverse single strand ruptures");

        List<FaultSubsectionCluster> clusterList =
                Arrays.stream(rupture.clusters)
                        .map(FaultSubsectionCluster::reversed)
                        .collect(Collectors.toList());
        Collections.reverse(clusterList);

        List<Jump> jumps =
                rupture.internalJumps.stream()
                        .map(
                                j ->
                                        new Jump(
                                                j.toSection,
                                                j.toCluster,
                                                j.fromSection,
                                                j.fromCluster,
                                                j.distance))
                        .collect(Collectors.toList());
        Collections.reverse(jumps);

        return new ManipulatedClusterRupture(
                clusterList.toArray(new FaultSubsectionCluster[0]),
                ImmutableList.copyOf(jumps),
                rupture.unique);
    }
}

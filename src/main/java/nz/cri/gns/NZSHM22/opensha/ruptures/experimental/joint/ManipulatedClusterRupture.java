package nz.cri.gns.NZSHM22.opensha.ruptures.experimental.joint;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import nz.cri.gns.NZSHM22.opensha.ruptures.downDip.DownDipFaultSubSectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture.MultiRuptureJump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.UniqueRupture;
import org.opensha.sha.faultSurface.FaultSection;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Provides functionality to combine ruptures into a new rupture using join() and splay()
 */
public class ManipulatedClusterRupture extends ClusterRupture {

    /**
     * We need this class and this constructor because the super constructor it calls is protected.
     *
     * @param clusters
     * @param internalJumps
     * @param unique
     */
    public ManipulatedClusterRupture(FaultSubsectionCluster[] clusters, ImmutableList<Jump> internalJumps, UniqueRupture unique) {
        super(clusters, internalJumps, ImmutableMap.of(), unique, unique, true);
    }

    public ManipulatedClusterRupture
            (FaultSubsectionCluster[] clusters,
             ImmutableList<Jump> internalJumps,
             ImmutableMap<Jump, ClusterRupture> splays,
             UniqueRupture unique,
             UniqueRupture internalUnique) {
        super(clusters, internalJumps, splays, unique, internalUnique, splays == null || splays.isEmpty());
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

    static boolean isCrustal(FaultSection section) {
        return !section.getSectionName().contains("row:");
    }

    static boolean isSubduction(FaultSection section) {
        return section.getSectionName().contains("row:");
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
     * Creates a new rupture by jumping from the last section of ruptureA to the first section of ruptureB
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
     * Creates a new rupture by taking a splay jump
     * OpenSHA only allows taking a jump to a cluster, not to a complete rupture.
     *
     * @param ruptureA
     * @param ruptureB
     * @param jump
     * @return
     */
    public static ManipulatedClusterRupture splay(
            ClusterRupture ruptureA,
            ClusterRupture ruptureB,
            Jump jump) {
        ImmutableMap.Builder<Jump, ClusterRupture> splayBuilder = ImmutableMap.builder();
        splayBuilder.putAll(ruptureA.splays);
        ClusterRupture targetRupture = new ManipulatedClusterRupture(ruptureB.clusters, ruptureB.internalJumps, ruptureB.unique);
        splayBuilder.put(jump, targetRupture);
        ImmutableMap<Jump, ClusterRupture> newSplays = splayBuilder.build();
        UniqueRupture newUnique = UniqueRupture.add(ruptureA.unique, ruptureB.unique);
        return new ManipulatedClusterRupture(ruptureA.clusters, ruptureA.internalJumps, newSplays, newUnique, ruptureA.internalUnique);
    }

    public static ManipulatedClusterRupture splay(
            ClusterRupture rupture,
            ClusterRupture splayRupture) {
        Jump jump = new Jump(rupture.clusters[0].startSect, rupture.clusters[0], splayRupture.clusters[0].startSect, splayRupture.clusters[0], 5);
        return splay(rupture, splayRupture, jump);
    }


    /**
     * Safely reverses ruptures that may have a subduction cluster.
     *
     * @param rupture
     * @return
     */
    public static ClusterRupture reverse(ClusterRupture rupture) {
        Preconditions.checkState(rupture.singleStrand, "Can only reverse single strand ruptures");

        List<FaultSubsectionCluster> clusterList = Arrays.stream(rupture.clusters).map(FaultSubsectionCluster::reversed).collect(Collectors.toList());
        Collections.reverse(clusterList);

        List<Jump> jumps = rupture.internalJumps.stream().map(j -> new Jump(j.toSection, j.toCluster, j.fromSection, j.fromCluster, j.distance)).collect(Collectors.toList());
        Collections.reverse(jumps);

        return new ManipulatedClusterRupture(clusterList.toArray(new FaultSubsectionCluster[0]), ImmutableList.copyOf(jumps), rupture.unique);
    }

    public static ManipulatedClusterRupture makeFromClusters(List<FaultSubsectionCluster> clusters) {
        FaultSubsectionCluster[] clusterArray = clusters.toArray(new FaultSubsectionCluster[]{});
        List<Jump> jumps = new ArrayList<>();
        UniqueRupture uniqueRupture = clusters.stream().map(UniqueRupture::forClusters).reduce(UniqueRupture::add).get();

        for (int c = 1; c < clusterArray.length; c++) {
            FaultSubsectionCluster fromCluster = clusterArray[c - 1];
            FaultSection from = last(fromCluster);
            FaultSubsectionCluster toCluster = clusterArray[c];
            FaultSection to = first(toCluster);
            double distance = 5;
            jumps.add(new Jump(from, fromCluster, to, toCluster, distance));
        }

        return new ManipulatedClusterRupture(clusterArray, ImmutableList.copyOf(jumps), uniqueRupture);
    }

    public static ManipulatedClusterRupture makeFromSections(List<FaultSection> sections) {
        List<FaultSubsectionCluster> clusters = sections.stream()
                .collect(Collectors.groupingBy(FaultSection::getParentSectionId))
                .values().stream()
                .peek(list -> list.sort(Comparator.comparing(FaultSection::getSectionId)))
                .map(FaultSubsectionCluster::new)
                .collect(Collectors.toList());
        return makeFromClusters(clusters);
    }

    /**
     * Can be used if we get a list of jumbled FaultSections from RSQSim data
     *
     * @param sections
     * @return
     */
    public static ClusterRupture makeRupture(List<FaultSection> sections) {
        ManipulatedClusterRupture crustal = makeFromSections(sections.stream().filter(ManipulatedClusterRupture::isCrustal).collect(Collectors.toList()));
        ManipulatedClusterRupture subduction = makeFromSections(sections.stream().filter(ManipulatedClusterRupture::isSubduction).collect(Collectors.toList()));
        return splay(subduction, crustal);
    }

    /**
     * Splits a MultiRuptureJump into two separate ruptures so that we can apply Coulomb filters
     * @param jump
     * @return
     */
    public static MultiRuptureJump reconstructJump(ClusterRupture rupture) {
        List<FaultSection> fromSections = Arrays.stream(rupture.clusters).flatMap(c -> c.subSects.stream()).collect(Collectors.toList());
        List<FaultSection> toSections = rupture.splays.values().asList().get(0).buildOrderedSectionList();
        ClusterRupture fromRupture = ManipulatedClusterRupture.makeFromSections(fromSections);
        ClusterRupture toRupture = ManipulatedClusterRupture.makeFromSections(toSections);
        return new MultiRuptureJump(fromSections.get(0), fromRupture, toSections.get(0), toRupture, 10 );
    }

}

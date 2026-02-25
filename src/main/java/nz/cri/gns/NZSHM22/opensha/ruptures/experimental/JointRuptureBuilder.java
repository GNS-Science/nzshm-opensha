package nz.cri.gns.NZSHM22.opensha.ruptures.experimental;

import java.util.*;
import java.util.stream.Collectors;
import nz.cri.gns.NZSHM22.opensha.ruptures.FaultSectionProperties;
import nz.cri.gns.NZSHM22.opensha.ruptures.downDip.DownDipFaultSubSectionCluster;
import nz.cri.gns.NZSHM22.opensha.ruptures.experimental.joint.ManipulatedClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;

/**
 * Takes a rupture set with subduction and crustal ruptures and creates a new rupture set with joint
 * ruptures.
 *
 * <p>Creates sequential joint rupture sets. In this case, sequential means that subduction clusters
 * are part of a string of clusters. See JointRuptureBuilderParallel for creating more realistic
 * ruptures.
 */
public class JointRuptureBuilder {
    final List<ClusterRupture> ruptures;
    final SectionDistanceAzimuthCalculator distAzCalc;

    final int subductionMinCount;
    final int subductionMaxCount;

    // jumps from subduction to crustal
    final OneToManyMap<Integer, Integer> jumps;

    OneToManyMap<Integer, ClusterRupture> targetRuptures;

    static FaultSection first(ClusterRupture rupture) {
        return rupture.clusters[0].startSect;
    }

    // TODO make this work for subduction as well
    static FaultSection last(ClusterRupture rupture) {
        List<FaultSection> subSects = rupture.clusters[rupture.clusters.length - 1].subSects;
        return subSects.get(subSects.size() - 1);
    }

    public JointRuptureBuilder(
            List<Jump> possibleJumps,
            List<ClusterRupture> ruptures,
            SectionDistanceAzimuthCalculator distAzCalc,
            int crustalMinCount,
            int subductionMinCount,
            int subductionMaxCount) {

        this.ruptures = ruptures;
        this.distAzCalc = distAzCalc;
        this.subductionMinCount = subductionMinCount;
        this.subductionMaxCount = subductionMaxCount;

        // fill jumps with all jumps from subduction
        this.jumps = new OneToManyMap<>();
        possibleJumps.stream()
                .filter(j -> FaultSectionProperties.isSubduction(j.fromSection))
                .forEach(
                        j ->
                                jumps.append(
                                        j.fromSection.getSectionId(), j.toSection.getSectionId()));
        possibleJumps.stream()
                .filter(j -> FaultSectionProperties.isSubduction(j.toSection))
                .forEach(
                        j ->
                                jumps.append(
                                        j.toSection.getSectionId(), j.fromSection.getSectionId()));

        // fill targetRuptures with all non-pure-subduction ruptures that can be jumped to
        targetRuptures = new OneToManyMap<>();
        ruptures.stream()
                .filter(
                        r ->
                                r.clusters.length > 1
                                        || !(r.clusters[0]
                                                instanceof DownDipFaultSubSectionCluster))
                .filter(r -> r.clusters[0].subSects.size() >= crustalMinCount)
                .forEach(
                        r -> {
                            int startID = first(r).getSectionId();
                            if (jumps.values.contains(startID)) {
                                targetRuptures.append(startID, r);
                            }

                            int endId = last(r).getSectionId();
                            if (jumps.values.contains(endId)) {
                                targetRuptures.append(endId, r);
                            }
                        });
    }

    public void stats(List<FaultSection> subSections) {
        for (Integer from : jumps.keySet()) {
            if (FaultSectionProperties.isSubduction(subSections.get(from))) {
                FaultSection fromSection = subSections.get(from);

                List<ClusterRupture> targets = new ArrayList<>();
                for (Integer to : jumps.get(from)) {
                    targets.addAll(targetRuptures.get(to));
                }
                System.out.println(
                        ""
                                + fromSection.getParentSectionId()
                                + ":"
                                + fromSection.getSectionId()
                                + " r "
                                + FaultSectionProperties.getRowIndex(fromSection)
                                + " count "
                                + targets.size());
            }
        }
    }

    class JointJump {
        public final int target;
        public final ClusterRupture rupture;
        public final ClusterRupture origin;

        public JointJump(int target, ClusterRupture rupture) {
            this.origin = null;
            this.target = target;
            this.rupture = rupture;
        }

        public JointJump(ClusterRupture origin, int target, ClusterRupture rupture) {
            this.origin = origin;
            this.target = target;
            this.rupture = rupture;
        }
    }

    protected List<ClusterRupture> joinRuptures(ClusterRupture subductionRupture) {
        List<ClusterRupture> jointRuptures = new ArrayList<>();

        jointRuptures.addAll(
                jumps.get(first(subductionRupture).getSectionId()).stream()
                        .flatMap(
                                target ->
                                        targetRuptures.get(target).stream()
                                                .map(crustal -> new JointJump(target, crustal)))
                        .parallel()
                        .map(
                                jump -> {
                                    if (first(jump.rupture).getSectionId() == jump.target) {
                                        // crustal = ManipulatedClusterRupture.reverse(crustal);
                                        return null;
                                    }
                                    return ManipulatedClusterRupture.join(
                                            jump.rupture, subductionRupture, distAzCalc);
                                })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList()));

        List<ClusterRupture> candidates = new ArrayList<>(jointRuptures);
        candidates.add(subductionRupture);

        jointRuptures.addAll(
                candidates.stream()
                        .flatMap(
                                c ->
                                        jumps.get(last(c).getSectionId()).stream()
                                                .flatMap(
                                                        target ->
                                                                targetRuptures.get(target).stream()
                                                                        .map(
                                                                                crustal ->
                                                                                        new JointJump(
                                                                                                c,
                                                                                                target,
                                                                                                crustal))))
                        .parallel()
                        .map(
                                jump -> {
                                    try {
                                        if (first(jump.rupture).getSectionId() != jump.target) {
                                            // crustal = ManipulatedClusterRupture.reverse(crustal);
                                            return null;
                                        }
                                        return ManipulatedClusterRupture.join(
                                                jump.origin, jump.rupture, distAzCalc);
                                    } catch (IllegalStateException x) {
                                        // this can happen if we have created a circular rupture.
                                        // we need to find a more elegant way of detecting this.
                                        return null;
                                    }
                                })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList()));

        return jointRuptures;
    }

    public List<ClusterRupture> build(int parentId) {

        List<ClusterRupture> subductionRuptures =
                ruptures.stream()
                        .filter(r -> r.getTotalNumClusters() == 1)
                        .filter(r -> r.clusters[0].parentSectionID == parentId)
                        .filter(
                                r ->
                                        r.clusters[0].subSects.size() >= subductionMinCount
                                                && r.clusters[0].subSects.size()
                                                        <= subductionMaxCount)
                        .filter(
                                r ->
                                        jumps.containsKey(first(r).getSectionId())
                                                || jumps.containsKey(last(r).getSectionId()))
                        .collect(Collectors.toList());

        System.out.println(
                "Generating joint ruptures from "
                        + subductionRuptures.size()
                        + " subduction ruptures and "
                        + jumps.values.size()
                        + " jump targets for parent ID "
                        + parentId);
        List<ClusterRupture> jointRuptures = new ArrayList<>();
        for (ClusterRupture rupture : subductionRuptures) {
            jointRuptures.addAll(joinRuptures(rupture));
        }
        System.out.println("Generated " + jointRuptures.size() + " joint ruptures.");
        return jointRuptures;
    }
}

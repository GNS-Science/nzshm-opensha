package nz.cri.gns.NZSHM22.opensha.ruptures.experimental;

import nz.cri.gns.NZSHM22.opensha.ruptures.DownDipFaultSection;
import nz.cri.gns.NZSHM22.opensha.ruptures.downDip.DownDipFaultSubSectionCluster;
import nz.cri.gns.NZSHM22.opensha.ruptures.experimental.joint.ManipulatedClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Takes a rupture set with subduction and crustal ruptures and creates a new rupture set with joint ruptures.
 * <p>
 * Can build parallel joint ruptures , i.e. joint ruptures with splays where the subduction and crustal ruptures run in parallel.
 */
public class JointRuptureBuilderParallel {
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

    public static class JointJump extends Jump {
        public ClusterRupture rupture;

        public JointJump(
                FaultSection fromSection,
                FaultSubsectionCluster fromCluster,
                FaultSection toSection,
                FaultSubsectionCluster toCluster,
                ClusterRupture toRupture,
                double distance) {
            super(fromSection, fromCluster, toSection, toCluster, distance);
            this.rupture = toRupture;
        }
    }

    public static Jump findJump(ClusterRupture subductionRupture,
                                FaultSubsectionCluster targetCluster,
                                SectionDistanceAzimuthCalculator distAzCalc,
                                double maxJumpDist,
                                double coverage
    ) {
        List<FaultSection> subductionSections = subductionRupture.buildOrderedSectionList();
        List<Jump> jumps = new ArrayList<>();
        int totalCount = 0;
        for (FaultSection section : targetCluster.subSects) {
            totalCount++;
            int nearest = -1;
            double distance = Double.MAX_VALUE;
            for (int ss = 0; ss < subductionSections.size(); ss++) {
                double candidateDist = distAzCalc.getDistance(subductionSections.get(ss), section);
                if (candidateDist < distance) {
                    nearest = ss;
                    distance = candidateDist;
                }
            }
            if (distance <= maxJumpDist) {
                jumps.add(new Jump(subductionSections.get(nearest), subductionRupture.clusters[0], section, targetCluster, distance));
            }
        }

        double actualCoverage = jumps.size() / (double) totalCount;
        if (actualCoverage >= coverage) {
            Jump jump = jumps.get(0);
            // targetCluster.addConnection(new Jump(jump.toSection, jump.toCluster, jump.fromSection, jump.fromCluster, jump.distance));
            return jump;
        }

        return null;
    }

    public boolean isCrustal(ClusterRupture rupture) {
        return Arrays.stream(rupture.clusters).noneMatch(c -> c instanceof DownDipFaultSubSectionCluster);
    }

    public List<ClusterRupture> sortedCrustalRuptures(List<ClusterRupture> ruptures) {
        return ruptures.stream().
                filter(this::isCrustal).
                sorted((a, b) -> Integer.compare(b.getTotalNumSects(), a.getTotalNumSects())).
                collect(Collectors.toList());
    }

    /**
     * Finds a jump between the two ruptures if enough sections of the crustalRupture are within maxJumpDist
     *
     * @param subductionRupture the subduction rupture
     * @param crustalRupture    the crustal rupture
     * @param distAzCalc        distAzCalc
     * @param maxJumpDist       the max jump distance
     * @param coverage          percentage of crustal sections that need to be within maxJumpDist
     * @return
     */
    public static JointJump findJump(ClusterRupture subductionRupture,
                                     ClusterRupture crustalRupture,
                                     SectionDistanceAzimuthCalculator distAzCalc,
                                     double maxJumpDist,
                                     double coverage
    ) {
        List<FaultSection> subductionSections = subductionRupture.buildOrderedSectionList();
        List<JointJump> jumps = new ArrayList<>();
        int totalCount = 0;
        for (FaultSubsectionCluster cluster : crustalRupture.clusters) {
            for (FaultSection section : cluster.subSects) {
                totalCount++;
                int nearest = -1;
                double distance = Double.MAX_VALUE;
                for (int ss = 0; ss < subductionSections.size(); ss++) {
                    double candidateDist = distAzCalc.getDistance(subductionSections.get(ss), section);
                    if (candidateDist < distance) {
                        nearest = ss;
                        distance = candidateDist;
                    }
                }
                if (distance <= maxJumpDist) {
                    jumps.add(new JointJump(subductionSections.get(nearest), subductionRupture.clusters[0], section, cluster, crustalRupture, distance));
                }
            }
        }

        double actualCoverage = jumps.size() / (double) totalCount;
        if (actualCoverage >= coverage) {

            return jumps.get(0);
        }

        return null;
    }

    public static List<Jump> findAllJumps(ClusterRupture nucleation, List<FaultSubsectionCluster> targetClusters, SectionDistanceAzimuthCalculator distAzCalc) {
//        ClusterRupture nucleation = ruptures.get(97653);

        return targetClusters.parallelStream().
                map(cluster -> findJump(nucleation, cluster, distAzCalc, 5, 0.9)).
                filter(Objects::nonNull).
                collect(Collectors.toList());
    }

    static ClusterRupture grow(ClusterRupture main, Set<FaultSubsectionCluster> connectedClusters, FaultSubsectionCluster fromCluster) {
        Optional<Jump> optionalJump = fromCluster.getConnections().stream().
                filter(j -> connectedClusters.contains(j.toCluster)).
                filter(j -> !main.contains(j.toSection)).
                findAny();
        if (optionalJump.isEmpty()) {
            return main;
        }
        Jump jump = optionalJump.get();
        ClusterRupture newMain = main.take(jump);
        return grow(newMain, connectedClusters, jump.toCluster);
    }

    static ClusterRupture growSplay(ClusterRupture nucleation, Set<FaultSubsectionCluster> connectedClusters, Jump jump) {
        ClusterRupture main = nucleation.take(jump);
        return grow(main, connectedClusters, jump.toCluster);
    }

    public static List<ClusterRupture> makeSplays(ClusterRupture nucleation, List<FaultSubsectionCluster> targetClusters, SectionDistanceAzimuthCalculator distAzCalc) {
        List<Jump> jumps = findAllJumps(nucleation, targetClusters, distAzCalc);
        List<ClusterRupture> result = new ArrayList<>();

        Set<FaultSubsectionCluster> connectedClusters = jumps.stream().map(j -> j.toCluster).collect(Collectors.toSet());

        result.add(growSplay(nucleation, connectedClusters, jumps.get(0)));
        result.add(growSplay(nucleation, connectedClusters, jumps.get(10)));
        result.add(growSplay(nucleation, connectedClusters, jumps.get(50)));
        // result.add(growSplay(nucleation, connectedClusters, jumps.get(100)));

        //result.add(nucleation.take(jumps.get(0)));
        return result;
    }

    List<ClusterRupture> crustalRuptures;

    public JointRuptureBuilderParallel(List<ClusterRupture> ruptures, SectionDistanceAzimuthCalculator distAzCalc) {
        this.ruptures = ruptures;
        this.distAzCalc = distAzCalc;
        crustalRuptures = sortedCrustalRuptures(ruptures);
        subductionMaxCount = 0;
        subductionMinCount = 0;
        jumps = null;
    }

    public ClusterRupture makeRuptureSplay(ClusterRupture nucleation, double maxJumpDist, int maxSplayCount) {
        System.out.println("make splay");
        int splayCount = 0;
        ClusterRupture result = nucleation;
        Set<Integer> crustalSections = new HashSet<>();
        for (ClusterRupture crustalRupture : crustalRuptures) {
            List<Integer> sectionIds = crustalRupture.buildOrderedSectionList().stream().map(FaultSection::getSectionId).collect(Collectors.toList());
            boolean skip = false;
            for (Integer id : sectionIds) {
                if (crustalSections.contains(id)) {
                    skip = true;
                    break;
                }
            }
            if (skip) {
                continue;
            }
            Jump jump = findJump(nucleation, crustalRupture, distAzCalc, maxJumpDist, 0.9);
            if (jump != null) {
                result = ManipulatedClusterRupture.splay(result, crustalRupture, jump);
                crustalSections.addAll(sectionIds);
                splayCount++;
                if (splayCount >= maxSplayCount) {
                    return result;
                }
            }
        }
        return result;
    }

    public JointRuptureBuilderParallel(
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
                .filter(j -> j.fromSection instanceof DownDipFaultSection)
                .forEach(j -> jumps.append(j.fromSection.getSectionId(), j.toSection.getSectionId()));
        possibleJumps.stream()
                .filter(j -> j.toSection instanceof DownDipFaultSection)
                .forEach(j -> jumps.append(j.toSection.getSectionId(), j.fromSection.getSectionId()));


        // fill targetRuptures with all non-pure-subduction ruptures that can be jumped to
        targetRuptures = new OneToManyMap<>();
        ruptures.stream()
                .filter(r -> r.clusters.length > 1 || !(r.clusters[0] instanceof DownDipFaultSubSectionCluster))
                .filter(r -> r.clusters[0].subSects.size() >= crustalMinCount)
                .forEach(r -> {
                    for (FaultSection section : r.buildOrderedSectionList()) {
                        if (jumps.values.contains(section.getSectionId())) {
                            targetRuptures.append(section.getSectionId(), r);
                        }
                    }
                });
    }

    public void stats(List<FaultSection> subSections) {
        for (Integer from : jumps.keySet()) {
            if (subSections.get(from) instanceof DownDipFaultSection) {
                DownDipFaultSection fromSection = (DownDipFaultSection) subSections.get(from);

                List<ClusterRupture> targets = new ArrayList<>();
                for (Integer to : jumps.get(from)) {
                    targets.addAll(targetRuptures.get(to));
                }
                System.out.println("" + fromSection.getParentSectionId() + ":" + fromSection.getSectionId() + " r " + fromSection.getRowIndex() + " count " + targets.size());
            }
        }
    }

    //    protected List<ClusterRupture> joinRuptures(ClusterRupture subductionRupture) {
//        List<ClusterRupture> jointRuptures = new ArrayList<>();
//
//
//        if (subductionRupture.getTotalNumSects() <= 6) {
//            DownDipFaultSubSectionCluster cluster = (DownDipFaultSubSectionCluster) subductionRupture.clusters[0];
//            int topRow = cluster.ddSections.get(0).getRowIndex();
//            for (DownDipFaultSection section : cluster.ddSections) {
//                if (section.getRowIndex() == topRow) {
//                    List<Integer> candidates = jumps.get(section.getSectionId());
//                    for (Integer candidate : candidates) {
//                        for (ClusterRupture target : targetRuptures.get(candidate)) {
////                    if(target.getTotalNumSects() > (subductionRupture.getTotalNumSects() * 5)) {
////                        Jump jump = new Jump(section, cluster, )
////                    }
////                        }
//                    }
//
//                }
//            }
//
//            jointRuptures.addAll(
//                    jumps.get(first(subductionRupture).getSectionId()).stream()
//                            .flatMap(target -> targetRuptures.get(target).stream()
//                                    .map(crustal -> new JointJump(target, crustal)))
//                            .parallel()
//                            .map(jump -> {
//                                if (first(jump.rupture).getSectionId() == jump.target) {
//                                    // crustal = ManipulatedClusterRupture.reverse(crustal);
//                                    return null;
//                                }
//                                return ManipulatedClusterRupture.join(jump.rupture, subductionRupture, distAzCalc);
//                            }).filter(Objects::nonNull).collect(Collectors.toList()));
//
//
//            List<ClusterRupture> candidates = new ArrayList<>(jointRuptures);
//            candidates.add(subductionRupture);
//
//
//            return jointRuptures;
//        }
//
//        public List<ClusterRupture> build ( int parentId){
//
//            List<ClusterRupture> subductionRuptures = ruptures.stream()
//                    .filter(r -> r.getTotalNumClusters() == 1)
//                    .filter(r -> r.clusters[0].parentSectionID == parentId)
//                    .filter(r -> r.clusters[0].subSects.size() >= subductionMinCount && r.clusters[0].subSects.size() <= subductionMaxCount)
//                    .filter(r -> jumps.containsKey(first(r).getSectionId()) || jumps.containsKey(last(r).getSectionId()))
//                    .collect(Collectors.toList());
//
//
//            System.out.println("Generating joint ruptures from " + subductionRuptures.size() + " subduction ruptures and " + jumps.values.size() + " jump targets for parent ID " + parentId);
//            List<ClusterRupture> jointRuptures = new ArrayList<>();
//            for (ClusterRupture rupture : subductionRuptures) {
//                jointRuptures.addAll(joinRuptures(rupture));
//            }
//            System.out.println("Generated " + jointRuptures.size() + " joint ruptures.");
//            return jointRuptures;
//        }
//    }
}
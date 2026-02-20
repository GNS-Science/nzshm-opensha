package nz.cri.gns.NZSHM22.opensha.ruptures.experimental.joint;

import com.google.common.base.Preconditions;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import nz.cri.gns.NZSHM22.opensha.calc.SimplifiedScalingRelationship;
import nz.cri.gns.NZSHM22.opensha.ruptures.FaultSectionProperties;
import nz.cri.gns.NZSHM22.opensha.ruptures.downDip.DownDipFaultSubSectionCluster;
import nz.cri.gns.NZSHM22.opensha.ruptures.experimental.FishboneGenerator;
import nz.cri.gns.NZSHM22.opensha.ruptures.experimental.JointRuptureBuilderParallel;
import org.dom4j.DocumentException;
import org.opensha.commons.geo.Location;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupCartoonGenerator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;

/**
 * Can read ruptures from an existing rupture set that contains subduction and crustal ruptures and
 * combines them into joint ruptures. Experimental.
 */
public class JointRupturePostProcessor {

    String rupturesFile =
            "C:\\Users\\user\\GNS\\nzshm-opensha\\TEST\\ruptures\\rupset-disjointed.zip";
    double maxJumpDistance = 5;
    int crustalMinCount = 2;
    int subductionMinCount = 1;
    int subductionMaxCount = 6;

    List<ClusterRupture> ruptures;
    List<FaultSection> subSections;
    Set<Integer> subductionParents;

    SectionDistanceAzimuthCalculator distCalc;

    List<FaultSubsectionCluster> crustalClusters;
    List<FaultSubsectionCluster> subductionClusters;

    public JointRupturePostProcessor() {}

    /**
     * Takes a list of subsections and rebuilds the parent faults as clusters. Creates separate
     * lists for crustal and subduction faults.
     *
     * @param subSections
     */
    protected void makeParentClusters(List<FaultSection> subSections) {
        crustalClusters = new ArrayList<>();
        subductionClusters = new ArrayList<>();

        int parentId = subSections.get(0).getParentSectionId();
        boolean crustal = FaultSectionProperties.isCrustal(subSections.get(0));
        List<FaultSection> clusterSections = new ArrayList<>();
        for (FaultSection section : subSections) {
            if (parentId != section.getParentSectionId()) {
                if (crustal) {
                    crustalClusters.add(new FaultSubsectionCluster(clusterSections));
                } else {
                    subductionClusters.add(new DownDipFaultSubSectionCluster(clusterSections));
                }
                parentId = section.getParentSectionId();
                crustal = FaultSectionProperties.isCrustal(section);
                clusterSections = new ArrayList<>();
            }
            clusterSections.add(section);
        }
        if (crustal) {
            crustalClusters.add(new FaultSubsectionCluster(clusterSections));
        } else {
            subductionClusters.add(new DownDipFaultSubSectionCluster(clusterSections));
        }
    }

    /**
     * adds jumps between clusters if the shortest distance is less than maxJumpDistance
     *
     * @param clusters
     * @param distAzCalc
     * @param maxJumpDistance
     */
    public void addCrustalJumps(
            List<FaultSubsectionCluster> clusters,
            SectionDistanceAzimuthCalculator distAzCalc,
            double maxJumpDistance) {
        for (FaultSubsectionCluster from : clusters) {
            for (FaultSubsectionCluster to : clusters) {
                if (from == to) {
                    continue;
                }
                FaultSection last = from.subSects.get(from.subSects.size() - 1);
                FaultSection first = to.subSects.get(0);
                double distance = distAzCalc.getDistance(last.getSectionId(), first.getSectionId());
                if (distance <= maxJumpDistance) {
                    from.addConnection(new Jump(last, from, first, to, distance));
                    //  to.addConnection(new Jump(first, to, last, from, distance));
                }
            }
        }
    }

    /**
     * Returns the shortest jump between the two clusters
     *
     * @param from
     * @param to
     * @return
     */
    protected Jump shortestJump(FaultSubsectionCluster from, FaultSubsectionCluster to) {
        FaultSection a = null;
        FaultSection b = null;
        double dist = Double.MAX_VALUE;
        // TODO make this more efficient by checking the boundary boxes
        for (FaultSection fromS : from.subSects) {
            for (FaultSection toS : to.subSects) {
                double candidateDist = distCalc.getDistance(fromS, toS);
                if (candidateDist < dist) {
                    dist = candidateDist;
                    a = fromS;
                    b = toS;
                }
            }
        }
        return new Jump(a, from, b, to, dist);
    }

    /**
     * Returns possible jumps from the from cluster to the clusters in the clusters list.
     *
     * @param from
     * @param clusters
     * @return
     */
    protected List<Jump> clusterJumps(
            FaultSubsectionCluster from, List<FaultSubsectionCluster> clusters) {
        List<Jump> result =
                clusters.parallelStream()
                        .map(c -> shortestJump(from, c))
                        .filter(j -> j.distance <= maxJumpDistance)
                        .collect(Collectors.toList());
        if (!result.isEmpty()) {
            for (Jump j : result) {
                System.out.println(
                        "from "
                                + j.fromSection.getSectionId()
                                + " to: "
                                + j.toSection.getSectionId()
                                + " d: "
                                + j.distance
                                + " rakes: "
                                + j.fromSection.getAveRake()
                                + ", "
                                + j.toSection.getAveRake()
                                + " dips: "
                                + j.fromSection.getAveDip()
                                + ", "
                                + j.toSection.getAveDip());
            }
            List<Integer> ids =
                    result.stream()
                            .map(j -> j.toSection.getSectionId())
                            .collect(Collectors.toList());
            List<Double> ds = result.stream().map(j -> j.distance).collect(Collectors.toList());
            List<Double> rakes =
                    result.stream().map(j -> j.toSection.getAveRake()).collect(Collectors.toList());
            System.out.println("hello");
        }
        return result;
    }

    /**
     * finds the shortest jumps between clusters
     *
     * @return
     */
    protected List<List<Jump>> clusterJumps() {
        return subductionClusters.stream()
                .map(c -> clusterJumps(c, crustalClusters))
                .collect(Collectors.toList());
    }

    /**
     * Calculates the length of a cluster. Has special code for handling subduction clusters so that
     * only the top row is counted towards length
     *
     * @param cluster a cluster
     * @return the length of the cluster
     */
    private double calculateLength(FaultSubsectionCluster cluster) {

        if (FaultSectionProperties.isSubduction(cluster.subSects.get(0))) {
            int minRow =
                    cluster.subSects.stream()
                            .mapToInt(s -> new FaultSectionProperties(s).getRowIndex())
                            .min()
                            .getAsInt();
            return cluster.subSects.stream()
                    .filter(s -> new FaultSectionProperties(s).getRowIndex() == minRow)
                    .mapToDouble(FaultSection::getTraceLength)
                    .sum();
        }

        return cluster.subSects.stream().mapToDouble(FaultSection::getTraceLength).sum();
    }

    private double calculateLength(ClusterRupture rupture) {
        return Arrays.stream(rupture.clusters).mapToDouble(this::calculateLength).sum() * 1e3;
    }

    protected double[] buildLengths(List<ClusterRupture> ruptures) {
        // we don't count splays yet
        System.out.println("start length " + new Date());
        double[] result = new double[ruptures.size()];

        IntStream.range(0, ruptures.size())
                .parallel()
                .forEach(
                        r -> {
                            result[r] = calculateLength(ruptures.get(r));
                        });
        System.out.println("end length " + new Date());

        return result;
    }

    static boolean includes(List<FaultSection> rupture, int sectionId) {

        for (FaultSection section : rupture) {
            if (section.getSectionId() == sectionId) {
                return true;
            }
        }
        return false;
    }

    public void load(String ruptureSetPath) throws IOException {
        RuptureLoader ruptureLoader = new RuptureLoader();
        FaultSystemRupSet rupSet = FaultSystemRupSet.load(new File(ruptureSetPath));
        ruptureLoader.loadRuptures(rupSet);
        this.ruptures = ruptureLoader.ruptures;
        this.subSections = ruptureLoader.subSections;
        this.subductionParents = ruptureLoader.subductionParents;
        //        ClusterRupture rupture = null;
        //        int size = Integer.MAX_VALUE;
        //        int ruptureId = -1;
        //        for (int r = 0; r < ruptures.size(); r++) {
        //            ClusterRupture candidate = ruptures.get(r);
        //            List<FaultSection> sections = candidate.buildOrderedSectionList();
        //            if (includes(sections, 2295) && includes(sections, 2268)) {
        //                if (size > sections.size()) {
        //                    rupture = candidate;
        //                    size = sections.size();
        //                    ruptureId = r;
        //                }
        //            }
        //        }
        //        SimpleGeoJsonBuilder builder = new SimpleGeoJsonBuilder();
        //        for (FaultSection section : rupture.buildOrderedSectionList()) {
        //            builder.addFaultSection(section);
        //        }
        //        builder.toJSON("/tmp/choseRupture.geojson");
    }

    public void buildRuptureSet() throws DocumentException, IOException {
        load(rupturesFile);
        System.out.println(
                "Loaded ruptures: " + ruptures.size() + " sections: " + subSections.size());
        makeParentClusters(subSections);
        System.out.println(
                "Crustal clusters: "
                        + crustalClusters.size()
                        + " subduction clusters: "
                        + subductionClusters.size());

        System.out.println("Calculating shortest jumps to subductions");

        distCalc = new SectionDistanceAzimuthCalculator(subSections);

        JointRuptureBuilderParallel jrbp = new JointRuptureBuilderParallel(ruptures, distCalc);
        ClusterRupture splayCandidate = jrbp.makeRuptureSplay(ruptures.get(97653), 5, 3);

        if (splayCandidate != null) {
            RupCartoonGenerator.plotRupture(
                    new File("/tmp/rupcartoons/"),
                    "splays",
                    splayCandidate,
                    "splays galore",
                    false,
                    true);
            FishboneGenerator.plotAll(splayCandidate, new File("/tmp/rupcartoons/"), "fishbone4");
            //            FishboneGenerator.plotTopDownDebug(splayCandidate, new
            // File("/tmp/rupcartoons/"), "fishbone-debug");
            //            FishboneGenerator.plotFishbone(splayCandidate, new
            // File("/tmp/rupcartoons/"), "fishbone3");
        }

        FaultSection firstSection = splayCandidate.clusters[0].subSects.get(0);
        Preconditions.checkState(FaultSectionProperties.isSubduction(firstSection));

        Location a = firstSection.getFaultTrace().first();
        Location b = firstSection.getFaultTrace().last();

        addCrustalJumps(crustalClusters, distCalc, 0.5);

        ruptures =
                JointRuptureBuilderParallel.makeSplays(
                        ruptures.get(97653), crustalClusters, distCalc);

        //        List<List<Jump>> jumps = clusterJumps();
        System.out.println("done");
        //
        //
        //        for (List<Jump> jumps1 : jumps) {
        //            for (Jump jump : jumps1) {
        //                System.out.println("parent " + jump.fromSection.getParentSectionId() + "
        // section " + jump.fromSection.getSectionId() + " row " + ((DownDipFaultSection)
        // jump.fromSection).getRowIndex());
        //            }
        //        }
        //
        //        for (int i = 0; i < jumps.size(); i++) {
        //            JointRuptureBuilder jointRuptureBuilder = new JointRuptureBuilder(
        //                    jumps.get(i),
        //                    ruptures,
        //                    distCalc,
        //                    crustalMinCount,
        //                    subductionMinCount,
        //                    subductionMaxCount
        //            );
        //            jointRuptureBuilder.stats(subSections);
        //
        // ruptures.addAll(jointRuptureBuilder.build(subductionClusters.get(i).parentSectionID));
        //        }
        //
        //        int printed = 0;

        System.out.println("ruptures before filtering: " + ruptures.size());

        //        int sectionCount = 0;
        //
        //        ruptures = ruptures.stream().filter(rupture -> {
        //            int subCount = 0;
        //            for (FaultSubsectionCluster cluster : rupture.clusters) {
        //                if (cluster instanceof DownDipFaultSubSectionCluster) {
        //                    subCount++;
        //                }
        //            }
        //
        //            return subCount > 1;
        //        }).collect(Collectors.toList());
        //
        //        int maxCount =
        // ruptures.parallelStream().mapToInt(ClusterRupture::getTotalNumSects).max().getAsInt();

        //        ruptures = ruptures.stream().filter(r -> r.getTotalNumSects()
        // ==maxCount).collect(Collectors.toList());

        //            if(subCount > 1 && printed < 10) {
        //                RupCartoonGenerator.plotRupture(new File ("/tmp/rupcartoons/"), "rupture"
        // + i, rupture, "Rupture " + i, false, true);
        //                System.out.println("yes! " + rupture.clusters.length);
        //                printed++;
        //            }

        List<ClusterRupture> filteredRuptures =
                ruptures.stream()
                        .filter(
                                rupture -> {
                                    int pos = 0;
                                    for (FaultSubsectionCluster cluster : rupture.clusters) {
                                        if (cluster instanceof DownDipFaultSubSectionCluster
                                                && pos > 0
                                                && pos < rupture.clusters.length - 1) {
                                            return true;
                                        }
                                        pos++;
                                    }
                                    return false;
                                })
                        .collect(Collectors.toList());

        System.out.println("ruptures with subduction in the middle: " + filteredRuptures.size());

        if (!filteredRuptures.isEmpty()) {
            RupCartoonGenerator.plotRupture(
                    new File("/tmp/rupcartoons/"),
                    "rupture",
                    filteredRuptures.get(0),
                    "Rupture ",
                    false,
                    true);
        }
        System.out.println(" Ruptures after filtering: " + ruptures.size());

        SimplifiedScalingRelationship sr = new SimplifiedScalingRelationship();
        sr.setupCrustal(4.2, 4.2);

        System.out.println("building rupset");
        FaultSystemRupSet rupSet =
                FaultSystemRupSet.builderForClusterRups(subSections, ruptures)
                        // FaultSystemRupSet.builderForClusterRups(ruptureLoader.subSections,
                        // ruptures)
                        .rupLengths(buildLengths(ruptures))
                        .forScalingRelationship(sr)
                        //                        .slipAlongRupture(getSlipAlongRuptureModel())
                        //
                        // .addModule(getLogicTreeBranch(FaultRegime.CRUSTAL))
                        //
                        // .addModule(SectionDistanceAzimuthCalculator.archivableInstance(distAzCalc))
                        .build();
        System.out.println("writing rupset to file");
        rupSet.write(new File("/tmp/joint-splay1.zip"));

        //        NZSHM22_ReportPageGen reportPageGen = new NZSHM22_ReportPageGen();
        //        reportPageGen.setName("Combined")
        //                .setRuptureSet(rupSet)
        //                .setOutputPath("/tmp/reports/joinrupset")
        //            //    .setRuptureSet("/tmp/jointrupset.zip");
        //        ;
        //        reportPageGen.generateRupSetPage();
    }

    public static void main(String[] args) throws IOException, DocumentException {
        JointRupturePostProcessor processor = new JointRupturePostProcessor();
        processor.buildRuptureSet();
    }
}

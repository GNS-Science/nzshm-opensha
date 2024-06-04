package nz.cri.gns.NZSHM22.opensha.ruptures.experimental;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.JumpAzimuthChangeFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.TotalAzimuthChangeFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.U3CompatibleCumulativeRakeChangeFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupCartoonGenerator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.UniqueRupture;
import org.opensha.sha.faultSurface.FaultSection;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * A bunch of helper methods for filtering and combining ruptures.
 * This was originally written for finding suitable rupture combinations for exploring joint rupture algorithms.
 */
public class RuptureQueries {

    final FaultSystemRupSet rupSet;
    final List<ClusterRupture> ruptures;
    final SectionDistanceAzimuthCalculator distAzCalc;
    final List<FaultSection> subductionSections;
    final List<ClusterRupture> subductionRuptures;
    final List<ClusterRupture> crustalRuptures;


    public RuptureQueries(FaultSystemRupSet rupSet) {
        this.rupSet = rupSet;
        ClusterRuptures cRups = rupSet.getModule(ClusterRuptures.class);
        if (cRups == null) {
            // assume single stranded for our purposes here
            cRups = ClusterRuptures.singleStranged(rupSet);
        }
        this.ruptures = cRups.getAll();
        this.distAzCalc = new SectionDistanceAzimuthCalculator(rupSet.getFaultSectionDataList());
        subductionSections = rupSet.getFaultSectionDataList().stream().filter(RuptureQueries::isSubduction).collect(Collectors.toList());
        subductionRuptures = ruptures.stream().filter(RuptureQueries::isSubduction).collect(Collectors.toList());
        crustalRuptures = ruptures.stream().filter(r -> !isSubduction(r)).collect(Collectors.toList());
    }

    /**
     * Returns true if section is a subduction section.
     *
     * @param section
     * @return
     */
    public static boolean isSubduction(FaultSection section) {
        return section.getSectionName().contains("row:");
    }

    /**
     * Returns true if rupture is a single-cluster subduction rupture.
     * This is sufficient for NZSHM22 ruptures.
     *
     * @param rupture
     * @return
     */
    public static boolean isSubduction(ClusterRupture rupture) {
        return rupture.singleStrand &&
                rupture.clusters.length == 1 &&
                isSubduction(rupture.clusters[0].startSect);
    }

    /**
     * Build map from subduction section id to crustal ruptures that are within
     * maxJumpDistance of the section.
     *
     * @param subductionSections
     * @param crustalRuptures
     * @param distAzCalc
     */
    public static Map<Integer, Set<ClusterRupture>> jumpIndex(
            List<FaultSection> subductionSections,
            List<ClusterRupture> crustalRuptures,
            SectionDistanceAzimuthCalculator distAzCalc,
            double maxJumpDistance) {

        Map<Integer, Set<ClusterRupture>> subSecToRups = new ConcurrentHashMap<>();
        crustalRuptures.parallelStream().forEach(cr -> {
            List<FaultSection> crustalSecs = cr.buildOrderedSectionList();
            for (FaultSection crustSec : crustalSecs) {
                for (FaultSection subSec : subductionSections) {
                    if (distAzCalc.getDistance(subSec, crustSec) <= maxJumpDistance) {
                        subSecToRups.compute(subSec.getSectionId(), (id, rups) -> {
                            rups = rups == null ? new HashSet<>() : rups;
                            rups.add(cr);
                            return rups;
                        });
                    }
                }
            }
        });
        return subSecToRups;
    }

    /**
     * Take all jumps from a data structure that was created by jumpIndex()
     * This can for example be used to get all crustal ruptures that we can jump to from a subduction rupture.
     *
     * @param sections
     * @param subSecToRups
     * @return
     */
    public static Set<ClusterRupture> jumpTo(List<FaultSection> sections, Map<Integer, Set<ClusterRupture>> subSecToRups) {
        Set<ClusterRupture> result = new HashSet<>();
        for (FaultSection section : sections) {
            Set<ClusterRupture> ruptures = subSecToRups.get(section.getSectionId());
            if (ruptures != null) {
                result.addAll(ruptures);
            }
        }
        return result;
    }

    /**
     * Keep only ruptures that have a confined azimuth and rake change.
     *
     * @param ruptures         the ruptures to be filtered. This list will not be modified.
     * @param maxAzimuthChange maximum permissible cumulative azimuth change of each rupture
     * @param maxRakeChange    maximum permissible rake change of each rupture
     * @return a list of ruptures that conform to the maximum changes
     */
    public List<ClusterRupture> homogeniseRuptures(List<ClusterRupture> ruptures, float maxAzimuthChange, double maxRakeChange) {
        List<ClusterRupture> result = new ArrayList<>(ruptures);
        SectionDistanceAzimuthCalculator crustalDistAzCalc = new SectionDistanceAzimuthCalculator(rupSet.getFaultSectionDataList());
        JumpAzimuthChangeFilter.AzimuthCalc azimuthCalc = new JumpAzimuthChangeFilter.SimpleAzimuthCalc(crustalDistAzCalc);
        TotalAzimuthChangeFilter totAzFilter = new TotalAzimuthChangeFilter(azimuthCalc, maxAzimuthChange, true, true);
        result.removeIf(r -> !totAzFilter.apply(r, false).isPass());
        U3CompatibleCumulativeRakeChangeFilter rakeChangeFilter = new U3CompatibleCumulativeRakeChangeFilter(maxRakeChange);
        result.removeIf(r -> !rakeChangeFilter.apply(r, false).isPass());
        return result;
    }

    /**
     * Keep all ruptures in the ruptures list that are completely composed of sections that are present in the filter rupture.
     *
     * @param ruptures
     * @param filter
     * @return
     */
    public static List<ClusterRupture> keepIfSubRupture(List<ClusterRupture> ruptures, ClusterRupture filter) {
        Set<Integer> ids = filter.buildOrderedSectionList().stream().map(FaultSection::getSectionId).collect(Collectors.toSet());
        List<ClusterRupture> result = new ArrayList<>(ruptures);
        result.removeIf(cr -> cr.buildOrderedSectionList().stream().map(FaultSection::getSectionId).filter(ids::contains).count() != cr.getTotalNumSects());
        return result;
    }

    /**
     * Result object specific to the findCandidates() method
     */
    static class RuptureCombination {
        final public ClusterRupture subduction;
        final public List<FaultSection> subductionSections;
        public List<ClusterRupture> crustalRuptures;
        final public ClusterRupture longestCrustal;
        final public double maxLength;
        public long overlap = -1;

        public RuptureCombination(ClusterRupture subduction, List<FaultSection> sections, Collection<ClusterRupture> crustalRuptures, Map<Integer, Set<ClusterRupture>> subSecToRups) {
            this.subduction = subduction;
            this.subductionSections = sections;
            this.crustalRuptures = crustalRuptures.stream().sorted(Comparator.comparing(cr -> cr.buildOrderedSectionList().size())).collect(Collectors.toList());
            if (this.crustalRuptures.isEmpty()) {
                maxLength = 0;
                longestCrustal = null;
                return;
            }
            longestCrustal = this.crustalRuptures.get(this.crustalRuptures.size() - 1);
            maxLength = crustalRuptures.isEmpty() ? 0 : longestCrustal.getTotalNumSects();
            overlap = subductionSections.stream().filter(s -> {
                Set<ClusterRupture> jumps = subSecToRups.get(s.getSectionId());
                return jumps != null && jumps.contains(longestCrustal);
            }).count();
        }
    }

    /**
     * Finds suitable candidates for joint rupture algorithm exploration and creates a rupture set and a report.
     *
     * @throws IOException
     */
    public void findCandidates() throws IOException {

        Set<Integer> noWant = Set.of(2335, 2330, 2327, 2336, 2331, 2328);
        List<ClusterRupture> crustalRuptures = homogeniseRuptures(this.crustalRuptures, 10, 10);
        Map<Integer, Set<ClusterRupture>> subSecToRups = jumpIndex(subductionSections, crustalRuptures, distAzCalc, 5);

        Optional<RuptureCombination> candidate = this.subductionRuptures.
                stream().
                filter(r -> r.getTotalNumSects() > 8 && r.getTotalNumSects() < 12).
                filter(r -> r.buildOrderedSectionList().stream().noneMatch(s -> noWant.contains(s.getSectionId()))).
                filter(r -> r.buildOrderedSectionList().stream().anyMatch(s -> s.getSectionId() == 2385)).
                map(r -> {
                    List<FaultSection> sections = r.buildOrderedSectionList();
                    Set<ClusterRupture> jumps = jumpTo(sections, subSecToRups);
                    return new RuptureCombination(r, sections, jumps, subSecToRups);
                }).
                filter(rc -> !rc.crustalRuptures.isEmpty()).
                peek(rc -> rc.crustalRuptures = keepIfSubRupture(rc.crustalRuptures, rc.longestCrustal)).
                max(Comparator.comparing((RuptureCombination rc) -> rc.overlap).thenComparing((RuptureCombination rc) -> rc.maxLength));


        if (candidate.isEmpty()) {
            System.out.println("empty!");
        } else {

            String outputDir = "/tmp/catalogue";

            RuptureCombination result = candidate.get();
            result.crustalRuptures = result.crustalRuptures.
                    stream().
                    sorted(Comparator.comparing((ClusterRupture r) -> r.clusters[0].startSect.getSectionId()).thenComparing(ClusterRupture::getTotalNumSects)).
                    collect(Collectors.toList());

            Set<Integer> smallTopRuptureIds = Set.of(2394, 2385); // 2394, 2393, 2384, 2385
            Optional<ClusterRupture> smallTopRupture = this.subductionRuptures.
                    stream().
                    filter(r -> r.getTotalNumSects() == 2).
                    filter(r -> r.buildOrderedSectionList().
                            stream().
                            allMatch(s -> smallTopRuptureIds.contains(s.getSectionId()))).
                    findAny();

            Preconditions.checkState(result.subduction != null);
            Preconditions.checkState(smallTopRupture.isPresent());

            FaultSystemRupSet resultRupSet = new RuptureAccumulator().
                    setRupSet(rupSet, ruptures).
                    add(result.subduction).
                    add(smallTopRupture.get()).
                    addAll(result.crustalRuptures).
                    build();

            resultRupSet.write(new File(outputDir + "/catalogue.zip"));

//            System.out.println("candidate size subduction" + result.subduction.getTotalNumSects());
//            System.out.println("candidate count crustal " + result.crustalRuptures.size());
//            System.out.println("length " + result.maxLength);
//            System.out.println("overlap " + result.overlap);
//            System.out.println("fromStart " + result.crustalRuptures.stream().filter(r -> r.clusters[0].startSect == result.longestCrustal.clusters[0].startSect).count());

            List<String> lines = new ArrayList<>();
            lines.add("# Possible Subduction / Crustal Combinations");
            lines.add("");
            lines.add("The rupture ids in the plots work for the attached rupture set.");
            lines.add("");
            lines.add("- " + writePage(outputDir, 0, result.subduction, result.crustalRuptures, resultRupSet));
            lines.add("- " + writePage(outputDir, 1, smallTopRupture.get(), result.crustalRuptures, resultRupSet));
            lines.add("");
            lines.add("[Download the rupture set](catalogue.zip)");
            MarkdownUtils.writeHTML(lines, new File(outputDir + "/index.html"));

//            int index = 3;
//            List<String> lines = new ArrayList<>();
//            MarkdownTableHelper table = new MarkdownTableHelper(3);
//            for (ClusterRupture rupture : result.crustalRuptures) {
//                accumulator.add(rupture);
//                ClusterRupture candidateRupture = MultiClusterRupture.takeSplayJump(result.subduction, rupture);
//                RupCartoonGenerator.plotRupture(new File(outputDir + "/resources/"), "catalogue" + index, candidateRupture, "catalogue candidate" + index, false, true);
//                table.add("![entry" + index + "](resources/catalogue" + index + ".png)");
//                index++;
//            }
//
//            lines.addAll(table.build());
//            MarkdownUtils.writeHTML(lines, new File(outputDir + "/index.html"));
        }
    }

    /**
     * Specifically used by findCandidates() to write a report page for a subduction rupture
     *
     * @param outputDir
     * @param subductionIndex
     * @param subductionRupture
     * @param crustalRuptures
     * @param rupSet
     * @return
     * @throws IOException
     */
    public static String writePage(String outputDir, int subductionIndex, ClusterRupture subductionRupture, List<ClusterRupture> crustalRuptures, FaultSystemRupSet rupSet) throws IOException {
        int index = 2;
        List<String> lines = new ArrayList<>();
        lines.add(" # Combinations For Subduction Rupture " + subductionIndex);
        lines.add("");
        lines.add("Rupture " + subductionIndex + " stats");
        lines.add("- length " + rupSet.getLengthForRup(subductionIndex));
        lines.add("- area " + rupSet.getAreaForRup(subductionIndex));
        lines.add("- rake " + rupSet.getAveRakeForRup(subductionIndex));
        lines.add("- magnitude " + rupSet.getMagForRup(subductionIndex));
        lines.add("");
        MarkdownTableHelper table = new MarkdownTableHelper(3);
        DecimalFormat df = new DecimalFormat("#.##");
        DecimalFormat km = new DecimalFormat("#.#km");
        for (ClusterRupture rupture : crustalRuptures) {
            ClusterRupture candidateRupture = MultiClusterRupture.takeSplayJump(subductionRupture, rupture);
            String fileName = "catalogue_" + subductionIndex + "_" + index;
            RupCartoonGenerator.plotRupture(new File(outputDir + "/resources/"), fileName, candidateRupture, "Subduction " + subductionIndex + " Crustal " + index, false, true);
            table.add("![entry](resources/" + fileName + ".png) Rupture " + index + " stats:  length: " + km.format(rupSet.getLengthForRup(index) / 1000) + " rake: " + df.format(rupSet.getAveRakeForRup(index)) + " mag: " + df.format(rupSet.getMagForRup(index)));
            index++;
        }

        lines.addAll(table.build());
        String htmlFileName = "catalogue_" + subductionIndex + ".html";
        MarkdownUtils.writeHTML(lines, new File(outputDir + "/" + htmlFileName));
        return "[Combinations For Subduction Rupture " + subductionIndex + "](" + htmlFileName + ")";
    }

    public static void main(String[] args) throws IOException {
        RuptureQueries queries = new RuptureQueries(FaultSystemRupSet.load(new File("C:\\Users\\user\\Dropbox\\RupSet_mergedNZSHM22_Crustal_Hikurangi.zip")));
        queries.findCandidates();
    }

    /**
     * Markdown table that automatically wraps after a specified number of columns.
     */
    public static class MarkdownTableHelper {
        final MarkdownUtils.TableBuilder table;
        int entryCount = 0;
        final int colCount;

        public MarkdownTableHelper(int columns) {
            this.table = MarkdownUtils.tableBuilder();
            this.colCount = columns;
            this.table.initNewLine();
        }

        public void add(String cell) {
            table.addColumn(cell);
            entryCount++;
            if (entryCount % colCount == 0) {
                table.initNewLine();
            }
        }

        public List<String> build() {
            return table.build();
        }
    }

    /**
     * A rupture that combines two ruptures.
     * Takes the jump at the start sections of each rupture, assuming that it does not matter.
     */
    public static class MultiClusterRupture extends ClusterRupture {

        protected MultiClusterRupture(FaultSubsectionCluster[] clusters,
                                      ImmutableList<Jump> internalJumps,
                                      ImmutableMap<Jump, ClusterRupture> splays,
                                      UniqueRupture unique,
                                      UniqueRupture internalUnique) {
            super(clusters, internalJumps, splays, unique, internalUnique, false);
        }

        public static ClusterRupture takeSplayJump(ClusterRupture fromRupture, ClusterRupture toRupture) {
            ImmutableMap.Builder<Jump, ClusterRupture> splayBuilder = ImmutableMap.builder();
            splayBuilder.putAll(fromRupture.splays);
            splayBuilder.put(new Jump(fromRupture.clusters[0].startSect, fromRupture.clusters[0], toRupture.clusters[0].startSect, toRupture.clusters[0], 5), toRupture);
            ImmutableMap<Jump, ClusterRupture> newSplays = splayBuilder.build();

            UniqueRupture newUnique = UniqueRupture.add(fromRupture.unique, toRupture.unique);
            return new MultiClusterRupture(
                    fromRupture.clusters,
                    fromRupture.internalJumps,
                    newSplays,
                    newUnique,
                    fromRupture.internalUnique);
        }
    }

}

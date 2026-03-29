package nz.cri.gns.NZSHM22.opensha.inversion.joint.reporting;

import java.io.File;
import java.io.IOException;
import java.util.*;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionPredicate;
import nz.cri.gns.NZSHM22.opensha.ruptures.FaultSectionProperties;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractRupSetPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.faultSurface.FaultSection;

/** Renders a markdown table summarizing section and rupture counts per partition. */
public class PartitionSummaryTable extends AbstractRupSetPlot {

    /** Per-partition statistics computed from a rupture set. */
    protected static class PartitionStats {
        /** Section counts keyed by partition. */
        public final Map<PartitionPredicate, Integer> sectionCounts;

        /** Ruptures belonging to exactly one partition. */
        public final Map<PartitionPredicate, Integer> exclusiveCounts;

        /** Ruptures shared across multiple partitions, counted per partition. */
        public final Map<PartitionPredicate, Integer> sharedCounts;

        /** Total number of ruptures spanning more than one partition. */
        public final int multiPartitionTotal;

        /** All partitions that appear in any of the count maps. */
        public final Set<PartitionPredicate> allPartitions;

        protected PartitionStats(
                Map<PartitionPredicate, Integer> sectionCounts,
                Map<PartitionPredicate, Integer> exclusiveCounts,
                Map<PartitionPredicate, Integer> sharedCounts,
                int multiPartitionTotal) {
            this.sectionCounts = sectionCounts;
            this.exclusiveCounts = exclusiveCounts;
            this.sharedCounts = sharedCounts;
            this.multiPartitionTotal = multiPartitionTotal;
            this.allPartitions = EnumSet.noneOf(PartitionPredicate.class);
            this.allPartitions.addAll(sectionCounts.keySet());
            this.allPartitions.addAll(exclusiveCounts.keySet());
            this.allPartitions.addAll(sharedCounts.keySet());
        }
    }

    /**
     * Computes per-partition statistics from the given rupture set.
     *
     * @param rupSet the rupture set to analyse
     * @return partition statistics
     */
    protected static PartitionStats computeStats(FaultSystemRupSet rupSet) {
        Map<PartitionPredicate, Integer> sectionCounts = new EnumMap<>(PartitionPredicate.class);
        for (FaultSection section : rupSet.getFaultSectionDataList()) {
            PartitionPredicate partition = FaultSectionProperties.getPartition(section);
            if (partition != null) {
                sectionCounts.merge(partition, 1, Integer::sum);
            }
        }

        Map<PartitionPredicate, Integer> exclusiveCounts = new EnumMap<>(PartitionPredicate.class);
        Map<PartitionPredicate, Integer> sharedCounts = new EnumMap<>(PartitionPredicate.class);
        int multiPartitionTotal = 0;

        for (int r = 0; r < rupSet.getNumRuptures(); r++) {
            Set<PartitionPredicate> partitions = EnumSet.noneOf(PartitionPredicate.class);
            for (int secIdx : rupSet.getSectionsIndicesForRup(r)) {
                PartitionPredicate p =
                        FaultSectionProperties.getPartition(rupSet.getFaultSectionData(secIdx));
                if (p != null) {
                    partitions.add(p);
                }
            }

            if (partitions.size() == 1) {
                PartitionPredicate sole = partitions.iterator().next();
                exclusiveCounts.merge(sole, 1, Integer::sum);
            } else if (partitions.size() > 1) {
                multiPartitionTotal++;
                for (PartitionPredicate p : partitions) {
                    sharedCounts.merge(p, 1, Integer::sum);
                }
            }
        }

        return new PartitionStats(
                sectionCounts, exclusiveCounts, sharedCounts, multiPartitionTotal);
    }

    @Override
    public String getName() {
        return "Ruptures Summary";
    }

    @Override
    public List<String> plot(
            FaultSystemRupSet rupSet,
            FaultSystemSolution sol,
            ReportMetadata meta,
            File resourcesDir,
            String relPathToResources,
            String topLink)
            throws IOException {

        PartitionStats primary = computeStats(rupSet);
        boolean hasComparison = meta != null && meta.hasComparison();
        PartitionStats comp = hasComparison ? computeStats(meta.comparison.rupSet) : null;

        Set<PartitionPredicate> allPartitions = EnumSet.noneOf(PartitionPredicate.class);
        allPartitions.addAll(primary.allPartitions);
        if (comp != null) {
            allPartitions.addAll(comp.allPartitions);
        }

        List<String> lines = new ArrayList<>();

        if (comp != null) {
            String pName = meta.primary.name;
            String cName = meta.comparison.name;
            lines.add(
                    String.format(
                            "| Partition "
                                    + " | Exclusive (%s) | Exclusive (%s)"
                                    + " | Joint (%s) | Joint (%s) |",
                            pName, cName, pName, cName));
            lines.add("|---|---|---|---|---|---|---|");
            for (PartitionPredicate p : allPartitions) {
                lines.add(
                        String.format(
                                "| %s | %,d | %,d | %,d | %,d |",
                                p.name(),
                                primary.exclusiveCounts.getOrDefault(p, 0),
                                comp.exclusiveCounts.getOrDefault(p, 0),
                                primary.sharedCounts.getOrDefault(p, 0),
                                comp.sharedCounts.getOrDefault(p, 0)));
            }
            int pExclTotal = 0, cExclTotal = 0;
            for (PartitionPredicate p : allPartitions) {
                pExclTotal += primary.exclusiveCounts.getOrDefault(p, 0);
                cExclTotal += comp.exclusiveCounts.getOrDefault(p, 0);
            }
            lines.add(
                    String.format(
                            "| **Total** | **%,d** | **%,d** | **%,d** | **%,d** |",
                            pExclTotal,
                            cExclTotal,
                            primary.multiPartitionTotal,
                            comp.multiPartitionTotal));
        } else {
            lines.add("| Partition | Exclusive Ruptures | Joint Ruptures |");
            lines.add("|-----------|--------------------|----------------|");
            for (PartitionPredicate p : allPartitions) {
                lines.add(
                        String.format(
                                "| %s | %,d | %,d |",
                                p.name(),
                                primary.exclusiveCounts.getOrDefault(p, 0),
                                primary.sharedCounts.getOrDefault(p, 0)));
            }
            int exclTotal = 0;
            for (PartitionPredicate p : allPartitions) {
                exclTotal += primary.exclusiveCounts.getOrDefault(p, 0);
            }
            lines.add(
                    String.format(
                            "| **Total** | **%,d** | **%,d** |",
                            exclTotal, primary.multiPartitionTotal));
        }

        return lines;
    }

    @Override
    public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
        return null;
    }
}

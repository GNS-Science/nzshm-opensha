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

public class PartitionSummaryTable extends AbstractRupSetPlot {

    @Override
    public String getName() {
        return "Partition Summary";
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

        // Count sections per partition
        Map<PartitionPredicate, Integer> sectionCounts = new EnumMap<>(PartitionPredicate.class);
        for (FaultSection section : rupSet.getFaultSectionDataList()) {
            PartitionPredicate partition = FaultSectionProperties.getPartition(section);
            if (partition != null) {
                sectionCounts.merge(partition, 1, Integer::sum);
            }
        }

        // Classify ruptures
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

        // Collect all partitions that appear, in enum order
        Set<PartitionPredicate> allPartitions = EnumSet.noneOf(PartitionPredicate.class);
        allPartitions.addAll(sectionCounts.keySet());
        allPartitions.addAll(exclusiveCounts.keySet());
        allPartitions.addAll(sharedCounts.keySet());

        List<String> lines = new ArrayList<>();
        lines.add("| Partition | Sections | Exclusive Ruptures | Shared Ruptures |");
        lines.add("|-----------|----------|-------------------|-----------------|");
        for (PartitionPredicate p : allPartitions) {
            lines.add(
                    String.format(
                            "| %s | %,d | %,d | %,d |",
                            p.name(),
                            sectionCounts.getOrDefault(p, 0),
                            exclusiveCounts.getOrDefault(p, 0),
                            sharedCounts.getOrDefault(p, 0)));
        }
        lines.add(
                String.format(
                        "| **Multi-partition ruptures** | | | **%,d** |", multiPartitionTotal));

        return lines;
    }

    @Override
    public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
        return null;
    }
}

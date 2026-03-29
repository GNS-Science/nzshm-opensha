package nz.cri.gns.NZSHM22.opensha.scripts;

import java.io.File;
import java.util.*;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionPredicate;
import nz.cri.gns.NZSHM22.opensha.ruptures.FaultSectionProperties;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.RupHistogramPlots.RakeType;

/**
 * One-off script that loads a rupture set, groups ruptures by their partition combination, and
 * prints the proportion of each rake type per group.
 */
public class RuptureRakeBreakdown {

    /** Entry point. Accepts an optional path to a rupture set zip file. */
    public static void main(String[] args) throws Exception {
        String defaultPath =
                "C:\\runs\\run_1\\rupset\\mergedRupset_15km_cffPatch2km_cff0SelfStiffness.zip";
        String path = args.length > 0 ? args[0] : defaultPath;

        System.out.println("Loading rupture set: " + path);
        FaultSystemRupSet rupSet = FaultSystemRupSet.load(new File(path));
        System.out.printf(
                "Loaded %,d ruptures, %,d sections%n%n",
                rupSet.getNumRuptures(), rupSet.getNumSections());

        // Group: sorted partition set -> (RakeType -> count)
        Map<List<PartitionPredicate>, Map<RakeType, Integer>> groups =
                new TreeMap<>(Comparator.comparing(Object::toString));

        for (int r = 0; r < rupSet.getNumRuptures(); r++) {
            Set<PartitionPredicate> partitions = EnumSet.noneOf(PartitionPredicate.class);
            for (int secIdx : rupSet.getSectionsIndicesForRup(r)) {
                PartitionPredicate p =
                        FaultSectionProperties.getPartition(rupSet.getFaultSectionData(secIdx));
                if (p != null) {
                    partitions.add(p);
                }
            }

            List<PartitionPredicate> key = new ArrayList<>(partitions);
            Collections.sort(key);

            double rake = rupSet.getAveRakeForRup(r);
            RakeType rakeType = RakeType.getType(rake);

            groups.computeIfAbsent(key, k -> new EnumMap<>(RakeType.class))
                    .merge(rakeType, 1, Integer::sum);
        }

        for (Map.Entry<List<PartitionPredicate>, Map<RakeType, Integer>> entry :
                groups.entrySet()) {
            Map<RakeType, Integer> rakeCounts = entry.getValue();
            int total = rakeCounts.values().stream().mapToInt(Integer::intValue).sum();

            System.out.printf("Partition Group: %s (%,d ruptures)%n", entry.getKey(), total);
            for (RakeType rt : RakeType.values()) {
                int count = rakeCounts.getOrDefault(rt, 0);
                if (count == 0) continue;
                double pct = 100.0 * count / total;
                System.out.printf("  %-15s %5.1f%% (%,d)%n", rt.name() + ":", pct, count);
            }
            System.out.println();
        }
    }
}

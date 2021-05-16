package nz.cri.gns.NZSHM22.opensha.ruptures;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.faultSurface.FaultSection;

import nz.cri.gns.NZSHM22.opensha.ruptures.downDip.DownDipRegistry;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * A helper class to filter ruptures.
 */
public class RuptureThinning {

    /**
     * Convenience function to filter ruptures. Does not modify the original list.
     * @param ruptures a list of generated ruptures
     * @param predicate a rupture predicate that indicates which ruptures to keep
     * @return a new list of the filtered ruptures
     */
    public static List<ClusterRupture> filterRuptures(List<ClusterRupture> ruptures, Predicate<ClusterRupture> predicate) {
        return ruptures.stream().filter(predicate).collect(Collectors.toList());
    }

    /**
     * Returns a predicate that takes a ClusterRupture and returns true iff the rupture is on a downdip fault.
     * @param registry
     * @return
     */
    public static Predicate<ClusterRupture> downDipPredicate(DownDipRegistry registry){
        return rupture -> registry.isDownDip(rupture.clusters[0]);
    }

    /**
     * Returns a predicate that takes a ClusterRupture and returns true iff the rupture's size (in sections) is
     * acceptable.
     * @param scalar determines the distance between acceptable sizes
     * @return a predicate
     */
    public static Predicate<ClusterRupture> coarsenessPredicate(double scalar) {
        Set<Integer> valid = new HashSet<>();
        int last = 0;
        while (last < 1000) {
            last = (int) Math.max(1, Math.round(last * scalar )) + last;
            valid.add(last);
        }
        return rupture -> valid.contains(rupture.getTotalNumSects());
    }

    /**
     * Returns a predicate that takes a ClusterRupture and returns true iff the rupture goes from the start
     * of a fault to the end of a fault.
     * @param connectionStrategy the ClusterConnectionStrategy that was used when generating the ruptures
     * @return the predicate
     */
    public static Predicate<ClusterRupture> endToEndPredicate(ClusterConnectionStrategy connectionStrategy) {
        HashMap<Integer, FaultSubsectionCluster> fullClusters = new HashMap<>();
        for (FaultSubsectionCluster cluster : connectionStrategy.getClusters()) {
            fullClusters.put(cluster.parentSectionID, cluster);
        }
        return rupture -> isEndToEndRupture(fullClusters, rupture);
    }

    static boolean isFaultEnd(HashMap<Integer, FaultSubsectionCluster> fullClusters, FaultSection section) {
        FaultSubsectionCluster parent = fullClusters.get(section.getParentSectionId());
        return parent.startSect == section || parent.subSects.get(parent.subSects.size() - 1) == section;
    }

    static boolean isEndToEndRupture(HashMap<Integer, FaultSubsectionCluster> fullClusters, ClusterRupture rupture) {
        if (fullClusters != null) {
            List<FaultSection> sections = rupture.buildOrderedSectionList();
            return isFaultEnd(fullClusters, sections.get(0)) && isFaultEnd(fullClusters, sections.get(sections.size() - 1));
        } else {
            return false;
        }
    }
}

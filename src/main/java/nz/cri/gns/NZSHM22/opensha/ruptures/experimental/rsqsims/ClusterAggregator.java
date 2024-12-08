package nz.cri.gns.NZSHM22.opensha.ruptures.experimental.rsqsims;

import com.google.common.base.Preconditions;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ClusterAggregator {

    final SectionDistanceAzimuthCalculator disAzCalc;

    final double maxInternalJumpDist;

    public ClusterAggregator(SectionDistanceAzimuthCalculator disAzCalc, double maxInternalJumpDist) {
        this.disAzCalc = disAzCalc;
        this.maxInternalJumpDist = maxInternalJumpDist;
    }

    /**
     * Turns an Event into two ruptures, one subduction, one crustal
     * @param event
     * @return
     */
    public List<ClusterRupture> makeRuptures(RsqSimEventLoader.Event event) {
        List<FaultSection> subductionSections = event.sections.stream().filter(s->s.getSectionName().contains("row:")).collect(Collectors.toList());
        List<FaultSection> crustalSections = event.sections.stream().filter(s->!s.getSectionName().contains("row:")).collect(Collectors.toList());
        Preconditions.checkState(!subductionSections.isEmpty());
        Preconditions.checkState(!crustalSections.isEmpty());
        List<ClusterRupture> ruptures = new ArrayList<>();
        ruptures.add(ClusterRupture.forOrderedSingleStrandRupture(subductionSections, disAzCalc));
        ruptures.add(ClusterRupture.forOrderedSingleStrandRupture(crustalSections, disAzCalc));
        return ruptures;
    }

    class ClusterData {
        FaultSubsectionCluster cluster;
        List<FaultSection> endPoints = new ArrayList<>();
        public boolean connected = false;

        ClusterData(FaultSubsectionCluster cluster) {
            this.cluster = cluster;
            endPoints.add(cluster.subSects.get(0));
            endPoints.add(cluster.subSects.get(cluster.subSects.size() - 1));
        }

        boolean isNear(FaultSection section) {
            for (FaultSection candidate : endPoints) {
                if (section == candidate || disAzCalc.getDistance(candidate, section) <= maxInternalJumpDist) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Returns true if all clusters can transitively be connected through maxInternalJumpDist jumps.
     * @param clusters
     * @return
     */
    public boolean allConnected(FaultSubsectionCluster[] clusters) {
        if (clusters.length == 1) {
            return true;
        }

        // wrap clusters in ClusterData
        List<ClusterData> groups = Arrays.stream(clusters).map(ClusterData::new).collect(Collectors.toList());

        ClusterData first = groups.get(0);
        List<FaultSection> edge = new ArrayList<>(first.endPoints);
        first.connected = true;

        // Go through all fault sections at the edge of the connected cluster.
        // The edge may grow as we add more clusters into the connected cluster.
        // See if we can jump from the selected fault section to a cluster that's so far unconnected.
        // If so, add the cluster to the connected cluster, and expand the edge accordingly
        for (int e = 0; e < edge.size(); e++) {
            FaultSection section = edge.get(e);
            for (ClusterData cluster : groups) {
                if (cluster.connected) {
                    continue;
                }
                if (cluster.isNear(section)) {
                    cluster.connected = true;
                    edge.addAll(cluster.endPoints);
                }
            }
        }

        // return true if all clusters are connected
        for (ClusterData data : groups) {
            if (!data.connected) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true if all clusters in the rupture can be connected by maxInternalJumpDist jumps
     * @param rupture
     * @return
     */
    public boolean allConnected(ClusterRupture rupture) {
        return allConnected(rupture.clusters);
    }

    /**
     * Returns true if all clusters in each rupture can be connected by maxInternalJumpDist jumps
     * Assumes that exactly two ruptures are passed in.
     * @param ruptures
     * @return
     */
    public boolean allConnected(List<ClusterRupture> ruptures) {
        return allConnected(ruptures.get(0)) && allConnected(ruptures.get(1));
    }
}

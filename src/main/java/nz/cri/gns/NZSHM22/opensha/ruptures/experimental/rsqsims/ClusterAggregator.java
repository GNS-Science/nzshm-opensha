package nz.cri.gns.NZSHM22.opensha.ruptures.experimental.rsqsims;

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

    public boolean allConnected(FaultSubsectionCluster[] clusters) {
        if (clusters.length == 1) {
            return true;
        }
        List<ClusterData> groups = Arrays.stream(clusters).map(ClusterData::new).collect(Collectors.toList());

        ClusterData first = groups.get(0);
        List<FaultSection> edge = new ArrayList<>(first.endPoints);
        first.connected = true;

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

        for (ClusterData data : groups) {
            if (!data.connected) {
                return false;
            }
        }
        return true;
    }

    public boolean allConnected(ClusterRupture rupture) {
        return allConnected(rupture.clusters);
    }

    public boolean allConnected(List<ClusterRupture> ruptures) {
        return allConnected(ruptures.get(0)) && allConnected(ruptures.get(1));
    }
}

package nz.cri.gns.NZSHM22.opensha.ruptures.experimental;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import nz.cri.gns.NZSHM22.opensha.ruptures.DownDipFaultSection;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityResult;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.UniqueRupture;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Wraps a PlausibilityFilter so that the filter skips downDip clusters but is applied to all crustal clusters.
 * <p>
 * Used in MixedRuptureSetBuilder
 */
public class DownDipSkipPlausibilityFilter implements PlausibilityFilter {

    final PlausibilityFilter filter;

    public DownDipSkipPlausibilityFilter(PlausibilityFilter filter) {
        this.filter = filter;
    }

    protected PlausibilityResult apply(ClusterRupture parentRupture, List<FaultSubsectionCluster> rupture, boolean verbose) {
        if (rupture.isEmpty()) {
            return PlausibilityResult.PASS;
        }
        ClusterRupture clusterRupture = new PartialClusterRupture(parentRupture, rupture);
        return filter.apply(clusterRupture, verbose);
    }

    @Override
    public PlausibilityResult apply(ClusterRupture rupture, boolean verbose) {
        if (Arrays.stream(rupture.clusters).anyMatch(c -> c.startSect instanceof DownDipFaultSection)) {
            List<FaultSubsectionCluster> crustalRupture = new ArrayList<>();
            for (FaultSubsectionCluster cluster : rupture.clusters) {
                if (cluster.startSect instanceof DownDipFaultSection) {
                    PlausibilityResult result = apply(rupture, crustalRupture, verbose);
                    if (!result.canContinue()) {
                        return result;
                    }
                    crustalRupture = new ArrayList<>();
                } else {
                    crustalRupture.add(cluster);
                }
            }
            return apply(rupture, crustalRupture, verbose);
        }
        return filter.apply(rupture, verbose);
    }

    static class PartialClusterRupture extends ClusterRupture {

        static ImmutableList<Jump> makeInternalJumps(ClusterRupture rupture, List<FaultSubsectionCluster> clusters) {
            return ImmutableList.copyOf(
                    rupture.internalJumps.stream().filter(
                                    j -> clusters.contains(j.fromCluster) && clusters.contains(j.toCluster)).
                            collect(Collectors.toList()));
        }

        public PartialClusterRupture(ClusterRupture rupture, List<FaultSubsectionCluster> clusters) {
            super(clusters.toArray(new FaultSubsectionCluster[0]), makeInternalJumps(rupture, clusters), ImmutableMap.of(), UniqueRupture.forClusters(clusters.toArray(new FaultSubsectionCluster[0])), UniqueRupture.forClusters(clusters.toArray(new FaultSubsectionCluster[0])), true);
        }
    }

    @Override
    public String getShortName() {
        return "DownDipSkipPlausibilityFilter";
    }

    @Override
    public String getName() {
        return "DownDipSkipPlausibilityFilter";
    }
}

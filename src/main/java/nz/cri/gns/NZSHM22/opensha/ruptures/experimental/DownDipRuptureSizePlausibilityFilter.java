package nz.cri.gns.NZSHM22.opensha.ruptures.experimental;

import nz.cri.gns.NZSHM22.opensha.ruptures.FaultSectionProperties;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityResult;

/**
 * Used in MixedRuptureSetBuilder. Ensures that jumps can only go to and from the top row of a
 * subduction cluster.
 *
 * <p>USed to restrict joint ruptures are at least a certain size. Should probably only be kept for
 * the duration of the experimental joint rupture phase.
 */
public class DownDipRuptureSizePlausibilityFilter implements PlausibilityFilter {
    @Override
    public PlausibilityResult apply(ClusterRupture rupture, boolean verbose) {
        double crustalSize = 0;
        double downDipSize = 0;
        for (FaultSubsectionCluster cluster : rupture.clusters) {
            if (FaultSectionProperties.isCrustal(cluster.startSect)) {
                crustalSize += cluster.subSects.size();
            } else {
                downDipSize += cluster.subSects.size();
            }
        }
        if (crustalSize > 0 && downDipSize > 0 && (crustalSize < 25 || downDipSize < 600)) {
            return PlausibilityResult.FAIL_HARD_STOP;
        }
        return PlausibilityResult.PASS;
    }

    @Override
    public String getShortName() {
        return "DownDipRuptureSizePlausibilityFilter";
    }

    @Override
    public String getName() {
        return "DownDipRuptureSizePlausibilityFilter";
    }
}

package nz.cri.gns.NZSHM22.opensha.ruptures.experimental;

import nz.cri.gns.NZSHM22.opensha.ruptures.DownDipFaultSection;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.PlausibleClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Drop-in replacement for PlausibleClusterConnectionStrategy that is more efficient when subduction faults are
 * present.
 * PlausibleClusterConnectionStrategy will vet a possible jump by looking at permutations on the target fault that
 * do not make sense for subduction faults and are magnitudes more expensive at the same time. These permutations
 * do not take into account the DownDipPermutationStrategy and any of its filters.
 * This implementation intercepts building possible connections with subduction faults and will simply create the
 * shortest jump between the two clusters (if within maxJumpDist) without doing any further vetting.
 */

public class MixedPlausibleClusterConnectionStrategy extends PlausibleClusterConnectionStrategy {

    final SectionDistanceAzimuthCalculator distCalc;

    public MixedPlausibleClusterConnectionStrategy(List<? extends FaultSection> subSects,
                                                   SectionDistanceAzimuthCalculator distCalc, double maxJumpDist, JumpSelector selector,
                                                   List<PlausibilityFilter> filters) {
        super(subSects, distCalc, maxJumpDist, selector, filters);
        this.distCalc = distCalc;
    }

    Map<String, List<Jump>> jumpTypeHistogram = new ConcurrentHashMap<>();

    /**
     * Debugging helper to inspect the generated jumps.
     *
     * @param jump
     */
    void countJumpType(Jump jump) {
        String jumpType = "";
        jumpType += jump.fromSection.getParentSectionId() == 10000 ? "sub" : "cru";
        jumpType += jump.toSection.getParentSectionId() == 10000 ? "sub" : "cru";
        jumpTypeHistogram.compute(jumpType, (k, v) ->
        {
            if (v == null) {
                v = new ArrayList<>();
            }
            v.add(jump);
            return v;
        });

    }

    @Override
    protected List<Jump> buildPossibleConnections(FaultSubsectionCluster from, FaultSubsectionCluster to) {
        List<Jump> result;
        if (from.startSect instanceof DownDipFaultSection || to.startSect instanceof DownDipFaultSection) {
            // find the shortest jump between the two clusters
            result = new ArrayList<>();
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
            if (dist <= getMaxJumpDist()) {
                result.add(new Jump(a, from, b, to, dist));
            }
        } else {
            result = super.buildPossibleConnections(from, to);
        }

        // for debugging
        if (result != null) {
            for (Jump j : result) {
                countJumpType(j);
            }
        }
        return result;
    }
}

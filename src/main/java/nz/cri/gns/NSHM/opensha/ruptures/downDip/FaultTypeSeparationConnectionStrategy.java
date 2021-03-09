package nz.cri.gns.NSHM.opensha.ruptures.downDip;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.DistCutoffClosestSectClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;

import java.util.List;

/**
 * Like DistCutoffClosestSectClusterConnectionStrategy but ensures that we don not create jumps from or to
 * downdip faults.
 */
public class FaultTypeSeparationConnectionStrategy extends DistCutoffClosestSectClusterConnectionStrategy {

    DownDipRegistry registry;

    /**
     * Creates a new FaultTypeSeparationConnectionStrategy
     * @param registry the downdip registry
     * @param subSects a list of all faultsections
     * @param distCalc the distance calculator
     * @param maxJumpDist the maximum jump distance in km
     */
    public FaultTypeSeparationConnectionStrategy(DownDipRegistry registry, List<? extends FaultSection> subSects,
                                                 SectionDistanceAzimuthCalculator distCalc, double maxJumpDist) {
        super(subSects, distCalc, maxJumpDist);
        this.registry = registry;
    }

    @Override
    protected List<Jump> buildPossibleConnections(FaultSubsectionCluster from, FaultSubsectionCluster to) {
        if (registry.isDownDip(from) || registry.isDownDip(to)) {
            return null;
        } else {
            return super.buildPossibleConnections(from, to);
        }
    }
}
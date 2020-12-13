package nz.cri.gns.NSHM.opensha.ruptures.downDip;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.DistCutoffClosestSectClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;

import java.util.List;

public class FaultTypeSeparationConnectionStrategy extends DistCutoffClosestSectClusterConnectionStrategy {

    DownDipRegistry registry;

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
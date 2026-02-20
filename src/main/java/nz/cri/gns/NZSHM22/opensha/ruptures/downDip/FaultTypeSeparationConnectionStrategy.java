package nz.cri.gns.NZSHM22.opensha.ruptures.downDip;

import java.util.List;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionPredicate;
import nz.cri.gns.NZSHM22.opensha.ruptures.FaultSectionProperties;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.DistCutoffClosestSectClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;

/**
 * Like DistCutoffClosestSectClusterConnectionStrategy but ensures that we do not create jumps from
 * or to downdip faults.
 */
public class FaultTypeSeparationConnectionStrategy
        extends DistCutoffClosestSectClusterConnectionStrategy {

    /**
     * Creates a new FaultTypeSeparationConnectionStrategy
     *
     * @param subSects a list of all faultsections
     * @param distCalc the distance calculator
     * @param maxJumpDist the maximum jump distance in km
     */
    public FaultTypeSeparationConnectionStrategy(
            List<? extends FaultSection> subSects,
            SectionDistanceAzimuthCalculator distCalc,
            double maxJumpDist) {
        super(subSects, distCalc, maxJumpDist);
    }

    @Override
    protected List<Jump> buildPossibleConnections(
            FaultSubsectionCluster from, FaultSubsectionCluster to) {
        if (new FaultSectionProperties(from.startSect).getPartition() == PartitionPredicate.CRUSTAL
                && new FaultSectionProperties(to.startSect).getPartition()
                        == PartitionPredicate.CRUSTAL) {
            return super.buildPossibleConnections(from, to);
        }
        return null;
    }
}

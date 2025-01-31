package nz.cri.gns.NZSHM22.opensha.ruptures.experimental;

import com.google.common.base.Preconditions;
import java.util.List;
import nz.cri.gns.NZSHM22.opensha.ruptures.DownDipFaultSection;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.RuptureGrowingStrategy;
import org.opensha.sha.faultSurface.FaultSection;

/**
 * RuptureGrowingStrategy that can be used to apply separate growing strategies to crustal and
 * subduction clusters.
 */
public class MixedGrowingStrategy implements RuptureGrowingStrategy {

    protected RuptureGrowingStrategy crustalStrategy;

    protected RuptureGrowingStrategy downdipStrategy;

    @Override
    public String getName() {
        return "MixedGrowingStrategy";
    }

    /**
     * Creates a new MixedGrowingStrategy that grows ruptures using the specified strategies.
     *
     * @param crustalStrategy when this strategy is passed a crustal cluster, this growing strategy
     *     is used.
     * @param downdipStrategy when this strategy is passed a subduction cluster, this growing
     *     strategy is used.
     */
    public MixedGrowingStrategy(
            RuptureGrowingStrategy crustalStrategy, RuptureGrowingStrategy downdipStrategy) {
        this.crustalStrategy = crustalStrategy;
        this.downdipStrategy = downdipStrategy;
    }

    @Override
    public List<FaultSubsectionCluster> getVariations(
            FaultSubsectionCluster fullCluster, FaultSection firstSection) {
        int myInd = fullCluster.subSects.indexOf(firstSection);
        Preconditions.checkState(myInd >= 0, "first section not found in cluster");

        if (firstSection instanceof DownDipFaultSection) {
            Preconditions.checkState(
                    fullCluster.subSects.stream()
                            .allMatch(section -> section instanceof DownDipFaultSection),
                    "all sections are downdip sections");
            return downdipStrategy.getVariations(fullCluster, firstSection);
        }

        Preconditions.checkState(
                fullCluster.subSects.stream()
                        .noneMatch(section -> section instanceof DownDipFaultSection),
                "no sections are downdip sections");
        return crustalStrategy.getVariations(fullCluster, firstSection);
    }
}

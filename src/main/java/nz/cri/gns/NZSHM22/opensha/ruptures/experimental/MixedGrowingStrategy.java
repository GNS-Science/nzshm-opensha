package nz.cri.gns.NZSHM22.opensha.ruptures.experimental;

import com.google.common.base.Preconditions;
import nz.cri.gns.NZSHM22.opensha.ruptures.DownDipFaultSection;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.RuptureGrowingStrategy;
import org.opensha.sha.faultSurface.FaultSection;

import java.util.List;

public class MixedGrowingStrategy implements RuptureGrowingStrategy {

    protected RuptureGrowingStrategy crustalStrategy;

    protected RuptureGrowingStrategy downdipStrategy;

    @Override
    public String getName() {
        return "MixedGrowingStrategy";
    }


    public MixedGrowingStrategy(RuptureGrowingStrategy crustalStrategy, RuptureGrowingStrategy downdipStrategy){
        this.crustalStrategy = crustalStrategy;
        this.downdipStrategy = downdipStrategy;
    }
    @Override
    public List<FaultSubsectionCluster> getVariations(FaultSubsectionCluster fullCluster, FaultSection firstSection) {
        List<FaultSection> clusterSects = fullCluster.subSects;
        int myInd = fullCluster.subSects.indexOf(firstSection);
        Preconditions.checkState(myInd >= 0, "first section not found in cluster");

        if (firstSection instanceof DownDipFaultSection) {
            Preconditions.checkState(
                    fullCluster.subSects.stream().allMatch(section -> section instanceof DownDipFaultSection),
                    "all sections are downdip sections");
            return downdipStrategy.getVariations(fullCluster, firstSection);
        }

        Preconditions.checkState(
                fullCluster.subSects.stream().noneMatch(section -> section instanceof DownDipFaultSection),
                "no sections are downdip sections");
        return crustalStrategy.getVariations(fullCluster, firstSection);
    }
}

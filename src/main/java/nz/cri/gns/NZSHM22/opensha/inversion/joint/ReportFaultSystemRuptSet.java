package nz.cri.gns.NZSHM22.opensha.inversion.joint;

import com.google.common.base.Preconditions;
import java.util.List;
import java.util.function.IntPredicate;
import nz.cri.gns.NZSHM22.opensha.data.region.NewZealandRegions;
import org.opensha.commons.geo.Region;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;

public class ReportFaultSystemRuptSet extends FaultSystemRupSet {

    double[] fractRupsInsidePartition;

    public ReportFaultSystemRuptSet(FaultSystemRupSet rupSet) {
        init(rupSet);
    }

    protected void synthesizeTargetMFDs() {
        PartitionMfds partitionMfds = getModule(PartitionMfds.class);
    }

    /**
     * TODO: basically copy of PartitionFaultSystemRupSet.getFractRupsInsideRegion
     *
     * @param region
     * @param traceOnly
     * @return
     */
    @Override
    public double[] getFractRupsInsideRegion(Region region, boolean traceOnly) {
        PartitionPredicate partition = ((NewZealandRegions.PartitionRegion) region).getPartition();
        IntPredicate predicate = partition.getPredicate(this);
        if (fractRupsInsidePartition == null) {
            Preconditions.checkArgument(!traceOnly, "traceOnly not implemented");
            double[] result = new double[getNumRuptures()];
            for (int ruptureIndex = 0; ruptureIndex < getNumRuptures(); ruptureIndex++) {
                List<Integer> sectionIds = getSectionsIndicesForRup(ruptureIndex);
                double totalArea = getAreaForRup(ruptureIndex);
                double fractionArea = 0;
                for (Integer sectionId : sectionIds) {
                    if (predicate.test(sectionId)) {
                        fractionArea += getAreaForSection(sectionId);
                    }
                }
                result[ruptureIndex] = fractionArea / totalArea;
            }
            fractRupsInsidePartition = result;
        }
        return fractRupsInsidePartition;
    }
}

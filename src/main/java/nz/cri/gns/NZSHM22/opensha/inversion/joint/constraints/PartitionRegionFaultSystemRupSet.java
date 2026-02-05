package nz.cri.gns.NZSHM22.opensha.inversion.joint.constraints;

import com.google.common.base.Preconditions;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;
import org.opensha.commons.geo.Region;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;

/**
 * A rupture set specifically to be used by MFDInversionConstraint.
 *
 * <p>Uses a partition instead of a region to calculate getFractRupsInsideRegion()
 */
public class PartitionRegionFaultSystemRupSet extends FaultSystemRupSet {

    final FaultSystemRupSet original;
    final IntPredicate partitionPredicate;
    double[] fractRupsInsidePartition;

    public PartitionRegionFaultSystemRupSet(
            FaultSystemRupSet original, IntPredicate partitionPredicate) {
        this.original = original;
        this.partitionPredicate = partitionPredicate;
    }

    @Override
    public int getNumRuptures() {
        return original.getNumRuptures();
    }

    @Override
    public double getMaxMag() {
        double[] inside = getFractRupsInsideRegion(null, false);
        return IntStream.range(0, getNumRuptures())
                .filter((index) -> inside[index] > 0)
                .mapToDouble(original::getMagForRup)
                .max()
                .orElse(0);
    }

    @Override
    public double getMinMag() {
        double[] inside = getFractRupsInsideRegion(null, false);
        return IntStream.range(0, getNumRuptures())
                .filter((index) -> inside[index] > 0)
                .mapToDouble(original::getMagForRup)
                .min()
                .orElse(0);
    }

    @Override
    public double getMagForRup(int rupIndex) {
        return original.getMagForRup(rupIndex);
    }

    @Override
    public double[] getFractRupsInsideRegion(Region region, boolean traceOnly) {
        if (fractRupsInsidePartition == null) {
            Preconditions.checkArgument(!traceOnly, "traceOnly not implemented");
            double[] result = new double[original.getNumRuptures()];
            for (int ruptureIndex = 0; ruptureIndex < original.getNumRuptures(); ruptureIndex++) {
                List<Integer> sectionIds = original.getSectionsIndicesForRup(ruptureIndex);
                double totalArea = original.getAreaForRup(ruptureIndex);
                double fractionArea = 0;
                for (Integer sectionId : sectionIds) {
                    if (partitionPredicate.test(sectionId)) {
                        fractionArea += original.getAreaForSection(sectionId);
                    }
                }
                result[ruptureIndex] = fractionArea / totalArea;
            }
            fractRupsInsidePartition = result;
        }
        return fractRupsInsidePartition;
    }
}

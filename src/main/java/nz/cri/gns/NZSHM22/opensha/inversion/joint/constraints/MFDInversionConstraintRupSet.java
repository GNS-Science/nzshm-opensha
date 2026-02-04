package nz.cri.gns.NZSHM22.opensha.inversion.joint.constraints;

import java.util.List;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionPredicate;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.scaling.JointScalingRelationship;
import org.opensha.commons.geo.Region;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.RupSetScalingRelationship;

/**
 * A Rupture set for setting up MFDInversionConstraints for a specific partition. - All original
 * ruptures are preserved - Magnitudes are only calculated for the part of the rupture that is in
 * the partition - getFractRupsInsideRegion() returns 1 if any part of the rupture is inside the
 * region, 0 otherwise
 */
public class MFDInversionConstraintRupSet extends FaultSystemRupSet {

    public MFDInversionConstraintRupSet(FaultSystemRupSet original) {
        init(original);
    }

    @Override
    public double[] getFractRupsInsideRegion(Region region, boolean traceOnly) {
        return getSectionIndicesForAllRups().stream()
                .mapToDouble(sections -> sections.isEmpty() ? 0 : 1)
                .toArray();
    }

    static List<List<Integer>> filterSectionIndices(
            FaultSystemRupSet original, PartitionPredicate partitionPredicate) {
        IntPredicate predicate = partitionPredicate.getPredicate(original);
        return original.getSectionIndicesForAllRups().stream()
                .map(
                        indices ->
                                indices.stream()
                                        .filter(predicate::test)
                                        .collect(Collectors.toList()))
                .collect(Collectors.toList());
    }

    static FaultSystemRupSet create(
            FaultSystemRupSet original,
            PartitionPredicate partitionPredicate,

            // TODO do we need to adjust rake?
            JointScalingRelationship scalingRelationship) {
        List<List<Integer>> sectionIndices = filterSectionIndices(original, partitionPredicate);
        RupSetScalingRelationship scalingRelationship1 =
                scalingRelationship.toRupSetScalingRelationship(partitionPredicate.isCrustal());

        return FaultSystemRupSet.builder(original.getFaultSectionDataList(), sectionIndices)
                .forScalingRelationship(scalingRelationship1)
                .build();
    }
}

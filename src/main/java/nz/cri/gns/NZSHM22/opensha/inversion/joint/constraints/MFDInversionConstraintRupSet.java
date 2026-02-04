package nz.cri.gns.NZSHM22.opensha.inversion.joint.constraints;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionPredicate;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.scaling.JointScalingRelationship;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.RupSetScalingRelationship;

/**
 * A Rupture set specifically for setting up MFDInversionConstraints for a partition. Ruptures that
 * are at least partially in the partition are trimmed to that partition. Magnitudes are only
 * calculated for these ruptures. Ruptures that are completely outside the partition will have
 * magnitude 0, but all their other properties are left intact. This is because ruptures with no
 * fault sections will cause exceptions in OpenSHA code. Ruptures with magnitude 0 are ignored by
 * MFDInversionConstraints.encode()
 */
public class MFDInversionConstraintRupSet extends FaultSystemRupSet {

    protected MFDInversionConstraintRupSet(FaultSystemRupSet original) {
        init(original);
    }

    @Override
    public double getMinMag() {
        // We ignore 0 magnitudes in order to have more streamlined constraints
        return Arrays.stream(getMagForAllRups()).filter(mag -> mag > 0).min().orElseThrow();
    }

    public static FaultSystemRupSet create(
            FaultSystemRupSet original,
            PartitionPredicate partitionPredicate,
            JointScalingRelationship scalingRelationship) {

        IntPredicate predicate = partitionPredicate.getPredicate(original);
        // a mask of all ruptures that contain sections inside the partition
        List<Boolean> mask =
                original.getSectionIndicesForAllRups().stream()
                        .map(sections -> sections.stream().anyMatch(predicate::test))
                        .collect(Collectors.toList());

        List<List<Integer>> sectionIndices =
                new ArrayList<>(original.getSectionIndicesForAllRups());

        // Filter out sections that are outside the partition.
        // We leave ruptures that are completely outside the partition alone, as OpenSHA will break
        // if we have ruptures with no sections.

        for (int r = 0; r < sectionIndices.size(); r++) {
            if (mask.get(r)) {
                sectionIndices.set(
                        r,
                        sectionIndices.get(r).stream()
                                .filter(predicate::test)
                                .collect(Collectors.toList()));
            }
        }

        RupSetScalingRelationship scalingRelationship1 =
                scalingRelationship.toRupSetScalingRelationship(partitionPredicate.isCrustal());

        // create rupture set with the modified ruptures
        FaultSystemRupSet filteredRuptureSet =
                FaultSystemRupSet.builder(original.getFaultSectionDataList(), sectionIndices)
                        .forScalingRelationship(scalingRelationship1)
                        .build();

        // set magnitude to 0 for all ruptures outside the partition
        for (int r = 0; r < mask.size(); r++) {
            if (!mask.get(r)) {
                filteredRuptureSet.getMagForAllRups()[r] = 0;
            }
        }

        return new MFDInversionConstraintRupSet(filteredRuptureSet);
    }
}

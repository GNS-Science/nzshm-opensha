package nz.cri.gns.NZSHM22.opensha.inversion.joint.constraints;

import java.util.*;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.RupSetScalingRelationship;
import org.opensha.sha.faultSurface.FaultSection;

/// A Rupture set specifically for setting up `MFDInversionConstraints` for a partition. Ruptures
// that
/// are at least partially in the partition are trimmed to that partition. Magnitudes are only
/// calculated for these ruptures. Ruptures that are completely outside the partition will have
/// magnitude 0, and no fault sections.
///
/// - Ruptures with no fault sections are ignored by SlipRateConstraint.encode()
/// - Ruptures with magnitude 0 are ignored by MFDInversionConstraints.encode()
public class MFDInversionConstraintRupSet extends FaultSystemRupSet {

    protected MFDInversionConstraintRupSet(FaultSystemRupSet original) {
        init(original);
    }

    @Override
    public double getMinMag() {
        // We ignore 0 magnitudes in order to have more streamlined constraints
        return Arrays.stream(getMagForAllRups()).filter(mag -> mag > 0).min().orElseThrow();
    }

    public static MFDInversionConstraintRupSet create(
            FaultSystemRupSet rupSet,
            IntPredicate predicate,
            RupSetScalingRelationship scalingRelationship) {

        Map<Integer, Integer> oldToNew = new HashMap<>();
        int nextId = 0;

        // Filter fault sections and adjust their section ids so that they are consecutive.
        // remember mapping between old and new ids so that we can adjust the ruptures
        List<FaultSection> faultSections = new ArrayList<>();
        for (FaultSection section : rupSet.getFaultSectionDataList()) {
            if (predicate.test(section.getSectionId())) {
                oldToNew.put(section.getSectionId(), nextId);
                FaultSectionPrefData copiedSection = new FaultSectionPrefData();
                copiedSection.setFaultSectionPrefData(section);
                copiedSection.setSectionId(nextId);
                faultSections.add(copiedSection);
                nextId++;
            }
        }

        // Filter ruptures and use new section ids
        List<List<Integer>> ruptures =
                rupSet.getSectionIndicesForAllRups().stream()
                        .map(
                                rupture ->
                                        rupture.stream()
                                                .map(oldToNew::get)
                                                .filter(Objects::nonNull)
                                                .collect(Collectors.toList()))
                        .collect(Collectors.toList());

        // Add dummy sections to empty ruptures so that rupture set builder does not blow up.
        // This masks which ruptures are now empty so we remember them for fixing later
        List<Boolean> hasMag = new ArrayList<>();
        for (List<Integer> rupture : ruptures) {
            if (rupture.isEmpty()) {
                rupture.add(0);
                hasMag.add(false);
            }
            hasMag.add(true);
        }

        FaultSystemRupSet filteredRuptureSet =
                FaultSystemRupSet.builder(faultSections, ruptures)
                        .forScalingRelationship(scalingRelationship)
                        .build();

        // remove dummy sections for ruptures outside the partition
        // set magnitude to 0 for all ruptures outside the partition
        for (int r = 0; r < hasMag.size(); r++) {
            if (!hasMag.get(r)) {
                filteredRuptureSet.getSectionsIndicesForRup(r).clear();
                filteredRuptureSet.getMagForAllRups()[r] = 0;
            }
        }

        return new MFDInversionConstraintRupSet(filteredRuptureSet);
    }
}

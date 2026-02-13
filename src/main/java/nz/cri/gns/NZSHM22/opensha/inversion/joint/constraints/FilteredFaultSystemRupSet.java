package nz.cri.gns.NZSHM22.opensha.inversion.joint.constraints;

import java.util.*;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.RupSetScalingRelationship;
import org.opensha.sha.faultSurface.FaultSection;

/// A rupture set that has reduced fault sections and/or ruptures.  Ruptures that are empty after
// filtering are removed.
/// Modules are not copied over.
public class FilteredFaultSystemRupSet extends FaultSystemRupSet {

    final Map<Integer, Integer> newToOldRuptures;

    protected FilteredFaultSystemRupSet(
            FaultSystemRupSet original, Map<Integer, Integer> newToOldRuptures) {
        init(original);
        this.newToOldRuptures = newToOldRuptures;
    }

    public int getOldRuptureId(int ruptureId) {
        return newToOldRuptures.get(ruptureId);
    }

    /**
     * Filters a rupture set based on fault section ids.
     *
     * @param rupSet the input rupture set
     * @param sectionIdPredicate a predicate to filter fault sections
     * @param scalingRelationship the scaling relationship to calculate magnitudes with
     * @return a filtered rupture set
     */
    public static FilteredFaultSystemRupSet forIntPredicate(
            FaultSystemRupSet rupSet,
            IntPredicate sectionIdPredicate,
            RupSetScalingRelationship scalingRelationship) {

        Map<Integer, Integer> oldToNewSections = new HashMap<>();
        int nextId = 0;

        // Filter fault sections and adjust their section ids so that they are consecutive.
        // remember mapping between old and new ids so that we can adjust the ruptures
        List<FaultSection> faultSections = new ArrayList<>();
        for (FaultSection section : rupSet.getFaultSectionDataList()) {
            if (sectionIdPredicate.test(section.getSectionId())) {
                oldToNewSections.put(section.getSectionId(), nextId);
                FaultSectionPrefData copiedSection = new FaultSectionPrefData();
                copiedSection.setFaultSectionPrefData(section);
                copiedSection.setSectionId(nextId);
                faultSections.add(copiedSection);
                nextId++;
            }
        }

        // Filter ruptures and use new section ids
        List<List<Integer>> ruptures = new ArrayList<>();
        Map<Integer, Integer> newToOldRuptures = new HashMap<>();
        int oldRuptureId = 0;
        for (List<Integer> rupture : rupSet.getSectionIndicesForAllRups()) {
            List<Integer> filteredRupture =
                    rupture.stream()
                            .map(oldToNewSections::get)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());
            if (!filteredRupture.isEmpty()) {
                newToOldRuptures.put(ruptures.size(), oldRuptureId);
                ruptures.add(filteredRupture);
            }
            oldRuptureId++;
        }

        FaultSystemRupSet filteredRuptureSet =
                FaultSystemRupSet.builder(faultSections, ruptures)
                        .forScalingRelationship(scalingRelationship)
                        .build();

        return new FilteredFaultSystemRupSet(filteredRuptureSet, newToOldRuptures);
    }
}

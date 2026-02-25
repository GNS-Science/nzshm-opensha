package nz.cri.gns.NZSHM22.opensha.inversion.joint.constraints;

import java.util.*;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.RupSetScalingRelationship;
import org.opensha.sha.faultSurface.FaultSection;

/// A rupture set that has reduced fault sections and/or ruptures.  Ruptures that are empty after
// filtering are removed.
/// Modules are not copied over.
public class FilteredFaultSystemRupSet extends FaultSystemRupSet {

    final Map<Integer, Integer> newToOldRuptures;
    final Map<Integer, Integer> oldToNewRuptures;

    protected FilteredFaultSystemRupSet(
            FaultSystemRupSet original,
            Map<Integer, Integer> newToOldRuptures,
            Map<Integer, Integer> oldToNewRuptures) {
        init(original);
        this.newToOldRuptures = newToOldRuptures;
        this.oldToNewRuptures = oldToNewRuptures;
    }

    public int getOldRuptureId(int ruptureId) {
        return newToOldRuptures.get(ruptureId);
    }

    public Integer getNewRuptureId(int oldRuptureId) {
        return oldToNewRuptures.get(oldRuptureId);
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
        Map<Integer, Integer> oldToNewRuptures = new HashMap<>();
        int oldRuptureId = 0;
        for (List<Integer> rupture : rupSet.getSectionIndicesForAllRups()) {
            List<Integer> filteredRupture =
                    rupture.stream()
                            .map(oldToNewSections::get)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());
            if (!filteredRupture.isEmpty()) {
                newToOldRuptures.put(ruptures.size(), oldRuptureId);
                oldToNewRuptures.put(oldRuptureId, ruptures.size());
                ruptures.add(filteredRupture);
            }
            oldRuptureId++;
        }

        FaultSystemRupSet filteredRuptureSet =
                FaultSystemRupSet.builder(faultSections, ruptures)
                        .forScalingRelationship(scalingRelationship)
                        .build();

        return new FilteredFaultSystemRupSet(
                filteredRuptureSet, newToOldRuptures, oldToNewRuptures);
    }

    /**
     * Returns a filtered solution based on the provided predicate and scaling relationship. The
     * rupture set of the solution is filtered using the provided predicate and scaling
     * relationship, and the rates are adjusted accordingly. Ruptures that are empty after filtering
     * are removed.
     *
     * @param solution the original solution to be filtered
     * @param predicate the predicate to filter the ruptures
     * @param scalingRelationship the scaling relationship to calculate magnitudes with
     * @return a new FaultSystemSolution that is filtered based on the provided predicate and
     *     scaling relationship
     */
    public static FaultSystemSolution forIntPredicate(
            FaultSystemSolution solution,
            IntPredicate predicate,
            RupSetScalingRelationship scalingRelationship) {
        FilteredFaultSystemRupSet filteredRupSet =
                FilteredFaultSystemRupSet.forIntPredicate(
                        solution.getRupSet(), predicate, scalingRelationship);

        double[] rates = solution.getRateForAllRups();
        double[] filteredRates = new double[filteredRupSet.getNumRuptures()];

        for (int r = 0; r < rates.length; r++) {
            Integer newIndex = filteredRupSet.getNewRuptureId(r);
            if (newIndex != null) {
                filteredRates[newIndex] = rates[r];
            }
        }

        return new FaultSystemSolution(filteredRupSet, filteredRates);
    }
}

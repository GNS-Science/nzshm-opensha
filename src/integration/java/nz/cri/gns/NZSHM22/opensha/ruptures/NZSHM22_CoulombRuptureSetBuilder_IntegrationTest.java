package nz.cri.gns.NZSHM22.opensha.ruptures;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.*;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import org.dom4j.DocumentException;
import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;

public class NZSHM22_CoulombRuptureSetBuilder_IntegrationTest {

    /**
     * Turns all ruptures into sets of parent ids
     *
     * @param rupSet
     * @return
     */
    public static List<Set<Integer>> toParentIds(FaultSystemRupSet rupSet) {
        List<Set<Integer>> parentIds = new ArrayList<>();
        for (List<Integer> rupture : rupSet.getSectionIndicesForAllRups()) {
            Set<Integer> parents = new HashSet<>();
            for (Integer sectionId : rupture) {
                parents.add(rupSet.getFaultSectionData(sectionId).getParentSectionId());
            }
            parentIds.add(parents);
        }

        return parentIds;
    }

    public Set<Integer> getMostParents(FaultSystemRupSet rupSet) {
        List<Set<Integer>> parents = toParentIds(rupSet);
        parents.sort(Comparator.comparing(Set::size));
        return parents.get(parents.size() - 1);
    }

    @Test
    public void testAlpineVernon() throws IOException, DocumentException {
        Set<Integer> faults = Set.of(35, 123, 30, 31);
        FaultSystemRupSet ruptureSet =
                new NZSHM22_CoulombRuptureSetBuilder()
                        .setFaultModel(NZSHM22_FaultModels.CFM_1_0A_DOM_SANSTVZ)
                        .setFaultFilter(faults)
                        .buildRuptureSet();

        assertEquals(87, ruptureSet.getNumRuptures());

        // we can build a rupture that has at least one of each fault
        assertEquals(faults, getMostParents(ruptureSet));

        assertEquals(18, ruptureSet.getNumSections());
        assertEquals(18, ruptureSet.getSlipRateForAllSections().length);
        assertEquals(18, ruptureSet.getSlipRateStdDevForAllSections().length);

        assertEquals("Fowlers", ruptureSet.getFaultSectionData(11).getParentSectionName());
        assertEquals(5.0E-4, ruptureSet.getSlipRateForSection(11), 0.0000001);
        assertEquals(0.35, ruptureSet.getFaultSectionData(11).getOrigSlipRateStdDev(), 0.0000001);
        assertEquals(3.5E-4, ruptureSet.getSlipRateStdDevForSection(11), 0.0000001);
    }
}

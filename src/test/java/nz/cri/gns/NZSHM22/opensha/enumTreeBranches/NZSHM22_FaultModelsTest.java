package nz.cri.gns.NZSHM22.opensha.enumTreeBranches;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import nz.cri.gns.NZSHM22.opensha.faults.FaultSectionList;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionPredicate;
import nz.cri.gns.NZSHM22.opensha.ruptures.FaultSectionProperties;
import org.dom4j.DocumentException;
import org.junit.Test;
import org.opensha.sha.faultSurface.FaultSection;

public class NZSHM22_FaultModelsTest {

    @Test
    public void testDuplicateNames() throws DocumentException, IOException {
        for (NZSHM22_FaultModels model : NZSHM22_FaultModels.values()) {
            if (model == NZSHM22_FaultModels.CFM_0_9_ALL_2010
                    || model == NZSHM22_FaultModels.CFM_0_9_SANSTVZ_2010
                    || model == NZSHM22_FaultModels.CFM_0_9_ALL_D90
                    || model == NZSHM22_FaultModels.CFM_0_9_SANSTVZ_D90) {
                continue;
            }
            System.out.println(model.name());
            FaultSectionList sections = new FaultSectionList();
            model.fetchFaultSections(sections);
            Set<String> faultNames = new HashSet<>();
            for (FaultSection section : sections) {
                String name = section.getSectionName();
                assertFalse(faultNames.contains(name));
                faultNames.add(name);
            }
        }
    }

    @Test
    public void testFetchFaultSections() throws DocumentException, IOException {
        for (NZSHM22_FaultModels model : NZSHM22_FaultModels.values()) {
            FaultSectionList sections = new FaultSectionList();
            model.fetchFaultSections(sections);
            if (model == NZSHM22_FaultModels.CUSTOM) {
                assertEquals(model.getName(), 0, sections.size());
            } else {
                assertFalse(model.getName(), sections.isEmpty());
            }
        }
    }

    @Test
    public void testFetchNamedFaults() {
        // just checking that we don't explode
        for (NZSHM22_FaultModels model : NZSHM22_FaultModels.values()) {
            model.getNamedFaultsMapAlt();
        }
    }

    @Test
    public void testProperties() throws DocumentException, IOException {
        FaultSectionList sections = new FaultSectionList();
        NZSHM22_FaultModels.SBD_0_4_HKR_LR_30.fetchFaultSections(sections);
        FaultSectionProperties props = new FaultSectionProperties(sections.get(0));
        assertEquals(PartitionPredicate.HIKURANGI, props.getPartition());
        assertEquals(0, (int) props.getColIndex());
        assertEquals(0, (int) props.getRowIndex());
        assertNull(props.getDomain());
    }
}

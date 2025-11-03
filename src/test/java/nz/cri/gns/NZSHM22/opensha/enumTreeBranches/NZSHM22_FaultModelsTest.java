package nz.cri.gns.NZSHM22.opensha.enumTreeBranches;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import nz.cri.gns.NZSHM22.opensha.faults.FaultSectionList;
import nz.cri.gns.NZSHM22.opensha.faults.NZFaultSection;
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
    public void testDomains() throws DocumentException, IOException {
        FaultSectionList sections = new FaultSectionList();
        NZSHM22_FaultModels.CFM_1_0_DOM_ALL.fetchFaultSections(sections);

        NZFaultSection section = (NZFaultSection) sections.get(0);
        assertEquals("21", section.getDomainNo());
        assertEquals("Southern South Island", section.getDomainName());

        section = (NZFaultSection) sections.get(5);
        assertEquals("4", section.getDomainNo());
        assertEquals("Havre Trough - Taupo Rift", section.getDomainName());

        sections = new FaultSectionList();
        NZSHM22_FaultModels.CFM_0_9_ALL_D90.fetchFaultSections(sections);
        section = (NZFaultSection) sections.get(0);
        assertNull(section.getDomainNo());
        assertNull(section.getDomainName());
    }
}

package nz.cri.gns.NZSHM22.opensha.enumTreeBranches;

import static org.junit.Assert.*;

import nz.cri.gns.NZSHM22.opensha.util.FaultSectionList;
import org.dom4j.DocumentException;
import org.junit.Test;
import org.opensha.sha.faultSurface.FaultSection;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class NZSHM22_FaultModelsTest {

    @Test
    public void testDuplicateNames() throws DocumentException, IOException {
        for (NZSHM22_FaultModels model : NZSHM22_FaultModels.values()) {
            if (model == NZSHM22_FaultModels.CFM_0_9_ALL_2010 ||
                    model == NZSHM22_FaultModels.CFM_0_9_SANSTVZ_2010 ||
                    model == NZSHM22_FaultModels.CFM_0_9_ALL_D90 ||
                    model == NZSHM22_FaultModels.CFM_0_9_SANSTVZ_D90) {
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
            assertTrue(model.getName(), sections.size() > 0);
        }
    }

    @Test
    public void testFetchNamedFaults() {
        // just checking that we don't explode
        for (NZSHM22_FaultModels model : NZSHM22_FaultModels.values()) {
            model.getNamedFaultsMapAlt();
        }
    }
}

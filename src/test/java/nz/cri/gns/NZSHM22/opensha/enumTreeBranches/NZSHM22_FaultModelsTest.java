package nz.cri.gns.NZSHM22.opensha.enumTreeBranches;

import static org.junit.Assert.*;

import nz.cri.gns.NZSHM22.opensha.util.FaultSectionList;
import org.dom4j.DocumentException;
import org.junit.Test;

import java.io.IOException;

public class NZSHM22_FaultModelsTest {

    @Test
    public void testFetchFaultSections() throws DocumentException, IOException {
        for(NZSHM22_FaultModels model : NZSHM22_FaultModels.values()){
            FaultSectionList sections = new FaultSectionList();
            System.out.println(model.getName());
            model.fetchFaultSections(sections);
            assertTrue(model.getName(), sections.size() > 0);
        }
    }
}

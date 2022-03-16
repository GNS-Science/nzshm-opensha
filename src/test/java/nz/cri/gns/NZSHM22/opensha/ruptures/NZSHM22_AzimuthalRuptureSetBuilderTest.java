package nz.cri.gns.NZSHM22.opensha.ruptures;

import static org.junit.Assert.*;

import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.faults.FaultSectionList;
import nz.cri.gns.NZSHM22.opensha.faults.NZFaultSection;
import org.dom4j.DocumentException;
import org.junit.Test;
import org.opensha.sha.faultSurface.FaultSection;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class NZSHM22_AzimuthalRuptureSetBuilderTest {

    @Test
    public void testScaleDepth() throws DocumentException, IOException {

        FaultSectionList original = new FaultSectionList();
        NZSHM22_FaultModels.CFM_1_0_DOM_ALL.fetchFaultSections(original);
        Map<Integer, NZFaultSection> parents = new HashMap<>();
        for (FaultSection section : original) {
            parents.put(section.getSectionId(), (NZFaultSection) section);
        }

        NZSHM22_AzimuthalRuptureSetBuilder builder = new NZSHM22_AzimuthalRuptureSetBuilder();
        builder.setFaultModel(NZSHM22_FaultModels.CFM_1_0_DOM_ALL)
                .setScaleDepthIncludeDomain("4", 0.5)
                .setScaleDepthExcludeDomain("4", 0.8)
                .loadFaults();

        for (FaultSection section : builder.subSections) {
            NZFaultSection parent = parents.get(section.getParentSectionId());
            if(parent.getDomainNo().equals("4")){
                assertEquals(parent.getAveLowerDepth() * 0.5, section.getAveLowerDepth(), 0.0000001);
            }else{
                assertEquals(parent.getAveLowerDepth() * 0.8, section.getAveLowerDepth(), 0.0000001);
            }
        }
    }
}

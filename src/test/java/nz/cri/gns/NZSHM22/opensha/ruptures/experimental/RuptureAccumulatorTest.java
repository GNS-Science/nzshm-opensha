package nz.cri.gns.NZSHM22.opensha.ruptures.experimental;

import static nz.cri.gns.NZSHM22.opensha.util.TestHelpers.createRupSetForSections;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.List;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.ruptures.FaultSectionProperties;
import org.dom4j.DocumentException;
import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.faultSurface.FaultSection;

public class RuptureAccumulatorTest {

    // sections without original ids get their input ids as original ids
    @Test
    public void testSetsOriginalIds() throws DocumentException, IOException {
        FaultSystemRupSet rupSet = createRupSetForSections(NZSHM22_FaultModels.CFM_1_0A_DOM_ALL);
        FaultSection section = rupSet.getFaultSectionData(5);

        RuptureAccumulator accumulator = new RuptureAccumulator().setRupSet(rupSet, List.of());
        accumulator.add(section);

        FaultSectionProperties props = new FaultSectionProperties(accumulator.sections.get(0));
        assertEquals(0, accumulator.sections.get(0).getSectionId());
        assertEquals(Integer.valueOf(section.getSectionId()), props.getOriginalId());
        assertEquals(Integer.valueOf(section.getParentSectionId()), props.getOriginalParent());
    }

    // existing original ids are preserved
    @Test
    public void testPreservesOriginalIds() throws DocumentException, IOException {
        FaultSystemRupSet rupSet = createRupSetForSections(NZSHM22_FaultModels.CFM_1_0A_DOM_ALL);
        FaultSection section = rupSet.getFaultSectionData(5);
        FaultSectionProperties inputProps = new FaultSectionProperties(section);
        inputProps.setOriginalId(1000);
        inputProps.setOriginalParent(1200);

        RuptureAccumulator accumulator = new RuptureAccumulator().setRupSet(rupSet, List.of());
        accumulator.add(section);

        FaultSectionProperties props = new FaultSectionProperties(accumulator.sections.get(0));
        assertEquals(Integer.valueOf(1000), props.getOriginalId());
        assertEquals(Integer.valueOf(1200), props.getOriginalParent());
    }
}

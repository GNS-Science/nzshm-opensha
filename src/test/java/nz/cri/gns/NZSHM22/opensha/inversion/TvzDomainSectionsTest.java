package nz.cri.gns.NZSHM22.opensha.inversion;

import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.util.TestHelpers;
import org.dom4j.DocumentException;
import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;

import java.io.IOException;
import java.util.Set;

import static nz.cri.gns.NZSHM22.opensha.util.TestHelpers.makeRupSet;
import static org.junit.Assert.assertEquals;

public class TvzDomainSectionsTest {

    @Test
    public void testFilter() throws IOException, DocumentException {
        FaultSystemRupSet rupSet = makeRupSet(NZSHM22_FaultModels.CFM_1_0A_DOM_ALL, ScalingRelationships.SHAW_2009_MOD);
        TvzDomainSections tvzSections = new TvzDomainSections(rupSet);

        assertEquals(712, rupSet.getNumSections());
        assertEquals(200, tvzSections.sections.size());
    }

    @Test
    public void testSerialisation() throws IOException {
        TvzDomainSections sections = new TvzDomainSections();
        sections.sections = Set.of(3, 7, 42);

        TvzDomainSections actual = (TvzDomainSections) TestHelpers.serialiseDeserialise(sections);

        assertEquals(sections.getSections(), actual.getSections());
    }
}

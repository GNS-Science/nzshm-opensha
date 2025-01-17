package nz.cri.gns.NZSHM22.opensha.inversion;

import static nz.cri.gns.NZSHM22.opensha.util.TestHelpers.createRupSet;
import static org.junit.Assert.*;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_LogicTreeBranch;
import org.dom4j.DocumentException;
import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;

public class NZSHM22_CrustalInversionConfigurationTest {

    public NZSHM22_InversionFaultSystemRuptSet loadRupSet() throws IOException, DocumentException {
        FaultSystemRupSet rupSet =
                createRupSet(
                        NZSHM22_FaultModels.CFM_1_0A_DOM_ALL,
                        ScalingRelationships.SHAW_2009_MOD,
                        List.of(
                                // non-TVZ rupture
                                List.of(1, 2, 3),
                                // TVZ rupture
                                List.of(5, 6, 7)));
        NZSHM22_LogicTreeBranch branch = NZSHM22_LogicTreeBranch.crustalInversion();
        return NZSHM22_InversionFaultSystemRuptSet.fromExistingCrustalSet(rupSet, branch);
    }

    @Test
    public void testRegions() throws IOException, DocumentException {
        NZSHM22_InversionFaultSystemRuptSet rupSet = loadRupSet();
        assertEquals(712, rupSet.getNumSections());

        NZSHM22_CrustalInversionConfiguration.setRegionalData(rupSet, 5, 7);
        RegionalRupSetData tvzData = rupSet.getTvzRegionalData();
        RegionalRupSetData sansTvzData = rupSet.getSansTvzRegionalData();

        // Assert that the sections are split between the regions with no overlap or omissions.
        assertEquals(200, tvzData.getFaultSectionDataList().size());
        assertEquals(512, sansTvzData.getFaultSectionDataList().size());
        assertEquals(
                List.of(true, true, true),
                Stream.of(5, 6, 7).map(tvzData::isInRegion).collect(Collectors.toList()));
        assertEquals(
                List.of(true, true, true, true, true),
                Stream.of(0, 1, 2, 3, 4).map(sansTvzData::isInRegion).collect(Collectors.toList()));

        // sansTVZ min mag
        assertEquals(5.0, rupSet.getFinalMinMagForSection(0), 0.00000001);
        // TVZ min mag
        assertEquals(7.0, rupSet.getFinalMinMagForSection(5), 0.00000001);
    }
}

package nz.cri.gns.NZSHM22.opensha.inversion;

import static org.junit.Assert.*;

import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_LogicTreeBranch;
import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class NZSHM22_CrustalInversionConfigurationTest {


    public NZSHM22_InversionFaultSystemRuptSet loadRupSet() throws URISyntaxException, IOException {
        URL url = Thread.currentThread().getContextClassLoader().getResource("RupSetWaiohauNorth.zip");
        return NZSHM22_InversionFaultSystemRuptSet.loadCrustalRuptureSet(new File(url.toURI()), NZSHM22_LogicTreeBranch.crustalInversion());
    }


    @Test
    public void testRegions() throws URISyntaxException, IOException {
        NZSHM22_InversionFaultSystemRuptSet rupSet = loadRupSet();
        assertEquals(8, rupSet.getNumSections());

        NZSHM22_CrustalInversionConfiguration.setRegionalData(rupSet, 5, 7);
        RegionalRupSetData tvzData = rupSet.getTvzRegionalData();
        RegionalRupSetData sansTvzData = rupSet.getSansTvzRegionalData();

        // Assert that the sections are split between the regions with no overlap or omissions.
        assertEquals(3, tvzData.getFaultSectionDataList().size());
        assertEquals(5, sansTvzData.getFaultSectionDataList().size());
        assertEquals(List.of(true, true, true), Stream.of(0, 1, 2).map(tvzData::isInRegion).collect(Collectors.toList()));
        assertEquals(List.of(true, true, true, true, true), Stream.of(3, 4, 5, 6, 7).map(sansTvzData::isInRegion).collect(Collectors.toList()));

        // TVZ has a different minMag applied
        assertEquals(
                List.of(7.0, 7.0, 7.0, 6.354597519806781, 6.354597519806781, 6.354597519806781, 6.354597519806781, 6.354597519806781),
                Stream.of(0, 1, 2, 3, 4, 5, 6, 7).map(rupSet::getFinalMinMagForSection).collect(Collectors.toList()));

    }
}

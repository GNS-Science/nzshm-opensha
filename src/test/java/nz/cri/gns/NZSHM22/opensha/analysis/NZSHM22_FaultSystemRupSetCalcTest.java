package nz.cri.gns.NZSHM22.opensha.analysis;

import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_LogicTreeBranch;
import nz.cri.gns.NZSHM22.opensha.inversion.NZSHM22_CrustalInversionTargetMFDs;
import nz.cri.gns.NZSHM22.opensha.inversion.NZSHM22_InversionFaultSystemRuptSet;
import nz.cri.gns.NZSHM22.opensha.inversion.RegionalRupSetData;
import org.junit.Test;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import scratch.UCERF3.griddedSeismicity.GriddedSeisUtils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NZSHM22_FaultSystemRupSetCalcTest {

    public NZSHM22_InversionFaultSystemRuptSet loadRupSet() throws URISyntaxException, IOException {
        URL url = Thread.currentThread().getContextClassLoader().getResource("RupSetWaiohauNorth.zip");
        return NZSHM22_InversionFaultSystemRuptSet.loadCrustalRuptureSet(new File(url.toURI()), NZSHM22_LogicTreeBranch.crustalInversion());
    }

    @Test
    public void testgetCharSubSeismoOnFaultMFD_forEachSection() throws URISyntaxException, IOException {
        List faultSections = new ArrayList<>();
        faultSections.add(mock(FaultSection.class));
        RegionalRupSetData rupSet = mock(RegionalRupSetData.class);
        when(rupSet.getFaultSectionDataList()).thenReturn(faultSections);

        GriddedSeisUtils gsu = mock(GriddedSeisUtils.class);
        when(gsu.pdfValForSection(0)).thenReturn(1.2);
        GutenbergRichterMagFreqDist totalTargetGR = new GutenbergRichterMagFreqDist(
                NZSHM22_CrustalInversionTargetMFDs.NZ_MIN_MAG,
                NZSHM22_CrustalInversionTargetMFDs.NZ_NUM_BINS,
                NZSHM22_CrustalInversionTargetMFDs.DELTA_MAG);

        // populate the MFD bins
        totalTargetGR.setAllButTotMoRate(NZSHM22_CrustalInversionTargetMFDs.NZ_MIN_MAG, 8.05, 1, 0.959);

        // Test Case: global minMag is greater than minMag for section
        when(rupSet.getMinMagForSection(0)).thenReturn(3.0);

        ArrayList<GutenbergRichterMagFreqDist> actual = NZSHM22_FaultSystemRupSetCalc.getCharSubSeismoOnFaultMFD_forEachSection(
                rupSet,
                gsu,
                totalTargetGR,
                7.05
        );

        int bin705 = actual.get(0).getClosestXIndex(7.05);
        // Everything from magnitude 7.05 is zero
        for (int index = bin705; index < actual.get(0).size(); index++) {
            assertEquals(actual.get(0).getY(index), 0, 0.000001);
        }

        // Everything below 7.05 is not zero
        for (int index = 0; index < bin705; index++) {
            assertTrue(actual.get(0).getY(index) > 0);
        }

        // Test Case: global minMag is less than minMag for section
        when(rupSet.getMinMagForSection(0)).thenReturn(7.25);

        actual = NZSHM22_FaultSystemRupSetCalc.getCharSubSeismoOnFaultMFD_forEachSection(
                rupSet,
                gsu,
                totalTargetGR,
                7.05
        );

        int bin725 = actual.get(0).getClosestXIndex(7.25);
        // Everything from magnitude 7.25 is zero
        for (int index = bin725; index < actual.get(0).size(); index++) {
            assertEquals(actual.get(0).getY(index), 0, 0.000001);
        }

        // Everything below 7.25 is not zero
        for (int index = 0; index < bin725; index++) {
            assertTrue(actual.get(0).getY(index) > 0);
        }

    }
}

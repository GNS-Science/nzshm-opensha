package nz.cri.gns.NZSHM22.opensha.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import nz.cri.gns.NZSHM22.opensha.inversion.NZSHM22_CrustalInversionTargetMFDs;
import nz.cri.gns.NZSHM22.opensha.inversion.RegionalRupSetData;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.Config;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionConfig;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionPredicate;
import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import scratch.UCERF3.griddedSeismicity.GriddedSeisUtils;

public class NZSHM22_FaultSystemRupSetCalcTest {

    @Test
    public void testgetCharSubSeismoOnFaultMFD_forEachSection() {
        List faultSections = new ArrayList<>();
        faultSections.add(mock(FaultSection.class));
        RegionalRupSetData rupSet = mock(RegionalRupSetData.class);
        when(rupSet.getFaultSectionDataList()).thenReturn(faultSections);

        GriddedSeisUtils gsu = mock(GriddedSeisUtils.class);
        when(gsu.pdfValForSection(0)).thenReturn(1.2);
        GutenbergRichterMagFreqDist totalTargetGR =
                new GutenbergRichterMagFreqDist(
                        NZSHM22_CrustalInversionTargetMFDs.NZ_MIN_MAG,
                        NZSHM22_CrustalInversionTargetMFDs.NZ_NUM_BINS,
                        NZSHM22_CrustalInversionTargetMFDs.DELTA_MAG);

        // populate the MFD bins
        totalTargetGR.setAllButTotMoRate(
                NZSHM22_CrustalInversionTargetMFDs.NZ_MIN_MAG, 8.05, 1, 0.959);

        // Test Case: global minMag is greater than minMag for section
        when(rupSet.getMinMagForSection(0)).thenReturn(3.0);

        ArrayList<GutenbergRichterMagFreqDist> actual =
                NZSHM22_FaultSystemRupSetCalc.getCharSubSeismoOnFaultMFD_forEachSection(
                        rupSet, gsu, totalTargetGR, 7.05);

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

        actual =
                NZSHM22_FaultSystemRupSetCalc.getCharSubSeismoOnFaultMFD_forEachSection(
                        rupSet, gsu, totalTargetGR, 7.05);

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

    @Test
    public void testComputeMinSeismoMagForSections() {
        FaultSection sectionA = mock(FaultSection.class);
        FaultSection sectionB = mock(FaultSection.class);
        FaultSection sectionC = mock(FaultSection.class);
        when(sectionA.getParentSectionId()).thenReturn(0);
        when(sectionB.getParentSectionId()).thenReturn(0);
        when(sectionC.getParentSectionId()).thenReturn(1);
        List faultSections = List.of(sectionA, sectionB, sectionC);
        FaultSystemRupSet rupSet = mock(FaultSystemRupSet.class);
        when(rupSet.getFaultSectionDataList()).thenReturn(faultSections);
        when(rupSet.getMinMagForSection(0)).thenReturn(0.15);
        when(rupSet.getMinMagForSection(1)).thenReturn(0.2);
        when(rupSet.getMinMagForSection(2)).thenReturn(0.05);

        double systemWideMinSeismoMag = 0.1;

        double[] actual =
                NZSHM22_FaultSystemRupSetCalc.computeMinSeismoMagForSections(
                        rupSet, systemWideMinSeismoMag);

        // the parent section of 0 and 1 has a max minseismomag of 0.2
        assertEquals(0.2, actual[0], 0.0001);
        assertEquals(0.2, actual[1], 0.0001);
        // the parent section of 2 has a minmseismomag of 0.05, so here the system-wide min value
        // takes effect
        assertEquals(systemWideMinSeismoMag, actual[2], 0.0001);
    }

    @Test
    public void testComputeMinSeismoMagForSectionsJoint() {
        FaultSection sectionA = mock(FaultSection.class);
        FaultSection sectionB = mock(FaultSection.class);
        FaultSection sectionC = mock(FaultSection.class);
        FaultSection sectionD = mock(FaultSection.class);
        when(sectionA.getParentSectionId()).thenReturn(0);
        when(sectionB.getParentSectionId()).thenReturn(1);
        when(sectionC.getParentSectionId()).thenReturn(2);
        when(sectionD.getParentSectionId()).thenReturn(2);
        List faultSections = List.of(sectionA, sectionB, sectionC, sectionD);
        FaultSystemRupSet rupSet = mock(FaultSystemRupSet.class);
        when(rupSet.getFaultSectionDataList()).thenReturn(faultSections);
        when(rupSet.getMinMagForSection(0)).thenReturn(0.15);
        when(rupSet.getMinMagForSection(1)).thenReturn(0.2);
        when(rupSet.getMinMagForSection(2)).thenReturn(0.5);
        when(rupSet.getMinMagForSection(3)).thenReturn(0.1);

        PartitionConfig config1 = new PartitionConfig(PartitionPredicate.CRUSTAL);
        PartitionConfig config2 = new PartitionConfig(PartitionPredicate.HIKURANGI);
        config1.partitionPredicate = (sectionId) -> sectionId == 0;
        config1.minMag = 0.42;
        config2.partitionPredicate = (sectionId) -> sectionId > 0;
        config2.minMag = 0.31;
        Config config = new Config();
        config.ruptureSet = rupSet;
        config.partitions.add(config1);
        config.partitions.add(config2);

        double[] actual = NZSHM22_FaultSystemRupSetCalc.computeMinSeismoMagForSections(config);

        // sections 1 and 2 are in separate partitions and get assigned those minMags
        assertEquals(config1.minMag, actual[0], 0.0001);
        assertEquals(config2.minMag, actual[1], 0.0001);
        // sections 2 and 3 have a parent with a minMag greater than the partition minMag
        assertEquals(0.5, actual[2], 0.0001);
        assertEquals(0.5, actual[3], 0.0001);
    }
}

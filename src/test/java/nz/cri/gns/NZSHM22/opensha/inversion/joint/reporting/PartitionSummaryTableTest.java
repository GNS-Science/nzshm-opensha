package nz.cri.gns.NZSHM22.opensha.inversion.joint.reporting;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionPredicate;
import nz.cri.gns.NZSHM22.opensha.ruptures.FaultSectionProperties;
import org.junit.Test;
import org.opensha.commons.geo.Location;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;

public class PartitionSummaryTableTest {

    private GeoJSONFaultSection makeSection(int id, PartitionPredicate partition) {
        FaultTrace trace = new FaultTrace("trace");
        trace.add(new Location(-41, 174));
        trace.add(new Location(-42, 175));
        FaultSectionPrefData pref = new FaultSectionPrefData();
        pref.setSectionId(id);
        pref.setSectionName("Section " + id);
        pref.setFaultTrace(trace);
        pref.setAveDip(45);
        pref.setAveRake(90);
        pref.setAveUpperDepth(0);
        pref.setAveLowerDepth(10);
        pref.setDipDirection((float) trace.getDipDirection());
        GeoJSONFaultSection section = GeoJSONFaultSection.fromFaultSection(pref);
        new FaultSectionProperties(section).setPartition(partition);
        return section;
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testCounts() throws Exception {
        GeoJSONFaultSection s0 = makeSection(0, PartitionPredicate.CRUSTAL);
        GeoJSONFaultSection s1 = makeSection(1, PartitionPredicate.CRUSTAL);
        GeoJSONFaultSection s2 = makeSection(2, PartitionPredicate.HIKURANGI);

        List sections = Arrays.asList(s0, s1, s2);

        FaultSystemRupSet rupSet = mock(FaultSystemRupSet.class);
        when(rupSet.getFaultSectionDataList()).thenReturn(sections);
        when(rupSet.getNumRuptures()).thenReturn(3);

        // Rup 0: exclusive CRUSTAL (sections 0,1)
        when(rupSet.getSectionsIndicesForRup(0)).thenReturn(Arrays.asList(0, 1));
        when(rupSet.getFaultSectionData(0)).thenReturn(s0);
        when(rupSet.getFaultSectionData(1)).thenReturn(s1);

        // Rup 1: exclusive HIKURANGI (section 2)
        when(rupSet.getSectionsIndicesForRup(1)).thenReturn(Arrays.asList(2));
        when(rupSet.getFaultSectionData(2)).thenReturn(s2);

        // Rup 2: shared CRUSTAL+HIKURANGI (sections 1,2)
        when(rupSet.getSectionsIndicesForRup(2)).thenReturn(Arrays.asList(1, 2));

        PartitionSummaryTable table = new PartitionSummaryTable();
        List<String> lines =
                table.plot(
                        rupSet,
                        (FaultSystemSolution) null,
                        (ReportMetadata) null,
                        (File) null,
                        null,
                        null);

        assertNotNull(lines);
        assertEquals(5, lines.size());

        // CRUSTAL comes before HIKURANGI in enum order
        String crustalRow = lines.get(2);
        assertTrue("Expected CRUSTAL in: " + crustalRow, crustalRow.contains("CRUSTAL"));
        assertTrue("Expected 2 sections in: " + crustalRow, crustalRow.contains("| 2 |"));

        String hikurangiRow = lines.get(3);
        assertTrue("Expected HIKURANGI in: " + hikurangiRow, hikurangiRow.contains("HIKURANGI"));

        // Multi-partition total = 1
        String multiRow = lines.get(4);
        assertTrue("Expected 1 multi-partition in: " + multiRow, multiRow.contains("**1**"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testNoRuptures() throws Exception {
        GeoJSONFaultSection s0 = makeSection(0, PartitionPredicate.CRUSTAL);

        List sections = Arrays.asList(s0);

        FaultSystemRupSet rupSet = mock(FaultSystemRupSet.class);
        when(rupSet.getFaultSectionDataList()).thenReturn(sections);
        when(rupSet.getNumRuptures()).thenReturn(0);

        PartitionSummaryTable table = new PartitionSummaryTable();
        List<String> lines =
                table.plot(
                        rupSet,
                        (FaultSystemSolution) null,
                        (ReportMetadata) null,
                        (File) null,
                        null,
                        null);

        assertNotNull(lines);
        assertEquals(4, lines.size());
        assertTrue(lines.get(2).contains("| 0 | 0 |"));
        assertTrue(lines.get(3).contains("**0**"));
    }
}

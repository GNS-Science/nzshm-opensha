package nz.cri.gns.NZSHM22.opensha.inversion.joint.reporting;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionPredicate;
import nz.cri.gns.NZSHM22.opensha.ruptures.FaultSectionProperties;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.opensha.commons.geo.Location;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;

/** Tests for {@link JointRuptureRatePlot}. */
public class JointRuptureRatePlotTest {

    @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

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

    /** Returns null when no solution is provided. */
    @Test
    public void testNullSolution() throws Exception {
        FaultSystemRupSet rupSet = mock(FaultSystemRupSet.class);
        JointRuptureRatePlot plot = new JointRuptureRatePlot();
        List<String> result =
                plot.plot(
                        rupSet,
                        (FaultSystemSolution) null,
                        (ReportMetadata) null,
                        (File) null,
                        null,
                        null);
        assertNull(result);
    }

    /** Classifies exclusive ruptures correctly. */
    @Test
    public void testClassifyExclusive() {
        assertEquals(
                "CRUSTAL", JointRuptureRatePlot.classify(EnumSet.of(PartitionPredicate.CRUSTAL)));
        assertEquals(
                "HIKURANGI",
                JointRuptureRatePlot.classify(EnumSet.of(PartitionPredicate.HIKURANGI)));
        assertEquals(
                "PUYSEGUR", JointRuptureRatePlot.classify(EnumSet.of(PartitionPredicate.PUYSEGUR)));
    }

    /** Classifies joint ruptures correctly. */
    @Test
    public void testClassifyJoint() {
        assertEquals(
                "CRUSTAL+HIKURANGI",
                JointRuptureRatePlot.classify(
                        EnumSet.of(PartitionPredicate.CRUSTAL, PartitionPredicate.HIKURANGI)));
        assertEquals(
                "CRUSTAL+PUYSEGUR",
                JointRuptureRatePlot.classify(
                        EnumSet.of(PartitionPredicate.CRUSTAL, PartitionPredicate.PUYSEGUR)));
    }

    /** Returns null for unrecognised partition combinations. */
    @Test
    public void testClassifyUnknown() {
        assertNull(
                JointRuptureRatePlot.classify(
                        EnumSet.of(PartitionPredicate.HIKURANGI, PartitionPredicate.PUYSEGUR)));
    }

    /** Produces markdown output with chart and table for a solution with joint ruptures. */
    @SuppressWarnings("unchecked")
    @Test
    public void testPlotProducesOutput() throws Exception {
        GeoJSONFaultSection s0 = makeSection(0, PartitionPredicate.CRUSTAL);
        GeoJSONFaultSection s1 = makeSection(1, PartitionPredicate.HIKURANGI);
        GeoJSONFaultSection s2 = makeSection(2, PartitionPredicate.PUYSEGUR);

        List sections = Arrays.asList(s0, s1, s2);

        FaultSystemRupSet rupSet = mock(FaultSystemRupSet.class);
        when(rupSet.getFaultSectionDataList()).thenReturn(sections);
        when(rupSet.getNumRuptures()).thenReturn(3);
        when(rupSet.getMinMag()).thenReturn(6.0);
        when(rupSet.getMaxMag()).thenReturn(8.0);

        // Rup 0: exclusive CRUSTAL
        when(rupSet.getSectionsIndicesForRup(0)).thenReturn(Arrays.asList(0));
        when(rupSet.getFaultSectionData(0)).thenReturn(s0);
        when(rupSet.getMagForRup(0)).thenReturn(6.5);

        // Rup 1: exclusive HIKURANGI
        when(rupSet.getSectionsIndicesForRup(1)).thenReturn(Arrays.asList(1));
        when(rupSet.getFaultSectionData(1)).thenReturn(s1);
        when(rupSet.getMagForRup(1)).thenReturn(7.0);

        // Rup 2: joint CRUSTAL+HIKURANGI
        when(rupSet.getSectionsIndicesForRup(2)).thenReturn(Arrays.asList(0, 1));
        when(rupSet.getMagForRup(2)).thenReturn(7.5);

        FaultSystemSolution sol = mock(FaultSystemSolution.class);
        when(sol.getRupSet()).thenReturn(rupSet);
        when(sol.getRateForRup(0)).thenReturn(1e-4);
        when(sol.getRateForRup(1)).thenReturn(0.0);
        when(sol.getRateForRup(2)).thenReturn(2e-5);

        File resourcesDir = tempFolder.newFolder("resources");

        JointRuptureRatePlot plot = new JointRuptureRatePlot();
        List<String> lines =
                plot.plot(rupSet, sol, (ReportMetadata) null, resourcesDir, "resources", "");

        assertNotNull(lines);
        assertFalse(lines.isEmpty());

        // Should contain side-by-side table with incremental and cumulative image references
        String allText = String.join("\n", lines);
        assertTrue(allText.contains("joint_rupture_mfds.png"));
        assertTrue(allText.contains("joint_rupture_mfds_cumulative.png"));
        assertTrue(allText.contains("Incremental MFDs"));
        assertTrue(allText.contains("Cumulative MFDs"));
        assertTrue(allText.contains("CRUSTAL+HIKURANGI"));
        assertTrue(allText.contains("CRUSTAL+PUYSEGUR"));

        // CRUSTAL row: 1 total, 1 with rate
        String crustalRow =
                lines.stream().filter(l -> l.startsWith("| CRUSTAL |")).findFirst().orElseThrow();
        assertTrue(crustalRow.contains("| 1 | 1 |"));

        // HIKURANGI row: 1 total, 0 with rate
        String hikRow =
                lines.stream().filter(l -> l.startsWith("| HIKURANGI |")).findFirst().orElseThrow();
        assertTrue(hikRow.contains("| 1 | 0 |"));

        // CRUSTAL+HIKURANGI row: 1 total, 1 with rate
        String jointRow =
                lines.stream()
                        .filter(l -> l.contains("CRUSTAL+HIKURANGI"))
                        .findFirst()
                        .orElseThrow();
        assertTrue(jointRow.contains("| 1 | 1 |"));

        // PNG files should have been written
        assertTrue(new File(resourcesDir, "joint_rupture_mfds.png").exists());
        assertTrue(new File(resourcesDir, "joint_rupture_mfds_cumulative.png").exists());
    }
}

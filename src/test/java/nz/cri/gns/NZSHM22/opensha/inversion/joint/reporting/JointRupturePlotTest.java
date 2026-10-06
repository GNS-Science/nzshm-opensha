package nz.cri.gns.NZSHM22.opensha.inversion.joint.reporting;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.util.Arrays;
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

/** Tests for {@link JointRupturePlot}. */
public class JointRupturePlotTest {

    @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

    protected GeoJSONFaultSection makeSection(int id, PartitionPredicate partition, double lon) {
        FaultTrace trace = new FaultTrace("trace");
        trace.add(new Location(-41, lon));
        trace.add(new Location(-42, lon + 0.5));
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
        assertNull(
                new JointRupturePlot().plot(rupSet, null, (ReportMetadata) null, null, null, null));
    }

    /** Produces maps, MFDs and statistics, counting only joint ruptures. */
    @SuppressWarnings("unchecked")
    @Test
    public void testPlotProducesOutput() throws Exception {
        GeoJSONFaultSection s0 = makeSection(0, PartitionPredicate.CRUSTAL, 174);
        GeoJSONFaultSection s1 = makeSection(1, PartitionPredicate.HIKURANGI, 175);
        GeoJSONFaultSection s2 = makeSection(2, PartitionPredicate.CRUSTAL, 176);
        List sections = Arrays.asList(s0, s1, s2);

        FaultSystemRupSet rupSet = mock(FaultSystemRupSet.class);
        when(rupSet.getFaultSectionDataList()).thenReturn(sections);
        when(rupSet.getNumRuptures()).thenReturn(3);
        when(rupSet.getMinMag()).thenReturn(6.0);
        when(rupSet.getMaxMag()).thenReturn(8.0);
        when(rupSet.getFaultSectionData(0)).thenReturn(s0);
        when(rupSet.getFaultSectionData(1)).thenReturn(s1);
        when(rupSet.getFaultSectionData(2)).thenReturn(s2);

        // Rup 0: crustal only
        when(rupSet.getSectionsIndicesForRup(0)).thenReturn(Arrays.asList(2));
        when(rupSet.getMagForRup(0)).thenReturn(6.5);
        // Rup 1: joint with rate
        when(rupSet.getSectionsIndicesForRup(1)).thenReturn(Arrays.asList(0, 1));
        when(rupSet.getMagForRup(1)).thenReturn(7.5);
        // Rup 2: joint without rate
        when(rupSet.getSectionsIndicesForRup(2)).thenReturn(Arrays.asList(0, 1));
        when(rupSet.getMagForRup(2)).thenReturn(7.6);

        FaultSystemSolution sol = mock(FaultSystemSolution.class);
        when(sol.getRupSet()).thenReturn(rupSet);
        when(sol.getRateForRup(0)).thenReturn(1e-3);
        when(sol.getRateForRup(1)).thenReturn(2e-5);
        when(sol.getRateForRup(2)).thenReturn(0.0);

        File resourcesDir = tempFolder.newFolder("resources");
        JointRupturePlot plot = new JointRupturePlot();
        plot.setSubHeading("##");
        List<String> lines =
                plot.plot(rupSet, sol, (ReportMetadata) null, resourcesDir, "resources", "");

        String allText = String.join("\n", lines);
        assertTrue(allText.contains("joint_partic_crustal.png"));
        assertTrue(allText.contains("joint_partic_subduction.png"));
        assertTrue(allText.contains("joint_only_mfds.png"));
        assertTrue(allText.contains("joint_only_mfds_cumulative.png"));

        String row =
                lines.stream()
                        .filter(l -> l.startsWith("| CRUSTAL+HIKURANGI |"))
                        .findFirst()
                        .orElseThrow();
        assertTrue(row.startsWith("| CRUSTAL+HIKURANGI | 2 | 1 |"));
        assertTrue(row.endsWith("| 2 |"));

        for (String name :
                List.of(
                        "joint_partic_crustal.png",
                        "joint_partic_subduction.png",
                        "joint_only_mfds.png",
                        "joint_only_mfds_cumulative.png")) {
            assertTrue(name, new File(resourcesDir, name).exists());
        }
    }

    /** Ruptures within a single partition are not joint. */
    @Test
    public void testJointCategory() {
        GeoJSONFaultSection s0 = makeSection(0, PartitionPredicate.CRUSTAL, 174);
        GeoJSONFaultSection s1 = makeSection(1, PartitionPredicate.PUYSEGUR, 175);
        FaultSystemRupSet rupSet = mock(FaultSystemRupSet.class);
        when(rupSet.getFaultSectionData(0)).thenReturn(s0);
        when(rupSet.getFaultSectionData(1)).thenReturn(s1);
        when(rupSet.getSectionsIndicesForRup(0)).thenReturn(Arrays.asList(0));
        when(rupSet.getSectionsIndicesForRup(1)).thenReturn(Arrays.asList(0, 1));

        assertNull(JointRupturePlot.jointCategory(rupSet, 0));
        assertEquals("CRUSTAL+PUYSEGUR", JointRupturePlot.jointCategory(rupSet, 1));
    }
}

package nz.cri.gns.NZSHM22.opensha.inversion.joint.reporting;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionPredicate;
import nz.cri.gns.NZSHM22.opensha.ruptures.FaultSectionProperties;
import org.junit.Test;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.geo.Location;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.RupSetMetadata;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;

/** Tests for {@link PartitionSummaryTable}. */
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

    /**
     * Sets a field value on an object, traversing the class hierarchy to find the field. Works on
     * final fields of mocked objects.
     */
    private static void setField(Object obj, String fieldName, Object value) throws Exception {
        Class<?> clazz = obj.getClass();
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(obj, value);
                return;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName + " not found in hierarchy of " + obj.getClass());
    }

    /**
     * Creates a mock {@link ReportMetadata} with primary and comparison rupture sets.
     *
     * @param primaryRupSet the primary rupture set
     * @param primaryName display name for the primary
     * @param compRupSet the comparison rupture set
     * @param compName display name for the comparison
     * @return a mocked ReportMetadata
     */
    @SuppressWarnings("unchecked")
    private ReportMetadata makeMeta(
            FaultSystemRupSet primaryRupSet,
            String primaryName,
            FaultSystemRupSet compRupSet,
            String compName)
            throws Exception {
        RupSetMetadata primaryMeta = mock(RupSetMetadata.class);
        setField(primaryMeta, "name", primaryName);
        setField(primaryMeta, "rupSet", primaryRupSet);

        RupSetMetadata compMeta = mock(RupSetMetadata.class);
        setField(compMeta, "name", compName);
        setField(compMeta, "rupSet", compRupSet);

        ReportMetadata meta = mock(ReportMetadata.class);
        when(meta.hasComparison()).thenReturn(true);
        setField(meta, "primary", primaryMeta);
        setField(meta, "comparison", compMeta);

        return meta;
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
        // 1 exclusive, 1 joint
        assertTrue("Expected 1 exclusive in: " + crustalRow, crustalRow.contains("| 1 | 1 |"));

        String hikurangiRow = lines.get(3);
        assertTrue("Expected HIKURANGI in: " + hikurangiRow, hikurangiRow.contains("HIKURANGI"));

        // Total row: 2 exclusive (1 CRUSTAL + 1 HIKURANGI), 1 joint (deduplicated)
        String totalRow = lines.get(4);
        assertTrue("Expected Total in: " + totalRow, totalRow.contains("**Total**"));
        assertTrue("Expected 2 exclusive total in: " + totalRow, totalRow.contains("**2**"));
        assertTrue("Expected 1 joint total in: " + totalRow, totalRow.contains("**1**"));
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
        String totalRow = lines.get(3);
        assertTrue(totalRow.contains("**Total**"));
        assertTrue(totalRow.contains("**0**"));
    }

    /** Tests that {@link PartitionSummaryTable#computeStats} correctly tallies partition counts. */
    @SuppressWarnings("unchecked")
    @Test
    public void testComputeStats() {
        GeoJSONFaultSection s0 = makeSection(0, PartitionPredicate.CRUSTAL);
        GeoJSONFaultSection s1 = makeSection(1, PartitionPredicate.CRUSTAL);
        GeoJSONFaultSection s2 = makeSection(2, PartitionPredicate.HIKURANGI);

        List sections = Arrays.asList(s0, s1, s2);

        FaultSystemRupSet rupSet = mock(FaultSystemRupSet.class);
        when(rupSet.getFaultSectionDataList()).thenReturn(sections);
        when(rupSet.getNumRuptures()).thenReturn(3);

        when(rupSet.getSectionsIndicesForRup(0)).thenReturn(Arrays.asList(0, 1));
        when(rupSet.getFaultSectionData(0)).thenReturn(s0);
        when(rupSet.getFaultSectionData(1)).thenReturn(s1);

        when(rupSet.getSectionsIndicesForRup(1)).thenReturn(Arrays.asList(2));
        when(rupSet.getFaultSectionData(2)).thenReturn(s2);

        // Shared rupture across both partitions
        when(rupSet.getSectionsIndicesForRup(2)).thenReturn(Arrays.asList(1, 2));

        PartitionSummaryTable.PartitionStats stats = PartitionSummaryTable.computeStats(rupSet);

        assertEquals(2, (int) stats.sectionCounts.get(PartitionPredicate.CRUSTAL));
        assertEquals(1, (int) stats.sectionCounts.get(PartitionPredicate.HIKURANGI));
        assertEquals(1, (int) stats.exclusiveCounts.get(PartitionPredicate.CRUSTAL));
        assertEquals(1, (int) stats.exclusiveCounts.get(PartitionPredicate.HIKURANGI));
        assertEquals(1, (int) stats.sharedCounts.get(PartitionPredicate.CRUSTAL));
        assertEquals(1, (int) stats.sharedCounts.get(PartitionPredicate.HIKURANGI));
        assertEquals(1, stats.multiPartitionTotal);
        assertTrue(stats.allPartitions.contains(PartitionPredicate.CRUSTAL));
        assertTrue(stats.allPartitions.contains(PartitionPredicate.HIKURANGI));
    }

    /** Tests that comparison columns appear when a comparison rupture set is provided. */
    @SuppressWarnings("unchecked")
    @Test
    public void testComparisonTable() throws Exception {
        // Primary: 2 CRUSTAL sections, 1 HIKURANGI section, 1 exclusive crustal rup
        GeoJSONFaultSection s0 = makeSection(0, PartitionPredicate.CRUSTAL);
        GeoJSONFaultSection s1 = makeSection(1, PartitionPredicate.CRUSTAL);
        GeoJSONFaultSection s2 = makeSection(2, PartitionPredicate.HIKURANGI);

        List primarySections = Arrays.asList(s0, s1, s2);

        FaultSystemRupSet primaryRupSet = mock(FaultSystemRupSet.class);
        when(primaryRupSet.getFaultSectionDataList()).thenReturn(primarySections);
        when(primaryRupSet.getNumRuptures()).thenReturn(1);
        when(primaryRupSet.getSectionsIndicesForRup(0)).thenReturn(Arrays.asList(0, 1));
        when(primaryRupSet.getFaultSectionData(0)).thenReturn(s0);
        when(primaryRupSet.getFaultSectionData(1)).thenReturn(s1);

        // Comparison: 1 CRUSTAL section, 2 HIKURANGI sections, 1 exclusive hikurangi rup
        GeoJSONFaultSection c0 = makeSection(10, PartitionPredicate.CRUSTAL);
        GeoJSONFaultSection c1 = makeSection(11, PartitionPredicate.HIKURANGI);
        GeoJSONFaultSection c2 = makeSection(12, PartitionPredicate.HIKURANGI);

        List compSections = Arrays.asList(c0, c1, c2);

        FaultSystemRupSet compRupSet = mock(FaultSystemRupSet.class);
        when(compRupSet.getFaultSectionDataList()).thenReturn(compSections);
        when(compRupSet.getNumRuptures()).thenReturn(1);
        when(compRupSet.getSectionsIndicesForRup(0)).thenReturn(Arrays.asList(11, 12));
        when(compRupSet.getFaultSectionData(11)).thenReturn(c1);
        when(compRupSet.getFaultSectionData(12)).thenReturn(c2);

        ReportMetadata meta = makeMeta(primaryRupSet, "Model A", compRupSet, "Model B");

        PartitionSummaryTable table = new PartitionSummaryTable();
        List<String> lines = table.plot(primaryRupSet, null, meta, (File) null, null, null);

        // Header should contain both model names
        String header = lines.get(0);
        assertTrue("Header should contain Model A: " + header, header.contains("Model A"));
        assertTrue("Header should contain Model B: " + header, header.contains("Model B"));

        // Should have header + separator + 2 partition rows + multi-partition row = 5 lines
        assertEquals(5, lines.size());

        // CRUSTAL row: primary 1 exclusive, comp 0 exclusive, 0 joint each
        String crustalRow = lines.get(2);
        assertTrue("CRUSTAL row: " + crustalRow, crustalRow.contains("CRUSTAL"));
        assertTrue(
                "Primary 1 excl, comp 0: " + crustalRow, crustalRow.contains("| 1 | 0 | 0 | 0 |"));

        // HIKURANGI row: primary 0 exclusive, comp 1 exclusive, 0 joint each
        String hikurangiRow = lines.get(3);
        assertTrue("HIKURANGI row: " + hikurangiRow, hikurangiRow.contains("HIKURANGI"));
        assertTrue(
                "Primary 0 excl, comp 1: " + hikurangiRow,
                hikurangiRow.contains("| 0 | 1 | 0 | 0 |"));
    }

    /** Tests that computeCrustalFractions returns correct fractions for joint ruptures only. */
    @SuppressWarnings("unchecked")
    @Test
    public void testComputeCrustalFractions() {
        GeoJSONFaultSection s0 = makeSection(0, PartitionPredicate.CRUSTAL);
        GeoJSONFaultSection s1 = makeSection(1, PartitionPredicate.HIKURANGI);
        GeoJSONFaultSection s2 = makeSection(2, PartitionPredicate.CRUSTAL);

        List sections = Arrays.asList(s0, s1, s2);

        FaultSystemRupSet rupSet = mock(FaultSystemRupSet.class);
        when(rupSet.getFaultSectionDataList()).thenReturn(sections);
        when(rupSet.getNumRuptures()).thenReturn(2);

        // Rup 0: exclusive CRUSTAL — should be skipped
        when(rupSet.getSectionsIndicesForRup(0)).thenReturn(Arrays.asList(0, 2));
        when(rupSet.getFaultSectionData(0)).thenReturn(s0);
        when(rupSet.getFaultSectionData(2)).thenReturn(s2);

        // Rup 1: joint CRUSTAL+HIKURANGI (sections 0, 1)
        when(rupSet.getSectionsIndicesForRup(1)).thenReturn(Arrays.asList(0, 1));
        when(rupSet.getFaultSectionData(1)).thenReturn(s1);

        // Section 0 (crustal): area 300, Section 1 (hikurangi): area 700
        when(rupSet.getAreaForSection(0)).thenReturn(300.0);
        when(rupSet.getAreaForSection(1)).thenReturn(700.0);
        when(rupSet.getAreaForSection(2)).thenReturn(500.0);

        List<Double> fractions = PartitionSummaryTable.computeCrustalFractions(rupSet);

        assertEquals("Only joint ruptures should be included", 1, fractions.size());
        assertEquals(0.3, fractions.get(0), 1e-9);
    }

    /** Tests that binFractions correctly bins fractions into the histogram. */
    @Test
    public void testBinFractions() {
        List<Double> fractions = Arrays.asList(0.1, 0.12, 0.9, 0.5);
        EvenlyDiscretizedFunc func = PartitionSummaryTable.binFractions(fractions);

        assertEquals(PartitionSummaryTable.NUM_BINS, func.size());

        // 0.1 and 0.12 should both land in bin centered at 0.125 (bin index 2)
        assertEquals(2.0, func.getY(2), 1e-9);

        // 0.5 should land in bin centered at 0.525 (bin index 10)
        assertEquals(1.0, func.getY(10), 1e-9);

        // 0.9 should land in bin centered at 0.925 (bin index 18)
        assertEquals(1.0, func.getY(18), 1e-9);
    }

    /** Tests that plot includes histogram image when joint ruptures have nonzero area. */
    @SuppressWarnings("unchecked")
    @Test
    public void testPlotIncludesHistogram() throws Exception {
        GeoJSONFaultSection s0 = makeSection(0, PartitionPredicate.CRUSTAL);
        GeoJSONFaultSection s1 = makeSection(1, PartitionPredicate.HIKURANGI);

        List sections = Arrays.asList(s0, s1);

        FaultSystemRupSet rupSet = mock(FaultSystemRupSet.class);
        when(rupSet.getFaultSectionDataList()).thenReturn(sections);
        when(rupSet.getNumRuptures()).thenReturn(1);

        // One joint rupture
        when(rupSet.getSectionsIndicesForRup(0)).thenReturn(Arrays.asList(0, 1));
        when(rupSet.getFaultSectionData(0)).thenReturn(s0);
        when(rupSet.getFaultSectionData(1)).thenReturn(s1);
        when(rupSet.getAreaForSection(0)).thenReturn(400.0);
        when(rupSet.getAreaForSection(1)).thenReturn(600.0);

        File tempDir =
                new File(System.getProperty("java.io.tmpdir"), "pst_test_" + System.nanoTime());
        tempDir.mkdirs();

        try {
            PartitionSummaryTable table = new PartitionSummaryTable();
            List<String> lines =
                    table.plot(rupSet, null, (ReportMetadata) null, tempDir, "resources", null);

            String allText = String.join("\n", lines);
            assertTrue("Should contain table", allText.contains("Partition"));
            assertTrue("Should contain histogram image", allText.contains("joint_area_ratio.png"));
        } finally {
            // Clean up temp files
            for (File f : tempDir.listFiles()) {
                f.delete();
            }
            tempDir.delete();
        }
    }

    /**
     * Tests that partitions appearing in only one of the two rupture sets still show up with zeros
     * for the other.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testComparisonWithDifferentPartitions() throws Exception {
        // Primary: CRUSTAL + HIKURANGI
        GeoJSONFaultSection s0 = makeSection(0, PartitionPredicate.CRUSTAL);
        GeoJSONFaultSection s1 = makeSection(1, PartitionPredicate.HIKURANGI);

        List primarySections = Arrays.asList(s0, s1);

        FaultSystemRupSet primaryRupSet = mock(FaultSystemRupSet.class);
        when(primaryRupSet.getFaultSectionDataList()).thenReturn(primarySections);
        when(primaryRupSet.getNumRuptures()).thenReturn(0);

        // Comparison: CRUSTAL + PUYSEGUR
        GeoJSONFaultSection c0 = makeSection(10, PartitionPredicate.CRUSTAL);
        GeoJSONFaultSection c1 = makeSection(11, PartitionPredicate.PUYSEGUR);

        List compSections = Arrays.asList(c0, c1);

        FaultSystemRupSet compRupSet = mock(FaultSystemRupSet.class);
        when(compRupSet.getFaultSectionDataList()).thenReturn(compSections);
        when(compRupSet.getNumRuptures()).thenReturn(0);

        ReportMetadata meta = makeMeta(primaryRupSet, "Primary", compRupSet, "Comparison");

        PartitionSummaryTable table = new PartitionSummaryTable();
        List<String> lines = table.plot(primaryRupSet, null, meta, (File) null, null, null);

        // Should have header + separator + 3 partition rows + multi-partition row = 6 lines
        assertEquals(6, lines.size());

        // All three partitions should appear
        String allText = String.join("\n", lines);
        assertTrue("Should contain CRUSTAL", allText.contains("CRUSTAL"));
        assertTrue("Should contain HIKURANGI", allText.contains("HIKURANGI"));
        assertTrue("Should contain PUYSEGUR", allText.contains("PUYSEGUR"));

        // HIKURANGI: only in primary, all counts 0 (no ruptures)
        String hikurangiRow =
                lines.stream().filter(l -> l.contains("HIKURANGI")).findFirst().orElseThrow();
        assertTrue(
                "HIKURANGI all zeros: " + hikurangiRow, hikurangiRow.contains("| 0 | 0 | 0 | 0 |"));

        // PUYSEGUR: only in comparison, all counts 0 (no ruptures)
        String puysegurRow =
                lines.stream().filter(l -> l.contains("PUYSEGUR")).findFirst().orElseThrow();
        assertTrue("PUYSEGUR all zeros: " + puysegurRow, puysegurRow.contains("| 0 | 0 | 0 | 0 |"));
    }
}

package nz.cri.gns.NZSHM22.opensha.ruptures.experimental;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionPredicate;
import nz.cri.gns.NZSHM22.opensha.ruptures.FaultSectionProperties;
import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;

public class MixedRuptureSetBuilderTest {

    @Test
    public void testSingleCrustalSection() {
        MixedRuptureSetBuilder builder = mockBuilder(new MockRupture(mockCrustalCluster(30)));
        double[] actual = builder.buildLengths();

        assertArrayEquals(new double[] {(30) * 1000}, actual, 0.0000001);
    }

    @Test
    public void testSingleCrustalCluster() {
        MixedRuptureSetBuilder builder =
                mockBuilder(new MockRupture(mockCrustalCluster(10, 20, 30)));
        double[] actual = builder.buildLengths();

        assertArrayEquals(new double[] {(10 + 20 + 30) * 1000}, actual, 0.0000001);
    }

    @Test
    public void testCrustalClusters() {
        MixedRuptureSetBuilder builder =
                mockBuilder(
                        new MockRupture(
                                mockCrustalCluster(10, 20, 30), mockCrustalCluster(40, 50, 60)));
        double[] actual = builder.buildLengths();

        assertArrayEquals(new double[] {(10 + 20 + 30 + 40 + 50 + 60) * 1000}, actual, 0.0000001);
    }

    @Test
    public void testSingleDownDipSection() {
        MixedRuptureSetBuilder builder =
                mockBuilder(new MockRupture(mockDownDipCluster(mockDownDipRow(0, 10))));
        double[] actual = builder.buildLengths();

        assertArrayEquals(new double[] {(10) * 1000}, actual, 0.0000001);
    }

    @Test
    public void testSingleDownDipCluster() {
        MixedRuptureSetBuilder builder =
                mockBuilder(
                        new MockRupture(
                                mockDownDipCluster(
                                        mockDownDipRow(1, 30, 40),
                                        mockDownDipRow(0, 10, 20), // this is the top row
                                        mockDownDipRow(2, 50, 60))));
        double[] actual = builder.buildLengths();

        assertArrayEquals(new double[] {(10 + 20) * 1000}, actual, 0.0000001);
    }

    @Test
    public void testUnevenDownDipCluster() {
        MixedRuptureSetBuilder builder =
                mockBuilder(
                        new MockRupture(
                                mockDownDipCluster(
                                        mockDownDipRow(1, 30, 40, 50),
                                        mockDownDipRow(0, 10, 20), // this is the top row
                                        mockDownDipRow(2, 50, 60))));
        double[] actual = builder.buildLengths();

        assertArrayEquals(new double[] {(10 + 20) * 1000}, actual, 0.0000001);
    }

    @Test
    public void testJointRupture() {
        MixedRuptureSetBuilder builder =
                mockBuilder(
                        new MockRupture(
                                mockCrustalCluster(70, 80, 90),
                                mockDownDipCluster(
                                        mockDownDipRow(1, 30, 40),
                                        mockDownDipRow(0, 10, 20), // this is the top row
                                        mockDownDipRow(2, 50, 60))));
        double[] actual = builder.buildLengths();

        assertArrayEquals(new double[] {(70 + 80 + 90 + 10 + 20) * 1000}, actual, 0.0000001);
    }

    @Test
    public void testMultipleRupture() {
        MixedRuptureSetBuilder builder =
                mockBuilder(
                        new MockRupture(
                                mockCrustalCluster(70, 80, 90),
                                mockDownDipCluster(
                                        mockDownDipRow(1, 30, 40),
                                        mockDownDipRow(0, 10, 20), // this is the top row
                                        mockDownDipRow(2, 50, 60))),
                        new MockRupture(mockCrustalCluster(70, 80, 90)),
                        new MockRupture(
                                mockDownDipCluster(
                                        mockDownDipRow(1, 30, 40),
                                        mockDownDipRow(0, 10, 20), // this is the top row
                                        mockDownDipRow(2, 50, 60))));
        double[] actual = builder.buildLengths();

        assertArrayEquals(
                new double[] {
                    (70 + 80 + 90 + 10 + 20) * 1000, (70 + 80 + 90) * 1000, (10 + 20) * 1000
                },
                actual,
                0.0000001);
    }

    static class MockMixedRuptureSetBuilder extends MixedRuptureSetBuilder {
        public MockMixedRuptureSetBuilder(List<ClusterRupture> ruptures) {
            this.ruptures = ruptures;
        }
    }

    static MixedRuptureSetBuilder mockBuilder(ClusterRupture... ruptures) {
        return new MockMixedRuptureSetBuilder(ImmutableList.copyOf(ruptures));
    }

    static class MockRupture extends ClusterRupture {

        static ImmutableList<Jump> makeJumps(FaultSubsectionCluster[] clusters) {
            List<Jump> jumps = new ArrayList<>();
            for (int i = 0; i < clusters.length - 1; i++) {
                jumps.add(mock(Jump.class));
            }
            return ImmutableList.copyOf(jumps);
        }

        public MockRupture(FaultSubsectionCluster... clusters) {
            super(
                    clusters,
                    makeJumps(clusters),
                    ImmutableMap.of(),
                    clusters[0].unique,
                    clusters[0].unique,
                    true);
        }
    }

    static int sectionId = 0;

    public static FaultSubsectionCluster mockCrustalCluster(double... lengths) {
        List<FaultSection> subSects = new ArrayList<>();
        for (double length : lengths) {
            GeoJSONFaultSection s = mock(GeoJSONFaultSection.class);
            when(s.getSectionId()).thenReturn(sectionId++);
            when(s.getTraceLength()).thenReturn(length);
            when(s.getProperty(FaultSectionProperties.PARTITION))
                    .thenReturn(PartitionPredicate.CRUSTAL.name());
            subSects.add(s);
        }
        return new FaultSubsectionCluster(subSects);
    }

    public static List<FaultSection> mockDownDipRow(int rowIndex, double... lengths) {
        List<FaultSection> subSects = new ArrayList<>();
        for (double length : lengths) {
            GeoJSONFaultSection s = mock(GeoJSONFaultSection.class);
            when(s.getSectionId()).thenReturn(sectionId++);
            when(s.getProperty(FaultSectionProperties.PARTITION))
                    .thenReturn(PartitionPredicate.HIKURANGI.name());
            when(s.getProperty(FaultSectionProperties.ROW_INDEX)).thenReturn(rowIndex);
            when(s.getTraceLength()).thenReturn(length);
            subSects.add(s);
        }
        return subSects;
    }

    public static FaultSubsectionCluster mockDownDipCluster(List<FaultSection>... rows) {
        List<FaultSection> subSects = new ArrayList<>();
        for (List<FaultSection> row : rows) {
            subSects.addAll(row);
        }
        return new FaultSubsectionCluster(subSects);
    }
}

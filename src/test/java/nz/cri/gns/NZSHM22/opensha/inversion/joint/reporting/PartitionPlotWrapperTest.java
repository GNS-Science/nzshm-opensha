package nz.cri.gns.NZSHM22.opensha.inversion.joint.reporting;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.io.IOException;
import java.util.List;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionMfds;
import nz.cri.gns.NZSHM22.util.NZSHM22_ReportPageGen;
import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractRupSetPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportPageGen.PlotLevel;
import org.opensha.sha.earthquake.faultSysSolution.reports.SolidFillPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.SectBValuePlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.SlipRatePlots;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.SolMFDPlot;

/** Tests for {@link PartitionPlotWrapper}. */
public class PartitionPlotWrapperTest {

    /** A minimal plot that records the configuration it was given by the wrapper. */
    static class RecordingPlot extends AbstractRupSetPlot implements SolidFillPlot {
        List<String> result = List.of("inner plot output");
        Boolean fillSurfaces = null;
        String seenSubHeading = null;

        @Override
        public void setFillSurfaces(boolean fillSurfaces) {
            this.fillSurfaces = fillSurfaces;
        }

        @Override
        public List<String> plot(
                FaultSystemRupSet rupSet,
                FaultSystemSolution sol,
                ReportMetadata meta,
                File resourcesDir,
                String relPathToResources,
                String topLink) {
            seenSubHeading = getSubHeading();
            return result;
        }

        @Override
        public List<Class<? extends org.opensha.commons.util.modules.OpenSHA_Module>>
                getRequiredModules() {
            return List.of();
        }

        @Override
        public String getName() {
            return "Recording Plot";
        }
    }

    @Test
    public void testPassesThroughWithoutPartitionMfds() throws IOException {
        RecordingPlot inner = new RecordingPlot();
        PartitionPlotWrapper wrapper = new PartitionPlotWrapper(inner);

        FaultSystemRupSet rupSet = mock(FaultSystemRupSet.class);
        when(rupSet.getModule(PartitionMfds.class)).thenReturn(null);

        wrapper.setSubHeading("###");
        List<String> result =
                wrapper.plot(
                        rupSet, null, (ReportMetadata) null, new File("."), "resources", "top");

        assertEquals(List.of("inner plot output"), result);
        assertEquals("###", inner.seenSubHeading);
    }

    @Test
    public void testPassesThroughNullInnerResult() throws IOException {
        RecordingPlot inner = new RecordingPlot();
        inner.result = null;
        PartitionPlotWrapper wrapper = new PartitionPlotWrapper(inner);

        FaultSystemRupSet rupSet = mock(FaultSystemRupSet.class);
        when(rupSet.getModule(PartitionMfds.class)).thenReturn(new PartitionMfds());

        assertNull(
                wrapper.plot(
                        rupSet, null, (ReportMetadata) null, new File("."), "resources", "top"));
    }

    @Test
    public void testForwardsConfigurationToInner() {
        RecordingPlot inner = new RecordingPlot();
        PartitionPlotWrapper wrapper = new PartitionPlotWrapper(inner);

        wrapper.setPlotLevel(PlotLevel.LIGHT);
        wrapper.setNumThreads(3);
        wrapper.setSubHeading("####");
        wrapper.setFillSurfaces(true);

        assertEquals(PlotLevel.LIGHT, inner.getPlotLevel());
        assertEquals(3, inner.getNumThreads());
        assertEquals(Boolean.TRUE, inner.fillSurfaces);
        assertEquals(List.of(), wrapper.getRequiredModules());
        assertEquals("Recording Plot Split by Partition", wrapper.getName());
        assertSame(inner, wrapper.getInner());
    }

    @Test
    public void testSplitByPartitionWrapsSelectedPlots() {
        assertTrue(
                NZSHM22_ReportPageGen.splitByPartition(new SlipRatePlots())
                        instanceof PartitionPlotWrapper);
        assertTrue(
                NZSHM22_ReportPageGen.splitByPartition(new SolMFDPlot())
                        instanceof PartitionPlotWrapper);
        assertFalse(
                NZSHM22_ReportPageGen.splitByPartition(new SectBValuePlot())
                        instanceof PartitionPlotWrapper);
    }
}

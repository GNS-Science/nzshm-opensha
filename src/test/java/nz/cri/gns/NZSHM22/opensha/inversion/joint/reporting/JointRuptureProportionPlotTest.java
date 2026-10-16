package nz.cri.gns.NZSHM22.opensha.inversion.joint.reporting;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.util.List;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionPredicate;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.constraint.FilteredFaultSystemRupSetTest;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.constraints.FilteredFaultSystemRupSet;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;

/** Tests for {@link JointRuptureProportionPlot}. */
public class JointRuptureProportionPlotTest {

    static final double DELTA = 1e-9;

    @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

    /** Returns null when no solution is provided. */
    @Test
    public void testNullSolution() throws Exception {
        FaultSystemRupSet rupSet = mock(FaultSystemRupSet.class);
        assertNull(
                new JointRuptureProportionPlot()
                        .plot(rupSet, null, (ReportMetadata) null, null, null, null));
    }

    /** Rates per bin sum to the total rate of the bin and ignore zero rates. */
    @Test
    public void testJointRates() {
        IncrementalMagFreqDist template = new IncrementalMagFreqDist(6.05, 10, 0.1);
        double[] mags = {6.05, 6.05, 6.05, 6.25, 6.35};
        double[] rates = {3, 1, 5, 2, 0};
        boolean[] isJoint = {false, true, true, false, true};

        IncrementalMagFreqDist[] result =
                JointRuptureProportionPlot.jointRates(mags, rates, isJoint, template);
        IncrementalMagFreqDist joint = result[0];
        IncrementalMagFreqDist nonJoint = result[1];

        assertEquals(6, joint.getY(0), DELTA);
        assertEquals(3, nonJoint.getY(0), DELTA);
        // only a non-joint rupture
        assertEquals(0, joint.getY(2), DELTA);
        assertEquals(2, nonJoint.getY(2), DELTA);
        // only a zero rate rupture
        assertEquals(0, joint.getY(3), DELTA);
        assertEquals(0, nonJoint.getY(3), DELTA);
        // empty bin
        assertEquals(0, joint.getY(1), DELTA);
        assertEquals(0, nonJoint.getY(1), DELTA);
    }

    /** Joint ruptures are detected in the full rupture set and in a partition rupture set. */
    @Test
    public void testJointRuptures() throws Exception {
        FaultSystemRupSet original = FilteredFaultSystemRupSetTest.makeRupSet();
        assertArrayEquals(
                new boolean[] {false, false, true},
                JointRuptureProportionPlot.jointRuptures(original));

        FilteredFaultSystemRupSet crustal =
                FilteredFaultSystemRupSet.forIntPredicate(
                        original,
                        PartitionPredicate.CRUSTAL.getPredicate(original),
                        ScalingRelationships.SHAW_2009_MOD);
        // crustal rupture and the crustal part of the joint rupture
        assertArrayEquals(
                new boolean[] {false, true},
                JointRuptureProportionPlot.jointRuptures(crustal, original));

        assertEquals(
                List.of(PartitionPredicate.CRUSTAL, PartitionPredicate.HIKURANGI),
                JointRuptureProportionPlot.presentPartitions(original));
    }

    /** Writes the total plot; without a ConfigModule no partition plots are made. */
    @Test
    public void testPlotProducesOutput() throws Exception {
        FaultSystemRupSet rupSet = FilteredFaultSystemRupSetTest.makeRupSet();
        FaultSystemSolution sol = new FaultSystemSolution(rupSet, new double[] {1e-3, 1e-4, 1e-5});

        File resourcesDir = tempFolder.newFolder("resources");
        List<String> lines =
                new JointRuptureProportionPlot()
                        .plot(rupSet, sol, (ReportMetadata) null, resourcesDir, "resources", "");

        String allText = String.join("\n", lines);
        assertTrue(allText.contains("resources/joint_proportion_mfd.png"));
        assertFalse(allText.contains("joint_proportion_mfd_CRUSTAL"));
        assertTrue(new File(resourcesDir, "joint_proportion_mfd.png").exists());
        assertTrue(allText.contains("resources/joint_proportion_mfd_shared_y.png"));
        assertTrue(new File(resourcesDir, "joint_proportion_mfd_shared_y.png").exists());
    }
}

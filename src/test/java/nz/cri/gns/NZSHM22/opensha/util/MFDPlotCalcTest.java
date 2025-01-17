package nz.cri.gns.NZSHM22.opensha.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.inversion.NZSHM22_CrustalInversionRunner;
import nz.cri.gns.NZSHM22.util.MFDPlotCalc;
import org.dom4j.DocumentException;
import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;

public class MFDPlotCalcTest {
    public static FaultSystemSolution loadSolution() throws DocumentException, IOException {

        FaultSystemRupSet rupSet =
                TestHelpers.makeRupSet(
                        NZSHM22_FaultModels.CFM_1_0A_DOM_SANSTVZ,
                        ScalingRelationships.SHAW_2009_MOD);
        ParameterRunner parameterRunner = new ParameterRunner(Parameters.NZSHM22.INVERSION_CRUSTAL);
        NZSHM22_CrustalInversionRunner runner = new NZSHM22_CrustalInversionRunner();
        parameterRunner.setUpCrustalInversionRunner(runner);
        runner.setRuptureSetArchiveInput(TestHelpers.archiveInput(rupSet));
        runner.setIterationCompletionCriteria(1);
        runner.setSelectionIterations(3);
        runner.setRepeatable(true);
        runner.setInversionAveraging(false);
        return runner.runInversion();
    }

    protected void changeParentFromTo(FaultSystemSolution solution, int from, int to) {
        for (FaultSection section : solution.getRupSet().getFaultSectionDataList()) {
            if (section.getParentSectionId() == from) {
                section.setParentSectionId(to);
            }
        }
    }

    @Test
    public void calcNucleationMFD_forParentSectTest()
            throws DocumentException, URISyntaxException, IOException {
        FaultSystemSolution solution = loadSolution();

        // preconditions: the data we're using is valid
        SummedMagFreqDist five = solution.calcNucleationMFD_forParentSect(5, 0, 9, 10);
        SummedMagFreqDist nine = solution.calcNucleationMFD_forParentSect(9, 0, 9, 10);
        assertTrue("mfd is not zero", five.getMaxY() > 0);
        assertTrue("mfd is not zero", nine.getMaxY() > 0);

        // This test verifies that the new method calcNucleationMFD_forParentSect which takes a set
        // of parent ids
        // is equivalent to the old method which takes a single parent id.

        SummedMagFreqDist actual =
                MFDPlotCalc.calcNucleationMFD_forParentSect(
                        solution, Sets.newHashSet(5, 9), 0, 9, 10);

        assertTrue("mfd is not zero", actual.getMaxY() > 0);

        solution = loadSolution(); // needs to be done to reset caches
        // merge parent faults 5 and 9 into one fault
        changeParentFromTo(solution, 9, 5);

        SummedMagFreqDist expected = solution.calcNucleationMFD_forParentSect(5, 0, 9, 10);

        List<Point2D> expectedValues = Lists.newArrayList(expected.getPointsIterator());
        List<Point2D> actualValues = Lists.newArrayList(actual.getPointsIterator());

        assertEquals(expectedValues, actualValues);
    }

    @Test
    public void calcParticipationMFD_forParentSectTest()
            throws DocumentException, URISyntaxException, IOException {
        FaultSystemSolution solution = loadSolution();

        // preconditions: the data we're using is valid
        IncrementalMagFreqDist five = solution.calcParticipationMFD_forParentSect(5, 0, 9, 10);
        IncrementalMagFreqDist nine = solution.calcParticipationMFD_forParentSect(9, 0, 9, 10);
        assertTrue("mfd is not zero", five.getMaxY() > 0);
        assertTrue("mfd is not zero", nine.getMaxY() > 0);

        // This test verifies that the new method calcParticipationMFD_forParentSect which takes a
        // set of parent ids
        // is equivalent to the old method which takes a single parent id.

        IncrementalMagFreqDist actual =
                MFDPlotCalc.calcParticipationMFD_forParentSect(
                        solution, Sets.newHashSet(5, 9), 0, 9, 10);

        solution = loadSolution(); // needs to be done to reset caches
        // merge parent faults 5 and 9 into one fault
        changeParentFromTo(solution, 9, 5);

        IncrementalMagFreqDist expected = solution.calcParticipationMFD_forParentSect(5, 0, 9, 10);

        List<Point2D> expectedValues = Lists.newArrayList(expected.getPointsIterator());
        List<Point2D> actualValues = Lists.newArrayList(actual.getPointsIterator());

        assertEquals(expectedValues, actualValues);
    }
}

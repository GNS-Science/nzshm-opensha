package nz.cri.gns.NZSHM22.opensha.inversion;

import static org.junit.Assert.*;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.dom4j.DocumentException;
import org.junit.Test;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;

public class NZSHM22_InversionFaultSystemSolutionTest {

    protected NZSHM22_InversionFaultSystemSolution loadSolution() throws URISyntaxException, DocumentException, IOException {
        URL alpineVernonRupturesUrl = Thread.currentThread().getContextClassLoader().getResource("AlpineVernonInversionSolution.zip");
        System.out.println(alpineVernonRupturesUrl);
        return NZSHM22_InversionFaultSystemSolution.fromFile(new File(alpineVernonRupturesUrl.toURI()));
    }

    protected void changeParentFromTo(NZSHM22_InversionFaultSystemSolution solution, int from, int to) {
        for (FaultSection section : solution.getRupSet().getFaultSectionDataList()) {
            if (section.getParentSectionId() == from) {
                section.setParentSectionId(to);
            }
        }
    }

    @Test
    public void calcNucleationMFD_forParentSectTest() throws DocumentException, URISyntaxException, IOException {
        NZSHM22_InversionFaultSystemSolution solution = loadSolution();

        // This test verifies that the new method calcNucleationMFD_forParentSect which takes a set of parent ids
        // is equivalent to the old method which takes a single parent id.

        SummedMagFreqDist actual = solution.calcNucleationMFD_forParentSect(Sets.newHashSet(23, 24), 0, 9, 10);

        solution = loadSolution(); // needs to be done to reset caches
        // merge parent faults 23 and 24 into one fault
        changeParentFromTo(solution, 24, 23);

        SummedMagFreqDist expected = solution.calcNucleationMFD_forParentSect(23, 0, 9, 10);

        List<Point2D> expectedValues = Lists.newArrayList(expected.getPointsIterator());
        List<Point2D> actualValues = Lists.newArrayList(actual.getPointsIterator());

        assertEquals(expectedValues, actualValues);
    }

    @Test
    public void calcParticipationMFD_forParentSectTest() throws DocumentException, URISyntaxException, IOException {
        NZSHM22_InversionFaultSystemSolution solution = loadSolution();

        // This test verifies that the new method calcParticipationMFD_forParentSect which takes a set of parent ids
        // is equivalent to the old method which takes a single parent id.

        IncrementalMagFreqDist actual = solution.calcParticipationMFD_forParentSect(Sets.newHashSet(23, 24), 0, 9, 10);

        solution = loadSolution(); // needs to be done to reset caches
        // merge parent faults 23 and 24 into one fault
        changeParentFromTo(solution, 24, 23);

        IncrementalMagFreqDist expected = solution.calcParticipationMFD_forParentSect(23, 0, 9,10);

        List<Point2D> expectedValues = Lists.newArrayList(expected.getPointsIterator());
        List<Point2D> actualValues = Lists.newArrayList(actual.getPointsIterator());

        assertEquals(expectedValues, actualValues);
    }
}

package nz.cri.gns.NZSHM22.opensha.timeDependent;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.opensha.commons.geo.Location;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;

public class MREDataTest {

    Location a = new Location(1, 1);
    Location b = new Location(2, 2);
    Location c = new Location(3, 3);
    Location d = new Location(4, 4);
    Location e = new Location(5, 5);

    private static FaultSection makeSection(String parentName, Location... locations) {
        FaultSectionPrefData result = new FaultSectionPrefData();
        result.setParentSectionName(parentName);
        FaultTrace trace = new FaultTrace(parentName + "child");
        trace.addAll(Arrays.asList(locations));
        result.setFaultTrace(trace);
        return result;
    }

    private static FaultSystemSolution mockSolution(FaultSection... sections) {
        FaultSystemRupSet rupSet = mock(FaultSystemRupSet.class);
        List s = Arrays.asList(sections);
        when(rupSet.getFaultSectionDataList()).thenReturn(s);
        FaultSystemSolution solution = mock(FaultSystemSolution.class);
        when(solution.getRupSet()).thenReturn(rupSet);
        return solution;
    }

    @Test
    public void testReconstructFaults() {
        FaultSection sectionA = makeSection("parent1", a, b);
        FaultSection sectionB = makeSection("parent1", b, c);
        FaultSection sectionC = makeSection("other parent", c, d);
        FaultSystemSolution solution = mockSolution(sectionA, sectionB, sectionC);

        Map<String, List<FaultSection>> actual = MREData.reconstructFaults(solution);
        assertEquals(2, actual.size());
        assertEquals(List.of(sectionA, sectionB), actual.get("parent1"));
        assertEquals(List.of(sectionC), actual.get("other parent"));
    }

    @Test(expected = IllegalStateException.class)
    public void testReconstructFaultsOutOfSequence() {
        FaultSection sectionA = makeSection("parent1", a, b);
        FaultSection sectionB = makeSection("parent1", e, c);
        FaultSystemSolution solution = mockSolution(sectionA, sectionB);

        Map<String, List<FaultSection>> actual = MREData.reconstructFaults(solution);
    }

    @Test
    public void testNearestSection() {
        FaultSection sectionA = makeSection("p", a, b);
        FaultSection sectionB = makeSection("p", b, c);
        FaultSection sectionC = makeSection("p", c, d);
        List<FaultSection> fault = List.of(sectionA, sectionB, sectionC);

        assertEquals(0, MREData.nearestSection(fault, a));
        assertEquals(2, MREData.nearestSection(fault, d));
        assertEquals(0, MREData.nearestSection(fault, new Location(0, 0)));
        assertEquals(0, MREData.nearestSection(fault, new Location(1, 2)));
        assertEquals(1, MREData.nearestSection(fault, new Location(2, 3)));
    }

    @Test(expected = IllegalStateException.class)
    public void testNearestSectionAmbiguous() {
        FaultSection sectionA = makeSection("p", a, b);
        FaultSection sectionB = makeSection("p", b, c);
        FaultSection sectionC = makeSection("p", c, d);
        List<FaultSection> fault = List.of(sectionA, sectionB, sectionC);

        // a point on two sections
        assertEquals(0, MREData.nearestSection(fault, b));
    }

    @Test
    public void testApply() throws IOException {
        Location a = new Location(-42.96422202713509, 171.00793421846896);
        Location b = new Location(-42.8914, 171.1464);
        FaultSection section = makeSection("Alpine: Jacksons to Kaniere", a, b);
        FaultSystemSolution solution = mockSolution(section);

        assertEquals(Long.MIN_VALUE, section.getDateOfLastEvent());
        MREData.CFM_1_1.apply(solution, 2022);
        assertEquals(-7984072800000L, section.getDateOfLastEvent());
    }
}

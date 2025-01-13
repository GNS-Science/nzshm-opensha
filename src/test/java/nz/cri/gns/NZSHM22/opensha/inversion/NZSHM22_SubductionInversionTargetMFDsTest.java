package nz.cri.gns.NZSHM22.opensha.inversion;

import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_LogicTreeBranch;
import nz.cri.gns.NZSHM22.opensha.util.TestHelpers;
import org.dom4j.DocumentException;
import org.junit.Test;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class NZSHM22_SubductionInversionTargetMFDsTest {

    public static NZSHM22_InversionFaultSystemRuptSet loadRupSet() throws URISyntaxException, DocumentException, IOException {
        FaultSystemRupSet rupSet = TestHelpers.createRupSet(
                NZSHM22_FaultModels.SBD_0_2_HKR_LR_30,
                ScalingRelationships.TMG_SUB_2017,
                List.of(
                        List.of(1,2,3,4,5,6,7,8,9),
                        List.of(4,5,6,10,11,12)
                ));
        NZSHM22_LogicTreeBranch ltb  = NZSHM22_LogicTreeBranch.subductionInversion();
        return NZSHM22_InversionFaultSystemRuptSet.fromExistingSubductionRuptureSet(rupSet, ltb);
    }

    public static List<Double> getPoints(EvenlyDiscretizedFunc func) {
        List<Double> result = new ArrayList<>();
        for (Point2D point : func) {
            result.add(point.getX());
            result.add(point.getY());
        }
        return result;
    }

    @Test
    public void testMFDConstraints() throws DocumentException, URISyntaxException, IOException {
        NZSHM22_InversionFaultSystemRuptSet ruptSet = loadRupSet();
        NZSHM22_SubductionInversionTargetMFDs mfds = new NZSHM22_SubductionInversionTargetMFDs(ruptSet);

        List<IncrementalMagFreqDist> actual = mfds.getMFD_Constraints();

        assertEquals(1, actual.size());
        IncrementalMagFreqDist actualConstraint = actual.get(0);

        assertEquals("targetOnFaultSupraSeisMFD", actualConstraint.getName());
        assertNull(actualConstraint.getRegion());
        System.out.println(getPoints(actualConstraint));
        assertEquals(List.of(5.05, 1.0E-20, 5.1499999999999995, 1.0E-20, 5.25, 1.0E-20, 5.35, 1.0E-20, 5.45, 1.0E-20, 5.55, 1.0E-20, 5.65, 1.0E-20, 5.75, 1.0E-20, 5.85, 1.0E-20, 5.95, 1.0E-20, 6.05, 1.0E-20, 6.15, 1.0E-20, 6.25, 1.0E-20, 6.35, 1.0E-20, 6.45, 1.0E-20, 6.55, 1.0E-20, 6.65, 1.0E-20, 6.75, 1.0E-20, 6.85, 1.0E-20, 6.95, 1.0E-20, 7.05, 9.896157257580686E-4, 7.15, 7.681863536901102E-4, 7.25, 5.963024420854573E-4, 7.35, 4.6287805130748323E-4, 7.45, 3.59307752678142E-4, 7.55, 2.7891160699874317E-4, 7.65, 0.0, 7.75, 0.0, 7.85, 0.0, 7.95, 0.0, 8.05, 0.0, 8.15, 0.0, 8.25, 0.0, 8.35, 0.0, 8.45, 0.0, 8.55, 0.0, 8.65, 0.0, 8.75, 0.0, 8.85, 0.0, 8.95, 0.0, 9.05, 0.0, 9.15, 0.0, 9.25, 0.0, 9.35, 0.0, 9.45, 0.0, 9.55, 0.0, 9.65, 0.0),
                getPoints(actualConstraint));
    }
}

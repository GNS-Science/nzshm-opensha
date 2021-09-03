package nz.cri.gns.NZSHM22.opensha.inversion;

import org.dom4j.DocumentException;
import org.junit.Test;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import scratch.UCERF3.inversion.InversionTargetMFDs;
import scratch.UCERF3.logicTree.U3LogicTreeBranch;
import scratch.UCERF3.utils.MFD_InversionConstraint;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class NZSHM22_SubductionInversionTargetMFDsTest {

    public static NZSHM22_InversionFaultSystemRuptSet loadRupSet() throws URISyntaxException, DocumentException, IOException {
        URL first100 = Thread.currentThread().getContextClassLoader().getResource("RupSet_Az_FM(CFM_0_9_SANSTVZ_D90)_mnSbS(2)_mnSSPP(2)_mxSSL(0.5)_mxFS(100)_mxAzCh(60.0)_mxCmAzCh(560.0)_mxJpDs(5.0)_mxTtAzCh(60.0)_thFc(0.2).zip");
        return NZSHM22_SubductionInversionRunner.loadRuptureSet(new File(first100.toURI()), U3LogicTreeBranch.DEFAULT);
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

        List<MFD_InversionConstraint> actual = (List<MFD_InversionConstraint>) mfds.getMFD_Constraints();

        assertEquals(1, actual.size());
        MFD_InversionConstraint actualConstraint = actual.get(0);

        assertEquals("targetOnFaultSupraSeisMFD", actualConstraint.getMagFreqDist().getName());
        assertNull(actualConstraint.getRegion());
        assertEquals(List.of(5.05, 1.0E-20, 5.1499999999999995, 1.0E-20, 5.25, 1.0E-20, 5.35, 1.0E-20, 5.45, 1.0E-20, 5.55, 1.0E-20, 5.65, 1.0E-20, 5.75, 1.0E-20, 5.85, 1.0E-20, 5.95, 1.0E-20, 6.05, 1.0E-20, 6.15, 1.0E-20, 6.25, 1.0E-20, 6.35, 1.0E-20, 6.45, 1.0E-20, 6.55, 1.0E-20, 6.65, 1.0E-20, 6.75, 1.0E-20, 6.85, 1.0E-20, 6.95, 1.0E-20, 7.05, 9.884813984399915E-4, 7.15, 7.673058353801398E-4, 7.25, 5.956189422862039E-4, 7.35, 4.6234748655909656E-4, 7.45, 3.58895903322022E-4, 7.55, 2.785919101235696E-4, 7.65, 2.1625616694949997E-4, 7.75, 1.6786822604772256E-4, 7.85, 1.3030722644311824E-4, 7.95, 1.0115060880235229E-4, 8.05, 7.85178684280625E-5, 8.15, 6.094926897111459E-5, 8.25, 4.731169429945435E-5, 8.35, 0.0, 8.45, 0.0, 8.55, 0.0, 8.65, 0.0, 8.75, 0.0, 8.85, 0.0, 8.95, 0.0, 9.05, 0.0, 9.15, 0.0, 9.25, 0.0, 9.35, 0.0, 9.45, 0.0, 9.55, 0.0, 9.65, 0.0),
                getPoints(actualConstraint.getMagFreqDist()));
    }
}

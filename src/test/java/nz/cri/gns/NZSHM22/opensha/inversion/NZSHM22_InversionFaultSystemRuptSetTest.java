package nz.cri.gns.NZSHM22.opensha.inversion;

import static org.junit.Assert.*;

import org.dom4j.DocumentException;
import org.junit.Test;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;

public class NZSHM22_InversionFaultSystemRuptSetTest {

    @Test
    public void recalcMagsTest() throws DocumentException, URISyntaxException, IOException {
        NZSHM22_InversionFaultSystemRuptSet rupSet =  (NZSHM22_InversionFaultSystemRuptSet) NZSHM22_InversionFaultSystemSolutionTest.loadSolution().getRupSet();

        // irgMags were calculated with SHAW_2009_MOD
        double[] origMags = Arrays.copyOf(rupSet.getMagForAllRups(),rupSet.getMagForAllRups().length);

        rupSet.recalcMags(ScalingRelationships.TMG_CRU_2017);
        assertNotEquals(origMags[0], rupSet.getMagForAllRups()[0], 0.00000001);

        rupSet.recalcMags(ScalingRelationships.SHAW_2009_MOD);
        assertArrayEquals(origMags, rupSet.getMagForAllRups(), 0.00000001);
    }
}

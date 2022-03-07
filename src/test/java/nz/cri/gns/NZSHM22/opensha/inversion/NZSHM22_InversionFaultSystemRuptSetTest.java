package nz.cri.gns.NZSHM22.opensha.inversion;

import static org.junit.Assert.*;

import nz.cri.gns.NZSHM22.opensha.calc.SimplifiedScalingRelationship;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_LogicTreeBranch;
import org.dom4j.DocumentException;
import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;

public class NZSHM22_InversionFaultSystemRuptSetTest {

    public FaultSystemRupSet modularRupSet() throws URISyntaxException, IOException {
        URL alpineVernonRupturesUrl = Thread.currentThread().getContextClassLoader().getResource("ModularAlpineVernonInversionSolution.zip");
        return NZSHM22_InversionFaultSystemRuptSet.loadCrustalRuptureSet(new File(alpineVernonRupturesUrl.toURI()), NZSHM22_LogicTreeBranch.crustalInversion());
    }

    @Test
    public void recalcMagsTest() throws DocumentException, URISyntaxException, IOException {
        FaultSystemRupSet rupSet =  modularRupSet();

        // orgMags were calculated with SimplifiedScalingRelationship: crustal, 4.0, 4.1
        double[] origMags = Arrays.copyOf(rupSet.getMagForAllRups(),rupSet.getMagForAllRups().length);

        rupSet = NZSHM22_InversionFaultSystemRuptSet.recalcMags(rupSet, ScalingRelationships.TMG_CRU_2017);
        assertNotEquals(origMags[0], rupSet.getMagForAllRups()[0], 0.00000001);

        // to recreate original values
        SimplifiedScalingRelationship scaling = new SimplifiedScalingRelationship();
        scaling.setupCrustal(4.0, 4.1);
        rupSet = NZSHM22_InversionFaultSystemRuptSet.recalcMags(rupSet, scaling);
        assertArrayEquals(origMags, rupSet.getMagForAllRups(), 0.00000001);
    }
}

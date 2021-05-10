package nz.cri.gns.NZSHM22.opensha.ruptures;

import static org.junit.Assert.*;


import com.google.common.collect.Sets;

import nz.cri.gns.NZSHM22.opensha.ruptures.NZSHM22_RuptureSetBuilder;

import org.dom4j.DocumentException;
import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import scratch.UCERF3.SlipAlongRuptureModelRupSet;
import scratch.UCERF3.utils.FaultSystemIO;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class NZSHM22_RuptureSetBuilder_IntegrationTest {

    public boolean hasRuptureWithFaults(Set<Integer> faults, SlipAlongRuptureModelRupSet rupSet) {
        for (ClusterRupture rupture : rupSet.getClusterRuptures()) {
            Set<Integer> parents = new HashSet<>();
            for (FaultSubsectionCluster cluster : rupture.getClustersIterable()) {
                parents.add(cluster.parentSectionID);
            }
            if (parents.equals(faults)) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void testRuptureSetSlipRateMethods() throws IOException, DocumentException {

    	//see TODO in NZSHM22_SlipEnabledRuptureSet
        SlipAlongRuptureModelRupSet ruptureSet =
                new NZSHM22_RuptureSetBuilder()
                        .setFaultModelFile(new File("src/integration/resources/KAIK2016.xml"))
                        .buildRuptureSet();
        
        assertEquals(667, ruptureSet.getClusterRuptures().size());
        assertEquals(667, ruptureSet.getAveSlipForAllRups().length);
        assertEquals(68, ruptureSet.getSlipRateForAllSections().length);
        assertEquals(68, ruptureSet.getSlipRateStdDevForAllSections().length);
        
        assertEquals("HopeConwayOS", ruptureSet.getFaultSectionData(11).getParentSectionName());
        assertEquals(new Float(0.0214), new Float(ruptureSet.getSlipRateForSection(11)));
        assertEquals(new Float(2.3), new Float(ruptureSet.getFaultSectionData(11).getOrigSlipRateStdDev()));
        assertEquals(new Float(2.3), new Float(ruptureSet.getSlipRateStdDevForSection(11)));
    }
    
    @Test
    public void testCantBuildKaikoura2016() throws IOException, DocumentException {

        Set<Integer> kaikouraFaults = Sets.newHashSet(95, 132, 136, 149, 162, 178, 189, 245, 310, 387, 400);

        SlipAlongRuptureModelRupSet ruptureSet =
                new NZSHM22_RuptureSetBuilder()
                        .setFaultModelFile(new File("src/integration/resources/KAIK2016.xml"))
                        .buildRuptureSet();

        //FaultSystemIO.writeRupSet(ruptureSet, new File("/tmp/testCantBuildKaikoura2016.zip"));        
        assertFalse(hasRuptureWithFaults(kaikouraFaults, ruptureSet));
    }

    @Test
    public void testAlpineVernon() throws IOException, DocumentException {
        Set<Integer> faults = Sets.newHashSet( 23, 24, 130, 50, 48, 46, 585);

        SlipAlongRuptureModelRupSet ruptureSet =
                new NZSHM22_RuptureSetBuilder()
                        .setFaultModelFile(new File("src/integration/resources/alpine-vernon.xml"))
                        .buildRuptureSet();

        //FaultSystemIO.writeRupSet(ruptureSet, new File("./tmp/testAlpineVernon.zip"));

        assertEquals(3101, ruptureSet.getClusterRuptures().size());
        assertTrue(hasRuptureWithFaults(faults, ruptureSet));

        assertEquals(3101, ruptureSet.getAveSlipForAllRups().length);
        assertEquals(86, ruptureSet.getSlipRateForAllSections().length);
        assertEquals(86, ruptureSet.getSlipRateStdDevForAllSections().length);
    }


}

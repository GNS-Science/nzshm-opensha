package nz.cri.gns.NZSHM22.opensha.ruptures;

import com.google.common.collect.Sets;
import org.dom4j.DocumentException;
import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class NZSHM22_CoulombRuptureSetBuilder_IntegrationTest {

    public boolean hasRuptureWithFaults(Set<Integer> faults, FaultSystemRupSet rupSet) {

        for (List<Integer> sections : rupSet.getSectionIndicesForAllRups()) {
            Set<Integer> parents = new HashSet<>();
            for (int s : sections) {
                parents.add(rupSet.getFaultSectionData(s).getParentSectionId());
            }
            if (parents.equals(faults)) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void testRuptureSetSlipRateMethods() throws IOException, DocumentException {

            FaultSystemRupSet ruptureSet =
                new NZSHM22_CoulombRuptureSetBuilder()
                        .setFaultModelFile(new File("src/integration/resources/KAIK2016.xml"))
                        .buildRuptureSet();

        assertEquals(1185, ruptureSet.getNumRuptures());
        assertEquals(68, ruptureSet.getSlipRateForAllSections().length);
        assertEquals(68, ruptureSet.getSlipRateStdDevForAllSections().length);
        
        assertEquals("HopeConwayOS", ruptureSet.getFaultSectionData(11).getParentSectionName());

        assertEquals(new Float(0.0214), new Float(ruptureSet.getSlipRateForSection(11)));
        assertEquals(new Float(2.3), new Float(ruptureSet.getFaultSectionData(11).getOrigSlipRateStdDev()));
        assertEquals(new Float(0.0023), new Float(ruptureSet.getSlipRateStdDevForSection(11)));
    }
    
    @Test
    public void testCantBuildKaikoura2016() throws IOException, DocumentException {

        Set<Integer> kaikouraFaults = Sets.newHashSet(95, 132, 136, 149, 162, 178, 189, 245, 310, 387, 400);

        FaultSystemRupSet ruptureSet =
                new NZSHM22_CoulombRuptureSetBuilder()
                        .setFaultModelFile(new File("src/integration/resources/KAIK2016.xml"))
                        .buildRuptureSet();

        //FaultSystemIO.writeRupSet(ruptureSet, new File("/tmp/testCantBuildKaikoura2016.zip"));        
        assertFalse(hasRuptureWithFaults(kaikouraFaults, ruptureSet));
    }

    @Test
    public void testAlpineVernon() throws IOException, DocumentException {
        Set<Integer> faults = Sets.newHashSet( 23, 24, 130, 50, 48, 46, 585);

        FaultSystemRupSet ruptureSet =
                new NZSHM22_CoulombRuptureSetBuilder()
                        .setFaultModelFile(new File("src/integration/resources/alpine-vernon.xml"))
                        .buildRuptureSet();

        //FaultSystemIO.writeRupSet(ruptureSet, new File("./tmp/testAlpineVernon.zip"));

        assertEquals(2171, ruptureSet.getNumRuptures());
        assertTrue(hasRuptureWithFaults(faults, ruptureSet));

        assertEquals(86, ruptureSet.getSlipRateForAllSections().length);
        assertEquals(86, ruptureSet.getSlipRateStdDevForAllSections().length);
    }


}

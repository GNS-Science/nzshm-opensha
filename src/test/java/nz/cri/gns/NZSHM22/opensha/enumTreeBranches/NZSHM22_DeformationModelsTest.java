package nz.cri.gns.NZSHM22.opensha.enumTreeBranches;

import static org.junit.Assert.*;

import nz.cri.gns.NZSHM22.opensha.inversion.NZSHM22_InversionFaultSystemRuptSet;
import org.dom4j.DocumentException;
import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.faultSurface.FaultSection;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class NZSHM22_DeformationModelsTest {

    protected NZSHM22_InversionFaultSystemRuptSet loadRupSet() throws URISyntaxException, DocumentException, IOException {
        URL alpineVernonRupturesUrl = Thread.currentThread().getContextClassLoader().getResource("AlpineVernonInversionSolution.zip");
        FaultSystemRupSet rupSet = FaultSystemSolution.load(new File(alpineVernonRupturesUrl.toURI())).getRupSet();
        return NZSHM22_InversionFaultSystemRuptSet.fromExistingCrustalSet(rupSet, NZSHM22_LogicTreeBranch.crustalInversion());
    }

    @Test
    public void testApplyTo() throws DocumentException, URISyntaxException, IOException {
        NZSHM22_InversionFaultSystemRuptSet ruptSet = loadRupSet();
        FaultSection s = ruptSet.getFaultSectionData(0);
        assertEquals(0, s.getSectionId());
        assertEquals(27, s.getOrigAveSlipRate(), 0.00000001);
        assertEquals(0.027, ruptSet.getSlipRateForSection(0), 0.0000001);
        assertEquals(0.027, ruptSet.getSlipRateForSection(1), 0.0000001);

        NZSHM22_DeformationModel.DeformationHelper helper = new NZSHM22_DeformationModel.DeformationHelper("vernonDeformation.dat") {
            public InputStream getStream() {
                try {
                    URL url = Thread.currentThread().getContextClassLoader().getResource(fileName);
                    return new FileInputStream(new File(url.toURI()));
                } catch (Exception x) {
                    throw new RuntimeException(x);
                }
            }
        };

        // set slip to be equal section ID
        helper.applyTo(ruptSet);

        assertEquals(0.0, ruptSet.getSlipRateForSection(0), 0.0000001);
        assertEquals(0.001, ruptSet.getSlipRateForSection(1), 0.0000001);

        for (FaultSection section : ruptSet.getFaultSectionDataList()) {
            assertEquals(section.getSectionId(), section.getOrigAveSlipRate(), 0.000000001);
            assertEquals(section.getSectionId(), section.getOrigSlipRateStdDev(), 0.000000001);
        }

        // Testing that we check the length
        helper = new NZSHM22_DeformationModel.DeformationHelper("vernonDeformation.dat") {
            public List<SlipDeformation> getDeformations() {
                List<SlipDeformation> result = new ArrayList<>();
                for (int i = 0; i < ruptSet.getNumSections() + 1; i++) {
                    result.add(new SlipDeformation());
                }
                return result;
            }
        };

        String message = null;
        try {
            helper.applyTo(ruptSet);
        } catch (IllegalArgumentException x) {
            message = x.getMessage();
        }
        assertEquals("Deformation model length does not match number of sections.", message);

        // Testing that we check the length
        helper = new NZSHM22_DeformationModel.DeformationHelper("vernonDeformation.dat") {
            public List<SlipDeformation> getDeformations() {
                List<SlipDeformation> result = new ArrayList<>();
                for (int i = 0; i < ruptSet.getNumSections(); i++) {
                    result.add(new SlipDeformation());
                }
                return result;
            }
        };

        message = null;
        try {
            helper.applyTo(ruptSet);
        } catch (IllegalArgumentException x) {
            message = x.getMessage();
        }
        assertEquals("Deformation parent id does not match section parent id.", message);

    }

    @Test
    public void testLoad() throws DocumentException, IOException {
        for(NZSHM22_DeformationModel model : NZSHM22_DeformationModel.values()){
            System.out.println(model.name());
            model.load();
        }
    }

}

package nz.cri.gns.NZSHM22.opensha.inversion;

import static nz.cri.gns.NZSHM22.opensha.util.TestHelpers.makeRupSet;
import static org.junit.Assert.*;

import nz.cri.gns.NZSHM22.opensha.calc.SimplifiedScalingRelationship;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_LogicTreeBranch;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_ScalingRelationshipNode;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_SlipRateFactors;
import nz.cri.gns.NZSHM22.opensha.faults.FaultSectionList;
import nz.cri.gns.NZSHM22.opensha.faults.NZFaultSection;
import org.dom4j.DocumentException;
import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import org.opensha.sha.faultSurface.FaultSection;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

public class NZSHM22_InversionFaultSystemRuptSetTest {

    public File modularRupSetFile() throws URISyntaxException {
        URL alpineVernonRupturesUrl = Thread.currentThread().getContextClassLoader().getResource("ModularAlpineVernonInversionSolution.zip");
        return new File(alpineVernonRupturesUrl.toURI());
    }

    public FaultSystemRupSet modularRupSet() throws URISyntaxException, IOException {
        FaultSystemRupSet rupSet = FaultSystemRupSet.load(modularRupSetFile());
        NZSHM22_LogicTreeBranch branch = new NZSHM22_LogicTreeBranch();
        branch.setValue(NZSHM22_FaultModels.CFM_1_0A_DOM_ALL);
        rupSet.addModule(branch);
        return rupSet;
    }

    @Test
    public void testPreserveLTB() throws IOException, DocumentException {
        FaultSystemRupSet rupSet = makeRupSet(NZSHM22_FaultModels.CFM_1_0A_DOM_ALL, ScalingRelationships.SHAW_2009_MOD);
        NZSHM22_LogicTreeBranch branch = new NZSHM22_LogicTreeBranch();
        NZSHM22_ScalingRelationshipNode scalingRelationship = new NZSHM22_ScalingRelationshipNode();
        SimplifiedScalingRelationship scaling = new SimplifiedScalingRelationship();
        scaling.setupCrustal(4.0, 4.0);
        scalingRelationship.setScalingRelationship(scaling);

        branch.setValue(NZSHM22_FaultModels.CFM_1_0A_DOM_ALL);
        branch.setValue(scalingRelationship);
        rupSet.addModule(branch);

        NZSHM22_InversionFaultSystemRuptSet actual = NZSHM22_InversionFaultSystemRuptSet.fromExistingCrustalSet(rupSet, NZSHM22_LogicTreeBranch.crustalInversion());

        NZSHM22_LogicTreeBranch actualBranch = actual.getModule(NZSHM22_LogicTreeBranch.class);

        assertEquals(NZSHM22_FaultModels.CFM_1_0A_DOM_ALL, actualBranch.getValue(NZSHM22_FaultModels.class));
        assertEquals(scaling, actualBranch.getValue(NZSHM22_ScalingRelationshipNode.class).getScalingRelationship());

    }

    @Test
    public void recalcMagsTest() throws URISyntaxException, IOException {
        FaultSystemRupSet rupSet =  modularRupSet();

        // orgMags were calculated with SimplifiedScalingRelationship: crustal, 4.0, 4.1
        double[] origMags = Arrays.copyOf(rupSet.getMagForAllRups(),rupSet.getMagForAllRups().length);

        NZSHM22_LogicTreeBranch branch = new NZSHM22_LogicTreeBranch();
        NZSHM22_ScalingRelationshipNode scalingNode = new NZSHM22_ScalingRelationshipNode();
        scalingNode.setScalingRelationship(ScalingRelationships.TMG_CRU_2017);
        scalingNode.setRecalc(true);
        branch.setValue(scalingNode);

        rupSet = NZSHM22_InversionFaultSystemRuptSet.recalcMags(rupSet, branch);
        assertNotEquals(origMags[0], rupSet.getMagForAllRups()[0], 0.00000001);

        // to recreate original values
        SimplifiedScalingRelationship scaling = new SimplifiedScalingRelationship();
        scaling.setupCrustal(4.0, 4.1);
        scalingNode.setScalingRelationship(scaling);
        branch.setValue(scalingNode);
        rupSet = NZSHM22_InversionFaultSystemRuptSet.recalcMags(rupSet, branch);
        assertArrayEquals(origMags, rupSet.getMagForAllRups(), 0.00000001);
    }

    @Test
    public void testRegionSlipScaling() throws IOException, DocumentException {
        FaultSystemRupSet rupSet = makeRupSet(NZSHM22_FaultModels.CFM_1_0A_DOM_ALL, ScalingRelationships.SHAW_2009_MOD);

        double[] expected = rupSet.getModule(SectSlipRates.class).getSlipRates().clone();
        NZSHM22_SlipRateFactors factors = new NZSHM22_SlipRateFactors(0.3, 0.4);
        NZSHM22_LogicTreeBranch branch = new NZSHM22_LogicTreeBranch();
        branch.setValue(factors);
        branch.setValue(NZSHM22_FaultModels.CFM_1_0A_DOM_ALL);

        // applySlipRateFactor expects this module to be present
        rupSet.addModule(new TvzDomainSections(rupSet));

        NZSHM22_InversionFaultSystemRuptSet.applySlipRateFactor(rupSet, branch);
        double[] actual = rupSet.getModule(SectSlipRates.class).getSlipRates().clone();

        FaultSectionList parents = new FaultSectionList();
        NZSHM22_FaultModels.CFM_1_0A_DOM_ALL.fetchFaultSections(parents);
        List<? extends FaultSection> sections = rupSet.getFaultSectionDataList();

        for(int i =0; i < expected.length; i++){
            NZFaultSection parent = ((NZFaultSection) parents.get(sections.get(i).getParentSectionId()));
            if(parent.getDomainNo().equals(NZSHM22_FaultModels.CFM_1_0A_DOM_ALL.getTvzDomain())){
                expected[i] *= 0.4;
            } else {
                expected[i] *= 0.3;
            }
        }

        assertArrayEquals(expected, actual, 0);
    }
}

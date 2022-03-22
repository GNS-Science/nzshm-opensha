package nz.cri.gns.NZSHM22.opensha.enumTreeBranches;

import nz.cri.gns.NZSHM22.opensha.inversion.NZSHM22_InversionFaultSystemRuptSet;
import org.dom4j.DocumentException;
import org.junit.Test;
import org.opensha.commons.geo.Location;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.UncertainDataConstraint;
import org.opensha.sha.faultSurface.FaultSection;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;

import static org.junit.Assert.*;

public class NZSHM22_PaleoRatesTest {

    protected NZSHM22_InversionFaultSystemRuptSet loadRupSet() throws URISyntaxException, DocumentException, IOException {
        URL alpineVernonRupturesUrl = Thread.currentThread().getContextClassLoader().getResource("AlpineVernonInversionSolution.zip");
        FaultSystemRupSet rupSet = FaultSystemSolution.load(new File(alpineVernonRupturesUrl.toURI())).getRupSet();
        return NZSHM22_InversionFaultSystemRuptSet.fromExistingCrustalSet(rupSet, NZSHM22_LogicTreeBranch.crustalInversion());
    }

    @Test
    public void testFetchRatesConstraints() throws DocumentException, URISyntaxException, IOException {
        List<? extends FaultSection> sections = loadRupSet().getFaultSectionDataList();
        for(NZSHM22_PaleoRates rates : NZSHM22_PaleoRates.values()){
            List<UncertainDataConstraint.SectMappedUncertainDataConstraint> constraints = rates.fetchConstraints(sections);

            assertTrue(constraints.size() > 0);

            for(UncertainDataConstraint.SectMappedUncertainDataConstraint constraint : constraints){
                FaultSection section = sections.get(constraint.sectionIndex);
                Location loc = constraint.dataLocation;
                double dist = section.getFaultTrace().minDistToLine(loc);
                
                assertTrue(dist < 5.25);
            }
        }
    }
}

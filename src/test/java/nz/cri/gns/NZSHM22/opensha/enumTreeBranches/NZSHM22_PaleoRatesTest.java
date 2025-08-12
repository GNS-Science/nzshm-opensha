package nz.cri.gns.NZSHM22.opensha.enumTreeBranches;

import static nz.cri.gns.NZSHM22.opensha.util.TestHelpers.createRupSetForSections;
import static org.junit.Assert.*;

import java.io.IOException;
import java.util.List;
import org.dom4j.DocumentException;
import org.junit.Test;
import org.opensha.commons.geo.Location;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.UncertainDataConstraint;
import org.opensha.sha.faultSurface.FaultSection;

public class NZSHM22_PaleoRatesTest {

    @Test
    public void testFetchRatesConstraints() throws DocumentException, IOException {
        List<? extends FaultSection> sections =
                createRupSetForSections(NZSHM22_FaultModels.CFM_1_0A_DOM_ALL)
                        .getFaultSectionDataList();
        for (NZSHM22_PaleoRates rates : NZSHM22_PaleoRates.values()) {
            List<UncertainDataConstraint.SectMappedUncertainDataConstraint> constraints =
                    rates.fetchConstraints(sections);

            if (rates == NZSHM22_PaleoRates.CUSTOM) {
                assertTrue(constraints.isEmpty());
            } else {
                assertFalse(constraints.isEmpty());
            }

            for (UncertainDataConstraint.SectMappedUncertainDataConstraint constraint :
                    constraints) {
                FaultSection section = sections.get(constraint.sectionIndex);
                Location loc = constraint.dataLocation;
                double dist = section.getFaultTrace().minDistToLine(loc);

                assertTrue(dist < 5.25);
            }
        }
    }
}

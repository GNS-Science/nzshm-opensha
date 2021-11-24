package nz.cri.gns.NZSHM22.opensha.inversion;

import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_LogicTreeBranch;
import org.junit.Test;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModSectMinMags;
import org.opensha.sha.faultSurface.FaultSection;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntPredicate;

import static org.junit.Assert.*;

public class RegionalRupSetDataTest {

    public FaultSystemRupSet modularRupSet() throws URISyntaxException, IOException {
        URL alpineVernonRupturesUrl = Thread.currentThread().getContextClassLoader().getResource("ModularAlpineVernonInversionSolution.zip");
        return FaultSystemSolution.load(new File(alpineVernonRupturesUrl.toURI())).getRupSet();
    }

    @Test
    public void testFilter() throws URISyntaxException, IOException {
        FaultSystemRupSet original = modularRupSet();
        // using section name as unique ID that does not change between sets
        Map<String, Integer> originalIds = new HashMap<>();
        for (FaultSection section : original.getFaultSectionDataList()) {
            originalIds.put(section.getSectionName(), section.getSectionId());
        }
        assertEquals(original.getNumSections(), originalIds.size());

        GriddedRegion region = new GriddedRegion(
                new Location(-42.48019996901214, 172.496337890625),
                new Location(-41.186922422902946, 174.781494140625),
                0.1, 0.1, new Location(0, 0));

        IntPredicate filter = RegionalRupSetData.createRegionFilter(original, region);

        RegionalRupSetData actual = new RegionalRupSetData(original, region, filter,7.0);

        assertEquals(86, original.getNumSections());
        assertEquals(34, actual.getFaultSectionDataList().size());

        for (int i = 0; i < actual.getFaultSectionDataList().size(); i++) {
            FaultSection section = actual.getFaultSectionDataList().get(i);
            assertEquals(i, section.getSectionId());
            assertNotNull(actual.getPolygonFaultGridAssociations().getPoly(i));
        }

    }

}

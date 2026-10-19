package nz.cri.gns.NZSHM22.opensha.polygonise;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.RuptureSetSetupTest;
import org.dom4j.DocumentException;
import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModSectMinMags;

/** Tests {@link NZSHM22_PolygonisedDistributedModelBuilder}. */
public class NZSHM22_PolygonisedDistributedModelBuilderTest {

    /** Section min mags above the default crustal MFD range must not be clamped to 8.95. */
    @Test
    public void mMinsPerSectionAboveDefaultRangeTest() throws DocumentException, IOException {
        FaultSystemRupSet rupSet = RuptureSetSetupTest.makeRupSet();
        double[] minMags = new double[rupSet.getNumSections()];
        Arrays.fill(minMags, 6.8);
        minMags[0] = 9.21;
        rupSet.addModule(ModSectMinMags.instance(rupSet, minMags));

        List<Double> actual =
                new NZSHM22_PolygonisedDistributedModelBuilder().mMinsPerSection(rupSet);

        assertEquals(rupSet.getNumSections(), actual.size());
        assertEquals(9.25, actual.get(0), 1e-9);
    }
}

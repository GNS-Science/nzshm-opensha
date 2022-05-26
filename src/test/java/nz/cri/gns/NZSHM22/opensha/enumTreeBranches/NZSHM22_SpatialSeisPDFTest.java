package nz.cri.gns.NZSHM22.opensha.enumTreeBranches;

import org.junit.Test;

public class NZSHM22_SpatialSeisPDFTest {

    @Test
    public void testLoad() {
        // just checking that we don't explode
        for (NZSHM22_SpatialSeisPDF pdf : NZSHM22_SpatialSeisPDF.values()) {
            pdf.getGriddedData();
        }
    }
}

package nz.cri.gns.NZSHM22.opensha.enumTreeBranches;

import org.junit.Test;

import static org.junit.Assert.*;

public class NZSHM22_PaleoProbabilityModelTest {

    @Test
    public void fetchTest(){
        // make sure we don't blow up
        for(NZSHM22_PaleoProbabilityModel model : NZSHM22_PaleoProbabilityModel.values()){
            assertNotNull(model.fetchModel());
        }
    }
}

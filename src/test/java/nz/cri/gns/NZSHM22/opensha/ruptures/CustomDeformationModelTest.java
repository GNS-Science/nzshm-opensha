package nz.cri.gns.NZSHM22.opensha.ruptures;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import nz.cri.gns.NZSHM22.opensha.util.TestHelpers;
import org.junit.Test;

public class CustomDeformationModelTest {

    @Test
    public void testSerialisation() throws IOException {
        String data = "% a comment\n0, 0, 1, 2\n1, 1, 3, 4\n";
        CustomDeformationModel module =
                (CustomDeformationModel)
                        TestHelpers.serialiseDeserialise(new CustomDeformationModel(data));
        assertEquals(data, module.getModelData());
    }
}

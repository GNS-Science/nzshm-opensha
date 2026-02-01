package nz.cri.gns.NZSHM22.inversion.joint;

import nz.cri.gns.NZSHM22.opensha.inversion.joint.ConfigModule;
import org.junit.Test;

public class ConfigModuleTest {

    @Test
    public void fromJsonTest() {
        String json = "{}";
        ConfigModule.fromJson(json);

        json =
                "//first line comment\n{\n//more comments\n   //cool comment\n}\n//last line comment";
        // we can read json with comments without blowing up
        ConfigModule.fromJson(json);
    }
}

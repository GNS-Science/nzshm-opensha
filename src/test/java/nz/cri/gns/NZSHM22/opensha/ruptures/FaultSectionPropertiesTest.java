package nz.cri.gns.NZSHM22.opensha.ruptures;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.Test;

public class FaultSectionPropertiesTest {

    @Test
    public void testWriteDifferentTypes() throws IOException {
        FaultSectionProperties properties = new FaultSectionProperties();
        properties.set(0, "a", 42);
        properties.set(0, "b", true);
        properties.set(0, "c", "x");
        properties.set(0, "d", null);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            properties.writeToStream(out);
            assertEquals("{\"0\":{\"a\":42,\"b\":true,\"c\":\"x\"}}", out.toString());
        }
    }

    @Test
    public void readDifferentTypes() throws IOException{
        FaultSectionProperties properties = new FaultSectionProperties();
        byte[] data = "{\"0\":{\"a\":42,\"b\":true,\"c\":\"x\"}}".getBytes();
        try(ByteArrayInputStream in = new ByteArrayInputStream(data)){
            properties.initFromStream(new BufferedInputStream(in));
            assertEquals(42, properties.get(0, "a"));
            assertEquals(true, properties.get(0, "b"));
            assertEquals("x", properties.get(0, "c"));
            assertNull(properties.get(0, "d"));
        }
    }
}

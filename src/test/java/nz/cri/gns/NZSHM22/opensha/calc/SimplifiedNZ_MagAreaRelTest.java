package nz.cri.gns.NZSHM22.opensha.calc;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class SimplifiedNZ_MagAreaRelTest {
    @Test
    public void testAdapter() throws IOException {

        SimplifiedNZ_MagAreaRel.Adapter adapter = new SimplifiedNZ_MagAreaRel.Adapter();

        SimplifiedNZ_MagAreaRel original = SimplifiedNZ_MagAreaRel.forCrustal(3.14, 42);
        String json = adapter.toJson(original);
        SimplifiedNZ_MagAreaRel actual = adapter.fromJson(json);
        String actualJson = adapter.toJson(actual);
        assertEquals(original, actual);
        assertEquals(json, actualJson);

        original = SimplifiedNZ_MagAreaRel.forSubduction(3.14);
        json = adapter.toJson(original);
        actual = adapter.fromJson(json);
        actualJson = adapter.toJson(actual);
        assertEquals(original, actual);
        assertEquals(json, actualJson);

    }
}

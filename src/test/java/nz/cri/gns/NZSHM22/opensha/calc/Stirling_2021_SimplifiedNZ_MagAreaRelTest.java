package nz.cri.gns.NZSHM22.opensha.calc;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class Stirling_2021_SimplifiedNZ_MagAreaRelTest {

    @Test
    public void testAdapter() throws IOException {

        Stirling_2021_SimplifiedNZ_MagAreaRel.Adapter adapter = new Stirling_2021_SimplifiedNZ_MagAreaRel.Adapter();

        Stirling_2021_SimplifiedNZ_MagAreaRel original = new Stirling_2021_SimplifiedNZ_MagAreaRel(42, "CRUSTAL", "MEAN");
        String json = adapter.toJson(original);
        Stirling_2021_SimplifiedNZ_MagAreaRel actual = adapter.fromJson(json);
        String actualJson = adapter.toJson(actual);
        assertEquals(original, actual);
        assertEquals(json, actualJson);

        original = new Stirling_2021_SimplifiedNZ_MagAreaRel("INTERFACE", "MEAN");
        json = adapter.toJson(original);
        actual = adapter.fromJson(json);
        actualJson = adapter.toJson(actual);
        assertEquals(original, actual);
        assertEquals(json, actualJson);

    }
}

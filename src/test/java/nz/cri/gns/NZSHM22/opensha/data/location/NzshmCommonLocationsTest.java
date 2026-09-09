package nz.cri.gns.NZSHM22.opensha.data.location;

import static org.junit.Assert.*;

import java.util.Map;
import org.junit.Test;
import org.opensha.commons.geo.Location;

/** Tests for {@link NzshmCommonLocations}. */
public class NzshmCommonLocationsTest {

    /** The nzshm-common "NZ" list has 36 locations. */
    @Test
    public void testNzLocationsAreLoaded() {
        Map<String, Location> locations = NzshmCommonLocations.nzLocations();
        assertEquals(36, locations.size());
    }

    /** Spot checks against nzshm-common's locations.json. */
    @Test
    public void testKnownLocations() {
        Map<String, Location> locations = NzshmCommonLocations.nzLocations();

        Location wellington = locations.get("Wellington");
        assertNotNull(wellington);
        assertEquals(-41.3, wellington.getLatitude(), 1e-9);
        assertEquals(174.78, wellington.getLongitude(), 1e-9);

        Location auckland = locations.get("Auckland");
        assertNotNull(auckland);
        assertEquals(-36.87, auckland.getLatitude(), 1e-9);
        assertEquals(174.77, auckland.getLongitude(), 1e-9);

        // the list goes well beyond the main centres
        assertTrue(locations.containsKey("Franz Josef"));
        assertTrue(locations.containsKey("Hanmer Springs"));
    }

    /** All locations sit within New Zealand. */
    @Test
    public void testLocationsAreInNewZealand() {
        for (Map.Entry<String, Location> entry : NzshmCommonLocations.nzLocations().entrySet()) {
            Location location = entry.getValue();
            assertTrue(
                    entry.getKey() + " latitude " + location.getLatitude(),
                    location.getLatitude() > -48 && location.getLatitude() < -34);
            assertTrue(
                    entry.getKey() + " longitude " + location.getLongitude(),
                    location.getLongitude() > 166 && location.getLongitude() < 179.5);
        }
    }

    /** The list is cached and preserves the order it is declared in. */
    @Test
    public void testOrderAndCaching() {
        Map<String, Location> first = NzshmCommonLocations.nzLocations();
        assertSame(first, NzshmCommonLocations.nzLocations());
        assertEquals("Auckland", first.keySet().iterator().next());
    }
}

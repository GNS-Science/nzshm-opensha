package nz.cri.gns.NZSHM22.opensha.data.location;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import nz.cri.gns.NZSHM22.opensha.util.NZSHM22_DataUtils;
import org.opensha.commons.geo.Location;

/**
 * The named New Zealand locations that NSHM hazard results are reported at.
 *
 * <p>The list is a copy of the "NZ" location list of <a
 * href="https://github.com/GNS-Science/nzshm-common-py">nzshm-common-py</a>, i.e. {@code
 * LOCATION_LISTS["NZ"]}, resolved against that project's {@code locations.json}. It is held here as
 * a resource rather than fetched, so update {@code data/location/nzshm_common_nz_locations.csv}
 * when nzshm-common changes.
 */
public class NzshmCommonLocations {

    static final String DATA_DIR = "location";
    static final String NZ_LOCATIONS_FILE = "nzshm_common_nz_locations.csv";

    private static Map<String, Location> nzLocations;

    private NzshmCommonLocations() {}

    /**
     * The nzshm-common "NZ" locations, keyed by name (e.g. "Wellington"), in the order that
     * nzshm-common lists them.
     */
    public static synchronized Map<String, Location> nzLocations() {
        if (nzLocations == null) {
            nzLocations = Collections.unmodifiableMap(load());
        }
        return nzLocations;
    }

    /** Reads the location list resource. Called once, on first use of {@link #nzLocations()}. */
    protected static Map<String, Location> load() {
        Map<String, Location> locations = new LinkedHashMap<>();
        try (BufferedReader reader =
                new BufferedReader(NZSHM22_DataUtils.getReader(DATA_DIR, NZ_LOCATIONS_FILE))) {
            int lineNumber = 1;
            String line = reader.readLine(); // header
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                // id,name,latitude,longitude
                String[] fields = line.split(",");
                if (fields.length != 4) {
                    throw new IllegalStateException(
                            "Expected 4 fields in "
                                    + NZ_LOCATIONS_FILE
                                    + " line "
                                    + lineNumber
                                    + ", got: "
                                    + line);
                }
                String name = fields[1].trim();
                double latitude = parseDouble(fields[2], "latitude", lineNumber);
                double longitude = parseDouble(fields[3], "longitude", lineNumber);

                locations.put(name, new Location(latitude, longitude));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + NZ_LOCATIONS_FILE, e);
        }
        return locations;
    }

    /**
     * Parses a CSV field as a double, reporting the offending line if it cannot be parsed.
     *
     * @param field the raw field text
     * @param fieldName the column name, used in the error message
     * @param lineNumber the 1-based line number of the field, used in the error message
     * @return the parsed value
     */
    protected static double parseDouble(String field, String fieldName, int lineNumber) {
        try {
            return Double.parseDouble(field.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    "Could not parse "
                            + fieldName
                            + " in "
                            + NZ_LOCATIONS_FILE
                            + " line "
                            + lineNumber
                            + ": "
                            + field,
                    e);
        }
    }
}

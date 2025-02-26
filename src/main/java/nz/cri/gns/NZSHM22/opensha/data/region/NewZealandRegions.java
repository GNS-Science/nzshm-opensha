package nz.cri.gns.NZSHM22.opensha.data.region;

import java.io.BufferedReader;
import nz.cri.gns.NZSHM22.opensha.griddedSeismicity.NZSHM22_GriddedData;
import nz.cri.gns.NZSHM22.opensha.util.NZSHM22_DataUtils;
import org.opensha.commons.geo.BorderType;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;

public class NewZealandRegions {

    static String DATA_DIR = "region";

    public static final GriddedRegion NZ = new NZ_RECTANGLE_GRIDDED();

    private NewZealandRegions() {}
    ;

    public static final class NZ_EMPTY_GRIDDED extends GriddedRegion {
        static LocationList coords = new LocationList();

        static {
            coords.add(new Location(-16.214675, 123.367611));
            coords.add(new Location(-24.447150, 140.957213));
        }

        public NZ_EMPTY_GRIDDED() {
            super(
                    new Region(
                            new Location(-16.214675, 123.367611),
                            new Location(-24.447150, 140.957213)),
                    0.1,
                    ANCHOR_0_0);
            setName("Not in NZ Region");
        }
    }

    /** Region used in NZNSHM22 */
    public static final class NZ_TEST extends Region {
        public NZ_TEST() {
            super(readCoords("nz_test.coords"), BorderType.MERCATOR_LINEAR);
            this.setName("NZTEST Region");
        }
    }

    /** Gridded Region used in NZNSHM22 Grid spacing is 0.1&deg;. */
    public static final class NZ_TEST_GRIDDED extends GriddedRegion {
        public NZ_TEST_GRIDDED() {
            super(
                    readCoords("nz_test.coords"),
                    BorderType.MERCATOR_LINEAR,
                    NZSHM22_GriddedData.GRID_SPACING,
                    ANCHOR_0_0);
            this.setName("NZTEST Gridded Region");
        }
    }

    /*
     * Taupo Volcanic Zone points from Chris Rollins
     */
    public static final class NZ_TVZ extends Region {
        public NZ_TVZ() {
            super(readCoords("nz_taupo_volcanic_2.coords", true), BorderType.MERCATOR_LINEAR);
            this.setName("Taupo Volcanic Zone Region 2");
        }
    }

    /*
     * Taupo Volcanic Zone points from MattG
     */
    public static final class NZ_TVZ_0 extends Region {
        public NZ_TVZ_0() {
            super(readCoords("nz_taupo_vocanic.coords", true), BorderType.MERCATOR_LINEAR);
            this.setName("Taupo Volcanic Zone Region 0");
        }
    }

    public static final class NZ_TVZ_GRIDDED extends GriddedRegion {
        public NZ_TVZ_GRIDDED() {
            super((Region) new NZ_TVZ(), NZSHM22_GriddedData.GRID_SPACING, ANCHOR_0_0);
            this.setName("NZ TVZ Gridded Region");
        }
    }

    /** NZ rectangle with the TVZ area removed */
    public static final class NZ_RECTANGLE_SANS_TVZ extends Region {
        public NZ_RECTANGLE_SANS_TVZ() {
            super(readCoords("nz_rectangle.coords"), BorderType.MERCATOR_LINEAR);
            Region regionTVZ = new NZ_TVZ();
            this.addInterior(regionTVZ);
            this.setName("nz_rectangle SANS TVZ Gridded Region");
        }
    }

    public static final class NZ_RECTANGLE_SANS_TVZ_GRIDDED extends GriddedRegion {
        public NZ_RECTANGLE_SANS_TVZ_GRIDDED() {
            super(
                    (Region) new NZ_RECTANGLE_SANS_TVZ(),
                    NZSHM22_GriddedData.GRID_SPACING,
                    ANCHOR_0_0);
            this.setName("NZ RECTANGLE SANS TVZ Gridded Region");
        }
    }

    /** The Rectangle used in scecVDO visualistions for the NZ graticule. */
    public static final class NZ_RECTANGLE extends Region {
        public NZ_RECTANGLE() {
            super(readCoords("nz_rectangle.coords"), BorderType.MERCATOR_LINEAR);
            this.setName("NZ_RECTANGLE Region");
        }
    }

    public static final class NZ_RECTANGLE_GRIDDED extends GriddedRegion {
        public NZ_RECTANGLE_GRIDDED() {
            super(
                    readCoords("nz_rectangle.coords"),
                    BorderType.MERCATOR_LINEAR,
                    NZSHM22_GriddedData.GRID_SPACING,
                    ANCHOR_0_0);
            this.setName("NZ_RECTANGLE Gridded Region");
        }
    }

    private static LocationList readCoords(String filename) {
        return readCoords(filename, false);
    }

    /*
     * Reads coordinate pairs from a file of comma-delimited pairs e.g.
     * "165.743,-46.1520"
     */
    private static LocationList readCoords(String filename, boolean latitudeFirst) {
        BufferedReader br;
        try {
            br = new BufferedReader(NZSHM22_DataUtils.getReader(DATA_DIR, filename));
            LocationList ll = new LocationList();
            String[] vals;
            String s;
            double lon, lat;
            while ((s = br.readLine()) != null) {
                vals = s.trim().split(",");
                if (latitudeFirst) {
                    lat = Double.valueOf(vals[0]);
                    lon = Double.valueOf(vals[1]);
                } else {
                    lon = Double.valueOf(vals[0]);
                    lat = Double.valueOf(vals[1]);
                }
                Location loc = new Location(lat, lon);
                ll.add(loc);
            }
            br.close();
            return ll;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}

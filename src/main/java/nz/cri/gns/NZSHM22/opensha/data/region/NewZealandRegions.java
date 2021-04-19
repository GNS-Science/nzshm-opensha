package nz.cri.gns.NZSHM22.opensha.data.region;

import java.io.BufferedReader;
import org.opensha.commons.geo.BorderType;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;

import nz.cri.gns.NZSHM22.opensha.util.NZSHM22_DataUtils;

public class NewZealandRegions {

	static String DATA_DIR = "region";

	private NewZealandRegions() {
	};

	/**
	 * Region used in NZNSHM22
	 * 
	 */
	public static final class NZ_TEST extends Region {
		public NZ_TEST() {
			super(readCoords("nz_test.coords"), BorderType.MERCATOR_LINEAR);
			this.setName("NZTEST Region");
		}
	}

	/**
	 * Gridded Region used in NZNSHM22 Grid spacing is 0.1&deg;.
	 * 
	 */
	public static final class NZ_TEST_GRIDDED extends GriddedRegion {
		public NZ_TEST_GRIDDED() {
			super(readCoords("nz_test.coords"), BorderType.MERCATOR_LINEAR, 0.1, ANCHOR_0_0);
			this.setName("NZTEST Gridded Region");
		}
	}

	/*
	 * Taupo Volcanic Zone points from MattG
	 */
	public static final class NZ_TVZ extends Region {
		public NZ_TVZ() {
			super(readCoords("nz_taupo_vocanic.coords", true), BorderType.MERCATOR_LINEAR);
			this.setName("Taupo Volcanic Zone Region");
		}
	}

	public static final class NZ_TVZ_GRIDDED extends GriddedRegion {
		public NZ_TVZ_GRIDDED() {
			super((Region) new NZ_TVZ(), 0.1, ANCHOR_0_0);
			this.setName("NZ TVZ Gridded Region");
		}
	}

	/**
	 * NZ rectangle with the TVZ area removed
	 */
	public static final class NZ_RECTANGLE_SANS_TVZ extends Region {
		public NZ_RECTANGLE_SANS_TVZ() {
			super(readCoords("nz_rectangle.coords"), BorderType.MERCATOR_LINEAR);
			Region regionTVZ = new NZ_TVZ();
			this.addInterior(regionTVZ);
			this.setName("nz_rectangle Gridded Region");
		}
	}

	public static final class NZ_RECTANGLE_SANS_TVZ_GRIDDED extends GriddedRegion {
		public NZ_RECTANGLE_SANS_TVZ_GRIDDED() {
			super((Region) new NZ_RECTANGLE_SANS_TVZ(), 0.1, ANCHOR_0_0);
			this.setName("NZ RECTANEL SANS TVZ Gridded Region");
		}
	}

	/**
	 * The Rectangle used in scecVDO visualistions for the NZ graticule.
	 */
	public static final class NZ_RECTANGLE extends Region {
		public NZ_RECTANGLE() {
			super(readCoords("nz_rectangle.coords"), BorderType.MERCATOR_LINEAR);
			this.setName("NZ_RECTANGLE Region");
		}
	}

	public static final class NZ_RECTANGLE_GRIDDED extends GriddedRegion {
		public NZ_RECTANGLE_GRIDDED() {
			super(readCoords("nz_rectangle.coords"), BorderType.MERCATOR_LINEAR, 0.1, ANCHOR_0_0);
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
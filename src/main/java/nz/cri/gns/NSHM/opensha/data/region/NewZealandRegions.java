package nz.cri.gns.NSHM.opensha.data.region;


import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.geo.BorderType;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.RegionUtils;

import nz.cri.gns.NSHM.opensha.util.NSHM_DataUtils;
import scratch.UCERF3.utils.UCERF3_DataUtils;


public class NewZealandRegions {
	
	static String DATA_DIR = "region";
	
	private NewZealandRegions() {};

	public static void main(String[] args) {
		GriddedRegion rr = new NZ_TEST_GRIDDED();
		System.out.println(rr.getNodeCount());
	}

	/** 
	 * Gridded region used in NZNSHM22
	 * Grid spacing is 0.1&deg;.
	 * 
	 */
	public static final class NZ_TEST_GRIDDED extends 
			GriddedRegion {
		/** New instance of region. */
		public NZ_TEST_GRIDDED() {
			super(readCoords("nztest.coords"), 
					BorderType.MERCATOR_LINEAR, 0.1, ANCHOR_0_0);
			this.setName("NZTEST Region");
		}
	}	
	

	/** 
	 * The Rectangle used in scecVDO visualistions for the NZ graticule.
	 */
	public static final class NZ_RECTANGLE extends Region {
		/** New instance of region. */
		public NZ_RECTANGLE() {
			super(readCoords("nz_rectangle.coords"), 
					BorderType.MERCATOR_LINEAR);
			this.setName("NZ_RECTANGLE Region");
		}
	}

	/** 
	 * The GriddedRectangle used in scecVDO visualistions for the NZ graticule.
	 */
	public static final class NZ_RECTANGLE_GRIDDED extends GriddedRegion {
		/** New instance of region. */
		public NZ_RECTANGLE_GRIDDED() {
			super(readCoords("nz_rectangle.coords"), 
					BorderType.MERCATOR_LINEAR, 0.1, ANCHOR_0_0);
			this.setName("NZ_RECTANGLE Gridded Region");
		}
	}	
	
	/*
	 * Reads coordinate pairs from a file.
	 * NZ files are Lon/Lat (again)Each line of the file should have
	 * a space-delimited lon lat pair e.g. "165.743   -46.1520"
	 */
	private static LocationList readCoords(String filename) {
		BufferedReader br;
		try {
			br = new BufferedReader(NSHM_DataUtils.getReader(DATA_DIR,
					filename));
			LocationList ll = new LocationList();
			String[] vals;
	        String s;
	        while ((s = br.readLine()) != null) {
	        	vals = s.trim().split(",");
	        	double lon = Double.valueOf(vals[0]);
	        	double lat = Double.valueOf(vals[1]);
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

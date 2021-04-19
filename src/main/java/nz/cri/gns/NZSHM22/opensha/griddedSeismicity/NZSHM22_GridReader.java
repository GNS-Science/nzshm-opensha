package nz.cri.gns.NZSHM22.opensha.griddedSeismicity;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Iterator;

import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.util.DataUtils;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import nz.cri.gns.NZSHM22.opensha.data.region.NewZealandRegions;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_SpatialSeisPDF;
import nz.cri.gns.NZSHM22.opensha.util.NZSHM22_DataUtils;
import scratch.UCERF3.griddedSeismicity.GridReader;

public class NZSHM22_GridReader extends GridReader {
	
	protected static final NewZealandRegions.NZ_TEST_GRIDDED region = 
			new NewZealandRegions.NZ_TEST_GRIDDED();
	
	/**
	 * Constructs a new grid reader for the supplied filename.
	 * @param filename
	 */
	public NZSHM22_GridReader(String filename) {
		super(filename);
	}

	/**
	 *  Build the PDF table from the input file
	 *  
	 *  NZ data files have data format: lon|lat|value
	 *  UCERF3 data format:  lat|lon|value 
	 *  
	 */
	@Override
	protected Table<Integer, Integer, Double> initTable() {
		Table<Integer, Integer, Double> table = HashBasedTable.create();
		String lon, lat;
		Integer lonkey, latkey;
		Double val;
		Double totalValue = 0.0;
		
		try {
			String DATA_DIR = "seismicityGrids";
			BufferedReader br = new BufferedReader(NZSHM22_DataUtils.getReader(DATA_DIR, filename));
			Iterator<String> dat;
			String line = br.readLine();
			while (line != null) {
				dat = SPLIT.split(line).iterator();
				lon = dat.next();
				lat = dat.next();
				lonkey = FN_STR_TO_KEY.apply(lon);
				latkey = FN_STR_TO_KEY.apply(lat);
				val = FN_STR_TO_DBL.apply(dat.next());
				//Guards
				Location loc = new Location(Double.parseDouble(lat), Double.parseDouble(lon));
				if (region.contains(loc) == false)
					System.out.println("location "+loc+ " is not within bounds of expected region");
				if (table.contains(latkey,  lonkey))
					System.out.println("location "+loc+ " is already defined in table, input data duplication!!");
				totalValue += val;
				table.put(latkey, lonkey, val);
				line = br.readLine();
			}
		} catch (IOException ioe) {
			throw  new RuntimeException(ioe);
		}
		System.out.println("total in " + filename + " = " + totalValue);
		return table;
	}	

	/**
	 * Returns all values in order corresponding to the node indices in the
	 * supplied GriddedRegion.
	 * @param region of interest
	 * @return all required values
	 */
	public double[] getValues() {
		double[] values = new double[region.getNodeCount()];
		double nullval = 0.0d; // 
		// double nullval = Double.NaN; //	maybe try 0 rather than NaN for nulls ...
		int i = 0;
		for (Location loc : region) {
			Double value = getValue(loc);
//			if (value == null )
//				System.out.println("gridded region location "  + loc + " was not found in seismicity table");
			values[i++] = (value == null) ? nullval : value;			
		}
		return values;
	}	
	
	
	/**
	 * table entries should be aligned with a grid location within the region
	 * so there should be no 'leftover' table entries printed by this method
	 * 
	 *   TODO: add this as a test case and identifyt the root cause. 
	 *   Maybe this is to do with the offset grid locations provided bu Sepi
	 *   and how these do/don't align to the actual GriddedRegion layout.
	 *   
	 * @param region
	 */
	private void missingTableEntries(GriddedRegion region) {	
		for (Location loc : region) {
			this.table.remove(
					FN_DBL_TO_KEY.apply(Double.valueOf(loc.getLatitude())), 
					FN_DBL_TO_KEY.apply(Double.valueOf(loc.getLongitude())));
		}
		
		double tot = 0.0;
		for (Integer rowkey : this.table.rowKeySet()) {
			for (Double x : table.row(rowkey).values()) {
				tot += x;
			}
		}
		System.out.println("total missing = " + tot);
	}
	
	public static void main(String[] args) {
		//TODO: some defensive testing around this is recommended to snsure that regional
		// PDF functions do work as expected.
		// see also missingTableEntries() method
		// so for now, just leavintg these examples in main()
		
		GriddedRegion reg = NZSHM22_GridReader.region.clone();
		NZSHM22_GridReader gr = new NZSHM22_GridReader("BEST2FLTOLDNC1246.txt");
		gr.missingTableEntries(reg);
		
		double[] pdf = NZSHM22_SpatialSeisPDF.NZSHM22_1246.getPDF();
		System.out.println("N22 1256  " + DataUtils.sum(pdf));
		System.out.println("N22 1256 min " + DataUtils.min(pdf));
		System.out.println("N22 1256 max " + DataUtils.max(pdf));

		pdf = NZSHM22_SpatialSeisPDF.NZSHM22_1456.getPDF();
		System.out.println("N22 1456  " + DataUtils.sum(pdf));
	
		pdf = NZSHM22_SpatialSeisPDF.NZSHM22_1246R.getPDF();
		System.out.println("N22 R1256 " + DataUtils.sum(pdf));

		pdf = NZSHM22_SpatialSeisPDF.NZSHM22_1456R.getPDF();
		System.out.println("N22 R1456 " + DataUtils.sum(pdf));

		GriddedRegion nzregion = new NewZealandRegions.NZ_TEST_GRIDDED();
		double fir = NZSHM22_SpatialSeisPDF.NZSHM22_1246.getFractionInRegion(nzregion);
		System.out.println("n22 FractionInRegion " + fir);
		
//		GriddedRegion region = new GriddedRegion(getRegionNZ(), 0.1d, null);
//		double fir = NZSHM22_SpatialSeisPDF.NZSHM22_1456.getFractionInRegion(region);
//		System.out.println("n22 FractionInRegion " + fir);		

		
		//		String fName =  "SmoothSeis_KF_5-5-2012.txt";
//		File f = getSourceFile(fName);
//		System.out.println(f.exists());
//		GridReader gr = new GridReader();
//		
//		 double sum = 0;
//		 for (Table.Cell<Integer, Integer, Double> cell : table.cellSet()) {
//			 sum += cell.getValue();
//		 }
//		 System.out.println(sum);
//		 System.out.println(GridReader.getScale(new Location(39.65,  -120.1)));
//		 System.out.println(GridReader.getScale(new Location(20, -20)));
	}
	
}

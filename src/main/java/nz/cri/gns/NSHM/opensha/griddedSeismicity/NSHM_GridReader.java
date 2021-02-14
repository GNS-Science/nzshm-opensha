package nz.cri.gns.NSHM.opensha.griddedSeismicity;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Iterator;

import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.commons.util.DataUtils;

import com.google.common.base.Throwables;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import nz.cri.gns.NSHM.opensha.enumTreeBranches.NSHM_SpatialSeisPDF;
import nz.cri.gns.NSHM.opensha.util.NSHM_DataUtils;
import scratch.UCERF3.griddedSeismicity.GridReader;

public class NSHM_GridReader extends GridReader {
	
	/**
	 * Constructs a new grid reader for the supplied filename.
	 * @param filename
	 */
	public NSHM_GridReader(String filename) {
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
		String lon, lat, val;
		try {
			BufferedReader br = new BufferedReader(NSHM_DataUtils.getReader(DATA_DIR,
				filename));
			Iterator<String> dat;
			String line = br.readLine();
			while (line != null) {
				dat = SPLIT.split(line).iterator();
				lon = dat.next();
				lat = dat.next();
				val = dat.next();
				table.put(FN_STR_TO_KEY.apply(lat),
					FN_STR_TO_KEY.apply(lon),
					FN_STR_TO_DBL.apply(val));
				line = br.readLine();
			}
		} catch (IOException ioe) {
			throw Throwables.propagate(ioe);
		}
		return table;
	}	

	
    private static Region getRegionNZ() {
    	// NZ as used for scecVDO graticule
		//    			upper-latitude = -30
		//    			lower-latitude = -50
		//    			right-longitude = 185
		//    			left-longitude = 165
		Location nw = new Location(-30.0, 165.0);
		Location se = new Location(-50.0, 185.0);
		return new Region(nw, se);
    }
	
	/**
	 * Returns all values in order corresponding to the node indices in the
	 * supplied GriddedRegion.
	 * @param region of interest
	 * @return all required values
	 */
	public double[] getValues() {
		GriddedRegion region = new GriddedRegion(getRegionNZ(), 0.1d, null);
		double[] values = new double[region.getNodeCount()];
		double nullval = 0.0d; // 
		//double nullval = Double.NaN; //	maybe try 0 rather than NaN for nulls ...
		int i = 0;
		for (Location loc : region) {
			Double value = getValue(loc);
			values[i++] = (value == null) ? nullval : value;			
		}
		return values;
	}	
	
	public static void main(String[] args) {
		double[] pdf = NSHM_SpatialSeisPDF.NZSHM22_1246.getPDF();
		System.out.println("N22 1256  " + DataUtils.sum(pdf));

		pdf = NSHM_SpatialSeisPDF.NZSHM22_1456.getPDF();
		System.out.println("N22 1456  " + DataUtils.sum(pdf));
	
		pdf = NSHM_SpatialSeisPDF.NZSHM22_1246R.getPDF();
		System.out.println("N22 R1256 " + DataUtils.sum(pdf));

		pdf = NSHM_SpatialSeisPDF.NZSHM22_1456R.getPDF();
		System.out.println("N22 R1456 " + DataUtils.sum(pdf));

		GriddedRegion region = new GriddedRegion(getRegionNZ(), 0.1d, null);
		double fir = NSHM_SpatialSeisPDF.NZSHM22_1246.getFractionInRegion(region);
		System.out.println("n22 FractionInRegion " + fir);		
		
//		GriddedRegion region = new GriddedRegion(getRegionNZ(), 0.1d, null);
//		double fir = NSHM_SpatialSeisPDF.NZSHM22_1456.getFractionInRegion(region);
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

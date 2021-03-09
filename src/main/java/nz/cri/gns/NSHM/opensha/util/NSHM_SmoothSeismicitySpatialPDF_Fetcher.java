package nz.cri.gns.NSHM.opensha.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.StringTokenizer;

import org.apache.commons.lang3.StringUtils;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.data.xyz.GeoDataSet;
import org.opensha.commons.data.xyz.GeoDataSetMath;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.geo.Location;
import org.opensha.commons.util.ExceptionUtils;

import nz.cri.gns.NSHM.opensha.griddedSeismicity.NSHM_GridReader;
import scratch.UCERF3.analysis.DeformationModelsCalc;
import scratch.UCERF3.analysis.GMT_CA_Maps;
import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.griddedSeismicity.GridReader;

/**
 * This reads and provides the smoothed seismicity spatial PDFs
 * 
 * based on scratch.UCERF3.utils.SmoothSeismicitySpatialPDF_Fetcher
 * 
 * @author chrisbc
 *
 */

public class NSHM_SmoothSeismicitySpatialPDF_Fetcher {

	public static final String SUBDIR = "SeismicityGrids";
	public static final String FILENAME_1246 = "BEST2FLTOLDNC1246.txt";
	public static final String FILENAME_1246_R = "BEST2FLTOLDNC1246r.txt";
	public static final String FILENAME_1456 = "BESTFLTOLDNC1456.txt";
	public static final String FILENAME_1456_R = "BESTFLTOLDNC1456r.txt";

	public static double[] get1246() {
		return new NSHM_GridReader(FILENAME_1246).getValues();
	}

	public static double[] get1456() {
		return new NSHM_GridReader(FILENAME_1456).getValues();
	}

	public static double[] get1246R() {
		return new NSHM_GridReader(FILENAME_1246_R).getValues();
	}

	public static double[] get1456R() {
		return new NSHM_GridReader(FILENAME_1456_R).getValues();
	}

//	public static GriddedGeoDataSet getUCERF2pdfAsGeoData() {
//		return readPDF_Data(FILENAME);
//	}

//	private static GriddedGeoDataSet readPDF_Data(String filename) {
//		GriddedGeoDataSet pdfData = new GriddedGeoDataSet(griddedRegion, true);	// true makes X latitude
//		GridReader reader = new GridReader(filename);
//		for (Location loc : griddedRegion) {
//			pdfData.set(loc, reader.getValue(loc));
//		}
//		return pdfData;
//	}

//	private static GriddedGeoDataSet readPDF_Data(String filename) {
//		GriddedGeoDataSet pdfData = new GriddedGeoDataSet(griddedRegion, true);	// true makes X latitude
//		try {
//			BufferedReader reader = new BufferedReader(UCERF3_DataUtils.getReader(SUBDIR, filename));
//			String line;
//			while ((line = reader.readLine()) != null) {
//				StringTokenizer tokenizer = new StringTokenizer(line);
//				Location loc = new Location(Double.valueOf(tokenizer.nextElement().toString()),Double.valueOf(tokenizer.nextElement().toString()));
//				int index = griddedRegion.indexForLocation(loc);
//				if(index >=0)
//					pdfData.set(index, Double.valueOf(tokenizer.nextElement().toString()));
//			}
//		} catch (Exception e) {
//			ExceptionUtils.throwAsRuntimeException(e);
//		}
//		
////		System.out.println("min="+pdfData.getMinZ());
////		System.out.println("max="+pdfData.getMaxZ());
////		System.out.println("sum="+getSumOfData(pdfData));
//		return pdfData;
//	}

//	/**
//	 * this normalizes the data so they sum to 1.0
//	 * @param data
//	 */
//	private static double getSumOfData(GriddedGeoDataSet data) {
//		double sum=0;
//		for(int i=0;i<data.size();i++) 
//			sum += data.get(i);
//		return sum;
//	}
//	
//	/**
//	 * The ratio here assumes equal weighting between U2 and U3 smoothed seis maps
//	 */
//	private static void plotMaps() {
//		try {
//			GriddedGeoDataSet u22pdf = readPDF_Data(FILENAME);
////			GriddedGeoDataSet u33pdfShallow = readPDF_Data(FILENAME_UCERF3pt3_SHALLOW);
//
//			// UC3 map
//			GMT_CA_Maps.plotSpatialPDF_Map(u22pdf.copy(), 
//				"NZSHM22_SmoothSeisPDF", "test meta data", 
//				"NZSHM22_SmoothSeisPFD_Map");
//			
////			// ratio of UC33shallow to UC33
////			GMT_CA_Maps.plotRatioOfRateMaps(u33pdfShallow, u33pdf, 
////				"UCERF33shallow_UCERF33_SeisPDF_Ratio", "test meta data", 
////				"UCERF33shallow_UCERF33_SeisPDF_Ratio");
////			
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//	}

}

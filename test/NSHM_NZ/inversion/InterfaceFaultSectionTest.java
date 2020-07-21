package NSHM_NZ.inversion;

import static org.junit.Assert.*;

import com.google.common.collect.Lists;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.dom4j.DocumentException;
import org.junit.BeforeClass;
import org.junit.Test;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.geo.Location;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
// import org.opensha.sha.faultSurface.RuptureSurface;
// import scratch.UCERF3.enumTreeBranches.FaultModels;

/*
 * Build FaultSections from a CSV fixture containing 9 10km * 10km subsections of the ikurangi Interface geometry.
 * And neighbor connection data
 * @author chrisbc
*/
public class InterfaceFaultSectionTest {

	private static final double grid_disc = 5d;

	//TODO move this helper to a utils.* class
	private static FaultSectionPrefData buildFSD(FaultTrace trace, double upper, double lower, double dip) {
		FaultSectionPrefData fsd = new FaultSectionPrefData();
		fsd.setFaultTrace(trace);
		fsd.setAveUpperDepth(upper);
		fsd.setAveLowerDepth(lower);
		fsd.setAveDip(dip);
		fsd.setDipDirection((float) trace.getDipDirection());
		return fsd.clone();
	}		

	private FaultSection buildFaultSectionFromCsvRow(List row) {
		// along_strike_index, down_dip_index, lon1(deg), lat1(deg), lon2(deg), lat2(deg), dip (deg), top_depth (km), bottom_depth (km),neighbours
		// [3, 9, 172.05718990191556, -43.02716092186062, 171.94629898533478, -43.06580050196082, 12.05019252859843, 36.59042136801586, 38.67810629370413, [(4, 9), (3, 10), (4, 10)]]
		
		FaultTrace trace = new FaultTrace("SubSectionTile_" + (String)row.get(0) + "_" + (String)row.get(1) );
		trace.add(new Location(Float.parseFloat((String)row.get(3)), Float.parseFloat((String)row.get(2)), 0.0));
		trace.add(new Location(Float.parseFloat((String)row.get(5)), Float.parseFloat((String)row.get(4)), 0.0));
	
		return (FaultSection)buildFSD(trace, 
			Float.parseFloat((String)row.get(7)), //top
			Float.parseFloat((String)row.get(8)), //bottom
			Float.parseFloat((String)row.get(6))); //dip	
	}


	@Test
	public void testParseSubSectionsFromCsvFixture() throws IOException {
		
		FaultSection fs = null;
		InputStream csvdata = this.getClass().getResourceAsStream("fixtures/patch_4_10.csv");
		CSVFile<String> csv = CSVFile.readStream(csvdata, false);
		
		System.out.println(csv.getHeader());

		for (int row=1; row<csv.getNumRows(); row++) {
			fs = buildFaultSectionFromCsvRow(csv.getLine(row));
		}		

		String last_trace_name = "SubSectionTile_5_11";
		assertEquals(last_trace_name, fs.getFaultTrace().getName());
		}

	@Test
	public void testBuildSubSectionsFromCsvFixture() throws IOException {
		InputStream csvdata = this.getClass().getResourceAsStream("fixtures/patch_4_10.csv");
		CSVFile<String> csv = CSVFile.readStream(csvdata, false);
		
		// System.out.println(csv.getHeader());
		FaultSectionPrefData interfaceFsd = new FaultSectionPrefData();
		List<FaultSection> subSections = Lists.newArrayList();

		for (int row=1; row<csv.getNumRows(); row++) {
			FaultSection fs = buildFaultSectionFromCsvRow(csv.getLine(row));
			subSections.add(fs);
		}
		
		System.out.println(subSections.size() + "Fault Sections");
		
		// // this list will store our subsections
		// List<FaultSection> subSections = Lists.newArrayList();
		
		// // build the subsections
		// int sectIndex = 0;
		// for (FaultSection parentSect : fsd) {
		// 	double ddw = parentSect.getOrigDownDipWidth();
		// 	double maxSectLength = ddw*maxSubSectionLength;
		// 	// the "2" here sets a minimum number of sub sections
		// 	List<? extends FaultSection> newSubSects = parentSect.getSubSectionsList(maxSectLength, sectIndex, 2);
		// 	subSections.addAll(newSubSects);
		// 	sectIndex += newSubSects.size();
		// }
		
		// System.out.println(subSections.size()+" Sub Sections");	
	}
}
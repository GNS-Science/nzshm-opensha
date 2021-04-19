package nz.cri.gns.NZSHM22.inversion;

import static org.junit.Assert.*;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.dom4j.Document;
import org.junit.Test;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.geo.Location;
import org.opensha.commons.util.IDPairing;
import org.opensha.commons.util.XMLUtils;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.SimpleFaultData;

import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.inversion.coulomb.CoulombRates;
import scratch.UCERF3.inversion.laughTest.UCERF3PlausibilityConfig;
import scratch.UCERF3.inversion.SectionClusterList;
import scratch.UCERF3.utils.DeformationModelFetcher;
import scratch.UCERF3.utils.FaultSystemIO;
import scratch.kevin.ucerf3.downDipSubSectTestOld.DownDipSubSectBuilder;
import scratch.kevin.ucerf3.downDipSubSectTestOld.DownDipTestPlausibilityConfig;
import scratch.UCERF3.inversion.UCERF3SectionConnectionStrategy;

/*
 * Build FaultSections from a CSV fixture containing 9 10km * 10km subsections of the Hikurangi Interface geometry.
 * 
 * Together with latest work by Kevin in opensha-scratch.kevin.ucerf3
 * 
 * @author chrisbc
*/
public class InterfacePlausabilityTest {

	private static final double grid_disc = 5d;

	//TODO move this helper to a utils.* class
	private static FaultSectionPrefData buildFSD(int sectionId, FaultTrace trace, double upper, double lower, double dip) {
		FaultSectionPrefData fsd = new FaultSectionPrefData();
		fsd.setSectionId(sectionId);
		fsd.setFaultTrace(trace);
		fsd.setAveUpperDepth(upper);
		fsd.setAveLowerDepth(lower);
		fsd.setAveDip(dip);
		fsd.setDipDirection((float) trace.getDipDirection());
		return fsd.clone();
	}

	private FaultSection buildFaultSectionFromCsvRow(int sectionId, List<String> row) {
		// along_strike_index, down_dip_index, lon1(deg), lat1(deg), lon2(deg), lat2(deg), dip (deg), top_depth (km), bottom_depth (km),neighbours
		// [3, 9, 172.05718990191556, -43.02716092186062, 171.94629898533478, -43.06580050196082, 12.05019252859843, 36.59042136801586, 38.67810629370413, [(4, 9), (3, 10), (4, 10)]]	
		FaultTrace trace = new FaultTrace("SubductionTile_" + (String)row.get(0) + "_" + (String)row.get(1) );
		trace.add(new Location(Float.parseFloat((String)row.get(3)), 
			Float.parseFloat((String)row.get(2)), 
			Float.parseFloat((String)row.get(7)))
		);
		trace.add(new Location(Float.parseFloat((String)row.get(5)),    //lat
			Float.parseFloat((String)row.get(4)), 						//lon
			Float.parseFloat((String)row.get(7)))						//top_depth (km)
		);
	
		return buildFSD(sectionId, trace, 
			Float.parseFloat((String)row.get(7)), //top
			Float.parseFloat((String)row.get(8)), //bottom
			Float.parseFloat((String)row.get(6))); //dip	
	}

	@Test
	public void testSubsectionBuilderMkI() {
		
		// we're going to manually build faults here
		// first, build a big fault with down-dip subsections
		String sectName = "Test SubSect Down-Dip Fault";
		int sectID = 0;
		int startID = 0;
		double upperDepth = 0d;
		double lowerDepth = 20d;
		double dip = 20d;
		int numDownDip = 3;
		int numAlongStrike = 5;
		FaultTrace trace = new FaultTrace(sectName);
		trace.add(new Location(34, -119, upperDepth));
		trace.add(new Location(34, -118, upperDepth));
		
		SimpleFaultData faultData = new SimpleFaultData(dip, lowerDepth, upperDepth, trace);
		double aveRake = 90d;
		
		DownDipSubSectBuilder builder = new DownDipSubSectBuilder(sectName, sectID, startID,
				faultData, aveRake, numAlongStrike, numDownDip);
		
		FaultSectionPrefData[][] subSections = builder.getSubSects();
		assertEquals(15, builder.getSubSectsList().size());
		
		// get some FaultSectionPrefData objects
		assertEquals(0d, subSections[4][0].getOrigAveUpperDepth(), 1e-7d);
		// check subSections dimensions
		assertEquals(5, subSections.length);
		assertEquals(3, subSections[0].length);
		assertEquals(3, subSections[4].length);
	}
	

	@Test
	public void testSubductionRuptureGeneratorSetup() throws IOException {
		InputStream csvdata = this.getClass().getResourceAsStream("patch_4_10.csv");
		CSVFile<String> csv = CSVFile.readStream(csvdata, false);
		
		FaultSectionPrefData parentSection = new FaultSectionPrefData();
		parentSection.setSectionId(10000);
		parentSection.setSectionName("ParentSection 10000 - Test SubSect Down-Dip Fault\"");

		List<FaultSection> subSections = Lists.newArrayList();
		for (int row=1; row<csv.getNumRows(); row++) {
			FaultSection fs = buildFaultSectionFromCsvRow(row-1, csv.getLine(row));
			fs.setParentSectionId(parentSection.getSectionId());
			fs.setParentSectionName(parentSection.getSectionName());
			subSections.add(fs);
		}

		System.out.println("Have "+subSections.size()+" sub-sections for " + parentSection.getSectionName());

		System.out.println(subSections.size() + " Subduction Fault Sections");

		
		for (int s=0; s<subSections.size(); s++)
			Preconditions.checkState(subSections.get(s).getSectionId() == s,
				"section at index %s has ID %s", s, subSections.get(s).getSectionId());
			
		
		// directory to write output files
		//File outputDir = new File("/tmp");

		//TODO - get a new builder 
		// instantiate our laugh test filter
		// DownDipTestPlausibilityConfig plausibility = new DownDipTestPlausibilityConfig(builder);
		
		assertEquals(9, subSections.size());
	}
}
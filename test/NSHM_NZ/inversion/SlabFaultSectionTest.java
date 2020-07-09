package NSHM_NZ.inversion;

import static org.junit.Assert.*;

import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.dom4j.DocumentException;
import org.junit.BeforeClass;
import org.junit.Test;

import org.opensha.commons.geo.Location;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.faultSurface.FaultTrace;
import scratch.UCERF3.enumTreeBranches.FaultModels;


public class SlabFaultSectionTest {

    static List<FaultSectionPrefData> fsd;
	private static FaultTrace straight_trace;
	private static Location start_loc = new Location(44, -159, 5);
	private static Location end_loc = new Location(45, -157, 5);
	
	private static FaultSectionPrefData buildFSD(FaultTrace trace, double upper, double lower, double dip) {
		FaultSectionPrefData fsd = new FaultSectionPrefData();
		fsd.setFaultTrace(trace);
		fsd.setAveUpperDepth(upper);
		fsd.setAveLowerDepth(lower);
		fsd.setAveDip(dip);
		fsd.setDipDirection((float) trace.getDipDirection());
		return fsd;
	}		

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		// this is the input fault section data file
		File fsdFile = new File("./data/FaultModels/alderman_sections.xml");
		// load in the fault section data ("parent sections")
		fsd = FaultModels.loadStoredFaultSections(fsdFile);

		straight_trace = new FaultTrace("straight");
		straight_trace.add(start_loc);
		straight_trace.add(end_loc);
	}
	
	@Test
	public void testBuildFsd() {
		FaultSectionPrefData fsd2 = buildFSD(straight_trace, 0d, 10d, 90);
		
		System.out.println("FSD name: "+fsd2.getFaultTrace().getName());		
		System.out.println("FSD toString: "+fsd2.toString());		

		assertTrue("test faultName", fsd2.getFaultTrace().getName() == "straight");
		assertTrue("test aveDip", fsd2.getAveDip() == 90);
	}

    @Test 
	public void testSlabFSD() {

		double maxSubSectionLength = 0.5;
		
		System.out.println(fsd.size()+" Parent Fault Sections");
		// this list will store our subsections
		List<FaultSectionPrefData> subSections = Lists.newArrayList();
		
		// build the subsections
		int sectIndex = 0;
		for (FaultSectionPrefData parentSect : this.fsd) {
			double ddw = parentSect.getOrigDownDipWidth();
			double maxSectLength = ddw*maxSubSectionLength;
			// the "2" here sets a minimum number of sub sections
			List<FaultSectionPrefData> newSubSects = parentSect.getSubSectionsList(maxSectLength, sectIndex, 2);
			subSections.addAll(newSubSects);
			sectIndex += newSubSects.size();
		}
		
		System.out.println(subSections.size()+" Sub Sections");
	}
}
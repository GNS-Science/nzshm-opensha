package nz.cri.gns.NZSHM22.inversion;

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
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.RuptureSurface;
import scratch.UCERF3.enumTreeBranches.FaultModels;

/*
 * Some exploratory tests, based on TestQuadSurface which is found in package org.opensha.sha
  * this tires to use the new FaultScection
 * @author chrisbc
*/
public class SurfaceFaultSectionTest {

	private static FaultTrace straight_trace;
	private static Location start_loc = new Location(44, -159, 0);
	private static Location end_loc = new Location(44, -158.2, 0);
	// private static FaultSectionPrefData fsd0;
	private static FaultSection fs0;


	private static final double grid_disc = 5d;
	//TODO move to a utils class
	private static FaultSectionPrefData buildFSD(FaultTrace trace, double upper, double lower, double dip) {
		FaultSectionPrefData fsd = new FaultSectionPrefData();
		fsd.setFaultTrace(trace);
		fsd.setAveUpperDepth(upper);
		fsd.setAveLowerDepth(lower);
		fsd.setAveDip(dip);
		fsd.setDipDirection((float) trace.getDipDirection());
		return fsd.clone();
	}		

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		FaultTrace straight_trace = new FaultTrace("straight");
		straight_trace.add(start_loc);
		straight_trace.add(end_loc);
		fs0 = (FaultSection)buildFSD(straight_trace, 0d, 10d, 66);
	}
	

	// play around with the fsd fixture we built in test setup
	@Test
	public void testFsdAsFaultSection() {
		
		// FaultSection fs0	= (FaultSection)fsd0;
		System.out.println("FaultSection name: "+fs0.getFaultTrace().getName());		
		System.out.println("FaultSection toString: "+fs0.toString());		

		assertTrue("test faultName", fs0.getFaultTrace().getName() == "straight");
		assertTrue("test aveDip", fs0.getAveDip() == 66);
	}


	// A FaultSection  can create a StirlingGriddedSurface object
	/*
 		TODO: should FaultSection provide this method at all?
	*/
	// @Test
	// public void testFsdAsStirlingGriddedSurface() {

	// 	// fs0	= (FaultSection)fsd0.clone();
	// 	RuptureSurface stirling_gridded = fs0.getStirlingGriddedSurface(grid_disc); //, false, false);

	// 	System.out.println("stirling_gridded toString: "+stirling_gridded.toString());		
	// 	System.out.println("stirling_gridded getInfo: "+stirling_gridded.getInfo());		

	// 	assertTrue("stirling_gridded.aveDip == fsd.aveDip", stirling_gridded.getAveDip() == fs0.getAveDip());
	// }

	// A FaultSection can create a QuadSurface object
	// @Test
	// public void testFsdAsQuadSurface() {
		
	// 	FaultSection fs	= (FaultSection)fsd0;

	// 	RuptureSurface quad_gridded = fsd0.getQuadSurface(false, grid_disc);

	// 	System.out.println("quad_gridded toString: "+quad_gridded.toString());		
	// 	System.out.println("quad_gridded getInfo: "+quad_gridded.getInfo());		

	// 	assertTrue("quad_gridded.aveDip == fsd.aveDip", quad_gridded.getAveDip() == fsd0.getAveDip());
	// }
}
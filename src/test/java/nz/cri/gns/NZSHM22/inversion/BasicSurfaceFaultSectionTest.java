package nz.cri.gns.NZSHM22.inversion;

import static org.junit.Assert.*;

import org.junit.BeforeClass;
import org.junit.Test;

import org.opensha.commons.geo.Location;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.RuptureSurface;

/*
 * Some exploratory tests, based on TestQuadSurface which is found in package org.opensha.sha
 * note this was written just before opensha-core chaanges to FaultSectionPrefData
 * but survived these so far
 * 
 * @author chrisbc
*/
public class BasicSurfaceFaultSectionTest {

	private static FaultTrace straight_trace;
	private static Location start_loc = new Location(44, -159, 0);
	private static Location end_loc = new Location(44, -158.2, 0);
	private static FaultSectionPrefData fsd0;

	private static final double grid_disc = 5d;
	//TODO mode to a utils class
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
		FaultTrace straight_trace = new FaultTrace("straight");
		straight_trace.add(start_loc);
		straight_trace.add(end_loc);
		fsd0 = buildFSD(straight_trace, 0d, 10d, 66);
	}
	
	// play around with the fsd fixture we built in test setup
	@Test
	public void testFsdFixture() {
		
		System.out.println("FSD name: "+fsd0.getFaultTrace().getName());		
		System.out.println("FSD toString: "+fsd0.toString());		

		assertTrue("test faultName", fsd0.getFaultTrace().getName() == "straight");
		assertTrue("test aveDip", fsd0.getAveDip() == 66);
	}

	// An fsd can create a StirlingGriddedSurface object
	@Test
	public void testFsdAsStirlingGriddedSurface() {

		RuptureSurface stirling_gridded = fsd0.getStirlingGriddedSurface(grid_disc); //, false, false);

		System.out.println("stirling_gridded toString: "+stirling_gridded.toString());		
		System.out.println("stirling_gridded getInfo: "+stirling_gridded.getInfo());		

		assertTrue("stirling_gridded.aveDip == fsd.aveDip", stirling_gridded.getAveDip() == fsd0.getAveDip());
	}

	// An fsd can create a QuadSurface object
	@Test
	public void testFsdAsQuadSurface() {

		RuptureSurface quad_gridded = fsd0.getQuadSurface(false, grid_disc);

		System.out.println("quad_gridded toString: "+quad_gridded.toString());		
		System.out.println("quad_gridded getInfo: "+quad_gridded.getInfo());		

		assertTrue("quad_gridded.aveDip == fsd.aveDip", quad_gridded.getAveDip() == fsd0.getAveDip());
	}
}
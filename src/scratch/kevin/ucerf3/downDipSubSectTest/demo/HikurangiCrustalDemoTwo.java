package scratch.kevin.ucerf3.downDipSubSectTest.demo;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
//import java.util.HashMap;
import java.util.List;
//import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.imageio.stream.FileImageInputStream;

import org.dom4j.DocumentException;
import org.opensha.commons.geo.Location;
import org.opensha.commons.util.FaultUtils;
import org.opensha.commons.util.IDPairing;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRuptureBuilder;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterPermutationStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.DistCutoffClosestSectClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.SimpleFaultData;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;

import scratch.UCERF3.utils.FaultSystemIO;
import scratch.kevin.ucerf3.downDipSubSectTest.DownDipSubSectBuilder;
import scratch.kevin.ucerf3.downDipSubSectTest.DownDipTestPermutationStrategy;
import scratch.kevin.ucerf3.downDipSubSectTest.RectangularityFilter;

public class HikurangiCrustalDemoTwo {

	static DownDipSubSectBuilder downDipBuilder;
	
	public static void main(String[] args) throws DocumentException, IOException {
		// maximum sub section length (in units of DDW)
		double maxSubSectionLength = 0.5;
		// max distance for linking multi fault ruptures, km
		//double maxDistance = 0.5d;
		
		File outputFile = new File("/tmp/rupSetLowerNIAndInterface30km.zip");
	
		File fsdFile = new File("./data/FaultModels/cfm_test.xml");
		
		// load in the fault section data ("parent sections")
		List<FaultSection> fsd = FaultModels.loadStoredFaultSections(fsdFile);
		
		Stream<Integer> myStream01 = Stream.of(83, 84, 85, 86, 87);
		Collection<Integer> sectsWairarapa = myStream01.collect(Collectors.toCollection(ArrayList::new));

		Stream<Integer> myStream02 = Stream.of(89, 90, 91, 92, 93);
		Collection<Integer> sectsWellington = myStream02.collect(Collectors.toCollection(ArrayList::new));
		
		List<Integer> sectsToKeep = Lists.newArrayList(Iterables.concat(sectsWairarapa, sectsWellington));

   	    if (sectsToKeep != null && !sectsToKeep.isEmpty()) {
		 	System.out.println("Only keeping these parent fault sections: "
		 			+Joiner.on(",").join(sectsToKeep));
		 	// iterate backwards as we will be removing from the list
		 	for (int i=fsd.size(); --i>=0;)
		 		if (!sectsToKeep.contains(fsd.get(i).getSectionId()))
		 			fsd.remove(i);
		 }
			
		
		// build the subsections
		List<FaultSection> subSections = new ArrayList<>();
		int sectIndex = 0;
		for (FaultSection parentSect : fsd) {
			double ddw = parentSect.getOrigDownDipWidth();
			double maxSectLength = ddw*maxSubSectionLength;
			// the "2" here sets a minimum number of sub sections
			List<? extends FaultSection> newSubSects = parentSect.getSubSectionsList(maxSectLength, sectIndex, 2);
			subSections.addAll(newSubSects);
			sectIndex += newSubSects.size();
		}
		
		System.out.println(subSections.size()+" Sub Sections");
		
		String sectName = "Interface Fault 30km";
//		int sectID = 0;
		int startID = subSections.size();
		
		FaultSection interfaceParentSection = new FaultSectionPrefData();
		interfaceParentSection.setSectionId(10000);
		interfaceParentSection.setSectionName(sectName);
				
		File initialFile = new File("./data/FaultModels/subduction_tile_parameters_30.csv");
	    InputStream inputStream = new FileInputStream(initialFile);
		downDipBuilder = new DownDipSubSectBuilder(sectName, interfaceParentSection, startID, inputStream);
		
		// Add the interface subsections
		subSections.addAll(downDipBuilder.getSubSectsList());
		
		System.out.println("Have "+subSections.size()+" sub-sections in total");		
		
		for (int s=0; s<subSections.size(); s++)
			Preconditions.checkState(subSections.get(s).getSectionId() == s,
				"section at index %s has ID %s", s, subSections.get(s).getSectionId());
		
		// instantiate plausibility filters
		List<PlausibilityFilter> filters = new ArrayList<>();
		int minDimension = 1; // minimum numer of rows or columns
		double maxAspectRatio = 5d; // max aspect ratio of rows/cols or cols/rows
		filters.add(new RectangularityFilter(downDipBuilder, minDimension, maxAspectRatio));
		
		SectionDistanceAzimuthCalculator distAzCalc = new SectionDistanceAzimuthCalculator(subSections);
		
		// this creates rectangular permutations only for our down-dip fault to speed up rupture building
		ClusterPermutationStrategy permutationStrategy = new DownDipTestPermutationStrategy(downDipBuilder);
		// connection strategy: parent faults connect at closest point, and only when dist <=5 km
		ClusterConnectionStrategy connectionStrategy = new DistCutoffClosestSectClusterConnectionStrategy(5d);
		int maxNumSplays = 0; // don't allow any splays
		
		ClusterRuptureBuilder builder = new ClusterRuptureBuilder(subSections, connectionStrategy,
				distAzCalc, filters, maxNumSplays);
		
		List<ClusterRupture> ruptures = builder.build(permutationStrategy);
		
		System.out.println("Built "+ruptures.size()+" total ruptures");
		
		// build a rupture set (doing this manually instead of creating an inversion fault system rup set,
		// mostly as a demonstration)
		double[] sectSlipRates = new double[subSections.size()];
		double[] sectAreasReduced = new double[subSections.size()];
		double[] sectAreasOrig = new double[subSections.size()];
		for (int s=0; s<sectSlipRates.length; s++) {
			FaultSection sect = subSections.get(s);
			sectAreasReduced[s] = sect.getArea(true);
			sectAreasOrig[s] = sect.getArea(false);
			sectSlipRates[s] = sect.getReducedAveSlipRate()*1e-3; // mm/yr => m/yr
		}
		
		double[] rupMags = new double[ruptures.size()];
		double[] rupRakes = new double[ruptures.size()];
		double[] rupAreas = new double[ruptures.size()];
		double[] rupLengths = new double[ruptures.size()];
		ScalingRelationships scale = ScalingRelationships.SHAW_2009_MOD;
		List<List<Integer>> rupsIDsList = new ArrayList<>();
		for (int r=0; r<ruptures.size(); r++) {
			ClusterRupture rup = ruptures.get(r);
			List<FaultSection> rupSects = rup.buildOrderedSectionList();
			List<Integer> sectIDs = new ArrayList<>();
			double totLength = 0d;
			double totArea = 0d;
			double totOrigArea = 0d; // not reduced for aseismicity
			List<Double> sectAreas = new ArrayList<>();
			List<Double> sectRakes = new ArrayList<>();
			for (FaultSection sect : rupSects) {
				sectIDs.add(sect.getSectionId());
				double length = sect.getTraceLength()*1e3;	// km --> m
				totLength += length;
				double area = sectAreasReduced[sect.getSectionId()];	// sq-m
				totArea += area;
				totOrigArea += sectAreasOrig[sect.getSectionId()];	// sq-m
				sectAreas.add(area);
				sectRakes.add(sect.getAveRake());
			}
			rupAreas[r] = totArea;
			rupLengths[r] = totLength;
			rupRakes[r] = FaultUtils.getInRakeRange(FaultUtils.getScaledAngleAverage(sectAreas, sectRakes));
			double origDDW = totOrigArea/totLength;
			rupMags[r] = scale.getMag(totArea, origDDW);
			rupsIDsList.add(sectIDs);
		}
		
		String info = "Test down-dip subsectioning rup set";
		FaultSystemRupSet rupSet = new FaultSystemRupSet(subSections, sectSlipRates, null, sectAreasReduced,
				rupsIDsList, rupMags, rupRakes, rupAreas, rupLengths, info);
		FaultSystemIO.writeRupSet(rupSet, outputFile);
		
		System.out.println("All done!"); 
	}
}

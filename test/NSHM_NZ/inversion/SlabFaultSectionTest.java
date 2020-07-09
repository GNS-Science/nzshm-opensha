package NSHM_NZ.inversion;

import java.io.File;
// import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
// import org.dom4j.Document;
import org.dom4j.DocumentException;

import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import scratch.UCERF3.enumTreeBranches.FaultModels;

import com.google.common.collect.Lists;


import org.junit.BeforeClass;
import org.junit.Test;
// import org.opensha.commons.data.Container2DImpl;

public class SlabFaultSectionTest {

    static List<FaultSectionPrefData> fsd;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {

		// this is the input fault section data file
		File fsdFile = new File("./data/FaultModels/sectionsv5_full_testlabe7.xml");
		// load in the fault section data ("parent sections")
		fsd = FaultModels.loadStoredFaultSections(fsdFile);

	}
	

    @Test 
	public void testSlabFSD() {

		// directory to write output files
		// File outputDir = new File("./data/output");
		// maximum sub section length (in units of DDW)
		
		double maxSubSectionLength = 0.5;
		// max distance for linking multi fault ruptures, km
		double maxDistance = 0.5d;
		boolean coulombFilter = false;
		//FaultModels fm = FaultModels.FM3_1;
		FaultModels fm = null;

		// // this is a list of parent fault sections to remove. can be empty or null
		// // currently set to remove Garlock to test Coulomb remapping.
		// //List<Integer> sectsToRemove = Lists.newArrayList(49, 341);
		// List<Integer> sectsToRemove = Lists.newArrayList();
		// // this is a list of sections to keep. if non null and non empty, only these
		// // ids will be kept
		// List<Integer> sectsToKeep = Lists.newArrayList();
		// //Preconditions.checkState(!coulombFilter || fm == FaultModels.FM3_1 || fm == FaultModels.FM3_2);
		
		// load in the fault section data ("parent sections")
		//List<FaultSectionPrefData> fsd = FaultModels.loadStoredFaultSections(fsdFile);
		
		// if (sectsToRemove != null && !sectsToRemove.isEmpty()) {
		// 	System.out.println("Removing these parent fault sections: "
		// 			+Joiner.on(",").join(sectsToRemove));
		// 	// iterate backwards as we will be removing from the list
		// 	for (int i=fsd.size(); --i>=0;)
		// 		if (sectsToRemove.contains(fsd.get(i).getSectionId()))
		// 			fsd.remove(i);
		// }
		
		// if (sectsToKeep != null && !sectsToKeep.isEmpty()) {
		// 	System.out.println("Only keeping these parent fault sections: "
		// 			+Joiner.on(",").join(sectsToKeep));
		// 	// iterate backwards as we will be removing from the list
		// 	for (int i=fsd.size(); --i>=0;)
		// 		if (!sectsToKeep.contains(fsd.get(i).getSectionId()))
		// 			fsd.remove(i);
		// }
		
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
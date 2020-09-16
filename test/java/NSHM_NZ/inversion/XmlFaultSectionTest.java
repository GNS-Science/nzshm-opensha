package NSHM_NZ.inversion;

import java.io.BufferedInputStream;
import java.util.List;

import org.dom4j.Document;
import org.dom4j.io.SAXReader;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.collect.Lists;

import scratch.UCERF3.enumTreeBranches.FaultModels;

/*
 * Some exploratory tests, based StandaloneSubSectRupGen which is found in package opensha-dev::scratch.kevin.ucerf3
 * @author chrisbc
*/
public class XmlFaultSectionTest {

    static List<FaultSection> fsd;
    
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		// this is the input fault section data file
		BufferedInputStream fsdFile = (BufferedInputStream) XmlFaultSectionTest.class.getResourceAsStream("alderman_sections.xml");
		SAXReader reader = new SAXReader();
	    Document doc = reader.read(fsdFile);
		fsd = FaultModels.loadStoredFaultSections(doc);
	}
	
    @Test 
	public void loadFSDfromXml() {

		double maxSubSectionLength = 0.5;
		
		System.out.println(fsd.size()+" Parent Fault Sections");
		// this list will store our subsections
		List<FaultSection> subSections = Lists.newArrayList();
		
		// build the subsections
		int sectIndex = 0;
		for (FaultSection parentSect : this.fsd) {
			double ddw = parentSect.getOrigDownDipWidth();
			double maxSectLength = ddw*maxSubSectionLength;
			// the "2" here sets a minimum number of sub sections
			List<? extends FaultSection> newSubSects = parentSect.getSubSectionsList(maxSectLength, sectIndex, 2);
			subSections.addAll(newSubSects);
			sectIndex += newSubSects.size();
		}
		
		System.out.println(subSections.size()+" Sub Sections");
	}
}
package scratch.kevin.ucerf3;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.opensha.commons.util.IDPairing;
import org.opensha.commons.util.XMLUtils;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import nz.cri.gns.NSHM.opensha.util.FaultSectionList;
import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;
import scratch.UCERF3.inversion.InversionFaultSystemRupSet;
import scratch.UCERF3.inversion.InversionFaultSystemRupSetFactory;
import scratch.UCERF3.inversion.SectionCluster;
import scratch.UCERF3.inversion.SectionClusterList;
import scratch.UCERF3.inversion.coulomb.CoulombRates;
import scratch.UCERF3.inversion.coulomb.CoulombRatesRecord;
import scratch.UCERF3.inversion.coulomb.CoulombRatesTester;
import scratch.UCERF3.inversion.coulomb.CoulombRatesTester.TestType;
import scratch.UCERF3.inversion.laughTest.UCERF3PlausibilityConfig;
import scratch.UCERF3.logicTree.LogicTreeBranch;
import scratch.UCERF3.utils.DeformationModelFetcher;
import scratch.UCERF3.utils.FaultSystemIO;
import scratch.UCERF3.utils.UCERF3_DataUtils;

import scratch.UCERF3.inversion.UCERF3SectionConnectionStrategy;

public class StandaloneSubSectRupGenMG {

	/**
	 * Create opensha rupture sets for NZ surface fault sections as simply as possible.
	 * 
	 * Based on https://github.com/opensha/opensha-dev/blob/a36d4f49d3fe1a70b86f3ab4b3e2280352a21ecb/src/scratch/kevin/scratch.kevin.ucerf3/StandaloneSubSectRupGen.java
     * with simlifications by MattG
     *
	 * 
	 * @param args
	 * @throws DocumentException 
	 * @throws IOException 
	 */
	public static void main(String[] args) throws DocumentException, IOException {
		// this is the input fault section data file
//		File fsdFile = new File("./data/FaultModels/DEMO2_crustal_opensha.xml"); 
		File fsdFile = new File("./data/FaultModels/DEMO2_DIPFIX_crustal_opensha.xml");
//		File fsdFile = new File("./data/FaultModels/sectionsv5_full_testlabe7.xml");
		// directory to write output files
		File outputDir = new File("./data/output");
		// maximum sub section length (in units of DDW)
		double maxSubSectionLength = 0.5;
		// max distance for linking multi fault ruptures, km
		double maxDistance = 2d;
		long maxFaultSections = 1000; // maximum fault ruptures to process
		long skipFaultSections = 0; // skip n fault ruptures, default 0"
		
		boolean coulombFilter = false;
		//FaultModels fm = FaultModels.FM3_1;
		FaultModels fm = null;

		// load in the fault section data ("parent sections")
		FaultSectionList fsd = FaultSectionList.fromList(FaultModels.loadStoredFaultSections(fsdFile));
		System.out.println("Fault model has "+fsd.size()+" fault sections");
		
		if (maxFaultSections < 1000 || skipFaultSections > 0) {
			final long endSection = maxFaultSections + skipFaultSections;
			final long skipSections = skipFaultSections;
			fsd.removeIf(section -> section.getSectionId() >= endSection || section.getSectionId() < skipSections);
			System.out.println("Fault model now has "+fsd.size()+" fault sections");
		}		
		
		// this list will store our subsections
		FaultSectionList subSections = new FaultSectionList(fsd);
		// build the subsections
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
		
		// write subsection data to file
		File subSectDataFile = new File(outputDir, "sub_sections.xml");
		Document doc = XMLUtils.createDocumentWithRoot();
		FaultSystemIO.fsDataToXML(doc.getRootElement(), FaultModels.XML_ELEMENT_NAME, null, null, subSections);
		XMLUtils.writeDocumentToFile(subSectDataFile, doc);

		// calculate distances between each subsection
		Map<IDPairing, Double> subSectionDistances = DeformationModelFetcher.calculateDistances(maxDistance, subSections);
		Map<IDPairing, Double> reversed = Maps.newHashMap();
		// now add the reverse distance
		for (IDPairing pair : subSectionDistances.keySet()) {
			IDPairing reverse = pair.getReversed();
			reversed.put(reverse, subSectionDistances.get(pair));
		}
		subSectionDistances.putAll(reversed);
		Map<IDPairing, Double> subSectionAzimuths = DeformationModelFetcher.getSubSectionAzimuthMap(
				subSectionDistances.keySet(), subSections);
	

		// instantiate our laugh test filter
		UCERF3PlausibilityConfig laughTest = NZSHMPlausibilityConfig.getDefault();
//		laughTest.setMaxCmlmJumpDist(Double.MAX_VALUE); // 5d); 	// has no effect here as it's a junction only test
		laughTest.setMaxJumpDist(maxDistance); 		// looks like this might only impact (parent) section jumps
		// laughTest.setMaxAzimuthChange(Double.MAX_VALUE); // azimuth change constraints makes no sense with 2 axes 
		// laughTest.setMaxCmlAzimuthChange(Double.MAX_VALUE);
//		laughTest.setMinNumSectInRup(2); 		//disable min sections = 0, default=2
//		laughTest.setAllowSingleSectDuringJumps(true);
		laughTest.setMaxCmlRakeChange(Double.MAX_VALUE); //Disable this filter
		
		//disable our coulomb filter as it uses a data file specific to SCEC subsections
		CoulombRates coulombRates  = null;
		laughTest.setCoulombFilter(null);

		// A section connection strategy is now required, let's use the default UCERF3...
		UCERF3SectionConnectionStrategy connectionStrategy = new UCERF3SectionConnectionStrategy(
				laughTest.getMaxAzimuthChange(), coulombRates);

		// this separates the sub sections into clusters which are all within maxDist of each other and builds ruptures
		// fault model and deformation model here are needed by InversionFaultSystemRuptSet later, just to create a rup set
		// zip file

		// SectionClusterList clusters = new SectionClusterList(
		// 		fm, DeformationModels.GEOLOGIC, laughTest, coulombRates, subSections, subSectionDistances, sectionAzimuths);
		SectionClusterList clusters = new SectionClusterList(
				connectionStrategy, laughTest, subSections, subSectionDistances, subSectionAzimuths);

		List<List<Integer>> ruptures = Lists.newArrayList();
		for (SectionCluster cluster : clusters) {
			ruptures.addAll(cluster.getSectionIndicesForRuptures());
		}
		
		System.out.println("Created "+ruptures.size()+" ruptures");
		
		// write rupture/subsection associations to file
		// format: rupID	sectID1,sectID2,sectID3,...,sectIDN
		File rupFile = new File(outputDir, "ruptures.txt");
		FileWriter fw = new FileWriter(rupFile);
		Joiner j = Joiner.on(",");
		for (int i=0; i<ruptures.size(); i++) {
			fw.write(i+"\t"+j.join(ruptures.get(i))+"\n");
		}
		fw.close();
		
		// build actual rupture set for magnitudes and such
//		LogicTreeBranch branch = LogicTreeBranch.fromValues(fm, DeformationModels.GEOLOGIC,
//				ScalingRelationships.SHAW_2009_MOD, SlipAlongRuptureModels.TAPERED);
//		InversionFaultSystemRupSet rupSet = new InversionFaultSystemRupSet(branch, clusters, subSections);
//		
//		File zipFile = new File(outputDir, "rupSet.zip");
//		FaultSystemIO.writeRupSet(rupSet, zipFile);
	}
	
	public static CoulombRates remapCoulombRates(List<FaultSection> subSections, FaultModels fm) throws IOException {
		// original coulomb rates
		CoulombRates origRates = CoulombRates.loadUCERF3CoulombRates(fm);
		
		// now load the original subsections
		List<? extends FaultSection> origSubSects = new DeformationModelFetcher(
				fm, DeformationModels.GEOLOGIC, UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR,
				InversionFaultSystemRupSetFactory.DEFAULT_ASEIS_VALUE).getSubSectionList();
		
		Map<IDPairing, CoulombRatesRecord> rates = Maps.newHashMap();
		
		Map<FaultSection, FaultSection> remappings = Maps.newHashMap();
		for (FaultSection sect : origSubSects)
			remappings.put(sect, getRemappedSubSect(sect, subSections));
		
		for (IDPairing pair : origRates.keySet()) {
			FaultSection subSect1 = origSubSects.get(pair.getID1());
			FaultSection subSect2 = origSubSects.get(pair.getID2());
			
			FaultSection mapped1 = remappings.get(subSect1);
			FaultSection mapped2 = remappings.get(subSect2);
			
			if (mapped1 != null && mapped2 != null) {
				CoulombRatesRecord origRec = origRates.get(pair);
				rates.put(new IDPairing(mapped1.getSectionId(), mapped2.getSectionId()), origRec);
			}
		}

		// FIXME
		//return new CoulombRates(rates);
		return null;
	}
	
	private static FaultSection getRemappedSubSect(FaultSection origSubSect,
			List<FaultSection> newSubSects) {
		for (FaultSection newSect : newSubSects) {
			if (newSect.getParentSectionId() != origSubSect.getParentSectionId())
				continue;
			if (newSect.getSectionName().equals(origSubSect.getSectionName()))
				return newSect;
		}
		return null;
	}

	/**
	 * This class over-rides the getDefault() method so the we can disable
	 * the hard-coded applyGarlockPintoMtnFix in the UCERF3 base class
	 * 
	 * @return
	 */
	private static class NZSHMPlausibilityConfig extends UCERF3PlausibilityConfig {
		
		public NZSHMPlausibilityConfig(double maxJumpDist, double maxAzimuthChange,
				double maxTotAzimuthChange, double maxCumJumpDist, double maxCmlRakeChange, double maxCmlAzimuthChange,
				int minNumSectInRup, boolean allowSingleSectDuringJumps, CoulombRatesTester coulombTester,
				boolean applyGarlockPintoMtnFix) {
			
			super(maxJumpDist, maxAzimuthChange, maxTotAzimuthChange, maxCumJumpDist, maxCmlRakeChange, maxCmlAzimuthChange,
					minNumSectInRup, allowSingleSectDuringJumps, coulombTester, applyGarlockPintoMtnFix);
			// TODO Auto-generated constructor stub
		}

		public static UCERF3PlausibilityConfig getDefault() {
			double maxAzimuthChange = 60;
			double maxJumpDist = 5d;
			double maxCumJumpDist = Double.POSITIVE_INFINITY;
			double maxTotAzimuthChange = 60d;
			int minNumSectInRup = 2;
			double maxCmlRakeChange = 180;
			double maxCmlAzimuthChange = 560;
			boolean allowSingleSectDuringJumps = true;
			double minAverageProb = 0.04;
			double minIndividualProb = 0.04;
			double minimumStressExclusionCeiling = 1.25d;
			// if true the coulomb filter will only be applied at branch points
			boolean applyBranchesOnly = true;
			boolean allowAnyWay = true;		
			
			CoulombRatesTester coulombTester = new CoulombRatesTester(
					TestType.COULOMB_STRESS, minAverageProb, minIndividualProb,
					minimumStressExclusionCeiling, applyBranchesOnly, allowAnyWay);
			
			// This is not helping us at all
			boolean applyGarlockPintoMtnFix = false;
			
			return new UCERF3PlausibilityConfig(maxJumpDist, maxAzimuthChange, maxTotAzimuthChange,
					maxCumJumpDist, maxCmlRakeChange, maxCmlAzimuthChange, minNumSectInRup,
					allowSingleSectDuringJumps, coulombTester, applyGarlockPintoMtnFix);
		}
	}
}

package nz.cri.gns.NSHM.opensha.ruptures;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.dom4j.DocumentException;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRuptureBuilder;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.CumulativeAzimuthChangeFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.JumpAzimuthChangeFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.MinSectsPerParentFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.TotalAzimuthChangeFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterPermutationStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ConnectionPointsPermutationStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.DistCutoffClosestSectClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.UCERF3ClusterPermuationStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

import nz.cri.gns.NSHM.opensha.ruptures.downDipSubSectTest.DownDipSubSectBuilder;
import nz.cri.gns.NSHM.opensha.ruptures.downDipSubSectTest.DownDipTestPermutationStrategy;
import nz.cri.gns.NSHM.opensha.ruptures.downDipSubSectTest.SubSectionParentFilter;
import nz.cri.gns.NSHM.opensha.util.FaultSectionList;
import scratch.UCERF3.SlipAlongRuptureModelRupSet;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;

public class NSHMRuptureSetBuilder {

	static DownDipSubSectBuilder downDipBuilder;	
	Set<Integer> faultIdIn = Collections.emptySet();
	
	double maxSubSectionLength = 0.5; 	// maximum sub section length (in units of DDW)
	double maxDistance = 5; 			// max distance for linking multi fault ruptures, km
	long maxFaultSections = 100000; 	// maximum fault ruptures to process
	long skipFaultSections = 0; 		// skip n fault ruptures, default 0"
	int numThreads = Runtime.getRuntime().availableProcessors(); // use all available processors
	int minSubSectsPerParent = 2;		// 2 are required for UCERf3 azimuth calcs
	RupturePermutationStrategy permutationStrategyClass = RupturePermutationStrategy.DOWNDIP;
	
	public enum RupturePermutationStrategy {
		DOWNDIP, UCERF3, POINTS,
	}
		
	public NSHMRuptureSetBuilder () {
		FaultSection interfaceParentSection = new FaultSectionPrefData();
		interfaceParentSection.setSectionId(10000);
		downDipBuilder = new DownDipSubSectBuilder(interfaceParentSection);
	}

	/* 
	 * Builder pattern setter methods
	 */

	public NSHMRuptureSetBuilder setFaultIdIn(Set<Integer> faultIdIn) {
		this.faultIdIn = faultIdIn;
		return this;
	}

	public NSHMRuptureSetBuilder setMaxJumpDistance(double maxDistance) {
		this.maxDistance = maxDistance;
		return this;
	}

	public NSHMRuptureSetBuilder setMaxFaultSections(int maxFaultSections) {
		this.maxFaultSections = maxFaultSections;
		return this;
	}

	public NSHMRuptureSetBuilder setSkipFaultSections(int skipFaultSections) {
		this.skipFaultSections = skipFaultSections;
		return this;
	}
	
	public NSHMRuptureSetBuilder setMinSubSectsPerParent(int minSubSectsPerParent) {
		this.minSubSectsPerParent = minSubSectsPerParent;
		return this;
	}

	public NSHMRuptureSetBuilder setMaxSubSectionLength(double maxSubSectionLength) {
		this.maxSubSectionLength = maxSubSectionLength;
		return this;
	}

	public NSHMRuptureSetBuilder setPermutationStrategy(RupturePermutationStrategy permutationStrategyClass) {
		this.permutationStrategyClass = permutationStrategyClass;
		return this;
	}
	
	public NSHMRuptureSetBuilder setNumThreads(int numThreads) {
		this.numThreads = numThreads;
		return this;
	}
	
	private ClusterPermutationStrategy createPermutationStrategy(RupturePermutationStrategy permutationStrategyClass) {
		ClusterPermutationStrategy permutationStrategy = null;
		switch (permutationStrategyClass) {
			case DOWNDIP:
				/* for down dip creates rectangular permutations to speed up rupture building
				*  for crustal , it uses something like UCERF3
				*/   
				permutationStrategy = new DownDipTestPermutationStrategy(downDipBuilder);
				break;
			case POINTS:
				// creates ruptures in blocks defined by the connection points between clusters
				permutationStrategy = new ConnectionPointsPermutationStrategy();
				break;
			case UCERF3:
				// creates ruptures covering the incremental permutations of sub-sections in each cluster 
				permutationStrategy = new UCERF3ClusterPermuationStrategy();
				break;
		}
		return permutationStrategy;
	}

	public SlipAlongRuptureModelRupSet buildRuptureSet(File fsdFile) throws DocumentException, IOException {
		
		// load in the fault section data ("parent sections")
		FaultSectionList fsd = FaultSectionList.fromList(FaultModels.loadStoredFaultSections(fsdFile));
		System.out.println("Fault model has "+fsd.size()+" fault sections");
		
		if (maxFaultSections < 1000 || skipFaultSections > 0) {
			final long endSection = maxFaultSections + skipFaultSections;
			final long skipSections = skipFaultSections;
			fsd.removeIf(section -> section.getSectionId() >= endSection || section.getSectionId() < skipSections);
			System.out.println("Fault model now has "+fsd.size()+" fault sections");
		}
		
		// build the subsections
		FaultSectionList subSections = new FaultSectionList(fsd);
		for (FaultSection parentSect : fsd) {
			double ddw = parentSect.getOrigDownDipWidth();
			double maxSectLength = ddw*maxSubSectionLength;
			System.out.println("Get subSections in "+parentSect.getName());
			// the "2" here sets a minimum number of sub sections
			List<? extends FaultSection> newSubSects = parentSect.getSubSectionsList(maxSectLength, subSections.getSafeId(), 2);
			subSections.addAll(newSubSects);
			System.out.println("Produced "+newSubSects.size()+" subSections in "+parentSect.getName());
		}		
		System.out.println(subSections.size()+" Sub Sections");
	
		// @voj is this redundant now we're using FaultSectionList??
		for (int s=0; s<subSections.size(); s++)
			Preconditions.checkState(subSections.get(s).getSectionId() == s,
				"section at index %s has ID %s", s, subSections.get(s).getSectionId());
			
		SectionDistanceAzimuthCalculator distAzCalc = new SectionDistanceAzimuthCalculator(subSections);
		JumpAzimuthChangeFilter.AzimuthCalc azimuthCalc = new JumpAzimuthChangeFilter.SimpleAzimuthCalc(distAzCalc);
		
		// connection strategy: parent faults connect at closest point, and only when dist <=5 km
		ClusterConnectionStrategy connectionStrategy = new DistCutoffClosestSectClusterConnectionStrategy(subSections, distAzCalc, maxDistance);
		System.out.println("Built connectionStrategy");
		
		int maxNumSplays = 0; // don't allow any splays
	
		Predicate<FaultSubsectionCluster> pFilter = new SubSectionParentFilter().makeParentIdFilter(faultIdIn);
		PlausibilityConfiguration config =
				PlausibilityConfiguration.builder(connectionStrategy, distAzCalc)
						.maxSplays(maxNumSplays)
						.add(new SubSectionParentFilter(pFilter))
						.add(new JumpAzimuthChangeFilter(azimuthCalc, 60f))
						.add(new TotalAzimuthChangeFilter(azimuthCalc, 60f, true, true))
						.add(new CumulativeAzimuthChangeFilter(azimuthCalc, 560f))
						.add(new MinSectsPerParentFilter(minSubSectsPerParent, true, true, connectionStrategy))
						.build();
		System.out.println("Built PlausibilityConfiguration");
		
		// Builder can now proceed using the clusters and all the filters...
		ClusterRuptureBuilder builder = new ClusterRuptureBuilder(config);
		System.out.println("initialised ClusterRuptureBuilder");
		
		ClusterPermutationStrategy permutationStrategy = createPermutationStrategy(permutationStrategyClass);
				
		List<ClusterRupture> ruptures = builder.build(permutationStrategy, numThreads);
		System.out.println("Built "+ruptures.size()+" total ruptures");
		
		NSHMSlipEnabledRuptureSet rupSet = new NSHMSlipEnabledRuptureSet(ruptures, subSections,
				ScalingRelationships.SHAW_2009_MOD, SlipAlongRuptureModels.UNIFORM);
		
		return (SlipAlongRuptureModelRupSet) rupSet;
	}			
}

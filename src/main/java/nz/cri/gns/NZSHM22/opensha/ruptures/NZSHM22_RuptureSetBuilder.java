package nz.cri.gns.NZSHM22.opensha.ruptures;

import java.io.*;
import java.net.MalformedURLException;
import java.util.List;
import java.util.Set;

import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import org.dom4j.DocumentException;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRuptureBuilder;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.JumpAzimuthChangeFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.MinSectsPerParentFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.TotalAzimuthChangeFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.RuptureGrowingStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ConnectionPointsRuptureGrowingStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ExhaustiveUnilateralRuptureGrowingStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;

import org.opensha.sha.faultSurface.FaultSection;

import nz.cri.gns.NZSHM22.opensha.ruptures.downDip.*;
import nz.cri.gns.NZSHM22.opensha.util.FaultSectionList;
import scratch.UCERF3.SlipAlongRuptureModelRupSet;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;

/**
 * Builds opensha SlipAlongRuptureModelRupSet rupture sets using NZ NSHM
 * configurations for: - plausability - rupture permutation Strategy (with
 * different strategies available for test purposes)
 */
public class NZSHM22_RuptureSetBuilder {

	FaultSectionList subSections;
	List<ClusterRupture> ruptures;
	PlausibilityConfiguration plausibilityConfig;
	ClusterRuptureBuilder builder;

	File fsdFile = null;
	File downDipFile = null;
	String downDipFaultName = null;
	Set<Integer> faultIds;
	FaultIdFilter.FilterType faultIdfilterType = null;
	NZSHM22_FaultModels faultModel = null;

	double maxSubSectionLength = 0.5; // maximum sub section length (in units of DDW)
	double maxDistance = 5; // max distance for linking multi fault ruptures, km
	long maxFaultSections = 100000; // maximum fault ruptures to process
	long skipFaultSections = 0; // skip n fault ruptures, default 0"
	int numThreads = Runtime.getRuntime().availableProcessors(); // use all available processors
	int minSubSectsPerParent = 2; // 2 are required for UCERf3 azimuth calcs
	float maxAzimuthChange = 60;
	float maxTotalAzimuthChange = 60;
	float maxCumulativeAzimuthChange = 560;
	RupturePermutationStrategy permutationStrategyClass = RupturePermutationStrategy.UCERF3;
	double thinningFactor = 0;
	double downDipMinAspect = 1;
	double downDipMaxAspect = 3;
	int downDipAspectDepthThreshold = Integer.MAX_VALUE; // from this 'depth' (in tile rows) the max aspect constraint
															// is ignored
	double downDipMinFill = 1; // 1 means only allow complete rectangles
	double downDipPositionCoarseness = 0; // 0 means no coarseness
	double downDipSizeCoarseness = 0; // 0 means no coarseness
	
	public String getDescriptiveString() {
		String description = "RupSet_Az";
		if (faultModel != null) {
			description = description + "_FM(" + faultModel.name() + ")";
		}
		if (fsdFile != null) {
			description = description + "_FF(" + fsdFile.getName() + ")";
		}
		if (downDipFile != null) {
			description = description + "_SF(" + downDipFile.getName() + ")";
		}
		if (downDipFile != null || (faultModel != null && !faultModel.isCrustal())) {
			description += "_ddAsRa(" + downDipMinAspect + "," + downDipMaxAspect + "," + downDipAspectDepthThreshold + ")";
			description += "_ddMnFl(" + downDipMinFill + ")";
			description += "_ddPsCo(" + downDipPositionCoarseness + ")";
			description += "_ddSzCo(" + downDipSizeCoarseness + ")";
		} else {
			description += "_mxSbScLn(" + maxSubSectionLength + ")";
			//description += "_skFtSc(" + skipFaultSections + ")";
		}

		description += "_mxAzCh(" + maxTotalAzimuthChange + ")";
		description += "_mxCmAzCh(" + maxCumulativeAzimuthChange + ")";
		//description += "_mxFaSe(" + maxFaultSections + ")";
		description += "_mxJpDs(" + maxDistance + ")";
		description += "_mxTtAzCh(" + maxTotalAzimuthChange + ")";
		//description += "_mnSsPrPa(" + minSubSectsPerParent + ")";
		//description += "_pmSt(" + permutationStrategyClass.name() + ")";
		description += "_thFc(" + thinningFactor + ")";

		return description;
	}

	public enum RupturePermutationStrategy {
		UCERF3, POINTS,
	}

	/**
	 * Constructs a new NZSHM22_RuptureSetBuilder with the default NSHM configuration.
	 */
	public NZSHM22_RuptureSetBuilder() {
		subSections = new FaultSectionList();
	}

	/**
	 * For testing of specific ruptures
	 *
	 * @param filterType The behaviour of the filter. See FaultIdFilter.
	 * @param faultIds   A set of fault section integer ids.
	 * @return NZSHM22_RuptureSetBuilder the builder
	 */
	public NZSHM22_RuptureSetBuilder setFaultIdFilter(FaultIdFilter.FilterType filterType, Set<Integer> faultIds) {
		this.faultIds = faultIds;
		this.faultIdfilterType = filterType;
		return this;
	}

	/**
	 * Sets the maximum jump distance allowed between fault sections
	 *
	 * @param maxDistance km
	 * @return NZSHM22_RuptureSetBuilder the builder
	 */
	public NZSHM22_RuptureSetBuilder setMaxJumpDistance(double maxDistance) {
		this.maxDistance = maxDistance;
		return this;
	}

	/**
	 * Sets the thinning factor e.g. 0.1 means that the number of sections to
	 * rupture must be at least 10% more than the previous count.
	 *
	 * @param thinningFactor
	 * @return NZSHM22_RuptureSetBuilder the builder
	 */
	public NZSHM22_RuptureSetBuilder setThinningFactor(double thinningFactor) {
		this.thinningFactor = thinningFactor;
		return this;
	}

	/**
	 * Used for testing only!
	 *
	 * @param maxFaultSections the maximum number of fault sections to be processed.
	 * @return NZSHM22_RuptureSetBuilder the builder
	 */
	public NZSHM22_RuptureSetBuilder setMaxFaultSections(int maxFaultSections) {
		this.maxFaultSections = maxFaultSections;
		return this;
	}

	/**
	 * Used for testing only!
	 *
	 * @param skipFaultSections sets the number fault sections to be skipped.
	 * @return NZSHM22_RuptureSetBuilder the builder
	 */
	public NZSHM22_RuptureSetBuilder setSkipFaultSections(int skipFaultSections) {
		this.skipFaultSections = skipFaultSections;
		return this;
	}

	/**
	 * @param minSubSectsPerParent sets the minimum subsections per parent, 2 is
	 *                             standard as per UCERF3
	 * @return NZSHM22_RuptureSetBuilder the builder
	 */
	public NZSHM22_RuptureSetBuilder setMinSubSectsPerParent(int minSubSectsPerParent) {
		this.minSubSectsPerParent = minSubSectsPerParent;
		return this;
	}

	/**
	 * Sets the ratio of relative to DownDipWidth (DDW) that is used to calculate
	 * subsection lengths.
	 * <p>
	 * However, if fault sections are very short, then the minSubSectsPerParent may
	 * still force shorter sections to be built.
	 *
	 * @param maxSubSectionLength defaults to 0.5, meaning the desired minimum
	 *                            length is half of the DDW.
	 * @return NZSHM22_RuptureSetBuilder the builder
	 */
	public NZSHM22_RuptureSetBuilder setMaxSubSectionLength(double maxSubSectionLength) {
		this.maxSubSectionLength = maxSubSectionLength;
		return this;
	}

	/**
	 * @param permutationStrategyClass sets the rupture permuation strategy
	 *                                 implementation
	 * @return NZSHM22_RuptureSetBuilder the builder
	 */
	public NZSHM22_RuptureSetBuilder setPermutationStrategy(RupturePermutationStrategy permutationStrategyClass) {
		this.permutationStrategyClass = permutationStrategyClass;
		return this;
	}

	/**
	 * Some internal classes support parallelisation.
	 *
	 * @param numThreads sets munber of threads to be used.
	 * @return NZSHM22_RuptureSetBuilder the builder
	 */
	public NZSHM22_RuptureSetBuilder setNumThreads(int numThreads) {
		this.numThreads = numThreads;
		return this;
	}

	public NZSHM22_RuptureSetBuilder setMaxAzimuthChange(float maxAzimuthChange) {
		this.maxAzimuthChange = maxAzimuthChange;
		return this;
	}

	public NZSHM22_RuptureSetBuilder setMaxTotalAzimuthChange(float maxTotalAzimuthChange) {
		this.maxTotalAzimuthChange = maxTotalAzimuthChange;
		return this;
	}

	public NZSHM22_RuptureSetBuilder setMaxCumulativeAzimuthChange(float maxCumulativeAzimuthChange) {
		this.maxCumulativeAzimuthChange = maxCumulativeAzimuthChange;
		return this;
	}

	/**
	 * Sets the FaultModel file for all crustal faults
	 *
	 * @param fsdFile the XML FaultSection data file containing source fault
	 *                information
	 * @return this builder
	 */
	public NZSHM22_RuptureSetBuilder setFaultModelFile(File fsdFile) {
		this.fsdFile = fsdFile;
		return this;
	}

	/**
	 * Sets the subduction fault. At the moment, only one fault can be set.
	 *
	 * @param faultName   The name fo the fault.
	 * @param downDipFile the CSV file containing all sections.
	 * @return this builder
	 */
	public NZSHM22_RuptureSetBuilder setSubductionFault(String faultName, File downDipFile) {
		this.downDipFaultName = faultName;
		this.downDipFile = downDipFile;
		return this;
	}

	/**
	 * Sets the aspect ratio boundaries for subduction zone ruptures.
	 *
	 * @param minAspect the minimum aspect ratio
	 * @param maxAspect the maximum aspect ratio
	 * @return this builder
	 */
	public NZSHM22_RuptureSetBuilder setDownDipAspectRatio(double minAspect, double maxAspect) {
		this.downDipMinAspect = minAspect;
		this.downDipMaxAspect = maxAspect;
		return this;
	}

	/**
	 * Sets the aspect ratio boundaries for subduction zone ruptures with elastic
	 * aspect ratinos set with depthThreshold.
	 *
	 * @param minAspect      the minimum aspect ratio
	 * @param maxAspect      the maximum aspect ratio
	 * @param depthThreshold the threshold (count of rows) from which the maxAspect
	 *                       constraint will be ignored
	 *
	 * @return this builder
	 */
	public NZSHM22_RuptureSetBuilder setDownDipAspectRatio(double minAspect, double maxAspect, int depthThreshold) {
		this.downDipMinAspect = minAspect;
		this.downDipMaxAspect = maxAspect;
		this.downDipAspectDepthThreshold = depthThreshold;
		return this;
	}

	/**
	 * Sets the required rectangularity for subduction zone ruptures. A value of 1
	 * means all ruptures need to be rectangular. A value smaller of 1 indicates the
	 * minimum percentage of actual section within the rupture rectangle.
	 *
	 * @param minFill the minimum fill of the rupture rectangle
	 * @return this builder
	 */
	public NZSHM22_RuptureSetBuilder setDownDipMinFill(double minFill) {
		this.downDipMinFill = minFill;
		return this;
	}

	/**
	 * Sets the position coarseness for subduction zone ruptures.
	 *
	 * @param epsilon epsilon
	 * @return this builder
	 */
	public NZSHM22_RuptureSetBuilder setDownDipPositionCoarseness(double epsilon) {
		this.downDipPositionCoarseness = epsilon;
		return this;
	}

	/**
	 * Sets the size coarseness for subduction zone ruptures.
	 *
	 * @param epsilon epsilon
	 * @return this builder
	 */
	public NZSHM22_RuptureSetBuilder setDownDipSizeCoarseness(double epsilon) {
		this.downDipSizeCoarseness = epsilon;
		return this;
	}

	public NZSHM22_RuptureSetBuilder setFaultModel(NZSHM22_FaultModels faultModel){
		this.faultModel = faultModel;
		return this;
	}

	/**
	 * @param permutationStrategyClass which strategy to choose
	 * @return a RuptureGrowingStrategy object
	 */
	private RuptureGrowingStrategy createPermutationStrategy(RupturePermutationStrategy permutationStrategyClass) {
		RuptureGrowingStrategy permutationStrategy = null;
		switch (permutationStrategyClass) {
		case POINTS:
			// creates ruptures in blocks defined by the connection points between clusters
			permutationStrategy = new ConnectionPointsRuptureGrowingStrategy();
			break;
		case UCERF3:
			// creates ruptures covering the incremental permutations of sub-sections in
			// each cluster
			permutationStrategy = new ExhaustiveUnilateralRuptureGrowingStrategy();
			break;
		}

		if (null != downDipFile) {
			permutationStrategy = new DownDipPermutationStrategy(permutationStrategy)
					.addAspectRatioConstraint(downDipMinAspect, downDipMaxAspect, downDipAspectDepthThreshold)
					.addPositionCoarsenessConstraint(downDipPositionCoarseness).addMinFillConstraint(downDipMinFill)
					.addSizeCoarsenessConstraint(downDipSizeCoarseness);
		}
		return permutationStrategy;
	}

    private void loadFaults() throws IOException, DocumentException {
        if (faultModel != null) {
            faultModel.fetchFaultSections(subSections);
        } else if (downDipFile != null) {
            try (FileInputStream in = new FileInputStream(downDipFile)) {
                DownDipSubSectBuilder.loadFromStream(subSections, 10000, downDipFaultName, in);
            }
        } else if (fsdFile != null) {
            subSections = FaultSectionList.fromList((FaultModels.loadStoredFaultSections(fsdFile)));
        } else {
            throw new IllegalArgumentException("No fault model specified.");
        }

        System.out.println("Fault model has " + subSections.size() + " fault sections");

        if (fsdFile != null || (faultModel != null && faultModel.isCrustal())) {

			if (maxFaultSections < 1000 || skipFaultSections > 0) {
				final long endSection = maxFaultSections + skipFaultSections;
				final long skipSections = skipFaultSections;
				subSections.removeIf(section -> section.getSectionId() >= endSection || section.getSectionId() < skipSections);
				System.out.println("Fault model filtered to " + subSections.size() + " fault sections");
			}

            FaultSectionList fsd = subSections;
            subSections = new FaultSectionList();
            // build the subsections
            subSections.addParents(fsd);
            for (FaultSection parentSect : fsd) {
                double ddw = parentSect.getOrigDownDipWidth();
                double maxSectLength = ddw * maxSubSectionLength;
                System.out.println("Get subSections in " + parentSect.getName());
                // the "2" here sets a minimum number of sub sections
                List<? extends FaultSection> newSubSects = parentSect.getSubSectionsList(maxSectLength,
                        subSections.getSafeId(), 2);
                getSubSections().addAll(newSubSects);
                System.out.println("Produced " + newSubSects.size() + " subSections in " + parentSect.getName());
            }
            System.out.println(subSections.size() + " Sub Sections created.");
        }
    }

	private void buildConfig() {
		SectionDistanceAzimuthCalculator distAzCalc = new SectionDistanceAzimuthCalculator(subSections);
		JumpAzimuthChangeFilter.AzimuthCalc azimuthCalc = new JumpAzimuthChangeFilter.SimpleAzimuthCalc(distAzCalc);

		// connection strategy: parent faults connect at closest point, and only when
		// dist <=5 km
		ClusterConnectionStrategy connectionStrategy = new FaultTypeSeparationConnectionStrategy(
				subSections, distAzCalc, maxDistance);

		System.out.println("Built connectionStrategy");

		int maxNumSplays = 0; // don't allow any splays

		PlausibilityConfiguration.Builder configBuilder = PlausibilityConfiguration
				.builder(connectionStrategy, distAzCalc).maxSplays(maxNumSplays)
				.add(new JumpAzimuthChangeFilter(azimuthCalc, maxAzimuthChange))
				.add(new TotalAzimuthChangeFilter(azimuthCalc, maxTotalAzimuthChange, true, true))
				.add(new DownDipSafeCumulativeAzimuthChangeFilter(azimuthCalc,
						maxCumulativeAzimuthChange))
				.add(new MinSectsPerParentFilter(minSubSectsPerParent, true, true, connectionStrategy));
		if (faultIdfilterType != null) {
			configBuilder.add(FaultIdFilter.create(faultIdfilterType, faultIds));
		}
		plausibilityConfig = configBuilder.build();
	}

	/**
	 * Builds an NSHM rupture set according to the configuration.
	 *
	 * @return a SlipAlongRuptureModelRupSet built according to the configuration
	 *         from the input fsdFile
	 * @throws DocumentException
	 * @throws IOException
	 */
	public SlipAlongRuptureModelRupSet buildRuptureSet() throws DocumentException, IOException {

	    loadFaults();

		buildConfig();
		System.out.println("Built PlausibilityConfiguration");

		// Builder can now proceed using the clusters and all the filters...
		builder = new ClusterRuptureBuilder(getPlausibilityConfig());
		System.out.println("initialised ClusterRuptureBuilder");

		// CBC debugging...
		// ParentSectsRupDebugCriteria debugCriteria = new
		// ParentSectsRupDebugCriteria(false, true, 2);
		// builder.setDebugCriteria(debugCriteria, true);

		RuptureGrowingStrategy permutationStrategy = createPermutationStrategy(permutationStrategyClass);
		// debugging
		// numThreads = 1;
		ruptures = getBuilder().build(permutationStrategy, numThreads);

		if (thinningFactor <= 0) {
			System.out.println("Built " + ruptures.size() + " total ruptures");
		} else {
			ruptures = RuptureThinning.filterRuptures(ruptures,
					RuptureThinning.downDipPredicate().or(RuptureThinning
							.coarsenessPredicate(thinningFactor)
							.or(RuptureThinning.endToEndPredicate(getPlausibilityConfig().getConnectionStrategy()))));
			System.out.println("Built " + ruptures.size() + " total ruptures after thinning");
		}

		// TODO: consider overloading this for Hikurangi to provide
		// Slip{DOWNDIP}RuptureModel (or similar) see [KKS,CBC]
		NZSHM22_SlipEnabledRuptureSet rupSet = null;
		try {
			rupSet = new NZSHM22_SlipEnabledRuptureSet(ruptures, subSections,
					ScalingRelationships.SHAW_2009_MOD, SlipAlongRuptureModels.UNIFORM);
//			rupSet = new NZSHM22_SlipEnabledRuptureSet(ruptures, subSections,
//					ScalingRelationships.TMG_SUB_2017, SlipAlongRuptureModels.UNIFORM);

			rupSet.setPlausibilityConfiguration(getPlausibilityConfig());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return rupSet;
	}

	/**
	 * @return the ruptures
	 */
	public List<ClusterRupture> getRuptures() {
		return ruptures;
	}

	/**
	 * @return the subSections
	 */
	public FaultSectionList getSubSections() {
		return subSections;
	}

	/**
	 * @return the plausabilityConfig
	 */
	public PlausibilityConfiguration getPlausibilityConfig() {
		return plausibilityConfig;
	}

	/**
	 * @return the builder
	 */
	public ClusterRuptureBuilder getBuilder() {
		return builder;
	}
}

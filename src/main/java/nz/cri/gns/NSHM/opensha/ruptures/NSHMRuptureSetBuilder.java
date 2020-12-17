package nz.cri.gns.NSHM.opensha.ruptures;

import java.io.*;
import java.net.MalformedURLException;
import java.util.List;
import java.util.Set;

import nz.cri.gns.NSHM.opensha.ruptures.downDip.*;
import org.dom4j.DocumentException;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRuptureBuilder;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
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

import nz.cri.gns.NSHM.opensha.util.FaultSectionList;
import scratch.UCERF3.SlipAlongRuptureModelRupSet;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;

/**
 * Builds opensha SlipAlongRuptureModelRupSet rupture sets
 * using NZ NSHM configurations for:
 * - plausability
 * - rupture permutation Strategy (with different strategies available for test purposes)
 */
public class NSHMRuptureSetBuilder {

    final DownDipRegistry downDipRegistry;
    final FaultSectionList subSections;
    List<ClusterRupture> ruptures;
    PlausibilityConfiguration plausibilityConfig;
    ClusterRuptureBuilder builder;

    File fsdFile = null;
    File downDipFile = null;
    String downDipFaultName = null;
    Set<Integer> faultIds;
    FaultIdFilter.FilterType faultIdfilterType = null;

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
    double thinningFactor = Double.NaN;
    double downDipMinAspect = 1;
    double downDipMaxAspect = 3;
    int downDipAspectDepthThreshold = Integer.MAX_VALUE; //from this 'depth' (in tile rows) the max aspect constraint is ignored  
    double downDipMinFill = 1; // 1 means only allow complete rectangles
    double downDipPositionCoarseness = 0; // 0 means no coarseness
    double downDipSizeCoarseness = 0; // 0 means no coarseness

    public enum RupturePermutationStrategy {
        UCERF3, POINTS,
    }

    /**
     * Constructs a new NSHMRuptureSetBuilder with the default NSHM configuration.
     */
    public NSHMRuptureSetBuilder() {
        subSections = new FaultSectionList();
        downDipRegistry = new DownDipRegistry(subSections);
    }

    /**
     * For testing of specific ruptures
     *
     * @param filterType The behaviour of the filter. See FaultIdFilter.
     * @param faultIds   A set of fault section integer ids.
     * @return NSHMRuptureSetBuilder the builder
     */
    public NSHMRuptureSetBuilder setFaultIdFilter(FaultIdFilter.FilterType filterType, Set<Integer> faultIds) {
        this.faultIds = faultIds;
        this.faultIdfilterType = filterType;
        return this;
    }

    /**
     * Sets the maximum jump distance allowed between fault sections
     *
     * @param maxDistance km
     * @return NSHMRuptureSetBuilder the builder
     */
    public NSHMRuptureSetBuilder setMaxJumpDistance(double maxDistance) {
        this.maxDistance = maxDistance;
        return this;
    }

    /**
     * Sets the thinning factor e.g. 0.1 means that the number of sections to rupture must be at least 10%
     * more than the previous count.
     *
     * @param thinningFactor
     * @return NSHMRuptureSetBuilder the builder
     */
    public NSHMRuptureSetBuilder setThinningFactor(double thinningFactor) {
        if (thinningFactor > 0.0) {
            this.thinningFactor = thinningFactor;
        } else {
            this.thinningFactor = Double.NaN;
        }
        return this;
    }

    /**
     * Used for testing only!
     *
     * @param maxFaultSections the maximum number of fault sections to be processed.
     * @return NSHMRuptureSetBuilder the builder
     */
    public NSHMRuptureSetBuilder setMaxFaultSections(int maxFaultSections) {
        this.maxFaultSections = maxFaultSections;
        return this;
    }

    /**
     * Used for testing only!
     *
     * @param skipFaultSections sets the number fault sections to be skipped.
     * @return NSHMRuptureSetBuilder the builder
     */
    public NSHMRuptureSetBuilder setSkipFaultSections(int skipFaultSections) {
        this.skipFaultSections = skipFaultSections;
        return this;
    }

    /**
     * @param minSubSectsPerParent sets the minimum subsections per parent, 2 is standard as per UCERF3
     * @return NSHMRuptureSetBuilder the builder
     */
    public NSHMRuptureSetBuilder setMinSubSectsPerParent(int minSubSectsPerParent) {
        this.minSubSectsPerParent = minSubSectsPerParent;
        return this;
    }

    /**
     * Sets the ratio of relative to DownDipWidth (DDW) that is used to calculate subsection lengths.
     * <p>
     * However, if fault sections are very short, then the minSubSectsPerParent may still force shorter sections
     * to be built.
     *
     * @param maxSubSectionLength defaults to 0.5, meaning the desired minimum length is half of the DDW.
     * @return NSHMRuptureSetBuilder the builder
     */
    public NSHMRuptureSetBuilder setMaxSubSectionLength(double maxSubSectionLength) {
        this.maxSubSectionLength = maxSubSectionLength;
        return this;
    }

    /**
     * @param permutationStrategyClass sets the rupture permuation strategy implementation
     * @return NSHMRuptureSetBuilder the builder
     */
    public NSHMRuptureSetBuilder setPermutationStrategy(RupturePermutationStrategy permutationStrategyClass) {
        this.permutationStrategyClass = permutationStrategyClass;
        return this;
    }

    /**
     * Some internal classes support parallelisation.
     *
     * @param numThreads sets munber of threads to be used.
     * @return NSHMRuptureSetBuilder the builder
     */
    public NSHMRuptureSetBuilder setNumThreads(int numThreads) {
        this.numThreads = numThreads;
        return this;
    }

    public NSHMRuptureSetBuilder setMaxAzimuthChange(float maxAzimuthChange) {
        this.maxAzimuthChange = maxAzimuthChange;
        return this;
    }

    public NSHMRuptureSetBuilder setMaxTotalAzimuthChange(float maxTotalAzimuthChange) {
        this.maxTotalAzimuthChange = maxTotalAzimuthChange;
        return this;
    }

    public NSHMRuptureSetBuilder setMaxCumulativeAzimuthChange(float maxCumulativeAzimuthChange) {
        this.maxCumulativeAzimuthChange = maxCumulativeAzimuthChange;
        return this;
    }

    /**
     * Sets the FaultModel file for all crustal faults
     *
     * @param fsdFile the XML FaultSection data file containing source fault information
     * @return this builder
     */
    public NSHMRuptureSetBuilder setFaultModelFile(File fsdFile) {
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
    public NSHMRuptureSetBuilder setSubductionFault(String faultName, File downDipFile) {
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
    public NSHMRuptureSetBuilder setDownDipAspectRatio(double minAspect, double maxAspect) {
        this.downDipMinAspect = minAspect;
        this.downDipMaxAspect = maxAspect;
        return this;
    }

    /**
     * Sets the aspect ratio boundaries for subduction zone ruptures with elastic aspect ratinos set with depthThreshold.
     *
     * @param minAspect the minimum aspect ratio
     * @param maxAspect the maximum aspect ratio
     * @param depthThreshold the threshold (count of rows) from which the maxAspect constraint will be ignored  
     * 
     * @return this builder
     */
    public NSHMRuptureSetBuilder setDownDipAspectRatio(double minAspect, double maxAspect, int depthThreshold) {
        this.downDipMinAspect = minAspect;
        this.downDipMaxAspect = maxAspect;
        this.downDipAspectDepthThreshold = depthThreshold;
        return this;
    }
    
    /**
     * Sets the required rectangularity for subduction zone ruptures.
     * A value of 1 means all ruptures need to be rectangular. A value smaller of 1
     * indicates the minimum percentage of actual section within the rupture rectangle.
     *
     * @param minFill the minimum fill of the rupture rectangle
     * @return this builder
     */
    public NSHMRuptureSetBuilder setDownDipMinFill(double minFill) {
        this.downDipMinFill = minFill;
        return this;
    }

    /**
     * Sets the position coarseness for subduction zone ruptures.
     *
     * @param epsilon epsilon
     * @return this builder
     */
    public NSHMRuptureSetBuilder setDownDipPositionCoarseness(double epsilon) {
        this.downDipPositionCoarseness = epsilon;
        return this;
    }

    /**
     * Sets the size coarseness for subduction zone ruptures.
     *
     * @param epsilon epsilon
     * @return this builder
     */
    public NSHMRuptureSetBuilder setDownDipSizeCoarseness(double epsilon) {
        this.downDipSizeCoarseness = epsilon;
        return this;
    }

    /**
     * @param permutationStrategyClass which strategy to choose
     * @return a ClusterPermutationStrategy object
     */
    private ClusterPermutationStrategy createPermutationStrategy(RupturePermutationStrategy permutationStrategyClass) {
        ClusterPermutationStrategy permutationStrategy = null;
        switch (permutationStrategyClass) {
            case POINTS:
                // creates ruptures in blocks defined by the connection points between clusters
                permutationStrategy = new ConnectionPointsPermutationStrategy();
                break;
            case UCERF3:
                // creates ruptures covering the incremental permutations of sub-sections in each cluster
                permutationStrategy = new UCERF3ClusterPermuationStrategy();
                break;
        }

        if (null != downDipFile) {
            permutationStrategy = new DownDipPermutationStrategy(downDipRegistry, permutationStrategy)
                    .addAspectRatioConstraint(downDipMinAspect, downDipMaxAspect, downDipAspectDepthThreshold)
                    .addPositionCoarsenessConstraint(downDipPositionCoarseness)
                    .addMinFillConstraint(downDipMinFill)
                    .addSizeCoarsenessConstraint(downDipSizeCoarseness);
        }
        return permutationStrategy;
    }

    private void loadFaults() throws MalformedURLException, DocumentException {
        if (fsdFile != null) {
            FaultSectionList fsd = FaultSectionList.fromList((FaultModels.loadStoredFaultSections(fsdFile)));
            System.out.println("Fault model has " + fsd.size() + " fault sections");

            if (maxFaultSections < 1000 || skipFaultSections > 0) {
                final long endSection = maxFaultSections + skipFaultSections;
                final long skipSections = skipFaultSections;
                fsd.removeIf(section -> section.getSectionId() >= endSection || section.getSectionId() < skipSections);
                System.out.println("Fault model now has " + fsd.size() + " fault sections");
            }

            // build the subsections
            subSections.addParents(fsd);
            for (FaultSection parentSect : fsd) {
                double ddw = parentSect.getOrigDownDipWidth();
                double maxSectLength = ddw * maxSubSectionLength;
                System.out.println("Get subSections in " + parentSect.getName());
                // the "2" here sets a minimum number of sub sections
                List<? extends FaultSection> newSubSects = parentSect.getSubSectionsList(maxSectLength, subSections.getSafeId(), 2);
                getSubSections().addAll(newSubSects);
                System.out.println("Produced " + newSubSects.size() + " subSections in " + parentSect.getName());
            }
            System.out.println(subSections.size() + " Sub Sections");
        }
    }

    private void loadSubductionFault(int id, String faultName, File file) throws IOException {
        downDipRegistry.loadFromFile(id, faultName, file);
    }

    private void buildConfig() {
        SectionDistanceAzimuthCalculator distAzCalc = new SectionDistanceAzimuthCalculator(subSections);
        JumpAzimuthChangeFilter.AzimuthCalc azimuthCalc = new JumpAzimuthChangeFilter.SimpleAzimuthCalc(distAzCalc);

        // connection strategy: parent faults connect at closest point, and only when dist <=5 km
        ClusterConnectionStrategy connectionStrategy = new FaultTypeSeparationConnectionStrategy(downDipRegistry, subSections, distAzCalc, maxDistance);
        System.out.println("Built connectionStrategy");

        int maxNumSplays = 0; // don't allow any splays

        PlausibilityConfiguration.Builder configBuilder =
                PlausibilityConfiguration.builder(connectionStrategy, distAzCalc)
                        .maxSplays(maxNumSplays)
                        .add(new FaultTypeSeparationFilter(downDipRegistry))
                        .add(new JumpAzimuthChangeFilter(azimuthCalc, maxAzimuthChange))
                        .add(new TotalAzimuthChangeFilter(azimuthCalc, maxTotalAzimuthChange, true, true))
                        .add(new DownDipSafeCumulativeAzimuthChangeFilter(downDipRegistry, azimuthCalc, maxCumulativeAzimuthChange))
                        .add(new MinSectsPerParentFilter(minSubSectsPerParent, true, true, connectionStrategy));
        if (faultIdfilterType != null) {
            configBuilder.add(FaultIdFilter.create(faultIdfilterType, faultIds));
        }
        plausibilityConfig = configBuilder.build();
    }

    /**
     * Builds an NSHM rupture set according to the configuration.
     *
     * @return a SlipAlongRuptureModelRupSet built according to the configuration from the input fsdFile
     * @throws DocumentException
     * @throws IOException
     */
    public SlipAlongRuptureModelRupSet buildRuptureSet() throws DocumentException, IOException {

        loadFaults();
        if (null != downDipFile) {
            // TODO call this multiple times to implement multiple downdip faults
            loadSubductionFault(10000, downDipFaultName, downDipFile);
        }
        System.out.println("Have " + subSections.size() + " sub-sections in total");

        buildConfig();
        System.out.println("Built PlausibilityConfiguration");

        // Builder can now proceed using the clusters and all the filters...
        builder = new ClusterRuptureBuilder(getPlausibilityConfig());
        System.out.println("initialised ClusterRuptureBuilder");

        ClusterPermutationStrategy permutationStrategy = createPermutationStrategy(permutationStrategyClass);

        ruptures = getBuilder().build(permutationStrategy, numThreads);

        if (Double.isNaN(thinningFactor)) {
            System.out.println("Built " + ruptures.size() + " total ruptures");
        } else {
            ruptures = RuptureThinning.filterRuptures(ruptures,
                    RuptureThinning.downDipPredicate(downDipRegistry).or(
                            RuptureThinning.coarsenessPredicate(thinningFactor)
                                    .or(RuptureThinning.endToEndPredicate(
                                            getPlausibilityConfig().getConnectionStrategy()))));
            System.out.println("Built " + ruptures.size() + " total ruptures after thinning");
        }

        // TODO: consider overloading this for Hikurangi to provide Slip{DOWNDIP}RuptureModel (or similar) see [KKS,CBC]
        NSHMSlipEnabledRuptureSet rupSet = new NSHMSlipEnabledRuptureSet(ruptures, subSections,
                ScalingRelationships.SHAW_2009_MOD, SlipAlongRuptureModels.UNIFORM);
        rupSet.setPlausibilityConfiguration(getPlausibilityConfig());
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

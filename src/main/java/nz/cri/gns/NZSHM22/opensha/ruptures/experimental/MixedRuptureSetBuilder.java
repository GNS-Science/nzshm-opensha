package nz.cri.gns.NZSHM22.opensha.ruptures.experimental;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Range;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import nz.cri.gns.NZSHM22.opensha.calc.SimplifiedScalingRelationship;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.FaultRegime;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.faults.FaultSectionList;
import nz.cri.gns.NZSHM22.opensha.ruptures.DownDipFaultSection;
import nz.cri.gns.NZSHM22.opensha.ruptures.NZSHM22_AbstractRuptureSetBuilder;
import nz.cri.gns.NZSHM22.opensha.ruptures.downDip.DownDipConstraint;
import nz.cri.gns.NZSHM22.opensha.ruptures.downDip.DownDipPermutationStrategy;
import org.dom4j.DocumentException;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRuptureBuilder;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityResult;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.CumulativeRakeChangeFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.DirectPathPlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.MinSectsPerParentFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.SplayConnectionsOnlyFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.coulomb.NetRuptureCoulombFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.path.CumulativeProbPathEvaluator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.path.NucleationClusterEvaluator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.path.PathPlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.*;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.*;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ExhaustiveBilateralRuptureGrowingStrategy.SecondaryVariations;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCache;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;

public class MixedRuptureSetBuilder extends NZSHM22_AbstractRuptureSetBuilder {

    // downdip parameters

    double thinningFactor = 0;
    double downDipMinAspect = 1;
    double downDipMaxAspect = 3;
    int downDipAspectDepthThreshold =
            Integer.MAX_VALUE; // from this 'depth' (in tile rows) the max aspect constraint
    // is ignored
    double downDipMinFill = 1; // 1 means only allow complete rectangles
    double downDipPositionCoarseness = 0; // 0 means no coarseness
    double downDipSizeCoarseness = 0; // 0 means no coarseness

    // filters out indirect paths
    protected boolean noIndirectPaths = true; // PREF: true
    // relative slip rate probability
    protected float slipRateProb = 0.05f; // PREF: 0.05
    // if false, slip rate probabilities only consider alternative jumps up to the distance (+2km)
    // of the taken jump
    protected boolean slipIncludeLonger = false; // PREF: false
    // fraction of interactions positive
    protected float cffFractInts = 0.75f; // PREF: 0.75
    // number of denominator values for the CFF favorability ratio
    protected int cffRatioN = 2; // PREF: 2
    // CFF favorability ratio threshold
    protected float cffRatioThresh = 0.5f; // PREF: 0.5
    // relative CFF probability
    protected float cffRelativeProb = 0.01f; // PREF: 0.01
    // if true, CFF calculations are computed with the most favorable path (up to max jump
    // distance), which may not
    // use the exact jumping point from the connection strategy
    protected boolean favorableJumps = true; // PREF: true
    // cumulative jump probability threshold
    protected float jumpProbThresh = 0.001f; // PREF: 0.001 (~21 km)
    // cumulative rake change threshold
    protected float cmlRakeThresh = 360f; // PREF: 360
    // CONNECTION STRATEGY
    // maximum individual jump distance
    protected double maxJumpDist = 15d; // PREF: 15
    // if true, connections happen at places that actually work and paths are optimized. if false,
    // closest points
    protected boolean plausibleConnections = true; // PREF: true
    // if >0 and <maxDist, connections will only be added above this distance when no other
    // connections exist from
    // a given subsection. e.g., if set to 5, you can jump more than 5 km but only if no <= 5km
    // jumps exist
    protected double adaptiveMinDist = 6d; // PREF: 6
    // GROWING STRATEGY
    // if nonzero, apply thinning to growing strategy
    protected float adaptiveSectFract = 0.1f; // PREF: 0.1
    // if true, allow bilateral rupture growing (using default settings)
    protected boolean bilateral = false; // PREF: false
    // if true, allow splays (using default settings)
    protected boolean splays = false; // PREF: false

    protected double stiffGridSpacing = 2d;
    protected double coeffOfFriction = 0.5;

    private static DecimalFormat oneDigitDF = new DecimalFormat("0.0");
    private static DecimalFormat countDF = new DecimalFormat("#");

    static {
        countDF.setGroupingUsed(true);
        countDF.setGroupingSize(3);
    }

    public MixedRuptureSetBuilder() {
        subSections = new FaultSectionList();
    }

    /**
     * Sets the aspect ratio boundaries for subduction zone ruptures.
     *
     * @param minAspect the minimum aspect ratio
     * @param maxAspect the maximum aspect ratio
     * @return this builder
     */
    public MixedRuptureSetBuilder setDownDipAspectRatio(double minAspect, double maxAspect) {
        this.downDipMinAspect = minAspect;
        this.downDipMaxAspect = maxAspect;
        return this;
    }

    /**
     * Sets the aspect ratio boundaries for subduction zone ruptures with elastic aspect ratinos set
     * with depthThreshold.
     *
     * @param minAspect the minimum aspect ratio
     * @param maxAspect the maximum aspect ratio
     * @param depthThreshold the threshold (count of rows) from which the maxAspect constraint will
     *     be ignored
     * @return this builder
     */
    public MixedRuptureSetBuilder setDownDipAspectRatio(
            double minAspect, double maxAspect, int depthThreshold) {
        this.downDipMinAspect = minAspect;
        this.downDipMaxAspect = maxAspect;
        this.downDipAspectDepthThreshold = depthThreshold;
        return this;
    }

    /**
     * Sets the required rectangularity for subduction zone ruptures. A value of 1 means all
     * ruptures need to be rectangular. A value smaller of 1 indicates the minimum percentage of
     * actual section within the rupture rectangle.
     *
     * @param minFill the minimum fill of the rupture rectangle
     * @return this builder
     */
    public MixedRuptureSetBuilder setDownDipMinFill(double minFill) {
        this.downDipMinFill = minFill;
        return this;
    }

    /**
     * Sets the position coarseness for subduction zone ruptures.
     *
     * @param epsilon epsilon
     * @return this builder
     */
    public MixedRuptureSetBuilder setDownDipPositionCoarseness(double epsilon) {
        this.downDipPositionCoarseness = epsilon;
        return this;
    }

    /**
     * Sets the size coarseness for subduction zone ruptures.
     *
     * @param epsilon epsilon
     * @return this builder
     */
    public MixedRuptureSetBuilder setDownDipSizeCoarseness(double epsilon) {
        this.downDipSizeCoarseness = epsilon;
        return this;
    }

    public MixedRuptureSetBuilder setStiffGridSpacing(double stiffGridSpacing) {
        this.stiffGridSpacing = stiffGridSpacing;
        return this;
    }

    public MixedRuptureSetBuilder setCoeffOfFriction(double coeffOfFriction) {
        this.coeffOfFriction = coeffOfFriction;
        return this;
    }

    @Override
    public String getDescriptiveName() {
        String description = "RupSet_Cl";
        description += super.getDescriptiveName();
        description += "_noInP(" + fmt(noIndirectPaths) + ")";
        description += "_slRtP(" + fmt(slipRateProb) + ")";
        description += "_slInL(" + fmt(slipIncludeLonger) + ")";
        description += "_cfFr(" + fmt(cffFractInts) + ")";
        description += "_cfRN(" + fmt(cffRatioN) + ")";
        description += "_cfRTh(" + fmt(cffRatioThresh) + ")";
        description += "_cfRP(" + fmt(cffRelativeProb) + ")";
        description += "_fvJm(" + fmt(favorableJumps) + ")";
        description += "_jmPTh(" + fmt(jumpProbThresh) + ")";
        description += "_cmRkTh(" + fmt(cmlRakeThresh) + ")";
        description += "_mxJmD(" + fmt(maxJumpDist) + ")";
        description += "_plCn(" + fmt(plausibleConnections) + ")";
        description += "_adMnD(" + fmt(adaptiveMinDist) + ")";
        description += "_adScFr(" + fmt(adaptiveSectFract) + ")";
        description += "_bi(" + fmt(bilateral) + ")";

        description += "_stGrSp(" + fmt(stiffGridSpacing) + ")";
        description += "_coFr(" + fmt(coeffOfFriction) + ")";

        return description;
    }

    /**
     * Calculates the length of a cluster. Has special code for handling subduction clusters so that
     * only the top row is counted towards length
     *
     * @param cluster a cluster
     * @return the length of the cluster
     */
    private double calculateLength(FaultSubsectionCluster cluster) {

        if (cluster.subSects.get(0) instanceof DownDipFaultSection) {
            List<DownDipFaultSection> ddCluster =
                    (List<DownDipFaultSection>) (List<?>) cluster.subSects;
            int minRow =
                    ddCluster.stream().mapToInt(DownDipFaultSection::getRowIndex).min().getAsInt();
            return ddCluster.stream()
                    .filter(s -> s.getRowIndex() == minRow)
                    .mapToDouble(FaultSection::getTraceLength)
                    .sum();
        }

        return cluster.subSects.stream().mapToDouble(FaultSection::getTraceLength).sum();
    }

    private double calculateLength(ClusterRupture rupture) {
        return Arrays.stream(rupture.clusters).mapToDouble(this::calculateLength).sum() * 1e3;
    }

    protected double[] buildLengths() {
        // we don't count splays yet
        Preconditions.checkState(!splays);

        return ruptures.stream().mapToDouble(this::calculateLength).toArray();
    }

    @Override
    public FaultSystemRupSet buildRuptureSet() throws DocumentException, IOException {

        loadFaults(NZSHM22_FaultModels.CFM_1_0A_DOM_SANSTVZ);
        loadFaults(NZSHM22_FaultModels.SBD_0_2_PUY_15);
        loadFaults(NZSHM22_FaultModels.SBD_0_3_HKR_LR_30);

        Preconditions.checkState(!subSections.isEmpty());
        String fmPrefix = "nz_demo_crustal";
        // File distAzCacheFile = new File(rupSetsDir, fmPrefix+"_dist_az_cache.csv");
        ScalingRelationships scale = ScalingRelationships.MEAN_UCERF3;
        // END NZ

        // NSHM23 tests
        //		String state = null;
        //
        //		String fmPrefix;
        //		File nshmBaseDir = new
        // File("/home/kevin/OpenSHA/UCERF4/fault_models/NSHM2023_FaultSectionsEQGeoDB_v1p2_29March2021");
        //		File setctsFile = new File(nshmBaseDir, "NSHM2023_FaultSections_v1p2.geojson");
        //		File geoDBFile = new File(nshmBaseDir, "NSHM2023_EQGeoDB_v1p2.geojson");
        //		List<FaultSection> sects;
        //		if (state == null)
        //			fmPrefix = "nshm23_v1p2_all";
        //		else
        //			fmPrefix = "nshm23_v1p2_"+state.toLowerCase();
        //
        //		File distAzCacheFile = new File(rupSetsDir, fmPrefix+"_dist_az_cache.csv");
        //		ScalingRelationships scale = ScalingRelationships.MEAN_UCERF3;
        //		List<FaultSection> subSects = GeoJSONFaultReader.buildSubSects(setctsFile, geoDBFile,
        // state);
        //		System.out.println("Built "+subSects.size()+" subsections");
        // END NSHM23

        ClusterRuptureBuilder.RupDebugCriteria debugCriteria = null;
        boolean stopAfterDebug = false;

        //		RupDebugCriteria debugCriteria = new ResultCriteria(PlausibilityResult.PASS);
        //		boolean stopAfterDebug = false;

        //		RupDebugCriteria debugCriteria = new ParentSectsRupDebugCriteria(false, false, 672,
        // 668);
        ////		RupDebugCriteria debugCriteria = new StartEndSectRupDebugCriteria(672, -1, true,
        // false);
        //		boolean stopAfterDebug = true;

        //		FaultSystemRupSet compRupSet =U3FaultSystemIO.loadRupSet(new File(
        ////				"/home/kevin/workspace/OpenSHA/dev/scratch/UCERF3/data/scratch/InversionSolutions/"
        ////				+
        // "2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_MEAN_BRANCH_AVG_SOL.zip"));
        //
        //	"/home/kevin/OpenSHA/UCERF4/rup_sets/fm3_1_plausibleMulti10km_direct_cmlRake360_jumpP0.001_slipP0.05incr"
        //				+
        // "_cff0.75IntsPos_comb2Paths_cffFavP0.01_cffFavRatioN2P0.5_sectFractPerm0.05_comp/alt_perm_Bilateral"
        //				+ "_SecondaryEndEqual_Adaptive_5SectIncrease_MaintainConnectivity.zip"));
        //		System.out.println("Loaded "+compRupSet.getNumRuptures()+" comparison ruptures");
        //		RupDebugCriteria debugCriteria = new CompareRupSetNewInclusionCriteria(compRupSet);
        //		boolean stopAfterDebug = true;

        //		RupDebugCriteria debugCriteria = new SectsRupDebugCriteria(false, false,
        //				1639, 1640, 1641, 1642, 1643);
        //		boolean stopAfterDebug = true;

        //		String rupStr =
        // "[219:14,13][220:1217,1216,1215,1214][184:345][108:871,870][199:1404][130:1413,1412]";
        //		RupDebugCriteria debugCriteria = new SectsRupDebugCriteria(false, false,
        //				loadRupString(rupStr, false));
        //		boolean stopAfterDebug = true;

        //		RupDebugCriteria debugCriteria = new ParentSectsRupDebugCriteria(false, false, 219, 220,
        // 184, 108, 240);
        //		boolean stopAfterDebug = false;

        SectionDistanceAzimuthCalculator distAzCalc =
                new SectionDistanceAzimuthCalculator(subSections);

        //        if (distAzCacheFile.exists()) {
        //            System.out.println("Loading dist/az cache from
        // "+distAzCacheFile.getAbsolutePath());
        //            distAzCalc.loadCacheFile(distAzCacheFile);
        //        }
        //        int numAzCached = distAzCalc.getNumCachedAzimuths();
        //        int numDistCached = distAzCalc.getNumCachedDistances();

        // int threads = Integer.max(1, Integer.min(31,
        // Runtime.getRuntime().availableProcessors()-2));

        /*
         * =============================
         * To reproduce UCERF3
         * =============================
         */
        //		PlausibilityConfiguration config = PlausibilityConfiguration.getUCERF3(subSects,
        // distAzCalc, fm);
        //		RuptureGrowingStrategy growingStrat = new ExhaustiveUnilateralRuptureGrowingStrategy();
        //		String outputName = fmPrefix+"_reproduce_ucerf3.zip";
        //		AggregatedStiffnessCache stiffnessCache = null;
        //		File stiffnessCacheFile = null;
        //		int stiffnessCacheSize = 0;
        //		File outputDir = rupSetsDir;

        /*
         * =============================
         * To reproduce UCERF3 with an alternative distance/conn strategy (and calculate missing Coulomb)
         * =============================
         */
        //		String outputName = fmPrefix+"_ucerf3";
        //		CoulombRates coulombRates = CoulombRates.loadUCERF3CoulombRates(fm);
        //
        //		double maxJumpDist = 10d;
        //		ClusterConnectionStrategy connectionStrategy =
        //				new UCERF3ClusterConnectionStrategy(subSects, distAzCalc, maxJumpDist, coulombRates);
        //		outputName += "_"+new DecimalFormat("0.#").format(maxJumpDist)+"km";
        //
        ////		double r0 = 5d;
        ////		double rMax = 10d;
        ////		int cMax = -1;
        ////		int sMax = 1;
        ////		ClusterConnectionStrategy connectionStrategy =
        ////				new AdaptiveDistCutoffClosestSectClusterConnectionStrategy(subSects, distAzCalc, r0,
        // rMax, cMax, sMax);
        ////		outputName += "_adapt"+new DecimalFormat("0.#").format(r0)+"_"+new
        // DecimalFormat("0.#").format(rMax)+"km";
        ////		if (cMax >= 0)
        ////			outputName += "_cMax"+cMax;
        ////		if (sMax >= 0)
        ////			outputName += "_sMax"+sMax;
        //
        //		Builder configBuilder = PlausibilityConfiguration.builder(connectionStrategy, subSects);
        //
        ////		configBuilder.u3All(coulombRates); outputName += "_u3All";
        //		configBuilder.u3Azimuth();
        //		configBuilder.cumulativeAzChange(560f);
        //		configBuilder.cumulativeRakeChange(180f);
        ////		configBuilder.add(new U3CompatibleCumulativeRakeChangeFilter(180d));
        ////		configBuilder.u3Cumulatives();
        //		configBuilder.minSectsPerParent(2, true, true);
        ////		configBuilder.u3Coulomb(coulombRates);
        ////		outputName += "_u3NoCmlAz";
        ////		outputName += "_u3NoSectsPerParent";
        ////		outputName += "_u3NoAz";
        ////		outputName += "_u3CorrectedRake";
        //		outputName += "_noCoulomb";
        //		AggregatedStiffnessCache stiffnessCache = null;
        //		File stiffnessCacheFile = null;
        //		int stiffnessCacheSize = 0;
        //
        ////		configBuilder.minSectsPerParent(2, true, true);
        ////		configBuilder.u3Cumulatives();
        //////		configBuilder.cumulativeAzChange(560f);
        ////		configBuilder.u3Azimuth();
        ////		SubSectStiffnessCalculator stiffnessCalc = new SubSectStiffnessCalculator(
        ////				subSects, 1d, 3e4, 3e4, 0.5, PatchAlignment.FILL_OVERLAP, 1d);
        ////		AggregatedStiffnessCache stiffnessCache =
        // stiffnessCalc.getAggregationCache(StiffnessType.CFF);
        ////		File stiffnessCacheFile = new File(rupSetsDir, stiffnessCache.getCacheFileName());
        ////		int stiffnessCacheSize = 0;
        ////		if (stiffnessCacheFile.exists())
        ////			stiffnessCacheSize = stiffnessCache.loadCacheFile(stiffnessCacheFile);
        //////		configBuilder.u3Coulomb(coulombRates, stiffnessCalc); outputName += "_cffFallback";
        //////		outputName += "_noCoulomb";
        ////		configBuilder.u3Coulomb(new CoulombRates(fm, new HashMap<>()), stiffnessCalc);
        // outputName += "_cffReproduce";
        //		PlausibilityConfiguration config = configBuilder.build();
        //		RuptureGrowingStrategy growingStrat = new ExhaustiveUnilateralRuptureGrowingStrategy();
        ////		float sectFract = 0.05f;
        ////		SectCountAdaptivePermutationStrategy permStrat = new
        // SectCountAdaptivePermutationStrategy(
        ////				new ExhaustiveUnilateralClusterPermuationStrategy(), sectFract, true);
        ////		configBuilder.add(permStrat.buildConnPointCleanupFilter(connectionStrategy));
        ////		outputName += "_sectFractPerm"+sectFract;
        //		outputName += ".zip";
        //		File outputDir = rupSetsDir;

        /*
         * =============================
         * For other experiments
         * =============================
         */
        /*
         * Thresholds & params
         *
         * preferred values are listed to the right of each parameter/threshold, and 'MOD' is appended whenever
         * one is temporarily modified from the preferred value.
         */
        // PLAUSIBILITY FILTERS

        /*
         * END Plausibility thresholds & params
         */

        // build stiffness calculator (used for new Coulomb)

        // TODO should we leave the constants?
        SubSectStiffnessCalculator stiffnessCalc =
                new SubSectStiffnessCalculator(
                        subSections,
                        stiffGridSpacing,
                        3e4,
                        3e4,
                        coeffOfFriction,
                        SubSectStiffnessCalculator.PatchAlignment.FILL_OVERLAP,
                        1d);
        AggregatedStiffnessCache stiffnessCache =
                stiffnessCalc.getAggregationCache(SubSectStiffnessCalculator.StiffnessType.CFF);
        //        File stiffnessCacheFile = new File(rupSetsDir, stiffnessCache.getCacheFileName());
        //        int stiffnessCacheSize = 0;
        //        if (stiffnessCacheFile.exists())
        //            stiffnessCacheSize = stiffnessCache.loadCacheFile(stiffnessCacheFile);
        // common aggregators
        // TODO which ones are important?
        AggregatedStiffnessCalculator sumAgg =
                new AggregatedStiffnessCalculator(
                        SubSectStiffnessCalculator.StiffnessType.CFF,
                        stiffnessCalc,
                        true,
                        AggregatedStiffnessCalculator.AggregationMethod.FLATTEN,
                        AggregatedStiffnessCalculator.AggregationMethod.SUM,
                        AggregatedStiffnessCalculator.AggregationMethod.SUM,
                        AggregatedStiffnessCalculator.AggregationMethod.SUM);
        AggregatedStiffnessCalculator fractRpatchPosAgg =
                new AggregatedStiffnessCalculator(
                        SubSectStiffnessCalculator.StiffnessType.CFF,
                        stiffnessCalc,
                        true,
                        AggregatedStiffnessCalculator.AggregationMethod.SUM,
                        AggregatedStiffnessCalculator.AggregationMethod.PASSTHROUGH,
                        AggregatedStiffnessCalculator.AggregationMethod.RECEIVER_SUM,
                        AggregatedStiffnessCalculator.AggregationMethod.FRACT_POSITIVE);
        //		AggregatedStiffnessCalculator threeQuarterInts = new
        // AggregatedStiffnessCalculator(StiffnessType.CFF, stiffnessCalc, true,
        //				AggregationMethod.NUM_POSITIVE, AggregationMethod.SUM, AggregationMethod.SUM,
        // AggregationMethod.THREE_QUARTER_INTERACTIONS);
        AggregatedStiffnessCalculator fractIntsAgg =
                new AggregatedStiffnessCalculator(
                        SubSectStiffnessCalculator.StiffnessType.CFF,
                        stiffnessCalc,
                        true,
                        AggregatedStiffnessCalculator.AggregationMethod.FLATTEN,
                        AggregatedStiffnessCalculator.AggregationMethod.NUM_POSITIVE,
                        AggregatedStiffnessCalculator.AggregationMethod.SUM,
                        AggregatedStiffnessCalculator.AggregationMethod.NORM_BY_COUNT);

        /*
         * Connection strategy: which faults are allowed to connect, and where?
         */
        // use this for the exact same connections as UCERF3
        //		double maxJumpDist = 5d;
        //		ClusterConnectionStrategy connectionStrategy =
        //				new UCERF3ClusterConnectionStrategy(subSects,
        //						distAzCalc, maxJumpDist, CoulombRates.loadUCERF3CoulombRates(fm));
        //		if (maxJumpDist != 5d)
        //			outputName += "_"+new DecimalFormat("0.#").format(maxJumpDist)+"km";
        ClusterConnectionStrategy connectionStrategy;
        if (plausibleConnections) {
            // use this to pick connections which agree with your plausibility filters

            // some filters need a connection strategy, use one that only includes immediate
            // neighbors at this step
            // TODO why is this maxJumpDist hard coded and what is it for?
            DistCutoffClosestSectClusterConnectionStrategy neighborsConnStrat =
                    new DistCutoffClosestSectClusterConnectionStrategy(
                            subSections, distAzCalc, 0.1d);
            List<PlausibilityFilter> connFilters = new ArrayList<>();
            if (cffRatioThresh > 0f) {
                connFilters.add(
                        new CumulativeProbabilityFilter(
                                cffRatioThresh,
                                new CoulombSectRatioProb(
                                        sumAgg,
                                        cffRatioN,
                                        favorableJumps,
                                        (float) maxJumpDist,
                                        distAzCalc)));
                if (cffRelativeProb > 0f)
                    connFilters.add(
                            new PathPlausibilityFilter(
                                    new CumulativeProbPathEvaluator(
                                            cffRatioThresh,
                                            PlausibilityResult.FAIL_HARD_STOP,
                                            new CoulombSectRatioProb(
                                                    sumAgg,
                                                    cffRatioN,
                                                    favorableJumps,
                                                    (float) maxJumpDist,
                                                    distAzCalc)),
                                    new CumulativeProbPathEvaluator(
                                            cffRelativeProb,
                                            PlausibilityResult.FAIL_HARD_STOP,
                                            new RelativeCoulombProb(
                                                    sumAgg,
                                                    neighborsConnStrat,
                                                    false,
                                                    true,
                                                    favorableJumps,
                                                    (float) maxJumpDist,
                                                    distAzCalc))));
            } else if (cffRelativeProb > 0f) {
                connFilters.add(
                        new CumulativeProbabilityFilter(
                                cffRatioThresh,
                                new RelativeCoulombProb(
                                        sumAgg,
                                        neighborsConnStrat,
                                        false,
                                        true,
                                        favorableJumps,
                                        (float) maxJumpDist,
                                        distAzCalc)));
            }
            if (cffFractInts > 0f)
                connFilters.add(new NetRuptureCoulombFilter(fractIntsAgg, cffFractInts));
            connectionStrategy =
                    new MixedPlausibleClusterConnectionStrategy(
                            subSections,
                            distAzCalc,
                            maxJumpDist,
                            PlausibleClusterConnectionStrategy.JUMP_SELECTOR_DEFAULT,
                            connFilters);
            System.out.println("Building plausible connections w/ " + numThreads + " threads...");
            connectionStrategy.checkBuildThreaded(numThreads);
            System.out.println("DONE building plausible connections");
        } else {
            // just use closest distance
            connectionStrategy =
                    new DistCutoffClosestSectClusterConnectionStrategy(
                            subSections, distAzCalc, maxJumpDist);
        }
        if (adaptiveMinDist > 0d && adaptiveMinDist < maxJumpDist) {
            connectionStrategy =
                    new AdaptiveClusterConnectionStrategy(connectionStrategy, adaptiveMinDist, 1);
        }

        PlausibilityConfiguration.Builder configBuilder =
                PlausibilityConfiguration.builder(connectionStrategy, subSections);

        /*
         * Plausibility filters: which ruptures (utilizing those connections) are allowed?
         */

        /*
         *  UCERF3 filters
         */
        //		configBuilder.u3All(CoulombRates.loadUCERF3CoulombRates(fm)); outputName += "_ucerf3";
        if (minSubSectsPerParent > 1) {
            configBuilder.minSectsPerParent(
                    this.minSubSectsPerParent, true, true); // always do this one
        }

        configBuilder.add(new DownDipRuptureGrowthPlausibilityFilter());
        configBuilder.add(new DownDipRuptureSizePlausibilityFilter());

        if (noIndirectPaths) {
            configBuilder.noIndirectPaths(true);
        }
        //		configBuilder.u3Cumulatives(); outputName += "_u3Cml"; // cml rake and azimuth
        //		configBuilder.cumulativeAzChange(560f); outputName += "_cmlAz"; // cml azimuth only
        if (cmlRakeThresh > 0) {
            configBuilder.cumulativeRakeChange(cmlRakeThresh);
        }
        //		configBuilder.cumulativeRakeChange(270f); outputName += "_cmlRake270"; // cml rake only
        //		configBuilder.cumulativeRakeChange(360f); outputName += "_cmlRake360"; // cml rake only
        //		configBuilder.u3Azimuth(); outputName += "_u3Az";
        //		configBuilder.u3Coulomb(CoulombRates.loadUCERF3CoulombRates(fm)); outputName +=
        // "_u3CFF";

        /*
         * Cumulative jump prob
         */
        // JUMP PROB: only increasing
        if (jumpProbThresh > 0f) {
            configBuilder.cumulativeProbability(jumpProbThresh, new Shaw07JumpDistProb(1d, 3d));
        }
        // JUMP RATE PROB

        /*
         * Regular slip prob
         */
        // SLIP RATE PROB: only increasing
        if (slipRateProb > 0f) {
            configBuilder.cumulativeProbability(
                    slipRateProb,
                    new RelativeSlipRateProb(connectionStrategy, true, slipIncludeLonger));
        }
        // END SLIP RATE PROB

        /*
         * Regular CFF prob (not currently used)
         */
        // CFF prob: allow neg, 0.01
        //		configBuilder.cumulativeProbability(0.01f, new RelativeCoulombProb(
        //				sumAgg, connectionStrategy, false, true, true));
        //		outputName += "_cffP0.01incr";
        // END SLIP RATE PROB

        /*
         *  CFF net rupture filters
         */
        // FRACT INTERACTIONS POSITIVE
        if (cffFractInts > 0f) {
            configBuilder.netRupCoulomb(fractIntsAgg, Range.greaterThan(cffFractInts));
        }
        // END MAIN 3/4 INTERACTIONS POSITIVE

        /** Path filters */
        List<NucleationClusterEvaluator> combPathEvals = new ArrayList<>();
        List<String> combPathPrefixes = new ArrayList<>();
        float fractPathsThreshold = 0f;
        String fractPathsStr = "";
        float favorableDist = Float.max((float) maxJumpDist, 10f);
        String favStr = "";
        if (favorableJumps) {
            favStr = "Fav";
            if (favorableDist != (float) maxJumpDist) favStr += (int) favorableDist;
        }
        // SLIP RATE PROB: as a path, only increasing NOT CURRENTLY PREFERRED
        //		float pathSlipProb = 0.1f;
        //		CumulativeJumpProbPathEvaluator slipEval = new CumulativeJumpProbPathEvaluator(
        //				pathSlipProb, PlausibilityResult.FAIL_HARD_STOP, new
        // RelativeSlipRateProb(connectionStrategy, true));
        //		combPathEvals.add(slipEval); combPathPrefixes.add("slipP"+pathSlipProb+"incr");
        ////		configBuilder.path(slipEval); outputName += "_slipPathP"+pathSlipProb+"incr"; // do it
        // separately
        // END SLIP RATE PROB
        // CFF PROB: as a path, allow negative, 0.01
        if (cffRelativeProb > 0f) {
            RelativeCoulombProb cffProbCalc =
                    new RelativeCoulombProb(
                            sumAgg,
                            connectionStrategy,
                            false,
                            true,
                            favorableJumps,
                            favorableDist,
                            distAzCalc);
            CumulativeProbPathEvaluator cffProbPathEval =
                    new CumulativeProbPathEvaluator(
                            cffRelativeProb, PlausibilityResult.FAIL_HARD_STOP, cffProbCalc);
            combPathEvals.add(cffProbPathEval);
            combPathPrefixes.add("cff" + favStr + "P" + cffRelativeProb);
        }
        //		configBuilder.path(cffProbPathEval); outputName += "_cffPathP0.01"; // do it separately
        // CFF SECT PATH: relBest, 15km
        //		SectCoulombPathEvaluator prefCFFSectPathEval = new SectCoulombPathEvaluator(
        //				sumAgg, Range.atLeast(0f), PlausibilityResult.FAIL_HARD_STOP, true, 15f, distAzCalc);
        //		combPathEvals.add(prefCFFSectPathEval); combPathPrefixes.add("cffSPathFav15");
        ////		configBuilder.path(prefCFFSectPathEval); outputName += "_cffSPathFav15"; // do it
        // separately
        // END CFF SECT PATH
        // CFF CLUSTER PATH: half RPatches positive
        //		ClusterCoulombPathEvaluator prefCFFRPatchEval = new ClusterCoulombPathEvaluator(
        //				fractRpatchPosAgg, Range.atLeast(0.5f), PlausibilityResult.FAIL_HARD_STOP);
        //		combPathEvals.add(prefCFFRPatchEval); combPathPrefixes.add("cffCPathRPatchHalfPos");
        ////		configBuilder.path(prefCFFRPatchEval); outputName += "_cffCPathRPatchHalfPos"; // do
        // it separately
        // END CFF CLUSTER PATH
        // CFF RATIO PATH: N=2, relBest, 15km
        if (cffRatioThresh > 0f) {
            CumulativeProbPathEvaluator cffRatioPatchEval =
                    new CumulativeProbPathEvaluator(
                            cffRatioThresh,
                            PlausibilityResult.FAIL_HARD_STOP,
                            new CoulombSectRatioProb(
                                    sumAgg, cffRatioN, favorableJumps, favorableDist, distAzCalc));
            combPathEvals.add(cffRatioPatchEval);
            combPathPrefixes.add("cff" + favStr + "RatioN" + cffRatioN + "P" + cffRatioThresh);
        }
        //		configBuilder.path(prefCFFRPatchEval); outputName += "_cffCPathRPatchHalfPos"; // do it
        // separately
        // END CFF RATIO PATH
        // add them
        Preconditions.checkState(combPathEvals.size() == combPathPrefixes.size());
        if (!combPathEvals.isEmpty()) {
            configBuilder.path(
                    fractPathsThreshold, combPathEvals.toArray(new NucleationClusterEvaluator[0]));
        }

        // Check connectivity only (maximum 2 clusters per rupture)
        //		configBuilder.maxNumClusters(2); outputName += "_connOnly";

        //		File outputDir = new File(rupSetsDir,
        // "fm3_1_plausible10km_slipP0.05incr_cff3_4_IntsPos_comb2Paths_cffP0.05_cffRatioN2P0.5_sectFractPerm0.05_comp");
        //		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());

        /*
         * Splay constraints
         */
        if (splays) {
            configBuilder.maxSplays(1);
            // configBuilder.splayLength(0.1, true, true); outputName += "_splayLenFract0.1";
            // configBuilder.splayLength(100, false, true, true); outputName += "_splayLen100km";
            configBuilder.splayLength(50, false, true, true);
            configBuilder.splayLength(.5, true, true, true);
            configBuilder.addFirst(new SplayConnectionsOnlyFilter(connectionStrategy, true));
        } else {
            configBuilder.maxSplays(0); // default, no splays
        }

        /*
         * Growing strategies: how should ruptures be broken up and spread onto new faults
         */
        RuptureGrowingStrategy growingStrat;
        if (bilateral) {
            growingStrat =
                    new ExhaustiveBilateralRuptureGrowingStrategy(
                            SecondaryVariations.EQUAL_LEN, false);
        } else {
            growingStrat = new ExhaustiveUnilateralRuptureGrowingStrategy();
        }
        if (adaptiveSectFract > 0f) {
            SectCountAdaptiveRuptureGrowingStrategy adaptiveStrat =
                    new SectCountAdaptiveRuptureGrowingStrategy(
                            growingStrat, adaptiveSectFract, true, minSubSectsPerParent);
            configBuilder.add(adaptiveStrat.buildConnPointCleanupFilter(connectionStrategy));
            growingStrat = adaptiveStrat;
        }
        DownDipConstraint constraint =
                DownDipConstraint.aspectRatioConstraint(
                                downDipMinAspect, downDipMaxAspect, downDipAspectDepthThreshold)
                        .and(DownDipConstraint.minFillConstraint(downDipMinFill))
                        .and(DownDipConstraint.connectednessConstraint());

        RuptureGrowingStrategy downDipPermutationStrategy =
                new DownDipPermutationStrategy(constraint);

        RuptureGrowingStrategy mixedGrowingStrategy =
                new MixedGrowingStrategy(growingStrat, downDipPermutationStrategy);

        // build our configuration
        PlausibilityConfiguration config = configBuilder.build();

        for (int i = 0; i < config.getFilters().size(); i++) {
            PlausibilityFilter filter = config.getFilters().get(i);
            if (!filter.getName().startsWith("DownDip")
                    && filter.getClass() != MinSectsPerParentFilter.class
                    && filter.getClass() != DirectPathPlausibilityFilter.class
                    && filter.getClass() != CumulativeRakeChangeFilter.class) {
                config.getFilters().set(i, new DownDipSkipPlausibilityFilter(filter));
            }
        }

        /*
         * =============================
         * END other experiments
         * =============================
         */

        //        config.getConnectionStrategy().getClusters();
        //        if (numAzCached < distAzCalc.getNumCachedAzimuths()
        //                || numDistCached < distAzCalc.getNumCachedDistances()) {
        //            System.out.println("Writing dist/az cache to
        // "+distAzCacheFile.getAbsolutePath());
        //            distAzCalc.writeCacheFile(distAzCacheFile);
        //            numAzCached = distAzCalc.getNumCachedAzimuths();
        //            numDistCached = distAzCalc.getNumCachedDistances();
        //        }

        ClusterRuptureBuilder builder = new ClusterRuptureBuilder(config);

        if (debugCriteria != null) builder.setDebugCriteria(debugCriteria, stopAfterDebug);
        System.out.println("Building ruptures with " + numThreads + " threads...");
        Stopwatch watch = Stopwatch.createStarted();
        ruptures = builder.build(mixedGrowingStrategy, numThreads);
        watch.stop();
        long millis = watch.elapsed(TimeUnit.MILLISECONDS);
        double secs = millis / 1000d;
        double mins = (secs / 60d);
        DecimalFormat timeDF = new DecimalFormat("0.00");
        System.out.println(
                "Built "
                        + countDF.format(ruptures.size())
                        + " ruptures in "
                        + timeDF.format(secs)
                        + " secs = "
                        + timeDF.format(mins)
                        + " mins. Total rate: "
                        + rupRate(ruptures.size(), millis));

        int crustalCount = 0;
        int subductionCount = 0;
        int jointCount = 0;

        for (ClusterRupture rupture : ruptures) {
            int crustal = 0;
            int subduction = 0;
            for (FaultSubsectionCluster cluster : rupture.clusters) {
                if (cluster.startSect instanceof DownDipFaultSection) {
                    subduction++;
                } else {
                    crustal++;
                }
            }
            if (subduction == 0) {
                crustalCount++;
            } else if (crustal == 0) {
                subductionCount++;
            } else {
                jointCount++;
            }
        }

        System.out.println(
                "crustal: "
                        + crustalCount
                        + " subduction: "
                        + subductionCount
                        + " joint: "
                        + jointCount);

        Map<Integer, Integer> jointStats = new HashMap<>();
        List<Jump> jointJumps =
                connectionStrategy.getAllPossibleJumps().stream()
                        .filter(j -> j.fromSection instanceof DownDipFaultSection)
                        .collect(Collectors.toList());
        Set<Integer> puyJumps =
                jointJumps.stream()
                        .filter(
                                j ->
                                        j.fromSection.getParentSectionId()
                                                == NZSHM22_FaultModels.SBD_0_2_PUY_15
                                                        .getParentSectionId())
                        .map(j -> j.toSection.getParentSectionId())
                        .collect(Collectors.toSet());
        Set<Integer> hikJumps =
                jointJumps.stream()
                        .filter(
                                j ->
                                        j.fromSection.getParentSectionId()
                                                == NZSHM22_FaultModels.SBD_0_3_HKR_LR_30
                                                        .getParentSectionId())
                        .map(j -> j.toSection.getParentSectionId())
                        .collect(Collectors.toSet());

        System.out.println("finding connecting ruptures");

        for (int i = 0; i < ruptures.size(); i++) {
            ClusterRupture rupture = ruptures.get(i);
            int start = rupture.clusters[0].startSect.getSectionId();
            FaultSubsectionCluster cluster = rupture.clusters[rupture.clusters.length - 1];
            int end = cluster.subSects.get(cluster.subSects.size() - 1).getSectionId();
            if ((puyJumps.contains(start) && hikJumps.contains((end)))
                    || (puyJumps.contains(end) && hikJumps.contains(start))) {
                System.out.println(
                        "connecting rupture "
                                + i
                                + " with "
                                + rupture.getTotalNumClusters()
                                + " clusters");
                for (FaultSubsectionCluster c : rupture.clusters) {
                    System.out.println("  " + c.parentSectionID + " " + c.parentSectionName);
                }
            }
        }
        System.out.println("done finding connecting ruptures");

        List<ClusterRupture> jointRuptures = new ArrayList<>();
        for (Jump jump : jointJumps) {
            FaultSection subdubSection = jump.fromSection;
            jointStats.compute(subdubSection.getSectionId(), (k, v) -> v == null ? 0 : v);
            for (ClusterRupture rupture : ruptures) {
                List<FaultSection> sections = rupture.buildOrderedSectionList();
                if (sections.get(0) == subdubSection
                        || sections.get(sections.size() - 1) == subdubSection) {
                    jointStats.compute(subdubSection.getSectionId(), (k, v) -> v + 1);
                }
            }
        }

        //        JointRuptureBuilder jointRuptureBuilder = new
        // JointRuptureBuilder(connectionStrategy, ruptures, distAzCalc, 1, 200);
        //
        //        List<ClusterRupture> newJointRuptures =
        // jointRuptureBuilder.build(NZSHM22_FaultModels.SBD_0_2_PUY_15.getParentSectionId());
        //        ruptures.addAll(newJointRuptures);
        //        newJointRuptures =
        // jointRuptureBuilder.build(NZSHM22_FaultModels.SBD_0_3_HKR_LR_30.getParentSectionId());
        //        ruptures.addAll(newJointRuptures);

        FaultSystemRupSet rupSet =
                FaultSystemRupSet.builderForClusterRups(subSections, ruptures)
                        .rupLengths(buildLengths())
                        .forScalingRelationship(getScalingRelationship())
                        .slipAlongRupture(getSlipAlongRuptureModel())
                        .addModule(getLogicTreeBranch(FaultRegime.CRUSTAL))
                        .addModule(SectionDistanceAzimuthCalculator.archivableInstance(distAzCalc))
                        .build();

        return rupSet;

        //        if (numAzCached < distAzCalc.getNumCachedAzimuths()
        //                || numDistCached < distAzCalc.getNumCachedDistances()) {
        //            System.out.println("Writing dist/az cache to
        // "+distAzCacheFile.getAbsolutePath());
        //            distAzCalc.writeCacheFile(distAzCacheFile);
        //            System.out.println("DONE writing dist/az cache");
        //        }
        //
        //        if (stiffnessCache != null && stiffnessCacheFile != null
        //                && stiffnessCacheSize < stiffnessCache.calcCacheSize()) {
        //            System.out.println("Writing stiffness cache to
        // "+stiffnessCacheFile.getAbsolutePath());
        //            stiffnessCache.writeCacheFile(stiffnessCacheFile);
        //            System.out.println("DONE writing stiffness cache");
        //        }
    }

    private static String rupRate(int count, long timeDeltaMillis) {
        if (timeDeltaMillis == 0) return "N/A rups/s";
        double timeDeltaSecs = (double) timeDeltaMillis / 1000d;

        double perSec = (double) count / timeDeltaSecs;
        if (count == 0 || perSec >= 0.1) {
            if (perSec > 10) return countDF.format(perSec) + " rups/s";
            return oneDigitDF.format(perSec) + " rups/s";
        }
        // switch to per minute
        double perMin = perSec * 60d;
        if (perMin >= 0.1) {
            if (perMin > 10) return countDF.format(perMin) + " rups/m";
            return oneDigitDF.format(perMin) + " rups/m";
        }
        // fallback to per hour
        double perHour = perMin * 60d;
        if (perHour > 10) return countDF.format(perHour) + " rups/hr";
        return oneDigitDF.format(perHour) + " rups/hr";
    }

    public MixedRuptureSetBuilder setNoIndirectPaths(boolean noIndirectPaths) {
        this.noIndirectPaths = noIndirectPaths;
        return this;
    }

    public MixedRuptureSetBuilder setSlipRateProb(float slipRateProb) {
        this.slipRateProb = slipRateProb;
        return this;
    }

    public MixedRuptureSetBuilder setSlipIncludeLonger(boolean slipIncludeLonger) {
        this.slipIncludeLonger = slipIncludeLonger;
        return this;
    }

    public MixedRuptureSetBuilder setCffFractInts(float cffFractInts) {
        this.cffFractInts = cffFractInts;
        return this;
    }

    public MixedRuptureSetBuilder setCffRatioN(int cffRatioN) {
        this.cffRatioN = cffRatioN;
        return this;
    }

    public MixedRuptureSetBuilder setCffRatioThresh(float cffRatioThresh) {
        this.cffRatioThresh = cffRatioThresh;
        return this;
    }

    public MixedRuptureSetBuilder setCffRelativeProb(float cffRelativeProb) {
        this.cffRelativeProb = cffRelativeProb;
        return this;
    }

    public MixedRuptureSetBuilder setFavorableJumps(boolean favorableJumps) {
        this.favorableJumps = favorableJumps;
        return this;
    }

    public MixedRuptureSetBuilder setJumpProbThresh(float jumpProbThresh) {
        this.jumpProbThresh = jumpProbThresh;
        return this;
    }

    public MixedRuptureSetBuilder setCmlRakeThresh(float cmlRakeThresh) {
        this.cmlRakeThresh = cmlRakeThresh;
        return this;
    }

    public MixedRuptureSetBuilder setMaxJumpDistance(double maxJumpDist) {
        this.maxJumpDist = maxJumpDist;
        return this;
    }

    public MixedRuptureSetBuilder setPlausibleConnections(boolean plausibleConnections) {
        this.plausibleConnections = plausibleConnections;
        return this;
    }

    public MixedRuptureSetBuilder setAdaptiveMinDist(double adaptiveMinDist) {
        this.adaptiveMinDist = adaptiveMinDist;
        return this;
    }

    public MixedRuptureSetBuilder setAdaptiveSectFract(float adaptiveSectFract) {
        this.adaptiveSectFract = adaptiveSectFract;
        return this;
    }

    public MixedRuptureSetBuilder setBilateral(boolean bilateral) {
        this.bilateral = bilateral;
        return this;
    }

    //    public static void main(String[] args) throws IOException {
    //        FaultSystemRupSet rupSet = InversionFaultSystemRupSet.load(new
    // File("C:\\Users\\volkertj\\Dropbox\\RupSet_Sub_FM(SBD_0_3_HKR_LR_30)_mnSbS(2)_mnSSPP(2)_mxSSL(0.5)_ddAsRa(2.0,5.0,5)_ddMnFl(0.1)_ddPsCo(0.0)_ddSzCo(0.0)_thFc(0.0).zip"));
    //        rupSet.write(new File("C:\\tmp\\oldsub.zip"));
    //    }

    public static void main(String[] args) throws DocumentException, IOException {
        MixedRuptureSetBuilder builder = new MixedRuptureSetBuilder();
        SimplifiedScalingRelationship sr = new SimplifiedScalingRelationship();
        sr.setupCrustal(4.2, 4.2);

        builder.setFaultModel(NZSHM22_FaultModels.SBD_0_1_PUY_30);
        builder.setIdRangeFilter(0, 2000);
        builder.setNumThreads(12);
        ((MixedRuptureSetBuilder) builder.setScalingRelationship(sr))
                .setAdaptiveMinDist(6.0d)
                .setMaxJumpDistance(5d)
                .setAdaptiveSectFract(0.1f)
                .setDownDipAspectRatio(2, 5, 5)
                .setDownDipPositionCoarseness(0.0)
                .setDownDipSizeCoarseness(0.0)
                .setDownDipMinFill(0.1);

        System.out.println(builder.getDescriptiveName());
        FaultSystemRupSet ruptureSet = builder.buildRuptureSet();
        File outputPath = new File("TEST/ruptures/" + builder.getDescriptiveName() + ".zip");
        ruptureSet.write(outputPath);

        //
        //
        //        NZSHM22_ReportPageGen reportPageGen = new NZSHM22_ReportPageGen();
        //        reportPageGen.setName("joint")
        //                .setOutputPath("/tmp/reports/joint3")
        //                .setRuptureSet(outputPath.getPath());
        //        reportPageGen.generateRupSetPage();

    }
}

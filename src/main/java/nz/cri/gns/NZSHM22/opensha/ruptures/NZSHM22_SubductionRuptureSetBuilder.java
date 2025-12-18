package nz.cri.gns.NZSHM22.opensha.ruptures;

import com.google.common.base.Preconditions;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.FaultRegime;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.faults.FaultSectionList;
import nz.cri.gns.NZSHM22.opensha.ruptures.downDip.DownDipConstraint;
import nz.cri.gns.NZSHM22.opensha.ruptures.downDip.DownDipPermutationStrategy;
import nz.cri.gns.NZSHM22.opensha.ruptures.downDip.FaultTypeSeparationConnectionStrategy;
import nz.cri.gns.NZSHM22.opensha.util.ParameterRunner;
import org.dom4j.DocumentException;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.NamedFaults;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRuptureBuilder;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.JumpAzimuthChangeFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.RuptureGrowingStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;

/**
 * Builds opensha SlipAlongRuptureModelRupSet rupture sets using NZ Subduction Ruptures
 *
 * <p>Note these have a differnt Faulty Model, with pre-computed subsections built externally with
 * eq-fault-geom project
 */
public class NZSHM22_SubductionRuptureSetBuilder extends NZSHM22_AbstractRuptureSetBuilder {

    //	PlausibilityConfiguration plausibilityConfig;

    double thinningFactor = 0;
    double downDipMinAspect = 1;
    double downDipMaxAspect = 3;
    int downDipAspectDepthThreshold =
            Integer.MAX_VALUE; // from this 'depth' (in tile rows) the max aspect constraint
    // is ignored
    double downDipMinFill = 1; // 1 means only allow complete rectangles
    double downDipPositionCoarseness = 0; // 0 means no coarseness
    double downDipSizeCoarseness = 0; // 0 means no coarseness

    int maxRuptures = Integer.MAX_VALUE;

    /** Constructs a new NZSHM22_RuptureSetBuilder with the default NSHM configuration. */
    public NZSHM22_SubductionRuptureSetBuilder() {
        subSections = new FaultSectionList();
    }

    @Override
    public String getDescriptiveName() {
        String description = "RupSet_Sub";
        description += super.getDescriptiveName();

        description +=
                "_ddAsRa("
                        + downDipMinAspect
                        + ","
                        + downDipMaxAspect
                        + ","
                        + downDipAspectDepthThreshold
                        + ")";
        description += "_ddMnFl(" + downDipMinFill + ")";
        description += "_ddPsCo(" + downDipPositionCoarseness + ")";
        description += "_ddSzCo(" + downDipSizeCoarseness + ")";

        // description += "_pmSt(" + permutationStrategyClass.name() + ")";
        description += "_thFc(" + thinningFactor + ")";
        return description;
    }

    /**
     * Chooses a known fault model.
     *
     * @param faultModel the name of a known fault model
     * @return this object
     */
    public NZSHM22_SubductionRuptureSetBuilder setFaultModel(String faultModel) {
        setFaultModel(NZSHM22_FaultModels.valueOf(faultModel));
        return this;
    }

    /**
     * Chooses a known SlipAlongRuptureModel model.
     *
     * @param slipAlongRuptureModel the name of a known slipAlongRuptureModel
     * @return this object
     */
    public NZSHM22_SubductionRuptureSetBuilder setSlipAlongRuptureModel(
            String slipAlongRuptureModel) {
        setSlipAlongRuptureModel(SlipAlongRuptureModels.valueOf(slipAlongRuptureModel));
        return this;
    }

    public NZSHM22_SubductionRuptureSetBuilder setMaxRuptures(int maxRuptures) {
        this.maxRuptures = maxRuptures;
        return this;
    }

    /**
     * Chooses a known ScalingRelationship model.
     *
     * @param scalingRelationship the name of a known scalingRelationship
     * @return this object
     */
    public NZSHM22_SubductionRuptureSetBuilder setScalingRelationship(String scalingRelationship) {
        setScalingRelationship(ScalingRelationships.valueOf(scalingRelationship));
        return this;
    }

    /**
     * Sets the aspect ratio boundaries for subduction zone ruptures.
     *
     * @param minAspect the minimum aspect ratio
     * @param maxAspect the maximum aspect ratio
     * @return this builder
     */
    public NZSHM22_SubductionRuptureSetBuilder setDownDipAspectRatio(
            double minAspect, double maxAspect) {
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
    public NZSHM22_SubductionRuptureSetBuilder setDownDipAspectRatio(
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
    public NZSHM22_SubductionRuptureSetBuilder setDownDipMinFill(double minFill) {
        this.downDipMinFill = minFill;
        return this;
    }

    /**
     * Sets the position coarseness for subduction zone ruptures.
     *
     * @param epsilon epsilon
     * @return this builder
     */
    public NZSHM22_SubductionRuptureSetBuilder setDownDipPositionCoarseness(double epsilon) {
        // do not use until understood and tested
        Preconditions.checkArgument(epsilon == 0);
        this.downDipPositionCoarseness = epsilon;
        return this;
    }

    /**
     * Sets the size coarseness for subduction zone ruptures.
     *
     * @param epsilon epsilon
     * @return this builder
     */
    public NZSHM22_SubductionRuptureSetBuilder setDownDipSizeCoarseness(double epsilon) {
        // do not use until understood and tested
        Preconditions.checkArgument(epsilon == 0);
        this.downDipSizeCoarseness = epsilon;
        return this;
    }

    private void buildConfig() {
        SectionDistanceAzimuthCalculator distAzCalc =
                new SectionDistanceAzimuthCalculator(subSections);
        JumpAzimuthChangeFilter.AzimuthCalc azimuthCalc =
                new JumpAzimuthChangeFilter.SimpleAzimuthCalc(distAzCalc);

        // connection strategy: parent faults connect at closest point, and only when
        // dist <=5 km
        ClusterConnectionStrategy connectionStrategy =
                new FaultTypeSeparationConnectionStrategy(subSections, distAzCalc, 5.0d);
        System.out.println("Built connectionStrategy _ although it's never used here");

        int maxNumSplays = 0; // don't allow any splays
        PlausibilityConfiguration.Builder configBuilder =
                PlausibilityConfiguration.builder(connectionStrategy, distAzCalc)
                        .maxSplays(maxNumSplays);

        plausibilityConfig = configBuilder.build();
    }

    private double[] buildLengths() {

        double[] result = new double[ruptures.size()];

        for (int r = 0; r < ruptures.size(); r++) {
            ClusterRupture rup = ruptures.get(r);
            int currentMin = Integer.MAX_VALUE;

            List<FaultSection> rupSects = rup.buildOrderedSectionList();
            // get min row of downdip (assumes there's just one of these)
            for (FaultSection sect : rupSects) {
                currentMin = Math.min(currentMin, ((DownDipFaultSection) sect).getRowIndex());
            }

            double length = 0d;
            // iterate sections adding lengths from those in the minimum row
            for (FaultSection sect : rupSects) {
                if (((DownDipFaultSection) sect).getRowIndex() == currentMin) {
                    length += sect.getTraceLength();
                }
            }

            result[r] = length * 1e3;
        }
        return result;
    }

    /**
     * Builds an NSHM rupture set according to the configuration.
     *
     * @return a SlipAlongRuptureModelRupSet built according to the configuration from the input
     *     fsdFile
     * @throws DocumentException
     * @throws IOException
     */
    @Override
    public FaultSystemRupSet buildRuptureSet() throws DocumentException, IOException {

        loadFaults();

        buildConfig();
        System.out.println("Built PlausibilityConfiguration");

        builder = new ClusterRuptureBuilder(getPlausibilityConfig());
        System.out.println("initialised ClusterRuptureBuilder");

        // CBC debugging...
        // ParentSectsRupDebugCriteria debugCriteria = new
        // ParentSectsRupDebugCriteria(false, true, 2);
        // builder.setDebugCriteria(debugCriteria, true);

        DownDipConstraint constraint =
                DownDipConstraint.aspectRatioConstraint(
                                downDipMinAspect, downDipMaxAspect, downDipAspectDepthThreshold)
                        .and(DownDipConstraint.minFillConstraint(downDipMinFill))
                        .and(DownDipConstraint.connectednessConstraint());

        //// RuptureGrowingStrategy permutationStrategy =
        // createPermutationStrategy(permutationStrategyClass);
        RuptureGrowingStrategy permutationStrategy = new DownDipPermutationStrategy(constraint);

        // debugging
        // numThreads = 1;
        ruptures = getBuilder().build(permutationStrategy, numThreads);

        if (thinningFactor <= 0) {
            System.out.println("Built " + ruptures.size() + " total ruptures");
        } else {
            ruptures =
                    RuptureThinning.filterRuptures(
                            ruptures,
                            RuptureThinning.downDipPredicate()
                                    .or(
                                            RuptureThinning.coarsenessPredicate(thinningFactor)
                                                    .or(
                                                            RuptureThinning.endToEndPredicate(
                                                                    getPlausibilityConfig()
                                                                            .getConnectionStrategy()))));
            System.out.println("Built " + ruptures.size() + " total ruptures after thinning");
        }

        if (maxRuptures != Integer.MAX_VALUE) {
            ruptures = ruptures.stream().limit(maxRuptures).collect(Collectors.toList());
        }

        FaultSystemRupSet rupSet =
                FaultSystemRupSet.builderForClusterRups(subSections, ruptures)
                        .rupLengths(buildLengths())
                        .forScalingRelationship(getScalingRelationship())
                        .slipAlongRupture(getSlipAlongRuptureModel())
                        .addModule(getPlausibilityConfig().getDistAzCalc())
                        .addModule(getPlausibilityConfig())
                        .addModule(getLogicTreeBranch(FaultRegime.SUBDUCTION))
                        .addModule(createBuildInfo())
                        .build();

        if (faultModel != null && faultModel.getCustomModel() != null) {
            rupSet.addModule(new CustomFaultModel(faultModel.getCustomModel()));
        }
        if (faultModel != null) {
            Map<String, List<Integer>> mapping = faultModel.getNamedFaultsMapAlt();

            if (mapping != null) {
                NamedFaults namedFaults = new NamedFaults(rupSet, mapping);
                rupSet.addModule(namedFaults);
            }
        }

        return rupSet;
    }

    public static void main(String[] args) throws DocumentException, IOException {
        ParameterRunner.buildNZSHM22HikurangiRupset();
        ParameterRunner.buildNZSHM22PuysegurRupset();
    }
}

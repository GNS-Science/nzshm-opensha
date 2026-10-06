package nz.cri.gns.NZSHM22.opensha.inversion;

import com.google.common.base.Preconditions;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import nz.cri.gns.NZSHM22.opensha.analysis.NZSHM22_FaultSystemRupSetCalc;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.Config;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionConfig;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.scaling.JointScalingRelationship;
import nz.cri.gns.NZSHM22.opensha.ruptures.CustomDeformationModel;
import nz.cri.gns.NZSHM22.opensha.ruptures.CustomFaultModel;
import nz.cri.gns.NZSHM22.opensha.ruptures.NZSHM22_RuptureSetBuilderModule;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.BuildInfoModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.modules.SplittableRuptureModule;

/**
 * Filters a rupture set by magnitude, dropping the ruptures that fall outside the minimum and
 * maximum magnitude bounds. Joint rupture sets are filtered with {@link #filter(FaultSystemRupSet,
 * Config)}, which tests each partition of a rupture against that partition's bounds. Other rupture
 * sets can be filtered with {@link #filter(FaultSystemRupSet, double, double)}. Magnitudes are
 * compared by magnitude bin, see {@link NZSHM22_FaultSystemRupSetCalc#isWithinBounds(double,
 * double, double)}.
 */
public class MagFilteredRupSet {

    /**
     * Modules that neither implement {@link SplittableRuptureModule} nor depend on the number of
     * ruptures, and can therefore be carried over unchanged.
     */
    public static final List<Class<? extends OpenSHA_Module>> RUPTURE_COUNT_AGNOSTIC_MODULES =
            List.of(
                    BuildInfoModule.class,
                    TvzDomainSections.class,
                    CustomFaultModel.class,
                    CustomDeformationModel.class,
                    NZSHM22_RuptureSetBuilderModule.class);

    /**
     * A maximum magnitude that is above the highest magnitude bin and therefore does not exclude
     * any rupture.
     */
    public static final double NO_MAX_MAG = NZSHM22_FaultSystemRupSetCalc.MAG_BINS.getMaxX();

    protected MagFilteredRupSet() {}

    /**
     * Returns true if the part of the rupture that is inside the partition is within the
     * partition's magnitude bounds. The magnitude of that part is calculated with {@link
     * JointScalingRelationship#partitionMagnitude(double, double, double)}. A rupture that does not
     * use any section of the partition is always within bounds, and a rupture that is entirely
     * inside the partition is tested with its own magnitude.
     *
     * @param rupSet the rupture set
     * @param partitionConfig the partition with its minimum and maximum magnitude
     * @param ruptureIndex the rupture to test
     * @return true if the rupture is within the partition's magnitude bounds
     */
    protected static boolean isWithinMagBounds(
            FaultSystemRupSet rupSet, PartitionConfig partitionConfig, int ruptureIndex) {
        List<Integer> sections = rupSet.getSectionsIndicesForRup(ruptureIndex);
        List<Integer> partitionSections =
                sections.stream().filter(partitionConfig::covers).collect(Collectors.toList());
        if (partitionSections.isEmpty()) {
            return true;
        }

        // same as FilteredFaultSystemRupSet: a rupture entirely inside the partition keeps its
        // magnitude, independent of whether the section areas add up to the rupture area
        if (partitionSections.size() == sections.size()) {
            return NZSHM22_FaultSystemRupSetCalc.isWithinBounds(
                    partitionConfig.minMag,
                    partitionConfig.maxMag,
                    rupSet.getMagForRup(ruptureIndex));
        }

        double partitionArea =
                partitionSections.stream().mapToDouble(rupSet::getAreaForSection).sum();
        double partitionMag =
                JointScalingRelationship.partitionMagnitude(
                        partitionArea,
                        rupSet.getAreaForRup(ruptureIndex),
                        rupSet.getMagForRup(ruptureIndex));

        return NZSHM22_FaultSystemRupSetCalc.isWithinBounds(
                partitionConfig.minMag, partitionConfig.maxMag, partitionMag);
    }

    /**
     * Creates a rupture set that only contains those ruptures of the original that are within the
     * magnitude bounds of every partition they belong to, see {@link
     * #isWithinMagBounds(FaultSystemRupSet, PartitionConfig, int)}.
     *
     * @param original the rupture set to filter
     * @param config the joint inversion config. The partition configs must have been initialised so
     *     that they can test which sections they cover.
     * @return the filtered rupture set
     * @throws IllegalStateException if all ruptures are outside the magnitude bounds
     */
    public static FaultSystemRupSet filter(FaultSystemRupSet original, Config config) {
        boolean[] drop = new boolean[original.getNumRuptures()];
        for (int r = 0; r < drop.length; r++) {
            for (PartitionConfig partitionConfig : config.partitions) {
                if (!isWithinMagBounds(original, partitionConfig, r)) {
                    drop[r] = true;
                }
            }
        }
        return filter(original, drop);
    }

    /**
     * Legacy filter, not to be used with joint rupture sets. Creates a rupture set that only
     * contains those ruptures of the original whose magnitude is within the bounds.
     *
     * @param original the rupture set to filter
     * @param minMag the minimum magnitude. The whole bin of minMag is retained.
     * @param maxMag the maximum magnitude. The whole bin of maxMag is retained. Use {@link
     *     #NO_MAX_MAG} for no upper bound.
     * @return the filtered rupture set
     * @throws IllegalStateException if all ruptures are outside the magnitude bounds
     */
    public static FaultSystemRupSet filter(
            FaultSystemRupSet original, double minMag, double maxMag) {
        boolean[] drop = new boolean[original.getNumRuptures()];
        for (int r = 0; r < drop.length; r++) {
            drop[r] =
                    !NZSHM22_FaultSystemRupSetCalc.isWithinBounds(
                            minMag, maxMag, original.getMagForRup(r));
        }
        return filter(original, drop);
    }

    /**
     * Creates a rupture set that only contains those ruptures of the original that are not marked
     * to be dropped.
     *
     * @param original the rupture set to filter
     * @param drop one entry per rupture, true if the rupture is to be dropped
     * @return the filtered rupture set
     * @throws IllegalStateException if all ruptures are dropped
     */
    protected static FaultSystemRupSet filter(FaultSystemRupSet original, boolean[] drop) {
        Set<Integer> retainedRuptureIds = new LinkedHashSet<>();
        for (int ruptureId = 0; ruptureId < drop.length; ruptureId++) {
            if (!drop[ruptureId]) {
                retainedRuptureIds.add(ruptureId);
            }
        }
        Preconditions.checkState(
                !retainedRuptureIds.isEmpty(),
                "All %s ruptures of the rupture set are outside the magnitude bounds.",
                original.getNumRuptures());

        ClusterRuptures clusterRuptures = original.getModule(ClusterRuptures.class);
        if (clusterRuptures != null) {
            original.removeModule(clusterRuptures);
        }
        FaultSystemRupSet filtered = original.getForRuptureSubSet(retainedRuptureIds);
        if (clusterRuptures != null) {
            original.addModule(clusterRuptures);
        }

        for (Class<? extends OpenSHA_Module> type : RUPTURE_COUNT_AGNOSTIC_MODULES) {
            OpenSHA_Module module = original.getModule(type);
            if (module != null) {
                filtered.addModule(module);
            }
        }

        return filtered;
    }
}

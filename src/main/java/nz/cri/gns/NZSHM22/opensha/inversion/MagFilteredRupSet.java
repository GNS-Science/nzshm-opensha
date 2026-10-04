package nz.cri.gns.NZSHM22.opensha.inversion;

import com.google.common.base.Preconditions;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
import org.opensha.sha.earthquake.faultSysSolution.modules.ModSectMinMags;
import org.opensha.sha.earthquake.faultSysSolution.modules.SplittableRuptureModule;

/**
 * Filters a rupture set by magnitude, dropping the ruptures that fall below the minimum magnitude
 * of any of the sections they use, and those above the maximum magnitude, with each partition of a
 * joint rupture tested separately. See {@link #filter(FaultSystemRupSet, ModSectMinMags, List,
 * double[])}.
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

    protected static boolean isWithinMagBounds(
            FaultSystemRupSet rupSet, PartitionConfig partitionConfig, int ruptureIndex) {
        double partitionArea =
                rupSet.getSectionsIndicesForRup(ruptureIndex).stream()
                        .filter(partitionConfig::covers)
                        .mapToDouble(rupSet::getAreaForSection)
                        .sum();
        if (partitionArea == 0) {
            return true;
        }

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
     * magnitude bounds of every partition they belong to.
     *
     * @param original the rupture set to filter
     * @param config the joint inversion config
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
     * Legacy filter, not to be used with joint rupture sets.
     *
     * @param original
     * @param minMag
     * @param maxMag
     * @return
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

        FaultSystemRupSet filtered = original.getForRuptureSubSet(retainedRuptureIds);

        for (Class<? extends OpenSHA_Module> type : RUPTURE_COUNT_AGNOSTIC_MODULES) {
            OpenSHA_Module module = original.getModule(type);
            if (module != null) {
                filtered.addModule(module);
            }
        }

        return filtered;
    }
}

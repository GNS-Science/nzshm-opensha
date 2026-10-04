package nz.cri.gns.NZSHM22.opensha.inversion;

import com.google.common.base.Preconditions;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;
import nz.cri.gns.NZSHM22.opensha.analysis.NZSHM22_FaultSystemRupSetCalc;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.scaling.JointScalingRelationship;
import nz.cri.gns.NZSHM22.opensha.ruptures.CustomDeformationModel;
import nz.cri.gns.NZSHM22.opensha.ruptures.CustomFaultModel;
import nz.cri.gns.NZSHM22.opensha.ruptures.NZSHM22_RuptureSetBuilderModule;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.BuildInfoModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModSectMinMags;
import org.opensha.sha.earthquake.faultSysSolution.modules.SplittableRuptureModule;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

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

    /**
     * Creates a rupture set that only contains those ruptures of the original that are within the
     * magnitude bounds of every partition they belong to. For each partition, the magnitude of the
     * part of the rupture inside that partition (see {@link
     * JointScalingRelationship#partitionMagnitude(double, double, double)}) must not fall below the
     * minimum magnitude of any of the rupture's sections in that partition, and must not be in a
     * bin above the bin of the partition's maximum magnitude. The whole bin of a maximum magnitude
     * is retained. A rupture that is in a single partition is tested with its own magnitude.
     *
     * @param original the rupture set to filter
     * @param minMags the section minimum magnitudes to test the ruptures against
     * @param partitions predicates on section ids, one per partition
     * @param maxMags the maximum magnitude of each partition. Use {@link #NO_MAX_MAG} for no upper
     *     bound.
     * @return the filtered rupture set
     * @throws IllegalStateException if all ruptures are outside the magnitude bounds
     */
    public static FaultSystemRupSet filter(
            FaultSystemRupSet original,
            ModSectMinMags minMags,
            List<IntPredicate> partitions,
            double[] maxMags) {
        Preconditions.checkArgument(
                partitions.size() == maxMags.length, "maxMags must have one entry per partition");
        for (double maxMag : maxMags) {
            Preconditions.checkArgument(
                    Double.isFinite(maxMag),
                    "maxMag must be finite, use NO_MAX_MAG for no upper bound");
        }
        IncrementalMagFreqDist bins = NZSHM22_FaultSystemRupSetCalc.MAG_BINS;
        boolean[] drop = new boolean[original.getNumRuptures()];
        for (int r = 0; r < drop.length; r++) {
            for (int p = 0; p < maxMags.length; p++) {
                double area = 0;
                int minMagForSectionBin = 0;
                for (int s : original.getSectionsIndicesForRup(r)) {
                    if (partitions.get(p).test(s)) {
                        area += original.getAreaForSection(s);
                        minMagForSectionBin =
                                Math.max(
                                        minMagForSectionBin,
                                        bins.getClosestXIndex(minMags.getMinMagForSection(s)));
                    }
                }
                if (area > 0) {
                    int bin =
                            bins.getClosestXIndex(
                                    JointScalingRelationship.partitionMagnitude(
                                            area,
                                            original.getAreaForRup(r),
                                            original.getMagForRup(r)));
                    drop[r] |= bin < minMagForSectionBin || bin > bins.getClosestXIndex(maxMags[p]);
                }
            }
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

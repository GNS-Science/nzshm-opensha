package nz.cri.gns.NZSHM22.opensha.inversion;

import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import java.util.ArrayList;
import java.util.List;
import nz.cri.gns.NZSHM22.opensha.ruptures.CustomDeformationModel;
import nz.cri.gns.NZSHM22.opensha.ruptures.CustomFaultModel;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.BuildInfoModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.RuptureSubSetMappings;
import org.opensha.sha.earthquake.faultSysSolution.modules.SplittableRuptureModule;

/**
 * A rupture set that keeps only those ruptures of an original rupture set whose magnitude lies
 * within a magnitude range. Ruptures with a magnitude below minMag or above maxMag are excluded.
 *
 * <p>All fault sections of the original rupture set are kept, even if no remaining rupture uses
 * them, so that section ids are unchanged. Rupture magnitudes, rakes, areas and lengths are carried
 * over verbatim from the original rupture set rather than being recalculated.
 *
 * <p>Modules that implement {@link SplittableRuptureModule} are filtered and attached to the
 * result, as are the rupture count agnostic modules listed in {@link
 * #RUPTURE_COUNT_AGNOSTIC_MODULES}. All other modules are dropped. A {@link RuptureSubSetMappings}
 * module describing the rupture id mapping is attached as well.
 *
 * <p>This is intended to be used by {@link NZSHM22_AbstractInversionRunner} on the rupture set as
 * loaded from file, before it is wrapped in a {@link NZSHM22_InversionFaultSystemRuptSet}.
 */
public class MagFilteredRupSet extends FaultSystemRupSet {

    /**
     * Modules that neither implement {@link SplittableRuptureModule} nor depend on the number of
     * ruptures, and can therefore be carried over unchanged.
     */
    public static final List<Class<? extends OpenSHA_Module>> RUPTURE_COUNT_AGNOSTIC_MODULES =
            List.of(
                    BuildInfoModule.class,
                    TvzDomainSections.class,
                    CustomFaultModel.class,
                    CustomDeformationModel.class);

    protected final double minMag;
    protected final double maxMag;
    protected final RuptureSubSetMappings mappings;

    /**
     * Creates a rupture set that only contains the ruptures of the original whose magnitude lies
     * within [minMag, maxMag].
     *
     * @param original the rupture set to filter
     * @param minMag the inclusive lower magnitude bound
     * @param maxMag the inclusive upper magnitude bound
     * @throws IllegalArgumentException if maxMag is not greater than minMag
     * @throws IllegalStateException if no rupture falls within the magnitude range
     */
    public MagFilteredRupSet(FaultSystemRupSet original, double minMag, double maxMag) {
        Preconditions.checkArgument(
                maxMag > minMag,
                "maxMag must be greater than minMag but was %s <= %s.",
                maxMag,
                minMag);
        this.minMag = minMag;
        this.maxMag = maxMag;

        BiMap<Integer, Integer> rupIdsNewToOld = HashBiMap.create();
        for (int oldRuptureId = 0; oldRuptureId < original.getNumRuptures(); oldRuptureId++) {
            double mag = original.getMagForRup(oldRuptureId);
            if (mag >= minMag && mag <= maxMag) {
                rupIdsNewToOld.put(rupIdsNewToOld.size(), oldRuptureId);
            }
        }
        Preconditions.checkState(
                !rupIdsNewToOld.isEmpty(),
                "No rupture of the rupture set has a magnitude between %s and %s.",
                minMag,
                maxMag);

        int numRuptures = rupIdsNewToOld.size();
        double[] originalLengths = original.getLengthForAllRups();
        double[] mags = new double[numRuptures];
        double[] rakes = new double[numRuptures];
        double[] areas = new double[numRuptures];
        double[] lengths = originalLengths == null ? null : new double[numRuptures];
        List<List<Integer>> sectionsForRups = new ArrayList<>(numRuptures);
        for (int ruptureId = 0; ruptureId < numRuptures; ruptureId++) {
            int oldRuptureId = rupIdsNewToOld.get(ruptureId);
            mags[ruptureId] = original.getMagForRup(oldRuptureId);
            rakes[ruptureId] = original.getAveRakeForRup(oldRuptureId);
            areas[ruptureId] = original.getAreaForRup(oldRuptureId);
            if (lengths != null) {
                lengths[ruptureId] = originalLengths[oldRuptureId];
            }
            sectionsForRups.add(original.getSectionsIndicesForRup(oldRuptureId));
        }

        init(original.getFaultSectionDataList(), sectionsForRups, mags, rakes, areas, lengths);

        BiMap<Integer, Integer> sectIdsNewToOld = HashBiMap.create(original.getNumSections());
        for (int s = 0; s < original.getNumSections(); s++) {
            sectIdsNewToOld.put(s, s);
        }
        mappings = new RuptureSubSetMappings(sectIdsNewToOld, rupIdsNewToOld, original);
        addModule(mappings);

        filterModules(original);
    }

    /**
     * Copies the modules of the original rupture set that survive the filtering. Splittable modules
     * are asked for a subset of themselves, rupture count agnostic modules are shared with the
     * original rupture set.
     *
     * @param original the rupture set that this rupture set was filtered from
     */
    protected void filterModules(FaultSystemRupSet original) {
        for (OpenSHA_Module module :
                original.getModulesAssignableTo(SplittableRuptureModule.class, true)) {
            OpenSHA_Module filtered =
                    ((SplittableRuptureModule<?>) module).getForRuptureSubSet(this, mappings);
            if (filtered != null) {
                addModule(filtered);
            }
        }
        for (Class<? extends OpenSHA_Module> type : RUPTURE_COUNT_AGNOSTIC_MODULES) {
            OpenSHA_Module module = original.getModule(type);
            if (module != null) {
                addModule(module);
            }
        }
    }

    /**
     * Returns the rupture id that the specified rupture had in the original rupture set.
     *
     * @param ruptureId a rupture id of this rupture set
     * @return the rupture id in the original rupture set
     */
    public int getOriginalRuptureId(int ruptureId) {
        return mappings.getOrigRupID(ruptureId);
    }

    /**
     * Returns the rupture id that an original rupture has in this rupture set.
     *
     * @param originalRuptureId a rupture id of the original rupture set
     * @return the rupture id in this rupture set, or null if the rupture was filtered out
     */
    public Integer getRuptureId(int originalRuptureId) {
        return mappings.isRupRetained(originalRuptureId)
                ? mappings.getNewRupID(originalRuptureId)
                : null;
    }

    /**
     * Returns the inclusive lower magnitude bound that this rupture set was filtered with. Note
     * that this is not the same as {@link #getMinMag()}, which returns the smallest magnitude that
     * actually occurs in this rupture set.
     *
     * @return the minMag that was passed to the constructor
     */
    public double getFilterMinMag() {
        return minMag;
    }

    /**
     * Returns the inclusive upper magnitude bound that this rupture set was filtered with. Note
     * that this is not the same as {@link #getMaxMag()}, which returns the largest magnitude that
     * actually occurs in this rupture set.
     *
     * @return the maxMag that was passed to the constructor
     */
    public double getFilterMaxMag() {
        return maxMag;
    }
}

package nz.cri.gns.NZSHM22.opensha.inversion;

import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import nz.cri.gns.NZSHM22.opensha.ruptures.CustomDeformationModel;
import nz.cri.gns.NZSHM22.opensha.ruptures.CustomFaultModel;
import nz.cri.gns.NZSHM22.opensha.ruptures.NZSHM22_RuptureSetBuilderModule;
import org.opensha.commons.util.modules.ModuleContainer;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.commons.util.modules.SubModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.BuildInfoModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.RuptureSubSetMappings;
import org.opensha.sha.earthquake.faultSysSolution.modules.SlipAlongRuptureModel;
import org.opensha.sha.earthquake.faultSysSolution.modules.SplittableRuptureModule;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;

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
 * <p>This is applied by {@link NZSHM22_InversionFaultSystemRuptSet#filterMags(FaultSystemRupSet,
 * Double, Double)} after magnitudes have been recalculated and before the rupture set is wrapped in
 * a {@link NZSHM22_InversionFaultSystemRuptSet}. The magnitude range is set on the inversion runner
 * with {@link NZSHM22_AbstractInversionRunner#setRupSetMagRange(double, double)}.
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
                    CustomDeformationModel.class,
                    NZSHM22_RuptureSetBuilderModule.class,
                    SectionDistanceAzimuthCalculator.class,
                    PlausibilityConfiguration.class,
                    SlipAlongRuptureModel.class);

    protected final double minMag;
    protected final double maxMag;
    protected final RuptureSubSetMappings mappings;

    /** Names of the modules of the original rupture set that could not be carried over. */
    protected final Set<String> droppedModules = new LinkedHashSet<>();

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
     * are asked for a subset of themselves, rupture count agnostic modules are copied or shared
     * with the original rupture set. The names of all other modules are collected in {@link
     * #getDroppedModules()} and logged.
     *
     * @param original the rupture set that this rupture set was filtered from
     */
    protected void filterModules(FaultSystemRupSet original) {
        Set<OpenSHA_Module> carriedOver = new LinkedHashSet<>();
        for (OpenSHA_Module module :
                original.getModulesAssignableTo(SplittableRuptureModule.class, true)) {
            carriedOver.add(module);
            OpenSHA_Module filtered =
                    ((SplittableRuptureModule<?>) module).getForRuptureSubSet(this, mappings);
            if (filtered != null) {
                addModule(filtered);
            }
        }
        for (Class<? extends OpenSHA_Module> type : RUPTURE_COUNT_AGNOSTIC_MODULES) {
            OpenSHA_Module module = original.getModule(type);
            if (module != null) {
                carriedOver.add(module);
                // a splittable subclass has already been filtered and must not be overwritten
                if (getModule(type) == null) {
                    addModule(copyFor(module));
                }
            }
        }

        for (OpenSHA_Module module : original.getModules(true)) {
            if (!carriedOver.contains(module)) {
                droppedModules.add(moduleName(module));
            }
        }
        if (!droppedModules.isEmpty()) {
            System.err.println(
                    "MagFilteredRupSet dropped modules that cannot be filtered by magnitude: "
                            + droppedModules.stream().collect(Collectors.joining(", ")));
        }
    }

    /**
     * Returns the name of the class of a module, qualified with its enclosing classes but without
     * its package, e.g. SlipAlongRuptureModel.Default.
     *
     * @param module the module to name
     * @return the name of the module class
     */
    protected static String moduleName(OpenSHA_Module module) {
        Class<?> type = module.getClass();
        String name = type.getSimpleName();
        while (type.getEnclosingClass() != null) {
            type = type.getEnclosingClass();
            name = type.getSimpleName() + "." + name;
        }
        return name;
    }

    /**
     * Returns a version of a rupture count agnostic module that belongs to this rupture set. {@link
     * SubModule} instances are copied so that they do not lose their original parent, all other
     * modules are shared with the original rupture set.
     *
     * @param module a module of the original rupture set
     * @return a module to attach to this rupture set
     */
    @SuppressWarnings("unchecked")
    protected OpenSHA_Module copyFor(OpenSHA_Module module) {
        if (module instanceof SubModule) {
            return ((SubModule<ModuleContainer<OpenSHA_Module>>) module).copy(this);
        }
        return module;
    }

    /**
     * Returns the simple class names of the modules of the original rupture set that could neither
     * be filtered nor carried over, and are therefore missing from this rupture set.
     *
     * @return the names of the dropped modules
     */
    public Set<String> getDroppedModules() {
        return droppedModules;
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

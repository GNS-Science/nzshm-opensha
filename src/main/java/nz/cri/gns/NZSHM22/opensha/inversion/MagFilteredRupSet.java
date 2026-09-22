package nz.cri.gns.NZSHM22.opensha.inversion;

import com.google.common.base.Preconditions;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import nz.cri.gns.NZSHM22.opensha.analysis.NZSHM22_FaultSystemRupSetCalc;
import nz.cri.gns.NZSHM22.opensha.ruptures.CustomDeformationModel;
import nz.cri.gns.NZSHM22.opensha.ruptures.CustomFaultModel;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.BuildInfoModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModSectMinMags;
import org.opensha.sha.earthquake.faultSysSolution.modules.SplittableRuptureModule;

/**
 * Filters a rupture set by magnitude, dropping the ruptures that fall below the minimum magnitude
 * of any of the sections they use. See {@link
 * NZSHM22_FaultSystemRupSetCalc#computeWhichRupsFallBelowSectionMinMags(FaultSystemRupSet,
 * ModSectMinMags)} for the exact test.
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
                    CustomDeformationModel.class);

    protected MagFilteredRupSet() {}

    /**
     * Creates a rupture set that only contains those ruptures of the original that are not below
     * the minimum magnitude of any of the sections they use. The minimum magnitudes are taken from
     * the original rupture set's {@link ModSectMinMags} module.
     *
     * @param original the rupture set to filter
     * @return the filtered rupture set
     * @throws IllegalStateException if the original has no {@link ModSectMinMags} module, or if all
     *     of its ruptures are below section minimum magnitude
     */
    public static FaultSystemRupSet filter(FaultSystemRupSet original) {
        return filter(original, original.requireModule(ModSectMinMags.class));
    }

    /**
     * Creates a rupture set that only contains those ruptures of the original that are not below
     * the minimum magnitude of any of the sections they use.
     *
     * @param original the rupture set to filter
     * @param minMags the section minimum magnitudes to test the ruptures against
     * @return the filtered rupture set
     * @throws IllegalStateException if all ruptures are below section minimum magnitude
     */
    public static FaultSystemRupSet filter(FaultSystemRupSet original, ModSectMinMags minMags) {
        boolean[] isBelowMinMag =
                NZSHM22_FaultSystemRupSetCalc.computeWhichRupsFallBelowSectionMinMags(
                        original, minMags);

        Set<Integer> retainedRuptureIds = new LinkedHashSet<>();
        for (int ruptureId = 0; ruptureId < original.getNumRuptures(); ruptureId++) {
            if (!isBelowMinMag[ruptureId]) {
                retainedRuptureIds.add(ruptureId);
            }
        }
        Preconditions.checkState(
                !retainedRuptureIds.isEmpty(),
                "All %s ruptures of the rupture set are below section minimum magnitude.",
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

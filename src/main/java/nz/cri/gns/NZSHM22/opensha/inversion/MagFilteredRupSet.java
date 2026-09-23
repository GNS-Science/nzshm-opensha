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
 * of any of the sections they use, and those above a maximum magnitude. See {@link
 * NZSHM22_FaultSystemRupSetCalc#computeWhichRupsFallBelowSectionMinMags(FaultSystemRupSet,
 * ModSectMinMags)} and {@link
 * NZSHM22_FaultSystemRupSetCalc#computeWhichRupsAreAboveMaxMag(FaultSystemRupSet, double)} for the
 * exact tests.
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

    /**
     * A maximum magnitude that is above the highest magnitude bin and therefore does not exclude
     * any rupture.
     */
    public static final double NO_MAX_MAG = NZSHM22_FaultSystemRupSetCalc.MAG_BINS.getMaxX();

    protected MagFilteredRupSet() {}

    /**
     * Creates a rupture set that only contains those ruptures of the original that are neither
     * below the minimum magnitude of any of the sections they use, nor in a magnitude bin above the
     * bin of maxMag. The whole bin of maxMag is retained.
     *
     * @param original the rupture set to filter
     * @param minMags the section minimum magnitudes to test the ruptures against
     * @param maxMag the maximum magnitude. Use {@link #NO_MAX_MAG} for no upper bound.
     * @return the filtered rupture set
     * @throws IllegalStateException if all ruptures are outside the magnitude bounds
     */
    public static FaultSystemRupSet filter(
            FaultSystemRupSet original, ModSectMinMags minMags, double maxMag) {
        Preconditions.checkArgument(
                Double.isFinite(maxMag),
                "maxMag must be finite, use NO_MAX_MAG for no upper bound");
        boolean[] isBelowMinMag =
                NZSHM22_FaultSystemRupSetCalc.computeWhichRupsFallBelowSectionMinMags(
                        original, minMags);
        boolean[] isAboveMaxMag =
                NZSHM22_FaultSystemRupSetCalc.computeWhichRupsAreAboveMaxMag(original, maxMag);

        Set<Integer> retainedRuptureIds = new LinkedHashSet<>();
        for (int ruptureId = 0; ruptureId < original.getNumRuptures(); ruptureId++) {
            if (!isBelowMinMag[ruptureId] && !isAboveMaxMag[ruptureId]) {
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

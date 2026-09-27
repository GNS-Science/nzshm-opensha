package nz.cri.gns.NZSHM22.opensha.inversion;

import com.google.common.base.Preconditions;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import nz.cri.gns.NZSHM22.opensha.analysis.NZSHM22_FaultSystemRupSetCalc;
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
 * of any of the sections they use, and those above the maximum magnitude of any of the sections
 * they use. See {@link
 * NZSHM22_FaultSystemRupSetCalc#computeWhichRupsFallBelowSectionMinMags(FaultSystemRupSet,
 * ModSectMinMags)} and {@link
 * NZSHM22_FaultSystemRupSetCalc#computeWhichRupsAreAboveSectionMaxMags(FaultSystemRupSet,
 * double[])} for the exact tests.
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
        double[] maxMagForSection = new double[original.getNumSections()];
        Arrays.fill(maxMagForSection, maxMag);
        return filter(original, minMags, maxMagForSection);
    }

    /**
     * Creates a rupture set that only contains those ruptures of the original that are neither
     * below the minimum magnitude of any of the sections they use, nor in a magnitude bin above the
     * bin of the maximum magnitude of any of the sections they use. The whole bin of a maximum
     * magnitude is retained. This is the joint inversion case, where each partition has its own
     * magnitude bounds and a rupture has to satisfy the bounds of every partition it belongs to.
     *
     * @param original the rupture set to filter
     * @param minMags the section minimum magnitudes to test the ruptures against
     * @param maxMagForSection the maximum magnitude of each section. Use {@link #NO_MAX_MAG} for no
     *     upper bound.
     * @return the filtered rupture set
     * @throws IllegalStateException if all ruptures are outside the magnitude bounds
     */
    public static FaultSystemRupSet filter(
            FaultSystemRupSet original, ModSectMinMags minMags, double[] maxMagForSection) {
        Preconditions.checkArgument(
                maxMagForSection.length == original.getNumSections(),
                "maxMagForSection must have one entry per fault section");
        for (double maxMag : maxMagForSection) {
            Preconditions.checkArgument(
                    Double.isFinite(maxMag),
                    "maxMag must be finite, use NO_MAX_MAG for no upper bound");
        }
        boolean[] isBelowMinMag =
                NZSHM22_FaultSystemRupSetCalc.computeWhichRupsFallBelowSectionMinMags(
                        original, minMags);
        boolean[] isAboveMaxMag =
                NZSHM22_FaultSystemRupSetCalc.computeWhichRupsAreAboveSectionMaxMags(
                        original, maxMagForSection);

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

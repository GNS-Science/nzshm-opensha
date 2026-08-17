package nz.cri.gns.NZSHM22.opensha.hazard.joint;

import com.google.common.base.Preconditions;
import nz.cri.gns.NZSHM22.opensha.hazard.joint.JointHazardInput.RuptureType;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.RupSetTectonicRegimes;
import org.opensha.sha.earthquake.faultSysSolution.util.MergedSolutionCreator;
import org.opensha.sha.util.TectonicRegionType;

/**
 * Solution plumbing for a crustal + subduction hazard calculation: merging two solutions into one,
 * and giving a rupture set the per-rupture tectonic region types that the ERF needs.
 *
 * <p>The tectonic region types matter because OpenSHA's hazard calculator picks a GMM per source
 * from {@code source.getTectonicRegionType()}, and {@code BaseFaultSystemSolutionERF} takes that
 * from the {@link RupSetTectonicRegimes} module rather than from the fault section data. Without
 * the module every source ends up as {@link TectonicRegionType#ACTIVE_SHALLOW} and a per-region GMM
 * map would send subduction sources to the crustal GMM.
 */
public class JointSolutions {

    private JointSolutions() {}

    /**
     * Merges a crustal and a subduction solution into a single solution whose ruptures carry
     * tectonic region types, ready to be calculated as one ERF.
     *
     * <p>Uses {@link MergedSolutionCreator}, which renumbers sections and concatenates ruptures and
     * rates. Note what that does not do:
     *
     * <ul>
     *   <li>it drops all modules, so logic tree branches, minimum magnitudes and the like do not
     *       survive. Only what the hazard calculation needs (sections, ruptures, magnitudes, rakes,
     *       rates) is carried over, and the tectonic region types this class adds back.
     *   <li>it does not merge grid source providers. That is harmless here because the joint hazard
     *       calculation excludes background seismicity; merging two solutions that both carry a
     *       grid source provider would otherwise double count it.
     *   <li>it overwrites rupture areas with rupture lengths and leaves the lengths at zero.
     *       Rupture surfaces are built from the section data, so hazard is unaffected, but do not
     *       trust the area or length of a merged rupture.
     * </ul>
     */
    public static FaultSystemSolution merge(
            FaultSystemSolution crustal, FaultSystemSolution subduction) {
        FaultSystemSolution merged = MergedSolutionCreator.merge(crustal, subduction);
        applyTectonicRegimes(merged.getRupSet());
        return merged;
    }

    /**
     * The tectonic region type of every rupture, derived from the tectonic region types of the
     * sections it uses.
     *
     * @throws IllegalStateException if a rupture spans both crustal and interface sections. Such a
     *     rupture has no single tectonic region type, so neither GMM would be right for it; use
     *     {@link JointHazardInput.GmmMode#JOINT_RUPTURE} for those.
     */
    public static TectonicRegionType[] tectonicRegimes(FaultSystemRupSet rupSet) {
        TectonicRegionType[] regimes = new TectonicRegionType[rupSet.getNumRuptures()];
        for (int r = 0; r < regimes.length; r++) {
            RuptureType type = JointHazardInput.typeOf(rupSet, r);
            Preconditions.checkState(
                    type != RuptureType.JOINT,
                    "Rupture %s spans crustal and interface sections, so it has no single tectonic"
                            + " region type. Calculate joint ruptures with GmmMode.JOINT_RUPTURE"
                            + " instead.",
                    r);
            regimes[r] =
                    type == RuptureType.CRUSTAL
                            ? TectonicRegionType.ACTIVE_SHALLOW
                            : TectonicRegionType.SUBDUCTION_INTERFACE;
        }
        return regimes;
    }

    /**
     * Adds a {@link RupSetTectonicRegimes} module derived from the section tectonic region types,
     * unless the rupture set already has one. Idempotent.
     */
    public static void applyTectonicRegimes(FaultSystemRupSet rupSet) {
        if (rupSet.getModule(RupSetTectonicRegimes.class) == null) {
            rupSet.addModule(new RupSetTectonicRegimes(rupSet, tectonicRegimes(rupSet)));
        }
    }
}

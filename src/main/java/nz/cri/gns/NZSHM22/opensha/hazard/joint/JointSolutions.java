package nz.cri.gns.NZSHM22.opensha.hazard.joint;

import com.google.common.base.Preconditions;
import java.io.IOException;
import nz.cri.gns.NZSHM22.opensha.hazard.joint.JointHazardInput.RuptureType;
import nz.cri.gns.NZSHM22.opensha.scripts.RupSetPropertyBackfill;
import org.dom4j.DocumentException;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.RupSetTectonicRegimes;
import org.opensha.sha.earthquake.faultSysSolution.util.MergedSolutionCreator;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.util.TectonicRegionType;

/**
 * Solution plumbing for a crustal + subduction hazard calculation: backfilling section properties
 * on older solutions, merging solutions into one, and giving a rupture set the per-rupture tectonic
 * region types that the ERF needs.
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
     * Merges any number of solutions, typically a crustal and one or more subduction solutions,
     * into a single solution whose ruptures carry tectonic region types, ready to be calculated as
     * one ERF.
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
     *
     * <p>Every solution is run through {@link #backfill} first, so solutions saved before fault
     * section properties were introduced can be calculated without being converted by hand.
     *
     * <p>A single solution is returned as it is (unless it needed backfilling), with the tectonic
     * region types applied to it in place. There is nothing to merge it with, and copying it would
     * only lose modules.
     *
     * @throws IllegalArgumentException if no solution is given
     */
    public static FaultSystemSolution merge(FaultSystemSolution... solutions) {
        Preconditions.checkArgument(solutions.length > 0, "need at least one solution to merge");
        FaultSystemSolution[] backfilled = new FaultSystemSolution[solutions.length];
        for (int i = 0; i < solutions.length; i++) {
            backfilled[i] = backfill(solutions[i]);
        }
        FaultSystemSolution merged =
                backfilled.length == 1 ? backfilled[0] : MergedSolutionCreator.merge(backfilled);
        applyTectonicRegimes(merged.getRupSet());
        return merged;
    }

    /**
     * Backfills fault section properties with {@link RupSetPropertyBackfill} if the solution needs
     * it, i.e. if any of its sections lacks a tectonic region type the joint hazard calculation
     * understands. See {@link #needsBackfill}.
     *
     * <p>Backfilling rebuilds the rupture set, so the returned solution wraps a new rupture set;
     * the modules of both the solution and the rupture set are carried over. Solutions that do not
     * need it are returned unchanged.
     *
     * <p>This has to happen before a merge, not after: the backfill reads the fault model from the
     * solution's logic tree branch to look up crustal domains, and {@link MergedSolutionCreator}
     * drops that module. It also corrects section rakes, and rupture rakes are recalculated from
     * them only while the rupture set is rebuilt.
     *
     * @throws IllegalStateException if the fault model data needed for the backfill cannot be read
     */
    public static FaultSystemSolution backfill(FaultSystemSolution solution) {
        if (!needsBackfill(solution.getRupSet())) {
            return solution;
        }
        FaultSystemRupSet rupSet;
        try {
            rupSet = RupSetPropertyBackfill.backfill(solution.getRupSet());
        } catch (IOException | DocumentException e) {
            throw new IllegalStateException(
                    "Could not backfill fault section properties, which this solution needs"
                            + " because some of its sections have no tectonic region type.",
                    e);
        }
        FaultSystemSolution backfilled =
                new FaultSystemSolution(rupSet, solution.getRateForAllRups());
        for (OpenSHA_Module module : solution.getModules(true)) {
            backfilled.addModule(module);
        }
        return backfilled;
    }

    /**
     * Whether a rupture set is missing the section properties the joint hazard calculation needs. A
     * section without a tectonic region type, or with one other than {@link
     * TectonicRegionType#ACTIVE_SHALLOW} or {@link TectonicRegionType#SUBDUCTION_INTERFACE}, means
     * the rupture set predates fault section properties and has to be backfilled.
     */
    public static boolean needsBackfill(FaultSystemRupSet rupSet) {
        for (FaultSection section : rupSet.getFaultSectionDataList()) {
            TectonicRegionType trt = section.getTectonicRegionType();
            if (trt != TectonicRegionType.ACTIVE_SHALLOW
                    && trt != TectonicRegionType.SUBDUCTION_INTERFACE) {
                return true;
            }
        }
        return false;
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

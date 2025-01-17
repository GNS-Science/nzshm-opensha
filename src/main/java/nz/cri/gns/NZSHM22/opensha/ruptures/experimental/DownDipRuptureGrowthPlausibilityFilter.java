package nz.cri.gns.NZSHM22.opensha.ruptures.experimental;

import nz.cri.gns.NZSHM22.opensha.ruptures.DownDipFaultSection;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityResult;

/**
 * Used in MixedRuptureSetBuilder. Ensures that jumps can only go to and from the top row of a
 * subduction cluster.
 *
 * <p>Based on a wrong assumption. Only kept for the duration of the experimental joint rupture
 * phase.
 */
public class DownDipRuptureGrowthPlausibilityFilter implements PlausibilityFilter {
    @Override
    public PlausibilityResult apply(ClusterRupture rupture, boolean verbose) {
        for (Jump jump : rupture.getJumpsIterable()) {

            //            if(jump.fromSection instanceof DownDipFaultSection || jump.toSection
            // instanceof DownDipFaultSection) {
            //                System.out.println("what");
            //            }
            if (jump.fromSection instanceof DownDipFaultSection
                    && ((DownDipFaultSection) jump.fromSection).getRowIndex()
                            > ((DownDipFaultSection) jump.fromCluster.startSect).getRowIndex()) {
                return PlausibilityResult.FAIL_HARD_STOP;
            }
            if (jump.toSection instanceof DownDipFaultSection
                    && ((DownDipFaultSection) jump.toSection).getRowIndex()
                            > ((DownDipFaultSection) jump.toCluster.startSect).getRowIndex()) {
                return PlausibilityResult.FAIL_HARD_STOP;
            }
        }
        return PlausibilityResult.PASS;
    }

    @Override
    public String getShortName() {
        return "DownDipRuptureGrowthPlausibilityFilter";
    }

    @Override
    public String getName() {
        return "DownDipRuptureGrowthPlausibilityFilter";
    }
}

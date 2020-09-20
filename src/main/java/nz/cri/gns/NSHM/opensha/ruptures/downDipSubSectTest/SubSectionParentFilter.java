package nz.cri.gns.NSHM.opensha.ruptures.downDipSubSectTest;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.faultSurface.FaultSection;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

public class SubSectionParentFilter implements PlausibilityFilter {

	private int parentId;

	public SubSectionParentFilter(int parentId) {
		this.parentId = parentId;
	}

	@Override
	public String getShortName() {
		return "ParentIdFilter";
	}

	@Override
	public String getName() {
		return getShortName();
	}
	
	private PlausibilityResult apply(FaultSubsectionCluster cluster, boolean verbose) {
		if (cluster.parentSectionID == parentId)
			return PlausibilityResult.PASS;
		return PlausibilityResult.FAIL_FUTURE_POSSIBLE;
	}

	@Override
	public PlausibilityResult apply(ClusterRupture rupture, boolean verbose) {
		PlausibilityResult result = PlausibilityResult.FAIL_FUTURE_POSSIBLE;
		for (FaultSubsectionCluster cluster : rupture.clusters) {
			result = result.logicalOr(apply(cluster, verbose));
			if (!result.canContinue())
				return result;
		}
		for (ClusterRupture splay : rupture.splays.values()) {
			result = result.logicalOr(apply(splay, verbose));
			if (!result.canContinue())
				return result;
		}
		return result;
	}

	@Override
	public PlausibilityResult testJump(ClusterRupture rupture, Jump newJump, boolean verbose) {
		PlausibilityResult result = apply(rupture, verbose);
		if (result.canContinue())
			result = result.logicalAnd(apply(newJump.toCluster, verbose));
		return result;
	}

}

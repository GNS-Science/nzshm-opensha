package nz.cri.gns.NSHM.opensha.ruptures.downDipSubSectTest;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.faultSurface.FaultSection;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

public class SubSectionParentFilter implements PlausibilityFilter {

	private Predicate<FaultSubsectionCluster> filter;

	public SubSectionParentFilter(Predicate<FaultSubsectionCluster> filter) {
		this.filter = filter;
	}

	public SubSectionParentFilter() {
		// TODO Auto-generated constructor stub
	}

	@Override
	public String getShortName() {
		return "ParentIdFilter";
	}

	@Override
	public String getName() {
		return getShortName();
	}
	
	/**
	 * Returns a Predicate<FaultSubsectionCluster> that returns true if the FaultSubsectionCluster parentSectionID()
	 * is required.
	 * 
	 * @param Set<Integer> filter list of integers to match
	 * @return a Predicate
	 */
	public Predicate<FaultSubsectionCluster> makeParentIdFilter(Set<Integer> filter) {
		if (filter.size() > 0) {
			return cluster -> filter.contains(cluster.parentSectionID);
		} else {
			return cluster -> true;
		}
	}
	
	private PlausibilityResult apply(FaultSubsectionCluster cluster, boolean verbose) {
		if (this.filter.test(cluster))
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

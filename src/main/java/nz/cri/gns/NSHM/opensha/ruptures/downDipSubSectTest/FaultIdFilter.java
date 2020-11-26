package nz.cri.gns.NSHM.opensha.ruptures.downDipSubSectTest;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

import java.util.*;

/**
 * PlausibilityFilter that restricts ruptures to those that contain certain fault Ids.
 */
public class FaultIdFilter implements PlausibilityFilter {

    public enum FilterType {
        ANY, ALL, EXACT
    }

    public interface PlausibilityPredicate {
        PlausibilityResult test(Set<Integer> faultIds);
    }

    private static boolean intersects(Set<Integer> a, Set<Integer> b) {
        Set<Integer> base = a.size() < b.size() ? a : b;
        Set<Integer> other = a.size() < b.size() ? b : a;
        for (Integer i : base) {
            if (other.contains(i)) {
                return true;
            }
        }
        return false;
    }

    private final PlausibilityPredicate predicate;
    private final FilterType filterType; // this is only stored here so that it gets documented in the rupture set zip file

    private FaultIdFilter(PlausibilityPredicate filter, FilterType filterType) {
        this.predicate = filter;
        this.filterType = filterType;
    }

    /**
     * Creates a new FaultIdFilter based on the filterType:
     * ANY: Creates a FaultIdFilter that will only accept ruptures that contain at least one of the faults in faultIds.
     * ALL: Creates a FaultIdFilter that will only accept ruptures that contain all of the faults in faultIds.
     * EXACT: Creates a FaultIdFilter that will only accept ruptures that contain exactly the faults in faultIds.
     *
     * @param filterType the type of the filter
     * @param faultIds   a set of fault ids
     * @return a FaultIdFilter
     */
    public static FaultIdFilter create(FilterType filterType, Set<Integer> faultIds) {
        switch (filterType) {
            case ANY:
                return new FaultIdFilter(ruptureFaults ->
                        intersects(ruptureFaults, faultIds) ? PlausibilityResult.PASS : PlausibilityResult.FAIL_FUTURE_POSSIBLE,
                        FilterType.ANY);
            case ALL:
                return new FaultIdFilter(ruptureFaults ->
                        ruptureFaults.containsAll(faultIds) ? PlausibilityResult.PASS : PlausibilityResult.FAIL_FUTURE_POSSIBLE,
                        FilterType.ALL);
            case EXACT:
                return new FaultIdFilter(ruptureFaults ->
                        ruptureFaults.equals(faultIds) ? PlausibilityResult.PASS :
                                faultIds.containsAll(ruptureFaults) ? PlausibilityResult.FAIL_FUTURE_POSSIBLE :
                                        PlausibilityResult.FAIL_HARD_STOP,
                        FilterType.EXACT);
        }
        throw new IllegalStateException("We should not be able to get here");
    }

    @Override
    public String getShortName() {
        return "FaultIdFilter";
    }

    @Override
    public String getName() {
        return getShortName();
    }


    private Set<Integer> clusterRuptureIds(ClusterRupture cr) {
        Set<Integer> ids = new HashSet<>();
        for (FaultSubsectionCluster cluster : cr.clusters) {
            ids.add(cluster.parentSectionID);
        }
        for (ClusterRupture splay : cr.splays.values()) {
            ids.addAll(clusterRuptureIds(splay));
        }
        return ids;
    }

    @Override
    public PlausibilityResult apply(ClusterRupture rupture, boolean verbose) {
        return predicate.test(clusterRuptureIds(rupture));
    }

}

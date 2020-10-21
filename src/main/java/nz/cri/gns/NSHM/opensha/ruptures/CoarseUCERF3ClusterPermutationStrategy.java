package nz.cri.gns.NSHM.opensha.ruptures;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.CachedClusterPermutationStrategy;
import org.opensha.sha.faultSurface.FaultSection;

import java.util.ArrayList;
import java.util.List;

/**
 * ClusterPermutationStrategy that works like UCERF3ClusterPermuationStrategy but takes a function that can
 * vary the coarseness of the permutations.
 * <p>
 * Based on org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.UCERF3ClusterPermuationStrategy
 * <p>
 * Same assumption as UCERF3ClusterPermuationStrategy:
 * "assumes that subsections are listed in order and connections only exist between neighbors in that list"
 */

public class CoarseUCERF3ClusterPermutationStrategy extends CachedClusterPermutationStrategy {

    public interface CoarsenessFunction {
        /**
         * Given the sections that are in a permutation, how many sections need to be added to create the next permutation?
         *
         * @param sections The sections in a permutation
         * @return the number of sections required to form the next permutation
         */
        int getCoarseness(List<FaultSection> sections);
    }

    protected final CoarsenessFunction coarseness;

    /**
     * Creates a CoarseUCERF3ClusterPermutationStrategy that will use cFn to determine how many sections to add to the
     * next permutation.
     * @param cFn a CoarsenessFunction that must return values no less than 1
     */
    public CoarseUCERF3ClusterPermutationStrategy(CoarsenessFunction cFn) {
        super();
        this.coarseness = cFn;
    }

    /**
     * Creates a CoarseUCERF3ClusterPermutationStrategy that will add epsilon * previous_permutation_size sections
     * to the next permutation.
     * @param epsilon
     */
    public CoarseUCERF3ClusterPermutationStrategy(double epsilon) {
        this(sections -> Math.max(1, (int) Math.round(epsilon * sections.size())));
    }

    /**
     * Creates a CoarseUCERF3ClusterPermutationStrategy that will always add 1 section to create the next permutation.
     * Equivalent to UCERF3ClusterPermuationStrategy.
     */
    public CoarseUCERF3ClusterPermutationStrategy() {
        this(sections -> 1);
    }

    /**
     * Returns true iff index is within the bounds of the list.
     * @param list a list
     * @param index an index into the list
     * @return true iff the index is a valid index into the list.
     */
    protected static boolean indexInBounds(List list, int index) {
        return index >= 0 && index < list.size();
    }

    private void buildPermutations(FaultSubsectionCluster fullCluster, int firstId, int direction, List<FaultSubsectionCluster> permutations) {
        List<FaultSection> clusterSects = fullCluster.subSects;
        List<FaultSection> newSects = new ArrayList<>();
        newSects.add(clusterSects.get(firstId));

        for (int i = firstId + direction; indexInBounds(clusterSects, i); ) {
            // ask the coarseness function how many sections we should take
            int count = coarseness.getCoarseness(newSects);
            Preconditions.checkState(count >= 1, "coarseness must be at least 1");
            // take a number of sections to be added to the next permutation
            for (int j = 0; j < count && indexInBounds(clusterSects, i); j++) {
                newSects.add(clusterSects.get(i));
                i += direction;
            }
            // build the permutation
            permutations.add(buildCopyJumps(fullCluster, newSects));
        }
    }

    @Override
    public List<FaultSubsectionCluster> calcPermutations(FaultSubsectionCluster fullCluster,
                                                         FaultSection firstSection) {
        int myInd = fullCluster.subSects.indexOf(firstSection);
        Preconditions.checkState(myInd >= 0, "first section not found in cluster");

        List<FaultSubsectionCluster> permutations = new ArrayList<>();
        permutations.add(buildCopyJumps(fullCluster, Lists.newArrayList(firstSection)));

        buildPermutations(fullCluster, myInd, -1, permutations);
        buildPermutations(fullCluster, myInd, 1, permutations);

        return permutations;
    }

    // copied from UCERF3ClusterPermuationStrategy
    // FIXME should not be copied
    private static FaultSubsectionCluster buildCopyJumps(FaultSubsectionCluster fullCluster,
                                                         List<FaultSection> subsetSects) {
        FaultSubsectionCluster permutation = new FaultSubsectionCluster(new ArrayList<>(subsetSects));
        for (FaultSection sect : subsetSects)
            for (Jump jump : fullCluster.getConnections(sect))
                permutation.addConnection(new Jump(sect, permutation,
                        jump.toSection, jump.toCluster, jump.distance));
        return permutation;
    }

}


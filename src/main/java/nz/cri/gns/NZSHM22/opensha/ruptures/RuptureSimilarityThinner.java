package nz.cri.gns.NZSHM22.opensha.ruptures;

import java.util.*;
import java.util.stream.Collectors;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;

/**
 * General-purpose rupture thinning using greedy Set Cover. Groups ruptures into size bins and,
 * within each bin, finds a minimum-ish subset such that every section is still covered by at least
 * one kept rupture.
 */
public class RuptureSimilarityThinner {

    private final FaultSystemRupSet rupSet;
    private final int sizeTolerance;

    public RuptureSimilarityThinner(FaultSystemRupSet rupSet) {
        this(rupSet, 1);
    }

    public RuptureSimilarityThinner(FaultSystemRupSet rupSet, int sizeTolerance) {
        this.rupSet = rupSet;
        this.sizeTolerance = sizeTolerance;
    }

    /** Thin all ruptures in the rupture set. */
    public List<Integer> thinAll() {
        List<Integer> all = new ArrayList<>();
        for (int i = 0; i < rupSet.getNumRuptures(); i++) {
            all.add(i);
        }
        return thin(all);
    }

    /** Thin the given subset of rupture indices. Returns kept indices in input order. */
    public List<Integer> thin(List<Integer> ruptureIndices) {
        if (ruptureIndices.isEmpty()) {
            return new ArrayList<>();
        }

        // Group by size bin
        int binWidth = 2 * sizeTolerance + 1;
        Map<Integer, List<Integer>> bins = new TreeMap<>();
        for (int idx : ruptureIndices) {
            int sectionCount = rupSet.getSectionsIndicesForRup(idx).size();
            int binKey = sectionCount / binWidth;
            bins.computeIfAbsent(binKey, k -> new ArrayList<>()).add(idx);
        }

        Set<Integer> kept = new HashSet<>();
        for (List<Integer> binIndices : bins.values()) {
            kept.addAll(greedySetCover(binIndices));
        }

        // Preserve original ordering
        return ruptureIndices.stream().filter(kept::contains).collect(Collectors.toList());
    }

    /**
     * Greedy set cover: repeatedly pick the rupture covering the most uncovered sections, until all
     * sections are covered. Ties broken by smallest first section index.
     */
    private List<Integer> greedySetCover(List<Integer> ruptureIndices) {
        // Build universe of all sections in this bin
        Set<Integer> universe = new HashSet<>();
        Map<Integer, List<Integer>> sectionsByRup = new HashMap<>();
        for (int idx : ruptureIndices) {
            List<Integer> sections = rupSet.getSectionsIndicesForRup(idx);
            sectionsByRup.put(idx, sections);
            universe.addAll(sections);
        }

        Set<Integer> uncovered = new HashSet<>(universe);
        Set<Integer> remaining = new HashSet<>(ruptureIndices);
        List<Integer> kept = new ArrayList<>();

        while (!uncovered.isEmpty() && !remaining.isEmpty()) {
            int bestIdx = -1;
            int bestCount = -1;
            int bestFirstSection = Integer.MAX_VALUE;

            for (int idx : remaining) {
                List<Integer> sections = sectionsByRup.get(idx);
                int count = 0;
                int firstSection = Integer.MAX_VALUE;
                for (int s : sections) {
                    if (uncovered.contains(s)) {
                        count++;
                    }
                    if (s < firstSection) {
                        firstSection = s;
                    }
                }
                if (count > bestCount || (count == bestCount && firstSection < bestFirstSection)) {
                    bestCount = count;
                    bestIdx = idx;
                    bestFirstSection = firstSection;
                }
            }

            if (bestCount == 0) {
                break;
            }

            kept.add(bestIdx);
            remaining.remove(bestIdx);
            uncovered.removeAll(sectionsByRup.get(bestIdx));
        }

        return kept;
    }
}

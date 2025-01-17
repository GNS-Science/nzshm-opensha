package nz.cri.gns.NZSHM22.opensha.ruptures.experimental;

import com.google.common.base.Preconditions;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.faultSurface.FaultSection;

public class JointSubductionSpreadPlausibilityFilter {

    final Extent subductionExtent;
    static final Pattern ROW_COL_PATTERN = Pattern.compile("col: (\\d+), row: (\\d+)");

    public JointSubductionSpreadPlausibilityFilter(List<? extends FaultSection> subsects) {
        subductionExtent = new Extent(subsects);
    }

    /**
     * Not the ideal way to filter
     *
     * @param rupture
     * @param verbose
     * @return
     */
    public boolean filterByPosition(ClusterRupture rupture, boolean verbose) {
        Extent extent = new RuptureExtent(rupture);

        return (extent.cols.min % extent.cols.getLength() == 0)
                && (extent.rows.min % extent.rows.getLength() == 0);
    }

    public List<ClusterRupture> filterBySize(List<ClusterRupture> ruptures) {
        List<ClusterRupture> result = new ArrayList<>();
        List<RuptureExtent> sorted =
                ruptures.stream()
                        .map(RuptureExtent::new)
                        .sorted(RuptureExtent.comp)
                        .collect(Collectors.toList());
        int nextSize = Integer.MIN_VALUE;
        int currentSize = Integer.MIN_VALUE;
        for (RuptureExtent extent : sorted) {
            if (extent.sections.size() == currentSize) {
                result.add(extent.rupture);
            } else if (extent.sections.size() >= nextSize) {
                result.add(extent.rupture);
                currentSize = extent.sections.size();
                nextSize = currentSize + (int) Math.max(1, currentSize * 0.1);
            }
        }
        return result;
    }

    public static class MinMax {
        public int min = Integer.MAX_VALUE;
        public int max = Integer.MIN_VALUE;

        public void add(int value) {
            min = Math.min(min, value);
            max = Math.max(max, value);
        }

        public int getLength() {
            return max - min + 1;
        }
    }

    public static class Extent {
        public final MinMax cols = new MinMax();
        public final MinMax rows = new MinMax();
        public final List<? extends FaultSection> sections;

        public Extent(List<? extends FaultSection> sections) {
            this.sections = sections;
            for (FaultSection section : sections) {
                Matcher matcher = ROW_COL_PATTERN.matcher(section.getSectionName());
                Preconditions.checkState(matcher.find());
                int col = Integer.parseInt(matcher.group(1));
                int row = Integer.parseInt(matcher.group(2));
                cols.add(col);
                rows.add(row);
            }
        }

        public static Comparator<Extent> comp =
                Comparator.comparingInt((Extent e) -> e.sections.size())
                        .thenComparingInt(e -> e.cols.min)
                        .thenComparingInt(e -> e.rows.min);
    }

    public static class RuptureExtent extends Extent {
        public final ClusterRupture rupture;

        public RuptureExtent(ClusterRupture rupture) {
            super(rupture.buildOrderedSectionList());
            this.rupture = rupture;
        }
    }
}

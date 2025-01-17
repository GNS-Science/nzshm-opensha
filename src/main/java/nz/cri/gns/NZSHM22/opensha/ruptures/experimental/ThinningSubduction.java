package nz.cri.gns.NZSHM22.opensha.ruptures.experimental;

import com.google.common.base.Preconditions;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.faultSurface.FaultSection;

public class ThinningSubduction {

    final FaultSystemRupSet rupSet;
    List<RuptureExtent> extents;
    final Map<ClusterRupture, Integer> ruptureIndices;
    final Extent extent;

    public ThinningSubduction(FaultSystemRupSet rupSet, Predicate<String> sectionSelector) {
        this.rupSet = rupSet;
        ClusterRuptures cRups = rupSet.getModule(ClusterRuptures.class);
        if (cRups == null) {
            // assume single stranded for our purposes here
            cRups = ClusterRuptures.singleStranged(rupSet);
        }
        List<ClusterRupture> ruptures = new ArrayList<>(cRups.getAll());
        ruptureIndices = new HashMap<>();
        for (int r = 0; r < ruptures.size(); r++) {
            ruptureIndices.put(ruptures.get(r), r);
        }
        extents =
                ruptures.stream()
                        .filter(r -> sectionSelector.test(r.clusters[0].startSect.getSectionName()))
                        .map(RuptureExtent::new)
                        .collect(Collectors.toList());
        extent =
                new Extent(
                        rupSet.getFaultSectionDataList().stream()
                                .filter(s -> sectionSelector.test(s.getSectionName()))
                                .collect(Collectors.toList()));
    }

    public List<Integer> getIds() {
        return extents.stream().map(RuptureExtent::getIndex).collect(Collectors.toList());
    }

    public List<ClusterRupture> getRuptures() {
        return extents.stream().map(e -> e.rupture).collect(Collectors.toList());
    }

    /** Not the ideal way to filter */
    protected boolean filterByPosition(RuptureExtent extent) {
        return (extent.cols.min % extent.cols.getLength() == 0)
                && (extent.rows.min % extent.rows.getLength() == 0);
    }

    public void filterByPosition() {
        extents.removeIf(e -> !filterByPosition(e));
    }

    public void filterBySize(double increase) {
        List<RuptureExtent> result = new ArrayList<>();
        List<RuptureExtent> sorted =
                extents.stream().sorted(RuptureExtent.comp).collect(Collectors.toList());
        int nextSize = Integer.MIN_VALUE;
        int currentSize = Integer.MIN_VALUE;
        for (RuptureExtent extent : sorted) {
            if (extent.sections.size() == currentSize) {
                result.add(extent);
            } else if (extent.sections.size() >= nextSize) {
                result.add(extent);
                currentSize = extent.sections.size();
                nextSize = currentSize + (int) Math.max(1, currentSize * increase);
            }
        }
        extents = result;
    }

    public static class Extent {

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

        static final Pattern ROW_COL_PATTERN = Pattern.compile("col: (\\d+), row: (\\d+)");
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

    public class RuptureExtent extends Extent {
        public final ClusterRupture rupture;

        public RuptureExtent(ClusterRupture rupture) {
            super(rupture.buildOrderedSectionList());
            this.rupture = rupture;
        }

        int getIndex() {
            return ruptureIndices.get(rupture);
        }
    }

    public static void main(String[] args) throws IOException {
        FaultSystemRupSet combined =
                FaultSystemRupSet.load(
                        new File("C:\\Users\\user\\GNS\\rupture sets\\nzshm_complete_merged.zip"));

        List<Integer> crustalIds = ThinningCrustal.filterCrustal(combined);

        ThinningSubduction hikurangiThinning =
                new ThinningSubduction(combined, s -> s.startsWith("Hikurangi"));
        hikurangiThinning.filterByPosition();
        hikurangiThinning.filterBySize(0.1);

        System.out.println("hikurangi after thinning " + hikurangiThinning.getRuptures().size());

        ThinningSubduction puysegurThinning =
                new ThinningSubduction(combined, s -> s.startsWith("Puysegur"));
        puysegurThinning.filterByPosition();
        puysegurThinning.filterBySize(0.1);

        System.out.println("puysegur after thinning " + puysegurThinning.getRuptures().size());

        BufferedWriter writer =
                new BufferedWriter(new FileWriter("/tmp/filteredSubductionRuptures.txt"));
        for (Integer r : crustalIds) {
            writer.write("" + r);
            writer.newLine();
        }
        for (Integer r : hikurangiThinning.getIds()) {
            writer.write("" + r);
            writer.newLine();
        }
        for (Integer r : puysegurThinning.getIds()) {
            writer.write("" + r);
            writer.newLine();
        }
        writer.close();
    }
}

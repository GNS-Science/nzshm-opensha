package nz.cri.gns.NZSHM22.opensha.inversion.joint.reporting;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.*;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionPredicate;
import nz.cri.gns.NZSHM22.opensha.ruptures.FaultSectionProperties;
import org.jfree.data.Range;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.gui.plot.*;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractRupSetPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.faultSurface.FaultSection;

/** Renders a markdown table summarizing section and rupture counts per partition. */
public class PartitionSummaryTable extends AbstractRupSetPlot {

    /** Per-partition statistics computed from a rupture set. */
    protected static class PartitionStats {
        /** Section counts keyed by partition. */
        public final Map<PartitionPredicate, Integer> sectionCounts;

        /** Ruptures belonging to exactly one partition. */
        public final Map<PartitionPredicate, Integer> exclusiveCounts;

        /** Ruptures shared across multiple partitions, counted per partition. */
        public final Map<PartitionPredicate, Integer> sharedCounts;

        /** Total number of ruptures spanning more than one partition. */
        public final int multiPartitionTotal;

        /** All partitions that appear in any of the count maps. */
        public final Set<PartitionPredicate> allPartitions;

        protected PartitionStats(
                Map<PartitionPredicate, Integer> sectionCounts,
                Map<PartitionPredicate, Integer> exclusiveCounts,
                Map<PartitionPredicate, Integer> sharedCounts,
                int multiPartitionTotal) {
            this.sectionCounts = sectionCounts;
            this.exclusiveCounts = exclusiveCounts;
            this.sharedCounts = sharedCounts;
            this.multiPartitionTotal = multiPartitionTotal;
            this.allPartitions = EnumSet.noneOf(PartitionPredicate.class);
            this.allPartitions.addAll(sectionCounts.keySet());
            this.allPartitions.addAll(exclusiveCounts.keySet());
            this.allPartitions.addAll(sharedCounts.keySet());
        }
    }

    /**
     * Computes per-partition statistics from the given rupture set.
     *
     * @param rupSet the rupture set to analyse
     * @return partition statistics
     */
    protected static PartitionStats computeStats(FaultSystemRupSet rupSet) {
        Map<PartitionPredicate, Integer> sectionCounts = new EnumMap<>(PartitionPredicate.class);
        for (FaultSection section : rupSet.getFaultSectionDataList()) {
            PartitionPredicate partition = FaultSectionProperties.getPartition(section);
            if (partition != null) {
                sectionCounts.merge(partition, 1, Integer::sum);
            }
        }

        Map<PartitionPredicate, Integer> exclusiveCounts = new EnumMap<>(PartitionPredicate.class);
        Map<PartitionPredicate, Integer> sharedCounts = new EnumMap<>(PartitionPredicate.class);
        int multiPartitionTotal = 0;

        for (int r = 0; r < rupSet.getNumRuptures(); r++) {
            Set<PartitionPredicate> partitions = EnumSet.noneOf(PartitionPredicate.class);
            for (int secIdx : rupSet.getSectionsIndicesForRup(r)) {
                PartitionPredicate p =
                        FaultSectionProperties.getPartition(rupSet.getFaultSectionData(secIdx));
                if (p != null) {
                    partitions.add(p);
                }
            }

            if (partitions.size() == 1) {
                PartitionPredicate sole = partitions.iterator().next();
                exclusiveCounts.merge(sole, 1, Integer::sum);
            } else if (partitions.size() > 1) {
                multiPartitionTotal++;
                for (PartitionPredicate p : partitions) {
                    sharedCounts.merge(p, 1, Integer::sum);
                }
            }
        }

        return new PartitionStats(
                sectionCounts, exclusiveCounts, sharedCounts, multiPartitionTotal);
    }

    /** Number of bins for the crustal area fraction histogram. */
    protected static final int NUM_BINS = 20;

    /** Bin width for the crustal area fraction histogram. */
    protected static final double BIN_WIDTH = 1.0 / NUM_BINS;

    /**
     * Computes the crustal area fraction for each joint (multi-partition) rupture.
     *
     * @param rupSet the rupture set to analyse
     * @return list of crustal area fractions (each in [0, 1]) for joint ruptures only
     */
    protected static List<Double> computeCrustalFractions(FaultSystemRupSet rupSet) {
        List<Double> fractions = new ArrayList<>();
        for (int r = 0; r < rupSet.getNumRuptures(); r++) {
            List<Integer> secIndices = rupSet.getSectionsIndicesForRup(r);
            Set<PartitionPredicate> partitions = EnumSet.noneOf(PartitionPredicate.class);
            for (int secIdx : secIndices) {
                PartitionPredicate p =
                        FaultSectionProperties.getPartition(rupSet.getFaultSectionData(secIdx));
                if (p != null) {
                    partitions.add(p);
                }
            }
            if (partitions.size() <= 1) {
                continue;
            }
            double crustalArea = 0;
            double totalArea = 0;
            for (int secIdx : secIndices) {
                double area = rupSet.getAreaForSection(secIdx);
                totalArea += area;
                FaultSection section = rupSet.getFaultSectionData(secIdx);
                if (FaultSectionProperties.isCrustal(section)) {
                    crustalArea += area;
                }
            }
            if (totalArea > 0) {
                fractions.add(crustalArea / totalArea);
            }
        }
        return fractions;
    }

    /**
     * Bins crustal area fractions into a histogram function.
     *
     * @param fractions list of crustal area fractions
     * @return histogram function with {@link #NUM_BINS} bins spanning [0, 1]
     */
    protected static EvenlyDiscretizedFunc binFractions(List<Double> fractions) {
        EvenlyDiscretizedFunc func =
                new EvenlyDiscretizedFunc(BIN_WIDTH / 2.0, NUM_BINS, BIN_WIDTH);
        for (double f : fractions) {
            int bin = func.getClosestXIndex(f);
            func.set(bin, func.getY(bin) + 1);
        }
        return func;
    }

    /**
     * Creates the histogram plot and writes it to disk.
     *
     * @param primaryFunc histogram function for the primary rupture set
     * @param compFunc histogram function for the comparison rupture set, or null
     * @param primaryName display name for the primary series
     * @param compName display name for the comparison series, or null
     * @param resourcesDir directory to write plot files
     * @return the file prefix used for the plot
     * @throws IOException if writing fails
     */
    protected static String writeHistogram(
            EvenlyDiscretizedFunc primaryFunc,
            EvenlyDiscretizedFunc compFunc,
            String primaryName,
            String compName,
            File resourcesDir)
            throws IOException {
        List<DiscretizedFunc> funcs = new ArrayList<>();
        List<PlotCurveCharacterstics> chars = new ArrayList<>();

        if (compFunc != null) {
            primaryFunc.setName(primaryName);
            compFunc.setName(compName);
            funcs.add(primaryFunc);
            chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, Color.BLUE));
            funcs.add(compFunc);
            chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, Color.RED.darker()));
        } else {
            funcs.add(primaryFunc);
            chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, Color.BLUE));
        }

        PlotSpec spec =
                new PlotSpec(
                        funcs,
                        chars,
                        "Joint Rupture Crustal Area Fraction",
                        "Crustal Area Fraction",
                        "Count");
        if (compFunc != null) {
            spec.setLegendInset(true);
        }

        double maxY = 0;
        for (DiscretizedFunc f : funcs) {
            for (int i = 0; i < f.size(); i++) {
                maxY = Math.max(maxY, f.getY(i));
            }
        }

        Range xRange = new Range(0, 1);
        Range yRange = new Range(0, maxY * 1.05);

        HeadlessGraphPanel gp = PlotUtils.initHeadless(PlotPreferences.getDefaultAppPrefs());
        gp.setTickLabelFontSize(20);
        gp.drawGraphPanel(spec, false, false, xRange, yRange);

        String prefix = "joint_area_ratio";
        PlotUtils.writePlots(resourcesDir, prefix, gp, 1000, 850, true, true, false);
        return prefix;
    }

    @Override
    public String getName() {
        return "Joint Rupture Summary";
    }

    @Override
    public List<String> plot(
            FaultSystemRupSet rupSet,
            FaultSystemSolution sol,
            ReportMetadata meta,
            File resourcesDir,
            String relPathToResources,
            String topLink)
            throws IOException {

        PartitionStats primary = computeStats(rupSet);
        boolean hasComparison = meta != null && meta.hasComparison();
        PartitionStats comp = hasComparison ? computeStats(meta.comparison.rupSet) : null;

        Set<PartitionPredicate> allPartitions = EnumSet.noneOf(PartitionPredicate.class);
        allPartitions.addAll(primary.allPartitions);
        if (comp != null) {
            allPartitions.addAll(comp.allPartitions);
        }

        List<String> lines = new ArrayList<>();

        if (comp != null) {
            String pName = meta.primary.name;
            String cName = meta.comparison.name;
            lines.add(
                    String.format(
                            "| Partition "
                                    + " | Exclusive (%s) | Exclusive (%s)"
                                    + " | Joint (%s) | Joint (%s) |",
                            pName, cName, pName, cName));
            lines.add("|---|---|---|---|---|---|---|");
            for (PartitionPredicate p : allPartitions) {
                lines.add(
                        String.format(
                                "| %s | %,d | %,d | %,d | %,d |",
                                p.name(),
                                primary.exclusiveCounts.getOrDefault(p, 0),
                                comp.exclusiveCounts.getOrDefault(p, 0),
                                primary.sharedCounts.getOrDefault(p, 0),
                                comp.sharedCounts.getOrDefault(p, 0)));
            }
            int pExclTotal = 0, cExclTotal = 0;
            for (PartitionPredicate p : allPartitions) {
                pExclTotal += primary.exclusiveCounts.getOrDefault(p, 0);
                cExclTotal += comp.exclusiveCounts.getOrDefault(p, 0);
            }
            lines.add(
                    String.format(
                            "| **Total** | **%,d** | **%,d** | **%,d** | **%,d** |",
                            pExclTotal,
                            cExclTotal,
                            primary.multiPartitionTotal,
                            comp.multiPartitionTotal));
        } else {
            lines.add("| Partition | Exclusive Ruptures | Joint Ruptures |");
            lines.add("|-----------|--------------------|----------------|");
            for (PartitionPredicate p : allPartitions) {
                lines.add(
                        String.format(
                                "| %s | %,d | %,d |",
                                p.name(),
                                primary.exclusiveCounts.getOrDefault(p, 0),
                                primary.sharedCounts.getOrDefault(p, 0)));
            }
            int exclTotal = 0;
            for (PartitionPredicate p : allPartitions) {
                exclTotal += primary.exclusiveCounts.getOrDefault(p, 0);
            }
            lines.add(
                    String.format(
                            "| **Total** | **%,d** | **%,d** |",
                            exclTotal, primary.multiPartitionTotal));
        }

        // Histogram of crustal area fraction for joint ruptures
        List<Double> primaryFractions = computeCrustalFractions(rupSet);
        if (!primaryFractions.isEmpty()) {
            EvenlyDiscretizedFunc primaryFunc = binFractions(primaryFractions);
            EvenlyDiscretizedFunc compFunc = null;
            String primaryName = null;
            String compName = null;
            if (hasComparison) {
                List<Double> compFractions = computeCrustalFractions(meta.comparison.rupSet);
                if (!compFractions.isEmpty()) {
                    compFunc = binFractions(compFractions);
                    primaryName = meta.primary.name;
                    compName = meta.comparison.name;
                }
            }
            String prefix =
                    writeHistogram(primaryFunc, compFunc, primaryName, compName, resourcesDir);
            lines.add(
                    "!["
                            + "Joint Rupture Crustal Area Fraction"
                            + "]("
                            + relPathToResources
                            + "/"
                            + prefix
                            + ".png)");
            lines.add("");
        }

        return lines;
    }

    @Override
    public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
        return null;
    }
}

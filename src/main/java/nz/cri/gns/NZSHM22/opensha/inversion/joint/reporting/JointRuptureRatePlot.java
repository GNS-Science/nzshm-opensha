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
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractRupSetPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.SolMFDPlot;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

/**
 * Report plot showing MFDs and rate statistics for joint ruptures (spanning both crustal and
 * subduction partitions) alongside exclusive rupture types.
 */
public class JointRuptureRatePlot extends AbstractRupSetPlot {

    /** The five rupture categories: three exclusive, two joint. */
    protected static final List<String> CATEGORIES =
            List.of("CRUSTAL", "HIKURANGI", "PUYSEGUR", "CRUSTAL+HIKURANGI", "CRUSTAL+PUYSEGUR");

    /** Colors for each category, in the same order as {@link #CATEGORIES}. */
    protected static final List<Color> COLORS =
            List.of(Color.BLUE, Color.RED, Color.GREEN.darker(), Color.MAGENTA, Color.ORANGE);

    /**
     * Classifies a rupture based on the partitions of its sections.
     *
     * @param partitions the set of partitions found in the rupture's sections
     * @return the category string, or null if the combination is unrecognised
     */
    protected static String classify(Set<PartitionPredicate> partitions) {
        if (partitions.size() == 1) {
            return partitions.iterator().next().name();
        }
        if (partitions.equals(
                EnumSet.of(PartitionPredicate.CRUSTAL, PartitionPredicate.HIKURANGI))) {
            return "CRUSTAL+HIKURANGI";
        }
        if (partitions.equals(
                EnumSet.of(PartitionPredicate.CRUSTAL, PartitionPredicate.PUYSEGUR))) {
            return "CRUSTAL+PUYSEGUR";
        }
        return null;
    }

    /**
     * Collects the partitions present in a rupture's sections.
     *
     * @param rupSet the rupture set
     * @param rupIndex the rupture index
     * @return set of partitions found
     */
    protected static Set<PartitionPredicate> partitionsForRup(
            FaultSystemRupSet rupSet, int rupIndex) {
        Set<PartitionPredicate> partitions = EnumSet.noneOf(PartitionPredicate.class);
        for (int secIdx : rupSet.getSectionsIndicesForRup(rupIndex)) {
            PartitionPredicate p =
                    FaultSectionProperties.getPartition(rupSet.getFaultSectionData(secIdx));
            if (p != null) {
                partitions.add(p);
            }
        }
        return partitions;
    }

    /** Per-category statistics. */
    protected static class CategoryStats {
        public int totalCount;
        public int withRateCount;
        public double rateSum;
    }

    /**
     * Computes a log-scale y-range from the given functions. Returns null if all values are zero.
     *
     * @param funcs the functions to scan
     * @return y-range with padding, or null if no positive values
     */
    protected static Range computeLogYRange(List<DiscretizedFunc> funcs) {
        double minNonZeroY = Double.MAX_VALUE;
        double maxY = 0;
        for (DiscretizedFunc func : funcs) {
            for (int b = 0; b < func.size(); b++) {
                double y = func.getY(b);
                if (y > 0) {
                    if (y < minNonZeroY) minNonZeroY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }
        if (minNonZeroY == Double.MAX_VALUE) return null;
        return new Range(minNonZeroY / 10.0, maxY * 2.0);
    }

    /**
     * Writes an MFD plot (incremental or cumulative) to disk.
     *
     * @param funcs the data series
     * @param chars the line characteristics for each series
     * @param title plot title
     * @param yLabel y-axis label
     * @param xRange x-axis range
     * @param resourcesDir output directory
     * @param prefix file name prefix
     * @throws IOException if writing fails
     */
    protected static void writeMFDPlot(
            List<DiscretizedFunc> funcs,
            List<PlotCurveCharacterstics> chars,
            String title,
            String yLabel,
            Range xRange,
            File resourcesDir,
            String prefix)
            throws IOException {
        PlotSpec spec = new PlotSpec(funcs, chars, title, "Magnitude", yLabel);
        spec.setLegendInset(true);

        Range yRange = computeLogYRange(funcs);

        HeadlessGraphPanel gp = PlotUtils.initHeadless(PlotPreferences.getDefaultAppPrefs());
        gp.setTickLabelFontSize(20);
        gp.drawGraphPanel(spec, false, true, xRange, yRange);

        PlotUtils.writePlots(resourcesDir, prefix, gp, 1000, 850, true, true, false);
    }

    @Override
    public String getName() {
        return "Joint Rupture Rates";
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

        if (sol == null) return null;

        IncrementalMagFreqDist templateMFD =
                SolMFDPlot.initDefaultMFD(rupSet.getMinMag(), rupSet.getMaxMag());
        int numBins = templateMFD.size();

        // Accumulate rates per category per bin
        Map<String, double[]> rateBins = new LinkedHashMap<>();
        Map<String, CategoryStats> stats = new LinkedHashMap<>();
        for (String cat : CATEGORIES) {
            rateBins.put(cat, new double[numBins]);
            stats.put(cat, new CategoryStats());
        }

        for (int r = 0; r < rupSet.getNumRuptures(); r++) {
            Set<PartitionPredicate> partitions = partitionsForRup(rupSet, r);
            String category = classify(partitions);
            if (category == null) continue;

            CategoryStats cs = stats.get(category);
            if (cs == null) continue;

            double rate = sol.getRateForRup(r);
            double mag = rupSet.getMagForRup(r);
            int bin = templateMFD.getClosestXIndex(mag);

            cs.totalCount++;
            if (rate > 0) {
                cs.withRateCount++;
                cs.rateSum += rate;
                rateBins.get(category)[bin] += rate;
            }
        }

        // Build incremental MFD functions
        List<IncrementalMagFreqDist> incrMFDs = new ArrayList<>();
        List<DiscretizedFunc> incrFuncs = new ArrayList<>();
        List<PlotCurveCharacterstics> incrChars = new ArrayList<>();
        for (int i = 0; i < CATEGORIES.size(); i++) {
            String cat = CATEGORIES.get(i);
            double[] bins = rateBins.get(cat);
            IncrementalMagFreqDist func =
                    new IncrementalMagFreqDist(
                            templateMFD.getMinX(), numBins, templateMFD.getDelta());
            for (int b = 0; b < numBins; b++) {
                func.set(b, bins[b]);
            }
            String legend = cat.contains("+") ? cat : cat + " only";
            func.setName(legend);
            incrMFDs.add(func);
            incrFuncs.add(func);
            incrChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, COLORS.get(i)));
        }

        Range xRange =
                new Range(
                        templateMFD.getMinX() - 0.5 * templateMFD.getDelta(),
                        templateMFD.getMaxX() + 0.5 * templateMFD.getDelta());

        // Write incremental plot
        String incrPrefix = "joint_rupture_mfds";
        writeMFDPlot(
                incrFuncs,
                incrChars,
                "Joint Rupture MFDs",
                "Incremental Rate (per yr)",
                xRange,
                resourcesDir,
                incrPrefix);

        // Build cumulative MFD functions
        List<DiscretizedFunc> cmlFuncs = new ArrayList<>();
        List<PlotCurveCharacterstics> cmlChars = new ArrayList<>();
        for (int i = 0; i < incrMFDs.size(); i++) {
            EvenlyDiscretizedFunc cmlFunc = incrMFDs.get(i).getCumRateDistWithOffset();
            cmlFunc.setName(incrMFDs.get(i).getName());
            cmlFuncs.add(cmlFunc);
            cmlChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, COLORS.get(i)));
        }

        // Write cumulative plot
        String cmlPrefix = "joint_rupture_mfds_cumulative";
        writeMFDPlot(
                cmlFuncs,
                cmlChars,
                "Joint Rupture Cumulative MFDs",
                "Cumulative Rate (per yr)",
                xRange,
                resourcesDir,
                cmlPrefix);

        // Build output with side-by-side table
        TableBuilder table = MarkdownUtils.tableBuilder();
        table.addLine("Incremental MFDs", "Cumulative MFDs");
        table.initNewLine();
        table.addColumn("![Incremental Plot](" + relPathToResources + "/" + incrPrefix + ".png)");
        table.addColumn("![Cumulative Plot](" + relPathToResources + "/" + cmlPrefix + ".png)");
        table.finalizeLine();

        List<String> lines = new ArrayList<>();
        lines.addAll(table.build());
        lines.add("");
        lines.add("| Category | Total | With Rate > 0 | % With Rate | Rate Sum |");
        lines.add("|----------|------:|--------------:|------------:|---------:|");
        for (String cat : CATEGORIES) {
            CategoryStats cs = stats.get(cat);
            double pct = cs.totalCount > 0 ? 100.0 * cs.withRateCount / cs.totalCount : 0.0;
            lines.add(
                    String.format(
                            "| %s | %,d | %,d | %.1f%% | %.4e |",
                            cat, cs.totalCount, cs.withRateCount, pct, cs.rateSum));
        }
        return lines;
    }

    @Override
    public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
        return null;
    }
}

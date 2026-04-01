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

        // Build MFD functions
        List<DiscretizedFunc> funcs = new ArrayList<>();
        List<PlotCurveCharacterstics> chars = new ArrayList<>();
        for (int i = 0; i < CATEGORIES.size(); i++) {
            String cat = CATEGORIES.get(i);
            double[] bins = rateBins.get(cat);
            EvenlyDiscretizedFunc func =
                    new EvenlyDiscretizedFunc(
                            templateMFD.getMinX(), numBins, templateMFD.getDelta());
            for (int b = 0; b < numBins; b++) {
                func.set(b, bins[b]);
            }
            String legend = cat.contains("+") ? cat : cat + " only";
            func.setName(legend);
            funcs.add(func);
            chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, COLORS.get(i)));
        }

        // Find smallest nonzero y value across all curves
        double minNonZeroY = Double.MAX_VALUE;
        for (DiscretizedFunc func : funcs) {
            for (int b = 0; b < func.size(); b++) {
                double y = func.getY(b);
                if (y > 0 && y < minNonZeroY) {
                    minNonZeroY = y;
                }
            }
        }
        Range yRange = null;
        if (minNonZeroY < Double.MAX_VALUE) {
            // Find max y for upper bound
            double maxY = 0;
            for (DiscretizedFunc func : funcs) {
                for (int b = 0; b < func.size(); b++) {
                    maxY = Math.max(maxY, func.getY(b));
                }
            }
            yRange = new Range(minNonZeroY / 10.0, maxY * 2.0);
        }

        PlotSpec spec =
                new PlotSpec(
                        funcs,
                        chars,
                        "Joint Rupture MFDs",
                        "Magnitude",
                        "Incremental Rate (per yr)");
        spec.setLegendInset(true);

        Range xRange =
                new Range(
                        templateMFD.getMinX() - 0.5 * templateMFD.getDelta(),
                        templateMFD.getMaxX() + 0.5 * templateMFD.getDelta());

        HeadlessGraphPanel gp = PlotUtils.initHeadless(PlotPreferences.getDefaultAppPrefs());
        gp.setTickLabelFontSize(20);
        gp.drawGraphPanel(spec, false, true, xRange, yRange);

        String prefix = "joint_rupture_mfds";
        PlotUtils.writePlots(resourcesDir, prefix, gp, 1000, 850, true, true, false);

        // Build output
        List<String> lines = new ArrayList<>();
        lines.add("![Joint Rupture MFDs](" + relPathToResources + "/" + prefix + ".png)");
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

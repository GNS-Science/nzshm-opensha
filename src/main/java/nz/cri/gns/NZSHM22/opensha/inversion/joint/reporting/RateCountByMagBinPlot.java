package nz.cri.gns.NZSHM22.opensha.inversion.joint.reporting;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.jfree.data.Range;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotUtils;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractRupSetPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.SolMFDPlot;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

public class RateCountByMagBinPlot extends AbstractRupSetPlot {

    @Override
    public String getName() {
        return "Rate Count by Magnitude Bin";
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

        // 1. Determine bins
        IncrementalMagFreqDist defaultMFD =
                SolMFDPlot.initDefaultMFD(rupSet.getMinMag(), rupSet.getMaxMag());
        int numBins = defaultMFD.size();

        // 2. Count ruptures per bin
        int[] totalCounts = new int[numBins];
        int[] withRateCounts = new int[numBins];
        for (int r = 0; r < rupSet.getNumRuptures(); r++) {
            double mag = rupSet.getMagForRup(r);
            double rate = sol.getRateForRup(r);
            int bin = defaultMFD.getClosestXIndex(mag);
            totalCounts[bin]++;
            if (rate > 0) withRateCounts[bin]++;
        }

        // 3. Build EvenlyDiscretizedFunc objects for count plot
        EvenlyDiscretizedFunc totalFunc =
                new EvenlyDiscretizedFunc(defaultMFD.getMinX(), numBins, defaultMFD.getDelta());
        EvenlyDiscretizedFunc withRateFunc =
                new EvenlyDiscretizedFunc(defaultMFD.getMinX(), numBins, defaultMFD.getDelta());
        for (int i = 0; i < numBins; i++) {
            totalFunc.set(i, totalCounts[i]);
            withRateFunc.set(i, withRateCounts[i]);
        }
        totalFunc.setName("Total Ruptures");
        withRateFunc.setName("With Rate > 0");

        Range xRange =
                new Range(
                        defaultMFD.getMinX() - 0.5 * defaultMFD.getDelta(),
                        defaultMFD.getMaxX() + 0.5 * defaultMFD.getDelta());

        // 4. Count plot
        List<DiscretizedFunc> funcs = new ArrayList<>();
        List<PlotCurveCharacterstics> chars = new ArrayList<>();
        funcs.add(totalFunc);
        chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, Color.LIGHT_GRAY));
        funcs.add(withRateFunc);
        chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, Color.GREEN.darker()));

        PlotSpec countSpec =
                new PlotSpec(
                        funcs, chars, "Rate Count by Magnitude Bin", "Magnitude", "Number of Ruptures");
        countSpec.setLegendInset(true);

        double maxCount = 0;
        for (int c : totalCounts) maxCount = Math.max(maxCount, c);
        Range countYRange = new Range(0.9, maxCount * 1.05);

        HeadlessGraphPanel gp = PlotUtils.initHeadless();
        gp.setTickLabelFontSize(20);
        gp.drawGraphPanel(countSpec, false, true, xRange, countYRange);

        String countPrefix = "rate_count_by_mag_bin";
        PlotUtils.writePlots(resourcesDir, countPrefix, gp, 1000, 850, true, true, false);

        // 5. Summarise totals
        int grandTotal = 0;
        int grandWithRate = 0;
        for (int i = 0; i < numBins; i++) {
            grandTotal += totalCounts[i];
            grandWithRate += withRateCounts[i];
        }
        double pct = grandTotal > 0 ? 100.0 * grandWithRate / grandTotal : 0.0;

        List<String> lines = new ArrayList<>();
        lines.add(
                "![Rate Count by Magnitude Bin]("
                        + relPathToResources
                        + "/"
                        + countPrefix
                        + ".png)");
        lines.add("");
        lines.add(
                String.format(
                        "| Ruptures | Count | Fraction |%n"
                                + "|----------|------:|--------:|%n"
                                + "| Total | %,d | 100%% |%n"
                                + "| With rate > 0 | %,d | %.1f%% |%n",
                        grandTotal, grandWithRate, pct));
        return lines;
    }

    @Override
    public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
        return null;
    }
}

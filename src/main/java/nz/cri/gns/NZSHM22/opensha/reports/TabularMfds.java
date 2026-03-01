package nz.cri.gns.NZSHM22.opensha.reports;

import java.util.ArrayList;
import java.util.List;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.*;
import org.apache.commons.math3.util.Precision;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionTargetMFDs;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.RupHistogramPlots;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.RupHistogramPlots.HistScalar;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

public class TabularMfds {

    public static List<IncrementalMagFreqDist> getSolutionMfds(
            FaultSystemSolution solution, boolean crustal) {
        InversionTargetMFDs targetMFDs = solution.getRupSet().getModule(InversionTargetMFDs.class);
        List<IncrementalMagFreqDist> solutionMfds = new ArrayList<>();
        if (crustal) {
            solutionMfds.add(targetMFDs.getTrulyOffFaultMFD());
            solutionMfds.add(targetMFDs.getTotalOnFaultSupraSeisMFD());
            solutionMfds.add(targetMFDs.getTotalOnFaultSubSeisMFD());
        } else {
            solutionMfds.add(targetMFDs.getTotalOnFaultSupraSeisMFD());
        }

        return solutionMfds;
    }

    public static List<IncrementalMagFreqDist> getSolutionMfdsV2(FaultSystemSolution solution) {
        InversionTargetMFDs targetMFDs = solution.getRupSet().getModule(InversionTargetMFDs.class);
        List<IncrementalMagFreqDist> solutionMfds = new ArrayList<>();

        solutionMfds.add(targetMFDs.getTotalRegionalMFD());
        solutionMfds.add(targetMFDs.getTrulyOffFaultMFD());
        solutionMfds.add(targetMFDs.getTotalOnFaultSupraSeisMFD());
        solutionMfds.add(targetMFDs.getTotalOnFaultSubSeisMFD());

        return solutionMfds;
    }

    /**
     * build an MFD from the inversion solution
     *
     * @param rateWeighted if false, returns the count of ruptures by magnitude, irrespective of
     *     rate.
     * @return
     */
    public static HistogramFunction solutionMagFreqHistogram(
            FaultSystemSolution solution, boolean rateWeighted) {

        ClusterRuptures cRups = solution.getRupSet().getModule(ClusterRuptures.class);

        RupHistogramPlots.HistScalarValues scalarVals =
                new RupHistogramPlots.HistScalarValues(
                        RupHistogramPlots.HistScalar.MAG,
                        solution.getRupSet(),
                        solution,
                        cRups.getAll(),
                        null);

        MinMaxAveTracker track = new MinMaxAveTracker();
        List<Integer> includeIndexes = new ArrayList<>();
        for (int r = 0; r < scalarVals.getRupSet().getNumRuptures(); r++) includeIndexes.add(r);
        for (int r : includeIndexes) track.addValue(scalarVals.getValues().get(r));

        HistScalar histScalar = scalarVals.getScalar();
        HistogramFunction histogram = histScalar.getHistogram(track);
        boolean logX = histScalar.isLogX();

        for (int i = 0; i < includeIndexes.size(); i++) {
            int rupIndex = includeIndexes.get(i);
            double scalar = scalarVals.getValues().get(i);
            double y = rateWeighted ? scalarVals.getSol().getRateForRup(rupIndex) : 1;
            int index;
            if (logX) index = scalar <= 0 ? 0 : histogram.getClosestXIndex(Math.log10(scalar));
            else index = histogram.getClosestXIndex(scalar);
            histogram.add(index, y);
        }
        return histogram;
    }

    private static void appendMfdRows(
            EvenlyDiscretizedFunc mfd, List<List<String>> rows, int series) {
        List<String> row;
        for (int i = 0; i < mfd.size(); i++) {
            row = new ArrayList<>();
            if (mfd.getY(i) > 0) {
                row.add(Integer.toString(series));
                row.add(mfd.getName());
                row.add(Double.toString(Precision.round(mfd.getX(i), 2)));
                row.add(Double.toString(mfd.getY(i)));
                rows.add(row);
            }
        }
    }

    public static List<List<String>> getTabularSolutionMfds(
            FaultSystemSolution solution, boolean crustal) {
        List<List<String>> rows = new ArrayList<>();

        int series = 0;
        for (IncrementalMagFreqDist mfd : getSolutionMfds(solution, crustal)) {
            appendMfdRows(mfd, rows, series);
            series++;
        }

        HistogramFunction magHist = solutionMagFreqHistogram(solution, true);
        magHist.setName("solutionMFD_rateWeighted");
        appendMfdRows(magHist, rows, series);
        series++;

        magHist = solutionMagFreqHistogram(solution, false);
        magHist.setName("solutionMFD_unweighted");
        appendMfdRows(magHist, rows, series);

        return rows;
    }

    public static List<List<String>> getTabularSolutionMfdsV2(FaultSystemSolution solution) {
        List<List<String>> rows = new ArrayList<>();

        int series = 0;
        for (IncrementalMagFreqDist mfd : getSolutionMfdsV2(solution)) {
            appendMfdRows(mfd, rows, series);
            series++;
        }

        HistogramFunction magHist = solutionMagFreqHistogram(solution, true);
        magHist.setName("solutionMFD");
        appendMfdRows(magHist, rows, series);

        return rows;
    }
}

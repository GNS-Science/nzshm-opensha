package nz.cri.gns.NZSHM22.opensha.hazard.joint;

import java.awt.Color;
import java.util.Collection;
import java.util.List;
import org.jfree.data.Range;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc.ReturnPeriods;

/**
 * The pieces every hazard curve plot needs: the return period marker lines, and the y range that a
 * set of curves fits into.
 *
 * <p>Shared by the site curve plots of {@link JointHazardMapCalculator}, which draw many sites of
 * one solution, and the side by side plots of {@link HazardComparisonReport}, which draw one site
 * of two solutions. Both have to place the markers and scale the axis the same way, otherwise
 * curves from the two cannot be compared by eye.
 */
public class CurvePlots {

    private CurvePlots() {}

    /**
     * Smallest annual exceedance probability worth showing. The y axis is logarithmic, so it needs
     * a floor, and curves are noise below this.
     */
    public static final double MIN_PLOTTED_PROBABILITY = 1e-8;

    /** Headroom left above the highest probability of a curve, as a factor. */
    public static final double Y_RANGE_HEADROOM = 1.2;

    /**
     * Adds a horizontal line for each return period that {@link SolHazardMapCalc#MAP_RPS} builds
     * maps for, so that a curve can be read against the return periods the maps use.
     *
     * @param funcs the plot's functions, appended to
     * @param chars the plot's line styles, appended to in step with {@code funcs}
     * @param xRange the extent the marker lines are drawn across
     */
    public static void addReturnPeriodLines(
            List<XY_DataSet> funcs, List<PlotCurveCharacterstics> chars, Range xRange) {
        PlotLineType[] lineTypes = {PlotLineType.DASHED, PlotLineType.DOTTED};
        for (int i = 0; i < SolHazardMapCalc.MAP_RPS.length; i++) {
            ReturnPeriods rp = SolHazardMapCalc.MAP_RPS[i];
            DefaultXY_DataSet line = new DefaultXY_DataSet();
            line.set(xRange.getLowerBound(), rp.oneYearProb);
            line.set(xRange.getUpperBound(), rp.oneYearProb);
            line.setName(rp.label);
            funcs.add(line);
            chars.add(new PlotCurveCharacterstics(lineTypes[i % lineTypes.length], 1f, Color.GRAY));
        }
    }

    /**
     * A logarithmic y range that every given curve fits into. Zero probabilities are skipped: a log
     * axis cannot show them, and a curve that has dropped to zero would otherwise pull the range
     * down to the floor. Curves that are entirely zero fall back to the floor as well.
     *
     * @param curves the curves that have to fit, e.g. the two curves of a comparison or every site
     *     of a site curve plot
     */
    public static Range yRange(Collection<? extends DiscretizedFunc> curves) {
        double min = Double.POSITIVE_INFINITY;
        double max = 0;
        for (DiscretizedFunc curve : curves) {
            for (int i = 0; i < curve.size(); i++) {
                double y = curve.getY(i);
                if (y > 0) {
                    min = Math.min(min, y);
                    max = Math.max(max, y);
                }
            }
        }
        return new Range(
                Math.max(
                        MIN_PLOTTED_PROBABILITY,
                        Double.isFinite(min) ? min : MIN_PLOTTED_PROBABILITY),
                Math.max(10d * MIN_PLOTTED_PROBABILITY, max * Y_RANGE_HEADROOM));
    }
}

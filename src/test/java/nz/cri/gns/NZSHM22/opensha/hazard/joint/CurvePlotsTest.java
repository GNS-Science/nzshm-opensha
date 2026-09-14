package nz.cri.gns.NZSHM22.opensha.hazard.joint;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import org.jfree.data.Range;
import org.junit.Test;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc.ReturnPeriods;

/** Tests for {@link CurvePlots}: the pieces shared by every hazard curve plot. */
public class CurvePlotsTest {

    /** One horizontal marker per return period, drawn across the whole x range. */
    @Test
    public void testAddReturnPeriodLines() {
        List<XY_DataSet> funcs = new ArrayList<>();
        List<PlotCurveCharacterstics> chars = new ArrayList<>();

        CurvePlots.addReturnPeriodLines(funcs, chars, new Range(0.01, 10d));

        assertEquals(SolHazardMapCalc.MAP_RPS.length, funcs.size());
        assertEquals(funcs.size(), chars.size());
        for (int i = 0; i < funcs.size(); i++) {
            ReturnPeriods rp = SolHazardMapCalc.MAP_RPS[i];
            XY_DataSet line = funcs.get(i);
            assertEquals(rp.label, line.getName());
            assertEquals(0.01, line.getX(0), 1e-12);
            assertEquals(10d, line.getX(1), 1e-12);
            assertEquals(rp.oneYearProb, line.getY(0), 1e-12);
            assertEquals(rp.oneYearProb, line.getY(1), 1e-12);
        }
    }

    /** The range covers every curve, so panels drawn with it can be compared by eye. */
    @Test
    public void testYRangeCoversEveryCurve() {
        DiscretizedFunc first = curve(1e-3, 1e-4);
        DiscretizedFunc second = curve(1e-5, 1e-6);

        Range range = CurvePlots.yRange(List.of(first, second));

        assertTrue(range.getLowerBound() <= 1e-6);
        assertTrue(range.getUpperBound() >= 1e-3);
    }

    /** Zeroes cannot be shown on a log axis, so they must not drag the range down to the floor. */
    @Test
    public void testYRangeIgnoresZeroes() {
        Range range = CurvePlots.yRange(List.of(curve(1e-3, 0d)));

        assertEquals(1e-3, range.getLowerBound(), 1e-12);
        assertTrue(range.getUpperBound() > 1e-3);
    }

    /** A curve that is entirely zero has no range of its own, so it falls back to the floor. */
    @Test
    public void testYRangeOfAnEmptyCurveFallsBackToTheFloor() {
        Range range = CurvePlots.yRange(List.of(curve(0d, 0d)));

        assertEquals(CurvePlots.MIN_PLOTTED_PROBABILITY, range.getLowerBound(), 1e-12);
        assertEquals(10d * CurvePlots.MIN_PLOTTED_PROBABILITY, range.getUpperBound(), 1e-12);
    }

    private static DiscretizedFunc curve(double... ys) {
        DiscretizedFunc curve = new ArbitrarilyDiscretizedFunc();
        for (int i = 0; i < ys.length; i++) {
            curve.set(0.1 * (i + 1), ys[i]);
        }
        return curve;
    }
}

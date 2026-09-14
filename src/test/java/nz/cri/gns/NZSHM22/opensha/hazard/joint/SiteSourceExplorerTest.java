package nz.cri.gns.NZSHM22.opensha.hazard.joint;

import static nz.cri.gns.NZSHM22.opensha.hazard.joint.JointTestSolutions.*;
import static org.junit.Assert.*;

import org.junit.Test;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc.ReturnPeriods;

/**
 * Tests for {@link SiteSourceExplorer}: reading an intensity level off a hazard curve, and
 * disaggregating a site's hazard into per-rupture contributions.
 *
 * <p>The central check is {@link #testContributionsMatchTheCurve}: the contributions have to add up
 * to the site's own hazard curve, or the decomposition is not a decomposition of anything.
 */
public class SiteSourceExplorerTest {

    static DiscretizedFunc curve(double[] imls, double[] poes) {
        DiscretizedFunc curve = new ArbitrarilyDiscretizedFunc();
        for (int i = 0; i < imls.length; i++) {
            curve.set(imls[i], poes[i]);
        }
        return curve;
    }

    /** The level is read off the curve where it crosses the return period's annual probability. */
    @Test
    public void testImlForReturnPeriod() {
        // 10% in 50 years is 2.1e-3 per year, which this curve reaches at 0.1g by construction
        DiscretizedFunc curve =
                curve(
                        new double[] {0.01, 0.1, 1.0},
                        new double[] {1e-1, ReturnPeriods.TEN_IN_50.oneYearProb, 1e-5});
        assertEquals(
                0.1, SiteSourceExplorer.imlForReturnPeriod(curve, ReturnPeriods.TEN_IN_50), 1e-6);
    }

    /**
     * A curve that stays above the return period across the whole grid is saturated: the level is
     * off the top of the grid, so the largest one on it is the best available answer.
     */
    @Test
    public void testImlForReturnPeriodSaturated() {
        DiscretizedFunc curve = curve(new double[] {0.01, 0.1, 1.0}, new double[] {1d, 0.5, 0.1});
        assertEquals(
                1.0, SiteSourceExplorer.imlForReturnPeriod(curve, ReturnPeriods.TEN_IN_50), 1e-6);
    }

    /** A site too quiet to reach the return period has no level to disaggregate at. */
    @Test
    public void testImlForReturnPeriodBelowCurve() {
        DiscretizedFunc curve =
                curve(new double[] {0.01, 0.1, 1.0}, new double[] {1e-4, 1e-5, 1e-6});
        try {
            SiteSourceExplorer.imlForReturnPeriod(curve, ReturnPeriods.TEN_IN_50);
            fail("expected a curve below the return period to be rejected");
        } catch (IllegalStateException expected) {
            // as expected
        }
    }

    /**
     * The contributions sum to the site's rate of exceedance, i.e. to the hazard curve the maps and
     * the site curves are built from. Rates and probabilities are related by {@code p =
     * 1-exp(-rate)}, so the two are compared through that.
     */
    @Test
    public void testContributionsMatchTheCurve() {
        SiteSourceExplorer explorer = new SiteSourceExplorer(makeSolution());
        double iml = 0.05;

        SiteSourceContributions contributions = explorer.exploreAtIml(SITE, 0d, iml);
        double poeFromCurve = explorer.siteCurve(SITE, 0d).getInterpolatedY_inLogXLogYDomain(iml);

        assertEquals(iml, contributions.getIml(), 1e-12);
        assertTrue(contributions.getTotalRate() > 0);
        assertEquals(
                poeFromCurve, 1 - Math.exp(-contributions.getTotalRate()), 0.02 * poeFromCurve);
    }

    /** Every rupture of the test solution reaches the site at a low enough level. */
    @Test
    public void testAllRupturesContributeAtLowLevel() {
        SiteSourceExplorer explorer = new SiteSourceExplorer(makeSolution());
        SiteSourceContributions contributions = explorer.exploreAtIml(SITE, 0d, 0.01);

        assertEquals(
                contributions.getRupSet().getNumRuptures(),
                contributions.getNumContributingRuptures());
        for (int r = 0; r < contributions.getRupSet().getNumRuptures(); r++) {
            assertTrue("rupture " + r, contributions.getRupRate(r) > 0);
        }
    }

    /** Raising the level cannot raise any rupture's contribution to exceeding it. */
    @Test
    public void testContributionsFallWithLevel() {
        SiteSourceExplorer explorer = new SiteSourceExplorer(makeSolution());
        SiteSourceContributions low = explorer.exploreAtIml(SITE, 0d, 0.01);
        SiteSourceContributions high = explorer.exploreAtIml(SITE, 0d, 0.2);

        assertTrue(high.getTotalRate() < low.getTotalRate());
        for (int r = 0; r < low.getRupSet().getNumRuptures(); r++) {
            assertTrue("rupture " + r, high.getRupRate(r) <= low.getRupRate(r));
        }
    }

    /**
     * A level nothing can reach leaves nothing to explore, and says so rather than returning
     * zeroes.
     */
    @Test
    public void testRejectsUnreachableLevel() {
        SiteSourceExplorer explorer = new SiteSourceExplorer(makeSolution());
        try {
            explorer.exploreAtIml(SITE, 0d, 1e3);
            fail("expected an unreachable level to be rejected");
        } catch (IllegalStateException expected) {
            // as expected
        }
    }
}

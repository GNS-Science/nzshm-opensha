package nz.cri.gns.NZSHM22.opensha.hazard.joint;

import static nz.cri.gns.NZSHM22.opensha.hazard.joint.JointTestSolutions.*;
import static org.junit.Assert.*;

import java.io.File;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.commons.param.Parameter;
import org.opensha.nshmp.shaded.gmm.NshmpGroundMotion;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.erf.BaseFaultSystemSolutionERF;
import org.opensha.sha.earthquake.faultSysSolution.modules.RupSetTectonicRegimes;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc.ReturnPeriods;
import org.opensha.sha.gui.infoTools.IMT_Info;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.attenRelImpl.JointRuptureExperimentalIMR;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.util.TectonicRegionType;

/**
 * Tests for {@link JointHazardCalcSetup} and {@link JointHazardMapCalculator}: the GMM setup and
 * the maps, curves and plots built on top of it. The test solution lives in {@link
 * JointTestSolutions}.
 */
public class JointHazardMapCalculatorTest {

    @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

    /**
     * The GMM map is deliberately single-entry: OpenSHA applies a single-entry IMR map to every
     * source regardless of the source's tectonic region type, which is what lets the joint GMM see
     * subduction sources as well and do its own dispatch.
     */
    @Test
    public void testGmmSupplierMapIsSingleEntry() {
        Map<TectonicRegionType, ?> map = JointHazardCalcSetup.gmmSupplierMap();
        assertEquals(1, map.size());
        assertTrue(map.containsKey(TectonicRegionType.ACTIVE_SHALLOW));
        assertTrue(JointHazardCalcSetup.buildGmm() instanceof JointRuptureExperimentalIMR);
    }

    /** The per-TRT map has a GMM for each region type, and they are different models. */
    @Test
    public void testPerTrtGmmSupplierMap() {
        Map<TectonicRegionType, Supplier<ScalarIMR>> map =
                JointHazardCalcSetup.perTrtGmmSupplierMap();
        assertEquals(2, map.size());

        ScalarIMR crustal = map.get(TectonicRegionType.ACTIVE_SHALLOW).get();
        ScalarIMR interfce = map.get(TectonicRegionType.SUBDUCTION_INTERFACE).get();
        assertNotNull(crustal);
        assertNotNull(interfce);
        assertNotEquals(crustal.getName(), interfce.getName());
    }

    /**
     * The joint GMM only splits a rupture into its crustal and interface parts when it is handed a
     * {@link org.opensha.sha.faultSurface.CompoundSurface} carrying section data. {@code
     * SolHazardMapCalc} gives each calculation thread a {@code DistCachedERFWrapper}, which
     * replaces every rupture surface with an opaque {@code CustomCacheWrappedSurface}, and the GMM
     * then falls back to classifying ruptures by magnitude alone. {@link
     * JointHazardCalcSetup#getCalc()} switches that wrapper off for {@link
     * JointHazardInput.GmmMode#JOINT_RUPTURE}.
     *
     * <p>Checked end to end: the map value at a node has to agree with the hazard curve calculated
     * at the same location, which uses the unwrapped ERF. The two use different IML grids so they
     * differ by a percent or so; with the wrapper in place they differ by more than 20%.
     */
    @Test
    public void testMapAgreesWithSiteCurves() {
        Region border = new Region(new Location(-42.2, 174.0), new Location(-40.6, 175.8));
        GriddedRegion region = new GriddedRegion(border, 0.2, 0.2, GriddedRegion.ANCHOR_0_0);
        JointHazardMapCalculator calculator =
                new JointHazardMapCalculator(
                        new JointHazardInput(makeSolution()).setRegion(region).setPeriods(0d));
        calculator.calcHazardCurves();

        GriddedGeoDataSet map = calculator.getCalc().buildMap(0d, ReturnPeriods.TWO_IN_50);
        int compared = 0;
        for (int i = 0; i < map.size(); i++) {
            double mapValue = map.get(i);
            if (mapValue <= 0d) {
                continue;
            }
            DiscretizedFunc curve = calculator.calcSiteCurve(map.getLocation(i), 0d);
            double curveValue =
                    HazardComparisonReport.imlAt(curve, ReturnPeriods.TWO_IN_50.oneYearProb);
            assertEquals(
                    "map and site curve disagree at " + map.getLocation(i),
                    mapValue,
                    curveValue,
                    0.05 * mapValue);
            compared++;
        }
        assertTrue("expected some nodes with hazard", compared > 0);
    }

    /**
     * The setup gives the rupture set its tectonic region types in {@link
     * JointHazardInput.GmmMode#JOINT_RUPTURE} too, even though the single-entry GMM map does not
     * dispatch on them.
     *
     * <p>They are needed by the calculator's default source filter, {@code
     * TectonicRegionDistCutoffFilter}, which drops a source beyond {@code
     * TectonicRegionType.defaultCutoffDist()}: 300km for {@link TectonicRegionType#ACTIVE_SHALLOW}
     * against 1000km for {@link TectonicRegionType#SUBDUCTION_INTERFACE}. Without the module the
     * ERF reports every source as ACTIVE_SHALLOW, subduction sources are culled at 300km, and the
     * hazard is silently zero at sites whose shaking comes from a distant interface.
     *
     * <p>Joint ruptures get SUBDUCTION_INTERFACE, the wider of the two cutoffs, so that they reach
     * the joint GMM rather than being filtered out before it.
     */
    @Test
    public void testJointModeAppliesTectonicRegimes() {
        JointHazardInput input = new JointHazardInput(makeSolution());
        assertEquals(JointHazardInput.GmmMode.JOINT_RUPTURE, input.getGmmMode());
        new JointHazardCalcSetup(input);

        RupSetTectonicRegimes regimes =
                input.getSolution().getRupSet().getModule(RupSetTectonicRegimes.class);
        assertNotNull("joint mode should apply tectonic regimes too", regimes);
        assertEquals(TectonicRegionType.ACTIVE_SHALLOW, regimes.get(CRUSTAL_RUP));
        assertEquals(TectonicRegionType.SUBDUCTION_INTERFACE, regimes.get(INTERFACE_RUP));
        assertEquals(TectonicRegionType.SUBDUCTION_INTERFACE, regimes.get(JOINT_RUP));
    }

    /**
     * The regimes reach the ERF's sources, which is where the distance cutoff filter reads them
     * from. Before they were applied in joint mode every source came out as ACTIVE_SHALLOW.
     */
    @Test
    public void testJointModeErfSourcesCarryTectonicRegionTypes() {
        JointHazardCalcSetup setup = new JointHazardCalcSetup(new JointHazardInput(makeSolution()));
        BaseFaultSystemSolutionERF erf = setup.getCalc().getERF();
        erf.updateForecast();

        Set<TectonicRegionType> trts = EnumSet.noneOf(TectonicRegionType.class);
        for (int i = 0; i < erf.getNumSources(); i++) {
            trts.add(erf.getSource(i).getTectonicRegionType());
        }
        assertTrue(
                "subduction sources should not reach the distance filter as ACTIVE_SHALLOW: "
                        + trts,
                trts.contains(TectonicRegionType.SUBDUCTION_INTERFACE));
    }

    /**
     * The map IML grid is the standard USGS SA function plus exactly one point, a decade below its
     * lowest IML. Guards against the extension being applied twice: a second point cannot rescue
     * any site, because the curve has already reached its low IML asymptote by then.
     */
    @Test
    public void testMapXValsExtendOneDecade() {
        ArbitrarilyDiscretizedFunc standard = IMT_Info.getUSGS_SA_Function();
        ArbitrarilyDiscretizedFunc xVals = JointHazardCalcSetup.mapXVals();

        assertEquals(standard.size() + 1, xVals.size());
        assertEquals(standard.getMinX() * 0.1, xVals.getMinX(), 1e-12);
        assertEquals(standard.getMinX(), xVals.getX(1), 1e-12);
        assertEquals(standard.getMaxX(), xVals.getMaxX(), 1e-12);
    }

    /**
     * The sources of a per-TRT ERF report their own tectonic region type. This is what makes the
     * two entry GMM map work: OpenSHA dispatches on the source's TRT, and the ERF only knows it if
     * the rupture set carries the tectonic regimes module.
     */
    @Test
    public void testPerTrtErfSourcesCarryTectonicRegionTypes() {
        JointHazardMapCalculator calculator =
                new JointHazardMapCalculator(
                        JointHazardInput.combined(makeCrustalSolution(), makeSubductionSolution())
                                .setRegion(smallRegion())
                                .setPeriods(0d)
                                .setNumThreads(1));

        BaseFaultSystemSolutionERF erf = calculator.getCalc().getERF();
        Set<TectonicRegionType> found = EnumSet.noneOf(TectonicRegionType.class);
        for (int s = 0; s < erf.getNumSources(); s++) {
            found.add(erf.getSource(s).getTectonicRegionType());
        }
        assertEquals(
                Set.of(TectonicRegionType.ACTIVE_SHALLOW, TectonicRegionType.SUBDUCTION_INTERFACE),
                found);
    }

    /**
     * Calculating two solutions as one ERF gives the same hazard as calculating them separately and
     * combining the curves probabilistically: 1 - (1 - Pc)(1 - Ps). That is what "one ERF, two
     * solutions" is supposed to buy, so it is worth pinning down.
     */
    @Test
    public void testCombinedCurveIsProbabilisticUnionOfItsParts() {
        DiscretizedFunc crustal = perTrtSiteCurve(makeCrustalSolution());
        DiscretizedFunc subduction = perTrtSiteCurve(makeSubductionSolution());

        JointHazardMapCalculator combined =
                new JointHazardMapCalculator(
                        JointHazardInput.combined(makeCrustalSolution(), makeSubductionSolution())
                                .setRegion(smallRegion())
                                .setPeriods(0d)
                                .setNumThreads(1));
        DiscretizedFunc curve = combined.calcSiteCurve(SITE, 0d);

        assertTrue("expected non-zero hazard at the site", curve.getY(0) > 0);
        for (int i = 0; i < curve.size(); i++) {
            double expected = 1d - (1d - crustal.getY(i)) * (1d - subduction.getY(i));
            assertEquals(
                    "at iml " + (float) curve.getX(i),
                    expected,
                    curve.getY(i),
                    1e-6 + 1e-3 * expected);
        }
    }

    /** A site curve for a single solution, calculated with the per-TRT GMMs. */
    private static DiscretizedFunc perTrtSiteCurve(FaultSystemSolution solution) {
        return new JointHazardMapCalculator(
                        JointHazardInput.perTectonicRegion(solution)
                                .setRegion(smallRegion())
                                .setPeriods(0d)
                                .setNumThreads(1))
                .calcSiteCurve(SITE, 0d);
    }

    /** End to end for two solutions calculated together. */
    @Test
    public void testCombinedEndToEnd() throws Exception {
        JointHazardMapCalculator calculator =
                new JointHazardMapCalculator(
                        JointHazardInput.combined(makeCrustalSolution(), makeSubductionSolution())
                                .setRegion(mapRegion())
                                .setPeriods(0d)
                                .setNumThreads(1));

        File outputDir = tempFolder.newFolder("combined");
        List<File> maps = calculator.writeMaps(outputDir);
        assertEquals(SolHazardMapCalc.MAP_RPS.length, maps.size());
        for (File map : maps) {
            assertTrue(map.getName() + " should exist", map.exists());
        }

        GriddedGeoDataSet map = calculator.getCalc().buildMap(0d, ReturnPeriods.TEN_IN_50);
        boolean anyPositive = false;
        for (int i = 0; i < map.size(); i++) {
            assertFalse("map values must be finite", Double.isNaN(map.get(i)));
            anyPositive |= map.get(i) > 0;
        }
        assertTrue("expected non-zero ground motion near the faults", anyPositive);
    }

    /** A single solution holding both kinds of rupture, calculated per tectonic region type. */
    @Test
    public void testMixedSolutionPerTectonicRegion() {
        JointHazardMapCalculator calculator =
                new JointHazardMapCalculator(
                        JointHazardInput.perTectonicRegion(makeMixedSolution())
                                .setRegion(smallRegion())
                                .setPeriods(0d)
                                .setNumThreads(1));

        DiscretizedFunc curve = calculator.calcSiteCurve(SITE, 0d);
        assertTrue("expected non-zero hazard at the site", curve.getY(0) > 0);

        BaseFaultSystemSolutionERF erf = calculator.getCalc().getERF();
        Set<TectonicRegionType> found = EnumSet.noneOf(TectonicRegionType.class);
        for (int s = 0; s < erf.getNumSources(); s++) {
            found.add(erf.getSource(s).getTectonicRegionType());
        }
        assertEquals(
                Set.of(TectonicRegionType.ACTIVE_SHALLOW, TectonicRegionType.SUBDUCTION_INTERFACE),
                found);
    }

    /** Big enough to plot a map: a single row or column of nodes cannot be drawn. */
    private static GriddedRegion mapRegion() {
        return new GriddedRegion(
                new Region(new Location(-41.6, 174.5), new Location(-41.1, 175.2)),
                0.25,
                GriddedRegion.ANCHOR_0_0);
    }

    /** Just enough nodes to build an ERF and calculate site curves. */
    private static GriddedRegion smallRegion() {
        return new GriddedRegion(
                new Region(new Location(-41.5, 174.7), new Location(-41.3, 174.9)),
                0.2,
                GriddedRegion.ANCHOR_0_0);
    }

    /**
     * The joint rupture produces a stronger shaking estimate than either of its parts on its own,
     * which is what the GMM's SRSS combination of the two component medians should do.
     */
    @Test
    public void testJointRuptureShakesHarderThanItsParts() {
        FaultSystemRupSet rupSet = makeRupSet(0d);

        ScalarIMR gmm = JointHazardCalcSetup.buildGmm();
        gmm.setIntensityMeasure(PGA_Param.NAME);
        Site site = new Site(SITE);
        for (Parameter<?> param : gmm.getSiteParams()) {
            site.addParameter((Parameter<?>) param.clone());
        }
        gmm.setSite(site);

        double crustalMean = meanFor(gmm, rupSet, CRUSTAL_RUP);
        double interfaceMean = meanFor(gmm, rupSet, INTERFACE_RUP);
        double jointMean = meanFor(gmm, rupSet, JOINT_RUP);

        assertTrue(
                "joint (" + jointMean + ") should exceed crustal (" + crustalMean + ")",
                jointMean > crustalMean);
        assertTrue(
                "joint (" + jointMean + ") should exceed interface (" + interfaceMean + ")",
                jointMean > interfaceMean);
    }

    private static double meanFor(ScalarIMR gmm, FaultSystemRupSet rupSet, int rupIndex) {
        gmm.setEqkRupture(
                new EqkRupture(
                        rupSet.getMagForRup(rupIndex),
                        rupSet.getAveRakeForRup(rupIndex),
                        rupSet.getSurfaceForRupture(rupIndex, 1d),
                        null));
        return gmm.getMean();
    }

    /** The joint ground motion combination is an SRSS of the component medians. */
    @Test
    public void testJointGroundMotionIsSRSS() {
        var crustal = NshmpGroundMotion.create(Math.log(0.3), 0.6);
        var interfce = NshmpGroundMotion.create(Math.log(0.4), 0.5);
        var joint = JointRuptureExperimentalIMR.calcJointGroundMotion(crustal, interfce);

        assertEquals(Math.log(Math.sqrt(0.3 * 0.3 + 0.4 * 0.4)), joint.mean(), 1e-9);
        // sigma is weighted by the energy each component contributes, so it sits between the two
        assertTrue(joint.sigma() > 0.5);
        assertTrue(joint.sigma() < 0.6);
    }

    /**
     * End to end: hazard curves at a small grid, then a map and a site curve plot. Verifies that
     * the joint GMM can be driven through the standard {@link SolHazardMapCalc} machinery.
     */
    @Test
    public void testEndToEndMapAndCurves() throws Exception {
        GriddedRegion region =
                new GriddedRegion(
                        new Region(new Location(-41.6, 174.5), new Location(-41.1, 175.2)),
                        0.25,
                        GriddedRegion.ANCHOR_0_0);

        JointHazardMapCalculator calculator =
                new JointHazardMapCalculator(
                        new JointHazardInput(makeSolution())
                                .setRegion(region)
                                .setPeriods(0d)
                                .setNumThreads(1));

        File outputDir = tempFolder.newFolder("hazard");
        List<File> maps = calculator.writeMaps(outputDir);

        assertEquals(SolHazardMapCalc.MAP_RPS.length, maps.size());
        for (File map : maps) {
            assertTrue(map.getName() + " should exist", map.exists());
            assertTrue(map.getName() + " should not be empty", map.length() > 0);
        }

        // curves must be non-zero and monotonically decreasing with increasing shaking
        DiscretizedFunc[] curves = calculator.getCalc().getCurves(0d);
        assertEquals(region.getNodeCount(), curves.length);
        boolean anyNonZero = false;
        for (DiscretizedFunc curve : curves) {
            assertNotNull(curve);
            for (int i = 1; i < curve.size(); i++) {
                assertTrue(
                        "hazard curve must not increase with shaking",
                        curve.getY(i) <= curve.getY(i - 1) + 1e-12);
            }
            anyNonZero |= curve.getY(0) > 0;
        }
        assertTrue("expected non-zero hazard near the faults", anyNonZero);

        // the 10% in 50 year map should have finite, positive values near the faults
        GriddedGeoDataSet map = calculator.getCalc().buildMap(0d, ReturnPeriods.TEN_IN_50);
        boolean anyPositive = false;
        for (int i = 0; i < map.size(); i++) {
            assertFalse("map values must be finite", Double.isNaN(map.get(i)));
            anyPositive |= map.get(i) > 0;
        }
        assertTrue("expected non-zero ground motion near the faults", anyPositive);

        // and a site curve plot
        Map<String, Location> sites = new LinkedHashMap<>();
        sites.put("Site", SITE);
        File plot = calculator.writeSiteCurves(outputDir, sites, 0d);
        assertTrue(plot.exists());
        assertTrue(new File(outputDir, "site_hazard_curves_pga.csv").exists());
    }

    /**
     * A curve calculated at a site next to the faults must be non-trivial: the single-entry GMM map
     * has to reach the subduction sources too, so this also guards the tectonic region type
     * dispatch.
     */
    @Test
    public void testSiteCurveIsNonZero() {
        JointHazardMapCalculator calculator =
                new JointHazardMapCalculator(
                        new JointHazardInput(makeSolution())
                                .setRegion(
                                        new GriddedRegion(
                                                new Region(
                                                        new Location(-41.5, 174.7),
                                                        new Location(-41.3, 174.9)),
                                                0.2,
                                                GriddedRegion.ANCHOR_0_0))
                                .setPeriods(0d));

        DiscretizedFunc curve = calculator.calcSiteCurve(SITE, 0d);
        assertTrue("expected non-zero hazard at the site", curve.getY(0) > 0);
        assertTrue(
                "annual exceedance probability must be a probability",
                curve.getY(0) <= 1d && curve.getY(curve.size() - 1) >= 0d);
    }
}

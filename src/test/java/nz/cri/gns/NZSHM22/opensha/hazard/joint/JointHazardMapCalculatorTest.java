package nz.cri.gns.NZSHM22.opensha.hazard.joint;

import static nz.cri.gns.NZSHM22.opensha.hazard.joint.JointTestSolutions.*;
import static org.junit.Assert.*;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.commons.param.Parameter;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc.ReturnPeriods;
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
        var crustal = gov.usgs.earthquake.nshmp.gmm.GroundMotion.create(Math.log(0.3), 0.6);
        var interfce = gov.usgs.earthquake.nshmp.gmm.GroundMotion.create(Math.log(0.4), 0.5);
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

    @Test
    public void testPeriodLabels() {
        assertEquals("PGA", JointHazardMapCalculator.periodLabel(0d));
        assertEquals("PGV", JointHazardMapCalculator.periodLabel(-1d));
        assertEquals("1s SA", JointHazardMapCalculator.periodLabel(1d));
        assertEquals("g", JointHazardMapCalculator.periodUnits(0d));
        assertEquals("cm/s", JointHazardMapCalculator.periodUnits(-1d));
    }
}

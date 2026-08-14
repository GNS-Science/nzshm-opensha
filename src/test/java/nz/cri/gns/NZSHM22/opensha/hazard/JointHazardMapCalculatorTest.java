package nz.cri.gns.NZSHM22.opensha.hazard;

import static org.junit.Assert.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nz.cri.gns.NZSHM22.opensha.data.location.NzshmCommonLocations;
import nz.cri.gns.NZSHM22.opensha.hazard.JointHazardMapCalculator.RuptureType;
import nz.cri.gns.NZSHM22.opensha.hazard.JointHazardMapCalculator.ValidationResult;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionPredicate;
import nz.cri.gns.NZSHM22.opensha.ruptures.FaultSectionProperties;
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
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc.ReturnPeriods;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.attenRelImpl.JointRuptureExperimentalIMR;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.util.TectonicRegionType;

/**
 * Tests for {@link JointHazardMapCalculator}.
 *
 * <p>The tests build a miniature joint solution: two crustal sections and two subduction interface
 * sections near Wellington, with a crustal-only, an interface-only and a joint rupture. Magnitudes
 * are derived from the rupture surface areas with the same scaling that {@link
 * JointRuptureExperimentalIMR} assumes, which is what a real joint solution built with the
 * JOIN_ESTIMATE scaling relationship does.
 */
public class JointHazardMapCalculatorTest {

    @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

    private static final List<List<Integer>> SECTIONS_FOR_RUPS =
            List.of(
                    List.of(0, 1), // crustal
                    List.of(2, 3), // interface
                    List.of(0, 1, 2, 3)); // joint

    private static final int CRUSTAL_RUP = 0;
    private static final int INTERFACE_RUP = 1;
    private static final int JOINT_RUP = 2;

    /** A site sitting between the crustal and the interface sections. */
    private static final Location SITE = new Location(-41.4, 174.85);

    private static GeoJSONFaultSection makeSection(
            int id, TectonicRegionType trt, double lat1, double lon1, double lat2, double lon2) {
        FaultTrace trace = new FaultTrace("trace " + id);
        trace.add(new Location(lat1, lon1));
        trace.add(new Location(lat2, lon2));
        FaultSectionPrefData pref = new FaultSectionPrefData();
        pref.setSectionId(id);
        pref.setSectionName("Section " + id);
        pref.setFaultTrace(trace);
        pref.setAveSlipRate(10);
        pref.setAveRake(trt == TectonicRegionType.ACTIVE_SHALLOW ? 180 : 90);
        pref.setAveDip(trt == TectonicRegionType.ACTIVE_SHALLOW ? 90 : 20);
        pref.setAveUpperDepth(0);
        pref.setAveLowerDepth(trt == TectonicRegionType.ACTIVE_SHALLOW ? 15 : 20);
        pref.setDipDirection((float) trace.getDipDirection());
        GeoJSONFaultSection section = GeoJSONFaultSection.fromFaultSection(pref);
        section.setTectonicRegionType(trt);
        new FaultSectionProperties(section)
                .setPartition(
                        trt == TectonicRegionType.ACTIVE_SHALLOW
                                ? PartitionPredicate.CRUSTAL
                                : PartitionPredicate.HIKURANGI);
        return section;
    }

    private static List<FaultSection> makeSections() {
        List<FaultSection> sections = new ArrayList<>();
        // two crustal sections, striking NE, west of the site
        sections.add(makeSection(0, TectonicRegionType.ACTIVE_SHALLOW, -41.5, 174.6, -41.4, 174.7));
        sections.add(makeSection(1, TectonicRegionType.ACTIVE_SHALLOW, -41.4, 174.7, -41.3, 174.8));
        // two interface sections, east of the site
        sections.add(
                makeSection(
                        2, TectonicRegionType.SUBDUCTION_INTERFACE, -41.4, 174.9, -41.3, 175.0));
        sections.add(
                makeSection(
                        3, TectonicRegionType.SUBDUCTION_INTERFACE, -41.3, 175.0, -41.2, 175.1));
        return sections;
    }

    /**
     * Builds the rupture set twice: the first pass gives us rupture surfaces to measure areas on,
     * the second sets the magnitudes that those areas imply under the joint scaling.
     */
    private static FaultSystemRupSet makeRupSet(double jointMagOffset) {
        List<FaultSection> sections = makeSections();
        double[] placeholderMags = new double[SECTIONS_FOR_RUPS.size()];
        Arrays.fill(placeholderMags, 7d);
        FaultSystemRupSet firstPass =
                FaultSystemRupSet.builder(sections, SECTIONS_FOR_RUPS)
                        .rupMags(placeholderMags)
                        .build();

        double[] mags = new double[SECTIONS_FOR_RUPS.size()];
        for (int r = 0; r < mags.length; r++) {
            double crustalArea = 0;
            double interfaceArea = 0;
            for (int s : SECTIONS_FOR_RUPS.get(r)) {
                double area =
                        firstPass
                                .getFaultSectionData(s)
                                .getFaultSurface(1d, false, false)
                                .getArea();
                if (firstPass.getFaultSectionData(s).getTectonicRegionType()
                        == TectonicRegionType.ACTIVE_SHALLOW) {
                    crustalArea += area;
                } else {
                    interfaceArea += area;
                }
            }
            if (crustalArea > 0 && interfaceArea > 0) {
                mags[r] =
                        JointRuptureExperimentalIMR.getJointMag(crustalArea, interfaceArea)
                                + jointMagOffset;
            } else if (crustalArea > 0) {
                mags[r] = JointRuptureExperimentalIMR.getCrustalMag(crustalArea);
            } else {
                mags[r] = JointRuptureExperimentalIMR.getInterfaceMag(interfaceArea);
            }
        }

        return FaultSystemRupSet.builder(makeSections(), SECTIONS_FOR_RUPS).rupMags(mags).build();
    }

    private static FaultSystemSolution makeSolution() {
        return makeSolution(0d);
    }

    private static FaultSystemSolution makeSolution(double jointMagOffset) {
        FaultSystemRupSet rupSet = makeRupSet(jointMagOffset);
        double[] rates = new double[rupSet.getNumRuptures()];
        Arrays.fill(rates, 1e-3);
        return new FaultSystemSolution(rupSet, rates);
    }

    /** Ruptures are classified by the tectonic region types of their sections. */
    @Test
    public void testTypeOf() {
        FaultSystemRupSet rupSet = makeRupSet(0d);
        assertEquals(RuptureType.CRUSTAL, JointHazardMapCalculator.typeOf(rupSet, CRUSTAL_RUP));
        assertEquals(RuptureType.INTERFACE, JointHazardMapCalculator.typeOf(rupSet, INTERFACE_RUP));
        assertEquals(RuptureType.JOINT, JointHazardMapCalculator.typeOf(rupSet, JOINT_RUP));
    }

    /** A solution built with the joint scaling passes validation. */
    @Test
    public void testValidate() {
        ValidationResult result = new JointHazardMapCalculator(makeSolution()).validate();
        assertEquals(1, result.numCrustal);
        assertEquals(1, result.numInterface);
        assertEquals(1, result.numJoint);
        assertEquals(1, result.numJointWithRate);
        assertTrue(result.isJoint());
        assertEquals(0d, result.maxJointMagDiff, 1e-6);
        assertEquals(0, result.getNumSingleSectionWithRate());
    }

    /**
     * Single-section ruptures with a rate are counted and reported, because the GMM cannot tell
     * from a single, non-compound surface whether the rupture is crustal or interface.
     */
    @Test
    public void testValidateCountsSingleSectionRuptures() {
        List<List<Integer>> sectionsForRups = List.of(List.of(0), List.of(0, 1));
        FaultSystemRupSet rupSet =
                FaultSystemRupSet.builder(makeSections(), sectionsForRups)
                        .rupMags(new double[] {6.5, 7.0})
                        .build();

        ValidationResult withRate =
                new JointHazardMapCalculator(
                                new FaultSystemSolution(rupSet, new double[] {1e-3, 1e-3}))
                        .validate();
        assertEquals(1, withRate.getNumSingleSectionWithRate());
        assertFalse(withRate.isJoint());

        // ruptures without a rate never make it into the ERF, so they do not matter
        ValidationResult withoutRate =
                new JointHazardMapCalculator(
                                new FaultSystemSolution(rupSet, new double[] {0d, 1e-3}))
                        .validate();
        assertEquals(0, withoutRate.getNumSingleSectionWithRate());
    }

    /**
     * Validation rejects a solution whose joint magnitudes do not follow the scaling the GMM
     * assumes. Without this check the GMM throws part way through a long calculation.
     */
    @Test
    public void testValidateRejectsIncompatibleScaling() {
        // 5% of ~8 is ~0.4, so offset the joint magnitude by more than that
        JointHazardMapCalculator calculator = new JointHazardMapCalculator(makeSolution(1.0));
        String message = validationFailure(calculator);
        assertTrue(message, message.contains("joint area scaling"));
    }

    /** Validation rejects sections that have no usable tectonic region type. */
    @Test
    public void testValidateRejectsMissingTectonicRegionType() {
        FaultSystemSolution solution = makeSolution();
        solution.getRupSet()
                .getFaultSectionData(0)
                .setTectonicRegionType(TectonicRegionType.SUBDUCTION_SLAB);
        JointHazardMapCalculator calculator = new JointHazardMapCalculator(solution);
        String message = validationFailure(calculator);
        assertTrue(message, message.contains("tectonic region type"));
    }

    /** Runs validation, expecting it to fail, and returns the failure message. */
    private static String validationFailure(JointHazardMapCalculator calculator) {
        try {
            calculator.validate();
        } catch (IllegalStateException e) {
            return e.getMessage();
        }
        fail("expected validation to fail");
        return null;
    }

    /** The magnitude the GMM derives from the surface areas matches the solution's magnitude. */
    @Test
    public void testJointMagForRupture() {
        FaultSystemRupSet rupSet = makeRupSet(0d);
        assertEquals(
                rupSet.getMagForRup(JOINT_RUP),
                JointHazardMapCalculator.jointMagForRupture(rupSet, JOINT_RUP),
                1e-6);
    }

    /**
     * The GMM map is deliberately single-entry: OpenSHA applies a single-entry IMR map to every
     * source regardless of the source's tectonic region type, which is what lets the joint GMM see
     * subduction sources as well and do its own dispatch.
     */
    @Test
    public void testGmmSupplierMapIsSingleEntry() {
        Map<TectonicRegionType, ?> map = JointHazardMapCalculator.gmmSupplierMap();
        assertEquals(1, map.size());
        assertTrue(map.containsKey(TectonicRegionType.ACTIVE_SHALLOW));
        assertTrue(JointHazardMapCalculator.buildGmm() instanceof JointRuptureExperimentalIMR);
    }

    /**
     * The joint rupture produces a stronger shaking estimate than either of its parts on its own,
     * which is what the GMM's SRSS combination of the two component medians should do.
     */
    @Test
    public void testJointRuptureShakesHarderThanItsParts() {
        FaultSystemRupSet rupSet = makeRupSet(0d);

        ScalarIMR gmm = JointHazardMapCalculator.buildGmm();
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
                new JointHazardMapCalculator(makeSolution())
                        .setRegion(region)
                        .setPeriods(0d)
                        .setNumThreads(1);

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
                new JointHazardMapCalculator(makeSolution())
                        .setRegion(
                                new GriddedRegion(
                                        new Region(
                                                new Location(-41.5, 174.7),
                                                new Location(-41.3, 174.9)),
                                        0.2,
                                        GriddedRegion.ANCHOR_0_0))
                        .setPeriods(0d);

        DiscretizedFunc curve = calculator.calcSiteCurve(SITE, 0d);
        assertTrue("expected non-zero hazard at the site", curve.getY(0) > 0);
        assertTrue(
                "annual exceedance probability must be a probability",
                curve.getY(0) <= 1d && curve.getY(curve.size() - 1) >= 0d);
    }

    /**
     * The defaults implement the requested specification: all of New Zealand at 0.1 degrees, PGA
     * and SA(3.0), curves at the nzshm-common NZ locations. Return periods (2% and 10% in 50 years)
     * come from {@link SolHazardMapCalc#MAP_RPS}.
     */
    @Test
    public void testDefaults() {
        assertEquals(0.1, JointHazardMapCalculator.DEFAULT_SPACING, 1e-9);
        assertArrayEquals(new double[] {0d, 3d}, JointHazardMapCalculator.DEFAULT_PERIODS, 1e-9);
        assertEquals(NzshmCommonLocations.nzLocations(), JointHazardMapCalculator.defaultSites());

        assertEquals(
                Set.of(ReturnPeriods.TWO_IN_50, ReturnPeriods.TEN_IN_50),
                Set.of(SolHazardMapCalc.MAP_RPS));

        // the default region covers the country at 0.1 degrees
        GriddedRegion region = new JointHazardMapCalculator(makeSolution()).getRegion();
        assertEquals(0.1, region.getSpacing(), 1e-9);
        for (Location location : NzshmCommonLocations.nzLocations().values()) {
            assertTrue(
                    "region should contain " + location,
                    region.contains(location) || region.distanceToLocation(location) < 20);
        }
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

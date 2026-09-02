package nz.cri.gns.NZSHM22.opensha.hazard.joint;

import static nz.cri.gns.NZSHM22.opensha.hazard.joint.JointTestSolutions.*;
import static org.junit.Assert.*;

import java.util.List;
import java.util.Set;
import nz.cri.gns.NZSHM22.opensha.data.location.NzshmCommonLocations;
import nz.cri.gns.NZSHM22.opensha.hazard.joint.JointHazardInput.GmmMode;
import nz.cri.gns.NZSHM22.opensha.hazard.joint.JointHazardInput.RuptureType;
import nz.cri.gns.NZSHM22.opensha.hazard.joint.JointHazardInput.ValidationResult;
import org.junit.Test;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc.ReturnPeriods;
import org.opensha.sha.util.TectonicRegionType;

/** Tests for {@link JointHazardInput}: the calculation inputs and their validation. */
public class JointHazardInputTest {

    /** Ruptures are classified by the tectonic region types of their sections. */
    @Test
    public void testTypeOf() {
        FaultSystemRupSet rupSet = makeRupSet(0d);
        assertEquals(RuptureType.CRUSTAL, JointHazardInput.typeOf(rupSet, CRUSTAL_RUP));
        assertEquals(RuptureType.INTERFACE, JointHazardInput.typeOf(rupSet, INTERFACE_RUP));
        assertEquals(RuptureType.JOINT, JointHazardInput.typeOf(rupSet, JOINT_RUP));
    }

    /** A solution built with the joint scaling passes validation. */
    @Test
    public void testValidate() {
        ValidationResult result = new JointHazardInput(makeSolution()).validate();
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
                new JointHazardInput(new FaultSystemSolution(rupSet, new double[] {1e-3, 1e-3}))
                        .validate();
        assertEquals(1, withRate.getNumSingleSectionWithRate());
        assertFalse(withRate.isJoint());

        // ruptures without a rate never make it into the ERF, so they do not matter
        ValidationResult withoutRate =
                new JointHazardInput(new FaultSystemSolution(rupSet, new double[] {0d, 1e-3}))
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
        String message = validationFailure(new JointHazardInput(makeSolution(1.0)));
        assertTrue(message, message.contains("joint area scaling"));
    }

    /** Validation rejects sections that have no usable tectonic region type. */
    @Test
    public void testValidateRejectsMissingTectonicRegionType() {
        FaultSystemSolution solution = makeSolution();
        solution.getRupSet()
                .getFaultSectionData(0)
                .setTectonicRegionType(TectonicRegionType.SUBDUCTION_SLAB);
        String message = validationFailure(new JointHazardInput(solution));
        assertTrue(message, message.contains("tectonic region type"));
    }

    /** Runs validation, expecting it to fail, and returns the failure message. */
    private static String validationFailure(JointHazardInput input) {
        try {
            input.validate();
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
                JointHazardInput.jointMagForRupture(rupSet, JOINT_RUP),
                1e-6);
    }

    /** A solution with crustal and interface ruptures but no joint ruptures validates per TRT. */
    @Test
    public void testPerTectonicRegionValidate() {
        ValidationResult result =
                JointHazardInput.perTectonicRegion(makeMixedSolution()).validate();
        assertEquals(1, result.numCrustal);
        assertEquals(1, result.numInterface);
        assertEquals(0, result.numJoint);
        assertFalse(result.isJoint());
    }

    /** Two solutions calculated together validate as one merged solution. */
    @Test
    public void testCombinedValidate() {
        JointHazardInput input =
                JointHazardInput.combined(makeCrustalSolution(), makeSubductionSolution());
        assertEquals(GmmMode.PER_TECTONIC_REGION, input.getGmmMode());

        ValidationResult result = input.validate();
        assertEquals(1, result.numCrustal);
        assertEquals(1, result.numInterface);
        assertEquals(0, result.numJoint);
    }

    /** More than two solutions can be calculated together as well. */
    @Test
    public void testCombinedValidateThreeSolutions() {
        ValidationResult result =
                JointHazardInput.combined(
                                makeCrustalSolution(),
                                makeSubductionSolution(),
                                makeSubductionSolution())
                        .validate();
        assertEquals(1, result.numCrustal);
        assertEquals(2, result.numInterface);
        assertEquals(0, result.numJoint);
    }

    /** A single solution is passed through unmerged, so combined is then per-TRT mode. */
    @Test
    public void testCombinedSingleSolution() {
        JointHazardInput input = JointHazardInput.combined(makeMixedSolution());
        assertEquals(GmmMode.PER_TECTONIC_REGION, input.getGmmMode());

        ValidationResult result = input.validate();
        assertEquals(1, result.numCrustal);
        assertEquals(1, result.numInterface);
    }

    /**
     * Per-TRT mode cannot calculate a joint rupture: it spans both region types, so neither the
     * crustal nor the interface GMM is right for it.
     */
    @Test
    public void testPerTectonicRegionRejectsJointRuptures() {
        String message = validationFailure(JointHazardInput.perTectonicRegion(makeSolution()));
        assertTrue(message, message.contains("JOINT_RUPTURE"));
    }

    /** Inputs are frozen once a calculation has been set up on top of them. */
    @Test
    public void testInputsAreLockedBySetup() {
        JointHazardInput input = new JointHazardInput(makeSolution());
        new JointHazardCalcSetup(input);
        try {
            input.setSpacing(0.5);
            fail("expected the inputs to be locked");
        } catch (IllegalStateException expected) {
            // as expected
        }
    }

    /**
     * The defaults implement the requested specification: all of New Zealand at 0.1 degrees, PGA
     * and SA(1.0), curves at the nzshm-common NZ locations. Return periods (2% and 10% in 50 years)
     * come from {@link SolHazardMapCalc#MAP_RPS}.
     */
    @Test
    public void testDefaults() {
        assertEquals(0.1, JointHazardInput.DEFAULT_SPACING, 1e-9);
        assertArrayEquals(new double[] {0d, 1d}, JointHazardInput.DEFAULT_PERIODS, 1e-9);
        assertEquals(NzshmCommonLocations.nzLocations(), JointHazardInput.defaultSites());

        assertEquals(
                Set.of(ReturnPeriods.TWO_IN_50, ReturnPeriods.TEN_IN_50),
                Set.of(SolHazardMapCalc.MAP_RPS));

        // the default region covers the country at 0.1 degrees
        GriddedRegion region = new JointHazardInput(makeSolution()).getRegion();
        assertEquals(0.1, region.getSpacing(), 1e-9);
        for (Location location : NzshmCommonLocations.nzLocations().values()) {
            assertTrue(
                    "region should contain " + location,
                    region.contains(location) || region.distanceToLocation(location) < 20);
        }
    }
}

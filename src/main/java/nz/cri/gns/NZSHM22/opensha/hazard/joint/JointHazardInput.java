package nz.cri.gns.NZSHM22.opensha.hazard.joint;

import com.google.common.base.Preconditions;
import java.util.List;
import java.util.Map;
import nz.cri.gns.NZSHM22.opensha.data.location.NzshmCommonLocations;
import nz.cri.gns.NZSHM22.opensha.data.region.NewZealandRegions;
import nz.cri.gns.NZSHM22.opensha.griddedSeismicity.NZSHM22_GriddedData;
import org.opensha.commons.geo.BorderType;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.faultSurface.CompoundSurface;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.imr.attenRelImpl.JointRuptureExperimentalIMR;
import org.opensha.sha.util.TectonicRegionType;

/**
 * The inputs of a joint (crustal + subduction) hazard calculation: the solution, the region, the
 * periods and the thread count, plus the validation that checks the solution against the
 * assumptions of {@link JointRuptureExperimentalIMR}.
 *
 * <p>The experimental GMM splits every rupture surface into its crustal and its interface parts,
 * evaluates a crustal and an interface GMM separately for the two sub-ruptures, and combines the
 * two ground motions (SRSS of the medians, energy-weighted sigma). For that to be meaningful the
 * solution has to satisfy two assumptions, both checked by {@link #validate()}:
 *
 * <ol>
 *   <li>every fault section carries a tectonic region type of either {@link
 *       TectonicRegionType#ACTIVE_SHALLOW} or {@link TectonicRegionType#SUBDUCTION_INTERFACE}, and
 *   <li>rupture magnitudes follow the same area scaling the GMM assumes, i.e. {@code
 *       log10(crustalArea*10^4.2 + interfaceArea*10^4.0)}. That is the scaling of {@code
 *       EstimatedJointScalingRelationship} (config {@code scalingRelationshipName:
 *       "JOIN_ESTIMATE"}). The GMM itself bails out with an exception mid-calculation when a joint
 *       rupture magnitude disagrees by more than 5%, so it is worth checking up front.
 * </ol>
 *
 * <p>Those two assumptions only apply to {@link GmmMode#JOINT_RUPTURE}. In {@link
 * GmmMode#PER_TECTONIC_REGION} — crustal and subduction solutions calculated together, see {@link
 * #combined}, or a single solution holding both kinds of rupture, see {@link #perTectonicRegion} —
 * each source is calculated with the GMM for its own tectonic region type, so no joint area scaling
 * is involved and joint ruptures are rejected instead.
 *
 * <p>Once a {@link JointHazardCalcSetup} has been built on top of these inputs they are locked and
 * the setters throw.
 */
public class JointHazardInput {

    /** Classification of a rupture by the tectonic region types of its sections. */
    public enum RuptureType {
        CRUSTAL,
        INTERFACE,
        JOINT
    }

    /** How ground motions are calculated. */
    public enum GmmMode {
        /**
         * A single {@link JointRuptureExperimentalIMR} for every source, which splits joint
         * ruptures into their crustal and interface parts internally. The only mode that can handle
         * ruptures spanning both tectonic region types.
         */
        JOINT_RUPTURE,
        /**
         * A crustal GMM for crustal sources and an interface GMM for interface sources, dispatched
         * by the source's tectonic region type. Use this for a crustal and a subduction solution
         * calculated together, or for a single solution holding both kinds of rupture. Cannot
         * handle joint ruptures.
         */
        PER_TECTONIC_REGION
    }

    /** Default map resolution in degrees, i.e. roughly 10km. */
    public static final double DEFAULT_SPACING = NZSHM22_GriddedData.GRID_SPACING;

    /** Default calculation periods: PGA and 1s SA. */
    public static final double[] DEFAULT_PERIODS = {0d, 1d};

    /**
     * Sites that hazard curves are calculated for: the nzshm-common "NZ" locations. See {@link
     * NzshmCommonLocations}.
     */
    public static Map<String, Location> defaultSites() {
        return NzshmCommonLocations.nzLocations();
    }

    /**
     * Largest fractional magnitude difference between the solution and the GMM's joint scaling that
     * we accept. Matches the tolerance hard-coded in {@link JointRuptureExperimentalIMR}.
     */
    public static final double MAX_FRACTIONAL_MAG_DIFF = 0.05;

    private final FaultSystemSolution solution;

    private GmmMode gmmMode = GmmMode.JOINT_RUPTURE;
    private GriddedRegion region;
    private double spacing = DEFAULT_SPACING;
    private double[] periods = DEFAULT_PERIODS;
    private int numThreads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);

    private boolean locked = false;

    public JointHazardInput(FaultSystemSolution solution) {
        this.solution = solution;
    }

    /**
     * Inputs for calculating any number of solutions together, typically a crustal and one or more
     * subduction solutions. They are merged into a single solution, and so into a single ERF, where
     * the crustal sources are calculated with a crustal GMM and the interface sources with an
     * interface GMM. See {@link JointSolutions#merge} for what the merge does and does not
     * preserve.
     *
     * <p>A single solution is passed through unmerged, which makes this equivalent to {@link
     * #perTectonicRegion}.
     *
     * @throws IllegalArgumentException if no solution is given
     */
    public static JointHazardInput combined(FaultSystemSolution... solutions) {
        return new JointHazardInput(JointSolutions.merge(solutions))
                .setGmmMode(GmmMode.PER_TECTONIC_REGION);
    }

    /**
     * Inputs for a single solution that holds both crustal and subduction ruptures but no joint
     * ruptures. Each rupture is calculated with the GMM for its own tectonic region type.
     */
    public static JointHazardInput perTectonicRegion(FaultSystemSolution solution) {
        return new JointHazardInput(solution).setGmmMode(GmmMode.PER_TECTONIC_REGION);
    }

    public FaultSystemSolution getSolution() {
        return solution;
    }

    /** Sets how ground motions are calculated. Defaults to {@link GmmMode#JOINT_RUPTURE}. */
    public JointHazardInput setGmmMode(GmmMode gmmMode) {
        checkNotLocked();
        this.gmmMode = gmmMode;
        return this;
    }

    public GmmMode getGmmMode() {
        return gmmMode;
    }

    /** Sets the gridded region that the map is calculated over. Overrides {@link #setSpacing}. */
    public JointHazardInput setRegion(GriddedRegion region) {
        checkNotLocked();
        this.region = region;
        return this;
    }

    /** Sets the map resolution in degrees. Ignored if a region has been set explicitly. */
    public JointHazardInput setSpacing(double spacing) {
        checkNotLocked();
        Preconditions.checkArgument(spacing > 0, "spacing must be positive");
        this.spacing = spacing;
        return this;
    }

    /** Sets the calculation periods. 0 is PGA, -1 is PGV, positive values are SA periods. */
    public JointHazardInput setPeriods(double... periods) {
        checkNotLocked();
        Preconditions.checkArgument(periods.length > 0, "need at least one period");
        this.periods = periods;
        return this;
    }

    public JointHazardInput setNumThreads(int numThreads) {
        checkNotLocked();
        Preconditions.checkArgument(numThreads > 0, "numThreads must be positive");
        this.numThreads = numThreads;
        return this;
    }

    public double[] getPeriods() {
        return periods;
    }

    public int getNumThreads() {
        return numThreads;
    }

    /**
     * The gridded region the map is calculated over. Defaults to {@link NewZealandRegions.NZ_TEST},
     * the NZSHM22 region covering all of New Zealand, at {@link #setSpacing} resolution. Pass
     * {@link NewZealandRegions.NZ_RECTANGLE} to {@link #setRegion} instead for the full NZ
     * graticule, which also covers the open ocean around the country and is six times as many sites
     * at 0.1 degrees.
     */
    public GriddedRegion getRegion() {
        if (region == null) {
            region =
                    new GriddedRegion(
                            new NewZealandRegions.NZ_TEST().getBorder(),
                            BorderType.MERCATOR_LINEAR,
                            spacing,
                            GriddedRegion.ANCHOR_0_0);
        }
        return region;
    }

    /**
     * Freezes the inputs so that the setters throw. Called by {@link JointHazardCalcSetup} when a
     * calculation is set up on top of them, because changing the region or the periods afterwards
     * would not reach the calculator that has already been built.
     */
    public void lock() {
        locked = true;
    }

    protected void checkNotLocked() {
        Preconditions.checkState(!locked, "calculation has already been set up");
    }

    /** Classifies a rupture by the tectonic region types of the sections it uses. */
    public static RuptureType typeOf(FaultSystemRupSet rupSet, int rupIndex) {
        boolean crustal = false;
        boolean interfce = false;
        for (int sectIndex : rupSet.getSectionsIndicesForRup(rupIndex)) {
            if (tectonicRegionType(rupSet.getFaultSectionData(sectIndex))
                    == TectonicRegionType.ACTIVE_SHALLOW) {
                crustal = true;
            } else {
                interfce = true;
            }
        }
        if (crustal && interfce) {
            return RuptureType.JOINT;
        }
        return crustal ? RuptureType.CRUSTAL : RuptureType.INTERFACE;
    }

    /**
     * Whether a tectonic region type is one the joint hazard calculation can work with, i.e. {@link
     * TectonicRegionType#ACTIVE_SHALLOW} or {@link TectonicRegionType#SUBDUCTION_INTERFACE}. A null
     * type, which is what a rupture set saved before fault section properties existed carries, is
     * not.
     */
    public static boolean isSupported(TectonicRegionType trt) {
        return trt == TectonicRegionType.ACTIVE_SHALLOW
                || trt == TectonicRegionType.SUBDUCTION_INTERFACE;
    }

    /**
     * The tectonic region type of a section, checked against {@link #isSupported}.
     *
     * @throws IllegalStateException if the section carries a type the calculation cannot use
     */
    protected static TectonicRegionType tectonicRegionType(FaultSection section) {
        TectonicRegionType trt = section.getTectonicRegionType();
        Preconditions.checkState(
                isSupported(trt),
                "Section %s (%s) has tectonic region type %s; the joint GMM only supports"
                        + " ACTIVE_SHALLOW and SUBDUCTION_INTERFACE. Rupture sets built before"
                        + " tectonic region types were written out can be fixed with"
                        + " RupSetPropertyBackfill.",
                section.getSectionId(),
                section.getSectionName(),
                trt);
        return trt;
    }

    /** Summary of {@link #validate()}. */
    public static class ValidationResult {
        public final int numCrustal;
        public final int numInterface;
        public final int numJoint;
        public final int numJointWithRate;

        /** Largest |calculated - solution| joint magnitude difference found. */
        public final double maxJointMagDiff;

        /** Index of the rupture with the largest magnitude difference, or -1. */
        public final int worstJointRupture;

        /**
         * Number of single-section ruptures with a non-zero rate. Those reach the joint GMM as a
         * plain surface rather than a compound one, and it then has to infer whether they are
         * crustal or interface from their magnitude alone. Only a concern in {@link
         * GmmMode#JOINT_RUPTURE}. See {@link #getNumSingleSectionWithRate()}.
         */
        private final int numSingleSectionWithRate;

        protected ValidationResult(
                int numCrustal,
                int numInterface,
                int numJoint,
                int numJointWithRate,
                double maxJointMagDiff,
                int worstJointRupture,
                int numSingleSectionWithRate) {
            this.numCrustal = numCrustal;
            this.numInterface = numInterface;
            this.numJoint = numJoint;
            this.numJointWithRate = numJointWithRate;
            this.maxJointMagDiff = maxJointMagDiff;
            this.worstJointRupture = worstJointRupture;
            this.numSingleSectionWithRate = numSingleSectionWithRate;
        }

        public boolean isJoint() {
            return numJoint > 0;
        }

        /**
         * Number of single-section ruptures that carry a rate and therefore end up in the ERF.
         *
         * <p>A single-section rupture reaches the joint GMM as a plain surface, which carries no
         * section data and so no tectonic region types. The GMM falls back to classifying it by
         * magnitude: it compares the rupture's magnitude against both {@code
         * JointRuptureExperimentalIMR.getCrustalMag} ({@code log10(area) + 4.2}) and {@code
         * getInterfaceMag} ({@code log10(area) + 4.0}) and picks whichever is closer.
         *
         * <p>That is only a guess. The two scalings differ by 0.2 magnitude units, which is small
         * against the scatter of real scaling relationships, so a rupture whose magnitude sits near
         * the midpoint can be classified either way and then be calculated with the wrong component
         * GMM. In {@link GmmMode#JOINT_RUPTURE} a non-zero count here is therefore worth checking.
         * In {@link GmmMode#PER_TECTONIC_REGION} it does not matter: the GMM is picked from the
         * rupture's tectonic region type, not from its surface.
         */
        public int getNumSingleSectionWithRate() {
            return numSingleSectionWithRate;
        }

        @Override
        public String toString() {
            return "crustal ruptures: "
                    + numCrustal
                    + ", interface ruptures: "
                    + numInterface
                    + ", joint ruptures: "
                    + numJoint
                    + " ("
                    + numJointWithRate
                    + " with a non-zero rate), max joint magnitude difference: "
                    + (float) maxJointMagDiff
                    + (worstJointRupture < 0 ? "" : " at rupture " + worstJointRupture)
                    + ", single-section ruptures with a rate: "
                    + numSingleSectionWithRate;
        }
    }

    /**
     * Checks that the solution matches the assumptions of the calculation and returns a summary of
     * its rupture composition.
     *
     * <p>Both modes require every section to carry a supported tectonic region type. {@link
     * GmmMode#JOINT_RUPTURE} additionally checks joint rupture magnitudes against the area scaling
     * the joint GMM assumes; {@link GmmMode#PER_TECTONIC_REGION} instead rejects joint ruptures
     * outright, because a rupture spanning both region types has no single GMM to be calculated
     * with.
     *
     * @throws IllegalStateException if a section has an unsupported tectonic region type, if a
     *     joint rupture magnitude disagrees with the GMM's joint area scaling, or if a joint
     *     rupture is found in {@link GmmMode#PER_TECTONIC_REGION}.
     */
    public ValidationResult validate() {
        FaultSystemRupSet rupSet = solution.getRupSet();

        // touches every section, so this also validates the tectonic region types
        for (int s = 0; s < rupSet.getNumSections(); s++) {
            tectonicRegionType(rupSet.getFaultSectionData(s));
        }

        int numCrustal = 0;
        int numInterface = 0;
        int numJoint = 0;
        int numJointWithRate = 0;
        double maxMagDiff = 0;
        int worst = -1;
        int numSingleSectionWithRate = 0;

        for (int r = 0; r < rupSet.getNumRuptures(); r++) {
            if (rupSet.getSectionsIndicesForRup(r).size() == 1 && solution.getRateForRup(r) > 0) {
                numSingleSectionWithRate++;
            }
            RuptureType type = typeOf(rupSet, r);
            if (type == RuptureType.CRUSTAL) {
                numCrustal++;
                continue;
            }
            if (type == RuptureType.INTERFACE) {
                numInterface++;
                continue;
            }
            numJoint++;
            if (solution.getRateForRup(r) > 0) {
                numJointWithRate++;
            }

            Preconditions.checkState(
                    gmmMode == GmmMode.JOINT_RUPTURE,
                    "Rupture %s spans crustal and interface sections, which"
                            + " GmmMode.PER_TECTONIC_REGION cannot calculate: the rupture has no"
                            + " single tectonic region type and so no single GMM. Use"
                            + " GmmMode.JOINT_RUPTURE for solutions with joint ruptures.",
                    r);

            double solutionMag = rupSet.getMagForRup(r);
            double jointMag = jointMagForRupture(rupSet, r);
            double magDiff = Math.abs(jointMag - solutionMag);
            if (magDiff > maxMagDiff) {
                maxMagDiff = magDiff;
                worst = r;
            }
            Preconditions.checkState(
                    magDiff / solutionMag < MAX_FRACTIONAL_MAG_DIFF,
                    "Rupture %s has magnitude %s, but the joint area scaling assumed by the GMM"
                            + " gives %s. The GMM would refuse to calculate this rupture. Was the"
                            + " solution built with a different scaling relationship than"
                            + " JOIN_ESTIMATE?",
                    r,
                    solutionMag,
                    jointMag);
        }

        return new ValidationResult(
                numCrustal,
                numInterface,
                numJoint,
                numJointWithRate,
                maxMagDiff,
                worst,
                numSingleSectionWithRate);
    }

    /**
     * The magnitude the joint GMM's area scaling gives for a rupture, calculated the same way the
     * GMM does it: from the areas of the crustal and the interface parts of the rupture surface.
     */
    public static double jointMagForRupture(FaultSystemRupSet rupSet, int rupIndex) {
        RuptureSurface surface = rupSet.getSurfaceForRupture(rupIndex, 1d);
        Preconditions.checkState(
                surface instanceof CompoundSurface,
                "Rupture %s is not made up of multiple sections, so it cannot be joint",
                rupIndex);
        CompoundSurface compound = (CompoundSurface) surface;
        List<? extends RuptureSurface> surfaces = compound.getSurfaceList();
        List<? extends FaultSection> sections = compound.getSectionsList();
        Preconditions.checkNotNull(
                sections, "Rupture %s has a compound surface without section data", rupIndex);

        double crustalArea = 0;
        double interfaceArea = 0;
        for (int i = 0; i < surfaces.size(); i++) {
            if (tectonicRegionType(sections.get(i)) == TectonicRegionType.ACTIVE_SHALLOW) {
                crustalArea += surfaces.get(i).getArea();
            } else {
                interfaceArea += surfaces.get(i).getArea();
            }
        }
        return JointRuptureExperimentalIMR.getJointMag(crustalArea, interfaceArea);
    }
}

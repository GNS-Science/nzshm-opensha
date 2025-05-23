package nz.cri.gns.NZSHM22.opensha.inversion;

import com.google.common.base.Preconditions;
import java.io.IOException;
import java.util.*;
import nz.cri.gns.NZSHM22.opensha.analysis.NZSHM22_FaultSystemRupSetCalc;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionInputGenerator;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.ConstraintWeightingType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.*;
import org.opensha.sha.faultSurface.FaultSection;
import scratch.UCERF3.utils.U3SectionMFD_constraint;
import scratch.UCERF3.utils.aveSlip.U3AveSlipConstraint;
import scratch.UCERF3.utils.paleoRateConstraints.UCERF3_PaleoProbabilityModel;

/**
 * This class is used to generate inversion inputs (A/A_ineq matrices, d/d_ineq vectors) for a given
 * rupture set, inversion configuration, paleo rate constraints, improbability constraint, and paleo
 * probability model. It can also save these inputs to a zip file to be run on high performance
 * computing.
 */
public class NZSHM22_CrustalInversionInputGenerator extends InversionInputGenerator {

    private static final boolean D = false;
    /**
     * this enables use of the getQuick and setQuick methods on the sparse matrices. this comes with
     * a performance boost, but disables range checks and is more prone to errors.
     */
    private static final boolean QUICK_GETS_SETS = true;

    // inputs
    private NZSHM22_InversionFaultSystemRuptSet rupSet;
    private AbstractInversionConfiguration config;
    private List<UncertainDataConstraint.SectMappedUncertainDataConstraint> paleoRateConstraints;
    private List<U3AveSlipConstraint> aveSlipConstraints;
    private double[] improbabilityConstraint;
    private PaleoProbabilityModel paleoProbabilityModel;

    public NZSHM22_CrustalInversionInputGenerator(
            NZSHM22_InversionFaultSystemRuptSet rupSet,
            NZSHM22_CrustalInversionConfiguration config,
            List<UncertainDataConstraint.SectMappedUncertainDataConstraint> paleoRateConstraints,
            List<U3AveSlipConstraint> aveSlipConstraints,
            double[] improbabilityConstraint, // may become an object in the future
            PaleoProbabilityModel paleoProbabilityModel) {
        super(
                rupSet,
                buildConstraints(
                        rupSet,
                        config,
                        paleoRateConstraints,
                        aveSlipConstraints,
                        paleoProbabilityModel),
                config.getInitialRupModel(),
                buildWaterLevel(config, rupSet));
        this.rupSet = rupSet;
        this.config = config;
        this.paleoRateConstraints = paleoRateConstraints;
        this.improbabilityConstraint = improbabilityConstraint;
        this.aveSlipConstraints = aveSlipConstraints;
        this.paleoProbabilityModel = paleoProbabilityModel;
    }

    private static PaleoProbabilityModel defaultProbModel = null;

    /**
     * Loads the default paleo probability model for UCERF3 (Glenn's file). Can be turned into an
     * enum if we get alternatives
     *
     * @return
     * @throws IOException
     */
    public static PaleoProbabilityModel loadDefaultPaleoProbabilityModel() throws IOException {
        if (defaultProbModel == null) defaultProbModel = UCERF3_PaleoProbabilityModel.load();
        return defaultProbModel;
    }

    private static List<InversionConstraint> buildConstraints(
            NZSHM22_InversionFaultSystemRuptSet rupSet,
            NZSHM22_CrustalInversionConfiguration config,
            List<UncertainDataConstraint.SectMappedUncertainDataConstraint> paleoRateConstraints,
            List<U3AveSlipConstraint> aveSlipConstraints,
            PaleoProbabilityModel paleoProbabilityModel) {

        System.out.println("buildConstraints");
        System.out.println("================");

        System.out.println(
                "config.getSlipRateWeightingType(): " + config.getSlipRateWeightingType());
        if (config.getSlipRateWeightingType()
                == AbstractInversionConfiguration.NZSlipRateConstraintWeightingType
                        .NORMALIZED_BY_UNCERTAINTY) {
            System.out.println(
                    "config.getSlipRateUncertaintyConstraintWt() :"
                            + config.getSlipRateUncertaintyConstraintWt());
            System.out.println(
                    "config.getSlipRateUncertaintyConstraintScalingFactor() :"
                            + config.getSlipRateUncertaintyConstraintScalingFactor());
        } else {
            System.out.println(
                    "config.getSlipRateConstraintWt_normalized(): "
                            + config.getSlipRateConstraintWt_normalized());
            System.out.println(
                    "config.getSlipRateConstraintWt_unnormalized(): "
                            + config.getSlipRateConstraintWt_unnormalized());
        }
        System.out.println(
                "config.getMinimizationConstraintWt(): " + config.getMinimizationConstraintWt());
        System.out.println(
                "config.getMagnitudeEqualityConstraintWt(): "
                        + config.getMagnitudeEqualityConstraintWt());
        System.out.println(
                "config.getMagnitudeInequalityConstraintWt(): "
                        + config.getMagnitudeInequalityConstraintWt());
        System.out.println(
                "config.getNucleationMFDConstraintWt():" + config.getNucleationMFDConstraintWt());

        // builds constraint instances
        List<InversionConstraint> constraints = new ArrayList<>();

        if (config.getSlipRateWeightingType()
                == AbstractInversionConfiguration.NZSlipRateConstraintWeightingType
                        .NORMALIZED_BY_UNCERTAINTY) {
            constraints.add(
                    NZSHM22_SlipRateInversionConstraintBuilder.buildUncertaintyConstraint(
                            config.getSlipRateUncertaintyConstraintWt(),
                            rupSet,
                            config.getSlipRateUncertaintyConstraintScalingFactor(),
                            config.getUnmodifiedSlipRateStdvs()));
        } else {
            if (config.getSlipRateConstraintWt_normalized() > 0d
                    && (config.getSlipRateWeightingType()
                                    == AbstractInversionConfiguration
                                            .NZSlipRateConstraintWeightingType.NORMALIZED
                            || config.getSlipRateWeightingType()
                                    == AbstractInversionConfiguration
                                            .NZSlipRateConstraintWeightingType.BOTH)) {
                constraints.add(
                        new SlipRateInversionConstraint(
                                config.getSlipRateConstraintWt_normalized(),
                                ConstraintWeightingType.NORMALIZED,
                                rupSet));
            }

            if (config.getSlipRateConstraintWt_unnormalized() > 0d
                    && (config.getSlipRateWeightingType()
                                    == AbstractInversionConfiguration
                                            .NZSlipRateConstraintWeightingType.UNNORMALIZED
                            || config.getSlipRateWeightingType()
                                    == AbstractInversionConfiguration
                                            .NZSlipRateConstraintWeightingType.BOTH)) {
                constraints.add(
                        new SlipRateInversionConstraint(
                                config.getSlipRateConstraintWt_unnormalized(),
                                ConstraintWeightingType.UNNORMALIZED,
                                rupSet));
            }
        }

        if (config.getPaleoRateConstraintWt() > 0) {
            constraints.add(
                    new PaleoRateInversionConstraint(
                            rupSet,
                            config.getPaleoRateConstraintWt(),
                            paleoRateConstraints,
                            paleoProbabilityModel));

            if (config.getpaleoParentRateSmoothnessConstraintWeight() > 0) {
                HashSet<Integer> paleoParentIDs = new HashSet();
                for (UncertainDataConstraint.SectMappedUncertainDataConstraint constraint :
                        paleoRateConstraints) {
                    paleoParentIDs.add(
                            rupSet.getFaultSectionDataList()
                                    .get(constraint.sectionIndex)
                                    .getParentSectionId());
                }
                constraints.add(
                        new LaplacianSmoothingInversionConstraint(
                                rupSet,
                                config.getpaleoParentRateSmoothnessConstraintWeight(),
                                paleoParentIDs));
            }
        }

        //        if (config.getPaleoSlipConstraintWt() > 0d)
        //            constraints.add(new PaleoSlipInversionConstraint(rupSet,
        // config.getPaleoSlipConstraintWt(),
        //                    aveSlipConstraints, sectSlipRateReduced));
        ////
        //        if (config.getRupRateConstraintWt() > 0d) {
        //            // This is the RupRateConstraintWt for ruptures not in UCERF2
        //            double zeroRupRateConstraintWt = 0;
        //            if (config.isAPrioriConstraintForZeroRates())
        //                zeroRupRateConstraintWt =
        // config.getRupRateConstraintWt()*config.getAPrioriConstraintForZeroRatesWtFactor();
        //            constraints.add(new
        // APrioriInversionConstraint(config.getRupRateConstraintWt(),
        // zeroRupRateConstraintWt, config.getA_PrioriRupConstraint()));
        //        }

        //        // This constrains rates of ruptures that differ by only 1 subsection
        //        if (config.getRupRateSmoothingConstraintWt() > 0)
        //            constraints.add(new
        // RupRateSmoothingInversionConstraint(config.getRupRateSmoothingConstraintWt(), rupSet));
        //

        // Rupture rate minimization constraint
        // Minimize the rates of ruptures below SectMinMag (strongly so that they have
        // zero rates)
        if (config.getMinimizationConstraintWt() > 0.0) {
            List<Integer> belowMinIndexes = new ArrayList<>();
            for (int r = 0; r < rupSet.getNumRuptures(); r++)
                if (rupSet.isRuptureBelowSectMinMag(r)) belowMinIndexes.add(r);
            constraints.add(
                    new RupRateMinimizationConstraint(
                            config.getMinimizationConstraintWt(), belowMinIndexes));
        }

        // Constrain Solution MFD to equal the Target MFD
        // This is for equality constraints only -- inequality constraints must be
        // encoded into the A_ineq matrix instead since they are nonlinear
        if (config.getMagnitudeEqualityConstraintWt() > 0.0) {
            constraints.add(
                    new MFDInversionConstraint(
                            rupSet,
                            config.getMagnitudeEqualityConstraintWt(),
                            false,
                            config.getMfdEqualityConstraints()));
        }

        // Prepare MFD Inequality Constraint (not added to A matrix directly since it's
        // nonlinear)
        if (config.getMagnitudeInequalityConstraintWt() > 0.0) {
            constraints.add(
                    new MFDInversionConstraint(
                            rupSet,
                            config.getMagnitudeInequalityConstraintWt(),
                            true,
                            config.getMfdInequalityConstraints()));
        }

        // Prepare MFD Uncertainty Weighted Constraint
        if (config.getMagnitudeUncertaintyWeightedConstraintWt() > 0.0)
            constraints.add(
                    new MFDInversionConstraint(
                            rupSet,
                            config.getMagnitudeUncertaintyWeightedConstraintWt(),
                            false,
                            ConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY,
                            config.getMfdUncertaintyWeightedConstraints()));

        //        // MFD Smoothness Constraint - Constrain participation MFD to be uniform for each
        // fault
        // subsection
        //        if (config.getParticipationSmoothnessConstraintWt() > 0.0)
        //            constraints.add(new MFDParticipationSmoothnessInversionConstraint(rupSet,
        //                    config.getParticipationSmoothnessConstraintWt(),
        // config.getParticipationConstraintMagBinSize()));

        // MFD Subsection nucleation MFD constraint
        ArrayList<U3SectionMFD_constraint> MFDConstraints = null;
        if (config.getNucleationMFDConstraintWt() > 0.0) {
            MFDConstraints =
                    NZSHM22_FaultSystemRupSetCalc.getCharInversionSectMFD_Constraints(rupSet);
            constraints.add(
                    new U3MFDSubSectNuclInversionConstraint(
                            rupSet, config.getNucleationMFDConstraintWt(), MFDConstraints));
        }

        //        // MFD Smoothing constraint - MFDs spatially smooth along adjacent subsections on
        // a
        // parent section (Laplacian smoothing)
        //        if (config.getMFDSmoothnessConstraintWt() > 0.0 ||
        // config.getMFDSmoothnessConstraintWtForPaleoParents() > 0.0) {
        //            if (MFDConstraints == null)
        //                MFDConstraints =
        // FaultSystemRupSetCalc.getCharInversionSectMFD_Constraints(rupSet);
        //
        //            HashSet<Integer> paleoParentIDs = new HashSet<>();
        //            // Get list of parent IDs that have a paleo data point (paleo event rate or
        // paleo mean
        // slip)
        //            if (config.getPaleoRateConstraintWt() > 0.0) {
        //                for (int i=0; i<paleoRateConstraints.size(); i++) {
        //                    int paleoParentID =
        // rupSet.getFaultSectionDataList().get(paleoRateConstraints.get(i).getSectionIndex()).getParentSectionId();
        //                    paleoParentIDs.add(paleoParentID);
        //                }
        //            }

        //            if (config.getPaleoSlipConstraintWt() > 0.0) {
        //                for (int i=0; i<aveSlipConstraints.size(); i++) {
        //                    int paleoParentID =
        // rupSet.getFaultSectionDataList().get(aveSlipConstraints.get(i).getSubSectionIndex()).getParentSectionId();
        //                    paleoParentIDs.add(paleoParentID);
        //                }
        //            }
        //
        //            constraints.add(new MFDLaplacianSmoothingInversionConstraint(rupSet,
        // config.getMFDSmoothnessConstraintWt(),
        //                    config.getMFDSmoothnessConstraintWtForPaleoParents(), paleoParentIDs,
        // MFDConstraints));
        //        }

        //        // Constraint solution moment to equal deformation-model moment
        //        if (config.getMomentConstraintWt() > 0.0)
        //            constraints.add(new TotalMomentInversionConstraint(rupSet,
        // config.getMomentConstraintWt(), rupSet.getTotalReducedMomentRate()));
        //

        //        // Constrain paleoseismically-visible event rates along parent sections to be
        // smooth
        //        if (config.getEventRateSmoothnessWt() > 0.0)
        //            constraints.add(new PaleoVisibleEventRateSmoothnessInversionConstraint(rupSet,
        // config.getEventRateSmoothnessWt(), paleoProbabilityModel));

        return constraints;
    }

    private static double[] buildWaterLevel(
            NZSHM22_CrustalInversionConfiguration config, FaultSystemRupSet rupSet) {
        double minimumRuptureRateFraction = config.getMinimumRuptureRateFraction();
        if (minimumRuptureRateFraction > 0) {
            // set up minimum rupture rates (water level)
            double[] minimumRuptureRateBasis = config.getMinimumRuptureRateBasis();
            Preconditions.checkNotNull(
                    minimumRuptureRateBasis,
                    "minimum rate fraction specified but no minimum rate basis given!");

            // first check to make sure that they're not all zeros
            boolean allZeros = true;
            int numRuptures = rupSet.getNumRuptures();
            for (int i = 0; i < numRuptures; i++) {
                if (minimumRuptureRateBasis[i] > 0) {
                    allZeros = false;
                    break;
                }
            }
            Preconditions.checkState(
                    !allZeros, "cannot set water level when water level rates are all zero!");

            double[] minimumRuptureRates = new double[numRuptures];
            for (int i = 0; i < numRuptures; i++)
                minimumRuptureRates[i] = minimumRuptureRateBasis[i] * minimumRuptureRateFraction;
            return minimumRuptureRates;
        }
        return null;
    }

    public void generateInputs() {
        generateInputs(null, D);
    }

    /**
     * This returns the normalized distance along a rupture that a paleoseismic trench is located
     * (Glenn's x/L). It is between 0 and 0.5. This currently puts the trench in the middle of the
     * subsection. We need this for the UCERF3 probability of detecting a rupture in a trench.
     *
     * @return
     */
    public static double getDistanceAlongRupture(
            List<FaultSection> sectsInRup, int targetSectIndex) {
        return getDistanceAlongRupture(sectsInRup, targetSectIndex, null);
    }

    public static double getDistanceAlongRupture(
            List<FaultSection> sectsInRup,
            int targetSectIndex,
            Map<Integer, Double> traceLengthCache) {
        double distanceAlongRup = 0;

        double totalLength = 0;
        double lengthToRup = 0;
        boolean reachConstraintLoc = false;

        // Find total length (km) of fault trace and length (km) from one end to the
        // paleo trench location
        for (int i = 0; i < sectsInRup.size(); i++) {
            FaultSection sect = sectsInRup.get(i);
            int sectIndex = sect.getSectionId();
            Double sectLength = null;
            if (traceLengthCache != null) {
                sectLength = traceLengthCache.get(sectIndex);
                if (sectLength == null) {
                    sectLength = sect.getFaultTrace().getTraceLength();
                    traceLengthCache.put(sectIndex, sectLength);
                }
            } else {
                sectLength = sect.getFaultTrace().getTraceLength();
            }
            totalLength += sectLength;
            if (sectIndex == targetSectIndex) {
                reachConstraintLoc = true;
                // We're putting the trench in the middle of the subsection for now
                lengthToRup += sectLength / 2;
            }
            // We haven't yet gotten to the trench subsection so keep adding to lengthToRup
            if (reachConstraintLoc == false) lengthToRup += sectLength;
        }

        if (!reachConstraintLoc) // check to make sure we came across the trench subsection in the
            // rupture
            throw new IllegalStateException(
                    "Paleo site subsection was not included in rupture subsections");

        // Normalized distance along the rainbow (Glenn's x/L) - between 0 and 1
        distanceAlongRup = lengthToRup / totalLength;
        // Adjust to be between 0 and 0.5 (since rainbow is symmetric about 0.5)
        if (distanceAlongRup > 0.5) distanceAlongRup = 1 - distanceAlongRup;

        return distanceAlongRup;
    }

    public AbstractInversionConfiguration getConfig() {
        return config;
    }

    public List<UncertainDataConstraint.SectMappedUncertainDataConstraint>
            getPaleoRateConstraints() {
        return paleoRateConstraints;
    }

    public double[] getImprobabilityConstraint() {
        return improbabilityConstraint;
    }

    public PaleoProbabilityModel getPaleoProbabilityModel() {
        return paleoProbabilityModel;
    }
}

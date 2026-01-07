package nz.cri.gns.NZSHM22.opensha.inversion.joint.constraints;

import java.util.ArrayList;
import java.util.List;
import nz.cri.gns.NZSHM22.opensha.inversion.AbstractInversionConfiguration;
import nz.cri.gns.NZSHM22.opensha.inversion.MFDManipulation;
import nz.cri.gns.NZSHM22.opensha.inversion.NZSHM22_SlipRateInversionConstraintBuilder;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionConfig;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.ConstraintWeightingType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.MFDInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.SlipRateInversionConstraint;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

public class JointConstraintGenerator {

    public static List<InversionConstraint> buildSlipRateConstraints(
            FaultSystemRupSet rupSet, PartitionConfig config) {
        List<InversionConstraint> constraints = new ArrayList<>();
        if (config.slipRateWeightingType
                == AbstractInversionConfiguration.NZSlipRateConstraintWeightingType
                        .NORMALIZED_BY_UNCERTAINTY) {
            constraints.add(
                    new JointConstraintWrapper(
                            config,
                            NZSHM22_SlipRateInversionConstraintBuilder.buildUncertaintyConstraint(
                                    config.slipRateUncertaintyConstraintWt,
                                    rupSet,
                                    config.slipRateUncertaintyConstraintScalingFactor,
                                    config.unmodifiedSlipRateStdvs)));
        } else {
            if (config.slipRateConstraintWt_normalized > 0d
                    && (config.slipRateWeightingType
                                    == AbstractInversionConfiguration
                                            .NZSlipRateConstraintWeightingType.NORMALIZED
                            || config.slipRateWeightingType
                                    == AbstractInversionConfiguration
                                            .NZSlipRateConstraintWeightingType.BOTH)) {
                constraints.add(
                        new JointConstraintWrapper(
                                config,
                                new SlipRateInversionConstraint(
                                        config.slipRateConstraintWt_normalized,
                                        ConstraintWeightingType.NORMALIZED,
                                        rupSet)));
            }

            if (config.slipRateConstraintWt_unnormalized > 0d
                    && (config.slipRateWeightingType
                                    == AbstractInversionConfiguration
                                            .NZSlipRateConstraintWeightingType.UNNORMALIZED
                            || config.slipRateWeightingType
                                    == AbstractInversionConfiguration
                                            .NZSlipRateConstraintWeightingType.BOTH)) {
                constraints.add(
                        new JointConstraintWrapper(
                                config,
                                new SlipRateInversionConstraint(
                                        config.slipRateConstraintWt_unnormalized,
                                        ConstraintWeightingType.UNNORMALIZED,
                                        rupSet)));
            }
        }
        return constraints;
    }

    public static List<InversionConstraint> buildMfdConstraints(
            FaultSystemRupSet rupSet, PartitionConfig config) {
        List<InversionConstraint> constraints = new ArrayList<>();
        List<IncrementalMagFreqDist> mfdEqualityConstraints = config.mfdConstraints;
        List<IncrementalMagFreqDist> mfdInequalityConstraints = config.mfdConstraints;

        if (config.mfdEqualityConstraintWt > 0.0 && config.mfdInequalityConstraintWt > 0.0) {
            // we have both MFD constraints, apply a transition mag from equality to
            // inequality
            mfdEqualityConstraints =
                    MFDManipulation.restrictMFDConstraintMagRange(
                            config.mfdConstraints,
                            config.mfdConstraints.get(0).getMinX(),
                            config.mfdTransitionMag);
            mfdInequalityConstraints =
                    MFDManipulation.restrictMFDConstraintMagRange(
                            config.mfdConstraints,
                            config.mfdTransitionMag,
                            config.mfdConstraints.get(0).getMaxX());
        }

        // Constrain Solution MFD to equal the Target MFD
        // This is for equality constraints only -- inequality constraints must be
        // encoded into the A_ineq matrix instead since they are nonlinear
        if (config.mfdEqualityConstraintWt > 0.0) {
            constraints.add(
                    new MFDInversionConstraint(
                            rupSet, config.mfdEqualityConstraintWt, false, mfdEqualityConstraints));
        }

        // Prepare MFD Inequality Constraint (not added to A matrix directly since it's
        // nonlinear)
        if (config.mfdInequalityConstraintWt > 0.0) {
            constraints.add(
                    new MFDInversionConstraint(
                            rupSet,
                            config.mfdInequalityConstraintWt,
                            true,
                            mfdInequalityConstraints));
        }

        // Prepare MFD Uncertainty Weighted Constraint
        if (config.mfdUncertaintyWeight > 0.0) {
            constraints.add(
                    new MFDInversionConstraint(
                            rupSet,
                            config.mfdUncertaintyWeight,
                            false,
                            ConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY,
                            config.mfdUncertaintyWeightedConstraints));
        }
        return constraints;
    }

    public static List<InversionConstraint> buildSharedConstraints(
            FaultSystemRupSet rupSet, PartitionConfig config) {

        List<InversionConstraint> constraints = new ArrayList<>();

        if (config.getNumSections() == 0) {
            return constraints;
        }

        constraints.addAll(buildSlipRateConstraints(rupSet, config));

        // TDODO: this is deprecated since '21 but the alternative is not yet used

        //        // Rupture rate minimization constraint
        //        // Minimize the rates of ruptures below SectMinMag (strongly so that they have
        //        // zero rates)
        //        if (config.getMinimizationConstraintWt() > 0.0) {
        //            List<Integer> belowMinIndexes = new ArrayList<>();
        //            for (int r = 0; r < rupSet.getNumRuptures(); r++) {
        //                if (rupSet.isRuptureBelowSectMinMag(r)) {
        //                    belowMinIndexes.add(r);
        //                }
        //            }
        //            constraints.add(
        //                    new RupRateMinimizationConstraint(
        //                            config.getMinimizationConstraintWt(), belowMinIndexes));
        //        }

        constraints.addAll(buildMfdConstraints(rupSet, config));

        return constraints;
    }
}

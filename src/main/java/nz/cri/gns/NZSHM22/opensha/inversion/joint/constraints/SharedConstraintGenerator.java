package nz.cri.gns.NZSHM22.opensha.inversion.joint.constraints;

import static nz.cri.gns.NZSHM22.opensha.inversion.AbstractInversionConfiguration.NZSlipRateConstraintWeightingType.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import nz.cri.gns.NZSHM22.opensha.inversion.MFDManipulation;
import nz.cri.gns.NZSHM22.opensha.inversion.NZSHM22_SlipRateInversionConstraintBuilder;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionConfig;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.ConstraintWeightingType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.MFDInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.SlipRateInversionConstraint;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

/**
 * Specifies how to generate constraints based on a partition configuration. This class takes care
 * of constraints that are relevant for each partition.
 */
public class SharedConstraintGenerator {

    // Setting this flag to true means we calculate joint rupture magnitude separately for crustal
    // and subduction components for MFD constraint encoding.
    // Setting this flag to false means we calculate joint rupture magnitude over the whole rupture
    // for MFD constraint encoding.
    static final boolean SPLIT_RUPSET_MFDS = false;

    public static List<InversionConstraint> buildSlipRateConstraints(PartitionConfig config) {
        List<InversionConstraint> constraints = new ArrayList<>();
        if (config.slipRateWeightingType == NORMALIZED_BY_UNCERTAINTY) {
            constraints.add(
                    NZSHM22_SlipRateInversionConstraintBuilder.buildUncertaintyConstraint(
                            config.slipRateUncertaintyConstraintWt,
                            config.parentConfig.ruptureSet,
                            config.slipRateUncertaintyConstraintScalingFactor,
                            config.unmodifiedSlipRateStdvs));
        } else {
            if (config.slipRateConstraintWt_normalized > 0d
                    && (config.slipRateWeightingType == NORMALIZED
                            || config.slipRateWeightingType == BOTH)) {
                constraints.add(
                        new SlipRateInversionConstraint(
                                config.slipRateConstraintWt_normalized,
                                ConstraintWeightingType.NORMALIZED,
                                config.parentConfig.ruptureSet));
            }

            if (config.slipRateConstraintWt_unnormalized > 0d
                    && (config.slipRateWeightingType == UNNORMALIZED
                            || config.slipRateWeightingType == BOTH)) {
                constraints.add(
                        new SlipRateInversionConstraint(
                                config.slipRateConstraintWt_unnormalized,
                                ConstraintWeightingType.UNNORMALIZED,
                                config.parentConfig.ruptureSet));
            }
        }
        return constraints;
    }

    public static List<InversionConstraint> buildMfdConstraints(PartitionConfig config) {
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
                            config.parentConfig.ruptureSet,
                            config.mfdEqualityConstraintWt,
                            false,
                            mfdEqualityConstraints));
        }

        // Prepare MFD Inequality Constraint (not added to A matrix directly since it's
        // nonlinear)
        if (config.mfdInequalityConstraintWt > 0.0) {
            constraints.add(
                    new MFDInversionConstraint(
                            config.parentConfig.ruptureSet,
                            config.mfdInequalityConstraintWt,
                            true,
                            mfdInequalityConstraints));
        }

        // Prepare MFD Uncertainty Weighted Constraint
        if (config.mfdUncertaintyWeight > 0.0) {
            constraints.add(
                    new MFDInversionConstraint(
                            config.parentConfig.ruptureSet,
                            config.mfdUncertaintyWeight,
                            false,
                            ConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY,
                            config.mfdUncertaintyWeightedConstraints));
        }

        if (SPLIT_RUPSET_MFDS) {
            constraints =
                    constraints.stream()
                            .map(
                                    constraint -> {
                                        constraint.setRuptureSet(config.partitionRuptureSet);
                                        return new FilteredInversionConstraint(
                                                constraint, config.partitionRuptureSet);
                                    })
                            .collect(Collectors.toList());
        }

        return constraints;
    }

    public static List<InversionConstraint> buildSharedConstraints(PartitionConfig config) {

        List<InversionConstraint> constraints = new ArrayList<>();

        if (config.getNumSections() == 0) {
            return constraints;
        }

        constraints.addAll(buildSlipRateConstraints(config));
        constraints.addAll(buildMfdConstraints(config));

        return constraints;
    }
}

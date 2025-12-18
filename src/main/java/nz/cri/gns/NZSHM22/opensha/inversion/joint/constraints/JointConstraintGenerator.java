package nz.cri.gns.NZSHM22.opensha.inversion.joint.constraints;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import nz.cri.gns.NZSHM22.opensha.inversion.AbstractInversionConfiguration;
import nz.cri.gns.NZSHM22.opensha.inversion.NZSHM22_InversionFaultSystemRuptSet;
import nz.cri.gns.NZSHM22.opensha.inversion.NZSHM22_SlipRateInversionConstraintBuilder;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.ConstraintWeightingType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.SlipRateInversionConstraint;

public class JointConstraintGenerator {

    public static List<InversionConstraint> buildSharedConstraints(
            FaultSystemRupSet rupSet, ConstraintConfig config) {

        List<InversionConstraint> constraints = new ArrayList<>();

        if (config.slipRateWeightingType
                == AbstractInversionConfiguration.NZSlipRateConstraintWeightingType
                        .NORMALIZED_BY_UNCERTAINTY) {
            constraints.add(
                    NZSHM22_SlipRateInversionConstraintBuilder.buildUncertaintyConstraint(
                            config.slipRateUncertaintyConstraintWt,
                            rupSet,
                            config.slipRateUncertaintyConstraintScalingFactor,
                            config.unmodifiedSlipRateStdvs));
        } else {
            if (config.slipRateConstraintWt_normalized > 0d
                    && (config.slipRateWeightingType
                                    == AbstractInversionConfiguration
                                            .NZSlipRateConstraintWeightingType.NORMALIZED
                            || config.slipRateWeightingType
                                    == AbstractInversionConfiguration
                                            .NZSlipRateConstraintWeightingType.BOTH)) {
                constraints.add(
                        new SlipRateInversionConstraint(
                                config.slipRateConstraintWt_normalized,
                                ConstraintWeightingType.NORMALIZED,
                                rupSet));
            }

            if (config.slipRateConstraintWt_unnormalized > 0d
                    && (config.slipRateWeightingType
                                    == AbstractInversionConfiguration
                                            .NZSlipRateConstraintWeightingType.UNNORMALIZED
                            || config.slipRateWeightingType
                                    == AbstractInversionConfiguration
                                            .NZSlipRateConstraintWeightingType.BOTH)) {
                constraints.add(
                        new SlipRateInversionConstraint(
                                config.slipRateConstraintWt_unnormalized,
                                ConstraintWeightingType.UNNORMALIZED,
                                rupSet));
            }
        }

        return constraints.stream()
                .map(constraint -> new JointConstraintWrapper(config, constraint))
                .collect(Collectors.toList());
    }
}

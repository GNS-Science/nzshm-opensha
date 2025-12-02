package nz.cri.gns.NZSHM22.opensha.inversion;

import com.google.common.base.Preconditions;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionInputGenerator;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.ConstraintWeightingType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.MFDInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.RupRateMinimizationConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.SlipRateInversionConstraint;

import java.util.ArrayList;
import java.util.List;

public class BaseInversionInputGenerator extends InversionInputGenerator {

    public BaseInversionInputGenerator(FaultSystemRupSet rupSet, List<InversionConstraint> constraints,
                                   double[] initialSolution, double[] waterLevelRates) {
    super(rupSet, constraints, initialSolution, waterLevelRates);
    }

    protected static List<InversionConstraint> buildSharedConstraints(
            NZSHM22_InversionFaultSystemRuptSet rupSet,
            AbstractInversionConfiguration config
    ) {

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

        // WARNING: pre-modular rupture sets have stdev 1000 times too large
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

        // Rupture rate minimization constraint
        // Minimize the rates of ruptures below SectMinMag (strongly so that they have
        // zero rates)
        if (config.getMinimizationConstraintWt() > 0.0) {
            List<Integer> belowMinIndexes = new ArrayList<>();
            for (int r = 0; r < rupSet.getNumRuptures(); r++) {
                if (rupSet.isRuptureBelowSectMinMag(r)) {
                    belowMinIndexes.add(r);
                }
            }
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
        if (config.getMagnitudeUncertaintyWeightedConstraintWt() > 0.0) {
            constraints.add(
                    new MFDInversionConstraint(
                            rupSet,
                            config.getMagnitudeUncertaintyWeightedConstraintWt(),
                            false,
                            ConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY,
                            config.getMfdUncertaintyWeightedConstraints()));
        }

        return constraints;
    }


    protected static double[] buildWaterLevel(
            AbstractInversionConfiguration config, FaultSystemRupSet rupSet) {
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
}

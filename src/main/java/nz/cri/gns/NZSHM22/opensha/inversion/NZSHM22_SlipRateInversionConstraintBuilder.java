package nz.cri.gns.NZSHM22.opensha.inversion;

import com.google.common.base.Preconditions;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.ConstraintWeightingType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.SlipRateInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.modules.AveSlipModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import org.opensha.sha.earthquake.faultSysSolution.modules.SlipAlongRuptureModel;

public class NZSHM22_SlipRateInversionConstraintBuilder {

    /**
     * The coefficient of variance (COV) for a value (rate) and a std deviation.
     *
     * @param rate
     * @param stdDev
     * @return
     */
    public static double coefficientOfVariance(double rate, double stdDev) {
        return rate / stdDev;
    }

    /**
     * Find the min/max COV values across the deformation model.
     *
     * @param targetSlipRates
     * @param targetSlipRateStdDevs
     */
    public static double[] getMinMaxCOV(double[] targetSlipRates, double[] targetSlipRateStdDevs) {
        Preconditions.checkArgument(targetSlipRates.length == targetSlipRateStdDevs.length);
        // max and min coefficient of variance (not normalised)
        double maxCOV = Double.NEGATIVE_INFINITY;
        double minCOV = Double.POSITIVE_INFINITY;
        for (int i = 0; i < targetSlipRates.length; i++) {
            double cov = coefficientOfVariance(targetSlipRates[i], targetSlipRateStdDevs[i]);
            if (Double.isNaN(cov) || Double.isInfinite(cov))
                continue;
            minCOV = Double.min(cov, minCOV);
            maxCOV = Double.max(cov, maxCOV);
        }
        Preconditions.checkState(Double.isFinite(minCOV));
        Preconditions.checkState(Double.isFinite(maxCOV));

        return new double[]{minCOV, maxCOV};
    }

    /**
     * Translate value from one range of values (left) onto another (right)
     * <p>
     * see: https://stackoverflow.com/a/1969274
     */
    public static double translate(double value, double leftMin, double leftMax, double rightMin, double rightMax) {
        // Figure out how 'wide' each range is
        double leftSpan = leftMax - leftMin;
        double rightSpan = rightMax - rightMin;
        // Convert the left range into a 0-1 range (float)
        double valueScaled = (value - leftMin) / leftSpan;
        // Convert the 0-1 range into a value in the right range.
        return rightMin + (valueScaled * rightSpan);
    }

    public static double[] buildNormalisedWeightTable(double[] targetSlipRates, double[] targetSlipRateStdDevs, double weightScalingOrderOfMagnitude) {
        Preconditions.checkArgument(targetSlipRates.length == targetSlipRateStdDevs.length);

        double[] result = new double[targetSlipRateStdDevs.length];

        double[] minMaxCOV = getMinMaxCOV(targetSlipRates, targetSlipRateStdDevs);
        double minCOV = minMaxCOV[0];
        double maxCOV = minMaxCOV[1];

        double minNormalisedWeight = 1;
        double maxNormalisedWeight = Math.pow(10, weightScalingOrderOfMagnitude);

        System.out.println("weightScalingOrderOfMagnitude " + weightScalingOrderOfMagnitude + " minCOV: " + minCOV
                + "; maxCOV: " + maxCOV + "; maxNormalisedWeight: " + maxNormalisedWeight);

        for (int i = 0; i < targetSlipRates.length; i++) {
            double cov = coefficientOfVariance(targetSlipRates[i], targetSlipRateStdDevs[i]);
            double normalised_weight = translate(cov, minCOV, maxCOV, minNormalisedWeight, maxNormalisedWeight);

            if (Double.isNaN(normalised_weight) || Double.isInfinite(normalised_weight)) {
                result[i] = 1;
            } else {
                result[i] = 1.0 / normalised_weight;
            }

            Preconditions.checkState(Double.isFinite(result[i]));
        }

        return result;
    }

    public static SectSlipRates createSectSlipRates(SectSlipRates oldSlipRates, double weightScalingOrderOfMagnitude) {
        double[] slipRates = oldSlipRates.getSlipRates();
        double[] modelStdDevs = oldSlipRates.getSlipRateStdDevs();

        double[] stdDevs = buildNormalisedWeightTable(slipRates, modelStdDevs, weightScalingOrderOfMagnitude);

        return SectSlipRates.precomputed(oldSlipRates.getParent(), slipRates, stdDevs);
    }

    public static InversionConstraint buildUncertaintyConstraint(double weight, FaultSystemRupSet rupSet, double weightScalingOrderOfMagnitude) {
        SectSlipRates sectSlipRates = createSectSlipRates(rupSet.getModule(SectSlipRates.class), weightScalingOrderOfMagnitude);
        return new SlipRateInversionConstraint(weight, ConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY, rupSet,
                rupSet.requireModule(AveSlipModule.class), rupSet.requireModule(SlipAlongRuptureModel.class), sectSlipRates);
    }

}

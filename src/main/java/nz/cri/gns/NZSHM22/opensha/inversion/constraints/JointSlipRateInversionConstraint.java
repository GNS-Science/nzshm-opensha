package nz.cri.gns.NZSHM22.opensha.inversion.constraints;

import cern.colt.matrix.tdouble.DoubleMatrix2D;
import java.util.List;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.ConstraintWeightingType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.SlipRateInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.modules.AveSlipModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import org.opensha.sha.earthquake.faultSysSolution.modules.SlipAlongRuptureModel;

/**
 * Constraints section slip rates to match the given target rate. It can apply normalized or
 * unnormalized constraints, or both:
 *
 * <p>If normalized, slip rate misfit is % difference for each section (recommended since it helps
 * fit slow-moving faults). Note that constraints for sections w/ slip rate < 0.1 mm/yr is not
 * normalized by slip rate -- otherwise misfit will be huge (GEOBOUND model has 10e-13 slip rates
 * that will dominate misfit otherwise)
 *
 * <p>If unnormalized, misfit is absolute difference.
 *
 * <p>Set the weighting with the SlipRateConstraintWeightingType enum.
 *
 * @author kevin
 */
public class JointSlipRateInversionConstraint extends InversionConstraint {

    private final transient ConstraintRegionConfig config;
    private transient FaultSystemRupSet rupSet;
    private transient AveSlipModule aveSlipModule;
    private transient SlipAlongRuptureModel slipAlongModule;
    private transient SectSlipRates targetSlipRates;

    public static final double DEFAULT_FRACT_STD_DEV = 0.5;

    public JointSlipRateInversionConstraint(
            ConstraintRegionConfig config,
            double weight,
            ConstraintWeightingType weightingType,
            FaultSystemRupSet rupSet) {
        this(
                config,
                weight,
                weightingType,
                rupSet,
                rupSet.requireModule(AveSlipModule.class),
                rupSet.requireModule(SlipAlongRuptureModel.class),
                rupSet.requireModule(SectSlipRates.class));
    }

    /**
     * Note: do not use this constructor if you rely on serialization / deserialization of the
     * constraint. The three modules will not be serialized by the constraint and will be taken from
     * the rupture set after deserialization.
     *
     * @param weight
     * @param weightingType
     * @param rupSet
     * @param aveSlipModule
     * @param slipAlongModule
     * @param targetSlipRates
     */
    public JointSlipRateInversionConstraint(
            ConstraintRegionConfig config,
            double weight,
            ConstraintWeightingType weightingType,
            FaultSystemRupSet rupSet,
            AveSlipModule aveSlipModule,
            SlipAlongRuptureModel slipAlongModule,
            SectSlipRates targetSlipRates) {
        super(
                weightingType.applyNamePrefix("Slip Rate"),
                weightingType.applyShortNamePrefix("SlipRate"),
                weight,
                false,
                weightingType);
        this.config = config;
        this.weightingType = weightingType;
        setRuptureSet(rupSet);
        this.aveSlipModule = aveSlipModule;
        this.slipAlongModule = slipAlongModule;
        this.targetSlipRates = targetSlipRates;
    }

    @Override
    public int getNumRows() {
        // one row for each section
        return config.sectionIds.size();
    }

    @Override
    public long encode(DoubleMatrix2D A, double[] d, int startRow) {
        long numNonZeroElements = 0;
        int numRuptures = rupSet.getNumRuptures();
        int numSections = rupSet.getNumSections();

        double[] weights = new double[numSections];
        for (int s = 0; s < numSections; s++) weights[s] = this.weight;
        if (weightingType == ConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY) {
            double[] stdDevs =
                    SlipRateInversionConstraint.getSlipRateStdDevs(
                            targetSlipRates, DEFAULT_FRACT_STD_DEV);
            for (int s = 0; s < numSections; s++) if (stdDevs[s] != 0d) weights[s] /= stdDevs[s];
        }

        // A matrix component of slip-rate constraint
        for (int rup = 0; rup < numRuptures; rup++) {
            double[] slips = slipAlongModule.calcSlipOnSectionsForRup(rupSet, aveSlipModule, rup);
            List<Integer> sects = rupSet.getSectionsIndicesForRup(rup);
            for (int i = 0; i < slips.length; i++) {
                int sectIndex = sects.get(i);
                Integer mappedRowIndex = config.mappingToARow.get(sectIndex);
                if (mappedRowIndex == null) {
                    continue;
                }
                int row = startRow + mappedRowIndex;
                int col = rup;
                double val = slips[i];
                if (weightingType == ConstraintWeightingType.NORMALIZED) {
                    double target = targetSlipRates.getSlipRate(sectIndex);
                    if (target != 0d) {
                        // Note that constraints for sections w/ slip rate < 0.1 mm/yr is not
                        // normalized by slip rate
                        // -- otherwise misfit will be huge (e.g., UCERF3 GEOBOUND model has 10e-13
                        // slip rates that will
                        // dominate misfit otherwise)
                        if (target < 1e-4 || Double.isNaN(target)) target = 1e-4;
                        val = slips[i] / target;
                    }
                }
                setA(A, row, col, val * weights[sectIndex]);
                numNonZeroElements++;
            }
        }
        // d vector component of slip-rate constraint
        for (int sectIndex = 0; sectIndex < numSections; sectIndex++) {
            Integer mappedRowIndex = config.mappingToARow.get(sectIndex);
            if (mappedRowIndex == null) {
                continue;
            }
            int row = startRow + mappedRowIndex;
            double target = targetSlipRates.getSlipRate(sectIndex);
            double val = target;
            if (weightingType == ConstraintWeightingType.NORMALIZED) {
                if (target == 0d)
                    // minimize
                    val = 0d;
                else if (target < 1E-4 || Double.isNaN(target))
                    // For very small slip rates, do not normalize by slip rate
                    //  (normalize by 0.0001 instead) so they don't dominate misfit
                    val = targetSlipRates.getSlipRate(sectIndex) / 1e-4;
                else val = 1d;
            }
            d[row] = val * weights[sectIndex];
            if (Double.isNaN(d[sectIndex]) || d[sectIndex] < 0)
                throw new IllegalStateException(
                        "d[" + sectIndex + "]=" + d[sectIndex] + " is NaN or 0!  target=" + target);
        }
        return numNonZeroElements;
    }

    @Override
    public void setRuptureSet(FaultSystemRupSet rupSet) {
        this.rupSet = rupSet;
        this.aveSlipModule = rupSet.requireModule(AveSlipModule.class);
        this.slipAlongModule = rupSet.requireModule(SlipAlongRuptureModel.class);
        this.targetSlipRates = rupSet.requireModule(SectSlipRates.class);
    }
}

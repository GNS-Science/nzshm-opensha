package nz.cri.gns.NZSHM22.opensha.inversion.constraints;

import java.util.ArrayList;
import java.util.List;
import nz.cri.gns.NZSHM22.opensha.inversion.AbstractInversionConfiguration;

public class ConstraintConfig {

    List<ConstraintRegionConfig> configs;

    public ConstraintConfig() {
        configs = new ArrayList<>();
    }

    public ConstraintConfig(List<ConstraintRegionConfig> configs) {
        this.configs = new ArrayList<>(configs);
    }

    public ConstraintRegionConfig config(int sectionId) {
        for (ConstraintRegionConfig config : configs) {
            if (config.covers(sectionId)) {
                return config;
            }
        }
        throw new IllegalArgumentException("No config found for section id " + sectionId);
    }

    public double getSlipRateConstraintWt_normalized(int sectionId) {
        return config(sectionId).getSlipRateConstraintWt_normalized();
    }

    public double getSlipRateConstraintWt_unnormalized(int sectionId) {
        return config(sectionId).getSlipRateConstraintWt_unnormalized();
    }

    public AbstractInversionConfiguration.NZSlipRateConstraintWeightingType getSlipRateWeighting(
            int sectionId) {
        return config(sectionId).getSlipRateWeightingType();
    }

    public double getSlipRateUncertaintyConstraintWt(int sectionId) {
        return config(sectionId).getSlipRateUncertaintyConstraintWt();
    }

    public double getSlipRateUncertaintyConstraintScalingFactor(int sectionId) {
        return config(sectionId).getSlipRateUncertaintyConstraintScalingFactor();
    }
}

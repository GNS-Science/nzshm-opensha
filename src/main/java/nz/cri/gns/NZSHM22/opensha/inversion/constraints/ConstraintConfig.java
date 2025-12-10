package nz.cri.gns.NZSHM22.opensha.inversion.constraints;

import java.util.ArrayList;
import java.util.List;

public class ConstraintConfig {

    List<ConstraintRegionConfig> configs;

    public ConstraintConfig() {
        configs = new ArrayList<>();
    }

    public ConstraintConfig(List<ConstraintRegionConfig> configs) {
        this.configs = new ArrayList<>(configs);
        // TODO: assert that there is no overlap in section id coverage
    }

    public ConstraintRegionConfig config(int sectionId) {
        for (ConstraintRegionConfig config : configs) {
            if (config.covers(sectionId)) {
                return config;
            }
        }
        throw new IllegalArgumentException("No config found for section id " + sectionId);
    }
}

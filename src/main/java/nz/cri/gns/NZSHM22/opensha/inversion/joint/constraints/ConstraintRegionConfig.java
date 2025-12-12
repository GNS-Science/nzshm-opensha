package nz.cri.gns.NZSHM22.opensha.inversion.joint.constraints;

import java.util.*;
import nz.cri.gns.NZSHM22.opensha.inversion.AbstractInversionConfiguration;

public class ConstraintRegionConfig {

    final List<Integer> sectionIds;
    final Map<Integer, Integer> mappingToARow;

    // slip rate section
    double slipRateConstraintWt_normalized = 1;
    double slipRateConstraintWt_unnormalized = 100;
    AbstractInversionConfiguration.NZSlipRateConstraintWeightingType slipRateWeightingType =
            AbstractInversionConfiguration.NZSlipRateConstraintWeightingType.BOTH;
    double slipRateUncertaintyConstraintWt;
    double slipRateUncertaintyConstraintScalingFactor;
    boolean unmodifiedSlipRateStdvs;

    public ConstraintRegionConfig(Collection<Integer> sectionIds) {
        this.sectionIds = new ArrayList<>(sectionIds);
        mappingToARow = new HashMap<>();
        for (int i = 0; i < sectionIds.size(); i++) {
            mappingToARow.put(this.sectionIds.get(i), i);
        }
    }

    /**
     * Returns true if this config covers the specified section id.
     *
     * @param sectionId
     * @return
     */
    public boolean covers(int sectionId) {
        return sectionIds.contains(sectionId);
    }

    public int getNumSections() {
        return sectionIds.size();
    }

    public int mapToRow(int sectionId) {
        return mappingToARow.get(sectionId);
    }
}

package nz.cri.gns.NZSHM22.opensha.inversion.joint.constraints;

import java.util.*;
import java.util.stream.Collectors;
import nz.cri.gns.NZSHM22.opensha.inversion.AbstractInversionConfiguration;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.RegionPredicate;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.faultSurface.FaultSection;

public class ConstraintConfig {

    final RegionPredicate region;

    transient List<Integer> sectionIds;
    transient Map<Integer, Integer> mappingToARow;

    // slip rate section
    double slipRateConstraintWt_normalized = 1;
    double slipRateConstraintWt_unnormalized = 100;
    AbstractInversionConfiguration.NZSlipRateConstraintWeightingType slipRateWeightingType =
            AbstractInversionConfiguration.NZSlipRateConstraintWeightingType.BOTH;
    double slipRateUncertaintyConstraintWt;
    double slipRateUncertaintyConstraintScalingFactor;
    boolean unmodifiedSlipRateStdvs;

    public ConstraintConfig(RegionPredicate region) {
        this.region = region;
    }

    public void init(FaultSystemRupSet ruptureSet) {
        sectionIds =
                ruptureSet.getFaultSectionDataList().stream()
                        .mapToInt(FaultSection::getSectionId)
                        .filter(region.getPredicate(ruptureSet))
                        .boxed()
                        .collect(Collectors.toList());
        mappingToARow = new HashMap<>();
        for (int i = 0; i < sectionIds.size(); i++) {
            mappingToARow.put(sectionIds.get(i), i);
        }
    }

    public RegionPredicate getRegion() {
        return region;
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

    public List<Integer> getSectionIds() {
        return sectionIds;
    }

    public int getNumSections() {
        return sectionIds.size();
    }

    public int mapToRow(int sectionId) {
        return mappingToARow.get(sectionId);
    }
}

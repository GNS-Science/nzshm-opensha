package nz.cri.gns.NZSHM22.opensha.inversion.joint;

import java.util.*;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_DeformationModel;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_SpatialSeisPDF;
import nz.cri.gns.NZSHM22.opensha.inversion.AbstractInversionConfiguration;
import org.opensha.commons.data.uncertainty.UncertainIncrMagFreqDist;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

public class PartitionConfig {

    public final PartitionPredicate partition;

    public transient List<Integer> sectionIds;
    public transient Map<Integer, Integer> mappingToARow;
    public transient IntPredicate partitionPredicate;

    // slip rate section
    public double slipRateConstraintWt_normalized = 1;
    public double slipRateConstraintWt_unnormalized = 100;
    public AbstractInversionConfiguration.NZSlipRateConstraintWeightingType slipRateWeightingType =
            AbstractInversionConfiguration.NZSlipRateConstraintWeightingType.BOTH;
    public double slipRateUncertaintyConstraintWt;
    public double slipRateUncertaintyConstraintScalingFactor;
    public boolean unmodifiedSlipRateStdvs;
    public NZSHM22_DeformationModel deformationModel = NZSHM22_DeformationModel.FAULT_MODEL;

    // MFD section
    public double totalRateM5;
    public double bValue;
    public double minMag;
    // only required for crustal
    public double maxMag;
    public double mfdTransitionMag;
    public double mfdEqualityConstraintWt;
    public double mfdInequalityConstraintWt;
    public double mfdUncertaintyWeight;
    public double mfdUncertaintyPower;
    public double mfdUncertaintyScalar;
    // crustal only
    public NZSHM22_SpatialSeisPDF spatialSeisPDF;
    // crustal only
    public double polygonBufferSize;
    // crustal only
    public double polygonMinBufferSize;

    public transient List<IncrementalMagFreqDist> mfdConstraints;
    public transient List<UncertainIncrMagFreqDist> mfdUncertaintyWeightedConstraints;

    public PartitionConfig(PartitionPredicate partition) {
        this.partition = partition;
    }

    public void init(FaultSystemRupSet ruptureSet) {
        partitionPredicate = partition.getPredicate(ruptureSet);
        sectionIds =
                ruptureSet.getFaultSectionDataList().stream()
                        .mapToInt(FaultSection::getSectionId)
                        .filter(partitionPredicate)
                        .boxed()
                        .collect(Collectors.toList());
        mappingToARow = new HashMap<>();
        for (int i = 0; i < sectionIds.size(); i++) {
            mappingToARow.put(sectionIds.get(i), i);
        }
    }

    /**
     * Returns true if this config covers the specified section id.
     *
     * @param sectionId
     * @return
     */
    public boolean covers(int sectionId) {
        return partitionPredicate.test(sectionId);
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

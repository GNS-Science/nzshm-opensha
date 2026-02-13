package nz.cri.gns.NZSHM22.opensha.inversion.joint;

import java.util.function.IntPredicate;
import nz.cri.gns.NZSHM22.opensha.ruptures.FaultSectionProperties;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;

public enum PartitionPredicate {
    TVZ,
    SANS_TVZ,
    CRUSTAL,
    HIKURANGI,
    PUYSEGUR;

    public IntPredicate getPredicate(FaultSystemRupSet ruptureSet) {

        switch (this) {
            case TVZ:
                return (sectionId) ->
                        FaultSectionProperties.isTvz(ruptureSet.getFaultSectionData(sectionId));
            case SANS_TVZ:
                return (sectionId) ->
                        !FaultSectionProperties.isTvz(ruptureSet.getFaultSectionData(sectionId));
            case CRUSTAL:
                return (sectionId) ->
                        FaultSectionProperties.getPartition(
                                        ruptureSet.getFaultSectionData(sectionId))
                                == CRUSTAL;
            case HIKURANGI:
                return (sectionId) ->
                        FaultSectionProperties.getPartition(
                                        ruptureSet.getFaultSectionData(sectionId))
                                == HIKURANGI;
            case PUYSEGUR:
                return (sectionId) ->
                        FaultSectionProperties.getPartition(
                                        ruptureSet.getFaultSectionData(sectionId))
                                == PUYSEGUR;
        }
        throw new IllegalStateException("Unknown RegionPredicate");
    }

    public boolean isSubduction() {
        return (this == PUYSEGUR || this == HIKURANGI);
    }

    public boolean isCrustal() {
        return !isSubduction();
    }
}

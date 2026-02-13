package nz.cri.gns.NZSHM22.opensha.inversion.joint;

import java.util.function.IntPredicate;
import nz.cri.gns.NZSHM22.opensha.faults.NZFaultSection;
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
                        ((NZFaultSection) ruptureSet.getFaultSectionData(sectionId)).isTvz();
            case SANS_TVZ:
                return (sectionId) ->
                        !((NZFaultSection) ruptureSet.getFaultSectionData(sectionId)).isTvz();
            case CRUSTAL:
                return (sectionId) ->
                        ((NZFaultSection) ruptureSet.getFaultSectionData(sectionId)).getPartition()
                                == CRUSTAL;
            case HIKURANGI:
                return (sectionId) ->
                        ((NZFaultSection) ruptureSet.getFaultSectionData(sectionId)).getPartition()
                                == HIKURANGI;
            case PUYSEGUR:
                return (sectionId) ->
                        ((NZFaultSection) ruptureSet.getFaultSectionData(sectionId)).getPartition()
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

package nz.cri.gns.NZSHM22.opensha.inversion.joint;

import java.util.function.IntPredicate;
import nz.cri.gns.NZSHM22.opensha.ruptures.FaultSectionProperties2;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;

public enum PartitionPredicate {
    TVZ,
    SANS_TVZ,
    CRUSTAL,
    HIKURANGI,
    PUYSEGUR;

    static FaultSectionProperties2 props(FaultSystemRupSet rupSet, int sectionId) {
        return new FaultSectionProperties2(rupSet.getFaultSectionData(sectionId));
    }

    public IntPredicate getPredicate(FaultSystemRupSet ruptureSet) {

        switch (this) {
            case TVZ:
                return (sectionId) -> props(ruptureSet, sectionId).getTvz();
            case SANS_TVZ:
                return (sectionId) -> !props(ruptureSet, sectionId).getTvz();
            case CRUSTAL:
                return (sectionId) ->
                        props(ruptureSet, sectionId).getPartition() == PartitionPredicate.CRUSTAL;
            case HIKURANGI:
                return (sectionId) ->
                        props(ruptureSet, sectionId).getPartition() == PartitionPredicate.HIKURANGI;
            case PUYSEGUR:
                return (sectionId) ->
                        props(ruptureSet, sectionId).getPartition() == PartitionPredicate.PUYSEGUR;
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

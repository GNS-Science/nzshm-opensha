package nz.cri.gns.NZSHM22.opensha.inversion.joint;

import java.util.function.IntPredicate;
import nz.cri.gns.NZSHM22.opensha.ruptures.FaultSectionProperties;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;

public enum RegionPredicate {
    TVZ,
    SANS_TVZ,
    CRUSTAL,
    HIKURANGI,
    PUYSEGUR;

    public IntPredicate getPredicate(FaultSystemRupSet ruptureSet) {
        FaultSectionProperties extraProperties =
                ruptureSet.requireModule(FaultSectionProperties.class);

        switch (this) {
            case TVZ:
                return (sectionId) -> extraProperties.get(sectionId, TVZ.name()) == Boolean.TRUE;
            case SANS_TVZ:
                return (sectionId) ->
                        extraProperties.get(sectionId, SANS_TVZ.name()) == Boolean.TRUE;
            case CRUSTAL:
                return (sectionId) ->
                        extraProperties.get(sectionId, CRUSTAL.name()) == Boolean.TRUE;
            case HIKURANGI:
                return (sectionId) ->
                        extraProperties.get(sectionId, HIKURANGI.name()) == Boolean.TRUE;
            case PUYSEGUR:
                return (sectionId) ->
                        extraProperties.get(sectionId, PUYSEGUR.name()) == Boolean.TRUE;
        }
        throw new IllegalStateException("Unknown RegionPredicate");
    }
}

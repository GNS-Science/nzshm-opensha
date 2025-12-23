package nz.cri.gns.NZSHM22.opensha.inversion.joint;

import java.util.function.IntPredicate;
import nz.cri.gns.NZSHM22.opensha.ruptures.FaultSectionProperties;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.faultSurface.FaultSection;

public enum RegionPredicate {
    TVZ,
    SANS_TVZ,
    CRUSTAL,
    SUBDUCTION;

    public boolean matches(FaultSection faultSection) {
        boolean subduction = faultSection.getSectionName().contains("row:");
        return (this == SUBDUCTION && subduction) || (this == CRUSTAL && !subduction);
    }

    public IntPredicate getPredicate(FaultSystemRupSet ruptureSet) {
        FaultSectionProperties extraProperties =
                ruptureSet.requireModule(FaultSectionProperties.class);

        switch (this) {
            case TVZ:
                return (sectionId) -> extraProperties.get(sectionId, TVZ.name()) == Boolean.TRUE;
            case SANS_TVZ:
                return (sectionId) ->
                        extraProperties.get(sectionId, SANS_TVZ.name()) == Boolean.TRUE;
            case SUBDUCTION:
                return (sectionId) ->
                        extraProperties.get(sectionId, SUBDUCTION.name()) == Boolean.TRUE;
            case CRUSTAL:
                return (sectionId) ->
                        extraProperties.get(sectionId, SUBDUCTION.name()) != Boolean.TRUE;
        }
        throw new IllegalStateException("Unknown RegionPredicate");
    }
}

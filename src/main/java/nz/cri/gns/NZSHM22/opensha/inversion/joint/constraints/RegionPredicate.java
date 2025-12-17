package nz.cri.gns.NZSHM22.opensha.inversion.joint.constraints;

import org.opensha.sha.faultSurface.FaultSection;

public enum RegionPredicate {
    CRUSTAL,
    SUBDUCTION;

    public boolean matches(FaultSection faultSection) {
        boolean subduction = faultSection.getSectionName().contains("row:");
        return (this == SUBDUCTION && subduction) || (this == CRUSTAL && !subduction);
    }
}

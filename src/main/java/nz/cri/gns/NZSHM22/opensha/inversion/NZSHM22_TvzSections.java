package nz.cri.gns.NZSHM22.opensha.inversion;

import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_LogicTreeBranch;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_Regions;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;

public class NZSHM22_TvzSections extends RegionSections {

    /**
     * Default constructor is required for deserialisation
     */
    public NZSHM22_TvzSections() {
        super();
    }

    public NZSHM22_TvzSections(FaultSystemRupSet rupSet) {
        super(rupSet, rupSet.getModule(NZSHM22_LogicTreeBranch.class).getValue(NZSHM22_Regions.class).getTvzRegion());
    }

    @Override
    public String getName() {
        return "NZSHM22_TvzSections";
    }
}

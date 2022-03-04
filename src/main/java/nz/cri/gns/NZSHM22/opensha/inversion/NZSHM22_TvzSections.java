package nz.cri.gns.NZSHM22.opensha.inversion;

import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import nz.cri.gns.NZSHM22.opensha.data.region.NewZealandRegions;

public class NZSHM22_TvzSections extends RegionSections {

	public NZSHM22_TvzSections() {
	}

	public NZSHM22_TvzSections(FaultSystemRupSet rupSet) {
		super(rupSet, new NewZealandRegions.NZ_TVZ_GRIDDED());
	}

}


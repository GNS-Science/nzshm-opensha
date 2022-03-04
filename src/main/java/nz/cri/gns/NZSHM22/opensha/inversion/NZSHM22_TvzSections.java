package nz.cri.gns.NZSHM22.opensha.inversion;

import nz.cri.gns.NZSHM22.opensha.data.region.NewZealandRegions;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;

public class NZSHM22_TvzSections extends RegionSections {

	public NZSHM22_TvzSections() {
		// TODO Auto-generated constructor stub
	}

	public NZSHM22_TvzSections(FaultSystemRupSet rupSet) {
		super(rupSet, new NewZealandRegions.NZ_TVZ_GRIDDED());
	}

}

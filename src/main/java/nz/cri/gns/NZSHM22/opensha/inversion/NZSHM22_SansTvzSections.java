package nz.cri.gns.NZSHM22.opensha.inversion;

import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import nz.cri.gns.NZSHM22.opensha.data.region.NewZealandRegions;

public class NZSHM22_SansTvzSections extends RegionSections {

	public NZSHM22_SansTvzSections() {
	}

	public NZSHM22_SansTvzSections(FaultSystemRupSet rupSet) {
		super(rupSet, new NewZealandRegions.NZ_RECTANGLE_SANS_TVZ_GRIDDED());
	}

}


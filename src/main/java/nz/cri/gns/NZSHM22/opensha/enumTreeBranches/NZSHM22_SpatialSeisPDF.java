package nz.cri.gns.NZSHM22.opensha.enumTreeBranches;

import org.opensha.commons.geo.GriddedRegion;

import nz.cri.gns.NZSHM22.opensha.data.region.NewZealandRegions;
import nz.cri.gns.NZSHM22.opensha.util.NZSHM22_SmoothSeismicitySpatialPDF_Fetcher;
import scratch.UCERF3.enumTreeBranches.InversionModels;
import scratch.UCERF3.enumTreeBranches.SpatialSeisPDF;
import scratch.UCERF3.logicTree.LogicTreeBranchNode;

public enum NZSHM22_SpatialSeisPDF implements LogicTreeBranchNode<SpatialSeisPDF> {

	NZSHM22_1246("NZSHM22_1246", "1246", 0.5d, 0.25d) {
		@Override
		public double[] getPDF() {
			return NZSHM22_SmoothSeismicitySpatialPDF_Fetcher.get1246();
		}
	},

	NZSHM22_1246R("NZSHM22_1246R", "1246R", 0.5d, 0.25d) {
		@Override
		public double[] getPDF() {
			return NZSHM22_SmoothSeismicitySpatialPDF_Fetcher.get1246R();
		}
	},

	NZSHM22_1456("NZSHM22_1456", "1456", 0.5d, 0.25d) {
		@Override
		public double[] getPDF() {
			return NZSHM22_SmoothSeismicitySpatialPDF_Fetcher.get1456();
		}
	},

	NZSHM22_1456R("NZSHM22_1456R", "1456R", 0.5d, 0.25d) {
		@Override
		public double[] getPDF() {
			return NZSHM22_SmoothSeismicitySpatialPDF_Fetcher.get1456R();
		}
	};

	private String name, shortName;
	private double charWeight, grWeight;

	private NZSHM22_SpatialSeisPDF(String name, String shortName, double charWeight, double grWeight) {
		this.name = name;
		this.shortName = shortName;
		this.charWeight = charWeight;
		this.grWeight = grWeight;
	}

	@Override
	public String getShortName() {
		return shortName;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public double getRelativeWeight(InversionModels im) {
		if (im.isCharacteristic())
			return charWeight;
		else
			return grWeight;
	}

	@Override
	public String encodeChoiceString() {
		return "SpatSeis" + getShortName();
	}

	@Override
	public String getBranchLevelName() {
		return "Spatial Seismicity PDF";
	}

	public abstract double[] getPDF();

	/**
	 * This returns the total sum of values inside the given gridded region
	 * 
	 * @param region
	 * @return
	 */
	public double getFractionInRegion(GriddedRegion region) {
		double[] vals = this.getPDF();
		double sum = 0;
		GriddedRegion nzRegion = new NewZealandRegions.NZ_TEST_GRIDDED();
		for (int i = 0; i < region.getNumLocations(); i++) {
			int iLoc = nzRegion.indexForLocation(region.getLocation(i));
			if (iLoc != -1)
				sum += vals[iLoc];
		}
		return sum;
	}

}

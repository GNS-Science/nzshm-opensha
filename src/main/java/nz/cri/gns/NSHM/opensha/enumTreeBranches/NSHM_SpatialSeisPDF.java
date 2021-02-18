package nz.cri.gns.NSHM.opensha.enumTreeBranches;


import org.opensha.commons.geo.GriddedRegion;

import nz.cri.gns.NSHM.opensha.data.region.NewZealandRegions;

//import org.opensha.commons.data.region.CaliforniaRegions;
//import org.opensha.commons.geo.GriddedRegion;

import nz.cri.gns.NSHM.opensha.util.NSHM_SmoothSeismicitySpatialPDF_Fetcher;
import scratch.UCERF3.enumTreeBranches.InversionModels;
import scratch.UCERF3.enumTreeBranches.SpatialSeisPDF;
import scratch.UCERF3.logicTree.LogicTreeBranchNode;
import scratch.UCERF3.utils.RELM_RegionUtils;


public enum NSHM_SpatialSeisPDF implements LogicTreeBranchNode<SpatialSeisPDF> {

	NZSHM22_1246("NZSHM22_1246",	"1246",		0.5d,	0.25d) {
		@Override public double[] getPDF() {
			return NSHM_SmoothSeismicitySpatialPDF_Fetcher.get1246();
		}
	},

	NZSHM22_1246R("NZSHM22_1246R", "1246R",		0.5d,	0.25d) {
		@Override public double[] getPDF() {
			return NSHM_SmoothSeismicitySpatialPDF_Fetcher.get1246R();
		}
	},
	
	NZSHM22_1456("NZSHM22_1456",	"1456",		0.5d,	0.25d) {
		@Override public double[] getPDF() {
			return NSHM_SmoothSeismicitySpatialPDF_Fetcher.get1456();
		}
	},

	NZSHM22_1456R("NZSHM22_1456R", "1456R",		0.5d,	0.25d) {
		@Override public double[] getPDF() {
			return NSHM_SmoothSeismicitySpatialPDF_Fetcher.get1456R();
		}
	};	
		
	private String name, shortName;
	private double charWeight, grWeight;
	
	private NSHM_SpatialSeisPDF(String name, String shortName, double charWeight, double grWeight) {
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
		return "SpatSeis"+getShortName();
	}
	
	@Override
	public String getBranchLevelName() {
		return "Spatial Seismicity PDF";
	}
	
	public abstract double[] getPDF();
	
	/**
	 * This returns the total sum of values inside the given gridded region
	 * @param region
	 * @return
	 */
	public double getFractionInRegion(GriddedRegion region) {
		double[] vals = this.getPDF();
		double sum=0;
		// TODO : this needs to be the NZ region!!
		// CaliforniaRegions.RELM_TESTING_GRIDDED relmRegion = RELM_RegionUtils.getGriddedRegionInstance();
		//		GriddedRegion nzRegion = new GriddedRegion(new NewZealandRegions.NZ_RECTANGLE(), 0.1d, null);
		GriddedRegion nzRegion = new NewZealandRegions.NZ_TEST_GRIDDED();
		for(int i=0; i<region.getNumLocations(); i++) {
			int iLoc = nzRegion.indexForLocation(region.getLocation(i));
			if(iLoc != -1)
				sum += vals[iLoc];
		}
		return sum;
	}	
	
}

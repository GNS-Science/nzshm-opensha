package nz.cri.gns.NZSHM22.opensha.inversion;

import nz.cri.gns.NZSHM22.opensha.analysis.NZSHM22_FaultSystemRupSetCalc;
import nz.cri.gns.NZSHM22.opensha.data.region.NewZealandRegions;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.*;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Region;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.RupSetScalingRelationship;
import org.opensha.sha.earthquake.faultSysSolution.modules.*;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.faultSurface.FaultSection;
import scratch.UCERF3.griddedSeismicity.FaultPolyMgr;
import scratch.UCERF3.inversion.InversionFaultSystemRupSet;
import scratch.UCERF3.inversion.U3InversionTargetMFDs;
import scratch.UCERF3.utils.UCERF3_Observed_MFD_Fetcher;

import java.awt.geom.Area;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.IntPredicate;

/**
 * This class provides specialisatations needed to override some UCERF3 defaults
 * in the base class.
 *
 * @author chrisbc
 *
 */
public class NZSHM22_InversionFaultSystemRuptSet extends InversionFaultSystemRupSet {

	private static final long serialVersionUID = 1091962054533163866L;

	protected NZSHM22_LogicTreeBranch branch;
	protected RegionalRupSetData sansTvz;
	protected RegionalRupSetData tvz;

    public NZSHM22_InversionFaultSystemRuptSet(FaultSystemRupSet rupSet, NZSHM22_LogicTreeBranch branch) {
        super(applyDeformationModel(rupSet, branch), branch.getU3Branch());
        init(branch);
    }

    protected static FaultSystemRupSet applyDeformationModel(FaultSystemRupSet rupSet, NZSHM22_LogicTreeBranch branch) {
        NZSHM22_DeformationModel model = branch.getValue(NZSHM22_DeformationModel.class);
        if (model != null) {
            model.applyTo(rupSet);
        }
        return rupSet;
    }

    protected void setLogicTreeBranch(NZSHM22_LogicTreeBranch branch) {
        removeModuleInstances(LogicTreeBranch.class);
        addModule(branch);
        this.branch = branch;
    }

	private void init(NZSHM22_LogicTreeBranch branch) {
		setLogicTreeBranch(branch);

		//overwrite behaviour of super class
		removeModuleInstances(FaultGridAssociations.class);

		if (branch.hasValue(NZSHM22_ScalingRelationshipNode.class)) {
			addModule(AveSlipModule.forModel(this, branch.getValue(NZSHM22_ScalingRelationshipNode.class)));
		}

		FaultRegime regime = branch.getValue(FaultRegime.class);
		if(regime == FaultRegime.SUBDUCTION) {

			offerAvailableModule(new Callable<NZSHM22_SubductionInversionTargetMFDs>() {
				@Override
				public NZSHM22_SubductionInversionTargetMFDs call() throws Exception {
					return new NZSHM22_SubductionInversionTargetMFDs(NZSHM22_InversionFaultSystemRuptSet.this);
				}
			}, NZSHM22_SubductionInversionTargetMFDs.class);

		}else if(regime == FaultRegime.CRUSTAL){

			offerAvailableModule(new Callable<PolygonFaultGridAssociations>() {
				@Override
				public PolygonFaultGridAssociations call() throws Exception {
					return FaultPolyMgr.create(getFaultSectionDataList(), U3InversionTargetMFDs.FAULT_BUFFER, new NewZealandRegions.NZ_RECTANGLE_GRIDDED());
				}
			}, PolygonFaultGridAssociations.class);

		}
	}

	public NZSHM22_InversionFaultSystemRuptSet setInversionTargetMFDs(InversionTargetMFDs inversionMFDs) {
		removeModuleInstances(InversionTargetMFDs.class);
		addModule(inversionMFDs);
		return this;
	}

	public NZSHM22_InversionFaultSystemRuptSet setRegionalData(RegionalRupSetData tvz, RegionalRupSetData sansTvz){
		this.tvz = tvz;
		this.sansTvz = sansTvz;
		return this;
	}

	public RegionalRupSetData getTvzRegionalData(){
		return tvz;
	}

	public RegionalRupSetData getSansTvzRegionalData(){
		return sansTvz;
	}

	/**
	 * Recalculate the magnitudes based on the specified ScalingRelationship
	 * @param scale
	 */
	public void recalcMags(RupSetScalingRelationship scale) {

		double[] mags = getMagForAllRups();
		double[] rakes = getAveRakeForAllRups();
		double[] areas = getAreaForAllRups();
		double[] lengths = getLengthForAllRups();

		double[] sectAreasOrig = new double[getFaultSectionDataList().size()];
		for (int i = 0; i < sectAreasOrig.length; i++) {
			sectAreasOrig[i] = getFaultSectionData(i).getArea(false);
		}

		for (int i = 0; i < mags.length; i++) {
			double totOrigArea = 0d; // not reduced for aseismicity
			for (FaultSection sect : getFaultSectionDataForRupture(i)) {
				totOrigArea += sectAreasOrig[sect.getSectionId()]; // sq-m
			}
			double origDDW = totOrigArea / lengths[i];
			mags[i] = scale.getMag(areas[i], origDDW, rakes[i]);
		}
	}

}

package nz.cri.gns.NZSHM22.opensha.inversion;

import nz.cri.gns.NZSHM22.opensha.analysis.NZSHM22_FaultSystemRupSetCalc;
import nz.cri.gns.NZSHM22.opensha.data.region.NewZealandRegions;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.*;
import nz.cri.gns.NZSHM22.opensha.griddedSeismicity.NZSHM22_FaultPolyMgr;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.RupSetScalingRelationship;
import org.opensha.sha.earthquake.faultSysSolution.modules.*;

import scratch.UCERF3.griddedSeismicity.FaultPolyMgr;
import scratch.UCERF3.inversion.InversionFaultSystemRupSet;
import scratch.UCERF3.inversion.U3InversionTargetMFDs;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;

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
	boolean[] isRupBelowMinMagsForSects;

    public NZSHM22_InversionFaultSystemRuptSet(FaultSystemRupSet rupSet, NZSHM22_LogicTreeBranch branch) {
        super(applyDeformationModel(rupSet, branch), branch.getU3Branch());
        init(branch);
    }

	/**
	 * Loads a RuptureSet from file.
	 * Strips the RuptureSet of stray U3 modules that are added when loading pre-modular files.
	 * Recalculates magnitudes if specified by the LTB.
	 * @param ruptureSetFile
	 * @param branch
	 * @return
	 * @throws IOException
	 */
	public static NZSHM22_InversionFaultSystemRuptSet loadRuptureSet(File ruptureSetFile, NZSHM22_LogicTreeBranch branch) throws IOException {
		FaultSystemRupSet rupSetA = FaultSystemRupSet.load(ruptureSetFile);

		NZSHM22_ScalingRelationshipNode scaling = branch.getValue(NZSHM22_ScalingRelationshipNode.class);
		if(scaling != null && scaling.getReCalc()){
			rupSetA = recalcMags(rupSetA, scaling);
		}

		return new NZSHM22_InversionFaultSystemRuptSet(rupSetA, branch);
	}

	/**
	 * Returns a new RuptureSet with recalculated magnitudes.
	 * @param rupSet
	 * @param scale
	 * @return
	 */
	public static FaultSystemRupSet recalcMags(FaultSystemRupSet rupSet, RupSetScalingRelationship scale){
		return FaultSystemRupSet.buildFromExisting(rupSet).forScalingRelationship(scale).build();
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
					NZSHM22_LogicTreeBranch branch =  NZSHM22_InversionFaultSystemRuptSet.this.branch;
					NZSHM22_FaultPolyParameters parameters = branch.getValue(NZSHM22_FaultPolyParameters.class);
					if (parameters == null) {
						parameters = new NZSHM22_FaultPolyParameters();
						branch.setValue(parameters);
					}
					return NZSHM22_FaultPolyMgr.create(getFaultSectionDataList(), parameters.getBufferSize(), parameters.getMinBufferSize(), new NewZealandRegions.NZ_RECTANGLE_GRIDDED());
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
	 * This tells whether the given rup is below any of the final minimum magnitudes
	 * of the sections utilized by the rup.  Actually, the test is really whether the
	 * mag falls below the lower bin edge implied by the section min mags; see doc for
	 * computeWhichRupsFallBelowSectionMinMags().
	 * @param rupIndex
	 * @return
	 */
	@Override
	public synchronized boolean isRuptureBelowSectMinMag(int rupIndex) {
		if(isRupBelowMinMagsForSects == null) {
			ModSectMinMags minMagsModule = getModule(ModSectMinMags.class);
			isRupBelowMinMagsForSects = NZSHM22_FaultSystemRupSetCalc.computeWhichRupsFallBelowSectionMinMags(this, minMagsModule);
		}
		return isRupBelowMinMagsForSects[rupIndex];
	}

	@Override
	public double getUpperMagForSubseismoRuptures(int sectIndex) {
		throw new RuntimeException("Not supported, don't use this!");
	}

}

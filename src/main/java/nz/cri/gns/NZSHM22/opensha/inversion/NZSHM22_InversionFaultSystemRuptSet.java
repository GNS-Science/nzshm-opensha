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

    private NZSHM22_InversionFaultSystemRuptSet(FaultSystemRupSet rupSet, NZSHM22_LogicTreeBranch branch) {
        super(rupSet, branch.getU3Branch());
        init(branch);
    }

	/**
	 * Loads a subduction RuptureSet from file.
	 * Strips the RuptureSet of stray U3 modules that are added when loading pre-modular files.
	 * Recalculates magnitudes if specified by the LTB.
	 * @param ruptureSetFile
	 * @param branch
	 * @return
	 * @throws IOException
	 */
	public static NZSHM22_InversionFaultSystemRuptSet loadSubductionRuptureSet(File ruptureSetFile, NZSHM22_LogicTreeBranch branch) throws IOException {
		FaultSystemRupSet rupSet = FaultSystemRupSet.load(ruptureSetFile);
		return fromExistingSubductionRuptureSet(rupSet, branch);
	}

	public static NZSHM22_InversionFaultSystemRuptSet fromExistingSubductionRuptureSet(FaultSystemRupSet rupSet, NZSHM22_LogicTreeBranch branch) {
		NZSHM22_ScalingRelationshipNode scaling = branch.getValue(NZSHM22_ScalingRelationshipNode.class);
		if (scaling != null && scaling.getReCalc()) {
			rupSet = recalcMags(rupSet, scaling);
		}

		return new NZSHM22_InversionFaultSystemRuptSet(rupSet, branch);
	}

	/**
	 * This needs to happen before rupSet is passed on to the constructor.
	 * @param rupSet
	 * @param branch
	 * @return
	 */
	protected static FaultSystemRupSet prepCrustalRupSet(FaultSystemRupSet rupSet, NZSHM22_LogicTreeBranch branch) throws IOException {
		rupSet.addModule(FaultPolyMgr.create(rupSet.getFaultSectionDataList(),
				U3InversionTargetMFDs.FAULT_BUFFER,
				new NewZealandRegions.NZ_RECTANGLE_GRIDDED()));
		rupSet.addModule(new NZSHM22_TvzSections(rupSet));
		applyDeformationModel(rupSet, branch);

		//applyTVZSlipRateFactor(rupSet, tvzSlipRateFactor);

		NZSHM22_ScalingRelationshipNode scaling = branch.getValue(NZSHM22_ScalingRelationshipNode.class);

		NZSHM22_MagBounds magBounds = branch.getValue(NZSHM22_MagBounds.class);
		if (magBounds != null && magBounds.getMaxMagType() == NZSHM22_MagBounds.MaxMagType.FILTER_RUPSET) {
			scaling.setRecalc(true);
		}

		if (scaling != null && scaling.getReCalc()) {
			rupSet = recalcMags(rupSet, scaling);
		}

		if (magBounds != null && magBounds.getMaxMagType() == NZSHM22_MagBounds.MaxMagType.FILTER_RUPSET) {
			rupSet = RupSetMaxMagFilter.filter(rupSet, scaling, magBounds.getMaxMagTvz(), magBounds.getMaxMagSans());
		}

		return rupSet;
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
	public static NZSHM22_InversionFaultSystemRuptSet loadCrustalRuptureSet(File ruptureSetFile, NZSHM22_LogicTreeBranch branch) throws IOException {
		return fromExistingCrustalSet(FaultSystemRupSet.load(ruptureSetFile), branch);
	}

	public static NZSHM22_InversionFaultSystemRuptSet fromExistingCrustalSet(FaultSystemRupSet rupSet, NZSHM22_LogicTreeBranch branch) throws IOException {
		rupSet = prepCrustalRupSet(rupSet, branch);
		return new NZSHM22_InversionFaultSystemRuptSet(rupSet, branch);
	}

	protected static void applyTVZSlipRateFactor(FaultSystemRupSet rupSet, double tvzSlipRateFactor){
		if(tvzSlipRateFactor >=0){
			NZSHM22_TvzSections tvzSections = rupSet.getModule(NZSHM22_TvzSections.class);
			SectSlipRates origSlips = rupSet.getModule(SectSlipRates.class);
			double[] slipRates = origSlips.getSlipRates();
			tvzSections.getSections().forEach(sectionId -> {
				slipRates[sectionId] *= tvzSlipRateFactor;
			});
			rupSet.addModule(SectSlipRates.precomputed(rupSet, slipRates, origSlips.getSlipRateStdDevs()));
		}
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

    protected static void applyDeformationModel(FaultSystemRupSet rupSet, NZSHM22_LogicTreeBranch branch) {
        NZSHM22_DeformationModel model = branch.getValue(NZSHM22_DeformationModel.class);
        if (model != null) {
            model.applyTo(rupSet);
        }
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
			addAvailableModule(new Callable<NZSHM22_SubductionInversionTargetMFDs>() {
				@Override
				public NZSHM22_SubductionInversionTargetMFDs call() throws Exception {
					return new NZSHM22_SubductionInversionTargetMFDs(NZSHM22_InversionFaultSystemRuptSet.this);
				}
			}, NZSHM22_SubductionInversionTargetMFDs.class);

		} else if (regime == FaultRegime.CRUSTAL) {
			addAvailableModule(new Callable<PolygonFaultGridAssociations>() {
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
			addAvailableModule(new Callable<NZSHM22_TvzSections>() {
				@Override
				public NZSHM22_TvzSections call() throws Exception {
					return new NZSHM22_TvzSections(NZSHM22_InversionFaultSystemRuptSet.this);
				}
			}, NZSHM22_TvzSections.class);
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

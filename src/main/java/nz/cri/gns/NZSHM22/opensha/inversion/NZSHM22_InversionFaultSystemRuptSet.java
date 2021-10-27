package nz.cri.gns.NZSHM22.opensha.inversion;

import nz.cri.gns.NZSHM22.opensha.analysis.NZSHM22_FaultSystemRupSetCalc;
import nz.cri.gns.NZSHM22.opensha.data.region.NewZealandRegions;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.FaultRegime;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_LogicTreeBranch;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_ScalingRelationshipNode;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_DeformationModel;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.RupSetScalingRelationship;
import org.opensha.sha.earthquake.faultSysSolution.modules.*;

import org.opensha.sha.faultSurface.FaultSection;
import scratch.UCERF3.griddedSeismicity.FaultPolyMgr;
import scratch.UCERF3.inversion.InversionFaultSystemRupSet;
import scratch.UCERF3.inversion.InversionTargetMFDs;
import scratch.UCERF3.inversion.U3InversionTargetMFDs;

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

	protected static double minMagForSeismogenicRups = 6.0;

	protected NZSHM22_LogicTreeBranch branch;

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
					return FaultPolyMgr.create(getFaultSectionDataList(), U3InversionTargetMFDs.FAULT_BUFFER, new NewZealandRegions.NZ_TEST_GRIDDED());
				}
			}, PolygonFaultGridAssociations.class);
		}


	}

	/**
	 * This returns the final minimum mag for a given fault section. This uses a
	 * generic version of computeMinSeismoMagForSections() instead of the UCERF3
	 * implementation.
	 *
	 * @param sectIndex
	 * @return
	 */
	@Override
	public synchronized double getFinalMinMagForSection(int sectIndex) {
		// FIXME
		throw new IllegalStateException("not yet refactored!");
//		if (minMagForSectArray == null) {
//			minMagForSectArray = NZSHM22_FaultSystemRupSetCalc.computeMinSeismoMagForSections(this,
//					minMagForSeismogenicRups);
//		}
//		return minMagForSectArray[sectIndex];
	}

	public NZSHM22_InversionFaultSystemRuptSet setInversionTargetMFDs(InversionTargetMFDs inversionMFDs) {
		removeModuleInstances(InversionTargetMFDs.class);
		addModule(inversionMFDs);
		return this;
	}

	public static void setMinMagForSeismogenicRups(double minMag){
		minMagForSeismogenicRups = minMag;
	}

	// this overrides the calculation for the ModSectMinMags module
    @Override
	protected double[] calcFinalMinMagForSections() {
		return NZSHM22_FaultSystemRupSetCalc.computeMinSeismoMagForSections(this,minMagForSeismogenicRups);
	}

	/**
	 * Recalculate the magnitudes based on the specified ScalingRelationship
	 * @param scale
	 */
	public void recalcMags(RupSetScalingRelationship scale){

		double[] mags = getMagForAllRups();

		double[] areas = getAreaForAllRups();
		double[] lengths = getLengthForAllRups();

		double[] sectAreasOrig = new double[getFaultSectionDataList().size()];
		for(int i = 0; i < sectAreasOrig.length; i++) {
			sectAreasOrig[i] = getFaultSectionData(i).getArea(false);
		}

		for(int i =0; i < mags.length; i++) {
			double totOrigArea = 0d; // not reduced for aseismicity
			for (FaultSection sect : getFaultSectionDataForRupture(i)) {
				totOrigArea += sectAreasOrig[sect.getSectionId()]; // sq-m
			}
			double origDDW = totOrigArea / lengths[i];
			mags[i] =  scale.getMag(areas[i], origDDW);
		}
	}

}

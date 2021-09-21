package nz.cri.gns.NZSHM22.opensha.inversion;

import nz.cri.gns.NZSHM22.opensha.analysis.NZSHM22_FaultSystemRupSetCalc;
import nz.cri.gns.NZSHM22.opensha.data.region.NewZealandRegions;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.FaultGridAssociations;

import org.opensha.sha.earthquake.faultSysSolution.modules.PolygonFaultGridAssociations;
import org.opensha.sha.earthquake.faultSysSolution.modules.SlipAlongRuptureModel;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;
import scratch.UCERF3.griddedSeismicity.FaultPolyMgr;
import scratch.UCERF3.inversion.InversionFaultSystemRupSet;
import scratch.UCERF3.inversion.InversionTargetMFDs;
import scratch.UCERF3.inversion.U3InversionTargetMFDs;
import scratch.UCERF3.logicTree.U3LogicTreeBranch;

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

	/**
	 * Constructor which relies on the super-class implementation
	 *
	 * @param rupSet
	 * @param branch
	 */
	private NZSHM22_InversionFaultSystemRuptSet (FaultSystemRupSet rupSet, U3LogicTreeBranch branch) {
	    super(rupSet, branch);
	    removeModuleInstances(PolygonFaultGridAssociations.class);
		offerAvailableModule(new Callable<PolygonFaultGridAssociations>() {
			@Override
			public PolygonFaultGridAssociations call() throws Exception {
				return FaultPolyMgr.create(getFaultSectionDataList(), U3InversionTargetMFDs.FAULT_BUFFER, new NewZealandRegions.NZ_TEST_GRIDDED());
			}
		}, PolygonFaultGridAssociations.class);
	}

	public static NZSHM22_InversionFaultSystemRuptSet fromSubduction(FaultSystemRupSet rupSet, U3LogicTreeBranch branch) {
		branch.clearValue(ScalingRelationships.class);
		branch.setValue(ScalingRelationships.TMG_SUB_2017);
		branch.clearValue(SlipAlongRuptureModels.class);
		branch.setValue(SlipAlongRuptureModels.UNIFORM);

		NZSHM22_InversionFaultSystemRuptSet result = new NZSHM22_InversionFaultSystemRuptSet(rupSet, branch);

		//overwrite behaviour of super class
        result.removeModuleInstances(FaultGridAssociations.class);
		result.removeModuleInstances(InversionTargetMFDs.class);
		result.offerAvailableModule(new Callable<NZSHM22_SubductionInversionTargetMFDs>() {
			@Override
			public NZSHM22_SubductionInversionTargetMFDs call() throws Exception {
				return new NZSHM22_SubductionInversionTargetMFDs(result);
			}
		}, NZSHM22_SubductionInversionTargetMFDs.class);
		return result;
	}

	public static NZSHM22_InversionFaultSystemRuptSet fromCrustal(FaultSystemRupSet rupSet, U3LogicTreeBranch branch){
		branch.clearValue(ScalingRelationships.class);
		branch.setValue(ScalingRelationships.TMG_CRU_2017);
		branch.clearValue(SlipAlongRuptureModels.class);
		branch.setValue(SlipAlongRuptureModels.UNIFORM);

		NZSHM22_InversionFaultSystemRuptSet result = new NZSHM22_InversionFaultSystemRuptSet(rupSet, branch);

		result.removeModuleInstances(InversionTargetMFDs.class);
		result.offerAvailableModule(new Callable<NZSHM22_CrustalInversionTargetMFDs>() {
			@Override
			public NZSHM22_CrustalInversionTargetMFDs call() throws Exception {
				return new NZSHM22_CrustalInversionTargetMFDs(result);
			}
		}, NZSHM22_CrustalInversionTargetMFDs.class);
		return result;
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
		throw new IllegalStateException("net yet refactored!");
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


}

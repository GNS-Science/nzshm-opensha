package nz.cri.gns.NZSHM22.opensha.inversion;

import nz.cri.gns.NZSHM22.opensha.analysis.NZSHM22_FaultSystemRupSetCalc;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import scratch.UCERF3.analysis.FaultSystemRupSetCalc;
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

	// overwrite isRupBelowMinMagsForSects from InversionFaultSystemRupSet
	private boolean[] isRupBelowMinMagsForSects;
	private double[] minMagForSectArray;
	protected static double minMagForSeismogenicRups = 6.0;

	/**
	 * Constructor which relies on the super-class implementation
	 *
	 * @param rupSet
	 * @param branch
	 */
	public NZSHM22_InversionFaultSystemRuptSet(FaultSystemRupSet rupSet, U3LogicTreeBranch branch) {
		super(rupSet, branch);

		//overwrite behaviour of super class
		removeModuleInstances(InversionTargetMFDs.class);
		offerAvailableModule(new Callable<NZSHM22_SubductionInversionTargetMFDs>() {
			@Override
			public NZSHM22_SubductionInversionTargetMFDs call() throws Exception {
				return new NZSHM22_SubductionInversionTargetMFDs(NZSHM22_InversionFaultSystemRuptSet.this);
			}
		}, NZSHM22_SubductionInversionTargetMFDs.class);
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

	@Override
	public U3InversionTargetMFDs getInversionTargetMFDs() {
		return getModule(NZSHM22_SubductionInversionTargetMFDs.class);
	}

	public NZSHM22_InversionFaultSystemRuptSet setInversionTargetMFDs(InversionTargetMFDs inversionMFDs) {
		removeModuleInstances(InversionTargetMFDs.class);
		addModule(inversionMFDs);
		return this;
	}

	public static void setMinMagForSeismogenicRups(double minMag){
		minMagForSeismogenicRups = minMag;
	}

	// this should override the calculation for the ModSectMinMags module
	// TODO double check that this works
    @Override
	protected double[] calcFinalMinMagForSections() {
		return FaultSystemRupSetCalc.computeMinSeismoMagForSections(this,minMagForSeismogenicRups);
	}


}

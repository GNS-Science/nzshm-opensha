package nz.cri.gns.NSHM.opensha.inversion;

import nz.cri.gns.NSHM.opensha.analysis.NSHM_FaultSystemRupSetCalc;
import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.analysis.FaultSystemRupSetCalc;
import scratch.UCERF3.inversion.InversionFaultSystemRupSet;
import scratch.UCERF3.inversion.InversionTargetMFDs;
import scratch.UCERF3.logicTree.LogicTreeBranch;

/**
 * This class provides specialisatations needed to override some UCERF3 defaults
 * in the base class.
 * 
 * @author chrisbc
 *
 */
public class NSHM_InversionFaultSystemRuptSet extends InversionFaultSystemRupSet {
	// this holds the various MFDs implied by the inversion fault system rupture set
	// (e.g., we need to know the sub-seismo on-fault moment rates to reduce slip
	// rates accordingly)
	private InversionTargetMFDs inversionMFDs;

	private static final long serialVersionUID = 1091962054533163866L;

	/**
	 * Use this constructor to enhance a rupture set with the additional input
	 * required for an Inversion
	 * 
	 * @param rupSet
	 */
	public NSHM_InversionFaultSystemRuptSet(InversionFaultSystemRupSet rupSet) {
		super(rupSet, rupSet.getLogicTreeBranch(), null, 
				rupSet.getAveSlipForAllRups(),rupSet.getCloseSectionsListList(),
				rupSet.getRupturesForClusters(), rupSet.getSectionsForClusters());
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
		if (minMagForSectArray == null) {
			minMagForSectArray = NSHM_FaultSystemRupSetCalc.computeMinSeismoMagForSections(this,
					MIN_MAG_FOR_SEISMOGENIC_RUPS);
		}
		return minMagForSectArray[sectIndex];
	}

	@Override
	public InversionTargetMFDs getInversionTargetMFDs() {
		if (inversionMFDs == null)
			inversionMFDs = new InversionTargetMFDs(this);
		return inversionMFDs;
	}
	
	public NSHM_InversionFaultSystemRuptSet setInversionTargetMFDs(InversionTargetMFDs inversionMFDs) {
		this.inversionMFDs = inversionMFDs;
		return this;
	}

}

package nz.cri.gns.NSHM.opensha.inversion;

import nz.cri.gns.NSHM.opensha.analysis.NSHM_FaultSystemRupSetCalc;
import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.analysis.FaultSystemRupSetCalc;
import scratch.UCERF3.inversion.InversionFaultSystemRupSet;
import scratch.UCERF3.logicTree.LogicTreeBranch;

/**
 * This class provides specialisatations needed
 * to override some UCERF3 defaults in the base class.
 * 
 * @author chrisbc
 *
 */
public class NSHM_InversionFaultSystemRuptSet extends InversionFaultSystemRupSet {
	// rupture attributes (all in SI units)
	//	final static double MIN_MO_RATE_REDUCTION = 0.1;
	//	private double[] rupMeanSlip;
	//
	//	// cluster information
	//	private List<List<Integer>> clusterRups;
	//	private List<List<Integer>> clusterSects;

	// this holds the various MFDs implied by the inversion fault system rupture set 
	// (e.g., we need to know the sub-seismo on-fault moment rates to reduce slip rates accordingly)
	private NSHM_InversionTargetMFDs inversionMFDs;

	private static final long serialVersionUID = 1091962054533163866L;

	/**
	 * Use this constructor to enhance a rupture set with the additional input required for an Inversion
	 * @param rupSet
	 * @param branch
	 */
	public NSHM_InversionFaultSystemRuptSet(FaultSystemRupSet rupSet, LogicTreeBranch branch) {
		//set filter, rupAveSlips, sectionConnectionsListList, clusterRups, clusterSects all to null
		super(rupSet, branch, null, null, null, null, null);
	}
	

	/**
	 * This returns the final minimum mag for a given fault section.
	 * This uses a generic version of computeMinSeismoMagForSections() instead 
	 * of the UCERF3 implementation. 
	 * @param sectIndex
	 * @return
	 */
	@Override
	public synchronized double getFinalMinMagForSection(int sectIndex) {
		if(minMagForSectArray == null) {
			minMagForSectArray = NSHM_FaultSystemRupSetCalc.computeMinSeismoMagForSections(this, MIN_MAG_FOR_SEISMOGENIC_RUPS);
		}
		return minMagForSectArray[sectIndex];
	}	
}

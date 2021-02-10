package nz.cri.gns.NSHM.opensha.inversion;

import java.util.Map;

import scratch.UCERF3.SlipEnabledSolution;
import scratch.UCERF3.enumTreeBranches.InversionModels;
import scratch.UCERF3.inversion.InversionFaultSystemRupSet;
import scratch.UCERF3.inversion.UCERF3InversionConfiguration;
import scratch.UCERF3.logicTree.LogicTreeBranch;

/**
 * This is a SlipEnabledSolution that also contains parameters used in the NSHM.NZ Inversion
 * 
 * @author chrisbc
 *
 */
@SuppressWarnings("serial")
public class NSHM_InversionFaultSystemSolution extends SlipEnabledSolution {
	
	private NSHM_InversionFaultSystemRuptSet rupSet;
//	private InversionModels invModel;
//	private LogicTreeBranch branch;
	
	/**
	 * Inversion constraint weights and such. Note that this won't include the initial rup model or
	 * target MFDs and cannot be used as input to InversionInputGenerator.
	 */
	private UCERF3InversionConfiguration inversionConfiguration; 
	
	private Map<String, Double> energies;
	private Map<String, Double> misfits;
	
	/**
	 * Can be used on the fly for when InversionConfiguration/energies are not available/relevant
	 * 
	 * @param rupSet
	 * @param rates
	 */
	public NSHM_InversionFaultSystemSolution(NSHM_InversionFaultSystemRuptSet rupSet, double[] rates) {
		this(rupSet, rates, null, null);
	}
	
	/**
	 * Default constructor, for post inversion or file loading.
	 * 
	 * @param rupSet
	 * @param rates
	 * @param config can be null
	 * @param energies can be null
	 */
	public NSHM_InversionFaultSystemSolution(NSHM_InversionFaultSystemRuptSet rupSet, double[] rates,
			UCERF3InversionConfiguration config, Map<String, Double> energies) {
		super();	
		init(rupSet, rates, config, energies);
	}
	
	protected void init(NSHM_InversionFaultSystemRuptSet rupSet, double[] rates,
			UCERF3InversionConfiguration config, Map<String, Double> energies) {
		super.init(rupSet, rates, rupSet.getInfoString(), null);
		this.rupSet = rupSet;
		
		this.branch = null;   //rupSet.getLogicTreeBranch();
		this.invModel = null; //branch.getValue(InversionModels.class);
		
		// these can all be null
		this.inversionConfiguration = config;
		this.energies = energies;
	}	
		
	@Override
	public NSHM_InversionFaultSystemRuptSet getRupSet() {
		return rupSet;
	}

}
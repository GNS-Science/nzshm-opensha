package nz.cri.gns.NZSHM22.opensha.inversion;

import java.util.ArrayList;
import java.util.List;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.faultSurface.FaultSection;

import nz.cri.gns.NZSHM22.opensha.ruptures.FilteredFaultSystemRuptureSet;
import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.inversion.InversionFaultSystemRupSet;
import scratch.UCERF3.inversion.InversionFaultSystemSolution;

/**
 * Primarily used for examining the results of inversion at the fault-system level.
 *   
 * @author chrisbc
 *
 */
public class FilteredInversionFaultSystemSolution {
	private FaultSystemRupSet filteredRupSet;
		
	public FilteredInversionFaultSystemSolution() {}

	/**
	 * Produce a new InversionFaultSystemSolution with a subset of the originals ruptures
	 * based on an input filter.
	 * 
	 * @param inputSol the original inversion solution
	 * @param selectedSubSects a list of subsection IDs tio filter on.
	 * @return the new filtered solution
	 */
	public InversionFaultSystemSolution createFilteredSolution(InversionFaultSystemSolution inputSol,
			List<FaultSection> selectedSubSects) {
		// Build filtered Rupture set
		FilteredFaultSystemRuptureSet builder = new FilteredFaultSystemRuptureSet();
		filteredRupSet = builder.create(inputSol.getRupSet(), selectedSubSects);
		InversionFaultSystemRupSet rupSet = new InversionFaultSystemRupSet(filteredRupSet, 
				inputSol.getLogicTreeBranch(), 
				null, null, null, null, null);	
				
		List<Double> filterRates = new ArrayList<Double>();
		for (Integer r=0; r<inputSol.getRateForAllRups().length; r++) {
			if (builder.getSelectedIndices().contains(r))
				filterRates.add(inputSol.getRateForRup(r));
		}
		
		double[] rates =  new double[filterRates.size()];
		for (int i = 0; i < rates.length; i++)
			rates[i] = filterRates.get(i);

		return new InversionFaultSystemSolution(rupSet, rates, 
				inputSol.getInversionConfiguration(), inputSol.getEnergies());
	}

	public FaultSystemRupSet getFilteredRupSet() {
		return filteredRupSet;
	}
		
}

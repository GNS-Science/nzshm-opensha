package nz.cri.gns.NSHM.opensha.ruptures;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.faultSurface.FaultSection;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.inversion.InversionFaultSystemRupSet;

/**
 * @author chrisbc
 *
 */
public class FilteredFaultSystemRuptureSet {
	private Set<Integer> selectedIndices = new HashSet<Integer>();
	
	public Set<Integer> getSelectedIndices() {
		return selectedIndices;
	}
	
	/**
	 * Produce a new FaultSystemRupSet containing a subset of the originals ruptures
	 * based on an input filter.
	 * @param rupSet
	 * @param selectedSubSects
	 * @return
	 */
	public FaultSystemRupSet create(FaultSystemRupSet rupSet, List<FaultSection> selectedSubSects) {
		
		List<List<Integer>>	filterSectionIndicesForAllRups = new ArrayList<List<Integer>>();//rupSet.getSectionIndicesForAllRups()
		
		List<Double> filterMagForAllRups = new ArrayList<Double>(); //rupSet.getMagForAllRups()
		List<Double> filterAveRakeForAllRups = new ArrayList<Double>(); //rupSet.getAveRakeForAllRups()
		List<Double> filterAreaForAllRups = new ArrayList<Double>(); //rupSet.getAreaForAllRups()
		List<Double> filterLengthForAllRups = new ArrayList<Double>(); //rupSet.getLengthForAllRups()
				
		List<ClusterRupture> ruptures = new ArrayList<>();
		
		for (int r=0; r<rupSet.getNumRuptures(); r++) {
			boolean found = false;
			List<Integer> sectIndices = rupSet.getSectionsIndicesForRup(r);
			for (int sectionIndex : sectIndices) {
				if (selectedSubSects.contains(rupSet.getFaultSectionData(sectionIndex))) {
					found = true;
					break;
				}
			}
			if (found) {
				selectedIndices.add(r);
				ruptures.add(rupSet.getClusterRuptures().get(r));
				filterMagForAllRups.add(rupSet.getMagForRup(r));
				filterAveRakeForAllRups.add(rupSet.getAveRakeForRup(r));
				filterAreaForAllRups.add(rupSet.getAreaForRup(r));
				filterLengthForAllRups.add(rupSet.getLengthForRup(r));
				filterSectionIndicesForAllRups.add(sectIndices);
			}
		}
		//Validation here
		
		// Create input args
		int newSize = filterMagForAllRups.size();
		double[] mags = new double[newSize];
		double[] rakes =  new double[newSize];
		double[] areas =  new double[newSize];
		double[] lengths =  new double[newSize];
		
		for (int i = 0; i < mags.length; i++) {
			mags[i] = filterMagForAllRups.get(i);
			rakes[i] = filterAveRakeForAllRups.get(i);
			areas[i] = filterAreaForAllRups.get(i);
			lengths[i] = filterLengthForAllRups.get(i);
		}
	
		// create the new ruptures set
		FaultSystemRupSet filterRupSet = new FaultSystemRupSet(rupSet.getFaultSectionDataList(), 
				rupSet.getSlipRateForAllSections(),
				rupSet.getSlipRateStdDevForAllSections(), rupSet.getAreaForAllSections(),
				filterSectionIndicesForAllRups, mags, rakes, areas, lengths, rupSet.getInfoString());
		filterRupSet.setClusterRuptures(ruptures);
		return filterRupSet;
	}

	public FilteredFaultSystemRuptureSet() {
		selectedIndices = new HashSet<Integer>();
	}

}

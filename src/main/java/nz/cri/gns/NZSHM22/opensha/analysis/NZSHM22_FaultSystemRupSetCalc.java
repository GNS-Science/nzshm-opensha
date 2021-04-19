package nz.cri.gns.NZSHM22.opensha.analysis;

import java.io.FileWriter;
import java.util.HashMap;
import java.util.List;

import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.sha.faultSurface.FaultSection;
import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.analysis.FaultSystemRupSetCalc;
import scratch.UCERF3.inversion.InversionFaultSystemRupSet;

public class NZSHM22_FaultSystemRupSetCalc extends FaultSystemRupSetCalc {

	/**
	 * Override the UCERF3 implementation which does something special when
	 * ID==Parkfield
	 * 
	 * @param fltSystRupSet
	 * @param systemWideMinSeismoMag
	 * @return
	 */
	public static double[] computeMinSeismoMagForSections(FaultSystemRupSet fltSystRupSet,
			double systemWideMinSeismoMag) {
		double[] minMagForSect = new double[fltSystRupSet.getNumSections()];
		String prevParSectName = "junk_imp0ss!ble_fault_name_1067487@#";
		List<? extends FaultSection> sectDataList = fltSystRupSet.getFaultSectionDataList();

		// make map between parent section name and maximum magnitude (magForParSectMap)
		HashMap<String, Double> magForParSectMap = new HashMap<String, Double>();
		double maxMinSeismoMag = 0;
		double minMinSeismoMag = 0; // this is for testing
		for (int s = 0; s < sectDataList.size(); s++) {
			String parSectName = sectDataList.get(s).getParentSectionName();
			double minSeismoMag = fltSystRupSet.getOrigMinMagForSection(s);
			if (!parSectName.equals(prevParSectName)) { // it's a new parent section
				// set the previous result
				if (!prevParSectName.equals("junk")) {
					magForParSectMap.put(prevParSectName, maxMinSeismoMag);
				}
				// reset maxMinSeismoMag & prevParSectName
				maxMinSeismoMag = minSeismoMag;
				minMinSeismoMag = minSeismoMag;
				prevParSectName = parSectName;

			} else {
				if (maxMinSeismoMag < minSeismoMag)
					maxMinSeismoMag = minSeismoMag;
				if (minMinSeismoMag > minSeismoMag)
					minMinSeismoMag = minSeismoMag;
			}
		}
		// do the last one:
		magForParSectMap.put(prevParSectName, maxMinSeismoMag);

		// for(String parName:magForParSectMap.keySet())
		// System.out.println(parName+"\t"+magForParSectMap.get(parName));

		// now set the value for each section in the array, giving a value of
		// systemWideMinMinSeismoMag
		// if the parent section value falls below this
		for (int s = 0; s < sectDataList.size(); s++) {
			double minMag = magForParSectMap.get(sectDataList.get(s).getParentSectionName());
			if (minMag > systemWideMinSeismoMag)
				minMagForSect[s] = minMag;
			else
				minMagForSect[s] = systemWideMinSeismoMag;
		}

		return minMagForSect;

//		// test result:
//		try {
//			FileWriter fw = new FileWriter("TestHereItIs");
//			for(int s=0; s< sectDataList.size();s++) {
//				String sectName = sectDataList.get(s).getSectionName();
//				double tempMag = magForParSectMap.get(sectDataList.get(s).getParentSectionName());
//				double origSlipRate = sectDataList.get(s).getOrigAveSlipRate();
//				double aseisSlipFactor = sectDataList.get(s).getAseismicSlipFactor();
//				fw.write(sectName+"\t"+getMinMagForSection(s)+"\t"+tempMag+"\t"+minMagForSect[s]+"\t"+origSlipRate+"\t"+aseisSlipFactor+"\n");
//			}
//			fw.close ();
//		}
//		catch (IOException e) {
//			System.out.println ("IO exception = " + e );
//		}

	}

}

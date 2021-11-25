package nz.cri.gns.NZSHM22.opensha.enumTreeBranches;

import nz.cri.gns.NZSHM22.opensha.ruptures.downDip.DownDipSubSectBuilder;
import nz.cri.gns.NZSHM22.opensha.util.FaultSectionList;
import org.dom4j.DocumentException;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.XMLUtils;
import scratch.UCERF3.enumTreeBranches.FaultModels;

import java.io.*;
import java.util.List;
import java.util.Map;

public enum NZSHM22_FaultModels implements LogicTreeNode {

	CFM_0_9_ALL_2010("CFM 0.9 all NZ faults, 2010 Stirling depth", "cfm_0_9_d90_all_stirling_depths.xml"),
	CFM_0_9_SANSTVZ_2010("CFM 0.9 sans TVZ, 2010 Stirling depth", "cfm_0_9_d90_no_tvz_stirling_depths.xml"),

	CFM_0_9_ALL_D90("CFM 0.9 all NZ faults, depth 90", "cfm_0_9_d90_all.xml"),
	CFM_0_9_SANSTVZ_D90("CFM 0.9 sans TVZ, depth 90", "cfm_0_9_d90_no_tvz.xml"),

	CFM_0_3_SANSTVZ("CFM 0.3 sans TVZ", "SANSTVZ2_crustal_opensha.xml"),

	// these two are missing the slip_deficit (mm/yr) column
//    SBD_0_1_HKR_30("Hikurangi 30km", "subduction_tile_parameters_30.csv", 10000),
//    SBD_0_1_HKR_10("Hikurangi 10km", "subduction_tile_parameters.csv", 10000),
	SBD_0_1_HKR_KRM_10("Hikurangi,Kermadec 10km", "hk_tile_parameters_10.csv", 10000),
	SBD_0_1_HKR_KRM_30("Hikurangi,Kermadec 30km", "hk_tile_parameters_30.csv", 10000),

	SBD_0_1_HKR_LR_10("Hikurangi, Kermadec to Louisville ridge, 10km", "hk_tile_parameters_10-short.csv", 10000),
	SBD_0_1_HKR_LR_30("Hikurangi, Kermadec to Louisville ridge, 30km", "hk_tile_parameters_30-short.csv", 10000),

	SBD_0_1_HKR_LR_10_FEC("Hikurangi, Kermadec to Louisville ridge, 10km - with slip deficit smoothed near east cape",
			"hk_tile_parameters_10-short-flat-eastcape.csv", 10000),
	SBD_0_1_HKR_LR_30_FEC("Hikurangi, Kermadec to Louisville ridge, 30km - with slip deficit smoothed near east cape",
			"hk_tile_parameters_30-short-flat-eastcape.csv", 10000),

	SBD_0_2_HKR_LR_30("Hikurangi, Kermadec to Louisville ridge, 30km - with slip deficit smoothed near east cape",
			"hk_tile_parameters_creeping_trench_slip_deficit_v2_30.csv", 10000),
	
	SBD_0_2A_HKR_LR_30("Hikurangi, Kermadec to Louisville ridge, 30km - with slip deficit smoothed near east cape",
			"hk_tile_parameters_creeping_trench_slip_deficit_v2a_30.csv", 10000),

	SBD_0_3_HKR_LR_30("Hikurangi, Kermadec to Louisville ridge, 30km - with slip deficit smoothed near East Cape and locked near trench.",
					  "hk_tile_parameters_locked_trench_slip_deficit_v2_30.csv", 10000),

	SBD_0_4_HKR_LR_30("Hikurangi, Kermadec to Louisville ridge, 30km - higher overall slip rates, aka Kermits revenge",
			"hk_tile_parameters_highkermsliprate_v2.csv", 100000);

	private final static String RESOURCE_PATH = "/faultModels/";

	private final String modelName;
	private final String fileName;
	private final boolean crustal;
	private final int id;

	private Map<String, List<Integer>> namedFaultsMapAlt;

	NZSHM22_FaultModels(String modelName, String fileName) {
		this.modelName = modelName;
		this.fileName = fileName;
		this.crustal = true;
		this.id = -1;
	}

	NZSHM22_FaultModels(String modelName, String fileName, int subductionId) {
		this.modelName = modelName;
		this.fileName = fileName;
		this.crustal = false;
		this.id = subductionId;
	}

	public InputStream getStream(String fileName) {
		return getClass().getResourceAsStream(RESOURCE_PATH + fileName);
	}

	/**
	 * Loads the sections of the fault model into sections.
	 *
	 * @param sections a list to be populated by fault sections.
	 * @throws IOException
	 * @throws DocumentException
	 */
	public void fetchFaultSections(FaultSectionList sections) throws IOException, DocumentException {
		try (InputStream in = getStream(fileName)) {
			if (crustal) {
				sections.addAll(FaultModels.loadStoredFaultSections(XMLUtils.loadDocument(in)));
			} else {
				DownDipSubSectBuilder.loadFromStream(sections, id, modelName, in);
			}
		}
	}

	/**
	 * This returns a mapping between a named fault (String keys) and the sections
	 * included in the named fault (sections IDs ids). name.
	 *
	 * @return the map from fault name to section Ids
	 */
	public Map<String, List<Integer>> getNamedFaultsMapAlt() {
		if (namedFaultsMapAlt == null) {
			synchronized (this) {
				if (namedFaultsMapAlt == null) {
					InputStream in = getStream(fileName + ".FaultsByNameAlt.txt");
					if (in != null) {
						try (Reader reader = new InputStreamReader(in)) {
							namedFaultsMapAlt = FaultModels.parseNamedFaultsAltFile(reader);
						} catch (Throwable t) {
							throw ExceptionUtils.asRuntimeException(t);
						}
					}
				}
			}
		}
		return namedFaultsMapAlt;
	}

	public boolean isCrustal() {
		return crustal;
	}

	@Override
	public String getName() {
		return modelName;
	}

	@Override
	public String getShortName() {
		return getName();
	}

	@Override
	public String toString() {
		return getName();
	}

	public String getFileName(){
		return fileName;
	}

	@Override
	public double getNodeWeight(LogicTreeBranch<?> fullBranch) {
		return 0;
	}

	@Override
	public String getFilePrefix() {
		return null;
	}
}

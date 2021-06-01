package nz.cri.gns.NZSHM22.opensha.enumTreeBranches;

import nz.cri.gns.NZSHM22.opensha.ruptures.downDip.DownDipSubSectBuilder;
import nz.cri.gns.NZSHM22.opensha.util.FaultSectionList;
import org.dom4j.DocumentException;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.XMLUtils;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.InversionModels;
import scratch.UCERF3.logicTree.LogicTreeBranchNode;
import scratch.UCERF3.utils.UCERF3_DataUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;
import java.util.Map;

public enum NZSHM22_FaultModels implements LogicTreeBranchNode<NZSHM22_FaultModels> {

    CFM_0_9_ALL_2010("CFM 0.9 all NZ faults, 2010 Stirling depth", "cfm_0_9_d90_all_stirling_depths.xml"),
    CFM_0_9_SANSTVZ_2010("CFM 0.9 sans TVZ, 2010 Stirling depth", "cfm_0_9_d90_no_tvz_stirling_depths.xml"),

    CFM_0_9_ALL_D90("CFM 0.9 all NZ faults, depth 90", "cfm_0_9_d90_all.xml"),
    CFM_0_9_SANSTVZ_D90("CFM 0.9 sans TVZ, depth 90", "cfm_0_9_d90_no_tvz.xml"),

    CFM_0_3_SANSTVZ("CFM 0.3 sans TVZ", "SANSTVZ2_crustal_opensha.xml"),

    // these two are missing the slip_deficit (mm/yr) column
//    SBD_0_1_HKR_30("Hikurangi 30km", "subduction_tile_parameters_30.csv", 10000),
//    SBD_0_1_HKR_10("Hikurangi 10km", "subduction_tile_parameters.csv", 10000),
    SBD_0_1_HKR_KRM_10("Hikurangi,Kermadec 10km", "hk_tile_parameters_10.csv", 10000),
    SBD_0_1_HKR_KRM_30("Hikurangi,Kermadec 30km", "hk_tile_parameters_30.csv", 10000);

    private final static String resourcePath = "/faultModels/";

    private final double weight;
    private final String modelName;
    private final String fileName;
    private final boolean crustal;
    private final int id;

    private Map<String, List<Integer>> namedFaultsMapAlt;

    private NZSHM22_FaultModels(String modelName, String fileName) {
        this.modelName = modelName;
        this.fileName = fileName;
        this.crustal = true;
        this.id = -1;
        this.weight = 1.0;
    }

    private NZSHM22_FaultModels(String modelName, String fileName, int subductionId) {
        this.modelName = modelName;
        this.fileName = fileName;
        this.crustal = false;
        this.id = subductionId;
        this.weight = 1.0;
    }

    protected InputStream getStream(String fileName) {
        return getClass().getResourceAsStream(resourcePath + fileName);
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
     * This returns a mapping between a named fault (String keys) and the sections included in the
     * named fault (sections IDs ids).
     * name.
     *
     * @return the map from fault name to section Ids
     */
    public Map<String, List<Integer>> getNamedFaultsMapAlt() {
        if (namedFaultsMapAlt == null) {
            synchronized (this) {
                if (namedFaultsMapAlt == null) {
                    try (Reader reader = new InputStreamReader(getStream(fileName + ".FaultsByNameAlt.txt"))) {
                        namedFaultsMapAlt = FaultModels.parseNamedFaultsAltFile(reader);
                    } catch (Throwable t) {
                        throw ExceptionUtils.asRuntimeException(t);
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
    public double getRelativeWeight(InversionModels im) {
        return weight;
    }

    @Override
    public String encodeChoiceString() {
        return getShortName();
    }

    @Override
    public String getBranchLevelName() {
        return "Fault Model";
    }

    @Override
    public String toString() {
        return getName();
    }
}

package nz.cri.gns.NZSHM22.opensha.enumTreeBranches;

import java.io.*;
import java.util.List;
import java.util.Map;
import nz.cri.gns.NZSHM22.opensha.faults.FaultSectionList;
import nz.cri.gns.NZSHM22.opensha.faults.NZFaultSection;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionPredicate;
import nz.cri.gns.NZSHM22.opensha.ruptures.downDip.DownDipSubSectBuilder;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.XMLUtils;
import org.opensha.sha.faultSurface.FaultSection;
import scratch.UCERF3.enumTreeBranches.FaultModels;

public enum NZSHM22_FaultModels implements LogicTreeNode {
    CUSTOM("The fault model is specified in a file outside the logic tree", null, "4"),

    // CFM 1.0 crustal files without either A-US or 0-slip rate, with and without slow TVZ faults,
    // with depths scaled from Dfc and CFM Domains
    // depth filtering: nothing shallower than 3.9
    CFM_1_0A_DOM_ALL(
            "CFM 1.0 all NZ faults, with Dfc depths and CFM Domains", "cfm_1_0A_all.xml", "4"),
    CFM_1_0A_DOM_SANSTVZ(
            "CFM 1.0 sans TVZ faults, with Dfc depths and CFM Domains", "cfm_1_0A_no_tvz.xml", "4"),

    // CFM 1.0 crustal files without either A-US or 0-slip rate, with and without slow TVZ faults,
    // with depths scaled from Dfc and CFM Domains
    CFM_1_0_DOM_ALL(
            "CFM 1.0 all NZ faults, with Dfc depths and CFM Domains",
            "cfm_1_0_domain_all.xml",
            "4"),
    CFM_1_0_DOM_SANSTVZ(
            "CFM 1.0 sans TVZ faults, with Dfc depths and CFM Domains",
            "cfm_1_0_domain_no_tvz.xml",
            "4"),

    // CFM 0.9 crustal files without either A-US or 0-slip rate, with and without slow TVZ faults,
    // with shallower TVZ depths
    CFM_0_9D_ALL_D90(
            "CFM 0.9revD all NZ faults, depth 90, with shallower TVZ depths",
            "cfm_0_9d_d90_all.xml"),
    CFM_0_9D_SANSTVZ_D90(
            "CFM 0.9revD sans TVZ, depth 90, with shallower TVZ depths", "cfm_0_9d_d90_no_tvz.xml"),

    // CFM 0.9 crustal files without either A-US or 0-slip rate, with and without slow TVZ faults
    CFM_0_9C_ALL_D90("CFM 0.9revC all NZ faults, depth 90", "cfm_0_9c_d90_all.xml"),
    CFM_0_9C_SANSTVZ_D90("CFM 0.9revC sans TVZ, depth 90", "cfm_0_9c_d90_no_tvz.xml"),

    CFM_0_9C_ALL_2010(
            "CFM 0.9revC all NZ faults, 2010 Stirling depth",
            "cfm_0_9c_d90_all_stirling_depths.xml"),
    CFM_0_9C_SANSTVZ_2010(
            "CFM 0.9revC sans TVZ, 2010 Stirling depth", "cfm_0_9c_d90_no_tvz_stirling_depths.xml"),

    // CFM 0.9 crustal files without either A-US or 0-slip rate, with and without slow TVZ faults
    @Deprecated
    CFM_0_9B_ALL_D90("CFM 0.9revB all NZ faults, depth 90", "cfm_0_9b_d90_all.xml"),
    @Deprecated
    CFM_0_9B_SANSTVZ_D90("CFM 0.9revB sans TVZ, depth 90", "cfm_0_9b_d90_no_tvz.xml"),
    @Deprecated
    CFM_0_9B_ALL_2010(
            "CFM 0.9revB all NZ faults, 2010 Stirling depth",
            "cfm_0_9b_d90_all_stirling_depths.xml"),
    @Deprecated
    CFM_0_9B_SANSTVZ_2010(
            "CFM 0.9revB sans TVZ, 2010 Stirling depth", "cfm_0_9b_d90_no_tvz_stirling_depths.xml"),

    // These for deprecated models have their rake 180 degrees off
    @Deprecated
    CFM_0_9A_ALL_D90("CFM 0.9revA all NZ faults, depth 90", "cfm_0_9a_d90_all.xml"),
    @Deprecated
    CFM_0_9A_SANSTVZ_D90("CFM 0.9revA sans TVZ, depth 90", "cfm_0_9a_d90_no_tvz.xml"),
    @Deprecated
    CFM_0_9_ALL_2010(
            "CFM 0.9 all NZ faults, 2010 Stirling depth", "cfm_0_9_d90_all_stirling_depths.xml"),
    @Deprecated
    CFM_0_9_SANSTVZ_2010(
            "CFM 0.9 sans TVZ, 2010 Stirling depth", "cfm_0_9_d90_no_tvz_stirling_depths.xml"),

    CFM_0_9_ALL_D90("CFM 0.9 all NZ faults, depth 90", "cfm_0_9_d90_all.xml"),
    CFM_0_9_SANSTVZ_D90("CFM 0.9 sans TVZ, depth 90", "cfm_0_9_d90_no_tvz.xml"),

    CFM_0_3_SANSTVZ("CFM 0.3 sans TVZ", "SANSTVZ2_crustal_opensha.xml"),

    // these two are missing the slip_deficit (mm/yr) column
    //    SBD_0_1_HKR_30("Hikurangi 30km", "subduction_tile_parameters_30.csv", 10000),
    //    SBD_0_1_HKR_10("Hikurangi 10km", "subduction_tile_parameters.csv", 10000),
    SBD_0_1_HKR_KRM_10(
            "Hikurangi,Kermadec 10km",
            "hk_tile_parameters_10.csv",
            10000,
            PartitionPredicate.HIKURANGI),
    SBD_0_1_HKR_KRM_30(
            "Hikurangi,Kermadec 30km",
            "hk_tile_parameters_30.csv",
            10000,
            PartitionPredicate.HIKURANGI),

    SBD_0_1_HKR_LR_10(
            "Hikurangi, Kermadec to Louisville ridge, 10km",
            "hk_tile_parameters_10-short.csv",
            10000,
            PartitionPredicate.HIKURANGI),
    SBD_0_1_HKR_LR_30(
            "Hikurangi, Kermadec to Louisville ridge, 30km",
            "hk_tile_parameters_30-short.csv",
            10000,
            PartitionPredicate.HIKURANGI),

    SBD_0_1_HKR_LR_10_FEC(
            "Hikurangi, Kermadec to Louisville ridge, 10km - with slip deficit smoothed near east cape",
            "hk_tile_parameters_10-short-flat-eastcape.csv",
            10000,
            PartitionPredicate.HIKURANGI),
    SBD_0_1_HKR_LR_30_FEC(
            "Hikurangi, Kermadec to Louisville ridge, 30km - with slip deficit smoothed near east cape",
            "hk_tile_parameters_30-short-flat-eastcape.csv",
            10000,
            PartitionPredicate.HIKURANGI),

    SBD_0_2_HKR_LR_30(
            "Hikurangi, Kermadec to Louisville ridge, 30km - with slip deficit smoothed near east cape",
            "hk_tile_parameters_creeping_trench_slip_deficit_v2_30.csv",
            10000,
            PartitionPredicate.HIKURANGI),

    // this model is mislabled 30km, it has 15km tiles
    SBD_0_1_PUY_30(
            "Puysegur, 30km, 50% coupling",
            "puysegur_tiles_30km_maxd60km_halfcoupled.csv", 10000, PartitionPredicate.PUYSEGUR),
    // dip direction for SBD_01_PUY_30 was to the west, causing the fault tiles to be "louvered" off
    // the sudcution interface surface
    // SBD_0_2_PUY_15 corrects the dip direction (right-hand rule) and indicates the correct tile
    // size (15km)
    SBD_0_2_PUY_15(
            "Puysegur, 15km, 50% coupling, corrected dip direction",
            "puysegur_tiles_15km_maxd60km_halfcoupled_dipcorr.csv",
            10000,
            PartitionPredicate.PUYSEGUR),

    // the following three FaultModels have been replaced by DeformationModels

    @Deprecated
    SBD_0_2A_HKR_LR_30(
            "Hikurangi, Kermadec to Louisville ridge, 30km - with slip deficit smoothed near east cape",
            "hk_tile_parameters_creeping_trench_slip_deficit_v2a_30.csv",
            10000,
            PartitionPredicate.HIKURANGI),
    @Deprecated
    SBD_0_3_HKR_LR_30(
            "Hikurangi, Kermadec to Louisville ridge, 30km - with slip deficit smoothed near East Cape and locked near trench.",
            "hk_tile_parameters_locked_trench_slip_deficit_v2_30.csv",
            10000,
            PartitionPredicate.HIKURANGI),
    @Deprecated
    SBD_0_4_HKR_LR_30(
            "Hikurangi, Kermadec to Louisville ridge, 30km - higher overall slip rates, aka Kermits revenge",
            "hk_tile_parameters_highkermsliprate_v2.csv",
            10000,
            PartitionPredicate.HIKURANGI);

    private static final String RESOURCE_PATH = "/faultModels/";

    private final String modelName;
    private final String fileName;
    private final boolean crustal;
    private final int id;
    private final String tvzDomain;
    private final PartitionPredicate partition;

    private Map<String, List<Integer>> namedFaultsMapAlt;

    private String customModel;
    private String customNamedFaults;

    NZSHM22_FaultModels(String modelName, String fileName) {
        this.modelName = modelName;
        this.fileName = fileName;
        this.crustal = true;
        this.id = -1;
        this.tvzDomain = null;
        this.partition = PartitionPredicate.CRUSTAL;
    }

    NZSHM22_FaultModels(String modelName, String fileName, String tvzDomain) {
        this.modelName = modelName;
        this.fileName = fileName;
        this.crustal = true;
        this.id = -1;
        this.tvzDomain = tvzDomain;
        this.partition = PartitionPredicate.CRUSTAL;
    }

    NZSHM22_FaultModels(
            String modelName, String fileName, int subductionId, PartitionPredicate partition) {
        this.modelName = modelName;
        this.fileName = fileName;
        this.crustal = false;
        this.id = subductionId;
        this.tvzDomain = null;
        this.partition = partition;
    }

    /**
     * Sets the custom model as text data (XML for crustal, CSV for subduction)
     *
     * @param customModel
     */
    public void setCustomModel(String customModel) {
        this.customModel = customModel;
    }

    public String getCustomModel() {
        return customModel;
    }

    public void setCustomNamedFaults(String customNamedFaults) {
        this.customNamedFaults = customNamedFaults;
    }

    public String getCustomNamedFaults() {
        return customNamedFaults;
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
    public void fetchFaultSections(FaultSectionList sections)
            throws IOException, DocumentException {
        if (customModel != null) {
            try (InputStream in = new ByteArrayInputStream(customModel.getBytes())) {
                fetchFaultSections(sections, in, crustal, id, modelName, partition);
            }
        }
        if (fileName != null) {
            try (InputStream in = getStream(fileName)) {
                fetchFaultSections(sections, in, crustal, id, modelName, partition);
            }
        }
    }

    public static void fetchFaultSections(
            FaultSectionList sections,
            InputStream in,
            boolean crustal,
            int subductionId,
            String modelName,
            PartitionPredicate partition)
            throws IOException, DocumentException {
        if (crustal) {
            loadStoredFaultSections(sections, XMLUtils.loadDocument(in));
        } else {
            DownDipSubSectBuilder.loadFromStream(sections, subductionId, modelName, in, partition);
        }
    }

    public static void loadStoredFaultSections(FaultSectionList sections, Document doc) {
        Element el = doc.getRootElement().element("FaultModel");
        for (int i = 0; i < el.elements().size(); i++) {
            Element subEl = el.element("i" + i);
            FaultSection sect;
            sect = NZFaultSection.fromXMLMetadata(subEl);
            sections.add(sect);
        }
    }

    /**
     * This returns a mapping between a named fault (String keys) and the sections included in the
     * named fault (sections IDs ids). name.
     *
     * @return the map from fault name to section Ids
     */
    public Map<String, List<Integer>> getNamedFaultsMapAlt() {
        if (namedFaultsMapAlt == null) {
            synchronized (this) {
                if (namedFaultsMapAlt == null) {
                    InputStream in;
                    if (customNamedFaults != null) {
                        in = new ByteArrayInputStream(customNamedFaults.getBytes());
                    } else {
                        in = getStream(fileName + ".FaultsByNameAlt.txt");
                    }
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

    public String getFileName() {
        return fileName;
    }

    public int getParentSectionId() {
        return id;
    }

    public String getTvzDomain() {
        return tvzDomain;
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

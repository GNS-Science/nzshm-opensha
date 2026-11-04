package nz.cri.gns.NZSHM22.opensha.enumTreeBranches;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import nz.cri.gns.NZSHM22.opensha.faults.FaultSectionList;
import nz.cri.gns.NZSHM22.opensha.inputs.FaultModels;
import nz.cri.gns.NZSHM22.opensha.inputs.InputModelMetaData;
import org.dom4j.DocumentException;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;

public enum NZSHM22_FaultModels implements LogicTreeNode {
    CUSTOM(null),

    // CRUSTAL/UPPER-PLATE
    // CFM 1.0 crustal files without either A-US or 0-slip rate, with and without slow TVZ faults,
    // with depths scaled from Dfc and CFM Domains
    // depth filtering: nothing shallower than 3.9
    CFM_1_0A_DOM_ALL("cfm_1_0A_all.xml"),
    CFM_1_0A_DOM_SANSTVZ("cfm_1_0A_no_tvz.xml"),

    // CFM 1.0 crustal files without either A-US or 0-slip rate, with and without slow TVZ faults,
    // with depths scaled from Dfc and CFM Domains
    CFM_1_0_DOM_ALL("cfm_1_0_domain_all.xml"),
    CFM_1_0_DOM_SANSTVZ("cfm_1_0_domain_no_tvz.xml"),

    // HIKURANGI-KERMADEC
    // Trench-creeping model used in 2022
    @Deprecated
    SBD_0_2A_HKR_LR_30("hk_tile_parameters_creeping_trench_slip_deficit_v2a_30.csv"),

    // 2022 trench-creeping model, but trimmed at 25.5 S (actual Louisville Ridge) - Sept 2026
    SBD_HKR_TC_30_TRIM("hk_tile_parameters_creeping_trench_slip_deficit_v2a_30_trimmed.csv"),

    // Trench-locked model used in 2022
    SBD_0_3_HKR_LR_30("hk_tile_parameters_locked_trench_slip_deficit_v2_30.csv"),

    // 2022 trench-locked model, but trimmed at 25.5 S (actual Louisville Ridge) - Sept 2026
    SBD_HKR_TL_30_TRIM("hk_tile_parameters_locked_trench_slip_deficit_v2_30_trimmed.csv"),

    // PUYSEGUR
    // dip direction for SBD_01_PUY_30 was to the west, causing the fault tiles to be "louvered" off
    // the subduction interface surface
    // SBD_0_2_PUY_15 corrects the dip direction (right-hand rule) and indicates the correct tile
    // size (15km)
    SBD_0_2_PUY_15("puysegur_tiles_15km_maxd60km_halfcoupled_dipcorr.csv"),

    // Deprecated files in folder old
    // CFM 0.9 crustal files without either A-US or 0-slip rate, with and without slow TVZ faults,
    // with shallower TVZ depths
    @Deprecated
    CFM_0_9D_ALL_D90("old/cfm_0_9d_d90_all.xml"),
    @Deprecated
    CFM_0_9D_SANSTVZ_D90("old/cfm_0_9d_d90_no_tvz.xml"),

    // CFM 0.9 crustal files without either A-US or 0-slip rate, with and without slow TVZ faults
    @Deprecated
    CFM_0_9C_ALL_D90("old/cfm_0_9c_d90_all.xml"),
    @Deprecated
    CFM_0_9C_SANSTVZ_D90("old/cfm_0_9c_d90_no_tvz.xml"),

    @Deprecated
    CFM_0_9C_ALL_2010("old/cfm_0_9c_d90_all_stirling_depths.xml"),
    @Deprecated
    CFM_0_9C_SANSTVZ_2010("old/cfm_0_9c_d90_no_tvz_stirling_depths.xml"),

    // CFM 0.9 crustal files without either A-US or 0-slip rate, with and without slow TVZ faults
    @Deprecated
    CFM_0_9B_ALL_D90("old/cfm_0_9b_d90_all.xml"),
    @Deprecated
    CFM_0_9B_SANSTVZ_D90("old/cfm_0_9b_d90_no_tvz.xml"),
    @Deprecated
    CFM_0_9B_ALL_2010("old/cfm_0_9b_d90_all_stirling_depths.xml"),
    @Deprecated
    CFM_0_9B_SANSTVZ_2010("old/cfm_0_9b_d90_no_tvz_stirling_depths.xml"),

    // These for deprecated models have their rake 180 degrees off
    @Deprecated
    CFM_0_9A_ALL_D90("old/cfm_0_9a_d90_all.xml"),
    @Deprecated
    CFM_0_9A_SANSTVZ_D90("old/cfm_0_9a_d90_no_tvz.xml"),
    @Deprecated
    CFM_0_9_ALL_2010("old/cfm_0_9_d90_all_stirling_depths.xml"),
    @Deprecated
    CFM_0_9_SANSTVZ_2010("old/cfm_0_9_d90_no_tvz_stirling_depths.xml"),

    @Deprecated
    CFM_0_9_ALL_D90("old/cfm_0_9_d90_all.xml"),
    @Deprecated
    CFM_0_9_SANSTVZ_D90("old/cfm_0_9_d90_no_tvz.xml"),

    @Deprecated
    CFM_0_3_SANSTVZ("old/SANSTVZ2_crustal_opensha.xml"),

    // these two are missing the slip_deficit (mm/yr) column
    //    SBD_0_1_HKR_30("Hikurangi 30km",
    // "subduction_tile_parameters_30.csv", 10000),
    //    SBD_0_1_HKR_10("Hikurangi 10km",
    // "subduction_tile_parameters.csv", 10000),
    @Deprecated
    SBD_0_1_HKR_KRM_10("old/hk_tile_parameters_10.csv"),
    @Deprecated
    SBD_0_1_HKR_KRM_30("old/hk_tile_parameters_30.csv"),

    @Deprecated
    SBD_0_1_HKR_LR_10("old/hk_tile_parameters_10-short.csv"),
    @Deprecated
    SBD_0_1_HKR_LR_30("old/hk_tile_parameters_30-short.csv"),

    @Deprecated
    SBD_0_1_HKR_LR_10_FEC("old/hk_tile_parameters_10-short-flat-eastcape.csv"),
    @Deprecated
    SBD_0_1_HKR_LR_30_FEC("old/hk_tile_parameters_30-short-flat-eastcape.csv"),

    @Deprecated
    SBD_0_2_HKR_LR_30("old/hk_tile_parameters_creeping_trench_slip_deficit_v2_30.csv"),

    @Deprecated
    SBD_0_4_HKR_LR_30("old/hk_tile_parameters_highkermsliprate_v2.csv"),

    // this model is mislabled 30km, it has 15km tiles
    @Deprecated
    SBD_0_1_PUY_30("old/puysegur_tiles_30km_maxd60km_halfcoupled.csv");

    private final FaultModels model;

    /**
     * @param fileName path below the faultModels folder of nzshm-input-models, or null for a custom
     *     model. Model properties are read from the file's meta data.
     */
    NZSHM22_FaultModels(String fileName) {
        this.model =
                fileName == null
                        ? new FaultModels(InputModelMetaData.parse(FaultModels.CUSTOM_META_DATA))
                        : new FaultModels(fileName);
    }

    /** The fault model that this logic tree node passes through to. */
    public FaultModels getModel() {
        return model;
    }

    /**
     * Sets the custom model as text data (XML for crustal, CSV for subduction)
     *
     * @param customModel
     */
    public void setCustomModel(String customModel) {
        model.setCustomModel(customModel);
    }

    public String getCustomModel() {
        return model.getCustomModel();
    }

    public void setCustomNamedFaults(String customNamedFaults) {
        model.setCustomNamedFaults(customNamedFaults);
    }

    public String getCustomNamedFaults() {
        return model.getCustomNamedFaults();
    }

    /**
     * Opens a fault model file from the main branch of the nzshm-input-models repo.
     *
     * @return the file's content, or null if the file does not exist or this model has no file
     */
    public InputStream getStream(String fileName) {
        if (model.getFileName() == null) {
            return null;
        }
        return FaultModels.getStream(fileName);
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
        model.fetchFaultSections(sections);
    }

    /**
     * This returns a mapping between a named fault (String keys) and the sections included in the
     * named fault (sections IDs ids). name.
     *
     * @return the map from fault name to section Ids
     */
    public Map<String, List<Integer>> getNamedFaultsMapAlt() {
        return model.getNamedFaultsMapAlt();
    }

    public boolean isCrustal() {
        return model.isCrustal();
    }

    @Override
    public String getName() {
        return model.getModelName();
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
        return model.getFileName();
    }

    public int getParentSectionId() {
        return model.getParentSectionId();
    }

    public String getTvzDomain() {
        return model.getTvzDomain();
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

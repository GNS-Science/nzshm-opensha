package nz.cri.gns.NZSHM22.opensha.enumTreeBranches;

import java.io.IOException;
import java.util.function.IntPredicate;
import nz.cri.gns.NZSHM22.opensha.inputs.DeformationModel;
import nz.cri.gns.NZSHM22.opensha.inputs.InputModelMetaData;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;

public enum NZSHM22_DeformationModel implements LogicTreeNode {
    FAULT_MODEL(DeformationModel.FAULT_MODEL),

    CUSTOM(DeformationModel.CUSTOM),

    GEOD_NO_PRIOR_2022_RmlsZToxMDAwODc_(
            "slip_deficit_rates_no_prior_correcteduncert_NZSHM22_RuptureSet-UnVwdHVyZUdlbmVyYXRpb25UYXNrOjEwMDAzOA==.dat"),
    GEOD_PRIOR_2022_RmlsZToxMDAwODc_(
            "slip_deficit_rates_with_prior_correcteduncert_NZSHM22_RuptureSet-UnVwdHVyZUdlbmVyYXRpb25UYXNrOjEwMDAzOA==.dat"),
    GEOD_NO_PRIOR_2022_RmlsZToyMjE4My4wUGVpWGE_(
            "slip_deficit_rates_no_prior_NZSHM22_RuptureSet-UnVwdHVyZUdlbmVyYXRpb25UYXNrOjc5OTBvWWZMVw.dat"),

    GEOD_PRIOR_2022_RmlsZToyMjE4My4wUGVpWGE_(
            "slip_deficit_rates_with_prior_NZSHM22_RuptureSet-UnVwdHVyZUdlbmVyYXRpb25UYXNrOjc5OTBvWWZMVw.dat"),

    GEOD_NO_PRIOR_UNISTD_2010_RmlsZTo4NTkuMDM2Z2Rw(
            "slip_deficit_rates_no_prior_uniform-stddev_2010_RmlsZTo4NTkuMDM2Z2Rw.dat"),

    GEOD_NO_PRIOR_UNISTD_D90_RmlsZTozMDMuMEJCOVVY(
            "slip_deficit_rates_no_prior_uniform-stddev_D90_RmlsZTozMDMuMEJCOVVY.dat"),

    GEOD_NO_PRIOR_2010_RmlsZTo4NTkuMDM2Z2Rw_OFF_GEOL(
            "slip_deficit_rates_no_prior_2010_RmlsZTo4NTkuMDM2Z2Rw_offshore_swap.dat"),

    GEOD_PRIOR_2010_RmlsZTo4NTkuMDM2Z2Rw_OFF_GEOL(
            "slip_deficit_rates_with_prior_2010_RmlsZTo4NTkuMDM2Z2Rw_offshore_swap.dat"),

    GEOD_NO_PRIOR_D90_RmlsZTozMDMuMEJCOVVY_OFF_GEOL(
            "slip_deficit_rates_no_prior_D90_RmlsZTozMDMuMEJCOVVY_offshore_swap.dat"),

    GEOD_PRIOR_D90_RmlsZTozMDMuMEJCOVVY_OFF_GEOL(
            "slip_deficit_rates_with_prior_D90_RmlsZTozMDMuMEJCOVVY_offshore_swap.dat"),

    // HIKURANGI-KERMADEC
    // Trench-creeping model used in 2022
    SBD_0_2A_HKR_LR_30("dm_hk_tile_parameters_creeping_trench_slip_deficit_v2a_30.csv"),

    // 2022 trench-creeping model, but trimmed at 25.5 S (actual Louisville Ridge) - Sept 2026
    SBD_HKR_TC_30_TRIM("dm_hk_tile_parameters_creeping_trench_slip_deficit_v2a_30_trimmed.csv"),

    // Trench-locked model used in 2022
    SBD_0_3_HKR_LR_30("dm_hk_tile_parameters_locked_trench_slip_deficit_v2_30.csv"),

    // 2022 trench-locked model, but trimmed at 25.5 S (actual Louisville Ridge) - Sept 2026
    SBD_HKR_TL_30_TRIM("dm_hk_tile_parameters_locked_trench_slip_deficit_v2_30_trimmed.csv"),

    // PUYSEGUR
    SBD_0_1_PUY_30_0PT4("dm_puysegur_tiles_30km_maxd60km_austpaci_0pt4coupled_notrand.csv"),
    SBD_0_1_PUY_30_0PT7("dm_puysegur_tiles_30km_maxd60km_austpaci_0pt7coupled_notrand.csv"),
    SBD_0_1_PUY_30_1PT0("dm_puysegur_tiles_30km_maxd60km_austpaci_1pt0coupled_notrand.csv"),

    // 2022 slip-rate-perturbed models for Hikurangi-Kermadec (to prevent small ruptures from having
    // weird spatial distributions)
    // I think one set may have the M7-7.5 moment contribution accounted for and the other might
    // not.

    SBD_0_2A_HKR_LR_30_MMIN7PT5_EXP_CTP1A(
            "sliprateperturbations/dm_hk_creepingtrench_Mmin7pt5_exp_perturbation1a.csv"),
    SBD_0_2A_HKR_LR_30_MMIN7PT5_EXP_CTP1B(
            "sliprateperturbations/dm_hk_creepingtrench_Mmin7pt5_exp_perturbation1b.csv"),
    SBD_0_2A_HKR_LR_30_MMIN7PT5_EXP_CTP2A(
            "sliprateperturbations/dm_hk_creepingtrench_Mmin7pt5_exp_perturbation2a.csv"),
    SBD_0_2A_HKR_LR_30_MMIN7PT5_EXP_CTP2B(
            "sliprateperturbations/dm_hk_creepingtrench_Mmin7pt5_exp_perturbation2b.csv"),
    SBD_0_2A_HKR_LR_30_MMIN7PT5_EXP_CTP3A(
            "sliprateperturbations/dm_hk_creepingtrench_Mmin7pt5_exp_perturbation3a.csv"),
    SBD_0_2A_HKR_LR_30_MMIN7PT5_EXP_CTP3B(
            "sliprateperturbations/dm_hk_creepingtrench_Mmin7pt5_exp_perturbation3b.csv"),
    SBD_0_2A_HKR_LR_30_MMIN7PT5_EXP_CTP4A(
            "sliprateperturbations/dm_hk_creepingtrench_Mmin7pt5_exp_perturbation4a.csv"),
    SBD_0_2A_HKR_LR_30_MMIN7PT5_EXP_CTP4B(
            "sliprateperturbations/dm_hk_creepingtrench_Mmin7pt5_exp_perturbation4b.csv"),
    SBD_0_2A_HKR_LR_30_MMIN7PT5_EXP_CTP5A(
            "sliprateperturbations/dm_hk_creepingtrench_Mmin7pt5_exp_perturbation5a.csv"),
    SBD_0_2A_HKR_LR_30_MMIN7PT5_EXP_CTP5B(
            "sliprateperturbations/dm_hk_creepingtrench_Mmin7pt5_exp_perturbation5b.csv"),

    SBD_0_2A_HKR_LR_30_MMIN7PT5_EXP_LTP1A(
            "sliprateperturbations/dm_hk_lockedtrench_Mmin7pt5_exp_perturbation1a.csv"),
    SBD_0_2A_HKR_LR_30_MMIN7PT5_EXP_LTP1B(
            "sliprateperturbations/dm_hk_lockedtrench_Mmin7pt5_exp_perturbation1b.csv"),
    SBD_0_2A_HKR_LR_30_MMIN7PT5_EXP_LTP2A(
            "sliprateperturbations/dm_hk_lockedtrench_Mmin7pt5_exp_perturbation2a.csv"),
    SBD_0_2A_HKR_LR_30_MMIN7PT5_EXP_LTP2B(
            "sliprateperturbations/dm_hk_lockedtrench_Mmin7pt5_exp_perturbation2b.csv"),
    SBD_0_2A_HKR_LR_30_MMIN7PT5_EXP_LTP3A(
            "sliprateperturbations/dm_hk_lockedtrench_Mmin7pt5_exp_perturbation3a.csv"),
    SBD_0_2A_HKR_LR_30_MMIN7PT5_EXP_LTP3B(
            "sliprateperturbations/dm_hk_lockedtrench_Mmin7pt5_exp_perturbation3b.csv"),
    SBD_0_2A_HKR_LR_30_MMIN7PT5_EXP_LTP4A(
            "sliprateperturbations/dm_hk_lockedtrench_Mmin7pt5_exp_perturbation4a.csv"),
    SBD_0_2A_HKR_LR_30_MMIN7PT5_EXP_LTP4B(
            "sliprateperturbations/dm_hk_lockedtrench_Mmin7pt5_exp_perturbation4b.csv"),
    SBD_0_2A_HKR_LR_30_MMIN7PT5_EXP_LTP5A(
            "sliprateperturbations/dm_hk_lockedtrench_Mmin7pt5_exp_perturbation5a.csv"),
    SBD_0_2A_HKR_LR_30_MMIN7PT5_EXP_LTP5B(
            "sliprateperturbations/dm_hk_lockedtrench_Mmin7pt5_exp_perturbation5b.csv"),

    SBD_0_2A_HKR_MMIN7PT5_EXP_CTP1A(
            "sliprateperturbations/dm_xhk_creepingtrench_Mmin7pt5_exp_perturbation1a.csv"),
    SBD_0_2A_HKR_MMIN7PT5_EXP_CTP1B(
            "sliprateperturbations/dm_xhk_creepingtrench_Mmin7pt5_exp_perturbation1b.csv"),
    SBD_0_2A_HKR_MMIN7PT5_EXP_CTP2A(
            "sliprateperturbations/dm_xhk_creepingtrench_Mmin7pt5_exp_perturbation2a.csv"),
    SBD_0_2A_HKR_MMIN7PT5_EXP_CTP2B(
            "sliprateperturbations/dm_xhk_creepingtrench_Mmin7pt5_exp_perturbation2b.csv"),
    SBD_0_2A_HKR_MMIN7PT5_EXP_CTP3A(
            "sliprateperturbations/dm_xhk_creepingtrench_Mmin7pt5_exp_perturbation3a.csv"),
    SBD_0_2A_HKR_MMIN7PT5_EXP_CTP3B(
            "sliprateperturbations/dm_xhk_creepingtrench_Mmin7pt5_exp_perturbation3b.csv"),
    SBD_0_2A_HKR_MMIN7PT5_EXP_CTP4A(
            "sliprateperturbations/dm_xhk_creepingtrench_Mmin7pt5_exp_perturbation4a.csv"),
    SBD_0_2A_HKR_MMIN7PT5_EXP_CTP4B(
            "sliprateperturbations/dm_xhk_creepingtrench_Mmin7pt5_exp_perturbation4b.csv"),
    SBD_0_2A_HKR_MMIN7PT5_EXP_CTP5A(
            "sliprateperturbations/dm_xhk_creepingtrench_Mmin7pt5_exp_perturbation5a.csv"),
    SBD_0_2A_HKR_MMIN7PT5_EXP_CTP5B(
            "sliprateperturbations/dm_xhk_creepingtrench_Mmin7pt5_exp_perturbation5b.csv"),
    SBD_0_2A_HKR_MMIN7PT5_EXP_LTP1A(
            "sliprateperturbations/dm_xhk_lockedtrench_Mmin7pt5_exp_perturbation1a.csv"),
    SBD_0_2A_HKR_MMIN7PT5_EXP_LTP1B(
            "sliprateperturbations/dm_xhk_lockedtrench_Mmin7pt5_exp_perturbation1b.csv"),
    SBD_0_2A_HKR_MMIN7PT5_EXP_LTP2A(
            "sliprateperturbations/dm_xhk_lockedtrench_Mmin7pt5_exp_perturbation2a.csv"),
    SBD_0_2A_HKR_MMIN7PT5_EXP_LTP2B(
            "sliprateperturbations/dm_xhk_lockedtrench_Mmin7pt5_exp_perturbation2b.csv"),
    SBD_0_2A_HKR_MMIN7PT5_EXP_LTP3A(
            "sliprateperturbations/dm_xhk_lockedtrench_Mmin7pt5_exp_perturbation3a.csv"),
    SBD_0_2A_HKR_MMIN7PT5_EXP_LTP3B(
            "sliprateperturbations/dm_xhk_lockedtrench_Mmin7pt5_exp_perturbation3b.csv"),
    SBD_0_2A_HKR_MMIN7PT5_EXP_LTP4A(
            "sliprateperturbations/dm_xhk_lockedtrench_Mmin7pt5_exp_perturbation4a.csv"),
    SBD_0_2A_HKR_MMIN7PT5_EXP_LTP4B(
            "sliprateperturbations/dm_xhk_lockedtrench_Mmin7pt5_exp_perturbation4b.csv"),
    SBD_0_2A_HKR_MMIN7PT5_EXP_LTP5A(
            "sliprateperturbations/dm_xhk_lockedtrench_Mmin7pt5_exp_perturbation5a.csv"),
    SBD_0_2A_HKR_MMIN7PT5_EXP_LTP5B(
            "sliprateperturbations/dm_xhk_lockedtrench_Mmin7pt5_exp_perturbation5b.csv"),

    // Deprecated
    @Deprecated
    SBD_0_4_HKR_LR_30("old/dm_hk_tile_parameters_highkermsliprate_v2.csv"),

    @Deprecated
    SBD_0_2A_HKR_LR_30_CTP1("old/dm_hk_eastcapesmoothed_PERTURBATION1.csv"),
    @Deprecated
    SBD_0_2A_HKR_LR_30_CTP2("old/dm_hk_eastcapesmoothed_PERTURBATION2.csv"),
    @Deprecated
    SBD_0_2A_HKR_LR_30_CTP3("old/dm_hk_eastcapesmoothed_PERTURBATION3.csv"),
    @Deprecated
    SBD_0_2A_HKR_LR_30_CTP4("old/dm_hk_eastcapesmoothed_PERTURBATION4.csv"),

    @Deprecated
    SBD_0_3_HKR_LR_30_LTP1("old/dm_hk_trenchlocked_PERTURBATION1.csv"),
    @Deprecated
    SBD_0_3_HKR_LR_30_LTP2("old/dm_hk_trenchlocked_PERTURBATION2.csv"),
    @Deprecated
    SBD_0_3_HKR_LR_30_LTP3("old/dm_hk_trenchlocked_PERTURBATION3.csv"),
    @Deprecated
    SBD_0_3_HKR_LR_30_LTP4("old/dm_hk_trenchlocked_PERTURBATION4.csv"),

    SBD_0_2A_HKR_MMIN8_EXP_CTP1A(
            "sliprateperturbations/dm_xhk_creepingtrench_Mmin8_exp_perturbation1a.csv"),
    SBD_0_2A_HKR_MMIN8_EXP_CTP1B(
            "sliprateperturbations/dm_xhk_creepingtrench_Mmin8_exp_perturbation1b.csv"),
    SBD_0_2A_HKR_MMIN8_EXP_CTP2A(
            "sliprateperturbations/dm_xhk_creepingtrench_Mmin8_exp_perturbation2a.csv"),
    SBD_0_2A_HKR_MMIN8_EXP_CTP2B(
            "sliprateperturbations/dm_xhk_creepingtrench_Mmin8_exp_perturbation2b.csv"),
    SBD_0_2A_HKR_MMIN8_EXP_CTP3A(
            "sliprateperturbations/dm_xhk_creepingtrench_Mmin8_exp_perturbation3a.csv"),
    SBD_0_2A_HKR_MMIN8_EXP_CTP3B(
            "sliprateperturbations/dm_xhk_creepingtrench_Mmin8_exp_perturbation3b.csv"),
    SBD_0_2A_HKR_MMIN8_EXP_CTP4A(
            "sliprateperturbations/dm_xhk_creepingtrench_Mmin8_exp_perturbation4a.csv"),
    SBD_0_2A_HKR_MMIN8_EXP_CTP4B(
            "sliprateperturbations/dm_xhk_creepingtrench_Mmin8_exp_perturbation4b.csv"),
    SBD_0_2A_HKR_MMIN8_EXP_CTP5A(
            "sliprateperturbations/dm_xhk_creepingtrench_Mmin8_exp_perturbation5a.csv"),
    SBD_0_2A_HKR_MMIN8_EXP_CTP5B(
            "sliprateperturbations/dm_xhk_creepingtrench_Mmin8_exp_perturbation5b.csv"),

    SBD_0_2A_HKR_MMIN8_EXP_LTP1A(
            "sliprateperturbations/dm_xhk_lockedtrench_Mmin8_exp_perturbation1a.csv"),
    SBD_0_2A_HKR_MMIN8_EXP_LTP1B(
            "sliprateperturbations/dm_xhk_lockedtrench_Mmin8_exp_perturbation1b.csv"),
    SBD_0_2A_HKR_MMIN8_EXP_LTP2A(
            "sliprateperturbations/dm_xhk_lockedtrench_Mmin8_exp_perturbation2a.csv"),
    SBD_0_2A_HKR_MMIN8_EXP_LTP2B(
            "sliprateperturbations/dm_xhk_lockedtrench_Mmin8_exp_perturbation2b.csv"),
    SBD_0_2A_HKR_MMIN8_EXP_LTP3A(
            "sliprateperturbations/dm_xhk_lockedtrench_Mmin8_exp_perturbation3a.csv"),
    SBD_0_2A_HKR_MMIN8_EXP_LTP3B(
            "sliprateperturbations/dm_xhk_lockedtrench_Mmin8_exp_perturbation3b.csv"),
    SBD_0_2A_HKR_MMIN8_EXP_LTP4A(
            "sliprateperturbations/dm_xhk_lockedtrench_Mmin8_exp_perturbation4a.csv"),
    SBD_0_2A_HKR_MMIN8_EXP_LTP4B(
            "sliprateperturbations/dm_xhk_lockedtrench_Mmin8_exp_perturbation4b.csv"),
    SBD_0_2A_HKR_MMIN8_EXP_LTP5A(
            "sliprateperturbations/dm_xhk_lockedtrench_Mmin8_exp_perturbation5a.csv"),
    SBD_0_2A_HKR_MMIN8_EXP_LTP5B(
            "sliprateperturbations/dm_xhk_lockedtrench_Mmin8_exp_perturbation5b.csv"),

    @Deprecated
    SBD_0_2_HKR_LR_30_CTP1(
            "old/dm_hk_tile_parameters_creeping_trench_slip_deficit_v2_30_PERTURBED.csv"),
    @Deprecated
    SBD_0_2_HKR_LR_30_CTP2(
            "old/dm_hk_tile_parameters_creeping_trench_slip_deficit_v2_30_PERTURBED_2.csv"),
    @Deprecated
    SBD_0_2_HKR_LR_30_CTP3(
            "old/dm_hk_tile_parameters_creeping_trench_slip_deficit_v2_30_PERTURBED_3.csv"),
    @Deprecated
    SBD_0_2_HKR_LR_30_CTP4(
            "old/dm_hk_tile_parameters_creeping_trench_slip_deficit_v2_30_PERTURBED_4.csv"),

    @Deprecated
    SBD_0_2_HKR_LR_30_LTP1(
            "old/dm_hk_tile_parameters_locked_trench_slip_deficit_v2_30_PERTURBED.csv"),
    @Deprecated
    SBD_0_2_HKR_LR_30_LTP2(
            "old/dm_hk_tile_parameters_locked_trench_slip_deficit_v2_30_PERTURBED_2.csv"),
    @Deprecated
    SBD_0_2_HKR_LR_30_LTP3(
            "old/dm_hk_tile_parameters_locked_trench_slip_deficit_v2_30_PERTURBED_3.csv"),
    @Deprecated
    SBD_0_2_HKR_LR_30_LTP4(
            "old/dm_hk_tile_parameters_locked_trench_slip_deficit_v2_30_PERTURBED_4.csv"),

    @Deprecated
    SBD_0_2_HKR_LR_30("old/dm_hk_tile_parameters_creeping_trench_slip_deficit_v2_30.csv"),

    @Deprecated
    SBD_0_2A_HKR_LR_30_M8("old/dm_hk_trenchcreeping_M7to8momentcorrected_notperturbed.csv"),
    @Deprecated
    SBD_0_2A_HKR_LR_30_M8_CTP1("old/dm_hk_trenchcreeping_M7to8momentcorrected_perturbation1.csv"),
    @Deprecated
    SBD_0_2A_HKR_LR_30_M8_CTP2("old/dm_hk_trenchcreeping_M7to8momentcorrected_perturbation2.csv"),
    @Deprecated
    SBD_0_2A_HKR_LR_30_M8_CTP3("old/dm_hk_trenchcreeping_M7to8momentcorrected_perturbation3.csv"),
    @Deprecated
    SBD_0_2A_HKR_LR_30_M8_CTP4("old/dm_hk_trenchcreeping_M7to8momentcorrected_perturbation4.csv"),

    @Deprecated
    SBD_0_3_HKR_LR_30_M8("old/dm_hk_trenchlocked_M7to8momentcorrected_notperturbed.csv"),
    @Deprecated
    SBD_0_3_HKR_LR_30_M8_LTP1("old/dm_hk_trenchlocked_M7to8momentcorrected_perturbation1.csv"),
    @Deprecated
    SBD_0_3_HKR_LR_30_M8_LTP2("old/dm_hk_trenchlocked_M7to8momentcorrected_perturbation2.csv"),
    @Deprecated
    SBD_0_3_HKR_LR_30_M8_LTP3("old/dm_hk_trenchlocked_M7to8momentcorrected_perturbation3.csv"),
    @Deprecated
    SBD_0_3_HKR_LR_30_M8_LTP4("old/dm_hk_trenchlocked_M7to8momentcorrected_perturbation4.csv"),

    @Deprecated
    SBD_0_2A_HKR_LR_30_EXP_CTP1("old/dm_hk_trenchcreeping_exp_perturbation1.csv"),
    @Deprecated
    SBD_0_2A_HKR_LR_30_EXP_CTP2("old/dm_hk_trenchcreeping_exp_perturbation2.csv"),
    @Deprecated
    SBD_0_2A_HKR_LR_30_EXP_CTP3("old/dm_hk_trenchcreeping_exp_perturbation3.csv"),
    @Deprecated
    SBD_0_2A_HKR_LR_30_EXP_CTP4("old/dm_hk_trenchcreeping_exp_perturbation4.csv"),
    @Deprecated
    SBD_0_2A_HKR_LR_30_EXP_CTP5("old/dm_hk_trenchcreeping_exp_perturbation5.csv"),

    @Deprecated
    SBD_0_3_HKR_LR_30_EXP_LTP1("old/dm_hk_trenchlocked_exp_perturbation1.csv"),
    @Deprecated
    SBD_0_3_HKR_LR_30_EXP_LTP2("old/dm_hk_trenchlocked_exp_perturbation2.csv"),
    @Deprecated
    SBD_0_3_HKR_LR_30_EXP_LTP3("old/dm_hk_trenchlocked_exp_perturbation3.csv"),
    @Deprecated
    SBD_0_3_HKR_LR_30_EXP_LTP4("old/dm_hk_trenchlocked_exp_perturbation4.csv"),
    @Deprecated
    SBD_0_3_HKR_LR_30_EXP_LTP5("old/dm_hk_trenchlocked_exp_perturbation5.csv"),
    ;

    private final DeformationModel model;

    /**
     * @param fileName path below the deformationModels folder of nzshm-input-models. Model
     *     properties are read from the file's meta data.
     */
    NZSHM22_DeformationModel(String fileName) {
        this.model = new DeformationModel(fileName);
    }

    /**
     * @param metaData meta data of a model without a file
     */
    NZSHM22_DeformationModel(InputModelMetaData metaData) {
        this.model = new DeformationModel(metaData);
    }

    /** The deformation model that this logic tree node passes through to. */
    public DeformationModel getModel() {
        return model;
    }

    /**
     * Sets the deformation model as CSV text data. Intended for the CUSTOM model, which has no file
     * of its own.
     *
     * @param customModel the CSV data of a deformation model
     */
    public void setCustomModel(String customModel) {
        model.setCustomModel(customModel);
    }

    /**
     * Sets the deformation model to the contents of the specified file. Intended for the CUSTOM
     * model, which has no file of its own.
     *
     * @param fileName the path of a deformation model CSV file
     * @throws IOException if the file cannot be read
     */
    public void setCustomModelFile(String fileName) throws IOException {
        model.setCustomModelFile(fileName);
    }

    public String getCustomModel() {
        return model.getCustomModel();
    }

    /** Used for testing only */
    public void load() {
        model.load();
    }

    public void applyTo(FaultSystemRupSet rupSet, IntPredicate predicate) {
        model.applyTo(rupSet, predicate);
    }

    @Override
    public String getName() {
        return name();
    }

    @Override
    public String getShortName() {
        return getName();
    }

    @Override
    public double getNodeWeight(LogicTreeBranch<?> fullBranch) {
        return 0;
    }

    @Override
    public String getFilePrefix() {
        return null;
    }

    /**
     * For testing
     *
     * @return the path below the deformationModels folder, or null
     */
    public String getFileName() {
        return model.getFileName();
    }

    public static LogicTreeLevel<LogicTreeNode> level() {
        return LogicTreeLevel.forEnumUnchecked(
                NZSHM22_DeformationModel.class,
                "NZSHM22_DeformationModel",
                "NZSHM22_DeformationModel");
    }
}

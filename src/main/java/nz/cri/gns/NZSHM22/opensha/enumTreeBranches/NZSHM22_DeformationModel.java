package nz.cri.gns.NZSHM22.opensha.enumTreeBranches;

import com.google.common.base.Preconditions;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntPredicate;
import nz.cri.gns.NZSHM22.opensha.faults.FaultSectionList;
import nz.cri.gns.NZSHM22.opensha.ruptures.FaultSectionProperties;
import org.dom4j.DocumentException;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.faultSurface.FaultSection;

public enum NZSHM22_DeformationModel implements LogicTreeNode {
    FAULT_MODEL("Use deformation model as provided by the fault model", "any", null),

    GEOD_NO_PRIOR_2022_RmlsZToxMDAwODc_(
            "geodetic, no geological prior constraint",
            "rupture set RmlsZToxMDAwODc=",
            "slip_deficit_rates_no_prior_correcteduncert_NZSHM22_RuptureSet-UnVwdHVyZUdlbmVyYXRpb25UYXNrOjEwMDAzOA==.dat"),
    GEOD_PRIOR_2022_RmlsZToxMDAwODc_(
            "geodetic, prior geological constraint",
            "rupture set RmlsZToxMDAwODc=",
            "slip_deficit_rates_with_prior_correcteduncert_NZSHM22_RuptureSet-UnVwdHVyZUdlbmVyYXRpb25UYXNrOjEwMDAzOA==.dat"),
    GEOD_NO_PRIOR_2022_RmlsZToyMjE4My4wUGVpWGE_(
            "geodetic, no geological prior constraint, 2022",
            "rupture set RmlsZToyMjE4My4wUGVpWGE=",
            "slip_deficit_rates_no_prior_NZSHM22_RuptureSet-UnVwdHVyZUdlbmVyYXRpb25UYXNrOjc5OTBvWWZMVw.dat"),

    GEOD_PRIOR_2022_RmlsZToyMjE4My4wUGVpWGE_(
            "geodetic, prior geological constraint, 2022",
            "rupture set RmlsZToyMjE4My4wUGVpWGE=",
            "slip_deficit_rates_with_prior_NZSHM22_RuptureSet-UnVwdHVyZUdlbmVyYXRpb25UYXNrOjc5OTBvWWZMVw.dat"),

    GEOD_NO_PRIOR_UNISTD_2010_RmlsZTo4NTkuMDM2Z2Rw(
            "geodetic, no geological prior constraint, uniform std-dev 2010",
            "rupture set RmlsZTo4NTkuMDM2Z2Rw",
            "slip_deficit_rates_no_prior_uniform-stddev_2010_RmlsZTo4NTkuMDM2Z2Rw.dat"),

    GEOD_NO_PRIOR_UNISTD_D90_RmlsZTozMDMuMEJCOVVY(
            "geodetic, no geological prior constraint, uniform std-dev D90",
            "rupture set RmlsZTozMDMuMEJCOVVY",
            "slip_deficit_rates_no_prior_uniform-stddev_D90_RmlsZTozMDMuMEJCOVVY.dat"),

    GEOD_NO_PRIOR_2010_RmlsZTo4NTkuMDM2Z2Rw_OFF_GEOL(
            "geodetic, no geological prior constraint, 2010, offshore rates from geologic",
            "rupture set RmlsZTo4NTkuMDM2Z2Rw",
            "slip_deficit_rates_no_prior_2010_RmlsZTo4NTkuMDM2Z2Rw_offshore_swap.dat"),

    GEOD_PRIOR_2010_RmlsZTo4NTkuMDM2Z2Rw_OFF_GEOL(
            "geodetic, prior geological constraint, 2010, offshore rates from geologic",
            "rupture set RmlsZTo4NTkuMDM2Z2Rw",
            "slip_deficit_rates_with_prior_2010_RmlsZTo4NTkuMDM2Z2Rw_offshore_swap.dat"),

    GEOD_NO_PRIOR_D90_RmlsZTozMDMuMEJCOVVY_OFF_GEOL(
            "geodetic, no geological prior constraint, D90, offshore rates from geologic",
            "rupture set RmlsZTozMDMuMEJCOVVY",
            "slip_deficit_rates_no_prior_D90_RmlsZTozMDMuMEJCOVVY_offshore_swap.dat"),

    GEOD_PRIOR_D90_RmlsZTozMDMuMEJCOVVY_OFF_GEOL(
            "geodetic, prior geological constraint, D90, offshore rates from geologic",
            "rupture set RmlsZTozMDMuMEJCOVVY",
            "slip_deficit_rates_with_prior_D90_RmlsZTozMDMuMEJCOVVY_offshore_swap.dat"),

    SBD_0_2_HKR_LR_30_CTP1(
            "Hikurangi, Kermadec to Louisville ridge, 30km - Creeping Trench Perturbed v1",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_hk_tile_parameters_creeping_trench_slip_deficit_v2_30_PERTURBED.csv"),
    SBD_0_2_HKR_LR_30_CTP2(
            "Hikurangi, Kermadec to Louisville ridge, 30km - Creeping Trench Perturbed v2",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_hk_tile_parameters_creeping_trench_slip_deficit_v2_30_PERTURBED_2.csv"),
    SBD_0_2_HKR_LR_30_CTP3(
            "Hikurangi, Kermadec to Louisville ridge, 30km - Creeping Trench Perturbed v3",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_hk_tile_parameters_creeping_trench_slip_deficit_v2_30_PERTURBED_3.csv"),
    SBD_0_2_HKR_LR_30_CTP4(
            "Hikurangi, Kermadec to Louisville ridge, 30km - Creeping Trench Perturbed v4",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_hk_tile_parameters_creeping_trench_slip_deficit_v2_30_PERTURBED_4.csv"),

    SBD_0_2_HKR_LR_30_LTP1(
            "Hikurangi, Kermadec to Louisville ridge, 30km - Locked Trench Perturbed v1",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_hk_tile_parameters_locked_trench_slip_deficit_v2_30_PERTURBED.csv"),
    SBD_0_2_HKR_LR_30_LTP2(
            "Hikurangi, Kermadec to Louisville ridge, 30km - Locked Trench Perturbed v2",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_hk_tile_parameters_locked_trench_slip_deficit_v2_30_PERTURBED_2.csv"),
    SBD_0_2_HKR_LR_30_LTP3(
            "Hikurangi, Kermadec to Louisville ridge, 30km - Locked Trench Perturbed v3",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_hk_tile_parameters_locked_trench_slip_deficit_v2_30_PERTURBED_3.csv"),
    SBD_0_2_HKR_LR_30_LTP4(
            "Hikurangi, Kermadec to Louisville ridge, 30km - Locked Trench Perturbed v4",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_hk_tile_parameters_locked_trench_slip_deficit_v2_30_PERTURBED_4.csv"),

    SBD_0_2_HKR_LR_30(
            "Hikurangi, Kermadec to Louisville ridge, 30km - with slip deficit smoothed near east cape",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_hk_tile_parameters_creeping_trench_slip_deficit_v2_30.csv"),

    SBD_0_2A_HKR_LR_30(
            "Hikurangi, Kermadec to Louisville ridge, 30km - with slip deficit smoothed near east cape",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_hk_tile_parameters_creeping_trench_slip_deficit_v2a_30.csv"),

    SBD_0_3_HKR_LR_30(
            "Hikurangi, Kermadec to Louisville ridge, 30km - with slip deficit smoothed near East Cape and locked near trench.",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_hk_tile_parameters_locked_trench_slip_deficit_v2_30.csv"),

    SBD_0_4_HKR_LR_30(
            "Hikurangi, Kermadec to Louisville ridge, 30km - higher overall slip rates, aka Kermits revenge",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_hk_tile_parameters_highkermsliprate_v2.csv"),

    SBD_0_2A_HKR_LR_30_CTP1(
            "Hikurangi, Kermadec to Louisville ridge, 30km - Creeping Trench Perturbed v1",
            "FaultModel SBD_0_2A_HKR_LR_30",
            "dm_hk_eastcapesmoothed_PERTURBATION1.csv"),
    SBD_0_2A_HKR_LR_30_CTP2(
            "Hikurangi, Kermadec to Louisville ridge, 30km - Creeping Trench Perturbed v2",
            "FaultModel SBD_0_2A_HKR_LR_30",
            "dm_hk_eastcapesmoothed_PERTURBATION2.csv"),
    SBD_0_2A_HKR_LR_30_CTP3(
            "Hikurangi, Kermadec to Louisville ridge, 30km - Creeping Trench Perturbed v3",
            "FaultModel SBD_0_2A_HKR_LR_30",
            "dm_hk_eastcapesmoothed_PERTURBATION3.csv"),
    SBD_0_2A_HKR_LR_30_CTP4(
            "Hikurangi, Kermadec to Louisville ridge, 30km - Creeping Trench Perturbed v4",
            "FaultModel SBD_0_2A_HKR_LR_30",
            "dm_hk_eastcapesmoothed_PERTURBATION4.csv"),

    SBD_0_3_HKR_LR_30_LTP1(
            "Hikurangi, Kermadec to Louisville ridge, 30km - Locked Trench Perturbed v1",
            "FaultModel SBD_0_3_HKR_LR_30",
            "dm_hk_trenchlocked_PERTURBATION1.csv"),
    SBD_0_3_HKR_LR_30_LTP2(
            "Hikurangi, Kermadec to Louisville ridge, 30km - Locked Trench Perturbed v2",
            "FaultModel SBD_0_3_HKR_LR_30",
            "dm_hk_trenchlocked_PERTURBATION2.csv"),
    SBD_0_3_HKR_LR_30_LTP3(
            "Hikurangi, Kermadec to Louisville ridge, 30km - Locked Trench Perturbed v3",
            "FaultModel SBD_0_3_HKR_LR_30",
            "dm_hk_trenchlocked_PERTURBATION3.csv"),
    SBD_0_3_HKR_LR_30_LTP4(
            "Hikurangi, Kermadec to Louisville ridge, 30km - Locked Trench Perturbed v4",
            "FaultModel SBD_0_3_HKR_LR_30",
            "dm_hk_trenchlocked_PERTURBATION4.csv"),

    SBD_0_1_PUY_30_0PT4(
            "Puysegur 0.4",
            "aust-pacific convergence, 0.4 coupling",
            "dm_puysegur_tiles_30km_maxd60km_austpaci_0pt4coupled_notrand.csv"),
    SBD_0_1_PUY_30_0PT7(
            "Puysegur 0.7",
            "aust-pacific convergence, 0.7 coupling",
            "dm_puysegur_tiles_30km_maxd60km_austpaci_0pt7coupled_notrand.csv"),
    SBD_0_1_PUY_30_1PT0(
            "Puysegur 1.0",
            "aust-pacific convergence, 1.0 coupling",
            "dm_puysegur_tiles_30km_maxd60km_austpaci_1pt0coupled_notrand.csv"),

    SBD_0_2A_HKR_LR_30_M8(
            "Hikurangi, Kermadec to Louisville ridge, 30km - M7 to M8 corrected with slip deficit smoothed near east cape",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones, min mag 8",
            "dm_hk_trenchcreeping_M7to8momentcorrected_notperturbed.csv"),
    SBD_0_2A_HKR_LR_30_M8_CTP1(
            "Hikurangi, Kermadec to Louisville ridge, 30km - M7 to M8 corrected with slip deficit smoothed near east cape perturbed1",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones, min mag 8",
            "dm_hk_trenchcreeping_M7to8momentcorrected_perturbation1.csv"),
    SBD_0_2A_HKR_LR_30_M8_CTP2(
            "Hikurangi, Kermadec to Louisville ridge, 30km - M7 to M8 corrected with slip deficit smoothed near east cape perturbed2",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones, min mag 8",
            "dm_hk_trenchcreeping_M7to8momentcorrected_perturbation2.csv"),
    SBD_0_2A_HKR_LR_30_M8_CTP3(
            "Hikurangi, Kermadec to Louisville ridge, 30km - M7 to M8 corrected with slip deficit smoothed near east cape perturbed3",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones, min mag 8",
            "dm_hk_trenchcreeping_M7to8momentcorrected_perturbation3.csv"),
    SBD_0_2A_HKR_LR_30_M8_CTP4(
            "Hikurangi, Kermadec to Louisville ridge, 30km - M7 to M8 corrected with slip deficit smoothed near east cape perturbed4",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones, min mag 8",
            "dm_hk_trenchcreeping_M7to8momentcorrected_perturbation4.csv"),

    SBD_0_3_HKR_LR_30_M8(
            "Hikurangi, Kermadec to Louisville ridge, 30km - M7 to M8 corrected with slip deficit smoothed near east cape and locked near trench",
            "FaultModel SBD_0_3_HKR_LR_30",
            "dm_hk_trenchlocked_M7to8momentcorrected_notperturbed.csv"),
    SBD_0_3_HKR_LR_30_M8_LTP1(
            "Hikurangi, Kermadec to Louisville ridge, 30km - M7 to M8 corrected with slip deficit smoothed near east cape and locked near trench perturbed1",
            "FaultModel SBD_0_3_HKR_LR_30",
            "dm_hk_trenchlocked_M7to8momentcorrected_perturbation1.csv"),
    SBD_0_3_HKR_LR_30_M8_LTP2(
            "Hikurangi, Kermadec to Louisville ridge, 30km - M7 to M8 corrected with slip deficit smoothed near east cape and locked near trench perturbed2",
            "FaultModel SBD_0_3_HKR_LR_30",
            "dm_hk_trenchlocked_M7to8momentcorrected_perturbation2.csv"),
    SBD_0_3_HKR_LR_30_M8_LTP3(
            "Hikurangi, Kermadec to Louisville ridge, 30km - M7 to M8 corrected with slip deficit smoothed near east cape and locked near trench perturbed3",
            "FaultModel SBD_0_3_HKR_LR_30",
            "dm_hk_trenchlocked_M7to8momentcorrected_perturbation3.csv"),
    SBD_0_3_HKR_LR_30_M8_LTP4(
            "Hikurangi, Kermadec to Louisville ridge, 30km - M7 to M8 corrected with slip deficit smoothed near east cape and locked near trench perturbed4",
            "FaultModel SBD_0_3_HKR_LR_30",
            "dm_hk_trenchlocked_M7to8momentcorrected_perturbation4.csv"),

    SBD_0_2A_HKR_LR_30_EXP_CTP1(
            "Hikurangi, Kermadec to Louisville ridge, 30km - correlated noise perturbed1",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_hk_trenchcreeping_exp_perturbation1.csv"),
    SBD_0_2A_HKR_LR_30_EXP_CTP2(
            "Hikurangi, Kermadec to Louisville ridge, 30km - correlated noise perturbed2",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_hk_trenchcreeping_exp_perturbation2.csv"),
    SBD_0_2A_HKR_LR_30_EXP_CTP3(
            "Hikurangi, Kermadec to Louisville ridge, 30km - correlated noise perturbed3",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_hk_trenchcreeping_exp_perturbation3.csv"),
    SBD_0_2A_HKR_LR_30_EXP_CTP4(
            "Hikurangi, Kermadec to Louisville ridge, 30km - correlated noise perturbed4",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_hk_trenchcreeping_exp_perturbation4.csv"),
    SBD_0_2A_HKR_LR_30_EXP_CTP5(
            "Hikurangi, Kermadec to Louisville ridge, 30km - correlated noise perturbed5",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_hk_trenchcreeping_exp_perturbation5.csv"),

    SBD_0_3_HKR_LR_30_EXP_LTP1(
            "Hikurangi, Kermadec to Louisville ridge, 30km - correlated noise and locked near trench perturbed1",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_hk_trenchlocked_exp_perturbation1.csv"),
    SBD_0_3_HKR_LR_30_EXP_LTP2(
            "Hikurangi, Kermadec to Louisville ridge, 30km - correlated noise and locked near trench perturbed2",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_hk_trenchlocked_exp_perturbation2.csv"),
    SBD_0_3_HKR_LR_30_EXP_LTP3(
            "Hikurangi, Kermadec to Louisville ridge, 30km - correlated noise and locked near trench perturbed3",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_hk_trenchlocked_exp_perturbation3.csv"),
    SBD_0_3_HKR_LR_30_EXP_LTP4(
            "Hikurangi, Kermadec to Louisville ridge, 30km - correlated noise and locked near trench perturbed4",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_hk_trenchlocked_exp_perturbation4.csv"),
    SBD_0_3_HKR_LR_30_EXP_LTP5(
            "Hikurangi, Kermadec to Louisville ridge, 30km - correlated noise and locked near trench perturbed5",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_hk_trenchlocked_exp_perturbation5.csv"),

    SBD_0_2A_HKR_LR_30_MMIN7PT5_EXP_CTP1A(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench creeping, Mmin 7pt5, exponential perturbation 1a",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_hk_creepingtrench_Mmin7pt5_exp_perturbation1a.csv"),
    SBD_0_2A_HKR_LR_30_MMIN7PT5_EXP_CTP1B(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench creeping, Mmin 7pt5, exponential perturbation 1b",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_hk_creepingtrench_Mmin7pt5_exp_perturbation1b.csv"),
    SBD_0_2A_HKR_LR_30_MMIN7PT5_EXP_CTP2A(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench creeping, Mmin 7pt5, exponential perturbation 2a",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_hk_creepingtrench_Mmin7pt5_exp_perturbation2a.csv"),
    SBD_0_2A_HKR_LR_30_MMIN7PT5_EXP_CTP2B(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench creeping, Mmin 7pt5, exponential perturbation 2b",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_hk_creepingtrench_Mmin7pt5_exp_perturbation2b.csv"),
    SBD_0_2A_HKR_LR_30_MMIN7PT5_EXP_CTP3A(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench creeping, Mmin 7pt5, exponential perturbation 3a",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_hk_creepingtrench_Mmin7pt5_exp_perturbation3a.csv"),
    SBD_0_2A_HKR_LR_30_MMIN7PT5_EXP_CTP3B(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench creeping, Mmin 7pt5, exponential perturbation 3b",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_hk_creepingtrench_Mmin7pt5_exp_perturbation3b.csv"),
    SBD_0_2A_HKR_LR_30_MMIN7PT5_EXP_CTP4A(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench creeping, Mmin 7pt5, exponential perturbation 4a",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_hk_creepingtrench_Mmin7pt5_exp_perturbation4a.csv"),
    SBD_0_2A_HKR_LR_30_MMIN7PT5_EXP_CTP4B(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench creeping, Mmin 7pt5, exponential perturbation 4b",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_hk_creepingtrench_Mmin7pt5_exp_perturbation4b.csv"),
    SBD_0_2A_HKR_LR_30_MMIN7PT5_EXP_CTP5A(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench creeping, Mmin 7pt5, exponential perturbation 5a",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_hk_creepingtrench_Mmin7pt5_exp_perturbation5a.csv"),
    SBD_0_2A_HKR_LR_30_MMIN7PT5_EXP_CTP5B(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench creeping, Mmin 7pt5, exponential perturbation 5b",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_hk_creepingtrench_Mmin7pt5_exp_perturbation5b.csv"),

    SBD_0_2A_HKR_LR_30_MMIN7PT5_EXP_LTP1A(
            "Hikurangi, Kermadec to Louisville ridge, 30km, Mmin 7pt5, trench locked, exponential perturbation 1a",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_hk_lockedtrench_Mmin7pt5_exp_perturbation1a.csv"),
    SBD_0_2A_HKR_LR_30_MMIN7PT5_EXP_LTP1B(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench locked, Mmin 7pt5, exponential perturbation 1b",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_hk_lockedtrench_Mmin7pt5_exp_perturbation1b.csv"),
    SBD_0_2A_HKR_LR_30_MMIN7PT5_EXP_LTP2A(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench locked, Mmin 7pt5, exponential perturbation 2a",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_hk_lockedtrench_Mmin7pt5_exp_perturbation2a.csv"),
    SBD_0_2A_HKR_LR_30_MMIN7PT5_EXP_LTP2B(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench locked, Mmin 7pt5, exponential perturbation 2b",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_hk_lockedtrench_Mmin7pt5_exp_perturbation2b.csv"),
    SBD_0_2A_HKR_LR_30_MMIN7PT5_EXP_LTP3A(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench locked, Mmin 7pt5, exponential perturbation 3a",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_hk_lockedtrench_Mmin7pt5_exp_perturbation3a.csv"),
    SBD_0_2A_HKR_LR_30_MMIN7PT5_EXP_LTP3B(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench locked, Mmin 7pt5, exponential perturbation 3b",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_hk_lockedtrench_Mmin7pt5_exp_perturbation3b.csv"),
    SBD_0_2A_HKR_LR_30_MMIN7PT5_EXP_LTP4A(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench locked, Mmin 7pt5, exponential perturbation 4a",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_hk_lockedtrench_Mmin7pt5_exp_perturbation4a.csv"),
    SBD_0_2A_HKR_LR_30_MMIN7PT5_EXP_LTP4B(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench locked, Mmin 7pt5, exponential perturbation 4b",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_hk_lockedtrench_Mmin7pt5_exp_perturbation4b.csv"),
    SBD_0_2A_HKR_LR_30_MMIN7PT5_EXP_LTP5A(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench locked, Mmin 7pt5, exponential perturbation 5a",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_hk_lockedtrench_Mmin7pt5_exp_perturbation5a.csv"),
    SBD_0_2A_HKR_LR_30_MMIN7PT5_EXP_LTP5B(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench locked, Mmin 7pt5, exponential perturbation 5b",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_hk_lockedtrench_Mmin7pt5_exp_perturbation5b.csv"),

    SBD_0_2A_HKR_MMIN7PT5_EXP_CTP1A(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench creeping, Mmin 7pt5, exponential perturbation 1a",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_xhk_creepingtrench_Mmin7pt5_exp_perturbation1a.csv"),
    SBD_0_2A_HKR_MMIN7PT5_EXP_CTP1B(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench creeping, Mmin 7pt5, exponential perturbation 1b",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_xhk_creepingtrench_Mmin7pt5_exp_perturbation1b.csv"),
    SBD_0_2A_HKR_MMIN7PT5_EXP_CTP2A(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench creeping, Mmin 7pt5, exponential perturbation 2a",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_xhk_creepingtrench_Mmin7pt5_exp_perturbation2a.csv"),
    SBD_0_2A_HKR_MMIN7PT5_EXP_CTP2B(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench creeping, Mmin 7pt5, exponential perturbation 2b",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_xhk_creepingtrench_Mmin7pt5_exp_perturbation2b.csv"),
    SBD_0_2A_HKR_MMIN7PT5_EXP_CTP3A(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench creeping, Mmin 7pt5, exponential perturbation 3a",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_xhk_creepingtrench_Mmin7pt5_exp_perturbation3a.csv"),
    SBD_0_2A_HKR_MMIN7PT5_EXP_CTP3B(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench creeping, Mmin 7pt5, exponential perturbation 3b",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_xhk_creepingtrench_Mmin7pt5_exp_perturbation3b.csv"),
    SBD_0_2A_HKR_MMIN7PT5_EXP_CTP4A(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench creeping, Mmin 7pt5, exponential perturbation 4a",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_xhk_creepingtrench_Mmin7pt5_exp_perturbation4a.csv"),
    SBD_0_2A_HKR_MMIN7PT5_EXP_CTP4B(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench creeping, Mmin 7pt5, exponential perturbation 4b",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_xhk_creepingtrench_Mmin7pt5_exp_perturbation4b.csv"),
    SBD_0_2A_HKR_MMIN7PT5_EXP_CTP5A(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench creeping, Mmin 7pt5, exponential perturbation 5a",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_xhk_creepingtrench_Mmin7pt5_exp_perturbation5a.csv"),
    SBD_0_2A_HKR_MMIN7PT5_EXP_CTP5B(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench creeping, Mmin 7pt5, exponential perturbation 5b",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_xhk_creepingtrench_Mmin7pt5_exp_perturbation5b.csv"),
    SBD_0_2A_HKR_MMIN7PT5_EXP_LTP1A(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench locked, Mmin 7pt5, exponential perturbation 1a",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_xhk_lockedtrench_Mmin7pt5_exp_perturbation1a.csv"),
    SBD_0_2A_HKR_MMIN7PT5_EXP_LTP1B(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench locked, Mmin 7pt5, exponential perturbation 1b",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_xhk_lockedtrench_Mmin7pt5_exp_perturbation1b.csv"),
    SBD_0_2A_HKR_MMIN7PT5_EXP_LTP2A(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench locked, Mmin 7pt5, exponential perturbation 2a",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_xhk_lockedtrench_Mmin7pt5_exp_perturbation2a.csv"),
    SBD_0_2A_HKR_MMIN7PT5_EXP_LTP2B(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench locked, Mmin 7pt5, exponential perturbation 2b",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_xhk_lockedtrench_Mmin7pt5_exp_perturbation2b.csv"),
    SBD_0_2A_HKR_MMIN7PT5_EXP_LTP3A(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench locked, Mmin 7pt5, exponential perturbation 3a",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_xhk_lockedtrench_Mmin7pt5_exp_perturbation3a.csv"),
    SBD_0_2A_HKR_MMIN7PT5_EXP_LTP3B(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench locked, Mmin 7pt5, exponential perturbation 3b",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_xhk_lockedtrench_Mmin7pt5_exp_perturbation3b.csv"),
    SBD_0_2A_HKR_MMIN7PT5_EXP_LTP4A(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench locked, Mmin 7pt5, exponential perturbation 4a",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_xhk_lockedtrench_Mmin7pt5_exp_perturbation4a.csv"),
    SBD_0_2A_HKR_MMIN7PT5_EXP_LTP4B(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench locked, Mmin 7pt5, exponential perturbation 4b",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_xhk_lockedtrench_Mmin7pt5_exp_perturbation4b.csv"),
    SBD_0_2A_HKR_MMIN7PT5_EXP_LTP5A(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench locked, Mmin 7pt5, exponential perturbation 5a",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_xhk_lockedtrench_Mmin7pt5_exp_perturbation5a.csv"),
    SBD_0_2A_HKR_MMIN7PT5_EXP_LTP5B(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench locked, Mmin 7pt5, exponential perturbation 5b",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_xhk_lockedtrench_Mmin7pt5_exp_perturbation5b.csv"),

    SBD_0_2A_HKR_MMIN8_EXP_CTP1A(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench creeping, Mmin 8, exponential perturbation 1a",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_xhk_creepingtrench_Mmin8_exp_perturbation1a.csv"),
    SBD_0_2A_HKR_MMIN8_EXP_CTP1B(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench creeping, Mmin 8, exponential perturbation 1b",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_xhk_creepingtrench_Mmin8_exp_perturbation1b.csv"),
    SBD_0_2A_HKR_MMIN8_EXP_CTP2A(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench creeping, Mmin 8, exponential perturbation 2a",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_xhk_creepingtrench_Mmin8_exp_perturbation2a.csv"),
    SBD_0_2A_HKR_MMIN8_EXP_CTP2B(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench creeping, Mmin 8, exponential perturbation 2b",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_xhk_creepingtrench_Mmin8_exp_perturbation2b.csv"),
    SBD_0_2A_HKR_MMIN8_EXP_CTP3A(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench creeping, Mmin 8, exponential perturbation 3a",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_xhk_creepingtrench_Mmin8_exp_perturbation3a.csv"),
    SBD_0_2A_HKR_MMIN8_EXP_CTP3B(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench creeping, Mmin 8, exponential perturbation 3b",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_xhk_creepingtrench_Mmin8_exp_perturbation3b.csv"),
    SBD_0_2A_HKR_MMIN8_EXP_CTP4A(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench creeping, Mmin 8, exponential perturbation 4a",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_xhk_creepingtrench_Mmin8_exp_perturbation4a.csv"),
    SBD_0_2A_HKR_MMIN8_EXP_CTP4B(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench creeping, Mmin 8, exponential perturbation 4b",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_xhk_creepingtrench_Mmin8_exp_perturbation4b.csv"),
    SBD_0_2A_HKR_MMIN8_EXP_CTP5A(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench creeping, Mmin 8, exponential perturbation 5a",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_xhk_creepingtrench_Mmin8_exp_perturbation5a.csv"),
    SBD_0_2A_HKR_MMIN8_EXP_CTP5B(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench creeping, Mmin 8, exponential perturbation 5b",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_xhk_creepingtrench_Mmin8_exp_perturbation5b.csv"),

    SBD_0_2A_HKR_MMIN8_EXP_LTP1A(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench locked, Mmin 8, exponential perturbation 1a",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_xhk_lockedtrench_Mmin8_exp_perturbation1a.csv"),
    SBD_0_2A_HKR_MMIN8_EXP_LTP1B(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench locked, Mmin 8, exponential perturbation 1b",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_xhk_lockedtrench_Mmin8_exp_perturbation1b.csv"),
    SBD_0_2A_HKR_MMIN8_EXP_LTP2A(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench locked, Mmin 8, exponential perturbation 2a",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_xhk_lockedtrench_Mmin8_exp_perturbation2a.csv"),
    SBD_0_2A_HKR_MMIN8_EXP_LTP2B(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench locked, Mmin 8, exponential perturbation 2b",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_xhk_lockedtrench_Mmin8_exp_perturbation2b.csv"),
    SBD_0_2A_HKR_MMIN8_EXP_LTP3A(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench locked, Mmin 8, exponential perturbation 3a",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_xhk_lockedtrench_Mmin8_exp_perturbation3a.csv"),
    SBD_0_2A_HKR_MMIN8_EXP_LTP3B(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench locked, Mmin 8, exponential perturbation 3b",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_xhk_lockedtrench_Mmin8_exp_perturbation3b.csv"),
    SBD_0_2A_HKR_MMIN8_EXP_LTP4A(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench locked, Mmin 8, exponential perturbation 4a",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_xhk_lockedtrench_Mmin8_exp_perturbation4a.csv"),
    SBD_0_2A_HKR_MMIN8_EXP_LTP4B(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench locked, Mmin 8, exponential perturbation 4b",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_xhk_lockedtrench_Mmin8_exp_perturbation4b.csv"),
    SBD_0_2A_HKR_MMIN8_EXP_LTP5A(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench locked, Mmin 8, exponential perturbation 5a",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_xhk_lockedtrench_Mmin8_exp_perturbation5a.csv"),
    SBD_0_2A_HKR_MMIN8_EXP_LTP5B(
            "Hikurangi, Kermadec to Louisville ridge, 30km, trench locked, Mmin 8, exponential perturbation 5b",
            "FaultModel SBD_0_2_HKR_LR_30 and the next three deprecated ones",
            "dm_xhk_lockedtrench_Mmin8_exp_perturbation5b.csv"),
    ;

    String description;
    String fileName;
    DeformationHelper helper;

    private static final String resourcePath = "/deformationModels/";

    NZSHM22_DeformationModel(String description, String compatibility, String fileName) {
        this.description = description;
        this.fileName = fileName;
        this.helper = new DeformationHelper(fileName);
    }

    public static class DeformationHelper {
        Map<Integer, SlipDeformation> deformations = null;
        String fileName;

        public DeformationHelper(String fileName) {
            this.fileName = fileName;
        }

        public InputStream getStream() {
            return getClass().getResourceAsStream(resourcePath + fileName);
        }

        public static class SlipDeformation {
            int sectionId;
            int parentId;
            double slip;
            double stdv;
        }

        protected static Map<Integer, SlipDeformation> loadDeformations(
                InputStream deformationsFile) throws IOException {
            Map<Integer, SlipDeformation> result = new HashMap<>();
            try (BufferedReader in = new BufferedReader(new InputStreamReader(deformationsFile))) {
                String line;
                int rowNum = 0;
                while ((line = in.readLine()) != null) {
                    try {
                        if (line.startsWith("%") || line.startsWith("//")) {
                            continue;
                        }
                        String[] data = line.split(",");
                        SlipDeformation deformation = new SlipDeformation();
                        deformation.sectionId = Integer.parseInt(data[0].trim());
                        deformation.parentId = Integer.parseInt(data[1].trim());
                        deformation.slip = Double.parseDouble(data[2].trim());
                        deformation.stdv = Double.parseDouble(data[3].trim());
                        result.put(deformation.sectionId, deformation);
                    } catch (Exception x) {
                        System.err.println("Error parsing deformation model at line " + rowNum);
                        x.printStackTrace();
                        System.err.println(x);
                        throw x;
                    }
                    rowNum++;
                }
                return result;
            }
        }

        protected Map<Integer, SlipDeformation> getDeformations() {
            if (deformations == null) {
                try {
                    deformations = loadDeformations(getStream());
                } catch (IOException x) {
                    throw new RuntimeException(x);
                }
            }
            return deformations;
        }

        public void applyTo(FaultSystemRupSet rupSet, IntPredicate predicate) {
            for (FaultSection section : rupSet.getFaultSectionDataList()) {
                if (predicate != null && !predicate.test(section.getSectionId())) {
                    continue;
                }

                FaultSectionProperties props = new FaultSectionProperties(section);
                int sectionId = section.getSectionId();
                if (props.getOriginalId() != null) {
                    sectionId = props.getOriginalId();
                }
                int parentId = section.getParentSectionId();
                if (props.getOriginalParent() != null) {
                    parentId = props.getOriginalParent();
                }

                SlipDeformation deformation = getDeformations().get(sectionId);
                if (deformation != null) {
                    Preconditions.checkArgument(
                            deformation.sectionId == sectionId,
                            "Deformation section id does not match section id.");
                    Preconditions.checkArgument(
                            deformation.parentId == parentId,
                            "Section "
                                    + section.getSectionId()
                                    + " Deformation parent id "
                                    + deformation.parentId
                                    + " does not match section parent id "
                                    + parentId);
                    section.setAveSlipRate(deformation.slip);
                    section.setSlipRateStdDev(deformation.stdv);
                }
            }
        }
    }

    /** Used for testing only */
    public void load() {
        if (fileName != null) {
            helper.getDeformations();
        }
    }

    public void applyTo(FaultSystemRupSet rupSet, IntPredicate predicate) {
        if (fileName != null) {
            helper.applyTo(rupSet, predicate);
        }
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
     * @return
     */
    public String getFileName() {
        return fileName;
    }

    public static LogicTreeLevel<LogicTreeNode> level() {
        return LogicTreeLevel.forEnumUnchecked(
                NZSHM22_DeformationModel.class,
                "NZSHM22_DeformationModel",
                "NZSHM22_DeformationModel");
    }

    /**
     * Transforms a subduction fault model into a deformation model
     *
     * @param subductionFaultModelFile
     * @throws DocumentException
     * @throws IOException
     */
    public static void subductionFmToDm(Path subductionFaultModelFile) {
        try {
            FaultSectionList sections = new FaultSectionList();
            InputStream in = new FileInputStream(subductionFaultModelFile.toFile());
            NZSHM22_FaultModels.fetchFaultSections(sections, in, false, 10000, "");

            try (PrintWriter out =
                    new PrintWriter(
                            new FileWriter(
                                    "dm_x" + subductionFaultModelFile.getFileName().toString()))) {
                out.println("% generated from faultmodel file " + subductionFaultModelFile);
                for (FaultSection section : sections) {
                    out.println(
                            ""
                                    + section.getSectionId()
                                    + ", "
                                    + section.getParentSectionId()
                                    + ", "
                                    + section.getOrigAveSlipRate()
                                    + ", "
                                    + section.getOrigSlipRateStdDev());
                }

            } catch (IOException x) {
                x.printStackTrace();
            }
        } catch (Exception x) {
            x.printStackTrace();
        }
    }

    public static void main(String[] args) throws DocumentException, IOException {
        Files.list(Paths.get("C:\\tmp\\temp\\"))
                .forEach(NZSHM22_DeformationModel::subductionFmToDm);
    }
}

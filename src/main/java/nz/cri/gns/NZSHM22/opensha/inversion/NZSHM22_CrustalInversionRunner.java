package nz.cri.gns.NZSHM22.opensha.inversion;

import nz.cri.gns.NZSHM22.opensha.calc.SimplifiedScalingRelationship;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_LogicTreeBranch;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_PaleoProbabilityModel;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_PaleoRates;
import org.dom4j.DocumentException;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;

import com.google.common.base.Preconditions;

import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.PaleoProbabilityModel;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.UncertainDataConstraint;
import scratch.UCERF3.enumTreeBranches.InversionModels;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs the standard NSHM inversion on a crustal rupture set.
 */
public class NZSHM22_CrustalInversionRunner extends NZSHM22_AbstractInversionRunner {

    //	private NZSHM22_CrustalInversionConfiguration inversionConfiguration;
    private double totalRateM5_Sans = 3.6;
    private double totalRateM5_TVZ = 0.4;
    private double bValue_Sans = 1.05;
    private double bValue_TVZ = 1.25;
    private double minMag_Sans = 6.95;
    private double minMag_TVZ = 6.95;

    private double maxMagTVZ = 20.0;
    private double maxMagSans = 20.0;
    private MaxMagType maxMagType = MaxMagType.NONE;

    private double paleoRateConstraintWt = 0;
    private double paleoParentRateSmoothnessConstraintWeight = 0;
    private NZSHM22_PaleoRates paleoRates;
    private NZSHM22_PaleoProbabilityModel paleoProbabilityModel;

    private double tvzSlipRateFactor = -1;

    public enum MaxMagType{
        NONE,
        FILTER_RUPSET,
        MANIPULATE_MFD;
    }

    /**
     * Creates a new NZSHM22_InversionRunner with defaults.
     */
    public NZSHM22_CrustalInversionRunner() {
        super();
    }

    /**
     * Sets the minimum magnitude for targetOnFaultSupraSeisMFDs
     * @param minMagSans
     * @param minMagTvz
     * @return this runner
     */
    public NZSHM22_CrustalInversionRunner setMinMags(double minMagSans, double minMagTvz){
        this.minMag_Sans = minMagSans;
        this.minMag_TVZ = minMagTvz;
        return this;
    }

    public NZSHM22_CrustalInversionRunner setMaxMags(String maxMagType, double maxMagSans, double maxMagTVZ){
        this.maxMagType = MaxMagType.valueOf(maxMagType);
        this.maxMagSans = maxMagSans;
        this.maxMagTVZ = maxMagTVZ;
        return this;
    }

    public NZSHM22_CrustalInversionRunner setTVZSlipRateFactor(double tvzSlipRateFactor){
        this.tvzSlipRateFactor = tvzSlipRateFactor;
        return this;
    }

    /**
     * Sets GutenbergRichterMFD arguments
     *
     * @param totalRateM5_Sans
     * @param totalRateM5_TVZ
     * @param bValue_Sans
     * @param bValue_TVZ
     * @param mfdTransitionMag
     * @return
     */
    public NZSHM22_CrustalInversionRunner setGutenbergRichterMFD(double totalRateM5_Sans, double totalRateM5_TVZ,
                                                                 double bValue_Sans, double bValue_TVZ, double mfdTransitionMag) {
        this.totalRateM5_Sans = totalRateM5_Sans;
        this.totalRateM5_TVZ = totalRateM5_TVZ;
        this.bValue_Sans = bValue_Sans;
        this.bValue_TVZ = bValue_TVZ;
        this.mfdTransitionMag = mfdTransitionMag;
        return this;
    }

    public NZSHM22_CrustalInversionRunner setPaleoRateConstraintWt(double paleoRateConstraintWt){
        this.paleoRateConstraintWt = paleoRateConstraintWt;
        return this;
    }

    public NZSHM22_CrustalInversionRunner setPaleoRates(NZSHM22_PaleoRates paleoRates){
        this.paleoRates = paleoRates;
        return this;
    }

    public NZSHM22_CrustalInversionRunner setPaleoProbabilityModel(NZSHM22_PaleoProbabilityModel probabilityModel){
        this.paleoProbabilityModel = probabilityModel;
        return this;
    }

    public NZSHM22_CrustalInversionRunner setPaleoRateConstraints(double weight, double smoothingWeight, String paleoRateConstraints, String paleoProbabilityModel){
        paleoRateConstraintWt = weight;
        paleoParentRateSmoothnessConstraintWeight = smoothingWeight;
        setPaleoRates(NZSHM22_PaleoRates.valueOf(paleoRateConstraints));
        setPaleoProbabilityModel(NZSHM22_PaleoProbabilityModel.valueOf(paleoProbabilityModel));
        return this;
    }

    @Override
    protected NZSHM22_CrustalInversionRunner configure() throws DocumentException, IOException {

        NZSHM22_LogicTreeBranch branch = NZSHM22_LogicTreeBranch.crustalInversion();
        setupLTB(branch);

        if (maxMagType == MaxMagType.FILTER_RUPSET) {
            this.rupSet = NZSHM22_InversionFaultSystemRuptSet.loadCrustalRuptureSet(rupSetFile, branch, tvzSlipRateFactor, maxMagTVZ, maxMagSans);
        } else {
            this.rupSet = NZSHM22_InversionFaultSystemRuptSet.loadCrustalRuptureSet(rupSetFile, branch, tvzSlipRateFactor);
        }

        InversionModels inversionModel = branch.getValue(InversionModels.class);

        // this contains all inversion weights
        NZSHM22_CrustalInversionConfiguration inversionConfiguration;

        if (maxMagType == MaxMagType.MANIPULATE_MFD) {
            inversionConfiguration = NZSHM22_CrustalInversionConfiguration.forModel(
                    inversionModel, rupSet, initialSolution, mfdEqualityConstraintWt, mfdInequalityConstraintWt, totalRateM5_Sans,
                    totalRateM5_TVZ, bValue_Sans, bValue_TVZ, mfdTransitionMag, minMag_Sans, minMag_TVZ, maxMagSans, maxMagTVZ,
                    mfdUncertaintyWeightedConstraintWt, mfdUncertaintyWeightedConstraintPower, excludeRupturesBelowMinMag);
        } else{
            inversionConfiguration = NZSHM22_CrustalInversionConfiguration.forModel(
                    inversionModel, rupSet, initialSolution, mfdEqualityConstraintWt, mfdInequalityConstraintWt, totalRateM5_Sans,
                    totalRateM5_TVZ, bValue_Sans, bValue_TVZ, mfdTransitionMag, minMag_Sans, minMag_TVZ, 100, 100,
                    mfdUncertaintyWeightedConstraintWt, mfdUncertaintyWeightedConstraintPower, excludeRupturesBelowMinMag);
        }

       inversionConfiguration
               .setPaleoRateConstraintWt(paleoRateConstraintWt)
               .setpaleoParentRateSmoothnessConstraintWeight(paleoParentRateSmoothnessConstraintWeight);

        solutionMfds = ((NZSHM22_CrustalInversionTargetMFDs) inversionConfiguration.getInversionTargetMfds())
                .getReportingMFDConstraintComponents();


        solutionMfdsV2 = ((NZSHM22_CrustalInversionTargetMFDs) inversionConfiguration.getInversionTargetMfds())
                .getReportingMFDConstraintComponentsV2();        
        
        // set up slip rate config
        inversionConfiguration.setSlipRateWeightingType(this.slipRateWeightingType);
        inversionConfiguration.setUnmodifiedSlipRateStdvs(this.unmodifiedSlipRateStdvs);
        if (this.slipRateWeightingType == AbstractInversionConfiguration.NZSlipRateConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY) {
            System.out.println("config for UNCERTAINTY_ADJUSTED " + this.slipRateUncertaintyWeight + ", "
                    + this.slipRateUncertaintyScalingFactor);
            inversionConfiguration.setSlipRateUncertaintyConstraintWt(this.slipRateUncertaintyWeight);
            inversionConfiguration.setSlipRateUncertaintyConstraintScalingFactor(this.slipRateUncertaintyScalingFactor);
        } else {
            inversionConfiguration.setSlipRateConstraintWt_normalized(this.slipRateConstraintWt_normalized);
            inversionConfiguration.setSlipRateConstraintWt_unnormalized(this.slipRateConstraintWt_unnormalized);
        }

        /*
         * Build inversion inputs
         */
        List<UncertainDataConstraint.SectMappedUncertainDataConstraint> paleoRateConstraints = null;
        if (paleoRates != null){
            paleoRateConstraints = paleoRates.fetchConstraints(rupSet.getFaultSectionDataList());
        }

        PaleoProbabilityModel paleoProbabilityModel = null;
        if(this.paleoProbabilityModel != null){
            paleoProbabilityModel = this.paleoProbabilityModel.fetchModel();
        }

        NZSHM22_CrustalInversionInputGenerator inversionInputGenerator = new NZSHM22_CrustalInversionInputGenerator(
                rupSet, inversionConfiguration, paleoRateConstraints, null, null, paleoProbabilityModel);
        setInversionInputGenerator(inversionInputGenerator);
        return this;
    }

    @Override
    public FaultSystemSolution runInversion() throws IOException, DocumentException {
        FaultSystemSolution solution = super.runInversion();
        solution.addModule(rupSet.getInversionTargetMFDs().getOnFaultSubSeisMFDs());
        return solution;
    }

    public static void main(String[] args) throws IOException, DocumentException {

        File inputDir = new File("./TEST");
        File outputRoot = new File("./TEST");
        File ruptureSet = new File(
        		"./TEST/NZSHM22_RuptureSet-UnVwdHVyZUdlbmVyYXRpb25UYXNrOjc5OTBvWWZMVw==.zip");
        File outputDir = new File(outputRoot, "uct_test");
        Preconditions.checkState(outputDir.exists() || outputDir.mkdir());

        SimplifiedScalingRelationship scaling = new SimplifiedScalingRelationship();
        scaling.setupCrustal(4.2, 4.2);

        NZSHM22_CrustalInversionRunner runner = ((NZSHM22_CrustalInversionRunner) new NZSHM22_CrustalInversionRunner()      		
                .setTVZSlipRateFactor(1.0)
                .setMaxMags("MANIPULATE_MFD",10,7.5) //FILTER_RUPSET
                .setMinMags(6.8 , 6.5)
                .setInversionSeconds(300)
                .setScalingRelationship(scaling, true)
                //.setDeformationModel("GEOD_NO_PRIOR_UNISTD_2010_RmlsZTo4NTkuMDM2Z2Rw")
                .setRuptureSetFile(ruptureSet)

                //.setGutenbergRichterMFDWeights(100.0, 1000.0)
                .setUncertaintyWeightedMFDWeights(1e0, .25)
                //.setSlipRateConstraint("BOTH", 1000, 1000)
                .setSlipRateUncertaintyConstraint(1e0, 0)
        		.setUnmodifiedSlipRateStdvs(true)
                .setReweightTargetQuantity("MAD"))
        		.setGutenbergRichterMFD(3.9, 1.0, 0.9, 1.2, 7.85)
        		.setPaleoRateConstraints(1e0, 1e0, "GEOLOGIC_SLIP_4FEB", "NZSHM22_C_42");
            
        FaultSystemSolution solution = runner.runInversion();

//		System.out.println("Solution MFDS...");
//		for (ArrayList<String> row: runner.getTabularSolutionMfds()) {
//			System.out.println(row);
//		}

        System.out.println("Solution MFDS...");
        for (ArrayList<String> row : runner.getTabularSolutionMfds()) {
            System.out.println(row);
        }
        System.out.println("Solution MFDS V2 ...");      
        for (ArrayList<String> row : runner.getTabularSolutionMfdsV2()) {
            System.out.println(row);
        }        
//		System.out.println(solution.getEnergies().toString());

        File solutionFile = new File(outputDir, "CrustalInversionSolution.zip");
        solution.write(solutionFile);

//	U3FaultSystemIO.writeSol(solution, solutionFile);

        
        /*
         * 
{
  "task_arguments": {
    "round": "0",
    "config_type": "crustal",
    "deformation_model": "FAULT_MODEL",
    "rupture_set_file_id": "RmlsZToxNTg3LjBuVm9GdA==",
    "rupture_set": "/WORKING/downloads/RmlsZToxNTg3LjBuVm9GdA==/RupSet_Cl_FM(CFM_0_9_SANSTVZ_D90)_mnSbS(2)_mnSSPP(2)_mxSSL(0.5)_mxFS(2000)_noInP(T)_slRtP(0.05)_slInL(F)_cfFr(0.75)_cfRN(2)_cfRTh(0.5)_cfRP(0
.01)_fvJm(T)_jmPTh(0.001)_cmRkTh(360)_mxJmD(15)_plCn(T)_adMnD(6)_adScFr(0.2)_bi(F)_stGrSp(2)_coFr(0.5).zip",
    "completion_energy": "0.0",
    "max_inversion_time": "15",
    "mfd_equality_weight": "2e4",
    "mfd_inequality_weight": "2e4",
    "slip_rate_weighting_type": "BOTH",
    "slip_rate_weight": null,
    "slip_uncertainty_scaling_factor": null,
    "slip_rate_normalized_weight": "1e3",
    "slip_rate_unnormalized_weight": "1e5",
    
    "max_mag_type": "FILTER_RUPSET",
    "min_mag_sans": 6.8,
    "min_mag_tvz": 6.5,
    
    "max_mag_sans": 10.0,
    "max_mag_tvz": 7.5,
    
    "mfd_mag_gt_5_sans": 3.9,                                                                                                                                                                       [0/73029]
    "mfd_mag_gt_5_tvz": 1.2,
    
    "mfd_b_value_sans": 0.9,
    "mfd_b_value_tvz": 0.9,
    
    "mfd_transition_mag": 7.85,
    "tvz_slip_rate_factor": 0.3,
    "selection_interval_secs": "1",
    "threads_per_selector": "4",
    "averaging_threads": "4",
    "averaging_interval_secs": "30",
    "non_negativity_function": "TRY_ZERO_RATES_OFTEN",
    "perturbation_function": "EXPONENTIAL_SCALE",
    "cooling_schedule": "FAST_SA",
    "scaling_relationship": "SIMPLE_CRUSTAL",
    "scaling_recalc_mag": "True",
    
    "paleo_rate_constraint_weight": "1e3",
    "paleo_rate_constraint": "GEOLOGIC_SLIP_4FEB",
    "paleo_probability_model": "NZSHM22_C_42",
    "paleo_parent_rate_smoothness_constraint_weight": "1e3",
    "scaling_c_val_dip_slip": 4.2,
    "scaling_c_val_strike_slip": 4.2,
    "initial_solution_id": "",
    
    "b_and_n": "{'tag': '1840-2020_MED + TVZd', 'b_sans': 0.9, 'N_sans': 3.9, 'b_tvz': 0.9, 'N_tvz': 1.2}",
    "scaling_c": "{'tag': 'MED', 'dip': 4.2, 'strike': 4.2}",
    "constraint_wts": "{'tag': 'MFD_SR_PL(3,3)', 'mfd_eq': '2e4', 'mfd_ineq': '2e4', 'sr_norm': '1e3', 'sr_unnorm': '1e5', 'paleo_rate': '1e3', 'paleo_smoothing': '1e3'}",
    "mag_ranges": "{'min_mag_sans': 6.8, 'min_mag_tvz': 6.5, 'max_mag_sans': 10.0, 'max_mag_tvz': 7.5}"
  },
  "job_arguments": {
    "java_threads": 16,
    "jvm_heap_max": "10",
    "working_path": "/WORKING",
    "root_folder": "/app",
    "general_task_id": "R2VuZXJhbFRhc2s6MjUzME43dHVL",
    "use_api": true,
    "task_id": 4,
    "java_gateway_port": 26533
  }
}    
    
    
    
    
         * 
         * 
         * 
         */
    }

}

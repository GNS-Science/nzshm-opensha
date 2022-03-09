package nz.cri.gns.NZSHM22.opensha.inversion;

import nz.cri.gns.NZSHM22.opensha.calc.SimplifiedScalingRelationship;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.*;
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
    private NZSHM22_MagBounds.MaxMagType maxMagType = NZSHM22_MagBounds.MaxMagType.NONE;

    private double paleoRateConstraintWt = 0;
    private double paleoParentRateSmoothnessConstraintWeight = 0;
    private NZSHM22_PaleoRates paleoRates;
    private NZSHM22_PaleoProbabilityModel paleoProbabilityModel;
    private double sansSlipRateFactor = -1;
    private double tvzSlipRateFactor = -1;

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
        this.maxMagType = NZSHM22_MagBounds.MaxMagType.valueOf(maxMagType);
        this.maxMagSans = maxMagSans;
        this.maxMagTVZ = maxMagTVZ;
        return this;
    }

    /**
     * Sets regional slip scaling factor
     * @param sansSlipRateFactor
     * @param tvzSlipRateFactor
     * @return
     */
    public NZSHM22_CrustalInversionRunner setSlipRateFactor(double sansSlipRateFactor, double tvzSlipRateFactor){
        this.sansSlipRateFactor = sansSlipRateFactor;
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
        if (maxMagType != NZSHM22_MagBounds.MaxMagType.NONE) {
            branch.setValue(new NZSHM22_MagBounds(maxMagSans, maxMagTVZ, maxMagType));
        }
        if (tvzSlipRateFactor != -1 || sansSlipRateFactor != -1) {
            branch.setValue(new NZSHM22_SlipRateFactors(sansSlipRateFactor, tvzSlipRateFactor));
        }

        this.rupSet = NZSHM22_InversionFaultSystemRuptSet.loadCrustalRuptureSet(rupSetFile, branch);

        InversionModels inversionModel = branch.getValue(InversionModels.class);

        // this contains all inversion weights
        NZSHM22_CrustalInversionConfiguration inversionConfiguration;

        if (maxMagType == NZSHM22_MagBounds.MaxMagType.MANIPULATE_MFD) {
            inversionConfiguration = NZSHM22_CrustalInversionConfiguration.forModel(
                    inversionModel, rupSet, initialSolution, mfdEqualityConstraintWt, mfdInequalityConstraintWt, totalRateM5_Sans,
                    totalRateM5_TVZ, bValue_Sans, bValue_TVZ, mfdTransitionMag, minMag_Sans, minMag_TVZ, maxMagSans, maxMagTVZ,
                    mfdUncertWtdConstraintWt, mfdUncertWtdConstraintPower, mfdUncertWtdConstraintScalar, excludeRupturesBelowMinMag);
        } else{
            inversionConfiguration = NZSHM22_CrustalInversionConfiguration.forModel(
                    inversionModel, rupSet, initialSolution, mfdEqualityConstraintWt, mfdInequalityConstraintWt, totalRateM5_Sans,
                    totalRateM5_TVZ, bValue_Sans, bValue_TVZ, mfdTransitionMag, minMag_Sans, minMag_TVZ, 100, 100,
                    mfdUncertWtdConstraintWt, mfdUncertWtdConstraintPower, mfdUncertWtdConstraintScalar, excludeRupturesBelowMinMag);
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

        rupSet.addModule(new PaleoseismicConstraintData(rupSet, paleoRateConstraints, paleoProbabilityModel, null, null));

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
//                "C:\\Users\\volkertj\\Downloads\\NZSHM22_RuptureSet-UnVwdHVyZUdlbmVyYXRpb25UYXNrOjc5OTBvWWZMVw==(1).zip");
        		"./TEST/NZSHM22_RuptureSet-UnVwdHVyZUdlbmVyYXRpb25UYXNrOjg5ODJGamtLRw==.zip"); //Latest Prod
        File outputDir = new File(outputRoot, "inversions");
        Preconditions.checkState(outputDir.exists() || outputDir.mkdir());

        SimplifiedScalingRelationship scaling = new SimplifiedScalingRelationship();
        scaling.setupCrustal(4.2, 4.2);

        NZSHM22_CrustalInversionRunner runner = ((NZSHM22_CrustalInversionRunner) new NZSHM22_CrustalInversionRunner()

                .setMaxMags("MANIPULATE_MFD",10,7.5)//FILTER_RUPSET
                .setMinMags(6.8 , 6.5)
              //  .setInitialSolution("C:\\tmp\\rates.csv")
                .setInversionSeconds(1)
              // .setSlipRateFactor(0.8, 0.3)

                .setScalingRelationship(scaling, true)
                //.setDeformationModel("GEOD_NO_PRIOR_UNISTD_2010_RmlsZTo4NTkuMDM2Z2Rw")
                .setRuptureSetFile(ruptureSet)

               // .setGutenbergRichterMFDWeights(100.0, 1000.0)
                .setUncertaintyWeightedMFDWeights(10000, .75, 0.4)
                //.setSlipRateConstraint("BOTH", 1000, 1000)
                .setSlipRateUncertaintyConstraint(1000, 2)
                .setReweightTargetQuantity("MAD"))
                .setGutenbergRichterMFD(3.9, 1.0, 0.9, 1.2, 7.85)
                .setPaleoRateConstraints(0.01, 1000, "GEOLOGIC_SLIP_22FEB", "UCERF3_PLUS_PT25");

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

    }

}

package nz.cri.gns.NZSHM22.opensha.inversion;

import nz.cri.gns.NZSHM22.opensha.calc.SimplifiedScalingRelationship;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_LogicTreeBranch;
import org.dom4j.DocumentException;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;

import com.google.common.base.Preconditions;

import scratch.UCERF3.U3FaultSystemRupSet;
import scratch.UCERF3.enumTreeBranches.InversionModels;
import scratch.UCERF3.utils.U3FaultSystemIO;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Runs the standard NSHM inversion on a crustal rupture set.
 */
public class NZSHM22_CrustalInversionRunner extends NZSHM22_AbstractInversionRunner {

    //	private NZSHM22_CrustalInversionConfiguration inversionConfiguration;
    private int slipRateUncertaintyWeight;
    private int slipRateUncertaintyScalingFactor;
    private double totalRateM5_Sans = 3.6;
    private double totalRateM5_TVZ = 0.4;
    private double bValue_Sans = 1.05;
    private double bValue_TVZ = 1.25;
    private double minMag_Sans = 6.95;
    private double minMag_TVZ = 6.95;

    /**
     * Creates a new NZSHM22_InversionRunner with defaults.
     */
    public NZSHM22_CrustalInversionRunner() {
        super();
    }

    /**
     * New NZSHM22 Slip rate uncertainty constraint
     *
     * @param uncertaintyWeight
     * @param scalingFactor
     * @return
     * @throws IllegalArgumentException if the weighting types is not supported by
     *                                  this constraint
     */
    public NZSHM22_CrustalInversionRunner setSlipRateUncertaintyConstraint(
            AbstractInversionConfiguration.NZSlipRateConstraintWeightingType weightingType, int uncertaintyWeight, int scalingFactor) {
        Preconditions.checkArgument(weightingType == AbstractInversionConfiguration.NZSlipRateConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY,
                "setSlipRateUncertaintyConstraint() using %s is not supported. Use setSlipRateConstraint() instead.",
                weightingType);
        this.slipRateWeightingType = weightingType;
        this.slipRateUncertaintyWeight = uncertaintyWeight;
        this.slipRateUncertaintyScalingFactor = scalingFactor;
        return this;
    }

    /**
     * New NZSHM22 Slip rate uncertainty constraint
     *
     * @param weightingType
     * @param uncertaintyWeight
     * @param scalingFactor
     * @return
     */
    public NZSHM22_CrustalInversionRunner setSlipRateUncertaintyConstraint(String weightingType, int uncertaintyWeight,
                                                                           int scalingFactor) {
        return setSlipRateUncertaintyConstraint(AbstractInversionConfiguration.NZSlipRateConstraintWeightingType.valueOf(weightingType),
                uncertaintyWeight, scalingFactor);
    }

    public NZSHM22_CrustalInversionRunner setMinMagForSeismogenicRups(double minMag) {
        NZSHM22_InversionFaultSystemRuptSet.setMinMagForSeismogenicRups(minMag);
        return this;
    }

    /**
     * Sets the minimum magnitude for targetOnFaultSupraSeisMFDs
     * @param minMagSans
     * @param minMagTvz
     * @return this runner
     */
    public NZSHM22_AbstractInversionRunner setMinMagForTargetOnFaultSupraSeisMFDs(double minMagSans, double minMagTvz){
        this.minMag_Sans = minMagSans;
        this.minMag_TVZ = minMagTvz;
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

    public static NZSHM22_InversionFaultSystemRuptSet loadRuptureSet(File ruptureSetFile, NZSHM22_LogicTreeBranch branch) throws DocumentException, IOException {
        U3FaultSystemRupSet rupSetA = U3FaultSystemIO.loadRupSet(ruptureSetFile);
        return new NZSHM22_InversionFaultSystemRuptSet(rupSetA, branch);
    }

    @Override
    public NZSHM22_AbstractInversionRunner setRuptureSetFile(File ruptureSetFile) throws DocumentException, IOException {
        NZSHM22_LogicTreeBranch branch = NZSHM22_LogicTreeBranch.crustal();
        setupLTB(branch);
        this.rupSet = loadRuptureSet(ruptureSetFile, branch);
        if (recalcMags) {
            rupSet.recalcMags(scalingRelationship);
        }
        return this;
    }

    @Override
    protected NZSHM22_CrustalInversionRunner configure() {
        LogicTreeBranch logicTreeBranch = this.rupSet.getLogicTreeBranch();
        InversionModels inversionModel = (InversionModels) logicTreeBranch.getValue(InversionModels.class);

        // this contains all inversion weights
        NZSHM22_CrustalInversionConfiguration inversionConfiguration = NZSHM22_CrustalInversionConfiguration.forModel(
                inversionModel, rupSet, mfdEqualityConstraintWt, mfdInequalityConstraintWt, totalRateM5_Sans,
                totalRateM5_TVZ, bValue_Sans, bValue_TVZ, mfdTransitionMag, minMag_Sans, minMag_TVZ);

        solutionMfds = ((NZSHM22_CrustalInversionTargetMFDs) inversionConfiguration.getInversionTargetMfds())
                .getReportingMFDConstraintComponents();

        // set up slip rate config
        inversionConfiguration.setSlipRateWeightingType(this.slipRateWeightingType);
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
        NZSHM22_CrustalInversionInputGenerator inversionInputGenerator = new NZSHM22_CrustalInversionInputGenerator(
                rupSet, inversionConfiguration, null, null, null, null);
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
                "C:\\Code\\NZSHM\\nzshm-opensha\\src\\test\\resources\\AlpineVernonInversionSolution.zip");
//				"RupSet_Cl_FM(CFM_0_9_SANSTVZ_2010)_mnSbS(2)_mnSSPP(2)_mxSSL(0.5)_mxFS(2000)_noInP(T)_slRtP(0.05)_slInL(F)_cfFr(0.75)_cfRN(2)_cfRTh(0.5)_cfRP(0.01)_fvJm(T)_jmPTh(0.001)_cmRkTh(360)_mxJmD(15)_plCn(T)_adMnD(6)_adScFr(0.2).zip");
//        		"C:\\Users\\volkertj\\Downloads\\RupSet_Cl_FM(CFM_0_9_SANSTVZ_D90)_noInP(T)_slRtP(0.05)_slInL(F)_cfFr(0.75)_cfRN(2)_cfRTh(0.5)_cfRP(0.01)_fvJm(T)_jmPTh(0.001)_cmRkTh(360)_mxJmD(15)_plCn(T)_adMnD(6)_adScFr(0)_bi(F)_stGrSp(2)_coFr(0.5)(5).zip");
        File outputDir = new File(outputRoot, "inversions");
        Preconditions.checkState(outputDir.exists() || outputDir.mkdir());

        SimplifiedScalingRelationship scaling = new SimplifiedScalingRelationship();
        scaling.setupCrustal(4, 4.1);

        NZSHM22_CrustalInversionRunner runner = ((NZSHM22_CrustalInversionRunner) new NZSHM22_CrustalInversionRunner()
                .setInversionSeconds(1)
                .setScalingRelationship(scaling, true)
             //   .setDeformationModel("GEOD_NO_PRIOR_UNISTD_2010_RmlsZTo4NTkuMDM2Z2Rw")
                .setRuptureSetFile(ruptureSet)
                .setGutenbergRichterMFDWeights(100.0, 1000.0)
                .setSlipRateConstraint("BOTH", 1000, 1000))
                .setSlipRateUncertaintyConstraint("NORMALIZED_BY_UNCERTAINTY", 1000, 2)
                .setGutenbergRichterMFD(4.0, 0.81, 0.91, 1.05, 7.85);

        FaultSystemSolution solution = runner.runInversion();

//		System.out.println("Solution MFDS...");
//		for (ArrayList<String> row: runner.getTabularSolutionMfds()) {
//			System.out.println(row);
//		}

        System.out.println("Solution MFDS...");
        for (ArrayList<String> row : runner.getTabularSolutionMfds()) {
            System.out.println(row);
        }
//		System.out.println(solution.getEnergies().toString());

        File solutionFile = new File(outputDir, "CrustalInversionSolution.zip");
        solution.write(solutionFile);

//	U3FaultSystemIO.writeSol(solution, solutionFile);

    }

}

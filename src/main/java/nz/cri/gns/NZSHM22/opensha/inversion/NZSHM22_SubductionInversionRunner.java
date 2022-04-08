package nz.cri.gns.NZSHM22.opensha.inversion;

import nz.cri.gns.NZSHM22.opensha.calc.SimplifiedScalingRelationship;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_LogicTreeBranch;
import org.dom4j.DocumentException;
import com.google.common.base.Preconditions;

import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import scratch.UCERF3.enumTreeBranches.InversionModels;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Runs the standard NSHM inversion on a subduction rupture set.
 */
public class NZSHM22_SubductionInversionRunner extends NZSHM22_AbstractInversionRunner {

	protected double mfdMinMag = 7.05;
		
	/**
	 * Creates a new NZSHM22_InversionRunner with defaults.
	 */
	public NZSHM22_SubductionInversionRunner() {
		super();
	}

	/**
	 * Sets GutenbergRichterMFD arguments
	 *
	 * @param totalRateM5      the number of M>=5's per year. TODO: ref David
	 *                         Rhodes/Chris Roland? [KKS, CBC]
	 * @param bValue
	 * @param mfdTransitionMag magnitude to switch from MFD equality to MFD
	 *                         inequality TODO: how to validate this number for NZ?
	 *                         (ref Morgan Page in USGS/UCERF3) [KKS, CBC]
	 * @param mfdMinMag			magnitude of minimum magnitude in MFD target, rate set to 1e-20 below [CDC]
	 * @return
	 */
	public NZSHM22_SubductionInversionRunner setGutenbergRichterMFD(double totalRateM5, double bValue,
			double mfdTransitionMag, double mfdMinMag) {
		this.totalRateM5 = totalRateM5;
		this.bValue = bValue;
		this.mfdTransitionMag = mfdTransitionMag;
		this.mfdMinMag = mfdMinMag;
		return this;
	}

	public NZSHM22_SubductionInversionRunner setGutenbergRichterMFD(double totalRateM5, double bValue,
			double mfdTransitionMag) {
		this.totalRateM5 = totalRateM5;
		this.bValue = bValue;
		this.mfdTransitionMag = mfdTransitionMag;
		return this;
	}

	@Override
	protected NZSHM22_SubductionInversionRunner configure() throws DocumentException, IOException {

		NZSHM22_LogicTreeBranch branch = NZSHM22_LogicTreeBranch.subductionInversion();
		setupLTB(branch);
		rupSet = NZSHM22_InversionFaultSystemRuptSet.loadSubductionRuptureSet(rupSetFile, branch);

		InversionModels inversionModel = branch.getValue(InversionModels.class);

		NZSHM22_SubductionInversionConfiguration inversionConfiguration = NZSHM22_SubductionInversionConfiguration
				.forModel(inversionModel, rupSet, initialSolution, mfdEqualityConstraintWt, mfdInequalityConstraintWt,
						mfdUncertWtdConstraintWt, mfdUncertWtdConstraintPower, mfdUncertWtdConstraintScalar,
						totalRateM5, bValue, mfdTransitionMag, mfdMinMag);

		// CBC This may not be needed long term
		solutionMfds = ((NZSHM22_SubductionInversionTargetMFDs) inversionConfiguration.getInversionTargetMfds()).getMFDConstraintComponents();

		if (this.slipRateWeightingType == AbstractInversionConfiguration.NZSlipRateConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY) {
			System.out.println("config for UNCERTAINTY_ADJUSTED " + this.slipRateUncertaintyWeight + ", "
					+ this.slipRateUncertaintyScalingFactor);
			inversionConfiguration.setSlipRateUncertaintyConstraintWt(this.slipRateUncertaintyWeight);
			inversionConfiguration.setSlipRateUncertaintyConstraintScalingFactor(this.slipRateUncertaintyScalingFactor);
		} else if (this.slipRateWeightingType != null)  {
			inversionConfiguration.setSlipRateWeightingType(this.slipRateWeightingType);
			inversionConfiguration.setSlipRateConstraintWt_normalized(this.slipRateConstraintWt_normalized);
			inversionConfiguration.setSlipRateConstraintWt_unnormalized(this.slipRateConstraintWt_unnormalized);
		}
		inversionConfiguration.setUnmodifiedSlipRateStdvs(unmodifiedSlipRateStdvs);

		NZSHM22_SubductionInversionInputGenerator inversionInputGenerator = new NZSHM22_SubductionInversionInputGenerator(
				rupSet, inversionConfiguration);
		setInversionInputGenerator(inversionInputGenerator);
		return this;
	}

	public static void main(String[] args) throws IOException, DocumentException {

		//File inputDir = new File("./TEST");
		File outputRoot = new File("/tmp");
		//File ruptureSet = new File("C:\\tmp\\NZSHM\\RupSet_Sub_FM(SBD_0_2_HKR_LR_30)_mnSbS(2)_mnSSPP(2)_mxSSL(0.5)_ddAsRa(2.0,5.0,7)_ddMnFl(0.5)_ddPsCo(0.0)_ddSzCo(0.0)_thFc(0.0).zip");
		File ruptureSet = new File("/home/chrisdc/NSHM/DEV/rupture_sets/RupSet_Sub_FM(SBD_0_3_HKR_LR_30)_mnSbS(2)_mnSSPP(2)_mxSSL(0.5)_ddAsRa(2.0,5.0,5)_ddMnFl(0.1)_ddPsCo(0.0)_ddSzCo(0.0)_thFc(0.0).zip");
		File outputDir = new File(outputRoot, "inversions");
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
		Preconditions.checkState(ruptureSet.exists());

		/*
		 * NZSHM22_InversionSolution-UnVwdHVyZUdlbmVyYXRpb25UYXNrOjIzMzliekRWcw==.zip
		 * mfd_equality_weight	1000.0
		 * mfd_inequality_weight	10000.0
		 * slip_rate_weighting_type	BOTH
		 * slip_rate_normalized_weight	1000.0
		 * slip_rate_unnormalized_weight	10000.0
		 * mfd_mag_gt_5	29
		 * mfd_b_value	1.05
		 * mfd_transition_mag	9.15
		 */

		double c = 4.0;
		double minMag = 8.0;
		SimplifiedScalingRelationship scale = new SimplifiedScalingRelationship();
		scale.setupSubduction(c);

		NZSHM22_SubductionInversionRunner runner = ((NZSHM22_SubductionInversionRunner) new NZSHM22_SubductionInversionRunner()
				.setScalingRelationship(scale, true)
				// .setScalingRelationship("SMPL_NZ_INT_MN", true)
				.setRuptureSetFile(ruptureSet)
				.setGutenbergRichterMFDWeights(1000, 1000.0)
				//.setUncertaintyWeightedMFDWeights(1000, 0.1, 0.4)
				.setGutenbergRichterMFDWeights(1.0e4, 0.0)
				.setSlipRateConstraint("BOTH", 1000, 1000.0)
				) // end super-class methods
				// .setGutenbergRichterMFD(29, 1.05, 8.85); //CBC add some sanity checking around the 3rd arg, it must be on a bin centre!
				.setGutenbergRichterMFD(29, 1.05, 8.85,minMag); //CBC add some sanity checking around the 3rd arg, it must be on a bin centre!

		FaultSystemSolution solution = runner
				.setInversionSeconds(60)
				.setNumThreadsPerSelector(1)
				.setSelectionInterval(2)
				.setDeformationModel("SBD_0_2_HKR_LR_30_CTP1")
//				.setInversionAveraging(2, 10)
				.runInversion();

		for (ArrayList<String> row: runner.getTabularSolutionMfds()) {
			System.out.println(row);
		}

		solution.write(new File(outputDir, "test_subscaling_min" + String.valueOf(minMag) + "_c" + String.valueOf(c) + ".zip"));

		System.out.println("Done!");
	}

}

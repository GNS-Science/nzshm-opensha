package nz.cri.gns.NZSHM22.opensha.inversion;

import org.dom4j.DocumentException;
import com.google.common.base.Preconditions;

import scratch.UCERF3.enumTreeBranches.InversionModels;
import scratch.UCERF3.inversion.UCERF3InversionConfiguration.SlipRateConstraintWeightingType;
import scratch.UCERF3.logicTree.LogicTreeBranch;
import scratch.UCERF3.utils.FaultSystemIO;
import scratch.UCERF3.utils.aveSlip.AveSlipConstraint;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Runs the standard NSHM inversion on a crustal rupture set.
 */
public class NZSHM22_CrustalInversionRunner extends NZSHM22_AbstractInversionRunner {

//	private NZSHM22_CrustalInversionConfiguration inversionConfiguration;
	private int slipRateUncertaintyWeight;
	private int slipRateUncertaintyScalingFactor;

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
	 * @throws IllegalArgumentException if the weighting types is not supported by
	 *                                  this constraint
	 * @return
	 */
	public NZSHM22_CrustalInversionRunner setSlipRateUncertaintyConstraint(
			SlipRateConstraintWeightingType weightingType, int uncertaintyWeight, int scalingFactor) {
		Preconditions.checkArgument(weightingType == SlipRateConstraintWeightingType.UNCERTAINTY_ADJUSTED,
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
		return setSlipRateUncertaintyConstraint(SlipRateConstraintWeightingType.valueOf(weightingType), uncertaintyWeight,
				scalingFactor);
	}

	public NZSHM22_CrustalInversionRunner configure() {
		LogicTreeBranch logicTreeBranch = this.rupSet.getLogicTreeBranch();
		InversionModels inversionModel = logicTreeBranch.getValue(InversionModels.class);

		// this contains all inversion weights
		NZSHM22_CrustalInversionConfiguration inversionConfiguration = NZSHM22_CrustalInversionConfiguration
				.forModel(inversionModel, rupSet, mfdEqualityConstraintWt, mfdInequalityConstraintWt);

		// set up slip rate config
		inversionConfiguration.setSlipRateWeightingType(this.slipRateWeightingType);
		if (this.slipRateWeightingType == SlipRateConstraintWeightingType.UNCERTAINTY_ADJUSTED) {
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
		List<AveSlipConstraint> aveSlipConstraints = null;
		NZSHM22_CrustalInversionInputGenerator inversionInputGenerator = new NZSHM22_CrustalInversionInputGenerator(
				rupSet, inversionConfiguration, null, aveSlipConstraints, null, null);
		super.setInversionInputGenerator(inversionInputGenerator);
		return this;
	}

	public static void main(String[] args) throws IOException, DocumentException {

		File inputDir = new File("/home/chrisbc/Downloads");
		File outputRoot = new File("/tmp/NZSHM");
		File ruptureSet = new File(inputDir,
				"RupSet_Az_FM(CFM_0_3_SANSTVZ)_mxSbScLn(0.5)_mxAzCh(60.0)_mxCmAzCh(560.0)_mxJpDs(5.0)_mxTtAzCh(60.0)_thFc(0.0)(1).zip");
		File outputDir = new File(outputRoot, "inversions");
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());

		NZSHM22_CrustalInversionRunner runner = ((NZSHM22_CrustalInversionRunner) new NZSHM22_CrustalInversionRunner()
				.setInversionSeconds(10).setRuptureSetFile(ruptureSet).setGutenbergRichterMFDWeights(100.0, 1000.0))
						.setSlipRateUncertaintyConstraint("UNCERTAINTY_ADJUSTED", 1000, 2);
		NZSHM22_InversionFaultSystemSolution solution = runner.configure().runInversion();

		File solutionFile = new File(outputDir, "CrustalInversionSolution.zip");

		FaultSystemIO.writeSol(solution, solutionFile);

	}
}

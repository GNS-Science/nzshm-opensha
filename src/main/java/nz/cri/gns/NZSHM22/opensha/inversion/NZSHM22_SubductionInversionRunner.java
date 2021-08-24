package nz.cri.gns.NZSHM22.opensha.inversion;

import org.dom4j.DocumentException;
import com.google.common.base.Preconditions;

import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.FaultGridAssociations;
import scratch.UCERF3.enumTreeBranches.InversionModels;

import scratch.UCERF3.inversion.InversionFaultSystemSolution;
import scratch.UCERF3.logicTree.U3LogicTreeBranch;
import scratch.UCERF3.utils.U3FaultSystemIO;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs the standard NSHM inversion on a crustal rupture set.
 */
public class NZSHM22_SubductionInversionRunner extends NZSHM22_AbstractInversionRunner {
	private double totalRateM5; // = 5d;
	private double bValue; // = 1d;
	private Double mfdTransitionMag; // = 7.85; // TODO: how to validate this number for NZ? (ref Morgan Page in
	// // USGS/UCERF3) [KKS, CBC]

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
	 * @return
	 */
	public NZSHM22_SubductionInversionRunner setGutenbergRichterMFD(double totalRateM5, double bValue,
			double mfdTransitionMag) {
		this.totalRateM5 = totalRateM5;
		this.bValue = bValue;
		this.mfdTransitionMag = mfdTransitionMag;
		return this;
	}

	@Override
	public NZSHM22_AbstractInversionRunner setRuptureSetFile(File ruptureSetFile)
			throws IOException, DocumentException {
		rupSet = loadSubductionRupSet(ruptureSetFile);
		rupSet.removeModuleInstances(FaultGridAssociations.class);
		return this;
	}

	@Override
	protected NZSHM22_SubductionInversionRunner configure() {
		U3LogicTreeBranch logicTreeBranch = this.rupSet.getLogicTreeBranch();
		InversionModels inversionModel = logicTreeBranch.getValue(InversionModels.class);

		NZSHM22_SubductionInversionConfiguration inversionConfiguration = NZSHM22_SubductionInversionConfiguration
				.forModel(inversionModel, rupSet, mfdEqualityConstraintWt, mfdInequalityConstraintWt, totalRateM5,
						bValue, mfdTransitionMag);

		solutionMfds = ((NZSHM22_SubductionInversionTargetMFDs) inversionConfiguration.getInversionTargetMfds()).getMFDConstraintComponents();

		if (this.slipRateWeightingType != null) {
			inversionConfiguration.setSlipRateWeightingType(this.slipRateWeightingType);
			inversionConfiguration.setSlipRateConstraintWt_normalized(this.slipRateConstraintWt_normalized);
			inversionConfiguration.setSlipRateConstraintWt_unnormalized(this.slipRateConstraintWt_unnormalized);
		}

		NZSHM22_SubductionInversionInputGenerator inversionInputGenerator = new NZSHM22_SubductionInversionInputGenerator(
				rupSet, inversionConfiguration);
		setInversionInputGenerator(inversionInputGenerator);
		return this;
	}

	public static void main(String[] args) throws IOException, DocumentException {

		File inputDir = new File("./TEST");
		File outputRoot = new File("./TEST");
		File ruptureSet = new File(inputDir,
				"RupSet_Sub_FM(SBD_0_2A_HKR_LR_30)_mnSbS(2)_mnSSPP(2)_mxSSL(0.5)_ddAsRa(2.0,5.0,5)_ddMnFl(0.1)_ddPsCo(0.0)_ddSzCo(0.0)_thFc(0.0).zip");
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
		NZSHM22_SubductionInversionRunner runner = ((NZSHM22_SubductionInversionRunner) new NZSHM22_SubductionInversionRunner()
				.setRuptureSetFile(ruptureSet)
				.setGutenbergRichterMFDWeights(1000.0, 10000.0)
				.setSlipRateConstraint("BOTH", 1000.0, 10000.0)
				) // end super-class methods
				.setGutenbergRichterMFD(29, 1.05, 9.15);

		FaultSystemSolution solution = runner
				.setInversionSeconds(20)
				.setNumThreadsPerSelector(2)
				.setSelectionInterval(2)
				.setInversionAveraging(2, 10)
				.runInversion();
		
		for (ArrayList<String> row: runner.getTabularSolutionMfds()) {
			System.out.println(row);
		}

		solution.write(new File(outputDir, "NewFormatSubductionInversionSolution-RWcw.zip"));

		System.out.println("Done!");
	}

}

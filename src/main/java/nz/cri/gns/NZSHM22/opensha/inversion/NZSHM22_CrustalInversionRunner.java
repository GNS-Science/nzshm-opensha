package nz.cri.gns.NZSHM22.opensha.inversion;

import org.apache.commons.math3.util.Precision;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.data.function.XY_DataSetList;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.UniqueRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupSetDiagnosticsPageGen.HistScalar;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupSetDiagnosticsPageGen.HistScalarValues;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;

import scratch.UCERF3.U3FaultSystemRupSet;
import scratch.UCERF3.enumTreeBranches.InversionModels;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.inversion.UCERF3InversionConfiguration.SlipRateConstraintWeightingType;

import scratch.UCERF3.logicTree.U3LogicTreeBranch;
import scratch.UCERF3.utils.MFD_InversionConstraint;
import scratch.UCERF3.utils.U3FaultSystemIO;
import scratch.UCERF3.utils.aveSlip.AveSlipConstraint;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

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
		return setSlipRateUncertaintyConstraint(SlipRateConstraintWeightingType.valueOf(weightingType),
				uncertaintyWeight, scalingFactor);
	}

	public NZSHM22_CrustalInversionRunner setMinMagForSeismogenicRups(double minMag) {
		NZSHM22_InversionFaultSystemRuptSet.setMinMagForSeismogenicRups(minMag);
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

	public static NZSHM22_InversionFaultSystemRuptSet loadRuptureSet(File ruptureSetFile, U3LogicTreeBranch branch) throws DocumentException, IOException {
		U3FaultSystemRupSet rupSetA = U3FaultSystemIO.loadRupSet(ruptureSetFile);
		return NZSHM22_InversionFaultSystemRuptSet.fromCrustal(rupSetA, branch);
	}

	@Override
	public NZSHM22_AbstractInversionRunner setRuptureSetFile(File ruptureSetFile) throws DocumentException, IOException {
		U3LogicTreeBranch branch = U3LogicTreeBranch.DEFAULT;
		if(scalingRelationship != null){
			branch.clearValue(ScalingRelationships.class);
			branch.setValue(scalingRelationship);
		}
		this.rupSet = loadRuptureSet(ruptureSetFile, branch);
		if(recalcMags){
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
				totalRateM5_TVZ, bValue_Sans, bValue_TVZ, mfdTransitionMag);

		solutionMfds = ((NZSHM22_CrustalInversionTargetMFDs) inversionConfiguration.getInversionTargetMfds())
				.getMFDConstraintComponents();

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
		File ruptureSet = new File(inputDir,
			"RupSet_Cl_FM(CFM_0_9_SANSTVZ_D90)_mnSbS(2)_mnSSPP(2)_mxSSL(0.5)_mxFS(2000)_noInP(T)_slRtP(0.05)_slInL(F)_cfFr(0.75)_cfRN(2)_cfRTh(0.5)_cfRP(0.01)_fvJm(T)_jmPTh(0.001)_cmRkTh(360)_mxJmD(15)_plCn(T)_adMnD(6)_adScFr(0.2)_.zip");
//				"RupSet_Cl_FM(CFM_0_9_SANSTVZ_2010)_mnSbS(2)_mnSSPP(2)_mxSSL(0.5)_mxFS(2000)_noInP(T)_slRtP(0.05)_slInL(F)_cfFr(0.75)_cfRN(2)_cfRTh(0.5)_cfRP(0.01)_fvJm(T)_jmPTh(0.001)_cmRkTh(360)_mxJmD(15)_plCn(T)_adMnD(6)_adScFr(0.2).zip");
		File outputDir = new File(outputRoot, "inversions");
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());

		NZSHM22_CrustalInversionRunner runner = ((NZSHM22_CrustalInversionRunner) new NZSHM22_CrustalInversionRunner()
				.setInversionSeconds(1)
				.setScalingRelationship("SMPL_NZ_CRU_LW", true)
				.setRuptureSetFile(ruptureSet)
				.setGutenbergRichterMFDWeights(100.0, 1000.0)
				.setSlipRateConstraint("BOTH", 1000, 1000))
				.setSlipRateUncertaintyConstraint("UNCERTAINTY_ADJUSTED", 1000, 2)
		        .setGutenbergRichterMFD(5, 0.5, 1.2, 1.5, 7.85);

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

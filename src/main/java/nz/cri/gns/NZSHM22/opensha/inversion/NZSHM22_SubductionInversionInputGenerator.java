package nz.cri.gns.NZSHM22.opensha.inversion;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

//import org.opensha.commons.eq.MagUtils;
//import org.opensha.commons.util.IDPairing;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionInputGenerator;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
//import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.APrioriInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.MFDEqualityInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.MFDInequalityInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.MFDSubSectNuclInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.RupRateMinimizationConstraint;
//import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.MFDLaplacianSmoothingInversionConstraint;
//import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.MFDParticipationSmoothnessInversionConstraint;
//import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.MFDSubSectNuclInversionConstraint;
//import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.PaleoRateInversionConstraint;
//import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.PaleoSlipInversionConstraint;
//import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.PaleoVisibleEventRateSmoothnessInversionConstraint;
//import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.ParkfieldInversionConstraint;
//import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.RupRateMinimizationConstraint;
//import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.RupRateSmoothingInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.SlipRateInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.SlipRateUncertaintyInversionConstraint;
//import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.TotalMomentInversionConstraint;
import org.opensha.sha.faultSurface.FaultSection;
//import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;
//import com.google.common.base.Stopwatch;
//import com.google.common.collect.Lists;
//import com.google.common.collect.Maps;

import nz.cri.gns.NZSHM22.opensha.analysis.NZSHM22_FaultSystemRupSetCalc;
//import cern.colt.function.tdouble.IntIntDoubleFunction;
//import cern.colt.list.tdouble.DoubleArrayList;
//import cern.colt.list.tint.IntArrayList;
//import cern.colt.matrix.tdouble.DoubleMatrix2D;
import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.SlipEnabledRupSet;
import scratch.UCERF3.utils.SectionMFD_constraint;
//import scratch.UCERF3.analysis.FaultSystemRupSetCalc;
//import scratch.UCERF3.enumTreeBranches.InversionModels;
//
//import scratch.UCERF3.logicTree.LogicTreeBranch;
//import scratch.UCERF3.simulatedAnnealing.ConstraintRange;
//import scratch.UCERF3.utils.MFD_InversionConstraint;
//import scratch.UCERF3.utils.SectionMFD_constraint;
import scratch.UCERF3.utils.aveSlip.AveSlipConstraint;
import scratch.UCERF3.utils.paleoRateConstraints.PaleoProbabilityModel;
import scratch.UCERF3.utils.paleoRateConstraints.PaleoRateConstraint;
import scratch.UCERF3.utils.paleoRateConstraints.UCERF3_PaleoProbabilityModel;
import scratch.UCERF3.inversion.UCERF3InversionConfiguration.SlipRateConstraintWeightingType;

/**
 * This class is used to generate inversion inputs (A/A_ineq matrices, d/d_ineq
 * vectors) for a given rupture set, inversion configuration, paleo rate
 * constraints, improbability constraint, and paleo probability model. It can
 * also save these inputs to a zip file to be run on high performance computing.
 *
 *
 */
public class NZSHM22_SubductionInversionInputGenerator extends InversionInputGenerator {

	private static final boolean D = false;
	/**
	 * this enables use of the getQuick and setQuick methods on the sparse matrices.
	 * this comes with a performance boost, but disables range checks and is more
	 * prone to errors.
	 */
//	private static final boolean QUICK_GETS_SETS = true;

	// inputs
//	private NZSHM22_InversionFaultSystemRuptSet rupSet;
	private NZSHM22_SubductionInversionConfiguration config;
//	private List<AveSlipConstraint> aveSlipConstraints;
//	private double[] improbabilityConstraint;
//	private PaleoProbabilityModel paleoProbabilityModel;

	public NZSHM22_SubductionInversionInputGenerator(NZSHM22_InversionFaultSystemRuptSet rupSet,
			NZSHM22_SubductionInversionConfiguration config
	// , List<AveSlipConstraint> aveSlipConstraints
	) {
		super(rupSet, buildConstraints(rupSet, config), config.getInitialRupModel(), buildWaterLevel(config, rupSet));
//		this.rupSet = rupSet;
		this.config = config;
//		this.aveSlipConstraints = aveSlipConstraints;
	}

	private static List<InversionConstraint> buildConstraints(NZSHM22_InversionFaultSystemRuptSet rupSet,
			NZSHM22_SubductionInversionConfiguration config
	// List<PaleoRateConstraint> paleoRateConstraints,
//			List<AveSlipConstraint> aveSlipConstraints
	// PaleoProbabilityModel paleoProbabilityModel
	) {

		System.out.println("buildConstraints");
		System.out.println("================");

		System.out.println("config.getSlipRateWeightingType(): " + config.getSlipRateWeightingType());
		if (config.getSlipRateWeightingType() == SlipRateConstraintWeightingType.UNCERTAINTY_ADJUSTED) {
			System.out.println(
					"config.getSlipRateUncertaintyConstraintWt() :" + config.getSlipRateUncertaintyConstraintWt());
			System.out.println("config.getSlipRateUncertaintyConstraintScalingFactor() :"
					+ config.getSlipRateUncertaintyConstraintScalingFactor());
		} else {
			System.out.println(
					"config.getSlipRateConstraintWt_normalized(): " + config.getSlipRateConstraintWt_normalized());
			System.out.println(
					"config.getSlipRateConstraintWt_unnormalized(): " + config.getSlipRateConstraintWt_unnormalized());
		}
		System.out.println("config.getMinimizationConstraintWt(): " + config.getMinimizationConstraintWt());
		System.out.println("config.getMagnitudeEqualityConstraintWt(): " + config.getMagnitudeEqualityConstraintWt());
		System.out
				.println("config.getMagnitudeInequalityConstraintWt(): " + config.getMagnitudeInequalityConstraintWt());
		System.out.println("config.getNucleationMFDConstraintWt():" + config.getNucleationMFDConstraintWt());

		// builds constraint instances
		List<InversionConstraint> constraints = new ArrayList<>();

		if (config.getSlipRateConstraintWt_normalized() > 0d || config.getSlipRateConstraintWt_unnormalized() > 0d) {
			// add slip rate constraint
			double[] sectSlipRateReduced = rupSet.getSlipRateForAllSections();
			constraints.add(new SlipRateInversionConstraint(config.getSlipRateConstraintWt_normalized(),
					config.getSlipRateConstraintWt_unnormalized(), config.getSlipRateWeightingType(), rupSet,
					sectSlipRateReduced));
		}

		// Rupture rate minimization constraint
		// Minimize the rates of ruptures below SectMinMag (strongly so that they have
		// zero rates)
		if (config.getMinimizationConstraintWt() > 0.0) {
			List<Integer> belowMinIndexes = new ArrayList<>();
			for (int r = 0; r < rupSet.getNumRuptures(); r++)
				if (rupSet.isRuptureBelowSectMinMag(r))
					belowMinIndexes.add(r);
			constraints.add(new RupRateMinimizationConstraint(config.getMinimizationConstraintWt(), belowMinIndexes));
		}

		// Constrain Solution MFD to equal the Target MFD
		// This is for equality constraints only -- inequality constraints must be
		// encoded into the A_ineq matrix instead since they are nonlinear
		if (config.getMagnitudeEqualityConstraintWt() > 0.0) {
			HashSet<Integer> excludeRupIndexes = null;
			constraints.add(new MFDEqualityInversionConstraint(rupSet, config.getMagnitudeEqualityConstraintWt(),
					config.getMfdEqualityConstraints(), excludeRupIndexes));
		}

		// Prepare MFD Inequality Constraint (not added to A matrix directly since it's
		// nonlinear)
		if (config.getMagnitudeInequalityConstraintWt() > 0.0)
			constraints.add(new MFDInequalityInversionConstraint(rupSet, config.getMagnitudeInequalityConstraintWt(),
					config.getMfdInequalityConstraints()));

//		// MFD Subsection nucleation MFD constraint
//		ArrayList<SectionMFD_constraint> MFDConstraints = null;
//		if (config.getNucleationMFDConstraintWt() > 0.0) {
//			MFDConstraints = NZSHM22_FaultSystemRupSetCalc.getCharInversionSectMFD_Constraints(rupSet);
//			constraints.add(new MFDSubSectNuclInversionConstraint(rupSet, config.getNucleationMFDConstraintWt(),
//					MFDConstraints));
//		}

		return constraints;
	}

	private static double[] buildWaterLevel(NZSHM22_SubductionInversionConfiguration config, FaultSystemRupSet rupSet) {
		double minimumRuptureRateFraction = config.getMinimumRuptureRateFraction();
		if (minimumRuptureRateFraction > 0) {
			// set up minimum rupture rates (water level)
			double[] minimumRuptureRateBasis = config.getMinimumRuptureRateBasis();
			Preconditions.checkNotNull(minimumRuptureRateBasis,
					"minimum rate fraction specified but no minimum rate basis given!");

			// first check to make sure that they're not all zeros
			boolean allZeros = true;
			int numRuptures = rupSet.getNumRuptures();
			for (int i = 0; i < numRuptures; i++) {
				if (minimumRuptureRateBasis[i] > 0) {
					allZeros = false;
					break;
				}
			}
			Preconditions.checkState(!allZeros, "cannot set water level when water level rates are all zero!");

			double[] minimumRuptureRates = new double[numRuptures];
			for (int i = 0; i < numRuptures; i++)
				minimumRuptureRates[i] = minimumRuptureRateBasis[i] * minimumRuptureRateFraction;
			return minimumRuptureRates;
		}
		return null;
	}

	public void generateInputs() {
		generateInputs(null, D);
	}

	public NZSHM22_SubductionInversionConfiguration getConfig() {
		return config;
	}

}
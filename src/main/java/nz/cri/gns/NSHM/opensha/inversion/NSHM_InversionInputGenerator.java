package nz.cri.gns.NSHM.opensha.inversion;

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
//import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.TotalMomentInversionConstraint;
import org.opensha.sha.faultSurface.FaultSection;
//import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;
//import com.google.common.base.Stopwatch;
//import com.google.common.collect.Lists;
//import com.google.common.collect.Maps;

import nz.cri.gns.NSHM.opensha.analysis.NSHM_FaultSystemRupSetCalc;
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

/**
 * This class is used to generate inversion inputs (A/A_ineq matrices, d/d_ineq
 * vectors) for a given rupture set, inversion configuration, paleo rate
 * constraints, improbability constraint, and paleo probability model. It can
 * also save these inputs to a zip file to be run on high performance computing.
 *
 *
 */
public class NSHM_InversionInputGenerator extends InversionInputGenerator {

	private static final boolean D = false;
	/**
	 * this enables use of the getQuick and setQuick methods on the sparse matrices.
	 * this comes with a performance boost, but disables range checks and is more
	 * prone to errors.
	 */
	private static final boolean QUICK_GETS_SETS = true;

	// inputs
	private NSHM_InversionFaultSystemRuptSet rupSet;
	private NSHM_InversionConfiguration config;
	private List<PaleoRateConstraint> paleoRateConstraints;
	private List<AveSlipConstraint> aveSlipConstraints;
	private double[] improbabilityConstraint;
	private PaleoProbabilityModel paleoProbabilityModel;

	public NSHM_InversionInputGenerator(NSHM_InversionFaultSystemRuptSet rupSet, NSHM_InversionConfiguration config,
			List<PaleoRateConstraint> paleoRateConstraints, List<AveSlipConstraint> aveSlipConstraints,
			double[] improbabilityConstraint, // may become an object in the future
			PaleoProbabilityModel paleoProbabilityModel) {
		super(rupSet, buildConstraints(rupSet, config, paleoRateConstraints, aveSlipConstraints, paleoProbabilityModel),
				config.getInitialRupModel(), buildWaterLevel(config, rupSet));
		this.rupSet = rupSet;
		this.config = config;
		this.paleoRateConstraints = paleoRateConstraints;
		this.improbabilityConstraint = improbabilityConstraint;
		this.aveSlipConstraints = aveSlipConstraints;
		this.paleoProbabilityModel = paleoProbabilityModel;
	}

	private static PaleoProbabilityModel defaultProbModel = null;

	/**
	 * Loads the default paleo probability model for UCERF3 (Glenn's file). Can be
	 * turned into an enum if we get alternatives
	 *
	 * @return
	 * @throws IOException
	 */
	public static PaleoProbabilityModel loadDefaultPaleoProbabilityModel() throws IOException {
		if (defaultProbModel == null)
			defaultProbModel = UCERF3_PaleoProbabilityModel.load();
		return defaultProbModel;
	}

	private static List<InversionConstraint> buildConstraints(NSHM_InversionFaultSystemRuptSet rupSet,
			NSHM_InversionConfiguration config, List<PaleoRateConstraint> paleoRateConstraints,
			List<AveSlipConstraint> aveSlipConstraints, PaleoProbabilityModel paleoProbabilityModel) {

		System.out.println("buildConstraints");
		System.out.println("config.getSlipRateConstraintWt_normalized(): " + config.getSlipRateConstraintWt_normalized());
		System.out.println("config.getSlipRateConstraintWt_unnormalized(): " +  config.getSlipRateConstraintWt_unnormalized());
		System.out.println("config.getMinimizationConstraintWt(): " +  config.getMinimizationConstraintWt());
		System.out.println("config.getMagnitudeEqualityConstraintWt(): " +  config.getMagnitudeEqualityConstraintWt());
		System.out.println("config.getMagnitudeInequalityConstraintWt(): " +   config.getMagnitudeInequalityConstraintWt());
		System.out.println("config.getNucleationMFDConstraintWt():" + config.getNucleationMFDConstraintWt());

		// builds constraint instances
		List<InversionConstraint> constraints = new ArrayList<>();

		double[] sectSlipRateReduced = rupSet.getSlipRateForAllSections();

		if (config.getSlipRateConstraintWt_normalized() > 0d || config.getSlipRateConstraintWt_unnormalized() > 0d)
			// add slip rate constraint
			constraints.add(new SlipRateInversionConstraint(config.getSlipRateConstraintWt_normalized(),
					config.getSlipRateConstraintWt_unnormalized(), config.getSlipRateWeightingType(), rupSet,
					sectSlipRateReduced));

//		if (config.getPaleoRateConstraintWt() > 0d)
//			constraints.add(new PaleoRateInversionConstraint(rupSet, config.getPaleoRateConstraintWt(),
//					paleoRateConstraints, paleoProbabilityModel));
//
//		if (config.getPaleoSlipConstraintWt() > 0d)
//			constraints.add(new PaleoSlipInversionConstraint(rupSet, config.getPaleoSlipConstraintWt(),
//					aveSlipConstraints, sectSlipRateReduced));
//
//		if (config.getRupRateConstraintWt() > 0d) {
//			// This is the RupRateConstraintWt for ruptures not in UCERF2
//			double zeroRupRateConstraintWt = 0;
//			if (config.isAPrioriConstraintForZeroRates())
//				zeroRupRateConstraintWt = config.getRupRateConstraintWt()*config.getAPrioriConstraintForZeroRatesWtFactor();
//			constraints.add(new APrioriInversionConstraint(config.getRupRateConstraintWt(), zeroRupRateConstraintWt, config.getA_PrioriRupConstraint()));
//		}

//		// This constrains rates of ruptures that differ by only 1 subsection
//		if (config.getRupRateSmoothingConstraintWt() > 0)
//			constraints.add(new RupRateSmoothingInversionConstraint(config.getRupRateSmoothingConstraintWt(), rupSet));
//

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

//		// MFD Smoothness Constraint - Constrain participation MFD to be uniform for each fault subsection
//		if (config.getParticipationSmoothnessConstraintWt() > 0.0)
//			constraints.add(new MFDParticipationSmoothnessInversionConstraint(rupSet,
//					config.getParticipationSmoothnessConstraintWt(), config.getParticipationConstraintMagBinSize()));

		// MFD Subsection nucleation MFD constraint
		ArrayList<SectionMFD_constraint> MFDConstraints = null;
		if (config.getNucleationMFDConstraintWt() > 0.0) {
			MFDConstraints = NSHM_FaultSystemRupSetCalc.getCharInversionSectMFD_Constraints(rupSet);
			constraints.add(new MFDSubSectNuclInversionConstraint(rupSet, config.getNucleationMFDConstraintWt(),
					MFDConstraints));
		}

//		// MFD Smoothing constraint - MFDs spatially smooth along adjacent subsections on a parent section (Laplacian smoothing)
//		if (config.getMFDSmoothnessConstraintWt() > 0.0 || config.getMFDSmoothnessConstraintWtForPaleoParents() > 0.0) {
//			if (MFDConstraints == null)
//				MFDConstraints = FaultSystemRupSetCalc.getCharInversionSectMFD_Constraints(rupSet);
//
//			HashSet<Integer> paleoParentIDs = new HashSet<>();
//			// Get list of parent IDs that have a paleo data point (paleo event rate or paleo mean slip)
//			if (config.getPaleoRateConstraintWt() > 0.0) {
//				for (int i=0; i<paleoRateConstraints.size(); i++) {
//					int paleoParentID = rupSet.getFaultSectionDataList().get(paleoRateConstraints.get(i).getSectionIndex()).getParentSectionId();
//					paleoParentIDs.add(paleoParentID);
//				}
//			}

//			if (config.getPaleoSlipConstraintWt() > 0.0) {
//				for (int i=0; i<aveSlipConstraints.size(); i++) {
//					int paleoParentID = rupSet.getFaultSectionDataList().get(aveSlipConstraints.get(i).getSubSectionIndex()).getParentSectionId();
//					paleoParentIDs.add(paleoParentID);
//				}
//			}
//
//			constraints.add(new MFDLaplacianSmoothingInversionConstraint(rupSet, config.getMFDSmoothnessConstraintWt(),
//					config.getMFDSmoothnessConstraintWtForPaleoParents(), paleoParentIDs, MFDConstraints));
//		}

//		// Constraint solution moment to equal deformation-model moment
//		if (config.getMomentConstraintWt() > 0.0)
//			constraints.add(new TotalMomentInversionConstraint(rupSet, config.getMomentConstraintWt(), rupSet.getTotalReducedMomentRate()));
//

//		// Constrain paleoseismically-visible event rates along parent sections to be smooth
//		if (config.getEventRateSmoothnessWt() > 0.0)
//			constraints.add(new PaleoVisibleEventRateSmoothnessInversionConstraint(rupSet, config.getEventRateSmoothnessWt(), paleoProbabilityModel));

		return constraints;
	}

	private static double[] buildWaterLevel(NSHM_InversionConfiguration config, FaultSystemRupSet rupSet) {
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

	/**
	 * This returns the normalized distance along a rupture that a paleoseismic
	 * trench is located (Glenn's x/L). It is between 0 and 0.5. This currently puts
	 * the trench in the middle of the subsection. We need this for the UCERF3
	 * probability of detecting a rupture in a trench.
	 *
	 * @return
	 */
	public static double getDistanceAlongRupture(List<FaultSection> sectsInRup, int targetSectIndex) {
		return getDistanceAlongRupture(sectsInRup, targetSectIndex, null);
	}

	public static double getDistanceAlongRupture(List<FaultSection> sectsInRup, int targetSectIndex,
			Map<Integer, Double> traceLengthCache) {
		double distanceAlongRup = 0;

		double totalLength = 0;
		double lengthToRup = 0;
		boolean reachConstraintLoc = false;

		// Find total length (km) of fault trace and length (km) from one end to the
		// paleo trench location
		for (int i = 0; i < sectsInRup.size(); i++) {
			FaultSection sect = sectsInRup.get(i);
			int sectIndex = sect.getSectionId();
			Double sectLength = null;
			if (traceLengthCache != null) {
				sectLength = traceLengthCache.get(sectIndex);
				if (sectLength == null) {
					sectLength = sect.getFaultTrace().getTraceLength();
					traceLengthCache.put(sectIndex, sectLength);
				}
			} else {
				sectLength = sect.getFaultTrace().getTraceLength();
			}
			totalLength += sectLength;
			if (sectIndex == targetSectIndex) {
				reachConstraintLoc = true;
				// We're putting the trench in the middle of the subsection for now
				lengthToRup += sectLength / 2;
			}
			// We haven't yet gotten to the trench subsection so keep adding to lengthToRup
			if (reachConstraintLoc == false)
				lengthToRup += sectLength;
		}

		if (!reachConstraintLoc) // check to make sure we came across the trench subsection in the rupture
			throw new IllegalStateException("Paleo site subsection was not included in rupture subsections");

		// Normalized distance along the rainbow (Glenn's x/L) - between 0 and 1
		distanceAlongRup = lengthToRup / totalLength;
		// Adjust to be between 0 and 0.5 (since rainbow is symmetric about 0.5)
		if (distanceAlongRup > 0.5)
			distanceAlongRup = 1 - distanceAlongRup;

		return distanceAlongRup;
	}

	public NSHM_InversionConfiguration getConfig() {
		return config;
	}

	public List<PaleoRateConstraint> getPaleoRateConstraints() {
		return paleoRateConstraints;
	}

	public double[] getImprobabilityConstraint() {
		return improbabilityConstraint;
	}

	public PaleoProbabilityModel getPaleoProbabilityModel() {
		return paleoProbabilityModel;
	}

}
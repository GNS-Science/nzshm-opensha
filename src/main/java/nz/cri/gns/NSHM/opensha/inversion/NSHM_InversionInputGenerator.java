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

//import cern.colt.function.tdouble.IntIntDoubleFunction;
//import cern.colt.list.tdouble.DoubleArrayList;
//import cern.colt.list.tint.IntArrayList;
//import cern.colt.matrix.tdouble.DoubleMatrix2D;
import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.SlipEnabledRupSet;
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
 * This class is used to generate inversion inputs (A/A_ineq matrices, d/d_ineq vectors) for a given
 * rupture set, inversion configuration, paleo rate constraints, improbability constraint, and paleo
 * probability model. It can also save these inputs to a zip file to be run on high performance
 * computing.
 * 
 * @author Kevin, Morgan, Ned
 *
 */
public class NSHM_InversionInputGenerator extends InversionInputGenerator {
	
	private static final boolean D = false;
	/**
	 * this enables use of the getQuick and setQuick methods on the sparse matrices.
	 * this comes with a performance boost, but disables range checks and is more prone
	 * to errors.
	 */
	private static final boolean QUICK_GETS_SETS = true;
	
	// inputs
	private NSHM_InversionFaultSystemRuptSet rupSet;
	private NSHM_InversionConfiguration config;
	private List<PaleoRateConstraint> paleoRateConstraints;
	private List<AveSlipConstraint> aveSlipConstraints;
	private double[] improbabilityConstraint;
	private PaleoProbabilityModel paleoProbabilityModel;
	
	public NSHM_InversionInputGenerator(
			NSHM_InversionFaultSystemRuptSet rupSet,
			NSHM_InversionConfiguration config,
			List<PaleoRateConstraint> paleoRateConstraints,
			List<AveSlipConstraint> aveSlipConstraints,
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
	 * Loads the default paleo probability model for UCERF3 (Glenn's file). Can be turned into
	 * an enum if we get alternatives
	 * @return
	 * @throws IOException 
	 */
	public static PaleoProbabilityModel loadDefaultPaleoProbabilityModel() throws IOException {
		if (defaultProbModel == null)
			defaultProbModel = UCERF3_PaleoProbabilityModel.load();
		return defaultProbModel;
	}
	
	private static List<InversionConstraint> buildConstraints(
			SlipEnabledRupSet rupSet,
			NSHM_InversionConfiguration config,
			List<PaleoRateConstraint> paleoRateConstraints,
			List<AveSlipConstraint> aveSlipConstraints,
			PaleoProbabilityModel paleoProbabilityModel) {
		
		// builds constraint instances
		List<InversionConstraint> constraints = new ArrayList<>();
		
		double[] sectSlipRateReduced = rupSet.getSlipRateForAllSections();
		
		if (config.getSlipRateConstraintWt_normalized() > 0d
				|| config.getSlipRateConstraintWt_unnormalized() > 0d)
			// add slip rate constraint
			constraints.add(new SlipRateInversionConstraint(config.getSlipRateConstraintWt_normalized(),
					config.getSlipRateConstraintWt_unnormalized(), config.getSlipRateWeightingType(),
					rupSet, sectSlipRateReduced));
		
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
//		// Rupture rate minimization constraint
//		// Minimize the rates of ruptures below SectMinMag (strongly so that they have zero rates)
//		if (config.getMinimizationConstraintWt() > 0.0) {
//			List<Integer> belowMinIndexes = new ArrayList<>();
//			for (int r=0; r<rupSet.getNumRuptures(); r++)
//				if (rupSet.isRuptureBelowSectMinMag(r))
//					belowMinIndexes.add(r);
//			constraints.add(new RupRateMinimizationConstraint(config.getMinimizationConstraintWt(), belowMinIndexes));
//		}
		
		// Constrain Solution MFD to equal the Target MFD 
		// This is for equality constraints only -- inequality constraints must be
		// encoded into the A_ineq matrix instead since they are nonlinear
		if (config.getMagnitudeEqualityConstraintWt() > 0.0) {
			HashSet<Integer> excludeRupIndexes = null;
//			if (config.isExcludeParkfieldRupsFromMfdEqualityConstraints() && config.getParkfieldConstraintWt() > 0.0) {
//				excludeRupIndexes = new HashSet<>();
//				int parkfieldParentSectID = 32;
//				List<Integer> potentialRups = rupSet.getRupturesForParentSection(parkfieldParentSectID);
//				rupLoop:
//					for (int i=0; i<potentialRups.size(); i++) {
//						List<Integer> sects = rupSet.getSectionsIndicesForRup(potentialRups.get(i));
//						// Make sure there are 6-8 subsections
//						if (sects.size()<6 || sects.size()>8)
//							continue rupLoop;
//						// Make sure each section in rup is in Parkfield parent section
//						for (int s=0; s<sects.size(); s++) {
//							int parent = rupSet.getFaultSectionData(sects.get(s)).getParentSectionId();
//							if (parent != parkfieldParentSectID)
//								continue rupLoop;
//						}
//						excludeRupIndexes.add(potentialRups.get(i));
//					}
//			}
			constraints.add(new MFDEqualityInversionConstraint(rupSet, config.getMagnitudeEqualityConstraintWt(),
					config.getMfdEqualityConstraints(), excludeRupIndexes));
		}
		
		// Prepare MFD Inequality Constraint (not added to A matrix directly since it's nonlinear)
		if (config.getMagnitudeInequalityConstraintWt() > 0.0)	
			constraints.add(new MFDInequalityInversionConstraint(rupSet, config.getMagnitudeInequalityConstraintWt(),
					config.getMfdInequalityConstraints()));
		
//		// MFD Smoothness Constraint - Constrain participation MFD to be uniform for each fault subsection
//		if (config.getParticipationSmoothnessConstraintWt() > 0.0)
//			constraints.add(new MFDParticipationSmoothnessInversionConstraint(rupSet,
//					config.getParticipationSmoothnessConstraintWt(), config.getParticipationConstraintMagBinSize()));
		
//		// MFD Subsection nucleation MFD constraint
//		ArrayList<SectionMFD_constraint> MFDConstraints = null;
//		if (config.getNucleationMFDConstraintWt() > 0.0) {
//			MFDConstraints = FaultSystemRupSetCalc.getCharInversionSectMFD_Constraints(rupSet);
//			constraints.add(new MFDSubSectNuclInversionConstraint(rupSet, config.getNucleationMFDConstraintWt(), MFDConstraints));
//		}
		
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
//		// Constraint rupture-rate for M~6 Parkfield earthquakes
//		// The Parkfield eqs are defined as rates of 6, 7, and 8 subsection ruptures in the Parkfield parent section (which has 8 subsections in total)
//		// THIS CONSTRAINT WILL NOT WORK IF SUBSECTIONS DRASTICALLY CHANGE IN SIZE OR IF PARENT-SECT-IDS CHANGE!
//		if (config.getParkfieldConstraintWt() > 0.0) {
//			if(D) System.out.println("\nAdding Parkfield rupture-rate constraints to A matrix ...");
//			double ParkfieldConstraintWt = config.getParkfieldConstraintWt();
//			double ParkfieldMeanRate = 1.0/25.0; // Bakun et al. (2005)
//			
//			// Find Parkfield M~6 ruptures
//			List<Integer> parkfieldRups = findParkfieldRups(rupSet);
//			
//			constraints.add(new ParkfieldInversionConstraint(ParkfieldConstraintWt, ParkfieldMeanRate, parkfieldRups));
//		}

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
			for (int i=0; i<numRuptures; i++) {
				if (minimumRuptureRateBasis[i] > 0) {
					allZeros = false;
					break;
				}
			}
			Preconditions.checkState(!allZeros, "cannot set water level when water level rates are all zero!");
			
			double[] minimumRuptureRates = new double[numRuptures];
			for (int i=0; i < numRuptures; i++)
				minimumRuptureRates[i] = minimumRuptureRateBasis[i]*minimumRuptureRateFraction;
			return minimumRuptureRates;
		}
		return null;
	}
	
	public void generateInputs() {
		generateInputs(null, D);
	}
		
	/**
	 * This returns the normalized distance along a rupture that a paleoseismic trench
	 * is located (Glenn's x/L).  It is between 0 and 0.5.
	 * This currently puts the trench in the middle of the subsection.
	 * We need this for the UCERF3 probability of detecting a rupture in a trench.
	 * @return
	 */
	public static double getDistanceAlongRupture(
			List<FaultSection> sectsInRup, int targetSectIndex) {
		return getDistanceAlongRupture(sectsInRup, targetSectIndex, null);
	}
	
	public static double getDistanceAlongRupture(
			List<FaultSection> sectsInRup, int targetSectIndex,
			Map<Integer, Double> traceLengthCache) {
		double distanceAlongRup = 0;
		
		double totalLength = 0;
		double lengthToRup = 0;
		boolean reachConstraintLoc = false;
		
		// Find total length (km) of fault trace and length (km) from one end to the paleo trench location
		for (int i=0; i<sectsInRup.size(); i++) {
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
			totalLength+=sectLength;
			if (sectIndex == targetSectIndex) {
				reachConstraintLoc = true;
				// We're putting the trench in the middle of the subsection for now
				lengthToRup+=sectLength/2;
			}
			// We haven't yet gotten to the trench subsection so keep adding to lengthToRup
			if (reachConstraintLoc == false)
				lengthToRup+=sectLength;
		}
		
		if (!reachConstraintLoc) // check to make sure we came across the trench subsection in the rupture
			throw new IllegalStateException("Paleo site subsection was not included in rupture subsections");
		
		// Normalized distance along the rainbow (Glenn's x/L) - between 0 and 1
		distanceAlongRup = lengthToRup/totalLength;
		// Adjust to be between 0 and 0.5 (since rainbow is symmetric about 0.5)
		if (distanceAlongRup>0.5)
			distanceAlongRup=1-distanceAlongRup;
		
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
	

//	public static void main(String[] args) throws IOException {
//		LogicTreeBranch branch = LogicTreeBranch.DEFAULT;
//		NSHM_InversionFaultSystemRuptSet rupSet = NSHM_InversionFaultSystemRupSetFactory.forBranch(branch);
//		NSHM_InversionConfiguration config = NSHM_InversionConfiguration.forModel(
//				branch.getValue(InversionModels.class), rupSet);
//		// first enable all other constraints
//		config.setRupRateSmoothingConstraintWt(1d);
//		config.setMagnitudeEqualityConstraintWt(1d);
//		config.setSmoothnessWt(10000);
//		config.setMomentConstraintWt(1d);
//		config.setRupRateConstraintWt(1d);
//		config.setEventRateSmoothnessWt(1d);
//		config.setParticipationSmoothnessConstraintWt(1d);
//		// disable any/all constraints below
////		config.setEventRateSmoothnessWt(0d);
////		config.setMFDSmoothnessConstraintWt(0d);
////		config.setMFDSmoothnessConstraintWtForPaleoParents(0d);
////		config.setMinimizationConstraintWt(0d);
////		config.setMomentConstraintWt(0d);
////		config.setNucleationMFDConstraintWt(0d);
////		config.setMagnitudeEqualityConstraintWt(0d);
////		config.setMagnitudeInequalityConstraintWt(0d);
////		config.setPaleoRateConstraintWt(0d);
////		config.setPaleoSlipWt(0d);
////		config.setParkfieldConstraintWt(0d);
////		config.setParticipationSmoothnessConstraintWt(0d);
////		config.setRupRateConstraintWt(0d);
////		config.setRupRateSmoothingConstraintWt(0d);
////		config.setSmoothnessWt(0d);
//		// always need these on for old to work
////		config.setSlipRateConstraintWt_normalized(0d);
////		config.setSlipRateConstraintWt_unnormalized(0d);
//		
//		// get the paleo rate constraints
//		List<PaleoRateConstraint> paleoRateConstraints = CommandLineInversionRunner.getPaleoConstraints(
//					rupSet.getFaultModel(), rupSet);
//
//		// get the improbability constraints
//		double[] improbabilityConstraint = null; // null for now
//
//		// paleo probability model
//		PaleoProbabilityModel paleoProbabilityModel = NSHM_InversionInputGenerator.loadDefaultPaleoProbabilityModel();
//
//		List<AveSlipConstraint> aveSlipConstraints = AveSlipConstraint.load(rupSet.getFaultSectionDataList());
//		
//		NSHM_InversionInputGenerator gen = new NSHM_InversionInputGenerator(
//				rupSet, config, paleoRateConstraints, aveSlipConstraints, improbabilityConstraint, paleoProbabilityModel);
//		
//		double[] preGenInitial = gen.initialSolution;
//		
//		System.out.println("BUILDING ORIGINAL");
//		gen.generateInputsOld(null);
//		DoubleMatrix2D A_orig = gen.A;
//		DoubleMatrix2D A_ineq_orig = gen.A_ineq;
//		double[] d_orig = gen.d;
//		double[] d_ineq_orig = gen.d_ineq;
//		List<ConstraintRange> origRanges = gen.constraintRowRanges;
//		
//		double[] initial_orig = gen.initialSolution;
//		
//		gen.A = null;
//		gen.A_ineq = null;
//		gen.d = null;
//		gen.d_ineq = null;
//		gen.initialSolution = preGenInitial;
//		gen.constraintRowRanges = null;
//		
//		System.out.println("BUILDING NEW");
//		gen.generateInputs(true);
//		DoubleMatrix2D A_new = gen.A;
//		DoubleMatrix2D A_ineq_new = gen.A_ineq;
//		double[] d_new = gen.d;
//		double[] d_ineq_new = gen.d_ineq;
//		List<ConstraintRange> newRanges = gen.constraintRowRanges;
//		
//		double[] initial_new = gen.initialSolution;
//
//		System.out.println("A orig size: "+A_orig.rows()+" x "+A_orig.columns());
//		System.out.println("A new size: "+A_new.rows()+" x "+A_new.columns());
//		if (A_ineq_orig != null || A_ineq_new != null) {
//			System.out.println("A_ineq orig size: "+A_ineq_orig.rows()+" x "+A_ineq_orig.columns());
//			System.out.println("A_ineq new size: "+A_ineq_new.rows()+" x "+A_ineq_new.columns());
//		}
//		
//		for (boolean ineq : new boolean [] { false, true }) {
//			List<ConstraintRange> ranges1 = getMatches(origRanges, ineq);
//			List<ConstraintRange> ranges2 = getMatches(newRanges, ineq);
//			Preconditions.checkState(ranges1.size() == ranges2.size(),
//					"Range sizes inconsistent: %s != %s", ranges1.size(), ranges2.size());
//			for (int i=0; i<ranges1.size(); i++) {
//				ConstraintRange r1 = ranges1.get(i);
//				ConstraintRange r2 = ranges2.get(i);
//				Preconditions.checkState(r1.startRow == r2.startRow,
//						"Start row mismatch:\n\tORIG: %s\n\tNEW: %s", r1, r2);
//				Preconditions.checkState(r1.endRow == r2.endRow,
//						"End row mismatch:\n\tORIG: %s\n\tNEW: %s", r1, r2);
//			}
//		}
//		
//		System.out.println("Validating A");
//		validateA(A_orig, A_new, origRanges, false);
//		if (A_ineq_orig != null || A_ineq_new != null) {
//			System.out.println("Validating A_ineq");
//			validateA(A_ineq_orig, A_ineq_new, origRanges, true);
//		}
//		
//		System.out.println("Validating D");
//		validateD(d_orig, d_new, origRanges, false);
//		if (d_ineq_orig != null || d_ineq_new != null) {
//			System.out.println("Validating D_ineq");
//			validateD(d_ineq_orig, d_ineq_new, origRanges, true);
//		}
//
//		System.out.println("Validating initial");
//		validateRates(initial_orig, initial_new);
//	}

	/*
	private static List<ConstraintRange> getMatches(List<ConstraintRange> ranges, boolean ineq) {
		List<ConstraintRange> ret = new ArrayList<>();
		for (ConstraintRange range : ranges)
			if (ineq == range.inequality)
				ret.add(range);
		return ret;
	}
	
	private static class ValidateFunc implements IntIntDoubleFunction {
		
		private DoubleMatrix2D compare;
		private List<ConstraintRange> constraintRanges;
		private boolean ineq;
		
		private long count = 0;

		public ValidateFunc(DoubleMatrix2D compare, List<ConstraintRange> constraintRanges, boolean ineq) {
			this.compare = compare;
			this.constraintRanges = constraintRanges;
			this.ineq = ineq;
		}

		@Override
		public double apply(int row, int col, double val) {
			if (compare != null) {
				double oVal = compare.get(row, col);
				ConstraintRange matchRange = null;
				if (val != oVal) {
					for (ConstraintRange range : constraintRanges)
						if (range.contains(row, ineq))
							matchRange = range;
				}
				Preconditions.checkState(val == oVal,
						"Value mismatch at row=%s, col=%s: %s != %s\nConstraint: %s",
						row, col, val, oVal, matchRange);
			}
			count++;
			return val;
		}
		
	}
	
	private static void validateA(DoubleMatrix2D A_orig, DoubleMatrix2D A_new,
			List<ConstraintRange> constraintRanges, boolean ineq) {
		Preconditions.checkState(A_orig != A_new, "orig and new are same instance!");
		
		ValidateFunc validateFunc = new ValidateFunc(A_new, constraintRanges, ineq);
		A_orig.forEachNonZero(validateFunc);
		long origCount = validateFunc.count;
		
		// now check that they're the same size
		ValidateFunc countFunc = new ValidateFunc(null, null, ineq);
		A_new.forEachNonZero(countFunc);
		long newCount = countFunc.count;
		
		Preconditions.checkState(origCount == newCount,
				"Nonzero count mismatch: %s != %s", origCount, newCount);
		
		System.out.println("Validated "+origCount+" non-zero values");
	}
	
	private static void validateD(double[] d_orig, double[] d_new,
			List<ConstraintRange> constraintRanges, boolean ineq) {
		Preconditions.checkState(d_orig != d_new, "orig and new are same instance!");
		Preconditions.checkState(d_orig.length == d_new.length,
				"d length mismatch: %s != %s", d_orig.length, d_new.length);
		for (int i=0; i<d_orig.length; i++) {
			ConstraintRange matchRange = null;
			if (d_orig[i] != d_new[i]) {
				for (ConstraintRange range : constraintRanges)
					if (range.contains(i, ineq))
						matchRange = range;
			}
			Preconditions.checkState(d_orig[i] == d_new[i], "d mismatch at %s: %s != %s\nConstraint: %s",
					i, d_orig[i], d_new[i], matchRange);
		}
		System.out.println("Validated "+d_orig.length+" data values");
	}
	
	private static void validateRates(double[] origRates, double[] newRates) {
		Preconditions.checkState(origRates != newRates, "orig and new are same instance!");
		Preconditions.checkState(origRates.length == newRates.length,
				"rates length mismatch: %s != %s", origRates.length, newRates.length);
		for (int i=0; i<newRates.length; i++)
			Preconditions.checkState(origRates[i] == newRates[i], "rate mismatch at %s: %s != %s",
					i, origRates[i], newRates[i]);
		System.out.println("Validated "+origRates.length+" rate values");
	}
	*/
}
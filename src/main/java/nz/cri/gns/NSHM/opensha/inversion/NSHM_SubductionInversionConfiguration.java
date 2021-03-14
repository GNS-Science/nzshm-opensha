package nz.cri.gns.NSHM.opensha.inversion;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.dom4j.Element;
import org.opensha.commons.metadata.XMLSaveable;
import org.opensha.commons.util.XMLUtils;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;

import com.google.common.collect.Lists;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.enumTreeBranches.InversionModels;
import scratch.UCERF3.inversion.UCERF3InversionConfiguration;
import scratch.UCERF3.inversion.UCERF3InversionConfiguration.SlipRateConstraintWeightingType;
import scratch.UCERF3.utils.MFD_InversionConstraint;

/**
 * This represents all of the inversion configuration parameters specific to an
 * individual model on the NZSHM22 logic tree. Parameters can be fetched for a
 * given logic tree branch with the <code>forModel(...)</code> method.
 * 
 * based on scratch.UCERF3.inversion.UCERF3InversionConfiguration
 * 
 * @author chrisbc
 *
 */
public class NSHM_SubductionInversionConfiguration extends NSHM_InversionConfiguration {

	public static final String XML_METADATA_NAME = "InversionConfiguration";

	private double slipRateConstraintWt_normalized;
	private double slipRateConstraintWt_unnormalized;
	private SlipRateConstraintWeightingType slipRateWeighting;
//	private double magnitudeEqualityConstraintWt;
//	private double magnitudeInequalityConstraintWt;

	private double nucleationMFDConstraintWt;

	private double minimizationConstraintWt;

	private double[] initialRupModel;
	// these are the rates that should be used for water level computation. this
	// will
	// often be set equal to initial rup model or a priori rup constraint
	private double[] minimumRuptureRateBasis;
	private double MFDTransitionMag;
	private List<MFD_InversionConstraint> mfdEqualityConstraints;
	private List<MFD_InversionConstraint> mfdInequalityConstraints;
	private double minimumRuptureRateFraction;

	protected final static boolean D = true; // for debugging

	private String metadata;

	/**
	 * 
	 */
	public NSHM_SubductionInversionConfiguration() {
	}

	public static final double DEFAULT_MFD_EQUALITY_WT = 10;
	public static final double DEFAULT_MFD_INEQUALITY_WT = 1000;

	/**
	 * This generates an inversion configuration for the given inversion model and
	 * rupture set
	 * 
	 * @param model
	 * @param rupSet
	 * @return
	 */
	public static NSHM_SubductionInversionConfiguration forModel(InversionModels model, NSHM_InversionFaultSystemRuptSet rupSet) {
		double mfdEqualityConstraintWt = DEFAULT_MFD_EQUALITY_WT;
		double mfdInequalityConstraintWt = DEFAULT_MFD_INEQUALITY_WT;

		return forModel(model, rupSet, mfdEqualityConstraintWt, mfdInequalityConstraintWt);
	}

	/**
	 * This generates an inversion configuration for the given inversion model and
	 * rupture set
	 * 
	 * @param model
	 * @param rupSet
	 * @param mfdEqualityConstraintWt   weight of magnitude-distribution EQUALITY
	 *                                  constraint relative to slip-rate constraint
	 *                                  (recommended: 10)
	 * @param mfdInequalityConstraintWt weight of magnitude-distribution INEQUALITY
	 *                                  constraint relative to slip-rate constraint
	 *                                  (recommended: 1000)
	 * @return
	 */
	public static NSHM_SubductionInversionConfiguration forModel(InversionModels model, NSHM_InversionFaultSystemRuptSet rupSet,
			double mfdEqualityConstraintWt, double mfdInequalityConstraintWt) {
		return forModel(model, rupSet, mfdEqualityConstraintWt, mfdInequalityConstraintWt, null);
	}

	/**
	 * This generates an inversion configuration for the given inversion model and
	 * rupture set
	 * 
	 * @param model
	 * @param rupSet
	 * @param mfdEqualityConstraintWt   weight of magnitude-distribution EQUALITY
	 *                                  constraint relative to slip-rate constraint
	 *                                  (recommended: 10)
	 * @param mfdInequalityConstraintWt weight of magnitude-distribution INEQUALITY
	 *                                  constraint relative to slip-rate constraint
	 *                                  (recommended: 1000)
	 * @param modifiers                 command line modifier arguments (can be
	 *                                  null)
	 * @return
	 */
	public static NSHM_SubductionInversionConfiguration forModel(InversionModels model, NSHM_InversionFaultSystemRuptSet rupSet,
			double mfdEqualityConstraintWt, double mfdInequalityConstraintWt, CommandLine modifiers) {

		/*
		 * ******************************************* COMMON TO ALL MODELS
		 * *******************************************
		 */
		// Setting slip-rate constraint weights to 0 does not disable them! To disable
		// one or the other (both cannot be), use slipConstraintRateWeightingType Below
		double slipRateConstraintWt_normalized = 1; // For SlipRateConstraintWeightingType.NORMALIZED (also used for
													// SlipRateConstraintWeightingType.BOTH) -- NOT USED if
													// UNNORMALIZED!
		double slipRateConstraintWt_unnormalized = 100; // For SlipRateConstraintWeightingType.UNNORMALIZED (also used
														// for SlipRateConstraintWeightingType.BOTH) -- NOT USED if
														// NORMALIZED!
		// If normalized, slip rate misfit is % difference for each section (recommended
		// since it helps fit slow-moving faults). If unnormalized, misfit is absolute
		// difference.
		// BOTH includes both normalized and unnormalized constraints.
		SlipRateConstraintWeightingType slipRateWeighting = SlipRateConstraintWeightingType.BOTH; // (recommended: BOTH)

		// weight of magnitude-distribution EQUALITY constraint relative to slip-rate
		// constraint (recommended: 10)
		// mfdEqualityConstraintWt = 10;

		// weight of magnitude-distribution INEQUALITY constraint relative to slip-rate
		// constraint (recommended: 1000)
		// double mfdInequalityConstraintWt = 1000;

		// magnitude-bin size for MFD participation smoothness constraint
//		double participationConstraintMagBinSize = 0.1;

		// weight of rupture-rate smoothing constraint
//		double rupRateSmoothingConstraintWt = 0;

		// weight of rupture-rate minimization constraint weights relative to slip-rate
		// constraint (recommended: 10,000)
		// (currently used to minimization rates of rups below sectMinMag)
		double minimizationConstraintWt = 10000;

		// weight of entropy-maximization constraint (should smooth rupture rates)
		// (recommended: 10000)
//		double smoothnessWt = 0;

		// weight of Moment Constraint (set solution moment to equal deformation model
		// moment) (recommended: 1e-17)
//		double momentConstraintWt = 0;

		// setup MFD constraints
		NSHM_SubductionInversionTargetMFDs inversionMFDs = new NSHM_SubductionInversionTargetMFDs(rupSet);
		rupSet.setInversionTargetMFDs(inversionMFDs);
		List<MFD_InversionConstraint> mfdConstraints = inversionMFDs.getMFDConstraints();

		
		double MFDTransitionMag = 7.85; // magnitude to switch from MFD equality to MFD inequality

		String metadata = "";

//		/* *******************************************
//		 * MODEL SPECIFIC
//		 * ******************************************* */
//		// define model specific value here (leave them as null or unassigned, then set values
//		// in the below switch statement
//		
//		// weight of nucleation MFD constraint - applied on subsection basis
		double nucleationMFDConstraintWt;
		// fraction of the minimum rupture rate basis to be used as initial rates
		double minimumRuptureRateFraction = 0;
		double[] initialRupModel = null;
		double[] minimumRuptureRateBasis = null;
		
		SummedMagFreqDist targetOnFaultMFD = rupSet.getInversionTargetMFDs().getOnFaultSupraSeisMFD();

		if (model == InversionModels.CHAR_CONSTRAINED) {
				nucleationMFDConstraintWt = 0.01;
				// For water level
				minimumRuptureRateFraction = 0.0;
				
				minimumRuptureRateBasis = UCERF3InversionConfiguration.adjustStartingModel(
						UCERF3InversionConfiguration.getSmoothStartingSolution(rupSet, targetOnFaultMFD),
						mfdConstraints, rupSet, true);

				initialRupModel = new double[rupSet.getNumRuptures()];
		}

		/* end MODIFIERS */

		List<MFD_InversionConstraint> mfdInequalityConstraints = new ArrayList<MFD_InversionConstraint>();
		List<MFD_InversionConstraint> mfdEqualityConstraints = new ArrayList<MFD_InversionConstraint>();

		
		if (mfdEqualityConstraintWt > 0.0 && mfdInequalityConstraintWt > 0.0) {
			// we have both MFD constraints, apply a transition mag from equality to
			// inequality
			metadata += "\nMFDTransitionMag: " + MFDTransitionMag;
			mfdEqualityConstraints = restrictMFDConstraintMagRange(mfdConstraints,
					mfdConstraints.get(0).getMagFreqDist().getMinX(), MFDTransitionMag);
			mfdInequalityConstraints = restrictMFDConstraintMagRange(mfdConstraints, MFDTransitionMag,
					mfdConstraints.get(0).getMagFreqDist().getMaxX());
		} else if (mfdEqualityConstraintWt > 0.0) {
			mfdEqualityConstraints = mfdConstraints;
		} else if (mfdInequalityConstraintWt > 0.0) {
			mfdInequalityConstraints = mfdConstraints;
		} else {
			// no MFD constraints, do nothing
		}
	
		// NSHM-style config using setter methods...
		NSHM_SubductionInversionConfiguration newConfig = new NSHM_SubductionInversionConfiguration()
				// MFD config
				.setMagnitudeEqualityConstraintWt(mfdEqualityConstraintWt)
				.setMagnitudeInequalityConstraintWt(mfdInequalityConstraintWt)
				.setMfdEqualityConstraints(mfdEqualityConstraints)
				.setMfdInequalityConstraints(mfdInequalityConstraints)
				// Slip Rate config
				.setSlipRateConstraintWt_normalized(slipRateConstraintWt_normalized)
				.setSlipRateConstraintWt_unnormalized(slipRateConstraintWt_unnormalized)
				.setSlipRateWeightingType(slipRateWeighting)
				// Rate Minimization config
				.setMinimizationConstraintWt(minimizationConstraintWt)
				.setMinimumRuptureRateFraction(minimumRuptureRateFraction)
				.setMinimumRuptureRateBasis(minimumRuptureRateBasis)
				.setInitialRupModel(initialRupModel);

		return newConfig;
	}

	/**
	 * This method returns the input MFD constraint array with each constraint now
	 * restricted between minMag and maxMag. WARNING! This doesn't interpolate. For
	 * best results, set minMag & maxMag to points along original MFD constraint
	 * (i.e. 7.05, 7.15, etc)
	 * 
	 * @param mfConstraints
	 * @param minMag
	 * @param maxMag
	 * @return newMFDConstraints
	 */
	private static List<MFD_InversionConstraint> restrictMFDConstraintMagRange(
			List<MFD_InversionConstraint> mfdConstraints, double minMag, double maxMag) {

		List<MFD_InversionConstraint> newMFDConstraints = new ArrayList<MFD_InversionConstraint>();

		for (int i = 0; i < mfdConstraints.size(); i++) {
			IncrementalMagFreqDist originalMFD = mfdConstraints.get(i).getMagFreqDist();
			double delta = originalMFD.getDelta();
			IncrementalMagFreqDist newMFD = new IncrementalMagFreqDist(minMag, maxMag,
					(int) Math.round((maxMag - minMag) / delta + 1.0));
			newMFD.setTolerance(delta / 2.0);
			for (double m = minMag; m <= maxMag; m += delta) {
				// WARNING! This doesn't interpolate. For best results, set minMag & maxMag to
				// points along original MFD constraint (i.e. 7.05, 7.15, etc)
				newMFD.set(m, originalMFD.getClosestYtoX(m));
			}
			newMFDConstraints.add(i, new MFD_InversionConstraint(newMFD, mfdConstraints.get(i).getRegion()));
		}

		return newMFDConstraints;
	}

	public double getSlipRateConstraintWt_normalized() {
		return slipRateConstraintWt_normalized;
	}

	public NSHM_SubductionInversionConfiguration setSlipRateConstraintWt_normalized(double slipRateConstraintWt_normalized) {
		this.slipRateConstraintWt_normalized = slipRateConstraintWt_normalized;
		return this;
	}

	public double getSlipRateConstraintWt_unnormalized() {
		return slipRateConstraintWt_unnormalized;
	}

	public NSHM_SubductionInversionConfiguration setSlipRateConstraintWt_unnormalized(double slipRateConstraintWt_unnormalized) {
		this.slipRateConstraintWt_unnormalized = slipRateConstraintWt_unnormalized;
		return this;
	}

	public SlipRateConstraintWeightingType getSlipRateWeightingType() {
		return slipRateWeighting;
	}

	public NSHM_SubductionInversionConfiguration setSlipRateWeightingType(SlipRateConstraintWeightingType slipRateWeighting) {
		this.slipRateWeighting = slipRateWeighting;
		return this;
	}

	public double getMagnitudeEqualityConstraintWt() {
		return magnitudeEqualityConstraintWt;
	}

	public NSHM_SubductionInversionConfiguration setMagnitudeEqualityConstraintWt(double relativeMagnitudeEqualityConstraintWt) {
		this.magnitudeEqualityConstraintWt = relativeMagnitudeEqualityConstraintWt;
		return this;
	}

	public double getMagnitudeInequalityConstraintWt() {
		return magnitudeInequalityConstraintWt;
	}

	public NSHM_SubductionInversionConfiguration setMagnitudeInequalityConstraintWt(
			double relativeMagnitudeInequalityConstraintWt) {
		this.magnitudeInequalityConstraintWt = relativeMagnitudeInequalityConstraintWt;
		return this;
	}

	public double getMinimizationConstraintWt() {
		return minimizationConstraintWt;
	}

	public NSHM_SubductionInversionConfiguration setMinimizationConstraintWt(double relativeMinimizationConstraintWt) {
		this.minimizationConstraintWt = relativeMinimizationConstraintWt;
		return this;
	}

	public double[] getInitialRupModel() {
		return initialRupModel;
	}

	public NSHM_SubductionInversionConfiguration setInitialRupModel(double[] initialRupModel) {
		this.initialRupModel = initialRupModel;
		return this;
	}

	public double[] getMinimumRuptureRateBasis() {
		return minimumRuptureRateBasis;
	}

	public NSHM_SubductionInversionConfiguration setMinimumRuptureRateBasis(double[] minimumRuptureRateBasis) {
		this.minimumRuptureRateBasis = minimumRuptureRateBasis;
		return this;
	}

	public double getNucleationMFDConstraintWt() {
		return nucleationMFDConstraintWt;
	}

	public NSHM_SubductionInversionConfiguration setNucleationMFDConstraintWt(double relativeNucleationMFDConstraintWt) {
		this.nucleationMFDConstraintWt = relativeNucleationMFDConstraintWt;
		return this;
	}

	public List<MFD_InversionConstraint> getMfdEqualityConstraints() {
		return mfdEqualityConstraints;
	}

	public NSHM_SubductionInversionConfiguration setMfdEqualityConstraints(List<MFD_InversionConstraint> mfdEqualityConstraints) {
		this.mfdEqualityConstraints = mfdEqualityConstraints;
		return this;
	}

	public List<MFD_InversionConstraint> getMfdInequalityConstraints() {
		return mfdInequalityConstraints;
	}

	public NSHM_SubductionInversionConfiguration setMfdInequalityConstraints(
			List<MFD_InversionConstraint> mfdInequalityConstraints) {
		this.mfdInequalityConstraints = mfdInequalityConstraints;
		return this;
	}

	public double getMinimumRuptureRateFraction() {
		return minimumRuptureRateFraction;
	}

	public NSHM_SubductionInversionConfiguration setMinimumRuptureRateFraction(double minimumRuptureRateFraction) {
		this.minimumRuptureRateFraction = minimumRuptureRateFraction;
		return this;
	}

	public String getMetadata() {
		return metadata;
	}

	public void updateRupSetInfoString(FaultSystemRupSet rupSet) {
		String info = rupSet.getInfoString();
		info += "\n\n****** Inversion Configuration Metadata ******";
		info += "\n" + getMetadata();
		info += "\n**********************************************";
		rupSet.setInfoString(info);
	}

	public double getMFDTransitionMag() {
		return MFDTransitionMag;
	}

	public NSHM_SubductionInversionConfiguration setMFDTransitionMag(double mFDTransitionMag) {
		MFDTransitionMag = mFDTransitionMag;
		return this;
	}



	@Override
	public Element toXMLMetadata(Element root) {
		Element el = root.addElement(XML_METADATA_NAME);

		el.addAttribute("slipRateConstraintWt_normalized", slipRateConstraintWt_normalized + "");
		el.addAttribute("slipRateConstraintWt_unnormalized", slipRateConstraintWt_unnormalized + "");
		el.addAttribute("slipRateWeighting", slipRateWeighting.name() + "");

		el.addAttribute("magnitudeEqualityConstraintWt", magnitudeEqualityConstraintWt + "");
		el.addAttribute("magnitudeInequalityConstraintWt", magnitudeInequalityConstraintWt + "");

		el.addAttribute("MFDTransitionMag", MFDTransitionMag+"");
		el.addAttribute("minimumRuptureRateFraction", minimumRuptureRateFraction + "");

		// write MFDs
		Element equalMFDsEl = el.addElement("MFD_EqualityConstraints");
		mfdsToXML(equalMFDsEl, mfdEqualityConstraints);
		Element inequalMFDsEl = el.addElement("MFD_InequalityConstraints");
		mfdsToXML(inequalMFDsEl, mfdInequalityConstraints);

		return null;
	}

	private static void mfdsToXML(Element el, List<MFD_InversionConstraint> constraints) {
		for (int i = 0; i < constraints.size(); i++) {
			MFD_InversionConstraint constr = constraints.get(i);
			constr.toXMLMetadata(el);
		}
		// now set indexes
		List<Element> subEls = XMLUtils.getSubElementsList(el);
		for (int i = 0; i < subEls.size(); i++)
			subEls.get(i).addAttribute("index", i + "");
	}

	private static List<MFD_InversionConstraint> mfdsFromXML(Element mfdsEl) {
		List<Element> mfdElList = XMLUtils.getSortedChildElements(mfdsEl, null, "index");
		List<MFD_InversionConstraint> mfds = Lists.newArrayList();
		for (Element mfdEl : mfdElList) {
			MFD_InversionConstraint constr = MFD_InversionConstraint.fromXMLMetadata(mfdEl);
			mfds.add(constr);
		}
		return mfds;
	}
}

package nz.cri.gns.NZSHM22.opensha.inversion;

import java.util.ArrayList;
import java.util.List;

import org.dom4j.Element;
import org.opensha.commons.data.uncertainty.UncertainIncrMagFreqDist;
import org.opensha.commons.metadata.XMLSaveable;
import org.opensha.commons.util.XMLUtils;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import org.opensha.sha.earthquake.faultSysSolution.modules.InversionTargetMFDs;

public class AbstractInversionConfiguration implements XMLSaveable  {

	public static enum NZSlipRateConstraintWeightingType {
		NORMALIZED,
		UNNORMALIZED,
		BOTH,
		NORMALIZED_BY_UNCERTAINTY
	}

	private InversionTargetMFDs inversionTargetMfds;
	private double magnitudeEqualityConstraintWt;
	private double magnitudeInequalityConstraintWt;
	private double mfdUncertaintyWeightedConstraintWt;

	private double slipRateConstraintWt_normalized;
	private double slipRateConstraintWt_unnormalized;
	private NZSlipRateConstraintWeightingType slipRateWeighting;

	public static final String XML_METADATA_NAME = "InversionConfiguration";
	
	//New NZSHM scaling 
	private int slipRateUncertaintyConstraintWt;
	private int slipRateUncertaintyConstraintScalingFactor;
//	private double paleoRateConstraintWt; 
//	private double paleoSlipConstraintWt;

//	private double rupRateConstraintWt;
//	private double participationSmoothnessConstraintWt;
//	private double participationConstraintMagBinSize;
	private double nucleationMFDConstraintWt;
//	private double mfdSmoothnessConstraintWt;
//	private double mfdSmoothnessConstraintWtForPaleoParents;
//	private double rupRateSmoothingConstraintWt;
	private double minimizationConstraintWt;
//	private double momentConstraintWt;
//	private double parkfieldConstraintWt;
//	private double[] aPrioriRupConstraint;
	private double[] initialRupModel;
	// these are the rates that should be used for water level computation. this
	// will
	// often be set equal to initial rup model or a priori rup constraint
	private double[] minimumRuptureRateBasis;
	private double MFDTransitionMag;
	private List<IncrementalMagFreqDist> mfdEqualityConstraints;
	private List<IncrementalMagFreqDist> mfdInequalityConstraints;
	private List<UncertainIncrMagFreqDist> mfdUncertaintyWeightedConstraints;
	private double minimumRuptureRateFraction;	
	
	
	public AbstractInversionConfiguration() {
		super();
	}

	public InversionTargetMFDs getInversionTargetMfds() {
		return inversionTargetMfds;
	}

	public AbstractInversionConfiguration setInversionTargetMfds(InversionTargetMFDs inversionTargetMfds) {
		this.inversionTargetMfds = inversionTargetMfds;
		return this;
	}

	public double getMagnitudeEqualityConstraintWt() {
		return magnitudeEqualityConstraintWt;
	}

	public AbstractInversionConfiguration setMagnitudeEqualityConstraintWt(double relativeMagnitudeEqualityConstraintWt) {
		this.magnitudeEqualityConstraintWt = relativeMagnitudeEqualityConstraintWt;
		return this;
	}

	public double getMagnitudeInequalityConstraintWt() {
		return magnitudeInequalityConstraintWt;
	}

	public AbstractInversionConfiguration setMagnitudeInequalityConstraintWt(
			double relativeMagnitudeInequalityConstraintWt) {
		this.magnitudeInequalityConstraintWt = relativeMagnitudeInequalityConstraintWt;
		return this;
	}	
	
	public double getMagnitudeUncertaintyWeightedConstraintWt() {
		return mfdUncertaintyWeightedConstraintWt;
	}

	public AbstractInversionConfiguration setMagnitudeUncertaintyWeightedConstraintWt(double mfdUncertaintyWeightedConstraintWt) {
		this.mfdUncertaintyWeightedConstraintWt = mfdUncertaintyWeightedConstraintWt;
		return this;
	}

	public double getSlipRateConstraintWt_normalized() {
		return slipRateConstraintWt_normalized;
	}

	public AbstractInversionConfiguration setSlipRateConstraintWt_normalized(double slipRateConstraintWt_normalized) {
		this.slipRateConstraintWt_normalized = slipRateConstraintWt_normalized;
		return this;
	}

	public double getSlipRateConstraintWt_unnormalized() {
		return slipRateConstraintWt_unnormalized;
	}

	public AbstractInversionConfiguration setSlipRateConstraintWt_unnormalized(double slipRateConstraintWt_unnormalized) {
		this.slipRateConstraintWt_unnormalized = slipRateConstraintWt_unnormalized;
		return this;
	}

	public NZSlipRateConstraintWeightingType getSlipRateWeightingType() {
		return slipRateWeighting;
	}

	public AbstractInversionConfiguration setSlipRateWeightingType(NZSlipRateConstraintWeightingType slipRateWeighting) {
		this.slipRateWeighting = slipRateWeighting;
		return this;
	}	

	public int getSlipRateUncertaintyConstraintWt() {
		return slipRateUncertaintyConstraintWt;
	}

	public AbstractInversionConfiguration setSlipRateUncertaintyConstraintWt(int slipRateUncertaintyConstraintWt) {
		this.slipRateUncertaintyConstraintWt = slipRateUncertaintyConstraintWt;
		return this;
	}

	public int getSlipRateUncertaintyConstraintScalingFactor() {
		return slipRateUncertaintyConstraintScalingFactor;
	}	
	
	public AbstractInversionConfiguration setSlipRateUncertaintyConstraintScalingFactor(int slipRateUncertaintyConstraintScalingFactor) {
		this.slipRateUncertaintyConstraintScalingFactor = slipRateUncertaintyConstraintScalingFactor;
		return this;
	}	
	
	public double getMinimizationConstraintWt() {
		return minimizationConstraintWt;
	}

	public AbstractInversionConfiguration setMinimizationConstraintWt(double relativeMinimizationConstraintWt) {
		this.minimizationConstraintWt = relativeMinimizationConstraintWt;
		return this;
	}	

	public double[] getInitialRupModel() {
		return initialRupModel;
	}

	public AbstractInversionConfiguration setInitialRupModel(double[] initialRupModel) {
		this.initialRupModel = initialRupModel;
		return this;
	}

	public double[] getMinimumRuptureRateBasis() {
		return minimumRuptureRateBasis;
	}

	public AbstractInversionConfiguration setMinimumRuptureRateBasis(double[] minimumRuptureRateBasis) {
		this.minimumRuptureRateBasis = minimumRuptureRateBasis;
		return this;
	}

	public double getNucleationMFDConstraintWt() {
		return nucleationMFDConstraintWt;
	}

	public AbstractInversionConfiguration setNucleationMFDConstraintWt(double relativeNucleationMFDConstraintWt) {
		this.nucleationMFDConstraintWt = relativeNucleationMFDConstraintWt;
		return this;
	}	
	
	public List<IncrementalMagFreqDist> getMfdEqualityConstraints() {
		return mfdEqualityConstraints;
	}

	public AbstractInversionConfiguration setMfdEqualityConstraints(List<IncrementalMagFreqDist> mfdEqualityConstraints) {
		this.mfdEqualityConstraints = mfdEqualityConstraints;
		return this;
	}

	public List<IncrementalMagFreqDist> getMfdInequalityConstraints() {
		return mfdInequalityConstraints;
	}

	public AbstractInversionConfiguration setMfdInequalityConstraints(
			List<IncrementalMagFreqDist> mfdInequalityConstraints) {
		this.mfdInequalityConstraints = mfdInequalityConstraints;
		return this;
	}

	public List<UncertainIncrMagFreqDist> getMfdUncertaintyWeightedConstraints() {
		return mfdUncertaintyWeightedConstraints;
	}

	public AbstractInversionConfiguration setMfdUncertaintyWeightedConstraints(List<UncertainIncrMagFreqDist> mfdUncertaintyWeightedConstraints) {
		this.mfdUncertaintyWeightedConstraints = mfdUncertaintyWeightedConstraints;
		return this;
	}

	public double getMinimumRuptureRateFraction() {
		return minimumRuptureRateFraction;
	}

	public AbstractInversionConfiguration setMinimumRuptureRateFraction(double minimumRuptureRateFraction) {
		this.minimumRuptureRateFraction = minimumRuptureRateFraction;
		return this;
	}
	

	public double getMFDTransitionMag() {
		return MFDTransitionMag;
	}

	public AbstractInversionConfiguration setMFDTransitionMag(double mFDTransitionMag) {
		MFDTransitionMag = mFDTransitionMag;
		return this;
	}	
	
	/**
	 * This method returns the input MFD constraint array with each constraint now
	 * restricted between minMag and maxMag. WARNING! This doesn't interpolate. For
	 * best results, set minMag & maxMag to points along original MFD constraint
	 * (i.e. 7.05, 7.15, etc)
	 * 
	 * @param mfdConstraints
	 * @param minMag
	 * @param maxMag
	 * @return newMFDConstraints
	 */
	protected static List<IncrementalMagFreqDist> restrictMFDConstraintMagRange(
			List<IncrementalMagFreqDist> mfdConstraints, double minMag, double maxMag) {

		List<IncrementalMagFreqDist> newMFDConstraints = new ArrayList<>();

		for (int i = 0; i < mfdConstraints.size(); i++) {
			IncrementalMagFreqDist originalMFD = mfdConstraints.get(i);
			double delta = originalMFD.getDelta();
			IncrementalMagFreqDist newMFD = new IncrementalMagFreqDist(minMag, maxMag,
					(int) Math.round((maxMag - minMag) / delta + 1.0));
			newMFD.setTolerance(delta / 2.0);
			newMFD.setRegion(originalMFD.getRegion());
			for (double m = minMag; m <= maxMag; m += delta) {
				// WARNING! This doesn't interpolate. For best results, set minMag & maxMag to
				// points along original MFD constraint (i.e. 7.05, 7.15, etc)
				newMFD.set(m, originalMFD.getClosestYtoX(m));
			}
			newMFDConstraints.add(i, newMFD);
		}

		return newMFDConstraints;
	}	

	@Override
	public Element toXMLMetadata(Element root) {
		Element el = root.addElement(XML_METADATA_NAME);

		el.addAttribute("slipRateConstraintWt_normalized", getSlipRateConstraintWt_normalized() + "");
		el.addAttribute("slipRateConstraintWt_unnormalized", slipRateConstraintWt_unnormalized + "");
		el.addAttribute("slipRateWeighting", slipRateWeighting.name() + "");		
		el.addAttribute("slipRateUncertaintyConstraintWt", slipRateUncertaintyConstraintWt + "");
		el.addAttribute("slipRateUncertaintyConstraintScalingFactor", slipRateUncertaintyConstraintScalingFactor + "");
//		el.addAttribute("paleoRateConstraintWt", paleoRateConstraintWt+"");
//		el.addAttribute("paleoSlipConstraintWt", paleoSlipConstraintWt+"");
		el.addAttribute("magnitudeEqualityConstraintWt", getMagnitudeEqualityConstraintWt() + "");
		el.addAttribute("magnitudeInequalityConstraintWt", getMagnitudeInequalityConstraintWt() + "");
//		el.addAttribute("rupRateConstraintWt", rupRateConstraintWt+"");
//		el.addAttribute("participationSmoothnessConstraintWt", participationSmoothnessConstraintWt+"");
//		el.addAttribute("participationConstraintMagBinSize", participationConstraintMagBinSize+"");
//		el.addAttribute("nucleationMFDConstraintWt", nucleationMFDConstraintWt+"");
//		el.addAttribute("mfdSmoothnessConstraintWt", mfdSmoothnessConstraintWt+"");
//		el.addAttribute("mfdSmoothnessConstraintWtForPaleoParents", mfdSmoothnessConstraintWtForPaleoParents+"");
//		el.addAttribute("rupRateSmoothingConstraintWt", rupRateSmoothingConstraintWt+"");
		el.addAttribute("minimizationConstraintWt", minimizationConstraintWt+"");
//		el.addAttribute("momentConstraintWt", momentConstraintWt+"");
//		el.addAttribute("parkfieldConstraintWt", parkfieldConstraintWt+"");
//		el.addAttribute("MFDTransitionMag", MFDTransitionMag+"");
		el.addAttribute("minimumRuptureRateFraction", minimumRuptureRateFraction + "");
//		el.addAttribute("smoothnessWt", smoothnessWt+"");
//		el.addAttribute("eventRateSmoothnessWt", eventRateSmoothnessWt+"");

		// write MFDs
		Element equalMFDsEl = el.addElement("MFD_EqualityConstraints");
		mfdsToXML(equalMFDsEl, mfdEqualityConstraints);
		Element inequalMFDsEl = el.addElement("MFD_InequalityConstraints");
		mfdsToXML(inequalMFDsEl, mfdInequalityConstraints);

		return null;
	}

	private static void mfdsToXML(Element el, List<IncrementalMagFreqDist> constraints) {
		for (int i = 0; i < constraints.size(); i++) {
			IncrementalMagFreqDist constr = constraints.get(i);
			constr.toXMLMetadata(el);
		}
		// now set indexes
		List<Element> subEls = XMLUtils.getSubElementsList(el);
		for (int i = 0; i < subEls.size(); i++)
			subEls.get(i).addAttribute("index", i + "");
	}	
	
}
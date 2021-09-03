package nz.cri.gns.NZSHM22.opensha.inversion;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.commons.util.modules.ArchivableModule;
import org.opensha.commons.util.modules.SubModule;
import org.opensha.commons.util.modules.helpers.FileBackedModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.MFDEqualityInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.MFDInequalityInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.modules.SubSeismoOnFaultMFDs;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonWriter;

import nz.cri.gns.NZSHM22.opensha.analysis.NZSHM22_FaultSystemRupSetCalc;
import nz.cri.gns.NZSHM22.opensha.data.region.NewZealandRegions;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_SpatialSeisPDF;
import nz.cri.gns.NZSHM22.opensha.ruptures.NZSHM22_SlipEnabledRuptureSet;
import scratch.UCERF3.SlipEnabledRupSet;
import scratch.UCERF3.analysis.DeformationModelsCalc;

import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.InversionModels;
import scratch.UCERF3.enumTreeBranches.MaxMagOffFault;
import scratch.UCERF3.enumTreeBranches.MomentRateFixes;
import scratch.UCERF3.enumTreeBranches.SpatialSeisPDF;
import scratch.UCERF3.enumTreeBranches.TotalMag5Rate;
import scratch.UCERF3.griddedSeismicity.GriddedSeisUtils;
import scratch.UCERF3.inversion.InversionFaultSystemRupSet;
import scratch.UCERF3.inversion.InversionTargetMFDs;
import scratch.UCERF3.inversion.U3InversionTargetMFDs;
import scratch.UCERF3.inversion.InversionTargetMFDs.Precomputed;
import scratch.UCERF3.logicTree.U3LogicTreeBranch;
import scratch.UCERF3.utils.MFD_InversionConstraint;
import scratch.UCERF3.utils.MFD_WeightedInversionConstraint;
import scratch.UCERF3.utils.RELM_RegionUtils;

/**
 * This class constructs and stores the various pre-inversion MFD Targets.
 * 
 * Details on what's returned are:
 * 
 * getTotalTargetGR() returns:
 * 
 * The total regional target GR (Same for both GR and Char branches)
 * 
 * getTotalGriddedSeisMFD() returns:IncrementalMagFreqDist
 * 
 * getTrulyOffFaultMFD()+getTotalSubSeismoOnFaultMFD()
 * 
 * getTotalOnFaultMFD() returns:
 * 
 * getTotalSubSeismoOnFaultMFD() + getOnFaultSupraSeisMFD();
 * 
 * TODO: this contains mostly UCERF3 stuff that will be replaced for NSHM
 * 
 * @author chrisbc
 *
 */
public class NZSHM22_SubductionInversionTargetMFDs extends NZSHM22_InversionTargetMFDs{

	static boolean MFD_STATS = true; //print some curves for analytics
	
//	// discretization parameters for MFDs
	public final static double MIN_MAG = 5.05; // 
	public final static double MAX_MAG = 9.75;
	public final static int NUM_MAG = (int) ((MAX_MAG - MIN_MAG) * 10.0d);
	public final static double DELTA_MAG = 0.1;

	// CBC NEW
	public final static double MINIMIZE_RATE_BELOW_MAG = 7.05;
	public final static double MINIMIZE_RATE_TARGET = 1.0e-20d;
	
	protected List<MFD_InversionConstraint> mfdEqIneqConstraints  = new ArrayList<>();
	protected List<MFD_WeightedInversionConstraint> mfdUncertaintyConstraints = new ArrayList<>();;

	protected List<IncrementalMagFreqDist> mfdConstraintComponents;
	
	public  NZSHM22_SubductionInversionTargetMFDs (NZSHM22_InversionFaultSystemRuptSet invRupSet){
		this(invRupSet, 0.7, 1.1, 7.85, 0, 0);
	}

	public NZSHM22_SubductionInversionTargetMFDs(NZSHM22_InversionFaultSystemRuptSet invRupSet,
									  double totalRateM5, double bValue, double mfdTransitionMag,
									  double mfdUncertaintyWeightedConstraintWt, double mfdUncertaintyWeightedConstraintPower){
		
		// TODO: we're getting a UCERF3 LTB now, this needs to be replaced with NSHM
		// equivalent
		U3LogicTreeBranch logicTreeBranch = invRupSet.getLogicTreeBranch();
		InversionModels inversionModel = logicTreeBranch.getValue(InversionModels.class);
		//this.totalRegionRateMgt5 = this.totalRateM5;
		double mMaxOffFault = logicTreeBranch.getValue(MaxMagOffFault.class).getMaxMagOffFault(); //TODO: set this to 8.05 (more NZ ish)

		// convert mMaxOffFault to bin center
		List<? extends FaultSection> faultSectionData = invRupSet.getFaultSectionDataList();
		
		double origOnFltDefModMoRate = DeformationModelsCalc.calculateTotalMomentRate(faultSectionData,true);
		
		// make the total target GR MFD
		// TODO: why MIN_MAG = 0 ??
		GutenbergRichterMagFreqDist totalTargetGR = new GutenbergRichterMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);
		if (MFD_STATS) {
			System.out.println("totalTargetGR");
			System.out.println(totalTargetGR.toString());
			System.out.println("");	
		}
		
		// sorting out scaling
		double roundedMmaxOnFault = totalTargetGR.getX(totalTargetGR.getClosestXIndex(invRupSet.getMaxMag()));
		totalTargetGR.setAllButTotMoRate(MIN_MAG, roundedMmaxOnFault, totalRateM5, bValue); //TODO: revisit
		
		if (MFD_STATS) {
			System.out.println("totalTargetGR after setAllButTotMoRate");
			System.out.println(totalTargetGR.toString());
			System.out.println("");		
		}
		
		//Doctor the target, setting a small value instead of 0
	 	totalTargetGR.setYofX((x, y) -> {return (x < MINIMIZE_RATE_BELOW_MAG) ? MINIMIZE_RATE_TARGET : y;});
		
		SummedMagFreqDist targetOnFaultSupraSeisMFD = new SummedMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);
		targetOnFaultSupraSeisMFD.addIncrementalMagFreqDist(totalTargetGR);

		if (MFD_STATS) {
			System.out.println("targetOnFaultSupraSeisMFD (SummedMagFreqDist)");
			System.out.println(targetOnFaultSupraSeisMFD.toString());
			System.out.println("");		
		}		

//		// compute coupling coefficients
//		impliedOnFaultCouplingCoeff = (targetOnFaultSupraSeisMFD.getTotalMomentRate()
//				+ totalSubSeismoOnFaultMFD.getTotalMomentRate()) / origOnFltDefModMoRate;
//		impliedTotalCouplingCoeff = totalTargetGR.getTotalMomentRate() / (origOnFltDefModMoRate + offFltDefModMoRate);

		// Build the MFD Constraints for regions
//		List<MFD_InversionConstraint> mfdUncertaintyConstraints = new ArrayList<>();

		if (mfdUncertaintyWeightedConstraintWt > 0.0) {
			EvenlyDiscretizedFunc weight = new EvenlyDiscretizedFunc(MIN_MAG, NUM_MAG, DELTA_MAG);
			double firstWeightPower = Math.pow(targetOnFaultSupraSeisMFD.getClosestYtoX(MINIMIZE_RATE_BELOW_MAG), mfdUncertaintyWeightedConstraintPower);
			weight.setYofX(mag -> {
				if (mag < MINIMIZE_RATE_BELOW_MAG) return 1.0;
				double rate = targetOnFaultSupraSeisMFD.getClosestYtoX(mag);
				return Math.pow(rate, mfdUncertaintyWeightedConstraintPower)/firstWeightPower;
			});
			mfdUncertaintyConstraints.add(new MFD_WeightedInversionConstraint(targetOnFaultSupraSeisMFD, null, weight));
		} 
		
		// original for Eq/InEq constraints
//			List<MFD_InversionConstraint> mfdEqIneqConstraints = new ArrayList<>();
		mfdEqIneqConstraints.add(new MFD_InversionConstraint(targetOnFaultSupraSeisMFD, null));	
		
		// Now collect the target MFDS we might want for plots
		targetOnFaultSupraSeisMFD.setName("targetOnFaultSupraSeisMFD");
		List<IncrementalMagFreqDist> mfdConstraintComponents = new ArrayList<>();
		mfdConstraintComponents.add(targetOnFaultSupraSeisMFD);

		setParent(invRupSet);
		this.totalTargetGR = totalTargetGR;
		this.targetOnFaultSupraSeisMFD = targetOnFaultSupraSeisMFD;
//		this.mfdConstraints = mfdConstraints;
		this.mfdConstraintComponents = mfdConstraintComponents;


//		return new InversionTargetMFDs.Precomputed( invRupSet,
//				totalTargetGR, targetOnFaultSupraSeisMFD, null,
//				null, mfdConstraints, null);

	}

	public List<MFD_InversionConstraint> getMfdEqIneqConstraints() {
		return mfdEqIneqConstraints;
	}

	public List<MFD_WeightedInversionConstraint> getMfdUncertaintyConstraints() {
		return mfdUncertaintyConstraints;
	}

	@Override
	public List<MFD_InversionConstraint> getMFD_Constraints(){
		List<MFD_InversionConstraint> mfdConstraints = new ArrayList<MFD_InversionConstraint>();
		mfdConstraints.addAll(getMfdEqIneqConstraints());
		mfdConstraints.addAll(getMfdUncertaintyConstraints());
		return mfdConstraints;
	}

	public List<IncrementalMagFreqDist> getMFDConstraintComponents(){
		return mfdConstraintComponents;
	}

	@Override
	public String getName() {
		return "NZSHM22 Subduction Inversion Target MFDs";
	}

	@Override
	public void writeToArchive(ZipOutputStream zout, String entryPrefix) throws IOException {
		new SubductionPrecomputed(this).writeToArchive(zout, entryPrefix);
	}	
	
	// only used for plots
	@Override
	public GutenbergRichterMagFreqDist getTotalTargetGR_NoCal() {throw new UnsupportedOperationException();}

	// only used for plots
	@Override
	public GutenbergRichterMagFreqDist getTotalTargetGR_SoCal() {throw new UnsupportedOperationException();}

	@Override
	public double[] getPDF(){
		return spatialSeisPDF.getPDF();
	}

	// TODO CBC decide if this pattern is OK, could also be inheriting from InversionTargetMFDs.Precomputed ??
	public static class SubductionPrecomputed extends NZSHM22_SubductionInversionTargetMFDs implements ArchivableModule {
		private static TypeAdapter<IncrementalMagFreqDist> mfdAdapter = new IncrementalMagFreqDist.Adapter();
		private static TypeAdapter<MFD_InversionConstraint> mfdConstraintAdapter = new MFD_InversionConstraint.Adapter();
		private static TypeAdapter<MFD_InversionConstraint> mfdWeightedConstraintAdapter = new MFD_WeightedInversionConstraint.Adapter();
		
		private IncrementalMagFreqDist totalRegionalMFD;
		private IncrementalMagFreqDist onFaultSupraSeisMFD;
		private ImmutableList<MFD_InversionConstraint> mfdConstraints;
			
		public SubductionPrecomputed(NZSHM22_SubductionInversionTargetMFDs targetMFDs) {
			this((NZSHM22_InversionFaultSystemRuptSet) targetMFDs.getParent(),
					targetMFDs.getTotalRegionalMFD(),
					targetMFDs.getTotalOnFaultSupraSeisMFD(),
					(List<MFD_InversionConstraint>) targetMFDs.getMFD_Constraints());
		}

		public SubductionPrecomputed(NZSHM22_InversionFaultSystemRuptSet rupSet, IncrementalMagFreqDist totalRegionalMFD,
				IncrementalMagFreqDist onFaultSupraSeisMFD, List<MFD_InversionConstraint> mfdConstraints) {
			super(rupSet);
			this.totalRegionalMFD = totalRegionalMFD;
			this.onFaultSupraSeisMFD = onFaultSupraSeisMFD;
			this.mfdConstraints = mfdConstraints == null ? null : ImmutableList.copyOf(mfdConstraints);
		}

		@Override
		public String getName() {
			// TODO Auto-generated method stub
			return "NZSHM Subduction Inversion Target MFDs";
		}

		@Override
		public SubModule<FaultSystemRupSet> copy(FaultSystemRupSet newParent) throws IllegalStateException {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public void writeToArchive(ZipOutputStream zout, String entryPrefix) throws IOException {
			FileBackedModule.initEntry(zout, entryPrefix, "inversion_target_mfds.json");
			BufferedOutputStream out = new BufferedOutputStream(zout);
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			OutputStreamWriter writer = new OutputStreamWriter(out);
			writeToJSON(gson.newJsonWriter(writer));
			writer.flush();
			out.flush();
			zout.closeEntry();
		}

		private void writeToJSON(JsonWriter out) throws IOException {
			out.beginObject();
			
			out.name("totalRegionalMFD");
			mfdAdapter.write(out, totalRegionalMFD);
			
			out.name("onFaultSupraSeisMFD");
			mfdAdapter.write(out, onFaultSupraSeisMFD);
			
			out.name("mfdConstraints");
			if (mfdConstraints == null) {
				out.nullValue();
			} else {
				out.beginArray();
				for (MFD_InversionConstraint constraint : mfdConstraints)
					if (constraint.getClass().getSimpleName().contentEquals("MFD_WeightedInversionConstraint")) {
						mfdWeightedConstraintAdapter.write(out, (MFD_WeightedInversionConstraint) constraint);
					} else {
						mfdConstraintAdapter.write(out, constraint);
					}
				out.endArray();
			}
			
			out.endObject();
		}		
		
		@Override
		public void initFromArchive(ZipFile zip, String entryPrefix) throws IOException {
			// TODO Auto-generated method stub
			
		}

		@Override
		public IncrementalMagFreqDist getTotalRegionalMFD() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public IncrementalMagFreqDist getTotalOnFaultSupraSeisMFD() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public List<MFD_InversionConstraint> getMFD_Constraints() {
			// TODO Auto-generated method stub
			return mfdConstraints;
		}

		@Override
		public SubSeismoOnFaultMFDs getOnFaultSubSeisMFDs() {
			// TODO Auto-generated method stub
			return null;
		}		
	}

}

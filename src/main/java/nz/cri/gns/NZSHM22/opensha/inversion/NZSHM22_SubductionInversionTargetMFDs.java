package nz.cri.gns.NZSHM22.opensha.inversion;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.uncertainty.UncertainIncrMagFreqDist;
import org.opensha.commons.util.modules.ArchivableModule;
import org.opensha.commons.util.modules.SubModule;
import org.opensha.commons.util.modules.helpers.FileBackedModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.SubSeismoOnFaultMFDs;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonWriter;

import scratch.UCERF3.analysis.DeformationModelsCalc;

import scratch.UCERF3.enumTreeBranches.InversionModels;
import scratch.UCERF3.enumTreeBranches.MaxMagOffFault;
import scratch.UCERF3.inversion.U3InversionTargetMFDs;
import scratch.UCERF3.logicTree.U3LogicTreeBranch;
import scratch.UCERF3.utils.MFD_InversionConstraint;
import scratch.UCERF3.utils.MFD_WeightedInversionConstraint;

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
public class NZSHM22_SubductionInversionTargetMFDs extends U3InversionTargetMFDs{

	static boolean MFD_STATS = true; //print some curves for analytics
	
//	// discretization parameters for MFDs
	public final static double MIN_MAG = 5.05; // 
	public final static double MAX_MAG = 9.75;
	public final static int NUM_MAG = (int) ((MAX_MAG - MIN_MAG) * 10.0d);
	public final static double DELTA_MAG = 0.1;

	// CBC NEW
	public final static double MINIMIZE_RATE_BELOW_MAG = 7.05;
	public final static double MINIMIZE_RATE_TARGET = 1.0e-20d;
	
	protected List<IncrementalMagFreqDist> mfdEqIneqConstraints  = new ArrayList<>();
	protected List<UncertainIncrMagFreqDist> mfdUncertaintyConstraints = new ArrayList<>();;

	protected List<IncrementalMagFreqDist> mfdConstraintComponents;
	
	public  NZSHM22_SubductionInversionTargetMFDs (NZSHM22_InversionFaultSystemRuptSet invRupSet){
		this(invRupSet, 0.7, 1.1, 0, 0);
	}

	public NZSHM22_SubductionInversionTargetMFDs(NZSHM22_InversionFaultSystemRuptSet invRupSet,
									  double totalRateM5, double bValue,
									  double mfdUncertaintyWeightedConstraintWt, double mfdUncertaintyWeightedConstraintPower){
		
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
			mfdUncertaintyConstraints.add(new UncertainIncrMagFreqDist(targetOnFaultSupraSeisMFD, weight));
		} 
		
		// original for Eq/InEq constraints
//			List<MFD_InversionConstraint> mfdEqIneqConstraints = new ArrayList<>();
		mfdEqIneqConstraints.add(targetOnFaultSupraSeisMFD);
		
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

	public List<IncrementalMagFreqDist> getMfdEqIneqConstraints() {
		return mfdEqIneqConstraints;
	}

	public List<UncertainIncrMagFreqDist> getMfdUncertaintyConstraints() {
		return mfdUncertaintyConstraints;
	}

	@Override
	public List<IncrementalMagFreqDist> getMFD_Constraints(){
		List<IncrementalMagFreqDist> mfdConstraints = new ArrayList<>();
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

	// TODO CBC decide if this pattern is OK, could also be inheriting from InversionTargetMFDs.Precomputed ??
	public static class SubductionPrecomputed extends NZSHM22_SubductionInversionTargetMFDs implements ArchivableModule {
		private static TypeAdapter<IncrementalMagFreqDist> mfdAdapter = new IncrementalMagFreqDist.Adapter();
		private static TypeAdapter<UncertainIncrMagFreqDist> mfdWeightedConstraintAdapter = new UncertainIncrMagFreqDist.Adapter();
		
		private IncrementalMagFreqDist totalRegionalMFD;
		private IncrementalMagFreqDist onFaultSupraSeisMFD;
		private ImmutableList<IncrementalMagFreqDist> mfdConstraints;
			
		public SubductionPrecomputed(NZSHM22_SubductionInversionTargetMFDs targetMFDs) {
			this((NZSHM22_InversionFaultSystemRuptSet) targetMFDs.getParent(),
					targetMFDs.getTotalRegionalMFD(),
					targetMFDs.getTotalOnFaultSupraSeisMFD(),
					(List<IncrementalMagFreqDist>) targetMFDs.getMFD_Constraints());
		}

		public SubductionPrecomputed(NZSHM22_InversionFaultSystemRuptSet rupSet, IncrementalMagFreqDist totalRegionalMFD,
				IncrementalMagFreqDist onFaultSupraSeisMFD, List<IncrementalMagFreqDist> mfdConstraints) {
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
				for (IncrementalMagFreqDist constraint : mfdConstraints)
					if (constraint.getClass().getSimpleName().contentEquals("UncertainIncrMagFreqDist")) {
						mfdWeightedConstraintAdapter.write(out, (UncertainIncrMagFreqDist) constraint);
					} else {
						mfdAdapter.write(out, constraint);
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
		public List<IncrementalMagFreqDist> getMFD_Constraints() {
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

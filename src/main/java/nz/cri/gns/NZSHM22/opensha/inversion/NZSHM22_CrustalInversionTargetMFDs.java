package nz.cri.gns.NZSHM22.opensha.inversion;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.awt.geom.Point2D;
import java.util.zip.ZipOutputStream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonWriter;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_LogicTreeBranch;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.util.modules.helpers.FileBackedModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.PolygonFaultGridAssociations;
import org.opensha.sha.earthquake.faultSysSolution.modules.SubSeismoOnFaultMFDs;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;

import nz.cri.gns.NZSHM22.opensha.analysis.NZSHM22_FaultSystemRupSetCalc;
import nz.cri.gns.NZSHM22.opensha.data.region.NewZealandRegions;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_SpatialSeisPDF;
import scratch.UCERF3.griddedSeismicity.GriddedSeisUtils;
import scratch.UCERF3.inversion.U3InversionTargetMFDs;
import scratch.UCERF3.utils.MFD_InversionConstraint;

/**
 * This class constructs and stores the various pre-inversion MFD Targets.
 *
 * Details on what's returned are:
 * 
 * getTotalTargetGR() returns:
 * 
 * The total regional target GR (Same for both GR and Char branches)
 * 
 * getTotalGriddedSeisMFD() returns:
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
public class NZSHM22_CrustalInversionTargetMFDs extends U3InversionTargetMFDs {

	static boolean MFD_STATS = true; //print some curves for analytics

	public final static double NZ_MIN_MAG = 5.05; //used instead of UCERF3 value 0.05
	public final static int NZ_NUM_BINS = 40;  //used instead of UCERF3 value 90

	RegionalTargetMFDs sansTvz;
	RegionalTargetMFDs tvz;

	protected List<MFD_InversionConstraint> mfdConstraints;

	/**
	 * For NZ reporting only
	 *
	 * @return
	 */
	public List<IncrementalMagFreqDist> getReportingMFDConstraintComponents() {
		List<IncrementalMagFreqDist> mfdConstraintComponents = new ArrayList<>();
		mfdConstraintComponents.add(trulyOffFaultMFD);
		mfdConstraintComponents.add(sansTvz.targetOnFaultSupraSeisMFDs);
		mfdConstraintComponents.add(tvz.targetOnFaultSupraSeisMFDs);
		mfdConstraintComponents.add(totalSubSeismoOnFaultMFD);
		return mfdConstraintComponents;
	}

	@Override
    public List<MFD_InversionConstraint> getMFD_Constraints() {
    	return mfdConstraints;
    }

	public NZSHM22_CrustalInversionTargetMFDs(NZSHM22_InversionFaultSystemRuptSet invRupSet,double totalRateM5_Sans,
											  double totalRateM5_TVZ, double bValue_Sans, double bValue_TVZ) {
		init(invRupSet, totalRateM5_Sans, totalRateM5_TVZ, bValue_Sans, bValue_TVZ);
	}

	public static class RegionalTargetMFDs {
		GriddedRegion region;
		String suffix;

		double totalRateM5;
		double bValue;

		GutenbergRichterMagFreqDist totalTargetGR;
		IncrementalMagFreqDist trulyOffFaultMFD;
		SummedMagFreqDist totalSubSeismoOnFaultMFD;
		IncrementalMagFreqDist targetOnFaultSupraSeisMFDs;
		List<GutenbergRichterMagFreqDist> subSeismoOnFaultMFD_List;

		private static final TypeAdapter<IncrementalMagFreqDist> mfdAdapter = new IncrementalMagFreqDist.Adapter();

		public RegionalTargetMFDs(NZSHM22_InversionFaultSystemRuptSet invRupSet, GriddedRegion region, double totalRateM5, double bValue) {
			this.region = region;
			this.totalRateM5 = totalRateM5;
			this.bValue = bValue;
			if (region.getName().contains("SANS TVZ")) {
				suffix = "SansTVZ";
			} else if (region.getName().contains("TVZ")) {
				suffix = "TVZ";
			} else {
				suffix = "";
			}
			init(invRupSet);
		}

		void writeToJson(JsonWriter out) throws IOException {
			out.beginObject();

			out.name("region");
			out.value(region.getName());

			out.name("totalRateM5");
			out.value(totalRateM5);

			out.name("bValue");
			out.value(bValue);

			out.name("totalTargetGR");
			mfdAdapter.write(out, totalTargetGR);

			out.name("trulyOffFaultMFD");
			mfdAdapter.write(out, trulyOffFaultMFD);

			out.name("totalSubSeismoOnFaultMFD");
			mfdAdapter.write(out, totalSubSeismoOnFaultMFD);

			out.name("targetOnFaultSupraSeisMFD");
			mfdAdapter.write(out, targetOnFaultSupraSeisMFDs);

			out.endObject();
		}

		public static IncrementalMagFreqDist fillBelowMag(IncrementalMagFreqDist source, double minMag, double value) {
			IncrementalMagFreqDist result = new IncrementalMagFreqDist(source.getMinX(), source.size(), source.getDelta());
			for (int i = 0; i < source.size(); i++) {
				Point2D point = source.get(i);
				if (point.getX() < minMag) {
					result.set(i, value);
				} else {
					result.set(i, point.getY());
				}
			}
			return result;
		}

		protected void init(NZSHM22_InversionFaultSystemRuptSet invRupSet) {

			double mMaxOffFault = 8.05d; // NZ-ish
			NZSHM22_SpatialSeisPDF spatialSeisPDF = invRupSet.getModule(NZSHM22_LogicTreeBranch.class).getValue(NZSHM22_SpatialSeisPDF.class);

			// convert mMaxOffFault to bin center
			mMaxOffFault -= DELTA_MAG / 2;  // TODO is 8.05 already a bin centre?

			List<? extends FaultSection> faultSectionData = invRupSet.getFaultSectionDataList();

			GriddedSeisUtils gridSeisUtils = new GriddedSeisUtils(faultSectionData,
					spatialSeisPDF.getPDF(), invRupSet.requireModule(PolygonFaultGridAssociations.class)); // TODO: OAKLEY: check this is ours already
			double fractionSeisOnFault = gridSeisUtils.pdfInPolys();        //TODO: split this for TVZ? Matt says yes

			double onFaultRegionRateMgt5 = totalRateM5 * fractionSeisOnFault;

			// make the total target GR MFD with empty bins
			totalTargetGR = new GutenbergRichterMagFreqDist(NZ_MIN_MAG, NZ_NUM_BINS, DELTA_MAG);

			// populate the MFD bins
			double roundedMmaxOnFault = totalTargetGR.getX(totalTargetGR.getClosestXIndex(invRupSet.getMaxMag()));
			totalTargetGR.setAllButTotMoRate(NZ_MIN_MAG, roundedMmaxOnFault, totalRateM5, bValue);

			// get ave min seismo mag for region
			// TODO: this is weighted by moment, so exponentially biased to larger ruptures (WHY?)
			// Kevin weighted by moment (which comes from slip rate) so higher momentrate faults WILL predominate
			// NZ many tiny faults will not really contribute much
			double tempMag = NZSHM22_FaultSystemRupSetCalc.getMeanMinMag(invRupSet, true);

			//TODO: why derive this from the rupt set and not use mMaxOffFault??
			double aveMinSeismoMag = totalTargetGR.getX(totalTargetGR.getClosestXIndex(tempMag));    // round to nearest MFD value

			//TODO: why aveMinSeismoMag (Ned??)
			// seems to calculate our corner magnitude for tapered GR
			trulyOffFaultMFD = NZSHM22_FaultSystemRupSetCalc.getTriLinearCharOffFaultTargetMFD(totalTargetGR, onFaultRegionRateMgt5, aveMinSeismoMag, mMaxOffFault);

			subSeismoOnFaultMFD_List = NZSHM22_FaultSystemRupSetCalc.getCharSubSeismoOnFaultMFD_forEachSection(invRupSet, gridSeisUtils, totalTargetGR);

			// TODO: use computeMinSeismoMagForSections to find NZ values and explain 7.4
			// histogram to look for min values > 7.X
			totalSubSeismoOnFaultMFD = new SummedMagFreqDist(NZ_MIN_MAG, NZ_NUM_BINS, DELTA_MAG);
			for (GutenbergRichterMagFreqDist mfd : subSeismoOnFaultMFD_List) {
				totalSubSeismoOnFaultMFD.addIncrementalMagFreqDist(mfd);
			}

			SummedMagFreqDist tempTargetOnFaultSupraSeisMFD = new SummedMagFreqDist(NZ_MIN_MAG, NZ_NUM_BINS, DELTA_MAG);
			tempTargetOnFaultSupraSeisMFD.addIncrementalMagFreqDist(totalTargetGR);
			tempTargetOnFaultSupraSeisMFD.subtractIncrementalMagFreqDist(trulyOffFaultMFD);
			tempTargetOnFaultSupraSeisMFD.subtractIncrementalMagFreqDist(totalSubSeismoOnFaultMFD);

			targetOnFaultSupraSeisMFDs = fillBelowMag(tempTargetOnFaultSupraSeisMFD, 7,  1.0e-20);

			if (MFD_STATS) {
				System.out.println("totalTargetGR_" + suffix + " after setAllButTotMoRate");
				System.out.println(totalTargetGR.toString());
				System.out.println("");

				System.out.println("trulyOffFaultMFD_" + suffix + " (TriLinearCharOffFaultTargetMFD)");
				System.out.println(trulyOffFaultMFD.toString());
				System.out.println("");

				System.out.println("totalSubSeismoOnFaultMFD_" + suffix + " (SummedMagFreqDist)");
				System.out.println(totalSubSeismoOnFaultMFD.toString());
				System.out.println("");

				System.out.println("targetOnFaultSupraSeisMFD_" + suffix + " (SummedMagFreqDist)");
				System.out.println(targetOnFaultSupraSeisMFDs.toString());
				System.out.println("");
			}

			//TODO are these purely analysis?? for now they're off
//		// compute coupling coefficients
//		impliedOnFaultCouplingCoeff = (targetOnFaultSupraSeisMFD.getTotalMomentRate()
//				+ totalSubSeismoOnFaultMFD.getTotalMomentRate()) / origOnFltDefModMoRate;
//		finalOffFaultCouplingCoeff = trulyOffFaultMFD.getTotalMomentRate() / offFltDefModMoRate;
//		impliedTotalCouplingCoeff = totalTargetGR.getTotalMomentRate() / (origOnFltDefModMoRate + offFltDefModMoRate);

			// set the names
			totalTargetGR.setName("InversionTargetMFDs.totalTargetGR_" + suffix);
			targetOnFaultSupraSeisMFDs.setName("InversionTargetMFDs.targetOnFaultSupraSeisMFD_" + suffix);
			trulyOffFaultMFD.setName("InversionTargetMFDs.trulyOffFaultMFD_" + suffix + ".");
			totalSubSeismoOnFaultMFD.setName("InversionTargetMFDs.totalSubSeismoOnFaultMFD_" + suffix + ".");
		}
	}

	protected void init(NZSHM22_InversionFaultSystemRuptSet invRupSet,
						double totalRateM5_SansTVZ,
						double totalRateM5_TVZ,
						double bValue_SansTVZ,
						double bValue_TVZ) {

		setParent(invRupSet);

		sansTvz = new RegionalTargetMFDs(invRupSet, new NewZealandRegions.NZ_RECTANGLE_SANS_TVZ_GRIDDED(), totalRateM5_SansTVZ, bValue_SansTVZ);
		tvz = new RegionalTargetMFDs(invRupSet, new NewZealandRegions.NZ_TVZ_GRIDDED(), totalRateM5_TVZ, bValue_TVZ);

		// Build the MFD Constraints for regions
		mfdConstraints = new ArrayList<>();
		mfdConstraints.add(new MFD_InversionConstraint(sansTvz.targetOnFaultSupraSeisMFDs, sansTvz.region));
		mfdConstraints.add(new MFD_InversionConstraint(tvz.targetOnFaultSupraSeisMFDs, tvz.region));

		/*
		 * TODO CBC the following block sets up base class var required later to save the solution,
		 * namely:
		 *  - totalTargetGR
		 *  - trulyOffFaultMFD
		 *  - totalSubSeismoOnFaultMFD
		 */
		SummedMagFreqDist tempTargetGR = new SummedMagFreqDist(NZ_MIN_MAG, NZ_NUM_BINS, DELTA_MAG);
		tempTargetGR.addIncrementalMagFreqDist(sansTvz.totalTargetGR);
		tempTargetGR.addIncrementalMagFreqDist(tvz.totalTargetGR);

		totalTargetGR = new GutenbergRichterMagFreqDist(NZ_MIN_MAG, NZ_NUM_BINS, DELTA_MAG);
		for(Point2D p : tempTargetGR){
			totalTargetGR.set(p);
		}

		SummedMagFreqDist tempTrulyOffFaultMFD = new SummedMagFreqDist(NZ_MIN_MAG, NZ_NUM_BINS, DELTA_MAG);
		tempTrulyOffFaultMFD.addIncrementalMagFreqDist(sansTvz.trulyOffFaultMFD);
		tempTrulyOffFaultMFD.addIncrementalMagFreqDist(tvz.trulyOffFaultMFD);

		trulyOffFaultMFD = new IncrementalMagFreqDist(NZ_MIN_MAG, NZ_NUM_BINS, DELTA_MAG);
		for(Point2D p : tempTrulyOffFaultMFD){
			trulyOffFaultMFD.set(p);
		}

		//TODO: review this (if really needed) should add the SansTVZ and TVZ
		//CHECK: New MFD addition approach....
		totalSubSeismoOnFaultMFD = new SummedMagFreqDist(NZ_MIN_MAG, NZ_NUM_BINS, DELTA_MAG);
		totalSubSeismoOnFaultMFD.addIncrementalMagFreqDist(sansTvz.totalSubSeismoOnFaultMFD);
		totalSubSeismoOnFaultMFD.addIncrementalMagFreqDist(tvz.totalSubSeismoOnFaultMFD);

		// TODO is this correct? It's just a guess by Oakley (and now Chris)
		ArrayList<GutenbergRichterMagFreqDist> subSeismoOnFaultMFD_List = new ArrayList<>();
		subSeismoOnFaultMFD_List.addAll(sansTvz.subSeismoOnFaultMFD_List);
		subSeismoOnFaultMFD_List.addAll(tvz.subSeismoOnFaultMFD_List);
		subSeismoOnFaultMFDs = new SubSeismoOnFaultMFDs(subSeismoOnFaultMFD_List);

		trulyOffFaultMFD.setName("trulyOffFaultMFD.all");
		totalSubSeismoOnFaultMFD.setName("totalSubSeismoOnFaultMFD");

		if (MFD_STATS) {

			System.out.println("trulyOffFaultMFD");
			System.out.println(trulyOffFaultMFD.toString());
			System.out.println("");

			System.out.println("totalSubSeismoOnFaultMFD");
			System.out.println(totalSubSeismoOnFaultMFD.toString());
			System.out.println("");

			System.out.println("totalTargetGR");
			System.out.println(totalTargetGR.toString());
			System.out.println("");

			System.out.println("totalSubSeismoOnFaultMFD");
			System.out.println(totalSubSeismoOnFaultMFD.toString());
			System.out.println("");

		}
	}
	
	@Override
	public GutenbergRichterMagFreqDist getTotalTargetGR_NoCal() {
		throw new UnsupportedOperationException();
	}

	@Override
	public GutenbergRichterMagFreqDist getTotalTargetGR_SoCal() {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getName() {
		return "NZSHM22 Crustal Inversion Target MFDs";
	}

	@Override
	public void writeToArchive(ZipOutputStream zout, String entryPrefix) throws IOException {
    	super.writeToArchive(zout, entryPrefix);

		FileBackedModule.initEntry(zout, entryPrefix, "regional_inversion_target_mfds.json");
		BufferedOutputStream out = new BufferedOutputStream(zout);
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		OutputStreamWriter writer = new OutputStreamWriter(out);
		JsonWriter json = gson.newJsonWriter(writer);

		json.beginObject();
		json.name("sansTVZ");
		sansTvz.writeToJson(json);
		json.name("TVZ");
		tvz.writeToJson(json);
		json.endObject();

		writer.flush();
		out.flush();
		zout.closeEntry();

	}

}

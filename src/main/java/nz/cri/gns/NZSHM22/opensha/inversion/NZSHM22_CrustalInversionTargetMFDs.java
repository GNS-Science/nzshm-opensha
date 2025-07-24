package nz.cri.gns.NZSHM22.opensha.inversion;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonWriter;
import java.awt.geom.Point2D;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import nz.cri.gns.NZSHM22.opensha.analysis.NZSHM22_FaultSystemRupSetCalc;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_LogicTreeBranch;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_Regions;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_SpatialSeisPDF;
import org.opensha.commons.data.uncertainty.UncertainIncrMagFreqDist;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.util.io.archive.ArchiveOutput;
import org.opensha.commons.util.modules.helpers.FileBackedModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.SubSeismoOnFaultMFDs;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;
import scratch.UCERF3.griddedSeismicity.GriddedSeisUtils;
import scratch.UCERF3.inversion.U3InversionTargetMFDs;

/**
 * This class constructs and stores the various pre-inversion MFD Targets.
 *
 * <p>Details on what's returned are:
 *
 * <p>getTotalTargetGR() returns:
 *
 * <p>The total regional target GR (Same for both GR and Char branches)
 *
 * <p>getTotalGriddedSeisMFD() returns:
 *
 * <p>getTrulyOffFaultMFD()+getTotalSubSeismoOnFaultMFD()
 *
 * <p>getTotalOnFaultMFD() returns:
 *
 * <p>getTotalSubSeismoOnFaultMFD() + getOnFaultSupraSeisMFD();
 *
 * <p>TODO: this contains mostly UCERF3 stuff that will be replaced for NSHM
 *
 * @author chrisbc
 */
public class NZSHM22_CrustalInversionTargetMFDs extends U3InversionTargetMFDs {

    static boolean MFD_STATS = true; // print some curves for analytics

    public static final double NZ_MIN_MAG = 5.05; // used instead of UCERF3 value 0.05
    public static final int NZ_NUM_BINS = 40; // used instead of UCERF3 value 90

    RegionalTargetMFDs sansTvz;
    RegionalTargetMFDs tvz;

    protected List<IncrementalMagFreqDist> mfdConstraints;
    protected List<UncertainIncrMagFreqDist> mfdUncertaintyConstraints;

    protected List<IncrementalMagFreqDist> reportingMFDConstraintComponents;
    protected List<IncrementalMagFreqDist> reportingMFDConstraintComponentsV2;

    /**
     * For NZ reporting only
     *
     * @return
     */
    public List<IncrementalMagFreqDist> getReportingMFDConstraintComponents() {
        return reportingMFDConstraintComponents;
    }

    /**
     * For NZ reporting. Version 2, with all the regional and total MFDs.
     *
     * @return
     */
    public List<IncrementalMagFreqDist> getReportingMFDConstraintComponentsV2() {
        return reportingMFDConstraintComponentsV2;
    }

    @Override
    public List<IncrementalMagFreqDist> getMFD_Constraints() {
        return mfdConstraints;
    }

    public List<UncertainIncrMagFreqDist> getMfdUncertaintyConstraints() {
        return mfdUncertaintyConstraints;
    }

    public NZSHM22_CrustalInversionTargetMFDs(
            NZSHM22_InversionFaultSystemRuptSet invRupSet,
            double totalRateM5_Sans,
            double totalRateM5_TVZ,
            double bValue_Sans,
            double bValue_TVZ,
            double minMag_Sans,
            double minMag_TVZ,
            double maxMagSans,
            double maxMagTVZ,
            double uncertaintyPower,
            double uncertaintyScalar) {
        init(
                invRupSet,
                totalRateM5_Sans,
                totalRateM5_TVZ,
                bValue_Sans,
                bValue_TVZ,
                minMag_Sans,
                minMag_TVZ,
                maxMagSans,
                maxMagTVZ,
                uncertaintyPower,
                uncertaintyScalar);
    }

    public static class RegionalTargetMFDs {
        public GriddedRegion region;
        public String suffix;
        public RegionalRupSetData regionalRupSet;
        public boolean ignore = false;

        public double totalRateM5;
        public double bValue;
        public double minMag;
        public double maxMag;
        public double uncertaintyPower;
        public double uncertaintyScalar;

        public GutenbergRichterMagFreqDist totalTargetGR;
        public IncrementalMagFreqDist trulyOffFaultMFD;
        public SummedMagFreqDist totalSubSeismoOnFaultMFD;
        public IncrementalMagFreqDist targetOnFaultSupraSeisMFDs;
        public List<GutenbergRichterMagFreqDist> subSeismoOnFaultMFD_List;
        public UncertainIncrMagFreqDist uncertaintyMFD;

        private static final TypeAdapter<IncrementalMagFreqDist> mfdAdapter =
                new IncrementalMagFreqDist.Adapter();

        public RegionalTargetMFDs(
                RegionalRupSetData regionalRupSet,
                double totalRateM5,
                double bValue,
                double minMag,
                double maxMag,
                double uncertaintyPower,
                double uncertaintyScalar) {
            this.region = regionalRupSet.getRegion();
            this.totalRateM5 = totalRateM5;
            this.bValue = bValue;
            this.minMag = minMag;
            this.maxMag = maxMag;
            this.uncertaintyPower = uncertaintyPower;
            this.uncertaintyScalar = uncertaintyScalar;
            if (region.getName().contains("SANS TVZ")
                    || region.getName().contains("NZ_RECTANGLE")) {
                suffix = "SansTVZ";
            } else if (region.getName().contains("TVZ")) {
                suffix = "TVZ";
            } else {
                suffix = "";
            }
            this.regionalRupSet = regionalRupSet;
            if (regionalRupSet.isEmpty()) {
                ignore = true;
            } else {
                init();
            }
        }

        void writeToJson(JsonWriter out) throws IOException {
            out.beginObject();

            out.name("region");
            out.value(region.getName());

            out.name("totalRateM5");
            out.value(totalRateM5);

            out.name("bValue");
            out.value(bValue);

            out.name("minMag");
            out.value(minMag);

            // hint for writing a readr: this value is not present in older versions
            out.name("maxMag");
            out.value(maxMag);

            // hint for writing a readr: this value is not present in older versions
            out.name("uncertaintyPower");
            out.value(uncertaintyPower);

            // hint for writing a readr: this value is not present in older versions
            out.name("uncertaintyScalar");
            out.value(uncertaintyScalar);

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

        protected void init() {

            double mMaxOffFault = 8.05d; // NZ-ish
            NZSHM22_SpatialSeisPDF spatialSeisPDF = regionalRupSet.getSpatialSeisPDF();

            // convert mMaxOffFault to bin center
            mMaxOffFault -= DELTA_MAG / 2; // TODO is 8.05 already a bin centre?

            List<? extends FaultSection> faultSectionData =
                    regionalRupSet.getFaultSectionDataList();

            GriddedSeisUtils gridSeisUtils =
                    new GriddedSeisUtils(
                            faultSectionData,
                            spatialSeisPDF.getPDF(region),
                            regionalRupSet.getPolygonFaultGridAssociations());
            double fractionSeisOnFault = gridSeisUtils.pdfInPolys();

            System.out.println("faultSectionData.size() " + faultSectionData.size());
            System.out.println("fractionSeisOnFault " + fractionSeisOnFault);

            double onFaultRegionRateMgt5 = totalRateM5 * fractionSeisOnFault;

            // make the total target GR MFD with empty bins
            totalTargetGR = new GutenbergRichterMagFreqDist(NZ_MIN_MAG, NZ_NUM_BINS, DELTA_MAG);

            // populate the MFD bins
            double roundedMmaxOnFault =
                    totalTargetGR.getX(
                            totalTargetGR.getClosestXIndex(
                                    Math.min(regionalRupSet.getMaxMag(), maxMag)));
            totalTargetGR.setAllButTotMoRate(NZ_MIN_MAG, roundedMmaxOnFault, totalRateM5, bValue);

            // get ave min seismo mag for region
            // TODO: this is weighted by moment, so exponentially biased to larger ruptures (WHY?)
            // Kevin weighted by moment (which comes from slip rate) so higher momentrate faults
            // WILL predominate
            // NZ many tiny faults will not really contribute much
            double tempMag = NZSHM22_FaultSystemRupSetCalc.getMeanMinMag(regionalRupSet, true);

            // TODO: why derive this from the rupt set and not use mMaxOffFault??
            double aveMinSeismoMag =
                    totalTargetGR.getX(
                            totalTargetGR.getClosestXIndex(tempMag)); // round to nearest MFD value

            // TODO: why aveMinSeismoMag (Ned??)
            // seems to calculate our corner magnitude for tapered GR
            trulyOffFaultMFD =
                    NZSHM22_FaultSystemRupSetCalc.getTriLinearCharOffFaultTargetMFD(
                            totalTargetGR, onFaultRegionRateMgt5, aveMinSeismoMag, mMaxOffFault);

            subSeismoOnFaultMFD_List =
                    NZSHM22_FaultSystemRupSetCalc.getCharSubSeismoOnFaultMFD_forEachSection(
                            regionalRupSet, gridSeisUtils, totalTargetGR, minMag);

            // TODO: use computeMinSeismoMagForSections to find NZ values and explain 7.4
            // histogram to look for min values > 7.X
            totalSubSeismoOnFaultMFD = new SummedMagFreqDist(NZ_MIN_MAG, NZ_NUM_BINS, DELTA_MAG);
            for (GutenbergRichterMagFreqDist mfd : subSeismoOnFaultMFD_List) {
                totalSubSeismoOnFaultMFD.addIncrementalMagFreqDist(mfd);
            }

            SummedMagFreqDist tempTargetOnFaultSupraSeisMFD =
                    new SummedMagFreqDist(NZ_MIN_MAG, NZ_NUM_BINS, DELTA_MAG);
            tempTargetOnFaultSupraSeisMFD.addIncrementalMagFreqDist(totalTargetGR);
            tempTargetOnFaultSupraSeisMFD.subtractIncrementalMagFreqDist(trulyOffFaultMFD);
            tempTargetOnFaultSupraSeisMFD.subtractIncrementalMagFreqDist(totalSubSeismoOnFaultMFD);

            targetOnFaultSupraSeisMFDs =
                    MFDManipulation.fillBelowMag(tempTargetOnFaultSupraSeisMFD, minMag, 1.0e-20);
            targetOnFaultSupraSeisMFDs =
                    MFDManipulation.fillAboveMag(targetOnFaultSupraSeisMFDs, maxMag, 1.0e-20);
            targetOnFaultSupraSeisMFDs =
                    MFDManipulation.swapZeros(targetOnFaultSupraSeisMFDs, 1.0e-20);
            targetOnFaultSupraSeisMFDs.setRegion(region);
            uncertaintyMFD =
                    MFDManipulation.addMfdUncertainty(
                            targetOnFaultSupraSeisMFDs,
                            minMag,
                            maxMag,
                            uncertaintyPower,
                            uncertaintyScalar);

            if (MFD_STATS) {
                System.out.println("totalTargetGR_" + suffix + " after setAllButTotMoRate");
                System.out.println(totalTargetGR.toString());
                System.out.println("");

                System.out.println(
                        "trulyOffFaultMFD_" + suffix + " (TriLinearCharOffFaultTargetMFD)");
                System.out.println(trulyOffFaultMFD.toString());
                System.out.println("");

                System.out.println("totalSubSeismoOnFaultMFD_" + suffix + " (SummedMagFreqDist)");
                System.out.println(totalSubSeismoOnFaultMFD.toString());
                System.out.println("");

                System.out.println("targetOnFaultSupraSeisMFD_" + suffix + " (SummedMagFreqDist)");
                System.out.println(targetOnFaultSupraSeisMFDs.toString());
                System.out.println("");
            }

            // TODO are these purely analysis?? for now they're off
            //		// compute coupling coefficients
            //		impliedOnFaultCouplingCoeff = (targetOnFaultSupraSeisMFD.getTotalMomentRate()
            //				+ totalSubSeismoOnFaultMFD.getTotalMomentRate()) / origOnFltDefModMoRate;
            //		finalOffFaultCouplingCoeff = trulyOffFaultMFD.getTotalMomentRate() /
            // offFltDefModMoRate;
            //		impliedTotalCouplingCoeff = totalTargetGR.getTotalMomentRate() /
            // (origOnFltDefModMoRate + offFltDefModMoRate);

            // set the names
            totalTargetGR.setName("InversionTargetMFDs.totalTargetGR_" + suffix);
            targetOnFaultSupraSeisMFDs.setName(
                    "InversionTargetMFDs.targetOnFaultSupraSeisMFD_" + suffix);
            trulyOffFaultMFD.setName("InversionTargetMFDs.trulyOffFaultMFD_" + suffix + ".");
            totalSubSeismoOnFaultMFD.setName(
                    "InversionTargetMFDs.totalSubSeismoOnFaultMFD_" + suffix + ".");
        }
    }

    protected void init(
            NZSHM22_InversionFaultSystemRuptSet invRupSet,
            double totalRateM5_SansTVZ,
            double totalRateM5_TVZ,
            double bValue_SansTVZ,
            double bValue_TVZ,
            double minMag_Sans,
            double minMag_TVZ,
            double maxMagSans,
            double maxMagTVZ,
            double uncertaintyPower,
            double uncertaintyScalar) {

        setParent(invRupSet);

        tvz =
                new RegionalTargetMFDs(
                        invRupSet.getTvzRegionalData(),
                        totalRateM5_TVZ,
                        bValue_TVZ,
                        minMag_TVZ,
                        maxMagTVZ,
                        uncertaintyPower,
                        uncertaintyScalar);
        sansTvz =
                new RegionalTargetMFDs(
                        invRupSet.getSansTvzRegionalData(),
                        totalRateM5_SansTVZ,
                        bValue_SansTVZ,
                        minMag_Sans,
                        maxMagSans,
                        uncertaintyPower,
                        uncertaintyScalar);

        NZSHM22_SpatialSeisPDF spatialSeisPDF =
                invRupSet
                        .getModule(NZSHM22_LogicTreeBranch.class)
                        .getValue(NZSHM22_SpatialSeisPDF.class);
        NZSHM22_Regions regions =
                invRupSet.getModule(NZSHM22_LogicTreeBranch.class).getValue(NZSHM22_Regions.class);
        System.out.println(
                "tvz pdf fraction: " + spatialSeisPDF.getFractionInRegion(regions.getTvzRegion()));
        System.out.println(
                "sans tvz pdf fraction: "
                        + spatialSeisPDF.getFractionInRegion(regions.getSansTvzRegion()));
        System.out.println(
                "combined: "
                        + (spatialSeisPDF.getFractionInRegion(regions.getTvzRegion())
                                + spatialSeisPDF.getFractionInRegion(regions.getSansTvzRegion())));

        // Build the MFD Constraints for regions
        mfdConstraints = new ArrayList<>();
        mfdConstraints.add(sansTvz.targetOnFaultSupraSeisMFDs);
        if (!tvz.ignore) {
            mfdConstraints.add(tvz.targetOnFaultSupraSeisMFDs);
        }

        mfdUncertaintyConstraints = new ArrayList<>();
        mfdUncertaintyConstraints.add(sansTvz.uncertaintyMFD);
        if (!tvz.ignore) {
            mfdUncertaintyConstraints.add(tvz.uncertaintyMFD);
        }

        /*
         * TODO CBC the following block sets up base class var required later to save the solution,
         * namely:
         *  - totalTargetGR
         *  - trulyOffFaultMFD
         *  - totalSubSeismoOnFaultMFD
         */

        SummedMagFreqDist tempTargetGR = new SummedMagFreqDist(NZ_MIN_MAG, NZ_NUM_BINS, DELTA_MAG);
        tempTargetGR.addIncrementalMagFreqDist(sansTvz.totalTargetGR);
        if (!tvz.ignore) {
            tempTargetGR.addIncrementalMagFreqDist(tvz.totalTargetGR);
        }

        totalTargetGR = new GutenbergRichterMagFreqDist(NZ_MIN_MAG, NZ_NUM_BINS, DELTA_MAG);
        for (Point2D p : tempTargetGR) {
            totalTargetGR.set(p);
        }

        SummedMagFreqDist tempTrulyOffFaultMFD =
                new SummedMagFreqDist(NZ_MIN_MAG, NZ_NUM_BINS, DELTA_MAG);
        tempTrulyOffFaultMFD.addIncrementalMagFreqDist(sansTvz.trulyOffFaultMFD);
        if (!tvz.ignore) {
            tempTrulyOffFaultMFD.addIncrementalMagFreqDist(tvz.trulyOffFaultMFD);
        }

        trulyOffFaultMFD = new IncrementalMagFreqDist(NZ_MIN_MAG, NZ_NUM_BINS, DELTA_MAG);
        for (Point2D p : tempTrulyOffFaultMFD) {
            trulyOffFaultMFD.set(p);
        }

        // TODO: review this (if really needed) should add the SansTVZ and TVZ
        // CHECK: New MFD addition approach....
        totalSubSeismoOnFaultMFD = new SummedMagFreqDist(NZ_MIN_MAG, NZ_NUM_BINS, DELTA_MAG);
        totalSubSeismoOnFaultMFD.addIncrementalMagFreqDist(sansTvz.totalSubSeismoOnFaultMFD);
        if (!tvz.ignore) {
            totalSubSeismoOnFaultMFD.addIncrementalMagFreqDist(tvz.totalSubSeismoOnFaultMFD);
        }

        // TODO is this correct? It's just a guess by Oakley (and now Chris)
        ArrayList<GutenbergRichterMagFreqDist> subSeismoOnFaultMFD_List = new ArrayList<>();
        subSeismoOnFaultMFD_List.addAll(sansTvz.subSeismoOnFaultMFD_List);
        if (!tvz.ignore) {
            // oakley XXX this is potentially wrong because mfds should be ordered by section id. Have not yet
            // investigated if this actually has any consequences for us
            subSeismoOnFaultMFD_List.addAll(tvz.subSeismoOnFaultMFD_List);
        }
        subSeismoOnFaultMFDs = new SubSeismoOnFaultMFDs(subSeismoOnFaultMFD_List);

        targetOnFaultSupraSeisMFD = new SummedMagFreqDist(NZ_MIN_MAG, NZ_NUM_BINS, DELTA_MAG);
        targetOnFaultSupraSeisMFD.addIncrementalMagFreqDist(sansTvz.targetOnFaultSupraSeisMFDs);
        if (!tvz.ignore) {
            targetOnFaultSupraSeisMFD.addIncrementalMagFreqDist(tvz.targetOnFaultSupraSeisMFDs);
        }

        totalTargetGR.setName("totalTargetGR.all");
        trulyOffFaultMFD.setName("trulyOffFaultMFD.all");
        totalSubSeismoOnFaultMFD.setName("totalSubSeismoOnFaultMFD.all");
        targetOnFaultSupraSeisMFD.setName("targetOnFaultSupraSeisMFD.all");

        //	Reporting MFD components
        this.reportingMFDConstraintComponents = new ArrayList<>();
        this.reportingMFDConstraintComponents.add(trulyOffFaultMFD);
        this.reportingMFDConstraintComponents.add(sansTvz.targetOnFaultSupraSeisMFDs);
        if (!tvz.ignore) {
            this.reportingMFDConstraintComponents.add(tvz.targetOnFaultSupraSeisMFDs);
        }
        this.reportingMFDConstraintComponents.add(totalSubSeismoOnFaultMFD);

        //	Reporting MFD components V2
        this.reportingMFDConstraintComponentsV2 = new ArrayList<>();
        this.reportingMFDConstraintComponentsV2.add(totalTargetGR);
        this.reportingMFDConstraintComponentsV2.add(trulyOffFaultMFD);
        this.reportingMFDConstraintComponentsV2.add(targetOnFaultSupraSeisMFD);
        this.reportingMFDConstraintComponentsV2.add(totalSubSeismoOnFaultMFD);
        if (!tvz.ignore) {
            this.reportingMFDConstraintComponentsV2.add(tvz.totalTargetGR);
            this.reportingMFDConstraintComponentsV2.add(sansTvz.totalTargetGR);
            this.reportingMFDConstraintComponentsV2.add(tvz.trulyOffFaultMFD);
            this.reportingMFDConstraintComponentsV2.add(sansTvz.trulyOffFaultMFD);
            this.reportingMFDConstraintComponentsV2.add(tvz.targetOnFaultSupraSeisMFDs);
            this.reportingMFDConstraintComponentsV2.add(sansTvz.targetOnFaultSupraSeisMFDs);
            this.reportingMFDConstraintComponentsV2.add(tvz.totalSubSeismoOnFaultMFD);
            this.reportingMFDConstraintComponentsV2.add(sansTvz.totalSubSeismoOnFaultMFD);
        }

        if (MFD_STATS) {

            System.out.println("trulyOffFaultMFD.all");
            System.out.println(trulyOffFaultMFD.toString());
            System.out.println("");

            System.out.println("totalTargetGR.all");
            System.out.println(totalTargetGR.toString());
            System.out.println("");

            System.out.println("totalSubSeismoOnFaultMFD.all");
            System.out.println(totalSubSeismoOnFaultMFD.toString());
            System.out.println("");
        }
    }

    public RegionalTargetMFDs getSansTvz() {
        return sansTvz;
    }

    public RegionalTargetMFDs getTvz() {
        return tvz;
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
    public void writeToArchive(ArchiveOutput zout, String entryPrefix) throws IOException {
        super.writeToArchive(zout, entryPrefix);

        FileBackedModule.initEntry(zout, entryPrefix, "regional_inversion_target_mfds.json");
        BufferedOutputStream out = new BufferedOutputStream(zout.getOutputStream());
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

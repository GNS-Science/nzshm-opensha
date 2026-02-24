package nz.cri.gns.NZSHM22.opensha.inversion.joint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonWriter;
import java.awt.geom.Point2D;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import nz.cri.gns.NZSHM22.opensha.analysis.NZSHM22_FaultSystemRupSetCalc;
import nz.cri.gns.NZSHM22.opensha.data.region.NewZealandRegions;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_SpatialSeisPDF;
import nz.cri.gns.NZSHM22.opensha.inversion.MFDManipulation;
import nz.cri.gns.NZSHM22.opensha.inversion.RegionalRupSetData;
import nz.earthsciences.jupyterlogger.CSVCell;
import nz.earthsciences.jupyterlogger.JupyterLogger;
import org.opensha.commons.data.uncertainty.UncertainIncrMagFreqDist;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.util.io.archive.ArchiveOutput;
import org.opensha.commons.util.modules.helpers.FileBackedModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
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
public class CrustalInversionTargetMFDs extends U3InversionTargetMFDs {

    public static final double NZ_MIN_MAG = 5.05; // used instead of UCERF3 value 0.05
    public static final int NZ_NUM_BINS = 40; // used instead of UCERF3 value 90

    RegionalTargetMFDs sansTvz;

    protected List<IncrementalMagFreqDist> mfdConstraints;
    protected List<UncertainIncrMagFreqDist> mfdUncertaintyConstraints;

    @Override
    public List<IncrementalMagFreqDist> getMFD_Constraints() {
        return mfdConstraints;
    }

    public List<UncertainIncrMagFreqDist> getMfdUncertaintyConstraints() {
        return mfdUncertaintyConstraints;
    }

    public CrustalInversionTargetMFDs(FaultSystemRupSet rupSet, PartitionConfig config) {
        init(rupSet, config);
    }

    public static class RegionalTargetMFDs {
        public GriddedRegion region = NewZealandRegions.NZ;
        public RegionalRupSetData regionalRupSet;
        public boolean isEmpty = false;

        public PartitionConfig config;

        public GutenbergRichterMagFreqDist totalTargetGR;
        public IncrementalMagFreqDist trulyOffFaultMFD;
        public SummedMagFreqDist totalSubSeismoOnFaultMFD;
        public IncrementalMagFreqDist targetOnFaultSupraSeisMFDs;
        public List<GutenbergRichterMagFreqDist> subSeismoOnFaultMFD_List;
        public UncertainIncrMagFreqDist uncertaintyMFD;

        private static final TypeAdapter<IncrementalMagFreqDist> mfdAdapter =
                new IncrementalMagFreqDist.Adapter();

        public RegionalTargetMFDs(RegionalRupSetData regionalRupSet, PartitionConfig config) {
            this.config = config;
            this.regionalRupSet = regionalRupSet;
            if (regionalRupSet.isEmpty()) {
                isEmpty = true;
            } else {
                init();
            }
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

            double onFaultRegionRateMgt5 = config.totalRateM5 * fractionSeisOnFault;

            // make the total target GR MFD with empty bins
            totalTargetGR = new GutenbergRichterMagFreqDist(NZ_MIN_MAG, NZ_NUM_BINS, DELTA_MAG);

            // populate the MFD bins
            double roundedMmaxOnFault =
                    totalTargetGR.getX(
                            totalTargetGR.getClosestXIndex(
                                    Math.min(regionalRupSet.getMaxMag(), config.maxMag)));
            totalTargetGR.setAllButTotMoRate(
                    NZ_MIN_MAG, roundedMmaxOnFault, config.totalRateM5, config.bValue);

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
                            regionalRupSet, gridSeisUtils, totalTargetGR, config.minMag);

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
                    MFDManipulation.fillBelowMag(
                            tempTargetOnFaultSupraSeisMFD, config.minMag, 1.0e-20);
            targetOnFaultSupraSeisMFDs =
                    MFDManipulation.fillAboveMag(
                            targetOnFaultSupraSeisMFDs, config.maxMag, 1.0e-20);
            targetOnFaultSupraSeisMFDs =
                    MFDManipulation.swapZeros(targetOnFaultSupraSeisMFDs, 1.0e-20);
            targetOnFaultSupraSeisMFDs.setRegion(region);
            uncertaintyMFD =
                    MFDManipulation.addMfdUncertainty(
                            targetOnFaultSupraSeisMFDs,
                            config.minMag,
                            config.maxMag,
                            config.mfdUncertaintyPower,
                            config.mfdUncertaintyScalar);

            JupyterLogger.logger().addMarkDown("## Crustal MFDs");

            CSVCell csvCell =
                    JupyterLogger.logger()
                            .addCSV("RegionalTargetMFDs", "magnitude")
                            .showTable(false);
            csvCell.setIndex(totalTargetGR.xValues());
            csvCell.addColumn("totalTargetGR_", totalTargetGR.yValues());
            csvCell.addColumn("trulyOffFaultMFD_", trulyOffFaultMFD.yValues());
            csvCell.addColumn("totalSubSeismoOnFaultMFD_", totalSubSeismoOnFaultMFD.yValues());
            csvCell.addColumn("targetOnFaultSupraSeisMFD_", targetOnFaultSupraSeisMFDs.yValues());

            JupyterLogger.logger().addLinePlot("RegionalTargetMFDs", csvCell).setYLog();

            // TODO are these purely analysis?? for now they're off
            //		// compute coupling coefficients
            //		impliedOnFaultCouplingCoeff = (targetOnFaultSupraSeisMFD.getTotalMomentRate()
            //				+ totalSubSeismoOnFaultMFD.getTotalMomentRate()) / origOnFltDefModMoRate;
            //		finalOffFaultCouplingCoeff = trulyOffFaultMFD.getTotalMomentRate() /
            // offFltDefModMoRate;
            //		impliedTotalCouplingCoeff = totalTargetGR.getTotalMomentRate() /
            // (origOnFltDefModMoRate + offFltDefModMoRate);

            // set the names
            totalTargetGR.setName("InversionTargetMFDs.totalTargetGR_SansTVZ");
            targetOnFaultSupraSeisMFDs.setName(
                    "InversionTargetMFDs.targetOnFaultSupraSeisMFD_SansTVZ");
            trulyOffFaultMFD.setName("InversionTargetMFDs.trulyOffFaultMFD_" + ".");
            totalSubSeismoOnFaultMFD.setName("InversionTargetMFDs.totalSubSeismoOnFaultMFD_" + ".");
        }
    }

    protected void init(FaultSystemRupSet rupSet, PartitionConfig config) {

        setParent(rupSet);

        RegionalRupSetData regionalData =
                new RegionalRupSetData(
                        rupSet,
                        NewZealandRegions.NZ,
                        PartitionPredicate.CRUSTAL.getPredicate(rupSet),
                        config.spatialSeisPDF,
                        config.minMag,
                        config.polygonBufferSize,
                        config.polygonMinBufferSize);

        sansTvz = new RegionalTargetMFDs(regionalData, config);

        // Build the MFD Constraints for regions
        mfdConstraints = new ArrayList<>();
        mfdConstraints.add(sansTvz.targetOnFaultSupraSeisMFDs);

        mfdUncertaintyConstraints = new ArrayList<>();
        mfdUncertaintyConstraints.add(sansTvz.uncertaintyMFD);

        /*
         * TODO CBC the following block sets up base class var required later to save the solution,
         * namely:
         *  - totalTargetGR
         *  - trulyOffFaultMFD
         *  - totalSubSeismoOnFaultMFD
         */

        SummedMagFreqDist tempTargetGR = new SummedMagFreqDist(NZ_MIN_MAG, NZ_NUM_BINS, DELTA_MAG);
        tempTargetGR.addIncrementalMagFreqDist(sansTvz.totalTargetGR);

        totalTargetGR = new GutenbergRichterMagFreqDist(NZ_MIN_MAG, NZ_NUM_BINS, DELTA_MAG);
        for (Point2D p : tempTargetGR) {
            totalTargetGR.set(p);
        }

        SummedMagFreqDist tempTrulyOffFaultMFD =
                new SummedMagFreqDist(NZ_MIN_MAG, NZ_NUM_BINS, DELTA_MAG);
        tempTrulyOffFaultMFD.addIncrementalMagFreqDist(sansTvz.trulyOffFaultMFD);

        trulyOffFaultMFD = new IncrementalMagFreqDist(NZ_MIN_MAG, NZ_NUM_BINS, DELTA_MAG);
        for (Point2D p : tempTrulyOffFaultMFD) {
            trulyOffFaultMFD.set(p);
        }

        // TODO: review this (if really needed) should add the SansTVZ and TVZ
        // CHECK: New MFD addition approach....
        totalSubSeismoOnFaultMFD = new SummedMagFreqDist(NZ_MIN_MAG, NZ_NUM_BINS, DELTA_MAG);
        totalSubSeismoOnFaultMFD.addIncrementalMagFreqDist(sansTvz.totalSubSeismoOnFaultMFD);

        // TODO is this correct? It's just a guess by Oakley (and now Chris)
        ArrayList<GutenbergRichterMagFreqDist> subSeismoOnFaultMFD_List = new ArrayList<>();
        subSeismoOnFaultMFD_List.addAll(sansTvz.subSeismoOnFaultMFD_List);
        subSeismoOnFaultMFDs = new SubSeismoOnFaultMFDs(subSeismoOnFaultMFD_List);

        targetOnFaultSupraSeisMFD = new SummedMagFreqDist(NZ_MIN_MAG, NZ_NUM_BINS, DELTA_MAG);
        targetOnFaultSupraSeisMFD.addIncrementalMagFreqDist(sansTvz.targetOnFaultSupraSeisMFDs);

        totalTargetGR.setName("totalTargetGR.all");
        trulyOffFaultMFD.setName("trulyOffFaultMFD.all");
        totalSubSeismoOnFaultMFD.setName("totalSubSeismoOnFaultMFD.all");
        targetOnFaultSupraSeisMFD.setName("targetOnFaultSupraSeisMFD.all");

        JupyterLogger.logger().addMarkDown("## Total MFDs");
        CSVCell csvCell =
                JupyterLogger.logger()
                        .addCSV("NZSHM22_CrustalInversionTargetMFDs_init", "magnitude")
                        .showTable(false);
        csvCell.setIndex(trulyOffFaultMFD.xValues());
        csvCell.addColumn("trulyOffFaultMFD.all", trulyOffFaultMFD.yValues());
        csvCell.addColumn("totalTargetGR.all", totalTargetGR.yValues());
        csvCell.addColumn("totalSubSeismoOnFaultMFD.all", totalSubSeismoOnFaultMFD.yValues());

        JupyterLogger.logger()
                .addLinePlot("NZSHM22_CrustalInversionTargetMFDs_init", csvCell)
                .setYLog();
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
        json.endObject();

        writer.flush();
        out.flush();
        zout.closeEntry();
    }
}

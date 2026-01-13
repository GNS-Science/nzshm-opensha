package nz.cri.gns.NZSHM22.opensha.analysis;

import static scratch.UCERF3.inversion.U3InversionTargetMFDs.DELTA_MAG;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import nz.cri.gns.NZSHM22.opensha.inversion.NZSHM22_CrustalInversionTargetMFDs;
import nz.cri.gns.NZSHM22.opensha.inversion.NZSHM22_SubductionInversionTargetMFDs;
import nz.cri.gns.NZSHM22.opensha.inversion.RegionalRupSetData;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModSectMinMags;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import scratch.UCERF3.analysis.FaultSystemRupSetCalc;
import scratch.UCERF3.griddedSeismicity.GriddedSeisUtils;

public class NZSHM22_FaultSystemRupSetCalc extends FaultSystemRupSetCalc {

    /**
     * Like Math.max(), but does its best to return a value. If one value is null or NaN, it will
     * return the other value.
     *
     * @param a
     * @param b
     * @return
     */
    public static double safeMax(Double a, Double b) {
        if (a == null || Double.isNaN(a)) {
            return b;
        }
        if (b == null || Double.isNaN(b)) {
            return a;
        }
        return Math.max(a, b);
    }

    /**
     * Override the UCERF3 implementation which does something special when ID==Parkfield
     *
     * @param fltSystRupSet
     * @param systemWideMinSeismoMag
     * @return
     */
    public static double[] computeMinSeismoMagForSections(
            FaultSystemRupSet fltSystRupSet, double systemWideMinSeismoMag) {
        double[] minMagForSect = new double[fltSystRupSet.getNumSections()];
        List<? extends FaultSection> sectDataList = fltSystRupSet.getFaultSectionDataList();

        // make map between parent section name and maximum minimum magnitude
        HashMap<Integer, Double> magForParSectMap = new HashMap<>();
        for (int s = 0; s < sectDataList.size(); s++) {
            double minSeismoMag = fltSystRupSet.getMinMagForSection(s);
            int parentId = sectDataList.get(s).getParentSectionId();
            magForParSectMap.compute(parentId, (k, v) -> safeMax(v, minSeismoMag));
        }

        // now set the value for each section in the array, giving a value of
        // systemWideMinMinSeismoMag
        // if the parent section value falls below this
        for (int s = 0; s < sectDataList.size(); s++) {
            double minMag = magForParSectMap.get(sectDataList.get(s).getParentSectionId());
            minMagForSect[s] = safeMax(minMag, systemWideMinSeismoMag);
        }

        return minMagForSect;
    }

    /**
     * This gets the sub-seismogenic MFD for each fault section for the characteristic model, where
     * each fault gets a GR up to just below the minimum seismogenic magnitude, with a total rate
     * equal to the rate of events inside the fault section polygon (as determined by the
     * spatialSeisPDF and tatal regional rate).
     *
     * @param rupSet
     * @param gridSeisUtils
     * @param totalTargetGR
     * @param minMag
     * @return
     */
    public static ArrayList<GutenbergRichterMagFreqDist> getCharSubSeismoOnFaultMFD_forEachSection(
            RegionalRupSetData rupSet,
            GriddedSeisUtils gridSeisUtils,
            GutenbergRichterMagFreqDist totalTargetGR,
            double minMag) {
        ArrayList<GutenbergRichterMagFreqDist> mfds = new ArrayList<GutenbergRichterMagFreqDist>();
        double totMgt5_rate = totalTargetGR.getCumRate(0);
        // The index (bin) below the bin with the global minMag.
        // This is the highest bin that a sub-seismogenic MFD can use.
        int globalMMaxIndex = totalTargetGR.getClosestXIndex(minMag) - 1;
        for (int s = 0; s < rupSet.getFaultSectionDataList().size(); s++) {

            // find the maximum magnitude of the sub seis MFD by getting the index (bin) for the
            // minimum Mag of the section
            int mMaxIndex =
                    totalTargetGR.getClosestXIndex(rupSet.getMinMagForSection(s))
                            - 1; // subtract 1 to stay under
            // make sure we stay below the global min mag
            mMaxIndex = Math.max(mMaxIndex, globalMMaxIndex);
            if (mMaxIndex == -1)
                throw new RuntimeException(
                        "Problem Mmax: "
                                + rupSet.getMinMagForSection(s)
                                + "\t"
                                + rupSet.getFaultSectionDataList().get(s).getName());
            // get the magnitude from the bin centre
            double mMax = totalTargetGR.getX(mMaxIndex);

            GutenbergRichterMagFreqDist tempOnFaultGR =
                    new GutenbergRichterMagFreqDist(
                            totalTargetGR.getMinX(),
                            totalTargetGR.size(),
                            totalTargetGR.getDelta(),
                            totalTargetGR.getMagLower(),
                            mMax,
                            1.0,
                            totalTargetGR.get_bValue());
            double sectRate = gridSeisUtils.pdfValForSection(s) * totMgt5_rate;
            tempOnFaultGR.scaleToCumRate(0, sectRate);

            // check for NaN rates
            if (Double.isNaN(tempOnFaultGR.getTotalIncrRate())) {
                throw new RuntimeException(
                        "Bad MFD for section:\t"
                                + s
                                + "\t"
                                + rupSet.getFaultSectionDataList().get(s).getName()
                                + "\tgridSeisUtils.pdfValForSection(s) = "
                                + gridSeisUtils.pdfValForSection(s)
                                + "\tmMax = "
                                + mMax);
            }
            mfds.add(tempOnFaultGR);
        }
        return mfds;
    }

    /**
     * This computes a tri-linear MFD for the off-fault MFD on the characteristic branch
     *
     * @param totalTargetGR - the total target MFD
     * @param totOnFaultMgt5_Rate - the rate of on-fault events at Mge5
     * @param mMinSeismoOnFault - the average minimum magnitude for seismogenic on-fault ruptures
     * @param mMaxOffFault - the maximum magnitude for off-fault events.
     * @return
     */
    public static IncrementalMagFreqDist getTriLinearCharOffFaultTargetMFD(
            GutenbergRichterMagFreqDist totalTargetGR,
            double totOnFaultMgt5_Rate,
            double mMinSeismoOnFault,
            double mMaxOffFault) {

        int mMinSeismoOnFaultIndex = totalTargetGR.getClosestXIndex(mMinSeismoOnFault);
        int mMaxOffFaultIndex = totalTargetGR.getClosestXIndex(mMaxOffFault);

        double offFaultMgt5_Rate = totalTargetGR.getCumRate(5.05) - totOnFaultMgt5_Rate;

        // rate corrections since final MFDs are not perfect GRs (found by hand)
        double onCorr = 0.98;
        double offCorr = 1.01;

        // create a temp GR with on-fault rate
        GutenbergRichterMagFreqDist tempOnFaultGR =
                new GutenbergRichterMagFreqDist(
                        totalTargetGR.getMinX(),
                        totalTargetGR.size(),
                        totalTargetGR.getDelta(),
                        totalTargetGR.getMagLower(),
                        totalTargetGR.getMagUpper(),
                        1.0,
                        totalTargetGR.get_bValue());
        tempOnFaultGR.scaleToCumRate(5.05, totOnFaultMgt5_Rate * onCorr);

        // create a temp GR with off-fault rate
        GutenbergRichterMagFreqDist tempOffFaultGR =
                new GutenbergRichterMagFreqDist(
                        totalTargetGR.getMinX(),
                        totalTargetGR.size(),
                        totalTargetGR.getDelta(),
                        totalTargetGR.getMagLower(),
                        totalTargetGR.getMagUpper(),
                        1.0,
                        totalTargetGR.get_bValue());
        tempOffFaultGR.scaleToCumRate(5.05, offFaultMgt5_Rate * offCorr);

        // now create the desired MFDs
        IncrementalMagFreqDist onFaultMFD =
                new IncrementalMagFreqDist(
                        totalTargetGR.getMinX(), totalTargetGR.size(), totalTargetGR.getDelta());
        IncrementalMagFreqDist offFaultMFD =
                new IncrementalMagFreqDist(
                        totalTargetGR.getMinX(), totalTargetGR.size(), totalTargetGR.getDelta());
        for (int i = 0; i < mMinSeismoOnFaultIndex; i++) {
            onFaultMFD.set(i, tempOnFaultGR.getY(i));
            offFaultMFD.set(i, tempOffFaultGR.getY(i));
        }
        for (int i = mMinSeismoOnFaultIndex; i <= mMaxOffFaultIndex + 1; i++) {
            double wtOnTotRate =
                    (double) (i - mMinSeismoOnFaultIndex)
                            / (double)
                                    ((mMaxOffFaultIndex + 1)
                                            - mMinSeismoOnFaultIndex); // starts at zero and builds
            double wtOnFaultRate = 1.0 - wtOnTotRate;
            // way 1
            //			double onFltRate =
            // wtOnFaultRate*tempOnFaultGR.getY(i)+wtOnTotRate*totalTargetGR.getY(i);
            //			onFaultMFD.set(i,onFltRate);
            //			offFaultMFD.set(i,totalTargetGR.getY(i)-onFltRate);
            // way 2 (same as Way 1)
            //			offFaultMFD.set(i,tempOffFaultGR.getY(i)*wtOnFaultRate);
            //			onFaultMFD.set(i,totalTargetGR.getY(i)-offFaultMFD.getY(i));
            // way 3
            double onFltRate =
                    Math.pow(
                            10,
                            wtOnFaultRate * Math.log10(tempOnFaultGR.getY(i))
                                    + wtOnTotRate * Math.log10(totalTargetGR.getY(i)));
            onFaultMFD.set(i, onFltRate);
            offFaultMFD.set(i, totalTargetGR.getY(i) - onFltRate);
            if (offFaultMFD.getY(i) < 0)
                offFaultMFD.set(i, 0); // numerical precision issue at last point if mMaxOffFault =
            // totalTargetGR.getMagUpper()
        }
        for (int i = mMaxOffFaultIndex + 1; i < totalTargetGR.size(); i++) {
            onFaultMFD.set(i, totalTargetGR.getY(i));
            offFaultMFD.set(i, 0);
        }

        onFaultMFD.setName("onFaultMFD");
        onFaultMFD.setInfo(
                "(rate(M>=5)="
                        + (float) onFaultMFD.getCumRate(5.05)
                        + "; totMoRate="
                        + onFaultMFD.getTotalMomentRate()
                        + ")");
        offFaultMFD.setName("offFaultMFD");
        offFaultMFD.setInfo(
                "(rate(M>=5)="
                        + (float) offFaultMFD.getCumRate(5.05)
                        + "; totMoRate="
                        + offFaultMFD.getTotalMomentRate()
                        + ")");

        //		// TESTS
        //		System.out.println("\nInputs:\n");
        //		System.out.println("\ttotOnFaultMgt5_Rate = "+(float)totOnFaultMgt5_Rate);
        //		double totRate = totalTargetGR.getCumRate(5.05);
        //		double totMoRate = totalTargetGR.getTotalMomentRate();
        //		System.out.println("\ttotalTargetGR.getCumRate(5.05) = "+(float)totRate);
        //		System.out.println("\ttotalTargetGR.getTotalMomentRate() = "+(float)totMoRate);
        //		System.out.println("\tmMinSeismoOnFault="+(float)mMinSeismoOnFault);
        //		System.out.println("\tmMaxOffFault="+(float)mMaxOffFault);
        //
        //		System.out.println("\nResults:\n");
        //		System.out.println("\tonFaultMFD.getCumRate(5.05) =
        // "+(float)onFaultMFD.getCumRate(5.05)+"\tfraction="+((float)(onFaultMFD.getCumRate(5.05)/totRate)));
        //		System.out.println("\toffFaultMFD.getCumRate(5.05) =
        // "+(float)offFaultMFD.getCumRate(5.05)+"\tfraction="+((float)(offFaultMFD.getCumRate(5.05)/totRate)));
        //		System.out.println("\ttotal implied Rate(>=5.05) =
        // "+(float)(offFaultMFD.getCumRate(5.05)+onFaultMFD.getCumRate(5.05)));
        //		System.out.println("\tonFaultMFD.getTotalMomentRate() =
        // "+(float)onFaultMFD.getTotalMomentRate()+"\tfraction="+((float)(onFaultMFD.getTotalMomentRate()/totMoRate)));
        //		System.out.println("\toffFaultMFD.getTotalMomentRate() =
        // "+(float)offFaultMFD.getTotalMomentRate()+"\tfraction="+((float)(offFaultMFD.getTotalMomentRate()/totMoRate)));
        //		System.out.println("\nTests (all should be close to 1.0):\n");
        //		System.out.println("\tTotMoRate:
        // "+(float)(totMoRate/(onFaultMFD.getTotalMomentRate()+offFaultMFD.getTotalMomentRate()))+"\t(totMoRate/(onFaultMFD.getTotalMomentRate()+offFaultMFD.getTotalMomentRate()))");
        //		System.out.println("\tTotCumRate:
        // "+(float)(totRate/(onFaultMFD.getCumRate(5.05)+offFaultMFD.getCumRate(5.05)))+"\t(totRate/(onFaultMFD.getCumRate(5.05)+offFaultMFD.getCumRate(5.05)))");
        //		System.out.println("\tOnFaultCumRate:
        // "+(float)(totOnFaultMgt5_Rate/onFaultMFD.getCumRate(5.05))+"\t(totOnFaultMgt5_Rate/onFaultMFD.getCumRate(5.05))");
        //		System.out.println("\tOffFaultCumRate:
        // "+(float)((totRate-totOnFaultMgt5_Rate)/+offFaultMFD.getCumRate(5.05))+"\t((totRate-totOnFaultMgt5_Rate)/+offFaultMFD.getCumRate(5.05))");
        //
        //		ArrayList<EvenlyDiscretizedFunc> funcs = new ArrayList<EvenlyDiscretizedFunc>();
        //		funcs.add(totalTargetGR);
        //		funcs.add(offFaultMFD);
        //		funcs.add(onFaultMFD);
        ////		funcs.add(totalTargetGR.getCumRateDistWithOffset());
        ////		funcs.add(offFaultMFD.getCumRateDistWithOffset());
        ////		funcs.add(onFaultMFD.getCumRateDistWithOffset());
        //		GraphWindow graph = new GraphWindow(funcs, "MFDs");
        //		graph.setX_AxisRange(5, 9);
        //		graph.setY_AxisRange(1e-5, 10);
        //		graph.setYLog(true);
        //		graph.setX_AxisLabel("Mag");
        //		graph.setY_AxisLabel("Rate (per year)");
        //		graph.setPlotLabelFontSize(18);
        //		graph.setAxisLabelFontSize(16);
        //		graph.setTickLabelFontSize(14);

        //		IncrementalMagFreqDist[] mfds = {onFaultMFD,offFaultMFD};
        return offFaultMFD;
    }

    /**
     * This the mean final minimum magnitude among all the fault sections in the given
     * FaultSystemRupSet
     *
     * @param wtByMoRate - determines whether or not it's a weighted average based on orignal moment
     *     rate
     */
    public static double getMeanMinMag(RegionalRupSetData rupSet, boolean wtByMoRate) {
        double wt = 1;
        double totWt = 0;
        double sum = 0;
        List<? extends FaultSection> sections = rupSet.getFaultSectionDataList();
        for (int i = 0; i < sections.size(); i++) {
            if (wtByMoRate) {
                wt = sections.get(i).calcMomentRate(true);
                if (Double.isNaN(wt)) {
                    wt = 0;
                }
            }
            sum += rupSet.getMinMagForSection(i) * wt;
            totWt += wt;
        }
        return sum / totWt;
    }

    /**
     * This computes whether each rupture has a magnitude below any of the final minimum mags for
     * the sections the rupture utilizes. To be precise, the magnitude must be below the lower bin
     * edge implied by the minimum magnitude.
     */
    public static boolean[] computeWhichRupsFallBelowSectionMinMags(
            FaultSystemRupSet fltSystRupSet, ModSectMinMags modMinMags) {
        boolean[] rupBelowSectMinMag = new boolean[fltSystRupSet.getNumRuptures()];
        for (int r = 0; r < fltSystRupSet.getNumRuptures(); r++) {
            rupBelowSectMinMag[r] = isRuptureBelowSectMinMag(fltSystRupSet, r, modMinMags);
        }
        return rupBelowSectMinMag;
    }

    /**
     * This computes whether the rupture at rupIndex has a magnitude below any of the final minimum
     * mags for the sections the rupture utilizes. To be precise, the magnitude must be below the
     * lower bin edge implied by the minimum magnitude.
     */
    public static boolean isRuptureBelowSectMinMag(
            FaultSystemRupSet fltSystRupSet, int rupIndex, ModSectMinMags modMinMags) {
        // We want to use binning that works for crustal and subduction
        Preconditions.checkState(
                NZSHM22_CrustalInversionTargetMFDs.NZ_MIN_MAG
                        == NZSHM22_SubductionInversionTargetMFDs.MIN_MAG);
        Preconditions.checkState(DELTA_MAG == NZSHM22_SubductionInversionTargetMFDs.DELTA_MAG);
        IncrementalMagFreqDist bins =
                new IncrementalMagFreqDist(
                        NZSHM22_CrustalInversionTargetMFDs.NZ_MIN_MAG,
                        Math.max(
                                NZSHM22_CrustalInversionTargetMFDs.NZ_NUM_BINS,
                                NZSHM22_SubductionInversionTargetMFDs.NUM_MAG),
                        DELTA_MAG);
        int rupBin = bins.getClosestXIndex(fltSystRupSet.getMagForRup(rupIndex));

        for (int s : fltSystRupSet.getSectionsIndicesForRup(rupIndex)) {
            int sectionBin = bins.getClosestXIndex(modMinMags.getMinMagForSection(s));
            if (rupBin < sectionBin) {
                return true;
            }
        }
        return false;
    }
}

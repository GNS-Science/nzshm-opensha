package nz.cri.gns.NZSHM22.util;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import nz.cri.gns.NZSHM22.opensha.inversion.NZSHM22_InversionFaultSystemSolution;
import org.jfree.chart.plot.DatasetRenderingOrder;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;
import scratch.UCERF3.analysis.CompoundFSSPlots;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

public class MFDPlot {

    /**
     * Writes incremental and cumulative participation and nucleation MFDs for each parent fault section.
     *
     * @param sol
     * @param dir
     * @throws IOException
     */
    public static void writeParentSectionMFDPlots(NZSHM22_InversionFaultSystemSolution sol,
                                                  Map<String, Set<Integer>> parents,
                                                  File dir) throws IOException {
        if (!dir.exists())
            dir.mkdir();

        File particIncrSubDir = new File(dir, "participation_incremental");
        if (!particIncrSubDir.exists())
            particIncrSubDir.mkdir();
        File particCmlSubDir = new File(dir, "participation_cumulative");
        if (!particCmlSubDir.exists())
            particCmlSubDir.mkdir();
        File nuclIncrSubDir = new File(dir, "nucleation_incremental");
        if (!nuclIncrSubDir.exists())
            nuclIncrSubDir.mkdir();
        File nuclCmlSubDir = new File(dir, "nucleation_cumulative");
        if (!nuclCmlSubDir.exists())
            nuclCmlSubDir.mkdir();

        if (parents == null) {
            parents = new HashMap<>();
            for (FaultSection sect : sol.getRupSet().getFaultSectionDataList()) {
                if (!parents.containsKey(sect.getParentSectionName())) {
                    parents.put(sect.getParentSectionName(), Sets.newHashSet(sect.getParentSectionId()));
                }
            }
        }

        // MFD extents
        double minMag = 5.05;
        double maxMag = 9.05;
        int numMag = (int) ((maxMag - minMag) / 0.1d) + 1;

        for (String parentName : parents.keySet()) {

            List<EvenlyDiscretizedFunc> nuclMFDs = Lists.newArrayList();
            List<EvenlyDiscretizedFunc> partMFDs = Lists.newArrayList();

            // get incremental MFDs
            SummedMagFreqDist nuclMFD = null;

            nuclMFD = sol.calcNucleationMFD_forParentSect(parents.get(parentName), minMag, maxMag, numMag);
            nuclMFDs.add(nuclMFD);
            IncrementalMagFreqDist partMFD = sol.calcParticipationMFD_forParentSect(parents.get(parentName), minMag, maxMag, numMag);
            partMFDs.add(partMFD);

            // make cumulative MFDs with offsets
            List<EvenlyDiscretizedFunc> nuclCmlMFDs = Lists.newArrayList();
            nuclCmlMFDs.add(nuclMFD.getCumRateDistWithOffset());
            List<EvenlyDiscretizedFunc> partCmlMFDs = Lists.newArrayList();
            EvenlyDiscretizedFunc partCmlMFD = partMFD.getCumRateDistWithOffset();
            partCmlMFDs.add(partCmlMFD);

            // if it's an IFSS, we can add sub seis MFDs
            List<EvenlyDiscretizedFunc> subSeismoMFDs;
            List<EvenlyDiscretizedFunc> subSeismoCmlMFDs;
            List<EvenlyDiscretizedFunc> subPlusSupraSeismoNuclMFDs;
            List<EvenlyDiscretizedFunc> subPlusSupraSeismoNuclCmlMFDs;
            List<EvenlyDiscretizedFunc> subPlusSupraSeismoParticMFDs;
            List<EvenlyDiscretizedFunc> subPlusSupraSeismoParticCmlMFDs;

            subSeismoMFDs = Lists.newArrayList();
            subSeismoCmlMFDs = Lists.newArrayList();
            SummedMagFreqDist subSeismoMFD = sol.getFinalSubSeismoOnFaultMFDForSects(parents.get(parentName));
            subSeismoMFDs.add(subSeismoMFD);
            subSeismoCmlMFDs.add(subSeismoMFD.getCumRateDistWithOffset());

            // nucleation sum
            SummedMagFreqDist subPlusSupraSeismoNuclMFD = new SummedMagFreqDist(
                    subSeismoMFD.getMinX(), subSeismoMFD.size(), subSeismoMFD.getDelta());
            subPlusSupraSeismoNuclMFD.addIncrementalMagFreqDist(subSeismoMFD);
            subPlusSupraSeismoNuclMFD.addIncrementalMagFreqDist(resizeToDimensions(
                    nuclMFD, subSeismoMFD.getMinX(), subSeismoMFD.size(), subSeismoMFD.getDelta()));
            EvenlyDiscretizedFunc subPlusSupraSeismoNuclCmlMFD = subPlusSupraSeismoNuclMFD.getCumRateDistWithOffset();
            subPlusSupraSeismoNuclMFDs = Lists.newArrayList();
            subPlusSupraSeismoNuclCmlMFDs = Lists.newArrayList();
            subPlusSupraSeismoNuclMFDs.add(subPlusSupraSeismoNuclMFD);
            subPlusSupraSeismoNuclCmlMFDs.add(subPlusSupraSeismoNuclCmlMFD);

            // participation sum
            SummedMagFreqDist subPlusSupraSeismoParticMFD = new SummedMagFreqDist(
                    subSeismoMFD.getMinX(), subSeismoMFD.size(), subSeismoMFD.getDelta());
            subPlusSupraSeismoParticMFD.addIncrementalMagFreqDist(subSeismoMFD);
            subPlusSupraSeismoParticMFD.addIncrementalMagFreqDist(resizeToDimensions(
                    partMFD, subSeismoMFD.getMinX(), subSeismoMFD.size(), subSeismoMFD.getDelta()));
            EvenlyDiscretizedFunc subPlusSupraSeismoParticCmlMFD = subPlusSupraSeismoParticMFD.getCumRateDistWithOffset();
            subPlusSupraSeismoParticMFDs = Lists.newArrayList();
            subPlusSupraSeismoParticCmlMFDs = Lists.newArrayList();
            subPlusSupraSeismoParticMFDs.add(subPlusSupraSeismoParticMFD);
            subPlusSupraSeismoParticCmlMFDs.add(subPlusSupraSeismoParticCmlMFD);

            // write out all of the plots

            // nucleation
            // incremental
            writeParentSectMFDPlot(nuclIncrSubDir, nuclMFDs, subSeismoMFDs, subPlusSupraSeismoNuclMFDs, null,
                    parentName, true, false);
            // cumulative
            writeParentSectMFDPlot(nuclCmlSubDir, nuclCmlMFDs, subSeismoCmlMFDs, subPlusSupraSeismoNuclCmlMFDs, null,
                    parentName, true, true);
            // participation
            // incremental
            writeParentSectMFDPlot(particIncrSubDir, partMFDs, subSeismoMFDs, subPlusSupraSeismoParticMFDs, null,
                    parentName, false, false);
            // cumulative
            writeParentSectMFDPlot(particCmlSubDir, partCmlMFDs, subSeismoCmlMFDs, subPlusSupraSeismoParticCmlMFDs, null,
                    parentName, false, true);
        }
    }

    protected static void setFontSizes(HeadlessGraphPanel gp) {
        gp.setTickLabelFontSize(18);
        gp.setAxisLabelFontSize(20);
        gp.setPlotLabelFontSize(21);
        gp.setBackgroundColor(Color.WHITE);
    }

    protected static void writeParentSectMFDPlot(File dir,
                                                 List<? extends EvenlyDiscretizedFunc> supraSeismoMFDs,
                                                 List<? extends EvenlyDiscretizedFunc> subSeismoMFDs,
                                                 List<? extends EvenlyDiscretizedFunc> subPlusSupraSeismoMFDs,
                                                 List<? extends EvenlyDiscretizedFunc> ucerf2MFDs,
                                                 String name, boolean nucleation, boolean cumulative) throws IOException {
        HeadlessGraphPanel gp = new HeadlessGraphPanel();
        setFontSizes(gp);
        gp.setYLog(true);
        gp.setRenderingOrder(DatasetRenderingOrder.FORWARD);

        ArrayList<DiscretizedFunc> funcs = Lists.newArrayList();
        ArrayList<PlotCurveCharacterstics> chars = Lists.newArrayList();

        EvenlyDiscretizedFunc mfd;
        if (supraSeismoMFDs.size() == 1) {
            mfd = supraSeismoMFDs.get(0);
            mfd.setName("Incremental MFD supra-seismogenic");
            funcs.add(mfd);
            chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLUE));
            
            if (subSeismoMFDs != null) {
            	mfd = subSeismoMFDs.get(0);
            	mfd.setName("Incremental MFD sub-seismogenic");
            	funcs.add(mfd);
                chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.GREEN));
            	
            	mfd = subPlusSupraSeismoMFDs.get(0);
            	mfd.setName("Incremental MFD sub + supra");
                funcs.add(mfd);
                chars.add(new PlotCurveCharacterstics(PlotLineType.DOTTED, 5f, Color.GRAY));
            }

        } else {
        	assert false;
            int numFractiles = supraSeismoMFDs.size() - 3;
            mfd = supraSeismoMFDs.get(supraSeismoMFDs.size() - 3);
            funcs.addAll(supraSeismoMFDs);
            // used to be: new Color(0, 126, 255)
            chars.addAll(CompoundFSSPlots.getFractileChars(Color.BLUE, Color.MAGENTA, numFractiles));
            numFractiles = subSeismoMFDs.size() - 3;
            funcs.addAll(subSeismoMFDs);
            chars.addAll(CompoundFSSPlots.getFractileChars(Color.CYAN, Color.MAGENTA, numFractiles));
            funcs.addAll(subPlusSupraSeismoMFDs);
            chars.addAll(CompoundFSSPlots.getFractileChars(Color.BLACK, Color.MAGENTA, numFractiles));
        }

//        if (ucerf2MFDs != null) {
////			Color lightRed = new Color (255, 128, 128);
//
//            for (EvenlyDiscretizedFunc ucerf2MFD : ucerf2MFDs)
//                ucerf2MFD.setName("UCERF2 " + ucerf2MFD.getName());
//            EvenlyDiscretizedFunc meanMFD = ucerf2MFDs.get(0);
//            funcs.add(meanMFD);
//            chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, Color.RED));
//            EvenlyDiscretizedFunc minMFD = ucerf2MFDs.get(1);
//            funcs.add(minMFD);
//            chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.RED));
//            EvenlyDiscretizedFunc maxMFD = ucerf2MFDs.get(2);
//            funcs.add(maxMFD);
//            chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.RED));
//        }

        double minX = mfd.getMinX();
        if (minX < 5)
            minX = 5;
        gp.setUserBounds(minX, mfd.getMaxX(),
                1e-10, 1e-1);
        String yAxisLabel;

        String fname = name.replaceAll("\\W+", "_");

        if (cumulative)
            fname += "_cumulative";

        if (nucleation) {
            yAxisLabel = "Nucleation Rate";
            fname += "_nucleation";
        } else {
            yAxisLabel = "Participation Rate";
            fname += "_participation";
        }
        String title = name;
        yAxisLabel += " (per yr)";

        gp.setRenderingOrder(DatasetRenderingOrder.REVERSE);
        gp.drawGraphPanel("Magnitude", yAxisLabel, funcs, chars, title);

        File file = new File(dir, fname);
        gp.getChartPanel().setSize(1000, 800);
        gp.saveAsPDF(file.getAbsolutePath() + ".pdf");
        gp.saveAsPNG(file.getAbsolutePath() + ".png");
        gp.saveAsTXT(file.getAbsolutePath() + ".txt");
        File smallDir = new File(dir.getParentFile(), "small_MFD_plots");
        if (!smallDir.exists())
            smallDir.mkdir();
        file = new File(smallDir, fname + "_small");
        gp.getChartPanel().setSize(500, 400);
        gp.saveAsPDF(file.getAbsolutePath() + ".pdf");
        gp.saveAsPNG(file.getAbsolutePath() + ".png");
        gp.getChartPanel().setSize(1000, 800);
    }

    private static IncrementalMagFreqDist resizeToDimensions(
            IncrementalMagFreqDist mfd, double min, int num, double delta) {
        if (mfd.getMinX() == min && mfd.size() == num && mfd.getDelta() == delta)
            return mfd;
        IncrementalMagFreqDist resized = new IncrementalMagFreqDist(min, num, delta);

        for (int i = 0; i < mfd.size(); i++)
            if (mfd.getY(i) > 0)
                resized.set(mfd.get(i));

        return resized;
    }
}

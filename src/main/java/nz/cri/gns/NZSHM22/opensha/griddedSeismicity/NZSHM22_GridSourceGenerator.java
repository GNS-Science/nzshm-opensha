package nz.cri.gns.NZSHM22.opensha.griddedSeismicity;

import com.google.common.collect.Maps;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import nz.cri.gns.NZSHM22.opensha.data.region.NewZealandRegions;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_LogicTreeBranch;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_Regions;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_SpatialSeisPDF;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.commons.util.DataUtils;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionTargetMFDs;
import org.opensha.sha.earthquake.faultSysSolution.modules.MFDGridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.modules.PolygonFaultGridAssociations;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;
import org.opensha.sha.util.TectonicRegionType;
import scratch.UCERF3.griddedSeismicity.AbstractGridSourceProvider;

/**
 * This is copied from UCERF3_GridSourceGenerator and mostly only the constructor is modified. This
 * was the advice we got from Kevin.
 */
public class NZSHM22_GridSourceGenerator extends AbstractGridSourceProvider {

    protected static final GriddedRegion region = NewZealandRegions.NZ;

    private static double[] fracStrikeSlip, fracNormal, fracReverse;
    protected NZSHM22_LogicTreeBranch branch;
    protected PolygonFaultGridAssociations polyMgr;

    // spatial pdfs of seismicity, orginal and revised (reduced and
    // renormalized) to avoid double counting with fault polygons
    protected double[] srcSpatialPDF;
    private double[] revisedSpatialPDF;

    //	private double totalMgt5_Rate;

    // total off-fault MFD (sub-seismo + background)
    protected IncrementalMagFreqDist realOffFaultMFD;

    // the sub-seismogenic MFDs for those nodes that have them
    private Map<Integer, SummedMagFreqDist> nodeSubSeisMFDs;

    // the sub-seismogenic MFDs for each section
    private Map<Integer, IncrementalMagFreqDist> sectSubSeisMFDs;

    // reference mfd values
    protected double mfdMin = 5.05;
    protected double mfdMax = 8.45;
    protected int mfdNum = 35;

    /**
     * Options:
     *
     * <p>1) set a-values in fault-section polygons from moment-rate reduction or from smoothed
     * seismicity
     *
     * <p>2) focal mechanism options, and finite vs point sources (cross hair, random strike, etc)?
     *
     * @param ifss {@code InversionFaultSystemSolution} for which gridded/background sources should
     *     be generated
     */
    public NZSHM22_GridSourceGenerator(FaultSystemSolution ifss) {
        branch = ifss.getRupSet().getModule(NZSHM22_LogicTreeBranch.class);
        NZSHM22_SpatialSeisPDF spatialSeisPDF = branch.getValue(NZSHM22_SpatialSeisPDF.class);

        spatialSeisPDF.normaliseRegion(branch.getValue(NZSHM22_Regions.class).getTvzRegion());
        spatialSeisPDF.normaliseRegion(branch.getValue(NZSHM22_Regions.class).getSansTvzRegion());
        srcSpatialPDF = spatialSeisPDF.getPDF(region);

        //		totalMgt5_Rate = branch.getValue(TotalMag5Rate.class).getRateMag5();
        realOffFaultMFD =
                ifss.getRupSet()
                        .getModule(InversionTargetMFDs.class)
                        .getTrulyOffFaultMFD()
                        .deepClone();

        mfdMin = realOffFaultMFD.getMinX();
        mfdMax = realOffFaultMFD.getMaxX();
        mfdNum = realOffFaultMFD.size();

        polyMgr = ifss.getRupSet().getModule(PolygonFaultGridAssociations.class);

        System.out.println("   initSectionMFDs() ...");
        initSectionMFDs(ifss);
        System.out.println("   initNodeMFDs() ...");
        initNodeMFDs(ifss);
        System.out.println("   updateSpatialPDF() ...");
        updateSpatialPDF();
    }

    /*
     * Initialize the sub-seismogenic MFDs for each fault section (sectSubSeisMFDs)
     */
    protected void initSectionMFDs(FaultSystemSolution ifss) {

        List<? extends IncrementalMagFreqDist> subSeisMFD_list =
                ifss.getRupSet()
                        .getModule(InversionTargetMFDs.class)
                        .getOnFaultSubSeisMFDs()
                        .getAll();

        sectSubSeisMFDs = Maps.newHashMap();
        List<? extends FaultSection> faults = ifss.getRupSet().getFaultSectionDataList();
        for (int i = 0; i < faults.size(); i++) {
            sectSubSeisMFDs.put(faults.get(i).getSectionId(), subSeisMFD_list.get(i));
        }
    }

    /*
     * Initialize the sub-seismogenic MFDs for each grid node (nodeSubSeisMFDs) by
     * partitioning the sectSubSeisMFDs according to the overlapping fraction of
     * each fault section and grid node.
     */
    protected void initNodeMFDs(FaultSystemSolution ifss) {
        nodeSubSeisMFDs = Maps.newHashMap();
        for (FaultSection sect : ifss.getRupSet().getFaultSectionDataList()) {
            int id = sect.getSectionId();
            IncrementalMagFreqDist sectSubSeisMFD = sectSubSeisMFDs.get(id);
            Map<Integer, Double> nodeFractions = polyMgr.getNodeFractions(id);
            for (Integer nodeIdx : nodeFractions.keySet()) {
                SummedMagFreqDist nodeMFD = nodeSubSeisMFDs.get(nodeIdx);
                if (nodeMFD == null) {
                    nodeMFD = new SummedMagFreqDist(mfdMin, mfdMax, mfdNum);
                    nodeSubSeisMFDs.put(nodeIdx, nodeMFD);
                }
                double scale = nodeFractions.get(nodeIdx);
                IncrementalMagFreqDist scaledMFD = sectSubSeisMFD.deepClone();
                scaledMFD.scale(scale);
                nodeMFD.addIncrementalMagFreqDist(scaledMFD);
            }
        }
    }

    /*
     * Update (normalize) the spatial PDF to account for those nodes that are
     * partially of fully occupied by faults to whom all small magnitude events will
     * have been apportioned.
     */
    protected void updateSpatialPDF() {
        // update pdf
        revisedSpatialPDF = new double[srcSpatialPDF.length];
        for (int i = 0; i < region.getNodeCount(); i++) {
            double fraction = 1 - polyMgr.getNodeFraction(i);
            revisedSpatialPDF[i] = srcSpatialPDF[i] * fraction;
        }
        // normalize
        DataUtils.asWeights(revisedSpatialPDF);
    }

    /**
     * Returns the sub-seismogenic MFD associated with a section.
     *
     * @param idx sub section index
     * @return the MFD
     */
    public IncrementalMagFreqDist getSectSubSeisMFD(int idx) {
        return sectSubSeisMFDs.get(idx);
    }

    /**
     * Returns the sum of the sub-seismogenic MFDs of all fault sub-sections.
     *
     * @return the MFD
     */
    public IncrementalMagFreqDist getSectSubSeisMFD() {
        SummedMagFreqDist sum = new SummedMagFreqDist(mfdMin, mfdMax, mfdNum);
        sum.setName("Sub-seismogenic MFD for all fault sections");
        for (IncrementalMagFreqDist mfd : sectSubSeisMFDs.values()) {
            sum.addIncrementalMagFreqDist(mfd);
        }
        return sum;
    }

    /**
     * Returns the sum of the unassociated MFD of all nodes.
     *
     * @return the MFD
     */
    public IncrementalMagFreqDist getNodeUnassociatedMFD() {
        realOffFaultMFD.setInfo("Same as " + realOffFaultMFD.getName());
        realOffFaultMFD.setName("Unassociated MFD for all nodes");
        return realOffFaultMFD;
    }

    @Override
    public IncrementalMagFreqDist getMFD_SubSeisOnFault(int idx) {
        return nodeSubSeisMFDs.get(idx);
    }

    @Override
    public IncrementalMagFreqDist getMFD_Unassociated(int idx) {
        IncrementalMagFreqDist mfd = realOffFaultMFD.deepClone();
        mfd.scale(revisedSpatialPDF[idx]);
        return mfd;
    }

    @Override
    public GriddedRegion getGriddedRegion() {
        return region;
    }

    /**
     * Returns the sum of the sub-seismogenic MFD of all nodes.
     *
     * @return the MFD
     */
    public IncrementalMagFreqDist getNodeSubSeisMFD() {
        SummedMagFreqDist sum = new SummedMagFreqDist(mfdMin, mfdMax, mfdNum);
        sum.setName("Sub-seismogenic MFD for all nodes");
        for (IncrementalMagFreqDist mfd : nodeSubSeisMFDs.values()) {
            sum.addIncrementalMagFreqDist(mfd);
        }
        return sum;
    }

    //	/**
    //	 * Returns the MFD associated with a grid node, implied by the
    //	 * {@code spatialPDF} of seismicity and the {@code totalMgt5_Rate} supplied
    //	 * at initialization.
    //	 * @param inPoly {@code true} for MFD associated with fault polygons,
    //	 *        {@code false} if unassociated part requested
    //	 * @param idx node index
    //	 * @return the MFD
    //	 */
    //	public IncrementalMagFreqDist getSpatialMFD(boolean inPoly, int idx) {
    //		GutenbergRichterMagFreqDist mfd = new GutenbergRichterMagFreqDist(
    //			mfdMin, mfdMax, mfdNum);
    //		mfd.setAllButTotMoRate(mfdMin, mfdMax, totalMgt5_Rate, 0.8);
    //		double frac = polyMgr.getNodeFraction(idx);
    //		if (!inPoly) frac = 1 - frac;
    //		mfd.scale(frac);
    //		return mfd;
    //	}

    static void plot(ArrayList<IncrementalMagFreqDist> mfds) {
        GraphWindow graph = new GraphWindow(mfds, "GridSeis Test");
        graph.setX_AxisLabel("Magnitude");
        graph.setY_AxisLabel("Incremental Rate");
        graph.setYLog(true);
        graph.setY_AxisRange(1e-8, 1e2);
    }

    @Override
    public double getFracStrikeSlip(int idx) {
        checkInitFocalMechGrids();
        return fracStrikeSlip[idx];
    }

    @Override
    public double getFracReverse(int idx) {
        checkInitFocalMechGrids();
        return fracReverse[idx];
    }

    @Override
    public double getFracNormal(int idx) {
        checkInitFocalMechGrids();
        return fracNormal[idx];
    }

    @Override
    public MFDGridSourceProvider newInstance(
            Map<Integer, IncrementalMagFreqDist> nodeSubSeisMFDs,
            Map<Integer, IncrementalMagFreqDist> nodeUnassociatedMFDs,
            double[] fracStrikeSlip,
            double[] fracNormal,
            double[] fracReverse,
            TectonicRegionType[] trts) {
        throw new UnsupportedOperationException("Not implemented");
    }

    private static synchronized void checkInitFocalMechGrids() {
        if (fracStrikeSlip == null) {
            System.out.println("checkInitFocalMechGrids");
            NZSHM22_GriddedData data =
                    NZSHM22_GriddedData.fromFile("seismicityGrids/strikeFocalHazMech.grid");
            fracStrikeSlip = data.getValues(region);
        }
        if (fracReverse == null) {
            NZSHM22_GriddedData data =
                    NZSHM22_GriddedData.fromFile("seismicityGrids/reverseFocalMech.grid");
            fracReverse = data.getValues(region);
        }
        if (fracNormal == null) {
            NZSHM22_GriddedData data =
                    NZSHM22_GriddedData.fromFile("seismicityGrids/normalFocalMech.grid");
            fracNormal = data.getValues(region);
        }
    }
}

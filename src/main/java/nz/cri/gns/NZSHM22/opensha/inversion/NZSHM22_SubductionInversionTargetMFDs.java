package nz.cri.gns.NZSHM22.opensha.inversion;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.List;

import nz.earthsciences.jupyterlogger.CSVCell;
import nz.earthsciences.jupyterlogger.JupyterLogger;
import org.opensha.commons.data.uncertainty.UncertainIncrMagFreqDist;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;
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
 * <p>getTotalGriddedSeisMFD() returns:IncrementalMagFreqDist
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
public class NZSHM22_SubductionInversionTargetMFDs extends U3InversionTargetMFDs {

    // discretization parameters for MFDs
    public static final double MIN_MAG = 5.05; //
    public static final double MAX_MAG = 9.75;
    public static final int NUM_MAG = (int) ((MAX_MAG - MIN_MAG) * 10.0d);
    public static final double DELTA_MAG = 0.1;

    protected List<IncrementalMagFreqDist> mfdEqIneqConstraints = new ArrayList<>();
    protected List<UncertainIncrMagFreqDist> mfdUncertaintyConstraints = new ArrayList<>();

    protected List<IncrementalMagFreqDist> mfdConstraintComponents;

    public NZSHM22_SubductionInversionTargetMFDs(
            FaultSystemRupSet invRupSet,
            double totalRateM5,
            double bValue,
            double mfdTransitionMag,
            double mfdMinMag,
            double mfdUncertaintyWeightedConstraintWt,
            double mfdUncertaintyWeightedConstraintPower,
            double mfdUncertaintyWeightedConstraintScalar) {

        // make the total target GR MFD
        GutenbergRichterMagFreqDist totalTargetGR =
                new GutenbergRichterMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);

        // sorting out scaling
        double roundedMmaxOnFault =
                totalTargetGR.getX(totalTargetGR.getClosestXIndex(invRupSet.getMaxMag()));
        totalTargetGR.setAllButTotMoRate(
                MIN_MAG, roundedMmaxOnFault, totalRateM5, bValue); // TODO: revisit

        SummedMagFreqDist targetOnFaultSupraSeisMFD =
                new SummedMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);
        targetOnFaultSupraSeisMFD.addIncrementalMagFreqDist(totalTargetGR);
        targetOnFaultSupraSeisMFD.setName("targetOnFaultSupraSeisMFD");

        if (mfdUncertaintyWeightedConstraintWt > 0.0) {
            mfdUncertaintyConstraints.add(
                    MFDManipulation.addMfdUncertainty(
                            targetOnFaultSupraSeisMFD,
                            mfdUncertaintyWeightedConstraintPower,
                            mfdUncertaintyWeightedConstraintScalar));
        }

        setParent(invRupSet);

        // trim all MFDs to the magnitude range of interest
        Preconditions.checkState(
                mfdMinMag <= invRupSet.getMaxMag(),
                "mfdMinMag %s is above the rupture set max mag %s",
                mfdMinMag,
                invRupSet.getMaxMag());
        // keep the upper bin of the existing MFDs
        double maxMag = totalTargetGR.getMaxX();
        this.totalTargetGR = MFDManipulation.trimMFD(totalTargetGR, mfdMinMag, maxMag);
        this.targetOnFaultSupraSeisMFD =
                MFDManipulation.trimMFD(targetOnFaultSupraSeisMFD, mfdMinMag, maxMag);
        this.mfdEqIneqConstraints.add(this.targetOnFaultSupraSeisMFD);
        List<UncertainIncrMagFreqDist> trimmedUncertaintyConstraints = new ArrayList<>();
        for (UncertainIncrMagFreqDist mfd : mfdUncertaintyConstraints) {
            trimmedUncertaintyConstraints.add(MFDManipulation.trimMFD(mfd, mfdMinMag, maxMag));
        }
        this.mfdUncertaintyConstraints = trimmedUncertaintyConstraints;
        this.mfdConstraintComponents = List.of(this.targetOnFaultSupraSeisMFD);

        JupyterLogger.logger().addMarkDown("## Subduction MFDs");
        CSVCell csvCell =
                JupyterLogger.logger()
                        .addCSV("NZSHM22_SubductionInversionTargetMFDs_init", "magnitude")
                        .showTable(false);
        csvCell.setIndex(totalTargetGR.xValues());
        csvCell.addColumn("totalTargetGR.all", totalTargetGR.yValues());
        JupyterLogger.logger()
                .addLinePlot("NZSHM22_SubductionInversionTargetMFDs_init", csvCell)
                .setYLog();
    }

    public List<IncrementalMagFreqDist> getMfdEqIneqConstraints() {
        return mfdEqIneqConstraints;
    }

    public List<UncertainIncrMagFreqDist> getMfdUncertaintyConstraints() {
        return mfdUncertaintyConstraints;
    }

    @Override
    public List<IncrementalMagFreqDist> getMFD_Constraints() {
        List<IncrementalMagFreqDist> mfdConstraints = new ArrayList<>();
        mfdConstraints.addAll(getMfdEqIneqConstraints());
        mfdConstraints.addAll(getMfdUncertaintyConstraints());
        return mfdConstraints;
    }

    @Override
    public String getName() {
        return "NZSHM22 Subduction Inversion Target MFDs";
    }

    // only used for plots
    @Override
    public GutenbergRichterMagFreqDist getTotalTargetGR_NoCal() {
        throw new UnsupportedOperationException();
    }

    // only used for plots
    @Override
    public GutenbergRichterMagFreqDist getTotalTargetGR_SoCal() {
        throw new UnsupportedOperationException();
    }
}

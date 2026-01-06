package nz.cri.gns.NZSHM22.opensha.inversion;

import java.util.ArrayList;
import java.util.List;
import org.opensha.commons.data.uncertainty.UncertainIncrMagFreqDist;
import org.opensha.sha.faultSurface.FaultSection;
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

    static boolean MFD_STATS = true; // print some curves for analytics

    //	// discretization parameters for MFDs
    public static final double MIN_MAG = 5.05; //
    public static final double MAX_MAG = 9.75;
    public static final int NUM_MAG = (int) ((MAX_MAG - MIN_MAG) * 10.0d);
    public static final double DELTA_MAG = 0.1;

    // CBC NEW
    public static final double MINIMIZE_RATE_TARGET = 1.0e-20d;

    protected List<IncrementalMagFreqDist> mfdEqIneqConstraints = new ArrayList<>();
    protected List<UncertainIncrMagFreqDist> mfdUncertaintyConstraints = new ArrayList<>();
    ;

    protected List<IncrementalMagFreqDist> mfdConstraintComponents;

    public NZSHM22_SubductionInversionTargetMFDs(
            NZSHM22_InversionFaultSystemRuptSet invRupSet,
            double totalRateM5,
            double bValue,
            double mfdTransitionMag,
            double mfdMinMag,
            double mfdUncertaintyWeightedConstraintWt,
            double mfdUncertaintyWeightedConstraintPower,
            double mfdUncertaintyWeightedConstraintScalar) {

        // convert mMaxOffFault to bin center
        List<? extends FaultSection> faultSectionData = invRupSet.getFaultSectionDataList();

        // make the total target GR MFD
        GutenbergRichterMagFreqDist totalTargetGR =
                new GutenbergRichterMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);
        if (MFD_STATS) {
            System.out.println("totalTargetGR");
            System.out.println(totalTargetGR.toString());
            System.out.println("");
        }

        // sorting out scaling
        double roundedMmaxOnFault =
                totalTargetGR.getX(totalTargetGR.getClosestXIndex(invRupSet.getMaxMag()));
        totalTargetGR.setAllButTotMoRate(
                MIN_MAG, roundedMmaxOnFault, totalRateM5, bValue); // TODO: revisit

        if (MFD_STATS) {
            System.out.println("totalTargetGR after setAllButTotMoRate");
            System.out.println(totalTargetGR.toString());
            System.out.println("");
        }

        // Doctor the target, setting a small value instead of 0
        totalTargetGR.setYofX(
                (x, y) -> {
                    return (x < mfdMinMag) ? MINIMIZE_RATE_TARGET : y;
                });

        SummedMagFreqDist targetOnFaultSupraSeisMFD =
                new SummedMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);
        targetOnFaultSupraSeisMFD.addIncrementalMagFreqDist(totalTargetGR);

        if (MFD_STATS) {
            System.out.println("targetOnFaultSupraSeisMFD (SummedMagFreqDist)");
            System.out.println(targetOnFaultSupraSeisMFD.toString());
            System.out.println("");
        }

        //		// compute coupling coefficients
        //		impliedOnFaultCouplingCoeff = (targetOnFaultSupraSeisMFD.getTotalMomentRate()
        //				+ totalSubSeismoOnFaultMFD.getTotalMomentRate()) / origOnFltDefModMoRate;
        //		impliedTotalCouplingCoeff = totalTargetGR.getTotalMomentRate() / (origOnFltDefModMoRate
        // + offFltDefModMoRate);

        // Build the MFD Constraints for regions
        //		List<MFD_InversionConstraint> mfdUncertaintyConstraints = new ArrayList<>();

        if (mfdUncertaintyWeightedConstraintWt > 0.0) {
            mfdUncertaintyConstraints.add(
                    MFDManipulation.addMfdUncertainty(
                            targetOnFaultSupraSeisMFD,
                            mfdMinMag,
                            20,
                            mfdUncertaintyWeightedConstraintPower,
                            mfdUncertaintyWeightedConstraintScalar));
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
    public List<IncrementalMagFreqDist> getMFD_Constraints() {
        List<IncrementalMagFreqDist> mfdConstraints = new ArrayList<>();
        mfdConstraints.addAll(getMfdEqIneqConstraints());
        mfdConstraints.addAll(getMfdUncertaintyConstraints());
        return mfdConstraints;
    }

    public List<IncrementalMagFreqDist> getMFDConstraintComponents() {
        return mfdConstraintComponents;
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

package nz.cri.gns.NZSHM22.opensha.inversion;

import com.google.common.base.Preconditions;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.uncertainty.UncertainIncrMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

public class MFDManipulation {

    public static final double FIRST_WEIGHT_POWER_MAG = 7.0;

    public static boolean isBinCenter(IncrementalMagFreqDist mfd, double magnitude) {
        double nearest = mfd.getX(mfd.getClosestXIndex(magnitude));
        return Math.abs(nearest - magnitude) < 0.000001;
    }

    /**
     * This method returns the input MFD constraint restricted between minMag and maxMag.
     *
     */
    public static IncrementalMagFreqDist trimMFD(
            IncrementalMagFreqDist originalMFD, double minMag, double maxMag) {

        Preconditions.checkArgument(originalMFD.getMinX() <= minMag);
        Preconditions.checkArgument(maxMag <= originalMFD.getMaxX());
        Preconditions.checkArgument(isBinCenter(originalMFD, minMag));
        Preconditions.checkArgument(isBinCenter(originalMFD, maxMag));

        double delta = originalMFD.getDelta();
        int num = (int) Math.round((maxMag - minMag) / delta + 1.0);

        IncrementalMagFreqDist newMFD = new IncrementalMagFreqDist(minMag, maxMag, num);
        newMFD.setName(originalMFD.getName());
        newMFD.setTolerance(originalMFD.getTolerance());
        newMFD.setRegion(originalMFD.getRegion());

        int startBin = originalMFD.getClosestXIndex(minMag);
        for (int i = 0; i < num; i++) {
            newMFD.set(i, originalMFD.getY(i + startBin));
        }
        return newMFD;
    }

    /**
     * This method returns the input MFD constraint array with each constraint now restricted
     * between minMag and maxMag. WARNING! This doesn't interpolate. For best results, set minMag &
     * maxMag to points along original MFD constraint (i.e. 7.05, 7.15, etc)
     *
     * @param mfdConstraints
     * @param minMag
     * @param maxMag
     * @return newMFDConstraints
     */
    public static List<IncrementalMagFreqDist> trimMFDs(
            List<IncrementalMagFreqDist> mfdConstraints, double minMag, double maxMag) {

        List<IncrementalMagFreqDist> newMFDConstraints = new ArrayList<>();
        for (IncrementalMagFreqDist originalMFD : mfdConstraints) {
            newMFDConstraints.add(trimMFD(originalMFD, minMag, maxMag));
        }
        return newMFDConstraints;
    }

    public static UncertainIncrMagFreqDist addMfdUncertainty(
            IncrementalMagFreqDist mfd,
            double power,
            double uncertaintyScalar) {
        int firstWeightPowerBin = mfd.getClosestXIndex(FIRST_WEIGHT_POWER_MAG);
        double firstWeightPower =
                Math.pow(mfd.getY(firstWeightPowerBin), power - 1)
                        * (mfd.getY(firstWeightPowerBin) * uncertaintyScalar);
        EvenlyDiscretizedFunc stdDevs =
                new EvenlyDiscretizedFunc(mfd.getMinX(), mfd.getMaxX(), mfd.size());
        for (int i = 0; i < stdDevs.size(); i++) {
            double rate = mfd.getY(i);
            double stdDev = firstWeightPower / Math.pow(rate, power - 1);
            stdDevs.set(i, stdDev);
        }
        return new UncertainIncrMagFreqDist(mfd, stdDevs);
    }

    /**
     * Returns a copy of source with value in all bins below the bin that minMag falls in.
     *
     * @param source
     * @param minMag
     * @param value
     * @return
     */
    public static IncrementalMagFreqDist fillBelowMag(
            IncrementalMagFreqDist source, double minMag, double value) {
        IncrementalMagFreqDist result =
                new IncrementalMagFreqDist(source.getMinX(), source.size(), source.getDelta());
        int minMagBin = result.getClosestXIndex(minMag);
        for (int i = 0; i < source.size(); i++) {
            Point2D point = source.get(i);
            if (i < minMagBin) {
                result.set(i, value);
            } else {
                result.set(i, point.getY());
            }
        }
        return result;
    }

    /**
     * Returns a copy of source with value in all bins above the bin that maxMag falls in.
     *
     * @param source
     * @param maxMag
     * @param value
     * @return
     */
    public static IncrementalMagFreqDist fillAboveMag(
            IncrementalMagFreqDist source, double maxMag, double value) {
        IncrementalMagFreqDist result =
                new IncrementalMagFreqDist(source.getMinX(), source.size(), source.getDelta());
        int minMagBin = result.getClosestXIndex(maxMag);
        for (int i = 0; i < source.size(); i++) {
            Point2D point = source.get(i);
            if (i > minMagBin) {
                result.set(i, value);
            } else {
                result.set(i, point.getY());
            }
        }
        return result;
    }

    public static IncrementalMagFreqDist swapZeros(IncrementalMagFreqDist source, double value) {
        IncrementalMagFreqDist result =
                new IncrementalMagFreqDist(source.getMinX(), source.size(), source.getDelta());
        for (int i = 0; i < source.size(); i++) {
            if (source.getY(i) == 0) {
                result.set(i, value);
            } else {
                result.set(i, source.getY(i));
            }
        }
        return result;
    }
}

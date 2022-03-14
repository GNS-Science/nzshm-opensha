package nz.cri.gns.NZSHM22.opensha.inversion;

import com.google.common.base.Preconditions;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.uncertainty.UncertainIncrMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

public class MFDManipulation {

    public final static double FIRST_WEIGHT_POWER_MAG = 7.0;

    /**
     * This method returns the input MFD constraint
     * restricted between minMag and maxMag. WARNING! This doesn't interpolate. For
     * best results, set minMag & maxMag to points along original MFD constraint
     * (i.e. 7.05, 7.15, etc)
     * <p>
     * Can handle UncertainIncrMagFreqDist objects.
     **/
    public static IncrementalMagFreqDist restrictMFDConstraintMagRange(
            IncrementalMagFreqDist originalMFD, double minMag, double maxMag) {

        Preconditions.checkArgument(originalMFD.getMinX() <= minMag);
        Preconditions.checkArgument(maxMag <= originalMFD.getMaxX());

        double delta = originalMFD.getDelta();
        int num = (int) Math.round((maxMag - minMag) / delta + 1.0);

        IncrementalMagFreqDist newMFD = new IncrementalMagFreqDist(minMag, maxMag, num);
        newMFD.setTolerance(delta / 2.0);
        newMFD.setRegion(originalMFD.getRegion());

        for (int i = 0; i < num; i++) {

            double m = minMag + delta * i;
            // WARNING! This doesn't interpolate. For best results, set minMag & maxMag to
            // points along original MFD constraint (i.e. 7.05, 7.15, etc)
            newMFD.set(m, originalMFD.getClosestYtoX(m));
        }
        return newMFD;
    }

    /**
     * This method returns the input MFD constraint array with each constraint now
     * restricted between minMag and maxMag. WARNING! This doesn't interpolate. For
     * best results, set minMag & maxMag to points along original MFD constraint
     * (i.e. 7.05, 7.15, etc)
     * <p>
     * Can handle UncertainIncrMagFreqDist objects.
     *
     * @param mfdConstraints
     * @param minMag
     * @param maxMag
     * @return newMFDConstraints
     */
    public static List<IncrementalMagFreqDist> restrictMFDConstraintMagRange(
            List<IncrementalMagFreqDist> mfdConstraints, double minMag, double maxMag) {

        List<IncrementalMagFreqDist> newMFDConstraints = new ArrayList<>();
        for (IncrementalMagFreqDist originalMFD : mfdConstraints) {
            newMFDConstraints.add(restrictMFDConstraintMagRange(originalMFD, minMag, maxMag));
        }
        return newMFDConstraints;
    }

    public static UncertainIncrMagFreqDist addMfdUncertainty(IncrementalMagFreqDist mfd, double minimize_below_mag, double minimizeAboveMag, double power, double uncertaintyScalar) {
        int minMagBin = mfd.getClosestXIndex(minimize_below_mag);
        int maxMagBin = mfd.getClosestXIndex(minimizeAboveMag);
        int firstWeightPowerBin = mfd.getClosestXIndex(FIRST_WEIGHT_POWER_MAG);
        Preconditions.checkArgument(minMagBin <= firstWeightPowerBin,
                "minMag may not be above the bin of " + FIRST_WEIGHT_POWER_MAG);
        Preconditions.checkArgument( firstWeightPowerBin <= maxMagBin,
                "maxMag may not be below the bin of " + FIRST_WEIGHT_POWER_MAG);
        double firstWeightPower = Math.pow(mfd.getY(firstWeightPowerBin), power - 1) * (mfd.getY(firstWeightPowerBin) * uncertaintyScalar);
        EvenlyDiscretizedFunc stdDevs = new EvenlyDiscretizedFunc(mfd.getMinX(), mfd.getMaxX(), mfd.size());
        for (int i = 0; i < stdDevs.size(); i++) {
            double rate = mfd.getY(i);
            double stdDev = ((i < minMagBin) || (maxMagBin < i))? 1e-20 : firstWeightPower / Math.pow(rate, power - 1);
            stdDevs.set(i, stdDev);
        }
        return new UncertainIncrMagFreqDist(mfd, stdDevs);
    }

    /**
     * Returns a copy of source with value in all bins below the bin that minMag falls in.
     * @param source
     * @param minMag
     * @param value
     * @return
     */
    public static IncrementalMagFreqDist fillBelowMag(IncrementalMagFreqDist source, double minMag, double value) {
        IncrementalMagFreqDist result = new IncrementalMagFreqDist(source.getMinX(), source.size(), source.getDelta());
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
     * @param source
     * @param maxMag
     * @param value
     * @return
     */
    public static IncrementalMagFreqDist fillAboveMag(IncrementalMagFreqDist source, double maxMag, double value) {
        IncrementalMagFreqDist result = new IncrementalMagFreqDist(source.getMinX(), source.size(), source.getDelta());
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


    public static IncrementalMagFreqDist swapZeros(IncrementalMagFreqDist source, double value){
        IncrementalMagFreqDist result = new IncrementalMagFreqDist(source.getMinX(), source.size(), source.getDelta());
        for(int i =0; i < source.size(); i++){
            if(source.getY(i) == 0){
                result.set(i, value);
            } else{
                result.set(i, source.getY(i));
            }
        }
        return result;
    }
}

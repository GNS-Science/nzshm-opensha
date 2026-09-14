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

    /**
     * This method returns the input MFD constraint restricted between minMag and maxMag. WARNING!
     * This doesn't interpolate. For best results, set minMag & maxMag to points along original MFD
     * constraint (i.e. 7.05, 7.15, etc)
     *
     * <p>Can handle UncertainIncrMagFreqDist objects.
     */
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
     * This method returns the input MFD constraint array with each constraint now restricted
     * between minMag and maxMag. WARNING! This doesn't interpolate. For best results, set minMag &
     * maxMag to points along original MFD constraint (i.e. 7.05, 7.15, etc)
     *
     * <p>Can handle UncertainIncrMagFreqDist objects.
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

    /**
     * Wraps the MFD with standard deviations of the form
     *
     * <pre>stdDev(m) = uncertaintyScalar * rate(anchor)^power * rate(m)^(1 - power)</pre>
     *
     * for bins within [minimize_below_mag, minimizeAboveMag], and 1e-20 outside that range so those
     * bins are pinned hard to their (near zero) target.
     *
     * <p>The anchor magnitude only scales the whole stdDev curve uniformly - it enters the formula
     * solely as rate(anchor)^power - so it is interchangeable with uncertaintyScalar and cannot
     * change the shape of the curve. It exists to avoid anchoring on a bin whose rate is zero,
     * which would make the result infinite. {@link #FIRST_WEIGHT_POWER_MAG} suits the crustal
     * inversion, whose minMag sits just below it; partitions with a higher minMag (subduction)
     * anchor on their own first in-range bin instead.
     *
     * @param mfd the target MFD to add uncertainties to
     * @param minimize_below_mag bins below this magnitude are pinned to near zero
     * @param minimizeAboveMag bins above this magnitude are pinned to near zero
     * @param power 0 gives a constant relative uncertainty, 1 a constant absolute one
     * @param uncertaintyScalar the relative uncertainty at the anchor magnitude
     * @return the MFD wrapped with standard deviations
     */
    public static UncertainIncrMagFreqDist addMfdUncertainty(
            IncrementalMagFreqDist mfd,
            double minimize_below_mag,
            double minimizeAboveMag,
            double power,
            double uncertaintyScalar) {
        int minMagBin = mfd.getClosestXIndex(minimize_below_mag);
        int maxMagBin = mfd.getClosestXIndex(minimizeAboveMag);
        double anchorMag = Math.max(FIRST_WEIGHT_POWER_MAG, minimize_below_mag);
        int firstWeightPowerBin = mfd.getClosestXIndex(anchorMag);
        Preconditions.checkArgument(
                minMagBin <= firstWeightPowerBin,
                "minMag may not be above the bin of " + anchorMag);
        Preconditions.checkArgument(
                firstWeightPowerBin <= maxMagBin,
                "maxMag may not be below the bin of " + anchorMag);
        double firstWeightPower =
                Math.pow(mfd.getY(firstWeightPowerBin), power - 1)
                        * (mfd.getY(firstWeightPowerBin) * uncertaintyScalar);
        EvenlyDiscretizedFunc stdDevs =
                new EvenlyDiscretizedFunc(mfd.getMinX(), mfd.getMaxX(), mfd.size());
        for (int i = 0; i < stdDevs.size(); i++) {
            double rate = mfd.getY(i);
            // TODO remove (rate == 1e-20) condition when it's no longer needed
            double stdDev =
                    ((i < minMagBin) || (maxMagBin < i) || rate == 1e-20)
                            ? 1e-20
                            : firstWeightPower / Math.pow(rate, power - 1);
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

package nz.cri.gns.NZSHM22.opensha.inversion;

import com.google.common.base.Preconditions;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.uncertainty.UncertainIncrMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

public class MFDManipulation {

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

    public static UncertainIncrMagFreqDist addMfdUncertainty(IncrementalMagFreqDist mfd, double minimize_below_mag, double power) {
        double firstWeightPower = Math.pow(mfd.getClosestYtoX(minimize_below_mag), power);
        EvenlyDiscretizedFunc stdDevs = new EvenlyDiscretizedFunc(mfd.getMinX(), mfd.getMaxX(), mfd.size());
        for (int i = 0; i < stdDevs.size(); i++) {
            double mag = mfd.getX(i);
            double rate = mfd.getY(i);
            double stdDev =
                    (mag < minimize_below_mag) ? 1.0 // TODO: using the old system, this was 1, double check that 1 is correct for the new formula as well
                            // note: oakley thought it should be 0, but that is the only number that is actually forbidden.
                            // this is based on Kevin's math, transforming our old formula Math.pow(rate, power)/firstWeightPower to fit
                            // the new classes
                            : power / Math.pow(rate, firstWeightPower - 1);
            stdDevs.set(i, stdDev);
        }
        return new UncertainIncrMagFreqDist(mfd, stdDevs);
    }

    public static IncrementalMagFreqDist fillBelowMag(IncrementalMagFreqDist source, double minMag, double value) {
        IncrementalMagFreqDist result = new IncrementalMagFreqDist(source.getMinX(), source.size(), source.getDelta());
        for (int i = 0; i < source.size(); i++) {
            Point2D point = source.get(i);
            if (point.getX() < minMag) {
                result.set(i, value);
            } else {
                result.set(i, point.getY());
            }
        }
        return result;
    }
}

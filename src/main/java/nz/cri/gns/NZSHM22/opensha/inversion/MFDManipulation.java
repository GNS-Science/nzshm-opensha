package nz.cri.gns.NZSHM22.opensha.inversion;

import com.google.common.base.Preconditions;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.uncertainty.UncertainIncrMagFreqDist;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;
import scratch.UCERF3.inversion.U3InversionTargetMFDs;

/**
 * Creates and manipulates NZSHM MFDs. All NZSHM MFDs use the bins of {@link #MAG_BINS}, create new
 * MFDs with {@link #createIncrementalMFD(double, double)}, {@link #createSummedMFD(double, double)}
 * or {@link #createGutenbergRichterMFD(double, double)} so that bin boundaries line up.
 */
public class MFDManipulation {

    public static final double FIRST_WEIGHT_POWER_MAG = 7.0;

    /**
     * The magnitude bins of all NZSHM MFDs, and the bins that rupture magnitudes and magnitude
     * bounds are compared in. The same discretisation is used for crustal and subduction. The range
     * spans every sensible minimum and maximum magnitude, so anything below or above it is safely
     * outside any bound that can be configured.
     *
     * <p>Do not modify.
     */
    public static final IncrementalMagFreqDist MAG_BINS =
            new IncrementalMagFreqDist(0.05, 201, U3InversionTargetMFDs.DELTA_MAG);

    static {
        // the bins have to work for crustal and subduction, and line up with their MFD bins
        Preconditions.checkState(
                NZSHM22_CrustalInversionTargetMFDs.NZ_MIN_MAG
                        == NZSHM22_SubductionInversionTargetMFDs.MIN_MAG);
        double mfdMinMag = NZSHM22_CrustalInversionTargetMFDs.NZ_MIN_MAG;
        Preconditions.checkState(
                Math.abs(MAG_BINS.getX(MAG_BINS.getClosestXIndex(mfdMinMag)) - mfdMinMag) < 1e-9,
                "the magnitude bins do not line up with the MFD bins");
    }

    /**
     * Returns true if the specified magnitude is at least the same bin as minMag and at most the
     * same bin as maxMag.
     *
     * @param minMag the lower bound
     * @param maxMag the upper bound
     * @param mag the magnitude to test
     * @return whether the magnitude is within the bounds
     */
    public static boolean isWithinBounds(double minMag, double maxMag, double mag) {
        int bin = MAG_BINS.getClosestXIndex(mag);
        return (bin >= MAG_BINS.getClosestXIndex(minMag)
                && bin <= MAG_BINS.getClosestXIndex(maxMag));
    }

    /**
     * Creates an empty MFD with the {@link #MAG_BINS} bins from the bin containing minMag to the
     * bin containing maxMag.
     *
     * @param minMag a magnitude in the first bin
     * @param maxMag a magnitude in the last bin
     * @return the MFD
     */
    public static IncrementalMagFreqDist createIncrementalMFD(double minMag, double maxMag) {
        int minBin = MAG_BINS.getClosestXIndex(minMag);
        int maxBin = MAG_BINS.getClosestXIndex(maxMag);
        Preconditions.checkArgument(
                minBin <= maxBin, "minMag %s is above maxMag %s", minMag, maxMag);
        return new IncrementalMagFreqDist(
                MAG_BINS.getX(minBin), maxBin - minBin + 1, MAG_BINS.getDelta());
    }

    /**
     * Creates an empty summed MFD with the {@link #MAG_BINS} bins from the bin containing minMag to
     * the bin containing maxMag.
     *
     * @param minMag a magnitude in the first bin
     * @param maxMag a magnitude in the last bin
     * @return the MFD
     */
    public static SummedMagFreqDist createSummedMFD(double minMag, double maxMag) {
        IncrementalMagFreqDist bins = createIncrementalMFD(minMag, maxMag);
        return new SummedMagFreqDist(bins.getMinX(), bins.size(), bins.getDelta());
    }

    /**
     * Creates an empty Gutenberg-Richter MFD with the {@link #MAG_BINS} bins from the bin
     * containing minMag to the bin containing maxMag.
     *
     * @param minMag a magnitude in the first bin
     * @param maxMag a magnitude in the last bin
     * @return the MFD
     */
    public static GutenbergRichterMagFreqDist createGutenbergRichterMFD(
            double minMag, double maxMag) {
        IncrementalMagFreqDist bins = createIncrementalMFD(minMag, maxMag);
        return new GutenbergRichterMagFreqDist(bins.getMinX(), bins.size(), bins.getDelta());
    }

    /**
     * Returns true iff the {@link #MAG_BINS} bin of the magnitude lies between the first and last
     * bin of the MFD. MFD constraints silently ignore ruptures above the last bin, so target MFDs
     * used for constraints must contain the rupture set max mag.
     *
     * @param mfd an MFD aligned with {@link #MAG_BINS}
     * @param magnitude a magnitude
     * @return whether the magnitude has a bin in the mfd
     */
    public static boolean containsMag(EvenlyDiscretizedFunc mfd, double magnitude) {
        return isWithinBounds(mfd.getMinX(), mfd.getMaxX(), magnitude);
    }

    /**
     * Returns the y values of mfd aligned to the x values of reference. Bins of reference that are
     * not in mfd are NaN. Useful for logging trimmed MFDs alongside full-range MFDs.
     *
     * @param mfd the MFD to take y values from
     * @param reference the function providing the x values
     * @return one y value per x value of reference
     */
    public static List<Double> alignYValues(
            EvenlyDiscretizedFunc mfd, EvenlyDiscretizedFunc reference) {
        List<Double> result = new ArrayList<>();
        for (int i = 0; i < reference.size(); i++) {
            double x = reference.getX(i);
            result.add(containsMag(mfd, x) ? mfd.getY(mfd.getClosestXIndex(x)) : Double.NaN);
        }
        return result;
    }

    /**
     * Returns the input MFD restricted between minMag and maxMag. minMag and maxMag are snapped to
     * the centres of the bins that contain them.
     *
     * @param originalMFD the MFD to trim
     * @param minMag the new minimum magnitude
     * @param maxMag the new maximum magnitude
     * @return the trimmed MFD
     */
    public static IncrementalMagFreqDist trimMFD(
            IncrementalMagFreqDist originalMFD, double minMag, double maxMag) {
        int[] bins = trimBins(originalMFD, minMag, maxMag);
        int startBin = bins[0];
        int endBin = bins[1];

        int num = endBin - startBin + 1;
        IncrementalMagFreqDist newMFD =
                new IncrementalMagFreqDist(
                        originalMFD.getX(startBin), originalMFD.getX(endBin), num);
        copyMetadata(originalMFD, newMFD);

        for (int i = 0; i < num; i++) {
            newMFD.set(i, originalMFD.getY(i + startBin));
        }
        return newMFD;
    }

    /**
     * Returns the first and last bin index of the range between minMag and maxMag. minMag and
     * maxMag are snapped to the centres of the bins that contain them. A maxMag above the MFD is
     * clamped to the last bin.
     *
     * @param originalMFD the MFD to trim
     * @param minMag the new minimum magnitude
     * @param maxMag the new maximum magnitude
     * @return an array of the start bin and end bin, both inclusive
     */
    protected static int[] trimBins(
            IncrementalMagFreqDist originalMFD, double minMag, double maxMag) {
        Preconditions.checkArgument(
                MAG_BINS.getClosestXIndex(minMag)
                        >= MAG_BINS.getClosestXIndex(originalMFD.getMinX()),
                "minMag %s is below the MFD",
                minMag);
        maxMag = Math.min(maxMag, originalMFD.getMaxX());
        int startBin = originalMFD.getClosestXIndex(minMag);
        int endBin = originalMFD.getClosestXIndex(maxMag);
        Preconditions.checkArgument(
                startBin <= endBin, "minMag %s is above maxMag %s", minMag, maxMag);
        return new int[] {startBin, endBin};
    }

    /** Copies name, tolerance and region from one MFD to another. */
    protected static void copyMetadata(IncrementalMagFreqDist from, IncrementalMagFreqDist to) {
        to.setName(from.getName());
        to.setTolerance(from.getTolerance());
        to.setRegion(from.getRegion());
    }

    /**
     * Returns the input GR MFD restricted between minMag and maxMag. minMag and maxMag are snapped
     * to the centres of the bins that contain them. magLower and magUpper are clipped to the new
     * range. If the GR's non-zero range lies outside the new range, all rates are zero.
     *
     * @param originalMFD the MFD to trim
     * @param minMag the new minimum magnitude
     * @param maxMag the new maximum magnitude
     * @return the trimmed MFD
     */
    public static GutenbergRichterMagFreqDist trimMFD(
            GutenbergRichterMagFreqDist originalMFD, double minMag, double maxMag) {
        int[] bins = trimBins(originalMFD, minMag, maxMag);
        int startBin = bins[0];
        int num = bins[1] - startBin + 1;
        GutenbergRichterMagFreqDist newMFD =
                new GutenbergRichterMagFreqDist(
                        originalMFD.getX(startBin), num, originalMFD.getDelta());
        copyMetadata(originalMFD, newMFD);

        double magLower = Math.max(originalMFD.getMagLower(), newMFD.getMinX());
        double magUpper = Math.min(originalMFD.getMagUpper(), newMFD.getMaxX());
        if (magLower <= magUpper) {
            // Setting magLower, magUpper, and b.
            // The rates will be overwritten in the next step
            newMFD.setAllButTotMoRate(magLower, magUpper, 1, originalMFD.get_bValue());
            // copy exact rates to avoid rounding differences
            for (int i = 0; i < num; i++) {
                newMFD.set(i, originalMFD.getY(i + startBin));
            }
        }
        return newMFD;
    }

    /**
     * Returns the input summed MFD restricted between minMag and maxMag. minMag and maxMag are
     * snapped to the centres of the bins that contain them. The result contains a single summed
     * component with the trimmed rates.
     *
     * @param originalMFD the MFD to trim
     * @param minMag the new minimum magnitude
     * @param maxMag the new maximum magnitude
     * @return the trimmed MFD
     */
    public static SummedMagFreqDist trimMFD(
            SummedMagFreqDist originalMFD, double minMag, double maxMag) {
        IncrementalMagFreqDist trimmed =
                trimMFD((IncrementalMagFreqDist) originalMFD, minMag, maxMag);
        SummedMagFreqDist newMFD =
                new SummedMagFreqDist(trimmed.getMinX(), trimmed.size(), trimmed.getDelta());
        newMFD.addIncrementalMagFreqDist(trimmed);
        copyMetadata(originalMFD, newMFD);
        return newMFD;
    }

    /**
     * Returns the input uncertain MFD restricted between minMag and maxMag, including its standard
     * deviations. minMag and maxMag are snapped to the centres of the bins that contain them.
     *
     * @param originalMFD the MFD to trim
     * @param minMag the new minimum magnitude
     * @param maxMag the new maximum magnitude
     * @return the trimmed MFD
     */
    public static UncertainIncrMagFreqDist trimMFD(
            UncertainIncrMagFreqDist originalMFD, double minMag, double maxMag) {
        IncrementalMagFreqDist trimmed =
                trimMFD((IncrementalMagFreqDist) originalMFD, minMag, maxMag);
        EvenlyDiscretizedFunc originalStdDevs = originalMFD.getStdDevs();
        EvenlyDiscretizedFunc stdDevs =
                new EvenlyDiscretizedFunc(trimmed.getMinX(), trimmed.size(), trimmed.getDelta());
        int startBin = originalMFD.getClosestXIndex(trimmed.getMinX());
        for (int i = 0; i < stdDevs.size(); i++) {
            stdDevs.set(i, originalStdDevs.getY(i + startBin));
        }
        UncertainIncrMagFreqDist newMFD = new UncertainIncrMagFreqDist(trimmed, stdDevs);
        copyMetadata(trimmed, newMFD);
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

    public static UncertainIncrMagFreqDist addMfdUncertainty(
            IncrementalMagFreqDist mfd, double power, double uncertaintyScalar) {

        Preconditions.checkArgument(
                containsMag(mfd, FIRST_WEIGHT_POWER_MAG),
                "MFD must contain magnitude " + FIRST_WEIGHT_POWER_MAG);

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

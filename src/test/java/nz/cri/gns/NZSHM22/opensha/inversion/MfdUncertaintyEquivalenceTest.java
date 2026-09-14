package nz.cri.gns.NZSHM22.opensha.inversion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.impl.SparseDoubleMatrix2D;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.mockito.Mockito;
import org.opensha.commons.data.uncertainty.UncertainIncrMagFreqDist;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.ConstraintWeightingType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.MFDInversionConstraint;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

/**
 * Characterises how a rate normalized MFD constraint compares to an uncertainty weighted one, which
 * matters because ReweightEvenFitSimulatedAnnealing only rebalances uncertainty weighted
 * constraints.
 *
 * <p>The two are not interchangeable across the whole magnitude range. Within the target range,
 * where {@link MFDManipulation#addMfdUncertainty} sets stdDev = uncertaintyScalar * rate, the rows
 * are identical once the weight is scaled by uncertaintyScalar. Outside it, stdDev is forced to
 * 1e-20, the same value as the pinned target rate, so those rows come out a factor of
 * uncertaintyScalar weaker instead. Switching a partition to uncertainty weighting therefore keeps
 * the MFD fit it expresses but relaxes how hard magnitudes outside the range are pinned to zero.
 */
public class MfdUncertaintyEquivalenceTest {

    public static final double MIN_MAG = 7.5;
    public static final int NUM_BINS = 47;
    public static final double PINNED_RATE = 1e-20;

    /** A subduction shaped target: pinned at 1e-20 below minMag, decaying above it. */
    public static IncrementalMagFreqDist subductionLikeDist() {
        IncrementalMagFreqDist dist = new IncrementalMagFreqDist(5.05, NUM_BINS, 0.1);
        int minBin = dist.getClosestXIndex(MIN_MAG);
        for (int i = 0; i < dist.size(); i++) {
            dist.set(i, i < minBin ? PINNED_RATE : 0.0087 * Math.pow(0.78, i - minBin));
        }
        return dist;
    }

    protected FaultSystemRupSet mockRupSet(IncrementalMagFreqDist dist) {
        int numRuptures = dist.size();
        FaultSystemRupSet rupSet = Mockito.mock(FaultSystemRupSet.class);
        Mockito.when(rupSet.getNumRuptures()).thenReturn(numRuptures);
        Mockito.when(rupSet.getMinMag()).thenReturn(dist.getMinX());
        Mockito.when(rupSet.getMaxMag()).thenReturn(dist.getMaxX());
        // one rupture per magnitude bin, so every bin gets a row with an entry in it
        for (int r = 0; r < numRuptures; r++) {
            Mockito.when(rupSet.getMagForRup(r)).thenReturn(dist.getX(r));
        }
        double[] allInside = new double[numRuptures];
        Arrays.fill(allInside, 1d);
        Mockito.when(rupSet.getFractRupsInsideRegion(Mockito.any(), Mockito.anyBoolean()))
                .thenReturn(allInside);
        return rupSet;
    }

    /** Encodes the constraint and returns one array per row, holding the A values then d. */
    protected double[][] encode(MFDInversionConstraint constraint, int numRuptures) {
        int rows = constraint.getNumRows();
        DoubleMatrix2D a = new SparseDoubleMatrix2D(rows, numRuptures);
        double[] d = new double[rows];
        constraint.encode(a, d, 0);
        double[][] encoded = new double[rows][numRuptures + 1];
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < numRuptures; col++) {
                encoded[row][col] = a.get(row, col);
            }
            encoded[row][numRuptures] = d[row];
        }
        return encoded;
    }

    /**
     * Encodes the same target both ways and returns the largest relative difference, separately for
     * rows inside the target magnitude range and for the pinned rows below it.
     *
     * @param power the uncertainty power
     * @param scalar the uncertainty scalar
     * @param normalizedWeight the weight given to the rate normalized constraint
     * @return the worst in range difference, then the worst pinned difference
     */
    protected double[] compare(double power, double scalar, double normalizedWeight) {
        IncrementalMagFreqDist dist = subductionLikeDist();
        UncertainIncrMagFreqDist uncertain =
                MFDManipulation.addMfdUncertainty(dist, MIN_MAG, 20, power, scalar);
        FaultSystemRupSet rupSet = mockRupSet(dist);

        double[][] normalized =
                encode(
                        new MFDInversionConstraint(
                                rupSet,
                                normalizedWeight,
                                false,
                                ConstraintWeightingType.NORMALIZED,
                                List.of(dist)),
                        dist.size());
        double[][] uncertaintyWeighted =
                encode(
                        new MFDInversionConstraint(
                                rupSet,
                                normalizedWeight * scalar,
                                false,
                                ConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY,
                                List.of(uncertain)),
                        dist.size());

        assertEquals(normalized.length, uncertaintyWeighted.length);
        double worstInRange = 0;
        double worstPinned = 0;
        for (int row = 0; row < normalized.length; row++) {
            // rupSet min mag is the MFD min mag here, so the row index is the bin index
            boolean pinned = dist.getY(row) == PINNED_RATE;
            for (int i = 0; i < normalized[row].length; i++) {
                double expected = normalized[row][i];
                double actual = uncertaintyWeighted[row][i];
                if (expected == 0d) {
                    assertEquals(0d, actual, 0d);
                    continue;
                }
                double diff = Math.abs(actual - expected) / Math.abs(expected);
                if (pinned) {
                    worstPinned = Math.max(worstPinned, diff);
                } else {
                    worstInRange = Math.max(worstInRange, diff);
                }
            }
        }
        return new double[] {worstInRange, worstPinned};
    }

    /** Within the target range at power 0, stdDev is exactly scalar * rate, so the rows match. */
    @Test
    public void inRangeRowsAreExactlyEquivalentAtPowerZero() {
        assertEquals(0d, compare(0.0, 0.1, 10000)[0], 1e-12);
    }

    /** The subduction partitions use power 0.001, equivalent in range to within a percent. */
    @Test
    public void inRangeRowsMatchToWithinOnePercentAtSubductionPower() {
        double worstInRange = compare(0.001, 0.1, 10000)[0];
        assertTrue("in range difference was " + worstInRange, worstInRange < 0.01);
        // a real, if tiny, difference rather than an accidental no-op
        assertTrue("in range difference was " + worstInRange, worstInRange > 0);
    }

    /**
     * The pinned rows below the target range do not follow the same equivalence: their stdDev is
     * forced to 1e-20 rather than scalar * rate, so they come out weaker by exactly scalar.
     */
    @Test
    public void pinnedRowsAreWeakerByTheUncertaintyScalar() {
        for (double scalar : new double[] {0.1, 0.4}) {
            double worstPinned = compare(0.0, scalar, 10000)[1];
            assertEquals("scalar " + scalar, 1 - scalar, worstPinned, 1e-12);
        }
    }
}

package nz.cri.gns.NZSHM22.opensha.inversion;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.opensha.commons.data.uncertainty.UncertainIncrMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

public class MFDManipulationTest {

    public static final int BINS = 40;

    public static List<Double> fillToBin(int magBin) {
        List<Double> result = new ArrayList<>();
        for (int i = 0; i < BINS; i++) {
            if (i < magBin) {
                result.add(Math.PI);
            } else {
                result.add((double) i);
            }
        }
        return result;
    }

    public static List<Double> fillAfterBin(int magBin) {
        List<Double> result = new ArrayList<>();
        for (int i = 0; i < BINS; i++) {
            if (i > magBin) {
                result.add(Math.PI);
            } else {
                result.add((double) i);
            }
        }
        return result;
    }

    public static IncrementalMagFreqDist fillBelowDist(double minMag, double fill) {
        IncrementalMagFreqDist dist = new IncrementalMagFreqDist(5.05, BINS, 0.1);
        for (int i = 0; i < BINS; i++) {
            dist.set(i, i);
        }
        return MFDManipulation.fillBelowMag(dist, minMag, fill);
    }

    public static IncrementalMagFreqDist fillAboveDist(double maxMag, double fill) {
        IncrementalMagFreqDist dist = new IncrementalMagFreqDist(5.05, BINS, 0.1);
        for (int i = 0; i < BINS; i++) {
            dist.set(i, i);
        }
        return MFDManipulation.fillAboveMag(dist, maxMag, fill);
    }

    public static List<Double> fillBelow(double minMag) {
        return fillBelowDist(minMag, Math.PI).yValues();
    }

    public static List<Double> fillAbove(double maxMag) {
        return fillAboveDist(maxMag, Math.PI).yValues();
    }

    @Test
    public void testFillBelowMag() {
        assertEquals(fillToBin(30), fillBelow(8.0));
        assertEquals(fillToBin(25), fillBelow(7.51));
        assertEquals(fillToBin(20), fillBelow(7.00));
        assertEquals(fillToBin(10), fillBelow(6.0));
        assertEquals(fillToBin(0), fillBelow(1.0));
    }

    @Test
    public void testFillAboveMag() {
        assertEquals(fillAfterBin(30), fillAbove(8.0));
        assertEquals(fillAfterBin(25), fillAbove(7.51));
        assertEquals(fillAfterBin(20), fillAbove(7.0));
        assertEquals(fillAfterBin(10), fillAbove(6.0));
        assertEquals(fillAfterBin(0), fillAbove(1.0));
    }

    @Test
    public void TestAddMfdUncertainty() {
        IncrementalMagFreqDist dist = new IncrementalMagFreqDist(5.05, BINS, 0.1);
        for (int i = 0; i < BINS; i++) {
            dist.set(i, i);
        }

        UncertainIncrMagFreqDist actual =
                MFDManipulation.addMfdUncertainty(dist, 5.1, 20.0, 0.5, 0.9);

        assertEquals(
                List.of(
                        1.0E-20,
                        4.024922359499621,
                        5.692099788303082,
                        6.971370023173351,
                        8.049844718999243,
                        9.0,
                        9.859006035092989,
                        10.648943609579307,
                        11.384199576606164,
                        12.074767078498864,
                        12.727922061357855,
                        13.349157276772193,
                        13.942740046346701,
                        14.512063946937388,
                        15.059880477613358,
                        15.588457268119893,
                        16.099689437998485,
                        16.595180023127195,
                        17.076299364909246,
                        17.544229820656135,
                        18.0,
                        18.444511378727274,
                        18.878559267062727,
                        19.302849530574495,
                        19.718012070185978,
                        20.124611797498105,
                        20.52315765178448,
                        20.91411006952005,
                        21.297887219158614,
                        21.67487024182613,
                        22.045407685048602,
                        22.409819276379718,
                        22.76839915321233,
                        23.12141864159723,
                        23.469128658729534,
                        23.811761799581316,
                        24.149534156997728,
                        24.482646915723798,
                        24.811287753762397,
                        25.135632078784095),
                actual.getStdDevs().yValues());

        assertEquals(dist.xValues(), actual.xValues());
        assertEquals(dist.yValues(), actual.yValues());

        // and now with minimize_below_mag set to something greater than 0

        dist = new IncrementalMagFreqDist(5.05, BINS, 0.1);
        for (int i = 0; i < BINS; i++) {
            dist.set(i, i);
        }

        actual = MFDManipulation.addMfdUncertainty(dist, 7.0, 20.0, 0.5, 0.9);

        assertEquals(
                List.of(
                        1.0E-20,
                        1.0E-20,
                        1.0E-20,
                        1.0E-20,
                        1.0E-20,
                        1.0E-20,
                        1.0E-20,
                        1.0E-20,
                        1.0E-20,
                        1.0E-20,
                        1.0E-20,
                        1.0E-20,
                        1.0E-20,
                        1.0E-20,
                        1.0E-20,
                        1.0E-20,
                        1.0E-20,
                        1.0E-20,
                        1.0E-20,
                        1.0E-20,
                        18.0,
                        18.444511378727274,
                        18.878559267062727,
                        19.302849530574495,
                        19.718012070185978,
                        20.124611797498105,
                        20.52315765178448,
                        20.91411006952005,
                        21.297887219158614,
                        21.67487024182613,
                        22.045407685048602,
                        22.409819276379718,
                        22.76839915321233,
                        23.12141864159723,
                        23.469128658729534,
                        23.811761799581316,
                        24.149534156997728,
                        24.482646915723798,
                        24.811287753762397,
                        25.135632078784095),
                actual.getStdDevs().yValues());

        assertEquals(dist.xValues(), actual.xValues());
        assertEquals(dist.yValues(), actual.yValues());
    }

    @Test
    public void combinedUncertaintyFillBelowTest() {
        IncrementalMagFreqDist filled = fillBelowDist(8, 0);
        UncertainIncrMagFreqDist actual =
                MFDManipulation.addMfdUncertainty(filled, 7.0, 20, 0.5, 0.4);
        int indexMinMag = filled.getClosestXIndex(7.0);

        assertTrue(
                "non-aligned minMag leads to NaN",
                Double.isNaN(actual.getStdDevs().getY(indexMinMag)));

        filled = fillBelowDist(7.0, 7);
        actual = MFDManipulation.addMfdUncertainty(filled, 7.0, 20, 0.5, 0.4);

        assertEquals(
                "formula always comes out to 0.4*rate at minMag",
                filled.getY(indexMinMag) * 0.4,
                actual.getStdDevs().getY(indexMinMag),
                0.00000001);
        // assertTrue("formula comes out to >1 at minMag+1", actual.getStdDevs().getY(indexMinMag +
        // 1) > 1);
    }

    @Test
    public void testRestrictMFDConstraintMagRange() {
        IncrementalMagFreqDist dist = new IncrementalMagFreqDist(5.05, BINS, 0.1);
        for (int i = 0; i < BINS; i++) {
            dist.set(i, i);
        }

        IncrementalMagFreqDist actual =
                MFDManipulation.restrictMFDConstraintMagRange(dist, dist.getMinX(), dist.getMaxX());
        assertEquals(dist.yValues(), actual.yValues());

        actual = MFDManipulation.restrictMFDConstraintMagRange(dist, 7, dist.getMaxX());
        assertEquals(
                List.of(
                        20.0, 21.0, 22.0, 23.0, 24.0, 25.0, 26.0, 27.0, 28.0, 29.0, 30.0, 31.0,
                        32.0, 33.0, 34.0, 35.0, 36.0, 37.0, 38.0, 39.0),
                actual.yValues());

        actual = MFDManipulation.restrictMFDConstraintMagRange(dist, 8, dist.getMaxX());
        assertEquals(
                List.of(30.0, 31.0, 32.0, 33.0, 34.0, 35.0, 36.0, 37.0, 38.0, 39.0),
                actual.yValues());

        actual = MFDManipulation.restrictMFDConstraintMagRange(dist, dist.getMinX(), 7);
        assertEquals(
                List.of(
                        0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0, 11.0, 12.0, 13.0,
                        14.0, 15.0, 16.0, 17.0, 18.0, 19.0, 20.0),
                actual.yValues());

        actual = MFDManipulation.restrictMFDConstraintMagRange(dist, 7, 8);
        assertEquals(
                List.of(20.0, 21.0, 22.0, 23.0, 24.0, 25.0, 26.0, 27.0, 28.0, 29.0, 30.0),
                actual.yValues());
    }

    @Test
    public void testSwapZeroes() {
        IncrementalMagFreqDist dist = new IncrementalMagFreqDist(5.05, BINS, 0.1);
        for (int i = 0; i < BINS; i++) {
            if (i % 3 == 0) {
                dist.set(i, 0);
            } else {
                dist.set(i, i + 1);
            }
        }
        IncrementalMagFreqDist expected = new IncrementalMagFreqDist(dist);
        for (int i = 0; i < BINS; i++) {
            if (i % 3 == 0) {
                expected.set(i, Math.PI);
            } else {
                expected.set(i, i + 1);
            }
        }

        IncrementalMagFreqDist actual = MFDManipulation.swapZeros(dist, Math.PI);
        assertEquals(expected, actual);
    }
}

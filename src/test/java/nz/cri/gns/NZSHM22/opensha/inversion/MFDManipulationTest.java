package nz.cri.gns.NZSHM22.opensha.inversion;

import org.junit.Test;
import org.opensha.commons.data.uncertainty.UncertainIncrMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class MFDManipulationTest {

    public final static int BINS = 40;

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

    public static IncrementalMagFreqDist fillBelowDist(double minMag, double fill) {
        IncrementalMagFreqDist dist = new IncrementalMagFreqDist(5.05, BINS, 0.1);
        for (int i = 0; i < BINS; i++) {
            dist.set(i, i);
        }
        return MFDManipulation.fillBelowMag(dist, minMag, fill);
    }

    public static List<Double> fillBelow(double minMag) {
        return fillBelowDist(minMag, Math.PI).yValues();
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
    public void TestAddMfdUncertainty() {
        IncrementalMagFreqDist dist = new IncrementalMagFreqDist(5.05, BINS, 0.1);
        for (int i = 0; i < BINS; i++) {
            dist.set(i, i);
        }

        UncertainIncrMagFreqDist actual = MFDManipulation.addMfdUncertainty(dist, 5.1, 0.5);

        assertEquals(List.of(1.0, 1.0, 1.414213562373095, 1.7320508075688774, 2.0, 2.23606797749979, 2.449489742783178, 2.6457513110645903, 2.82842712474619, 3.0, 3.162277660168379, 3.3166247903554, 3.464101615137755, 3.605551275463989, 3.7416573867739413, 3.8729833462074166, 4.0, 4.123105625617661, 4.242640687119285, 4.358898943540673, 4.47213595499958, 4.58257569495584, 4.69041575982343, 4.795831523312719, 4.898979485566356, 5.0, 5.0990195135927845, 5.196152422706632, 5.2915026221291805, 5.385164807134504, 5.477225575051661, 5.5677643628300215, 5.65685424949238, 5.744562646538029, 5.8309518948453, 5.916079783099616, 6.0, 6.082762530298219, 6.164414002968976, 6.244997998398398),
                actual.getStdDevs().yValues());

        assertEquals(dist.xValues(), actual.xValues());
        assertEquals(dist.yValues(), actual.yValues());

        // and now with minimize_below_mag set to something greater than 0

        dist = new IncrementalMagFreqDist(5.05, BINS, 0.1);
        for (int i = 0; i < BINS; i++) {
            dist.set(i, i);
        }

        actual = MFDManipulation.addMfdUncertainty(dist, 7.05, 0.5);

        assertEquals(List.of(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0246950765959597, 1.0488088481701514, 1.0723805294763609, 1.0954451150103321, 1.1180339887498947, 1.1401754250991378, 1.1618950038622249, 1.1832159566199232, 1.2041594578792296, 1.2247448713915892, 1.2449899597988732, 1.2649110640673515, 1.2845232578665127, 1.3038404810405297, 1.3228756555322954, 1.3416407864998738, 1.3601470508735443, 1.378404875209022, 1.396424004376894),
                actual.getStdDevs().yValues());

        assertEquals(dist.xValues(), actual.xValues());
        assertEquals(dist.yValues(), actual.yValues());
    }

    public static void testUncertaintyalignment(double minMag) {
        IncrementalMagFreqDist filled = fillBelowDist(minMag, 0);
        UncertainIncrMagFreqDist actual = MFDManipulation.addMfdUncertainty(filled, minMag - 1, 0.5);
        int indexMinMag = filled.getClosestXIndex(minMag);

        assertTrue("non-aligned minMag leads to infinity", Double.isInfinite(actual.getStdDevs().getY(indexMinMag)));

        filled = fillBelowDist(minMag, 7);
        actual = MFDManipulation.addMfdUncertainty(filled, minMag, 0.5);

        assertEquals("formula always comes out to 1 at minMag", 1, actual.getStdDevs().getY(indexMinMag), 0.00000001);
        assertTrue("formula comes out to >1 at minMag+1", actual.getStdDevs().getY(indexMinMag + 1) > 1);
    }

    @Test
    public void combinedUncertaintyFillBelowTest() {
        // tests the alignment of fillBelowMag and addMfdUncertainty
        testUncertaintyalignment(6.95);
        testUncertaintyalignment(6.99);
        testUncertaintyalignment(7.0);
        testUncertaintyalignment(7.06);
    }

    @Test
    public void testRestrictMFDConstraintMagRange() {
        IncrementalMagFreqDist dist = new IncrementalMagFreqDist(5.05, BINS, 0.1);
        for (int i = 0; i < BINS; i++) {
            dist.set(i, i);
        }

        IncrementalMagFreqDist actual = MFDManipulation.restrictMFDConstraintMagRange(dist, dist.getMinX(), dist.getMaxX());
        assertEquals(dist.yValues(), actual.yValues());

        actual = MFDManipulation.restrictMFDConstraintMagRange(dist, 7, dist.getMaxX());
        assertEquals(List.of(20.0, 21.0, 22.0, 23.0, 24.0, 25.0, 26.0, 27.0, 28.0, 29.0, 30.0, 31.0, 32.0, 33.0, 34.0, 35.0, 36.0, 37.0, 38.0, 39.0), actual.yValues());

        actual = MFDManipulation.restrictMFDConstraintMagRange(dist, 8, dist.getMaxX());
        assertEquals(List.of(30.0, 31.0, 32.0, 33.0, 34.0, 35.0, 36.0, 37.0, 38.0, 39.0), actual.yValues());

        actual = MFDManipulation.restrictMFDConstraintMagRange(dist, dist.getMinX(), 7);
        assertEquals(List.of(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0, 11.0, 12.0, 13.0, 14.0, 15.0, 16.0, 17.0, 18.0, 19.0, 20.0), actual.yValues());

        actual = MFDManipulation.restrictMFDConstraintMagRange(dist, 7, 8);
        assertEquals(List.of(20.0, 21.0, 22.0, 23.0, 24.0, 25.0, 26.0, 27.0, 28.0, 29.0, 30.0), actual.yValues());
    }

}

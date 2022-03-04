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
    public void testFillAboveMag(){
        assertEquals(fillAfterBin(30), fillAbove(8.0));
        assertEquals(fillAfterBin(25), fillAbove(7.51));
        assertEquals(fillAfterBin(20), fillAbove(7.0));
        assertEquals(fillAfterBin(10), fillAbove(6.0));
        assertEquals(fillAfterBin(0), fillAbove(1.0));
    }

//    @Test
//    public void TestAddMfdUncertainty() {
//        IncrementalMagFreqDist dist = new IncrementalMagFreqDist(5.05, BINS, 0.1);
//        for (int i = 0; i < BINS; i++) {
//            dist.set(i, i);
//        }
//
//        UncertainIncrMagFreqDist actual = MFDManipulation.addMfdUncertainty(dist, 5.1, 20.0, 0.5);
//
//        assertEquals(List.of(1.0, 0.22360679774997896, 0.3162277660168379, 0.3872983346207417, 0.4472135954999579, 0.5, 0.5477225575051661, 0.5916079783099616, 0.6324555320336758, 0.6708203932499369, 0.7071067811865475, 0.7416198487095662, 0.7745966692414834, 0.8062257748298549, 0.8366600265340755, 0.8660254037844385, 0.8944271909999159, 0.9219544457292888, 0.9486832980505138, 0.9746794344808963, 1.0, 1.0246950765959597, 1.0488088481701514, 1.0723805294763609, 1.0954451150103321, 1.1180339887498947, 1.1401754250991378, 1.1618950038622249, 1.1832159566199232, 1.2041594578792296, 1.2247448713915892, 1.2449899597988732, 1.2649110640673515, 1.2845232578665127, 1.3038404810405297, 1.3228756555322954, 1.3416407864998738, 1.3601470508735443, 1.378404875209022, 1.396424004376894),
//                actual.getStdDevs().yValues());
//
//        assertEquals(dist.xValues(), actual.xValues());
//        assertEquals(dist.yValues(), actual.yValues());
//
//        // and now with minimize_below_mag set to something greater than 0
//
//        dist = new IncrementalMagFreqDist(5.05, BINS, 0.1);
//        for (int i = 0; i < BINS; i++) {
//            dist.set(i, i);
//        }
//
//        actual = MFDManipulation.addMfdUncertainty(dist, 7.0, 20.0,0.5);
//
//        assertEquals(List.of(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0246950765959597, 1.0488088481701514, 1.0723805294763609, 1.0954451150103321, 1.1180339887498947, 1.1401754250991378, 1.1618950038622249, 1.1832159566199232, 1.2041594578792296, 1.2247448713915892, 1.2449899597988732, 1.2649110640673515, 1.2845232578665127, 1.3038404810405297, 1.3228756555322954, 1.3416407864998738, 1.3601470508735443, 1.378404875209022, 1.396424004376894),
//                actual.getStdDevs().yValues());
//
//        assertEquals(dist.xValues(), actual.xValues());
//        assertEquals(dist.yValues(), actual.yValues());
//    }

    @Test
    public void combinedUncertaintyFillBelowTest() {
        IncrementalMagFreqDist filled = fillBelowDist(8, 0);
        UncertainIncrMagFreqDist actual = MFDManipulation.addMfdUncertainty(filled, 7.0, 20, 0.5, 0.4);
        int indexMinMag = filled.getClosestXIndex(7.0);

        assertTrue("non-aligned minMag leads to NaN", Double.isNaN(actual.getStdDevs().getY(indexMinMag)));

        filled = fillBelowDist(7.0, 7);
        actual = MFDManipulation.addMfdUncertainty(filled, 7.0, 20, 0.5, 0.4);

        assertEquals("formula always comes out to 0.4*rate at minMag", filled.getY(indexMinMag)*0.4, actual.getStdDevs().getY(indexMinMag), 0.00000001);
        //assertTrue("formula comes out to >1 at minMag+1", actual.getStdDevs().getY(indexMinMag + 1) > 1);
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

//    @Test
//    public void testSwapZeroes(){
//        IncrementalMagFreqDist dist = new IncrementalMagFreqDist(5.05, BINS, 0.1);
//        for (int i = 0; i < BINS; i++) {
//            if(i % 3 == 0){
//                dist.set(i, 0);
//            } else {
//                dist.set(i, i+1);
//            }
//        }
//        IncrementalMagFreqDist expected = new IncrementalMagFreqDist(dist);
//        for (int i = 0; i < BINS; i++) {
//            if(i % 3 == 0){
//                expected.set(i, Math.PI);
//            } else {
//                expected.set(i, i+1);
//            }
//        }
//
//        IncrementalMagFreqDist actual = MFDManipulation.swapZeros(dist, Math.PI);
//        assertEquals(expected, actual);
//    }

}

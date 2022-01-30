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

    public static List<Double> fillBelow(double minMag) {
        IncrementalMagFreqDist dist = new IncrementalMagFreqDist(5.05, BINS, 0.1);
        for (int i = 0; i < BINS; i++) {
            dist.set(i, i);
        }
        IncrementalMagFreqDist actual = MFDManipulation.fillBelowMag(dist, minMag, Math.PI);
        return actual.yValues();
    }

    @Test
    public void testFillBelowMag() {
        assertEquals(fillToBin(30), fillBelow(8.0));
        assertEquals(fillToBin(25), fillBelow(7.5));
        assertEquals(fillToBin(20), fillBelow(7.0));
        assertEquals(fillToBin(10), fillBelow(6.0));
        assertEquals(fillToBin(0), fillBelow(1.0));
    }

    @Test
    public void TestAddMfdUncertainty() {
        IncrementalMagFreqDist dist = new IncrementalMagFreqDist(5.05, BINS, 0.1);
        for (int i = 0; i < BINS; i++) {
            dist.set(i, i);
        }

        UncertainIncrMagFreqDist actual = MFDManipulation.addMfdUncertainty(dist, 0, 0.5);

        assertEquals(List.of(0.0, 0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 4.5, 5.0, 5.5, 6.0, 6.5, 7.0, 7.5, 8.0, 8.5, 9.0, 9.5, 10.0, 10.5, 11.0, 11.5, 12.0, 12.5, 13.0, 13.5, 14.0, 14.5, 15.0, 15.5, 16.0, 16.5, 17.0, 17.5, 18.0, 18.5, 19.0, 19.5),
                actual.getStdDevs().yValues());

        assertEquals(dist.xValues(), actual.xValues());
        assertEquals(dist.yValues(), actual.yValues());

        // and now with minimize_below_mag set to something greater than 0

        dist = new IncrementalMagFreqDist(5.05, BINS, 0.1);
        for (int i = 0; i < BINS; i++) {
            dist.set(i, i);
        }

        actual = MFDManipulation.addMfdUncertainty(dist, 7.05, 0.5);

        assertEquals(List.of(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.5192070875933064E-5, 1.2824630190365355E-5, 1.091178869736066E-5, 9.351167235996958E-6, 8.066570364426977E-6, 7.000551216718148E-6, 6.1092819320455375E-6, 5.358958749925345E-6, 4.723246061953934E-6, 4.181434857540702E-6, 3.7170994949010563E-6, 3.317106728640118E-6, 2.970876831667035E-6, 2.669827213594752E-6, 2.4069495757855783E-6, 2.176485762599499E-6, 1.9736772472998204E-6, 1.7945700420563068E-6, 1.635861673261172E-6, 1.4947803343775466E-6),
                actual.getStdDevs().yValues());

        assertEquals(dist.xValues(), actual.xValues());
        assertEquals(dist.yValues(), actual.yValues());
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

package nz.cri.gns.NZSHM22.opensha.inversion;

import org.junit.Test;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class NZSHM22_CrustalInversionTargetMFDsTest {

    public final static int BINS = 40;

    public static List<Double> fillToBin(int magBin) {
        List<Double> result = new ArrayList<>();
        for (int i = 0; i < BINS; i++) {
            if (i < magBin) {
                result.add(Math.PI);
            } else {
                result.add((double)i);
            }
        }
        return result;
    }

    public static List<Double> fillBelow(double minMag) {
        IncrementalMagFreqDist dist = new IncrementalMagFreqDist(5.05, BINS, 0.1);
        for(int i = 0; i < BINS; i++){
            dist.set(i, i);
        }
        IncrementalMagFreqDist actual = NZSHM22_CrustalInversionTargetMFDs.RegionalTargetMFDs.fillBelowMag(dist, minMag, Math.PI);
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
}

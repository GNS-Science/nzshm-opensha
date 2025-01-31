package nz.cri.gns.NZSHM22.opensha.inversion;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;

public class NZSHM22_SlipRateInversionConstraintBuilderTest {

    public static final double DELTA = 0.00000000000001;

    @Test
    public void testTranslate() {
        assertEquals(3, NZSHM22_SlipRateInversionConstraintBuilder.translate(3, 3, 4, 3, 4), DELTA);
        assertEquals(4, NZSHM22_SlipRateInversionConstraintBuilder.translate(4, 3, 4, 3, 4), DELTA);

        assertEquals(5, NZSHM22_SlipRateInversionConstraintBuilder.translate(3, 3, 4, 5, 6), DELTA);
        assertEquals(6, NZSHM22_SlipRateInversionConstraintBuilder.translate(4, 3, 4, 5, 6), DELTA);

        assertEquals(
                5.5, NZSHM22_SlipRateInversionConstraintBuilder.translate(3.5, 3, 4, 5, 6), DELTA);
    }

    @Test
    public void testGetMinMaxCOV() {
        assertArrayEquals(
                new double[] {2, 3},
                NZSHM22_SlipRateInversionConstraintBuilder.getMinMaxCOV(
                        new double[] {10, 20, 30}, new double[] {5, 8, 10}),
                DELTA);
    }

    @Test
    public void testCreateSectSlipRates() {
        FaultSystemRupSet rupSet = mock(FaultSystemRupSet.class);
        when(rupSet.getNumSections()).thenReturn(3);

        double[] slipRate = new double[] {2, 3, 4};
        double[] originalStdDevs = new double[] {0.5, 0, 0.3};

        SectSlipRates original = SectSlipRates.precomputed(rupSet, slipRate, originalStdDevs);

        SectSlipRates actual =
                NZSHM22_SlipRateInversionConstraintBuilder.createSectSlipRates(original, 2);

        assertArrayEquals(slipRate, actual.getSlipRates(), DELTA);
        assertArrayEquals(new double[] {1, 1, 0.01}, actual.getSlipRateStdDevs(), DELTA);
    }
}

package nz.cri.gns.NZSHM22.opensha.inversion;

import static org.junit.Assert.*;

import org.junit.Test;

/** Tests for the static helpers of {@link NZSHM22_AbstractInversionRunner}. */
public class NZSHM22_AbstractInversionRunnerTest {

    /** Asserts that the range is rejected and returns the message of the resulting exception. */
    protected String assertInvalid(double minMag, double maxMag) {
        try {
            NZSHM22_AbstractInversionRunner.validateMagnitudeRange(minMag, maxMag);
            fail("expected IllegalArgumentException for " + minMag + ".." + maxMag);
            return null;
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    @Test
    public void testValidRanges() {
        NZSHM22_AbstractInversionRunner.validateMagnitudeRange(5, 10);
        NZSHM22_AbstractInversionRunner.validateMagnitudeRange(5.0, 5.1);
        NZSHM22_AbstractInversionRunner.validateMagnitudeRange(6.3, 9.7);
        NZSHM22_AbstractInversionRunner.validateMagnitudeRange(7.9, 8.0);
        // values that are not exactly representable as doubles
        NZSHM22_AbstractInversionRunner.validateMagnitudeRange(5.7, 8.3);
    }

    @Test
    public void testMinMagTooSmall() {
        assertTrue(assertInvalid(4.9, 8).contains("minMag must be at least 5"));
        assertTrue(assertInvalid(0, 8).contains("minMag must be at least 5"));
        assertTrue(assertInvalid(-5, 8).contains("minMag must be at least 5"));
    }

    @Test
    public void testMaxMagTooLarge() {
        assertTrue(assertInvalid(5, 10.1).contains("maxMag must be at most 10"));
        assertTrue(assertInvalid(5, 20).contains("maxMag must be at most 10"));
    }

    @Test
    public void testMaxMagNotGreaterThanMinMag() {
        assertTrue(assertInvalid(8, 8).contains("maxMag must be greater than minMag"));
        assertTrue(assertInvalid(9, 8).contains("maxMag must be greater than minMag"));
    }

    @Test
    public void testMagsOffGrid() {
        assertTrue(assertInvalid(5.05, 8).contains("minMag must be a multiple of"));
        assertTrue(assertInvalid(6.123, 8).contains("minMag must be a multiple of"));
        assertTrue(assertInvalid(5, 8.05).contains("maxMag must be a multiple of"));
        assertTrue(assertInvalid(5, 9.99).contains("maxMag must be a multiple of"));
    }

    @Test
    public void testIsOnMagGrid() {
        assertTrue(NZSHM22_AbstractInversionRunner.isOnMagGrid(0));
        assertTrue(NZSHM22_AbstractInversionRunner.isOnMagGrid(5.1));
        assertTrue(NZSHM22_AbstractInversionRunner.isOnMagGrid(8.3));
        assertTrue(NZSHM22_AbstractInversionRunner.isOnMagGrid(9.9));
        assertFalse(NZSHM22_AbstractInversionRunner.isOnMagGrid(5.05));
        assertFalse(NZSHM22_AbstractInversionRunner.isOnMagGrid(5.01));
    }
}

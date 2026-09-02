package nz.cri.gns.NZSHM22.opensha.hazard.joint;

import static org.junit.Assert.*;

import java.util.List;
import org.junit.Test;

/**
 * Tests for {@link SectionWeighting}, the choice of how a rupture's contribution is shared among
 * the sections it runs over.
 */
public class SectionWeightingTest {

    /** Distances from a site to four sections: the second is right at it, the last is far away. */
    static final double[] DISTANCES = {20d, 0d, 50d, 400d};

    static final List<Integer> RUPTURE = List.of(0, 1, 2, 3);

    /** Participation credits every section in full, so the weights do not partition the rupture. */
    @Test
    public void testParticipation() {
        double[] weights = SectionWeighting.participation().weights(RUPTURE, DISTANCES);
        assertArrayEquals(new double[] {1d, 1d, 1d, 1d}, weights, 1e-12);
    }

    /** Nearest gives the whole rupture to the one section that set its distance to the site. */
    @Test
    public void testNearest() {
        double[] weights = SectionWeighting.nearest().weights(RUPTURE, DISTANCES);
        assertArrayEquals(new double[] {0d, 1d, 0d, 0d}, weights, 1e-12);
    }

    /** A single section rupture goes entirely to that section whatever the weighting. */
    @Test
    public void testNearestSingleSection() {
        assertArrayEquals(
                new double[] {1d},
                SectionWeighting.nearest().weights(List.of(2), DISTANCES),
                1e-12);
    }

    /**
     * Proximity partitions the rupture, orders the sections by closeness, and leaves the far end of
     * a long rupture with almost nothing.
     */
    @Test
    public void testProximity() {
        double[] weights = SectionWeighting.proximity().weights(RUPTURE, DISTANCES);

        double sum = 0;
        for (double weight : weights) {
            sum += weight;
        }
        assertEquals(1d, sum, 1e-12);

        assertTrue(weights[1] > weights[0]);
        assertTrue(weights[0] > weights[2]);
        assertTrue(weights[2] > weights[3]);
        // the section 400km away contributes well under a percent of the rupture
        assertTrue(weights[3] < 0.01);
    }

    /** The near field distance bounds how much a section the site sits on can take. */
    @Test
    public void testProximityNearFieldBound() {
        List<Integer> pair = List.of(0, 1);
        double[] wide = new SectionWeighting.Proximity(50d, 1.5d).weights(pair, DISTANCES);
        double[] tight = new SectionWeighting.Proximity(1d, 1.5d).weights(pair, DISTANCES);
        assertTrue(tight[1] > wide[1]);
    }

    /** The weightings label themselves, because the label is what a map legend says. */
    @Test
    public void testLabels() {
        assertNotNull(SectionWeighting.participation().getLabel());
        assertNotEquals(
                SectionWeighting.participation().getLabel(),
                SectionWeighting.proximity().getLabel());
    }
}

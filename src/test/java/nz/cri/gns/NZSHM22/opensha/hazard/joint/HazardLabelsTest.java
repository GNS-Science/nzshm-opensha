package nz.cri.gns.NZSHM22.opensha.hazard.joint;

import static org.junit.Assert.*;

import java.util.Locale;
import org.junit.After;
import org.junit.Test;

/** Tests for {@link HazardLabels}: how periods are labelled and how labels become file names. */
public class HazardLabelsTest {

    private final Locale defaultLocale = Locale.getDefault();

    @After
    public void restoreLocale() {
        Locale.setDefault(defaultLocale);
    }

    @Test
    public void testPeriodLabel() {
        assertEquals("PGA", HazardLabels.periodLabel(0d));
        assertEquals("PGV", HazardLabels.periodLabel(-1d));
        assertEquals("1s SA", HazardLabels.periodLabel(1d));
        // whole periods lose the decimal point, fractional ones keep it
        assertEquals("3s SA", HazardLabels.periodLabel(3.0));
        assertEquals("0.5s SA", HazardLabels.periodLabel(0.5));
    }

    @Test
    public void testPeriodLabelRejectsUnknownPeriods() {
        try {
            HazardLabels.periodLabel(-2d);
            fail("expected an unknown period to be rejected");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Unexpected period"));
        }
    }

    @Test
    public void testPeriodUnits() {
        assertEquals("g", HazardLabels.periodUnits(0d));
        assertEquals("g", HazardLabels.periodUnits(3d));
        assertEquals("cm/s", HazardLabels.periodUnits(-1d));
    }

    /**
     * File name fragments hold nothing that needs escaping, including a fractional period's dot.
     */
    @Test
    public void testPeriodPrefix() {
        assertEquals("pga", HazardLabels.periodPrefix(0d));
        assertEquals("pgv", HazardLabels.periodPrefix(-1d));
        assertEquals("3s_sa", HazardLabels.periodPrefix(3d));
        assertEquals("0_5s_sa", HazardLabels.periodPrefix(0.5));
    }

    @Test
    public void testSlug() {
        assertEquals("nzshm22", HazardLabels.slug("NZSHM22"));
        assertEquals("joint_inversion_v2", HazardLabels.slug("Joint Inversion (v2)"));
        // runs of punctuation collapse, and leading and trailing underscores are dropped
        assertEquals("two_in_50", HazardLabels.slug("TWO_IN_50"));
        assertEquals("a_b", HazardLabels.slug("  a -- b  "));
    }

    /**
     * Slugs name files, so they must not depend on the machine's locale. A default {@code
     * toLowerCase} under a Turkish locale turns "I" into a dotless "ı", which is then stripped as a
     * non-ascii character.
     */
    @Test
    public void testSlugIsLocaleIndependent() {
        String expected = HazardLabels.slug("INVERCARGILL");
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        assertEquals("invercargill", expected);
        assertEquals(expected, HazardLabels.slug("INVERCARGILL"));
    }
}

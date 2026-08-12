package nz.cri.gns.NZSHM22.opensha.ruptures;

import static org.junit.Assert.*;

import java.io.IOException;
import nz.cri.gns.NZSHM22.opensha.calc.SimplifiedScalingRelationship;
import nz.cri.gns.NZSHM22.opensha.calc.TMG2017CruScalingRelationship;
import nz.cri.gns.NZSHM22.opensha.util.NZSHM22_PythonGateway;
import nz.cri.gns.NZSHM22.opensha.util.ParameterRunner;
import nz.cri.gns.NZSHM22.opensha.util.Parameters;
import nz.cri.gns.NZSHM22.opensha.util.TestHelpers;
import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.RupSetScalingRelationship;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;

public class NZSHM22_RuptureSetBuilderModuleTest {

    public static NZSHM22_CoulombRuptureSetBuilder makeCoulombBuilder() throws IOException {
        NZSHM22_CoulombRuptureSetBuilder builder = new NZSHM22_CoulombRuptureSetBuilder();
        new ParameterRunner(Parameters.NZSHM22.RUPSET_CRUSTAL)
                .setUpCoulombCrustalRuptureSetBuilder(builder);
        return builder;
    }

    public static NZSHM22_SubductionRuptureSetBuilder makeSubductionBuilder() throws IOException {
        NZSHM22_SubductionRuptureSetBuilder builder = new NZSHM22_SubductionRuptureSetBuilder();
        new ParameterRunner(Parameters.NZSHM22.RUPSET_HIKURANGI)
                .setUpSubductionRuptureSetBuilder(builder);
        return builder;
    }

    static NZSHM22_AbstractRuptureSetBuilder roundTrip(NZSHM22_AbstractRuptureSetBuilder builder)
            throws IOException {
        NZSHM22_RuptureSetBuilderModule module =
                (NZSHM22_RuptureSetBuilderModule)
                        TestHelpers.serialiseDeserialise(
                                new NZSHM22_RuptureSetBuilderModule(builder));
        return module.getBuilder();
    }

    @Test
    public void roundTripsCoulombBuilder() throws IOException {
        NZSHM22_CoulombRuptureSetBuilder builder = makeCoulombBuilder();

        NZSHM22_AbstractRuptureSetBuilder actual = roundTrip(builder);

        assertTrue(actual instanceof NZSHM22_CoulombRuptureSetBuilder);
        assertEquals(builder.toJson(), actual.toJson());
        assertEquals(builder.getDescriptiveName(), actual.getDescriptiveName());
    }

    @Test
    public void roundTripsSubductionBuilder() throws IOException {
        NZSHM22_SubductionRuptureSetBuilder builder = makeSubductionBuilder();

        NZSHM22_AbstractRuptureSetBuilder actual = roundTrip(builder);

        assertTrue(actual instanceof NZSHM22_SubductionRuptureSetBuilder);
        assertEquals(builder.toJson(), actual.toJson());
        assertEquals(builder.getDescriptiveName(), actual.getDescriptiveName());
    }

    /**
     * The cached builders of the python gateway inherit fromJson from their parent, so they come
     * back as their parent class. They only add caching, no configuration.
     */
    @Test
    public void roundTripsCachedBuilderAsParent() throws IOException {
        NZSHM22_PythonGateway.NZSHM22_CachedCoulombRuptureSetBuilder builder =
                new NZSHM22_PythonGateway.NZSHM22_CachedCoulombRuptureSetBuilder();
        new ParameterRunner(Parameters.NZSHM22.RUPSET_CRUSTAL)
                .setUpCoulombCrustalRuptureSetBuilder(builder);

        NZSHM22_AbstractRuptureSetBuilder actual = roundTrip(builder);

        assertEquals(NZSHM22_CoulombRuptureSetBuilder.class, actual.getClass());
        assertEquals(builder.toJson(), actual.toJson());
    }

    void assertScalingRelationshipRoundTrips(RupSetScalingRelationship scalingRelationship)
            throws IOException {
        NZSHM22_CoulombRuptureSetBuilder builder = new NZSHM22_CoulombRuptureSetBuilder();
        builder.setScalingRelationship(scalingRelationship);

        RupSetScalingRelationship actual = roundTrip(builder).getScalingRelationship();

        assertEquals(scalingRelationship.getClass(), actual.getClass());
        assertEquals(scalingRelationship.getShortName(), actual.getShortName());
    }

    /**
     * The scaling relationship is declared as an interface, so unlike the inversion runner config
     * it only round trips because of an explicitly registered type adapter.
     */
    @Test
    public void roundTripsScalingRelationships() throws IOException {
        // the default, and everything the String setter can produce
        assertScalingRelationshipRoundTrips(ScalingRelationships.SHAW_2009_MOD);
        assertScalingRelationshipRoundTrips(new TMG2017CruScalingRelationship());

        SimplifiedScalingRelationship simplified = new SimplifiedScalingRelationship();
        simplified.setupCrustal(4.2, 4.2);
        assertScalingRelationshipRoundTrips(simplified);
    }

    @Test
    public void ephemeralStateIsNotSerialised() throws IOException {
        NZSHM22_CoulombRuptureSetBuilder builder = makeCoulombBuilder();
        String json = builder.toJson();

        assertFalse(json.contains("subSections"));
        assertFalse(json.contains("ruptures"));
        assertFalse(json.contains("plausibilityConfig"));
        assertFalse(json.contains("\"builder\""));
        assertFalse(json.contains("numThreads"));

        // numThreads depends on the machine we happen to run on, so it must not end up in the
        // config
        assertEquals(json, makeCoulombBuilder().setNumThreads(1).toJson());
    }
}

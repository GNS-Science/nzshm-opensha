package nz.cri.gns.NZSHM22.opensha.ruptures;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import nz.cri.gns.NZSHM22.opensha.faults.NZFaultSection;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.opensha.commons.geo.Location;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;

/** Tests that FaultFilters survive the serialisation of a rupture set builder config. */
public class FaultFilterSerialisationTest {

    @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

    static NZFaultSection section(int id, String domainNo, double slipRate, Location... trace) {
        NZFaultSection section = new NZFaultSection();
        section.setSectionId(id);
        section.setDomainNo(domainNo);
        section.setAveSlipRate(slipRate);
        FaultTrace faultTrace = new FaultTrace("trace");
        faultTrace.addAll(List.of(trace));
        section.setFaultTrace(faultTrace);
        return section;
    }

    /**
     * Round trips a builder that has the specified filter and returns the reconstituted filter.
     * Also asserts that the JSON itself round trips.
     */
    FaultFilter roundTrip(FaultFilter filter) throws IOException {
        NZSHM22_CoulombRuptureSetBuilder builder = new NZSHM22_CoulombRuptureSetBuilder();
        builder.faultFilters.add(filter);

        NZSHM22_CoulombRuptureSetBuilder actual =
                NZSHM22_CoulombRuptureSetBuilder.fromJson(builder.toJson());

        assertEquals(builder.toJson(), actual.toJson());
        assertEquals(1, actual.faultFilters.size());
        return actual.faultFilters.get(0);
    }

    void assertSameBehaviour(FaultFilter expected, FaultFilter actual, FaultSection... sections) {
        assertEquals(expected.getClass(), actual.getClass());
        assertEquals(expected.toString(), actual.toString());
        assertEquals(expected.toDescription(), actual.toDescription());
        for (FaultSection section : sections) {
            assertEquals(
                    "section " + section.getSectionId(),
                    expected.keep(section),
                    actual.keep(section));
        }
    }

    @Test
    public void roundTripsIdRangeFilter() throws IOException {
        FaultFilter filter = new FaultFilter.IdRangeFilter(3, 5);

        assertSameBehaviour(
                filter,
                roundTrip(filter),
                section(2, "1", 1),
                section(3, "1", 1),
                section(7, "1", 1),
                section(8, "1", 1));
    }

    @Test
    public void roundTripsDomainFilter() throws IOException {
        FaultFilter filter = new FaultFilter.DomainFilter("4, 7");

        assertSameBehaviour(
                filter,
                roundTrip(filter),
                section(1, "1", 1),
                section(2, "4", 1),
                section(3, "7", 1));
    }

    @Test
    public void roundTripsMinSlipFilter() throws IOException {
        FaultFilter filter = new FaultFilter.MinSlipFilter(2.5);

        assertSameBehaviour(
                filter,
                roundTrip(filter),
                section(1, "1", 1),
                section(2, "1", 2.5),
                section(3, "1", 10));
    }

    @Test
    public void roundTripsPolygonFilter() throws IOException {
        File polygonFile = tempFolder.newFile("polygon.txt");
        Files.writeString(polygonFile.toPath(), "170 -40\n172 -40\n172 -42\n170 -42\n");

        FaultFilter filter = new FaultFilter.PolygonFilter(polygonFile.getPath());

        assertSameBehaviour(
                filter,
                roundTrip(filter),
                section(1, "1", 1, new Location(-41, 171), new Location(-41.5, 171.5)),
                section(2, "1", 1, new Location(-41, 171), new Location(-45, 175)));
    }

    /**
     * The lambda created by setFaultFilter() cannot be serialised. It is replaced by a placeholder
     * that fails loudly rather than silently building a different rupture set.
     */
    @Test
    public void unsupportedFilterRoundTripsAsPlaceholder() throws IOException {
        NZSHM22_CoulombRuptureSetBuilder builder = new NZSHM22_CoulombRuptureSetBuilder();
        builder.setFaultFilter(Set.of(1, 2));

        NZSHM22_CoulombRuptureSetBuilder actual =
                NZSHM22_CoulombRuptureSetBuilder.fromJson(builder.toJson());

        assertEquals(builder.toJson(), actual.toJson());
        FaultFilter filter = actual.faultFilters.get(0);
        assertTrue(filter instanceof FaultFilter.UnsupportedFilter);
        try {
            filter.keep(section(1, "1", 1));
            fail("expected the placeholder filter to refuse filtering");
        } catch (UnsupportedOperationException expected) {
        }
    }
}

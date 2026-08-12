package nz.cri.gns.NZSHM22.opensha.ruptures;

import com.google.common.base.Preconditions;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import nz.cri.gns.NZSHM22.opensha.faults.FaultSectionList;
import nz.cri.gns.NZSHM22.opensha.faults.NZFaultSection;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;
import org.opensha.sha.faultSurface.FaultSection;

/**
 * FaultFilters are used to filter crustal faults before they are divided into subsections. This is
 * done to create smaller rupture sets for debugging.
 */
public interface FaultFilter {

    boolean keep(FaultSection section);

    public default void filter(FaultSectionList sections) {
        sections.removeIf(section -> !keep(section));
        System.out.println(
                "Fault model filtered to " + sections.size() + " after applying " + this);
    }

    /**
     * A short description of the filter to be added to the rupture archive file name.
     *
     * @return
     */
    public default String toDescription() {
        return "";
    }

    public class IdRangeFilter implements FaultFilter {
        final int maxFaultSections;
        final int skipFaultSections;

        /**
         * Creates a FaultFilter based on section ids.
         *
         * @param skipFaultSections discard sections with ids less than this value.
         * @param maxFaultSections take this many sections if available.
         */
        public IdRangeFilter(int skipFaultSections, int maxFaultSections) {
            this.skipFaultSections = skipFaultSections;
            this.maxFaultSections = maxFaultSections;
            Preconditions.checkArgument(skipFaultSections >= 0);
            Preconditions.checkArgument(maxFaultSections > 0);
        }

        @Override
        public boolean keep(FaultSection section) {
            return section.getSectionId() >= skipFaultSections
                    && section.getSectionId() < (skipFaultSections + maxFaultSections);
        }

        @Override
        public String toString() {
            return "id range filter, skip: " + skipFaultSections + " max : " + maxFaultSections;
        }

        @Override
        public String toDescription() {
            String description = "";
            if (maxFaultSections != 100000) {
                description +=
                        "_mxFS(" + NZSHM22_AbstractRuptureSetBuilder.fmt(maxFaultSections) + ")";
            }
            if (skipFaultSections > 0) {
                description +=
                        "_skFS(" + NZSHM22_AbstractRuptureSetBuilder.fmt(skipFaultSections) + ")";
            }
            return description;
        }
    }

    public class DomainFilter implements FaultFilter {

        final String filterDescription;
        final Set<String> domains;

        /**
         * Creates a FaultFilter that will remove sections with the specified domains.
         *
         * @param domains a space and/or comma separated list of integers.
         */
        public DomainFilter(String domains) {
            this.filterDescription = domains;
            this.domains = new HashSet<>(List.of(domains.split("\\D+")));
            Preconditions.checkState(
                    !this.domains.isEmpty(), "Could not find any ids in the domains string");
        }

        @Override
        public boolean keep(FaultSection section) {
            return !domains.contains(((NZFaultSection) section).getDomainNo());
        }

        @Override
        public String toString() {
            return "domain filter " + filterDescription;
        }
    }

    public class MinSlipFilter implements FaultFilter {

        final double minSlip;

        /**
         * Creates a FaultFilter that removes sections below minSlip.
         *
         * @param minSlip the minimum slip required.
         */
        public MinSlipFilter(double minSlip) {
            this.minSlip = minSlip;
        }

        @Override
        public boolean keep(FaultSection section) {
            return section.getOrigAveSlipRate() >= minSlip;
        }

        @Override
        public String toString() {
            return "slip filter with a minimum of " + minSlip + " mm/yr";
        }

        //        @Override
        //        public String toDescription() {
        //            return "_mSlp(" + NZSHM22_AbstractRuptureSetBuilder.fmt(minSlip) + ")";
        //        }
    }

    public class PolygonFilter implements FaultFilter {

        final Region polygon;
        final String fileName;

        /**
         * Creates a FaultFilter that will remove all sections outside the specified polygon.
         *
         * @param fileName a text file that holds a lon lat value in each line.
         */
        public PolygonFilter(String fileName) {
            this.fileName = fileName;
            this.polygon = readPolygon(fileName);
        }

        static Region readPolygon(String fileName) {
            LocationList locations = new LocationList();
            try {
                BufferedReader reader = new BufferedReader(new FileReader(fileName));
                String line = reader.readLine();
                while (line != null) {
                    String[] parts = line.split("\\s+");
                    Location location =
                            new Location(
                                    Double.parseDouble(parts[1].trim()),
                                    Double.parseDouble(parts[0].trim()));
                    locations.add(location);
                    line = reader.readLine();
                }
                reader.close();
                // closing the polygon if necessary
                if (!locations.get(locations.size() - 1).equals(locations.get(0))) {
                    locations.add(locations.get(0));
                }
            } catch (IOException x) {
                throw new RuntimeException(x);
            }
            return new Region(locations, null);
        }

        @Override
        public boolean keep(FaultSection section) {
            for (Location l : section.getFaultTrace()) {
                if (!polygon.contains(l)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public String toString() {
            return "polygon filter " + fileName;
        }
    }

    /**
     * Stands in for a filter that could not be serialised, such as the lambda created by {@link
     * NZSHM22_AbstractRuptureSetBuilder#setFaultFilter(Set)}. It only carries the original filter's
     * description so that the builder config stays readable. Using it to actually filter faults
     * would silently produce a different rupture set, so it refuses to do so.
     */
    public class UnsupportedFilter implements FaultFilter {

        final String description;

        public UnsupportedFilter(String description) {
            this.description = description;
        }

        @Override
        public boolean keep(FaultSection section) {
            throw new UnsupportedOperationException(
                    "This fault filter was not preserved when the builder config was serialised: "
                            + description);
        }

        @Override
        public String toString() {
            return description;
        }
    }

    /**
     * Serialises FaultFilters as their constructor arguments, tagged with the filter type. Derived
     * state such as {@link DomainFilter#domains} or {@link PolygonFilter#polygon} is rebuilt by the
     * constructor rather than stored. Filters that are not one of the known implementations are
     * written as an {@link UnsupportedFilter}.
     */
    public class Adapter extends TypeAdapter<FaultFilter> {

        @Override
        public void write(JsonWriter out, FaultFilter value) throws IOException {
            out.beginObject();
            if (value instanceof IdRangeFilter) {
                IdRangeFilter filter = (IdRangeFilter) value;
                out.name("type").value("idRange");
                out.name("skipFaultSections").value(filter.skipFaultSections);
                out.name("maxFaultSections").value(filter.maxFaultSections);
            } else if (value instanceof DomainFilter) {
                out.name("type").value("domain");
                out.name("domains").value(((DomainFilter) value).filterDescription);
            } else if (value instanceof MinSlipFilter) {
                out.name("type").value("minSlip");
                out.name("minSlip").value(((MinSlipFilter) value).minSlip);
            } else if (value instanceof PolygonFilter) {
                out.name("type").value("polygon");
                out.name("fileName").value(((PolygonFilter) value).fileName);
            } else {
                out.name("type").value("unsupported");
                out.name("description").value(value.toString());
            }
            out.endObject();
        }

        @Override
        public FaultFilter read(JsonReader in) throws IOException {
            String type = null;
            Integer skipFaultSections = null;
            Integer maxFaultSections = null;
            String domains = null;
            Double minSlip = null;
            String fileName = null;
            String description = null;

            in.beginObject();
            while (in.hasNext()) {
                switch (in.nextName()) {
                    case "type":
                        type = in.nextString();
                        break;
                    case "skipFaultSections":
                        skipFaultSections = in.nextInt();
                        break;
                    case "maxFaultSections":
                        maxFaultSections = in.nextInt();
                        break;
                    case "domains":
                        domains = in.nextString();
                        break;
                    case "minSlip":
                        minSlip = in.nextDouble();
                        break;
                    case "fileName":
                        fileName = in.nextString();
                        break;
                    case "description":
                        description = in.nextString();
                        break;
                    default:
                        in.skipValue();
                }
            }
            in.endObject();

            Preconditions.checkNotNull(type, "fault filter is missing its type");
            switch (type) {
                case "idRange":
                    return new IdRangeFilter(skipFaultSections, maxFaultSections);
                case "domain":
                    return new DomainFilter(domains);
                case "minSlip":
                    return new MinSlipFilter(minSlip);
                case "polygon":
                    return new PolygonFilter(fileName);
                case "unsupported":
                    return new UnsupportedFilter(description);
                default:
                    throw new IllegalArgumentException("Unknown fault filter type " + type);
            }
        }
    }
}

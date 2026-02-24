package nz.cri.gns.NZSHM22.opensha.ruptures;

import com.google.common.base.Preconditions;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import nz.cri.gns.NZSHM22.opensha.faults.FaultSectionList;
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
            return !domains.contains(FaultSectionProperties.getDomain(section));
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
}

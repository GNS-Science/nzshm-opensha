package nz.cri.gns.NZSHM22.opensha.timeDependent;

import com.google.common.base.Preconditions;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.geo.Location;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.faultSurface.FaultSection;
import scratch.UCERF3.erf.utils.ProbabilityModelsCalc;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public enum MREData {

    CFM_1_1("ConditionalProbData_CFM1_1.csv");

    static final String RESOURCE_PATH = "/timeDependent/";

    final String fileName;

    MREData(String fileName) {
        this.fileName = fileName;
    }

    private static class YearData {
        static final int NAME = 1;
        static final int YEARS = 3;
        static final int LAT_1 = 4;
        static final int LON_1 = 5;
        static final int LAT_2 = 6;
        static final int LON_2 = 7;
        static final int WHOLE = 8;

        public String name;
        // in years
        public long yearsAgo;
        public Location start;
        public Location stop;
        public boolean whole = true;

        public boolean usable = false;

        public YearData(List<String> csvRow) {
            name = csvRow.get(NAME);
            System.out.println(name);
            if (csvRow.get(YEARS).trim().matches("\\d+")) {
                usable = true;
                yearsAgo = Integer.parseInt(csvRow.get(YEARS).trim());
                whole = csvRow.get(WHOLE).trim().equals("Y");
                if (!whole) {
                    start = new Location(
                            Double.parseDouble(csvRow.get(LAT_1)),
                            Double.parseDouble(csvRow.get(LON_1)));
                    stop = new Location(
                            Double.parseDouble(csvRow.get(LAT_2)),
                            Double.parseDouble(csvRow.get(LON_2)));
                }
            }
        }
    }

    public static long yearsAgoInMillis(int currentYear, long yearsAgo) {
        return (long) ((currentYear - 1970 - yearsAgo) * ProbabilityModelsCalc.MILLISEC_PER_YEAR);
    }

    InputStream getStream(String fileName) {
        return getClass().getResourceAsStream(RESOURCE_PATH + fileName);
    }

    /**
     * iron out differences in fault names between the fault model and the MRE data
     *
     * @param name
     * @return
     */
    static String unify(String name) {
        return name.toLowerCase().trim().replaceAll("\\W+", " ");
    }

    /**
     * Recreate faults as lists of subsections in order. Grouped by unified fault name.
     *
     * @param solution
     * @return
     */
    protected static Map<String, List<FaultSection>> reconstructFaults(FaultSystemSolution solution) {
        Map<String, List<FaultSection>> faults = new HashMap<>();
        for (FaultSection section : solution.getRupSet().getFaultSectionDataList()) {
            String faultName = unify(section.getParentSectionName());
            List<FaultSection> fault = faults.get(faultName);
            if (fault == null) {
                fault = new ArrayList<>();
                faults.put(faultName, fault);
            } else {
                // ensure that there are no gaps and that sections are in the correct order
                Preconditions.checkState(fault.get(fault.size() - 1).getFaultTrace().last().equals(section.getFaultTrace().first()));
            }
            fault.add(section);
        }
        return faults;
    }

    private void attachYear(FaultSection section, long yearsAgo, int currentYear) {
        Preconditions.checkArgument(
                section.getDateOfLastEvent() == Long.MIN_VALUE,
                "section " + section.getSectionName() + " already has a date of last event");
        section.setDateOfLastEvent(yearsAgoInMillis(currentYear, yearsAgo));
    }

    /**
     * Find the subsection of the fault that is nearest to the Location
     *
     * @param fault
     * @param l
     * @return
     */
    public static int nearestSection(List<FaultSection> fault, Location l) {
        double minDist = Double.MAX_VALUE;
        int minSection = -1;
        for (int i = 0; i < fault.size(); i++) {
            double distance = fault.get(i).getFaultTrace().minDistToLine(l);
            Preconditions.checkState(!((distance == 0) && (minDist == 0)));
            if (distance < minDist) {
                minDist = distance;
                minSection = i;
            }
        }
        Preconditions.checkState(minSection != -1);
        return minSection;
    }

    /**
     * Attach the MRE data from the CSV row to all relevant sections.
     *
     * @param fault
     * @param data
     */
    private void attachYears(List<FaultSection> fault, YearData data, int currentYear) {
        int from;
        int to;
        if (data.whole) {
            from = 0;
            to = fault.size() - 1;
        } else {
            from = nearestSection(fault, data.start);
            to = nearestSection(fault, data.stop);
        }
        for (int i = from; i <= to; i++) {
            attachYear(fault.get(i), data.yearsAgo, currentYear);
        }
    }

    /**
     * Read the CSV file and attach all year values to the relevant subsections.
     *
     * @throws IOException
     */
    public void apply(FaultSystemSolution solution, int currentYear) throws IOException {
        Map<String, List<FaultSection>> faults = reconstructFaults(solution);
        CSVFile<String> timeData = CSVFile.readStream(getStream(fileName), true);
        for (List<String> row : timeData) {
            if (row.get(0).equals("CFM #")) {
                continue;
            }
            try {
                YearData data = new YearData(row);
                if (data.usable) {
                    List<FaultSection> fault = faults.get(unify(data.name));
                    if (fault == null) {
                        System.out.println("----- no fault found");
                    } else {
                        attachYears(fault, data, currentYear);
                    }
                }
            } catch (Exception x) {
                x.printStackTrace();
            }
        }
    }
}

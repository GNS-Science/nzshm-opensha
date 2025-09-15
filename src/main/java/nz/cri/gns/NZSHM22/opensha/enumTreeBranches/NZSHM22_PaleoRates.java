package nz.cri.gns.NZSHM22.opensha.enumTreeBranches;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nz.cri.gns.NZSHM22.opensha.util.JupyterLogger;
import nz.cri.gns.NZSHM22.opensha.util.SimpleGeoJsonBuilder;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.uncertainty.BoundedUncertainty;
import org.opensha.commons.data.uncertainty.UncertaintyBoundType;
import org.opensha.commons.geo.Location;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.UncertainDataConstraint;
import org.opensha.sha.faultSurface.FaultSection;

public enum NZSHM22_PaleoRates implements LogicTreeNode {
    CUSTOM("Paleo Rates are side-loaded from custom file", null),

    GEODETIC_SLIP_PRIOR_NO_TVZ(
            "Geodetic with Geologic prior timing, no TVZ",
            "NZNSHM_paleotimings_GEODETICGEOLOGICPRIOR_notvz.txt"),

    GEODETIC_SLIP_NO_TVZ(
            "Geodetic timing, no TVZ", "NZNSHM_paleotimings_GEODETICsliprates_notvz.txt"),

    GEOLOGIC_SLIP_NO_TVZ(
            "Geologic timing, no TVZ", "NZNSHM_paleotimings_GEOLOGICsliprates_notvz.txt"),

    GEODETIC_SLIP_PRIOR_22FEB(
            "Geodetic with Geologic prior timing, 22 Feb 2022",
            "NZNSHM_paleotimings_GEODETICGEOLOGICPRIOR_22feb.txt"),

    GEODETIC_SLIP_22FEB(
            "Geodetic timing, 22 Feb 2022", "NZNSHM_paleotimings_GEODETICsliprates_22feb.txt"),

    GEOLOGIC_SLIP_22FEB(
            "Geologic timing, 22 Feb 2022", "NZNSHM_paleotimings_GEOLOGICsliprates_22feb.txt"),

    GEODETIC_SLIP_PRIOR_4FEB(
            "Geodetic with Geologic prior timing, 4 Feb 2022",
            "NZNSHM_paleotimings_GEODETICGEOLOGICPRIOR_4feb.csv"),

    GEODETIC_SLIP_4FEB(
            "Geodetic timing, 4 Feb 2022", "NZNSHM_paleotimings_GEODETICsliprates_4feb.csv"),

    GEOLOGIC_SLIP_4FEB(
            "Geologic timing, 4 Feb 2022", "NZNSHM_paleotimings_GEOLOGICsliprates_4feb.csv"),

    GEOLOGIC_SLIP_1_0("Geologic v1", "NZNSHM_paleotimings_GEOLOGICsliprates_all_1.0.csv"),

    GEODETIC_SLIP_1_0("Geodetic v1", "NZNSHM_paleotimings_GEODETICsliprates_1.0.csv"),

    PALEO_RI_GEOLOGIC_MAY24(
            "Geologic timing, no TVZ, Maruia moved, May 24 corrections",
            "NZNSHM_paleotimings_GEOLOGIC_24May.txt"),

    PALEO_RI_GEODETIC_MAY24(
            "Geodetic timing, no TVZ, Maruia moved, May 24 corrections",
            "NZNSHM_paleotimings_GEODETIC_24May.txt"),

    PALEO_RI_GEODETICGEOLOGICPRIOR_MAY24(
            "Geodetic with Geologic Prior timing, no TVZ, Maruia moved, May 24 corrections",
            "NZNSHM_paleotimings_GEODETICGEOLOGICPRIOR_24May.txt"),
    ;

    static final String RESOURCE_PATH = "/paleoRates/";

    static JupyterLogger.MapPlot jupyterMap;

    final String description;
    final String fileName;

    NZSHM22_PaleoRates(String description, String fileName) {
        this.description = description;
        this.fileName = fileName;
    }

    InputStream getStream(String fileName) {
        return getClass().getResourceAsStream(RESOURCE_PATH + fileName);
    }

    public List<UncertainDataConstraint.SectMappedUncertainDataConstraint> fetchConstraints(
            List<? extends FaultSection> faultSections) {
        if (fileName == null) {
            return new ArrayList<>();
        }
        return fetchConstraints(faultSections, getStream(fileName));
    }

    public static List<UncertainDataConstraint.SectMappedUncertainDataConstraint> fetchConstraints(
            List<? extends FaultSection> faultSections, InputStream in) {

        List<UncertainDataConstraint.SectMappedUncertainDataConstraint> paleoRateConstraints =
                new ArrayList<>();
        CSVFile<String> csv;
        try {
            csv = CSVFile.readStream(in, false);
        } catch (IOException x) {
            x.printStackTrace();
            throw new RuntimeException(x);
        }

        SimpleGeoJsonBuilder geoJson = new SimpleGeoJsonBuilder();

        Map<String, Integer> doubleUps = new HashMap<>();

        for (int row = 1; row < csv.getNumRows(); row++) {
            String siteName = csv.get(row, 0).trim();
            double lat = csv.getDouble(row, 1);
            double lon = csv.getDouble(row, 2);
            double meanRate = csv.getDouble(row, 8);

            double lower2sConf = csv.getDouble(row, 9);
            double lower1sConf = csv.getDouble(row, 10);
            double upper1sConf = csv.getDouble(row, 11);
            double upper2sConf = csv.getDouble(row, 12);

            Location loc = new Location(lat, lon);
            BoundedUncertainty[] uncertainties =
                    new BoundedUncertainty[] {
                        BoundedUncertainty.fromMeanAndBounds(
                                UncertaintyBoundType.ONE_SIGMA, meanRate, lower1sConf, upper1sConf),
                        BoundedUncertainty.fromMeanAndBounds(
                                UncertaintyBoundType.TWO_SIGMA, meanRate, lower2sConf, upper2sConf),
                    };

            double minDist = Double.MAX_VALUE;
            int closestFaultSectionIndex = -1;

            for (int sectionIndex = 0; sectionIndex < faultSections.size(); ++sectionIndex) {
                FaultSection data = faultSections.get(sectionIndex);
                double dist = data.getFaultTrace().minDistToLine(loc);
                if (dist < minDist) {
                    minDist = dist;
                    closestFaultSectionIndex = sectionIndex;
                }
            }
            if (minDist > 5.25) {
                System.out.print(
                        "No match for: "
                                + siteName
                                + " (lat="
                                + lat
                                + ", lon="
                                + lon
                                + ") closest was "
                                + minDist
                                + " away: "
                                + closestFaultSectionIndex);
                if (closestFaultSectionIndex >= 0) {
                    System.out.println(
                            ". " + faultSections.get(closestFaultSectionIndex).getSectionName());
                } else {
                    System.out.println();
                }

                continue; // closest fault section is at a distance of more than 5.25 km
            }

            // debugging

            String sectionName = faultSections.get(closestFaultSectionIndex).getSectionName();

            if (doubleUps.containsKey(sectionName)) {
                doubleUps.put(sectionName, doubleUps.get(sectionName) + 1);
            } else {
                doubleUps.put(sectionName, 1);
            }

            geoJson.addLocation(
                    loc,
                    "site",
                    siteName,
                    "closest section",
                    sectionName,
                    "distance",
                    "" + minDist);

            geoJson.addFaultSection(faultSections.get(closestFaultSectionIndex));

            paleoRateConstraints.add(
                    new UncertainDataConstraint.SectMappedUncertainDataConstraint(
                            siteName,
                            closestFaultSectionIndex,
                            sectionName,
                            loc,
                            meanRate,
                            uncertainties));
        }

        if (jupyterMap == null) {
            JupyterLogger.logger()
                    .addMarkDown(
                            "## Paleo Rates\n"
                                    + "A map of paleo sites and their matching fault sections.\n\n"
                                    + "If applicable, a table of fault sections that have more than one matching paleo site.");
            jupyterMap = JupyterLogger.logger().addMap("paleoRatesMatches");
        }

        if (jupyterMap.getLayerCount() == 0) {
            jupyterMap.addLayer("sites_from_enum", geoJson.toJSON());
        } else {
            jupyterMap.addLayer("sites_from_file", geoJson.toJSON());
        }

        List<List<Object>> doubleUpList = new ArrayList<>();
        doubleUpList.add(List.of("section id", "paleo sites count"));
        for (String section : doubleUps.keySet()) {
            if (doubleUps.get(section) > 1) {
                doubleUpList.add(List.of(section, doubleUps.get(section)));
            }
        }
        if (doubleUpList.size() > 1) {
            JupyterLogger.logger().addCSV("paleo_rates_double_up", doubleUpList);
        }

        return paleoRateConstraints;
    }

    @Override
    public String getName() {
        return "NZSHM22_PaleoRates";
    }

    @Override
    public String getShortName() {
        return "NZSHM22_PaleoRates";
    }

    @Override
    public double getNodeWeight(LogicTreeBranch<?> fullBranch) {
        return 0;
    }

    @Override
    public String getFilePrefix() {
        return null;
    }

    public static LogicTreeLevel<LogicTreeNode> level() {
        return LogicTreeLevel.forEnumUnchecked(
                NZSHM22_PaleoRates.class, "NZSHM22_PaleoRates", "NZSHM22_PaleoRates");
    }
}

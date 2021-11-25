package nz.cri.gns.NZSHM22.opensha.enumTreeBranches;

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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public enum NZSHM22_PaleoRates implements LogicTreeNode {

    GEOLOGIC_SLIP_1_0("NZNSHM_paleotimings_GEOLOGICsliprates_all_1.0.csv"),
    GEODETIC_SLIP_1_0("NZNSHM_paleotimings_GEODETICsliprates_1.0.csv");

    final static String RESOURCE_PATH = "/paleoRates/";

    final String fileName;

    NZSHM22_PaleoRates(String fileName) {
        this.fileName = fileName;
    }

    InputStream getStream(String fileName) {
        return getClass().getResourceAsStream(RESOURCE_PATH + fileName);
    }

    public List<UncertainDataConstraint.SectMappedUncertainDataConstraint> fetchConstraints(List<? extends FaultSection> faultSections) {

        List<UncertainDataConstraint.SectMappedUncertainDataConstraint> paleoRateConstraints = new ArrayList<>();
        CSVFile<String> csv;
        try {
            csv = CSVFile.readStream(getStream(fileName), false);
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
            BoundedUncertainty[] uncertainties = new BoundedUncertainty[]{
                    BoundedUncertainty.fromMeanAndBounds(UncertaintyBoundType.ONE_SIGMA, meanRate, lower1sConf, upper1sConf),
                    BoundedUncertainty.fromMeanAndBounds(UncertaintyBoundType.TWO_SIGMA, meanRate, lower2sConf, upper2sConf),
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
                System.out.print("No match for: " + siteName + " (lat=" + lat + ", lon=" + lon
                        + ") closest was " + minDist + " away: " + closestFaultSectionIndex);
                if (closestFaultSectionIndex >= 0) {
                    System.out.println(". " + faultSections.get(closestFaultSectionIndex).getSectionName());
                } else {
                    System.out.println();
                }

                continue; // closest fault section is at a distance of more than 5.25 km
            }

                // debugging

                String sectionName = faultSections.get(closestFaultSectionIndex).getSectionName();

                if(doubleUps.containsKey(sectionName)){
                    doubleUps.put(sectionName, doubleUps.get(sectionName)+1);
                }else{
                    doubleUps.put(sectionName, 1);
                }

                geoJson.addLocation(loc,
                        "site", siteName,
                        "closest section", sectionName,
                        "distance", ""+minDist);

                geoJson.addFaultSection(faultSections.get(closestFaultSectionIndex));
                System.out.println(siteName + " -> " + sectionName + ", " + minDist);

            paleoRateConstraints.add(new UncertainDataConstraint.SectMappedUncertainDataConstraint(siteName, closestFaultSectionIndex, sectionName, loc, meanRate, uncertainties));
        }

        geoJson.toJSON("paleoRates.geojson");
        for(String section : doubleUps.keySet()){
            if(doubleUps.get(section) > 1){
                System.out.println("subsection " + section + " has " + doubleUps.get(section) + " paleo sites.");
            }
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
        return LogicTreeLevel.forEnumUnchecked(NZSHM22_PaleoRates.class, "NZSHM22_PaleoRates", "NZSHM22_PaleoRates");
    }

}

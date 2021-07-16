package nz.cri.gns.NZSHM22.opensha.hazard;

import com.google.common.collect.Lists;
import nz.cri.gns.NZSHM22.opensha.data.region.NewZealandRegions;
import org.dom4j.DocumentException;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.geo.BorderType;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class NZSHM22_GridHazardCalculator {

    final NZSHM22_HazardCalculator calculator;
    Region parentRegion = new NewZealandRegions.NZ_TEST_GRIDDED();
    double spacing = 0.1;
    List<Double> pOfEts = Lists.newArrayList(0.02, 0.1);

    public NZSHM22_GridHazardCalculator(NZSHM22_HazardCalculator calculator) {
        this.calculator = calculator;
    }

    public NZSHM22_GridHazardCalculator setSpacing(double spacing) {
        this.spacing = spacing;
        return this;
    }

    public NZSHM22_GridHazardCalculator setRegion(String regionName) {

        switch (regionName) {
            case "NZ_TEST_GRIDDED":
                parentRegion = new NewZealandRegions.NZ_TEST_GRIDDED();
                break;
            case "NZ_TVZ_GRIDDED":
                parentRegion = new NewZealandRegions.NZ_TVZ_GRIDDED();
                break;
            case "NZ_RECTANGLE_SANS_TVZ_GRIDDED":
                parentRegion = new NewZealandRegions.NZ_RECTANGLE_SANS_TVZ_GRIDDED();
                break;
            case "NZ_RECTANGLE_GRIDDED":
                parentRegion = new NewZealandRegions.NZ_RECTANGLE_GRIDDED();
                break;
        }

        return this;
    }

    public NZSHM22_GridHazardCalculator addPOfEt(double pOfEt) {
        if (pOfEts.contains(pOfEt)) {
            throw new IllegalArgumentException("Value already in list of pOfEts: " + pOfEt);
        }
        pOfEts.add(pOfEt);
        return this;
    }

    public List<GridHazards> run() {
        GriddedRegion region = new GriddedRegion(parentRegion.getBorder(), BorderType.MERCATOR_LINEAR, spacing, GriddedRegion.ANCHOR_0_0);
        List<GridHazards> result = new ArrayList<>();
        for (Location location : region.getNodeList()) {
            GridHazards grdHaz = new GridHazards();
            grdHaz.location = location;
            grdHaz.hazards = calculator.calc(location.getLatitude(), location.getLongitude());
            if (pOfEts.size() > 0) {
                grdHaz.hazardsAtPOfEts = calcHazardsAtPOfEt(grdHaz.hazards);
            }
            result.add(grdHaz);
        }
        System.out.println("location count " + region.getNodeCount());

        return result;
    }

    /**
     * Runs the calculator and returns the result as tabular data (rows of String values).
     * The first row contains the column headers, e.g.
     * "lat", "long", "PofET 0.02", "PofET 0.1", "0.0001", "0.00013", ...
     * Then each row contains the values for each of these columns.
     *
     * @return rows of Strings representing the hazards in the specified grid.
     */
    public List<List<String>> getTabularGridHazards() {
        List<List<String>> result = new ArrayList<>();
        List<String> row = new ArrayList<>();

        List<GridHazards> hazards = run();

        // header row
        row.add("lat");
        row.add("long");
        for (double value : pOfEts) {
            row.add("PofET " + value);
        }
        for (double value : hazards.get(0).hazards.xValues()) {
            row.add(value + "");
        }

        result.add(row);

        // data rows
        for (GridHazards gridHazards : hazards) {
            row = new ArrayList<>();
            row.add(gridHazards.location.getLatitude() + "");
            row.add(gridHazards.location.getLongitude() + "");
            for (double value : gridHazards.hazardsAtPOfEts) {
                row.add(value + "");
            }
            for (double value : gridHazards.hazards.yValues()) {
                row.add(value + "");
            }
            result.add(row);
        }

        return result;
    }

    /**
     * creates a geojson hazard grid for debugging.
     *
     * @param pofetIndex which pofet the map should be created for
     * @param fileName
     * @throws IOException
     */
    public void createGeoJson(int pofetIndex, String fileName) throws IOException {
        List<GridHazards> result = run();

        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        for (GridHazards hazards : result) {
            double value = hazards.hazardsAtPOfEts.get(pofetIndex);
            if (value < min) {
                min = value;
            }
            if (value > max) {
                max = value;
            }
        }

        double sp = spacing * 0.5;

        try (PrintWriter out = new PrintWriter(new FileWriter(fileName))) {
            out.println("{\n" +
                    "  \"type\": \"FeatureCollection\",\n" +
                    "  \"features\": [");

            boolean previous = false;

            for (GridHazards hazards : result) {
                double value = hazards.hazardsAtPOfEts.get(pofetIndex);
                double lat = hazards.location.getLatitude();
                double lon = hazards.location.getLongitude();
                if (!Double.isNaN(value)) {

                    if (previous) {
                        out.println(",");
                    } else {
                        previous = true;
                    }

                    out.println("{\"type\": \"Feature\",\n" +
                            " \"geometry\": {\n" +
                            " \"type\": \"Polygon\",\n" +
                            " \"coordinates\": [[");
                    out.println("  [" + (lon - sp) + "," + (lat - sp) + "],");
                    out.println("  [" + (lon + sp) + "," + (lat - sp) + "],");
                    out.println("  [" + (lon + sp) + "," + (lat + sp) + "],");
                    out.println("  [" + (lon - sp) + "," + (lat + sp) + "],");
                    out.println("  [" + (lon - sp) + "," + (lat - sp) + "]");

                    out.println("]]},"); // coordinates, geometry


                    long colour = Math.round((1 - ((value - min) / (max - min))) * 255);


                    out.println(" \"properties\": {\n" +
                            "  \"fill\": \"#FF" + String.format("%02X", colour) + "FF\",\n" +
                            "  \"fill-opacity\": 0.5,\n" +
                            "  \"stroke-width\": 0\n" +
                            " }}");
                }
            }

            out.println("]}");
        }
    }

    public List<Double> calcHazardsAtPOfEt(DiscretizedFunc hazardCurve) {
        List<Double> result = new ArrayList<>();
        for (Double pOfEt : pOfEts) {
            result.add(hazardAtPOfEt(hazardCurve, pOfEt));
        }
        return result;
    }

    public static double hazardAtPOfEt(DiscretizedFunc func, double pOfEt) {
        if (func.getMinY() <= pOfEt && func.getMaxY() >= pOfEt) {
            return func.getFirstInterpolatedX_inLogXLogYDomain(pOfEt);
        } else {
            return Double.NaN;
        }
    }

    public static class GridHazards {
        public Location location;
        public DiscretizedFunc hazards;
        public List<Double> hazardsAtPOfEts;
    }

    public static void main(String[] args) throws DocumentException, IOException {

        NZSHM22_HazardCalculatorBuilder builder = new NZSHM22_HazardCalculatorBuilder();
        builder.setSolutionFile("src\\test\\resources\\AlpineVernonInversionSolution.zip");
        // builder.setSolutionFile("C:\\Users\\volkertj\\Downloads\\InversionSolution-RmlsZTo2-rnd0-t30.zip");
        builder.setForecastTimespan(50);
        builder.setLinear(true); // has to be linear to make pofet calc work
        NZSHM22_GridHazardCalculator gridCalc = new NZSHM22_GridHazardCalculator(builder.build());
        gridCalc.setRegion("NZ_TEST_GRIDDED");
        gridCalc.setSpacing(0.1);

        gridCalc.createGeoJson(0, "c:/tmp/hazard.json");

        List<List<String>> result = gridCalc.getTabularGridHazards();

        try (PrintWriter out = new PrintWriter(new FileWriter("c:/tmp/hazgrid2.csv"))) {
            for (List<String> row : result) {
                for (String value : row) {
                    out.print(value + ", ");
                }
                out.print("\n");
            }
        }

        System.out.println("done.");
    }

}

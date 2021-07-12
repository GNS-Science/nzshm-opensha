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
        builder.setSolutionFile("C:\\Code\\NZSHM\\nshm-nz-opensha\\src\\test\\resources\\AlpineVernonInversionSolution.zip");
        builder.setForecastTimespan(50);
        builder.setLinear(true); // has to be linear to make pofet calc work
        NZSHM22_GridHazardCalculator gridCalc = new NZSHM22_GridHazardCalculator(builder.build());
        gridCalc.setRegion("NZ_TEST_GRIDDED");
        gridCalc.setSpacing(0.1);
        List<GridHazards> result = gridCalc.run();
        
        try (PrintWriter out = new PrintWriter(new FileWriter("c:/tmp/hazgrid.csv"))) {
            // print header
            out.print("lat, long,");
            for(double value : gridCalc.pOfEts){
                out.print("PofET " + value + ",");
            }
            for (double value : result.get(0).hazards.xValues()) {
                out.print(value + ",");
            }
            out.println();

            // print data
            for (GridHazards gridHazards:result) {
                out.print(gridHazards.location.getLatitude() + ", " + gridHazards.location.getLongitude() + ", ");
                for (double value : gridHazards.hazardsAtPOfEts) {
                    out.print(value + ",");
                }
                for (double value : gridHazards.hazards.yValues()) {
                    out.print(value + ",");
                }
                out.println();
            }
        }
        System.out.println("done.");
    }

}

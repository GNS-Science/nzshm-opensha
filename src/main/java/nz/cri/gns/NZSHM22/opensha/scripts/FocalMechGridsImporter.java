package nz.cri.gns.NZSHM22.opensha.scripts;

import nz.cri.gns.NZSHM22.opensha.data.region.NewZealandRegions;
import org.opensha.commons.geo.BorderType;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;

import java.io.*;
import java.util.*;

/**
 * Imports 2010 data and turns it into NZSHM22 grids
 * Written for https://github.com/GNS-Science/fortran_SHMs/blob/main/2010_NSHM-corrected/NZBCK615.txt
 */
public class FocalMechGridsImporter {

    enum Mechs {
        NORMAL,
        REVERSE,
        STRIKESLIP
    }

    static class GridPoint extends Location {
        public Map<Mechs, Double> mechs;
        public int line;

        public GridPoint(double lat, double lon, Map<Mechs, Double> mechs, int line) {
            super(lat, lon);
            this.mechs = mechs;
            this.line = line;
        }
    }

    static Map<String, Map<Mechs, Double>> weights;

    static {
        Map<Mechs, Double> normal = Map.of(
                Mechs.NORMAL, 1.0,
                Mechs.REVERSE, 0.0,
                Mechs.STRIKESLIP, 0.0);

        Map<Mechs, Double> reverse = Map.of(
                Mechs.NORMAL, 0.0,
                Mechs.REVERSE, 1.0,
                Mechs.STRIKESLIP, 0.0);

        Map<Mechs, Double> strikeSlip = Map.of(
                Mechs.NORMAL, 0.0,
                Mechs.REVERSE, 0.0,
                Mechs.STRIKESLIP, 1.0);

        Map<Mechs, Double> mixed = Map.of(
                Mechs.NORMAL, 0.0,
                Mechs.REVERSE, 0.5,
                Mechs.STRIKESLIP, 0.5);

        weights = new HashMap<>();
        weights.put("nv", normal);
        weights.put("nn", normal);
        weights.put("ns", normal);
        weights.put("ds", normal);
        weights.put("rv", reverse);
        weights.put("if", reverse);
        weights.put("sr", strikeSlip);
        weights.put("ss", strikeSlip);
        weights.put("sn", strikeSlip);
        weights.put("ro", mixed);
        weights.put("rs", mixed);
    }

    public static List<GridPoint> readFile(String fileName) throws FileNotFoundException {
        List<GridPoint> result = new ArrayList<>();
        try (BufferedReader in = new BufferedReader(new FileReader(fileName))) {
            // discard first line
            String line = in.readLine();
            int i = 0;
            Set<String> rejects = new HashSet<>();
            for (line = in.readLine(); line != null; line = in.readLine()) {
                i++;
                String[] parts = line.split(" ");
                if (parts.length < 13) {
                    System.out.println("row " + i + " too short");
                }
                Map<Mechs, Double> mechs = weights.get(parts[12]);

                if (mechs != null) {
                    double lat = -Double.parseDouble(parts[15]);
                    double lon = Double.parseDouble(parts[16]);
                    result.add(new GridPoint(lat, lon, mechs, i));
                } else {
                    rejects.add(parts[12]);
                }
            }
            System.out.println("last row " + i);
            System.out.println("rejects count " + rejects.size());
            System.out.println("rejects " + rejects);
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Read " + result.size() + " grid points");
        return result;
    }

    static double maxDist = Double.NEGATIVE_INFINITY;

    public static GridPoint nearestSourcePoint(List<GridPoint> source, Location location) {
        GridPoint nearest = null;
        double distance = Double.MAX_VALUE;
        for (GridPoint point : source) {
            double d = LocationUtils.horzDistance(location, point);
            if (d < distance) {
                nearest = point;
                distance = d;
            }
        }
        if (distance > maxDist) {
            maxDist = distance;
        }
        return nearest;
    }

    public static void printGridExtent(List<? extends Location> locations) {
        double minLat = Double.POSITIVE_INFINITY;
        double maxLat = Double.NEGATIVE_INFINITY;
        double minLon = Double.POSITIVE_INFINITY;
        double maxLon = Double.NEGATIVE_INFINITY;

        for (Location location : locations) {
            double lat = location.getLatitude();
            double lon = location.getLongitude();

            if (lat < minLat) {
                minLat = lat;
            }
            if (lat > maxLat) {
                maxLat = lat;
            }
            if (lon < minLon) {
                minLon = lon;
            }
            if (lon > maxLon) {
                maxLon = lon;
            }
        }

        System.out.println("min " + minLat + " , " + minLon);
        System.out.println("max " + maxLat + ", " + maxLon);
    }

    public static void main(String[] args) throws IOException {
        List<GridPoint> source = readFile("C:\\Users\\volkertj\\Downloads\\NZBCK615.csv");

        System.out.println("Source bounding box");
        printGridExtent(source);

        GriddedRegion region = new GriddedRegion(new NewZealandRegions.NZ_TEST_GRIDDED().getBorder(),
                BorderType.MERCATOR_LINEAR,
                0.05, //safe with GridReader.getValue()
                GriddedRegion.ANCHOR_0_0);

        System.out.println("Target bounding box");
        printGridExtent(region.getNodeList());

        System.out.println("Creating " + region.getNodeCount() + " grid nodes");

        Set<String> seen = new HashSet<>();

        int i = 0;
        try (PrintWriter normalOut = new PrintWriter(new FileWriter("c:/tmp/normalFocalMech.grid"));
             PrintWriter reverseOut = new PrintWriter(new FileWriter("c:/tmp/reverseFocalMech.grid"));
             PrintWriter strikeSlipOut = new PrintWriter(new FileWriter("c:/tmp/strikeFocalHazMech.grid"))) {
            for (Location location : region.getNodeList()) {
                String locString = location.getLongitude() + " " + location.getLatitude() + " ";

                if (seen.contains(locString)) {
                    System.out.println("duplicate location");
                    continue;
                } else {
                    seen.add(locString);
                }

                GridPoint sourcePoint = nearestSourcePoint(source, location);
                if (i++ % 500 == 0) {
                    System.out.print(".");
                }
                normalOut.println(locString + sourcePoint.mechs.get(Mechs.NORMAL));
                reverseOut.println(locString + sourcePoint.mechs.get(Mechs.REVERSE));
                strikeSlipOut.println(locString + sourcePoint.mechs.get(Mechs.STRIKESLIP));
            }
        }
        System.out.println("\nMax distance to source " + maxDist);
    }
}

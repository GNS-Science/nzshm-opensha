package nz.cri.gns.NZSHM22.opensha.griddedSeismicity;

import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiFunction;

import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import nz.cri.gns.NZSHM22.opensha.data.region.NewZealandRegions;
import nz.cri.gns.NZSHM22.opensha.util.NZSHM22_DataUtils;

/**
 * Gridded data. Copied and modified from UCERF3's GridReader
 */

public class NZSHM22_GriddedData {

    protected static final Splitter SPLIT = Splitter.on(CharMatcher.whitespace().or(CharMatcher.anyOf(","))).omitEmptyStrings();

    protected static final double STEP = 20;
    public static final double GRID_SPACING = 1.0 / STEP;

    protected double step = STEP;
    protected double spacing = GRID_SPACING;

    protected Table<Integer, Integer, Double> table;
    protected String filename;

    public NZSHM22_GriddedData() {
    }

    public NZSHM22_GriddedData(String fileName) {
        this.filename = fileName;
        try (BufferedReader br = new BufferedReader(NZSHM22_DataUtils.getReader(filename))) {
            initTable(br);
        } catch (IOException x) {
            throw new RuntimeException(x);
        }

        System.out.println(filename + " fraction in nz test gridded " + getFractionInRegion(NewZealandRegions.NZ));
    }

    protected NZSHM22_GriddedData(Table<Integer, Integer, Double> table) {
        this.table = table;
    }

    protected NZSHM22_GriddedData(Table<Integer, Integer, Double> table, double step) {
        this.table = table;
        this.step = step;
        this.spacing = 1.0 / step;
    }

    public void writeToStream(BufferedOutputStream out) throws IOException {
        PrintWriter writer = new PrintWriter(new BufferedOutputStream(out));

        writer.println("step, " + step);

        for (Table.Cell<Integer, Integer, Double> cell : table.cellSet()) {
            writer.print(keyToLatLonComp(spacing, cell.getColumnKey()));
            writer.print(", ");
            writer.print(keyToLatLonComp(spacing, cell.getRowKey()));
            writer.print(", ");
            writer.print(cell.getValue());
            writer.println();
        }
        writer.flush();
    }

    public void initFromStream(BufferedInputStream in) throws IOException {
        initTable(new BufferedReader(new InputStreamReader(in)));
    }

    int latLonCompToKey(double step, double latLonComp) {
        return (int) Math.round(latLonComp * step);
    }

    double keyToLatLonComp(double spacing, int key) {
        return key * spacing;
    }

    /**
     * Build the data table from the input file
     * <p>
     * NZ data files have data format: lon|lat|value
     * UCERF3 data format:  lat|lon|value
     */
    protected void initTable(BufferedReader br) throws IOException {

        double inputStep;

        table = HashBasedTable.create();
        String line = br.readLine();
        if (line.startsWith("step")) {
            Iterator<String> dat = SPLIT.split(line).iterator();
            dat.next();
            inputStep = Double.parseDouble(dat.next());
            line = br.readLine();

            Preconditions.checkArgument(inputStep <= step);
        } else {
            inputStep = 10; // default
        }

        while (line != null) {
            Iterator<String> dat = SPLIT.split(line).iterator();
            double lon = Double.parseDouble(dat.next());
            double lat = Double.parseDouble(dat.next());
            double val = Double.parseDouble(dat.next());
            Location loc = new Location(lat, lon);
            if (getValue(loc) != null) {
                System.out.println("location " + loc + " is already defined in table, input data duplication!");
            }
            setValue(loc, val);
            line = br.readLine();
        }
        if (step != inputStep) {
            upSample(new NZSHM22_GriddedData(table, inputStep));
        }
    }

    public void upSample(NZSHM22_GriddedData source) {
        table = HashBasedTable.create();
        for (Location location : NewZealandRegions.NZ) {
            Double value = source.getValue(location);
            if (value != null) {
                setValue(location, value);
            }
        }
    }

    /**
     * Applies the transformer function to each location/value pair and sets the new value at the location.
     *
     * @param transformer a function that takes a location and a value and returns a new value
     * @return a new NZSHM22_GriddedData instance
     */
    public NZSHM22_GriddedData transform(BiFunction<Location, Double, Double> transformer) {
        Table<Integer, Integer, Double> result = HashBasedTable.create();
        for (Table.Cell<Integer, Integer, Double> cell : table.cellSet()) {
            double lon = keyToLatLonComp(spacing, cell.getColumnKey());
            double lat = keyToLatLonComp(spacing, cell.getRowKey());
            Location location = new Location(lat, lon);
            result.put(cell.getRowKey(), cell.getColumnKey(), transformer.apply(location, cell.getValue()));
        }
        return new NZSHM22_GriddedData(result);
    }

    /**
     * Returns all values in order corresponding to the node indices in the
     * supplied GriddedRegion.
     *
     * @return all required values
     */
    public double[] getValues(GriddedRegion region) {
        Preconditions.checkArgument(region.getSpacing() == GRID_SPACING);
        double[] values = new double[region.getNodeCount()];
        int i = 0;
        for (Location loc : region) {
            Double value = getValue(loc);
            values[i++] = (value == null) ? 0 : value;
        }
        return values;
    }

    public class GridPoint extends Location {
        int latKey;
        int lonKey;
        double value;

        public GridPoint(int latKey, int lonKey, double value) {
            super(keyToLatLonComp(spacing, latKey), keyToLatLonComp(spacing, lonKey));
            this.latKey = latKey;
            this.lonKey = lonKey;
            this.value = value;
        }

        public double getValue() {
            return value;
        }
    }

    public List<GridPoint> getPoints() {
        List<GridPoint> points = new ArrayList<>();
        for (Table.Cell<Integer, Integer, Double> cell : table.cellSet()) {
            points.add(new GridPoint(cell.getRowKey(), cell.getColumnKey(), cell.getValue()));
        }
        return points;
    }

    /**
     * Get all values for NZ
     *
     * @return
     */
    @Deprecated
    public double[] getValues() {
        return getValues(NewZealandRegions.NZ);
    }

    /**
     * Returns the spatial value at the point closest to the supplied
     * {@code Location}
     *
     * @param loc {@code Location} of interest
     * @return a value or @code null} if supplied {@code Location} is more
     * than 0.05&deg; outside the available data domain
     */
    public Double getValue(Location loc) {
        return table.get(latLonCompToKey(step, loc.getLatitude()),
                latLonCompToKey(step, loc.getLongitude()));
    }

    protected Double setValue(Location loc, double value) {
        return table.put(
                latLonCompToKey(step, loc.getLatitude()),
                latLonCompToKey(step, loc.getLongitude()),
                value);
    }

    /**
     * This returns the total sum of values inside the given gridded region
     *
     * @param region
     * @return
     */
    public double getFractionInRegion(GriddedRegion region) {
        Preconditions.checkArgument(region.getSpacing() == GRID_SPACING);
        double sum = 0;
        for (Location loc : region) {
            Double value = getValue(loc);
            if (value != null) {
                sum += getValue(loc);
            }
        }
        return sum;
    }

    /**
     * Normalises the values in the specified region.
     *
     * @param region
     */
    public void normaliseRegion(GriddedRegion region) {
        Preconditions.checkArgument(region.getSpacing() == GRID_SPACING);
        double fraction = getFractionInRegion(region);
        for (Location loc : region) {
            Double value = getValue(loc);
            if (value != null) {
                setValue(loc, getValue(loc) / fraction);
            }
        }

        System.out.println("" + filename + " normalised by " + fraction + " in region " + region.getName());
    }

    public void normaliseRegion(GriddedRegion region, double target) {
        Preconditions.checkArgument(region.getSpacing() == GRID_SPACING);
        double fraction = getFractionInRegion(region);
        for (Location loc : region) {
            Double value = getValue(loc);
            if (value != null) {
                setValue(loc, getValue(loc) / fraction * target);
            }
        }

        System.out.println("" + filename + " normalised by " + fraction + " in region " + region.getName());
    }

}

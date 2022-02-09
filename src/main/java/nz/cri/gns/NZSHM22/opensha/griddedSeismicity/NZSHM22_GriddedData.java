package nz.cri.gns.NZSHM22.opensha.griddedSeismicity;

import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiFunction;

import com.bbn.openmap.omGraphics.grid.GridData;
import com.google.common.base.*;
import org.jpedal.fonts.tt.Loca;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import nz.cri.gns.NZSHM22.opensha.data.region.NewZealandRegions;
import nz.cri.gns.NZSHM22.opensha.util.NZSHM22_DataUtils;
import org.opensha.commons.geo.Region;

/**
 * Gridded data. Copied and modifed from UCERF3's GridReader
 */

public class NZSHM22_GriddedData {

    protected static final Splitter SPLIT;

    protected static final double STEP = 10;
    protected static final double GRID_SPACING = 1.0 / STEP;
    protected static final Function<String, Double> FN_STR_TO_DBL;
    protected static final Function<Double, Integer> FN_DBL_TO_KEY;
    protected static final Function<String, Integer> FN_STR_TO_KEY;
    protected static final Function<Integer, Double> FN_KEY_TO_DBL;

    protected Table<Integer, Integer, Double> table;
    protected String filename;

    static {
        SPLIT = Splitter.on(CharMatcher.whitespace().or(CharMatcher.anyOf(","))).omitEmptyStrings();
        FN_STR_TO_DBL = new FnStrToDbl();
        FN_DBL_TO_KEY = new FnDblToKey();
        FN_STR_TO_KEY = Functions.compose(FN_DBL_TO_KEY, FN_STR_TO_DBL);
        FN_KEY_TO_DBL = new FnKeyToDbl();
    }

    public NZSHM22_GriddedData() {
    }

    public NZSHM22_GriddedData(String fileName) {
        this.filename = fileName;
        try (BufferedReader br = new BufferedReader(NZSHM22_DataUtils.getReader(filename))) {
            table = initTable(br);
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
    }

    protected NZSHM22_GriddedData(Table<Integer, Integer, Double> table) {
        this.table = table;
    }

    public void writeToStream(BufferedOutputStream out) throws IOException {
        PrintWriter writer = new PrintWriter(new BufferedOutputStream(out));

        for (Table.Cell<Integer, Integer, Double> cell : table.cellSet()) {
            writer.print(FN_KEY_TO_DBL.apply(cell.getColumnKey()));
            writer.print(", ");
            writer.print(FN_KEY_TO_DBL.apply(cell.getRowKey()));
            writer.print(", ");
            writer.print(cell.getValue());
            writer.println();
        }
        writer.flush();
    }

    public void initFromStream(BufferedInputStream in) throws IOException {
        this.table = initTable(new BufferedReader(new InputStreamReader(in)));
    }

    private static class FnStrToDbl implements Function<String, Double> {
        @Override
        public Double apply(String s) {
            return Double.valueOf(s);
        }
    }

    private static class FnDblToKey implements Function<Double, Integer> {
        @Override
        public Integer apply(Double d) {
            return (int) Math.round(d * STEP);
        }
    }

    private static class FnKeyToDbl implements Function<Integer, Double> {
        @Override
        public Double apply(Integer k) {
            return k * GRID_SPACING;
        }
    }

    /**
     * Build the data table from the input file
     * <p>
     * NZ data files have data format: lon|lat|value
     * UCERF3 data format:  lat|lon|value
     */
    protected Table<Integer, Integer, Double> initTable(BufferedReader br) throws IOException {

        Table<Integer, Integer, Double> table = HashBasedTable.create();

        String line = br.readLine();
        while (line != null) {
            Iterator<String> dat = SPLIT.split(line).iterator();
            String lon = dat.next();
            String lat = dat.next();
            Integer lonkey = FN_STR_TO_KEY.apply(lon);
            Integer latkey = FN_STR_TO_KEY.apply(lat);
            Double val = FN_STR_TO_DBL.apply(dat.next());
            //Guard
            Location loc = new Location(Double.parseDouble(lat), Double.parseDouble(lon));
            if (table.contains(latkey, lonkey))
                System.out.println("location " + loc + " is already defined in table, input data duplication!!");
            table.put(latkey, lonkey, val);
            line = br.readLine();
        }
        return table;
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
            double lon = FN_KEY_TO_DBL.apply(cell.getColumnKey());
            double lat = FN_KEY_TO_DBL.apply(cell.getRowKey());
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

    public class GridPoint {
        int latKey;
        int lonKey;
        double value;
        Location location;

        public GridPoint(int latKey, int lonKey, double value) {
            this.latKey = latKey;
            this.lonKey = lonKey;
            this.value = value;
            this.location = new Location(FN_KEY_TO_DBL.apply(latKey), FN_KEY_TO_DBL.apply(lonKey));
        }

        public Location getLocation(){
            return location;
        }

        public double getValue(){
            return value;
        }
    }

    public List<GridPoint> getPoints() {
        List<GridPoint> points = new ArrayList<>();
        for(Table.Cell<Integer, Integer, Double> cell : table.cellSet()){
            points.add(new GridPoint(cell.getRowKey(), cell.getColumnKey(), cell.getValue()));
        }
        return points;
    }

    /**
     * Get all values for NewZealandRegions.NZ_TEST_GRIDDED
     *
     * @return
     */
    public double[] getValues() {
        return getValues(new NewZealandRegions.NZ_TEST_GRIDDED());
    }

    /**
     * Returns the spatial value at the point closest to the supplied
     * {@code Location}
     *
     * @param loc {@code Location} of interest
     * @return a value or @code null} if supplied {@coed Location} is more
     * than 0.05&deg; outside the available data domain
     */
    public Double getValue(Location loc) {
        return table.get(FN_DBL_TO_KEY.apply(loc.getLatitude()),
                FN_DBL_TO_KEY.apply(loc.getLongitude()));
    }

    protected Double setValue(Location loc, double value) {
        return table.put(
                FN_DBL_TO_KEY.apply(loc.getLatitude()),
                FN_DBL_TO_KEY.apply(loc.getLongitude()),
                value);
    }

    /**
     * This returns the total sum of values inside the given gridded region
     *
     * @param region
     * @return
     */
    public double getFractionInRegion(GriddedRegion region) {
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
        double fraction = getFractionInRegion(region);
        for (Location loc : region) {
            Double value = getValue(loc);
            if (value != null) {
                setValue(loc, getValue(loc) / fraction);
            }
        }
    }

}

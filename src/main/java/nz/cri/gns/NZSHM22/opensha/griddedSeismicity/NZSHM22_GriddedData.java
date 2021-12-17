package nz.cri.gns.NZSHM22.opensha.griddedSeismicity;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Iterator;

import com.google.common.base.CharMatcher;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Splitter;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import nz.cri.gns.NZSHM22.opensha.data.region.NewZealandRegions;
import nz.cri.gns.NZSHM22.opensha.util.NZSHM22_DataUtils;

/**
 * Gridded data. Copied and modifed from UCERF3's GridReader
 */

public class NZSHM22_GriddedData {

    protected static final Splitter SPLIT;

    protected static final Function<String, Double> FN_STR_TO_DBL;
    protected static final Function<Double, Integer> FN_DBL_TO_KEY;
    protected static final Function<String, Integer> FN_STR_TO_KEY;

    protected Table<Integer, Integer, Double> table;
    protected String filename;

    static {
        SPLIT = Splitter.on(CharMatcher.whitespace()).omitEmptyStrings();
        FN_STR_TO_DBL = new FnStrToDbl();
        FN_DBL_TO_KEY = new FnDblToKey();
        FN_STR_TO_KEY = Functions.compose(FN_DBL_TO_KEY, FN_STR_TO_DBL);
    }

    public NZSHM22_GriddedData(String fileName) {
        this.filename = fileName;
        table = initTable();
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
            return (int) Math.round(d * 10);
        }
    }

    /**
     * Build the data table from the input file
     * <p>
     * NZ data files have data format: lon|lat|value
     * UCERF3 data format:  lat|lon|value
     */
    protected Table<Integer, Integer, Double> initTable() {

        Table<Integer, Integer, Double> table = HashBasedTable.create();
        String lon, lat;
        Integer lonkey, latkey;
        Double val;
        Double totalValue = 0.0;

        try {
            String DATA_DIR = "seismicityGrids";
            BufferedReader br = new BufferedReader(NZSHM22_DataUtils.getReader(DATA_DIR, filename));
            Iterator<String> dat;
            String line = br.readLine();
            while (line != null) {
                dat = SPLIT.split(line).iterator();
                lon = dat.next();
                lat = dat.next();
                lonkey = FN_STR_TO_KEY.apply(lon);
                latkey = FN_STR_TO_KEY.apply(lat);
                val = FN_STR_TO_DBL.apply(dat.next());
                //Guards
                Location loc = new Location(Double.parseDouble(lat), Double.parseDouble(lon));
//                if (!region.contains(loc))
//                    System.out.println("location " + loc + " is not within bounds of expected region");
                if (table.contains(latkey, lonkey))
                    System.out.println("location " + loc + " is already defined in table, input data duplication!!");
                totalValue += val;
                table.put(latkey, lonkey, val);
                line = br.readLine();
            }
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
        System.out.println("total in " + filename + " = " + totalValue);
        return table;
    }

    /**
     * Returns all values in order corresponding to the node indices in the
     * supplied GriddedRegion.
     *
     * @return all required values
     */
    public double[] getValues(GriddedRegion region) {
        double[] values = new double[region.getNodeCount()];
        double nullval = 0.0d; //
        // double nullval = Double.NaN; //	maybe try 0 rather than NaN for nulls ...
        int i = 0;
        for (Location loc : region) {
            Double value = getValue(loc);
//			if (value == null )
//				System.out.println("gridded region location "  + loc + " was not found in seismicity table");
            values[i++] = (value == null) ? nullval : value;
        }
        return values;
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

    /**
     * table entries should be aligned with a grid location within the region
     * so there should be no 'leftover' table entries printed by this method
     * <p>
     *   TODO: add this as a test case and identifyt the root cause.
     *   Maybe this is to do with the offset grid locations provided bu Sepi
     *   and how these do/don't align to the actual GriddedRegion layout.
     *
     * @param region
     */
    private void missingTableEntries(GriddedRegion region) {
        for (Location loc : region) {
            this.table.remove(
                    FN_DBL_TO_KEY.apply(loc.getLatitude()),
                    FN_DBL_TO_KEY.apply(loc.getLongitude()));
        }

        double tot = 0.0;
        for (Integer rowkey : this.table.rowKeySet()) {
            for (Double x : table.row(rowkey).values()) {
                tot += x;
            }
        }
        System.out.println("total missing = " + tot);
    }

}

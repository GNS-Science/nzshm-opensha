package nz.cri.gns.NZSHM22.opensha.griddedSeismicity;

import java.io.*;
import java.util.Iterator;

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

    // how many possible grid points per degree. UCERF3 uses 10
    protected static final double STEP = 10;
    // how many degrees between grid points
    public static final double GRID_SPACING = 1.0 / STEP;

    protected double step;
    protected double spacing;

    protected Table<Integer, Integer, Double> table;
    protected String filename;

    // for deserialisation
    public NZSHM22_GriddedData() {
    }

    /**
     * Loads the grid in the native resolution of the file.
     * Use NZSHM22_GriddedData(NZSHM22_GriddedData original) to upsample if required
     * @param fileName
     */
    private NZSHM22_GriddedData(String fileName) {
        this.filename = fileName;
        try (BufferedReader br = new BufferedReader(NZSHM22_DataUtils.getReader(filename))) {
            initTable(br);
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
    }

    /**
     * Upsamples the original grid to STEP
     * @param original
     */
    protected NZSHM22_GriddedData(NZSHM22_GriddedData original, double step) {
        setStep(step);
        this.filename = original.filename;
        upSample(original);
    }

    public static NZSHM22_GriddedData fromFile(String fileName){
        NZSHM22_GriddedData data = new NZSHM22_GriddedData(fileName);
        if(data.step != STEP){
            return new NZSHM22_GriddedData(data, STEP);
        } else {
            return data;
        }
    }

    /**
     * For debugging
     * @param filename
     * @return
     */
    protected static NZSHM22_GriddedData fromFileNativeStep(String filename){
        return new NZSHM22_GriddedData(filename);
    }

    protected void setStep(double step){
        this.step = step;
        this.spacing = 1.0 / step;
    }

    public double getStep(){
        return step;
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

        table = HashBasedTable.create();
        String line = br.readLine();
        if (line.startsWith("step")) {
            Iterator<String> dat = SPLIT.split(line).iterator();
            dat.next();
            setStep(Double.parseDouble(dat.next()));
            line = br.readLine();
        } else {
            setStep(10); // UCERF3 constant
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
    }

    protected void upSample(NZSHM22_GriddedData source) {
        table = HashBasedTable.create();
        GriddedRegion region = new GriddedRegion(new NewZealandRegions.NZ_RECTANGLE(), spacing, GriddedRegion.ANCHOR_0_0);
        for (Location location : region) {
            Double value = source.getValue(location);
            if (value != null) {
                setValue(location, value);
            }
        }
    }

    /**
     * Returns all values in order corresponding to the node indices in the
     * supplied GriddedRegion.
     *
     * @return all required values
     */
    public double[] getValues(GriddedRegion region) {
        double[] values = new double[region.getNodeCount()];
        int i = 0;
        for (Location loc : region) {
            Double value = getValue(loc);
            values[i++] = (value == null) ? 0 : value;
        }
        return values;
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
        Preconditions.checkArgument(region.getSpacing() == spacing);
        double fraction = getFractionInRegion(region);
        for (Location loc : region) {
            Double value = getValue(loc);
            if (value != null) {
                setValue(loc, getValue(loc) / fraction);
            }
        }
    }

}

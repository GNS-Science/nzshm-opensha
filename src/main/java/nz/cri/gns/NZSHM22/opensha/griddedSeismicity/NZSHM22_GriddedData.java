package nz.cri.gns.NZSHM22.opensha.griddedSeismicity;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import nz.cri.gns.NZSHM22.opensha.data.region.NewZealandRegions;
import nz.cri.gns.NZSHM22.opensha.util.NZSHM22_DataUtils;
import nz.cri.gns.NZSHM22.opensha.util.SimpleGeoJsonBuilder;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;

/** Gridded data. Copied and modified from UCERF3's GridReader */
public class NZSHM22_GriddedData {

    // how many possible grid points per degree. UCERF3 uses 10
    public static final int STEP = 10;
    // how many degrees between grid points
    public static final double GRID_SPACING = 1.0 / STEP;

    protected int step;
    protected double spacing;

    protected Table<Integer, Integer, Double> table;
    protected String filename;

    protected List<Location> gridPoints;

    // for deserialisation
    public NZSHM22_GriddedData() {
        setStep(STEP);
    }

    /**
     * Loads the grid in the native resolution of the file. Use
     * NZSHM22_GriddedData(NZSHM22_GriddedData original) to upsample if required
     *
     * @param fileName
     */
    private NZSHM22_GriddedData(String fileName) {
        setStep(STEP);
        this.filename = fileName;
        try (InputStream in = NZSHM22_DataUtils.locateResourceAsStream(filename)) {
            initFromStream(in);
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
    }

    /**
     * An up- or down-sampled version of original.
     *
     * @param original
     */
    public NZSHM22_GriddedData(NZSHM22_GriddedData original, int step) {
        setStep(step);
        this.filename = original.filename;
        if (step >= original.getStep()) {
            upSample(original);
            if (step != original.getStep()) {
                normaliseRegion(getNativeRegion());
            }
        } else if (step < original.getStep()) {
            downSample(original);
        }
    }

    public static NZSHM22_GriddedData fromFile(String fileName) {
        NZSHM22_GriddedData data = new NZSHM22_GriddedData(fileName);
        if (data.step != STEP) {
            return new NZSHM22_GriddedData(data, STEP);
        } else {
            return data;
        }
    }

    public static NZSHM22_GriddedData reSample(NZSHM22_GriddedData source, int newStep) {
        return new NZSHM22_GriddedData(source, newStep);
    }

    /**
     * For debugging
     *
     * @param filename
     * @return
     */
    protected static NZSHM22_GriddedData fromFileNativeStep(String filename) {
        return new NZSHM22_GriddedData(filename);
    }

    protected void setStep(int step) {
        this.step = step;
        this.spacing = 1.0 / step;
    }

    public int getStep() {
        return step;
    }

    public double getSpacing() {
        return spacing;
    }

    public void writeToStream(OutputStream out) throws IOException {
        toCsv().writeToStream(out);
    }

    public CSVFile<Double> toCsv() {
        CSVFile<Double> csv = new CSVFile<>(false);
        for (Location location : getGridPoints()) {
            csv.addLine(location.getLatitude(), location.getLongitude(), getValue(location));
        }
        return csv;
    }

    public void addToCsv(CSVFile<Double> csv) {
        for (List<Double> row : csv) {
            row.add(getValue(new Location(row.get(0), row.get(1))));
        }
    }

    public void writeToGeoJson(SimpleGeoJsonBuilder builder) {
        for (Table.Cell<Integer, Integer, Double> cell : table.cellSet()) {
            builder.addLocation(
                    new Location(
                            keyToLatLonComp(spacing, cell.getRowKey()),
                            keyToLatLonComp(spacing, cell.getColumnKey())));
        }
    }

    public void initFromStream(InputStream in) throws IOException {
        CSVFile<Double> csv = CSVFile.readStreamNumeric(in, true, -1, 0);
        initTable(csv);
    }

    public void initFromCsv(CSVFile<Double> csv) {
        initTable(csv);
    }

    int latLonCompToKey(double step, double latLonComp) {
        return (int) Math.round(latLonComp * step);
    }

    double keyToLatLonComp(double spacing, int key) {
        return key * spacing;
    }

    public Location snapToGrid(Location location) {
        return new Location(
                keyToLatLonComp(spacing, latLonCompToKey(step, location.getLatitude())),
                keyToLatLonComp(spacing, latLonCompToKey(step, location.getLongitude())));
    }

    /**
     * Build the data table from a CS file
     *
     * <p>NZ data files have data format: lat,lon,value UCERF3 data format: lat|lon|value
     */
    protected void initTable(CSVFile<Double> csv) {
        table = HashBasedTable.create();
        for (List<Double> line : csv) {
            Location loc = new Location(line.get(0), line.get(1));
            if (getValue(loc) != null) {
                System.out.println(
                        "location "
                                + loc
                                + " is already defined in table, input data duplication!");
            }
            setValue(loc, line.get(2));
        }
    }

    /**
     * Returns a GriddedRegion in the native resolution of this gridded data set.
     *
     * @return
     */
    public GriddedRegion getNativeRegion() {
        return new GriddedRegion(
                new NewZealandRegions.NZ_RECTANGLE(), spacing, GriddedRegion.ANCHOR_0_0);
    }

    /**
     * Returns the grid bin for the specified location. It is assumed that location is an actual
     * grid point.
     *
     * @param location
     * @return
     */
    public Region getBin(Location location) {
        return new Region(
                new Location(location.getLatitude() - spacing, location.getLongitude() - spacing),
                new Location(location.getLatitude() + spacing, location.getLongitude() + spacing));
    }

    /**
     * Returns all gridpoints for which there is a value
     *
     * @return
     */
    public List<Location> getGridPoints() {
        if (gridPoints == null) {
            List<Location> result = new ArrayList<>();
            for (Location location : getNativeRegion()) {
                if (getValue(location) != null) {
                    result.add(location);
                }
            }
            gridPoints = result;
        }
        return gridPoints;
    }

    public List<Region> getGridBins() {
        return getGridPoints().stream().map(this::getBin).collect(Collectors.toList());
    }

    protected void upSample(NZSHM22_GriddedData source) {
        table = HashBasedTable.create();
        for (Location location : getNativeRegion()) {
            Double value = source.getValue(location);
            if (value != null) {
                setValue(location, value);
            }
        }
    }

    protected void downSample(NZSHM22_GriddedData source) {
        table = HashBasedTable.create();
        for (Location location : source.getNativeRegion()) {
            Double sourceValue = source.getValue(location);
            if (sourceValue != null) {
                Double myValue = getValue(location);
                if (myValue == null) {
                    setValue(location, sourceValue);
                } else {
                    setValue(location, myValue + sourceValue);
                }
            }
        }
    }

    /**
     * Returns all values in order corresponding to the node indices in the supplied GriddedRegion.
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
     * Returns the spatial value at the point closest to the supplied {@code Location}
     *
     * @param loc {@code Location} of interest
     * @return a value or @code null} if supplied {@code Location} is more than 0.05&deg; outside
     *     the available data domain
     */
    public Double getValue(Location loc) {
        return table.get(
                latLonCompToKey(step, loc.getLatitude()),
                latLonCompToKey(step, loc.getLongitude()));
    }

    public Double setValue(Location loc, double value) {
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

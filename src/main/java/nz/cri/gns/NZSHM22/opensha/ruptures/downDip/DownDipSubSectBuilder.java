package nz.cri.gns.NZSHM22.opensha.ruptures.downDip;

import com.google.common.base.Preconditions;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nz.cri.gns.NZSHM22.opensha.faults.FaultSectionList;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionPredicate;
import nz.cri.gns.NZSHM22.opensha.ruptures.FaultSectionProperties;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.geo.Location;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;

public class DownDipSubSectBuilder {

    // [column, row]
    private FaultSection[][] subSects;
    private String sectName;
    private int parentID;
    private Map<Integer, Integer> idToRowMap;
    private Map<Integer, Integer> idToColMap;

    public static DownDipSubSectBuilder fromList(
            List<FaultSection> sections, String sectName, int parentId) {
        int maxRow = 0;
        int maxCol = 0;
        for (FaultSection section : sections) {
            FaultSectionProperties props = new FaultSectionProperties(section);
            maxRow = Math.max(maxRow, props.getRowIndex());
            maxCol = Math.max(maxCol, props.getColIndex());
        }
        DownDipSubSectBuilder result =
                new DownDipSubSectBuilder(maxCol, maxRow, sectName, parentId);

        for (FaultSection section : sections) {
            FaultSectionProperties props = new FaultSectionProperties(section);
            result.subSects[props.getColIndex()][props.getRowIndex()] = section;
            result.idToRowMap.put(section.getSectionId(), props.getRowIndex());
            result.idToColMap.put(section.getSectionId(), props.getColIndex());
        }
        return result;
    }

    // private constructor to aid the fromList method
    private DownDipSubSectBuilder(int maxCol, int maxRow, String sectName, int parentId) {
        subSects = new FaultSection[maxCol + 1][maxRow + 1];
        this.sectName = sectName;
        this.parentID = parentId;
        idToColMap = new HashMap<>();
        idToRowMap = new HashMap<>();
    }

    /**
     * Loads a downdip fault from a CSV file and adds all sections to the subSections list passed to
     * the registry constructor.
     *
     * @param subSections the subsection list to be populated
     * @param id The imaginary parent fault id.
     * @param name The name of the fault.
     * @param in The InputStream
     * @throws IOException
     */
    public static void loadFromStream(
            FaultSectionList subSections,
            int id,
            String name,
            InputStream in,
            PartitionPredicate partition)
            throws IOException {
        FaultSectionPrefData interfaceParentSection = new FaultSectionPrefData();
        interfaceParentSection.setSectionId(id);
        interfaceParentSection.setSectionName(name);
        interfaceParentSection.setAveDip(1); // otherwise the FaultSectionList will complain
        subSections.addParent(interfaceParentSection);

        DownDipSubSectBuilder downDipBuilder =
                new DownDipSubSectBuilder(
                        name, interfaceParentSection, subSections.getSafeId(), in, partition);
        subSections.addAll(downDipBuilder.getSubSectsList());
    }

    private static FaultSection buildFSD(
            int sectionId,
            FaultTrace trace,
            double upper,
            double lower,
            double dip,
            double slipRate) {
        FaultSectionPrefData fsd = new FaultSectionPrefData();

        // hack for testing
        // float divisor = if (sectionId < 100) ?
        //		double aveLongTermSlipRate = slipRateGenerator.nextDouble() * 0.2;

        /* a default of -1000 for no slip rate data */
        // South: -1000, centre:-2000, north:-3000
        if (slipRate == -1000.0) fsd.setAveSlipRate(0.0);
        if (slipRate == -2000.0) fsd.setAveSlipRate(0.0);
        if (slipRate == -3000.0) fsd.setAveSlipRate(50.0);
        if (slipRate >= 0.0) fsd.setAveSlipRate(slipRate);

        fsd.setSectionId(sectionId);
        fsd.setFaultTrace(trace);
        fsd.setAveUpperDepth(upper);
        fsd.setAveLowerDepth(lower);
        fsd.setAveDip(dip);
        fsd.setAveRake(90);
        fsd.setDipDirection((float) trace.getDipDirection());

        return new GeoJSONFaultSection(fsd);
    }

    private static double fixLongitudeOffset(String longitude) {
        double lon = Float.parseFloat((String) longitude);
        if (lon < 0) lon += 360.0d;
        return lon;
    }

    private static FaultSection buildFaultSectionFromCsvRow(int sectionId, List<String> row) {
        // along_strike_index, down_dip_index, lon1(deg), lat1(deg), lon2(deg), lat2(deg), dip
        // (deg), top_depth (km), bottom_depth (km),neighbours
        // [3, 9, 172.05718990191556, -43.02716092186062, 171.94629898533478, -43.06580050196082,
        // 12.05019252859843, 36.59042136801586, 38.67810629370413, [(4, 9), (3, 10), (4, 10)]]
        FaultTrace trace =
                new FaultTrace("SubductionTile_" + (String) row.get(0) + "_" + (String) row.get(1));
        trace.add(
                new Location(
                        Float.parseFloat((String) row.get(3)),
                        fixLongitudeOffset(row.get(2)),
                        Float.parseFloat((String) row.get(7))));
        trace.add(
                new Location(
                        Float.parseFloat((String) row.get(5)), // lat
                        fixLongitudeOffset(row.get(4)), // lon
                        Float.parseFloat((String) row.get(7))) // top_depth (km)
                );

        return buildFSD(
                sectionId,
                trace,
                Float.parseFloat((String) row.get(7)), // top
                Float.parseFloat((String) row.get(8)), // bottom
                Float.parseFloat((String) row.get(6)), // dip
                Float.parseFloat((String) row.get(9))); // slip_deficit (or slip rate)
    }

    /**
     * Sets up the subSects array with the size required by the CSV data.
     *
     * @param csv
     */
    private FaultSection[][] setUpSubSectsArray(CSVFile<String> csv) {
        int colCount = 0;
        int rowCount = 0;
        for (int row = 1; row < csv.getNumRows(); row++) {
            List<String> csvLine = csv.getLine(row);
            colCount = Math.max(Integer.parseInt(csvLine.get(0)), colCount);
            rowCount = Math.max(Integer.parseInt(csvLine.get(1)), rowCount);
        }
        return new FaultSection[colCount + 1][rowCount + 1];
    }

    /*
     * a DownDip Builder is needed for the permutation strategy
     *
     */
    public DownDipSubSectBuilder(FaultSection parentSection) {
        this.parentID = parentSection.getSectionId();
    }

    /*
     * Build subsections from csv data (ex Hikurangi)
     *
     */
    public DownDipSubSectBuilder(
            String sectName,
            FaultSection parentSection,
            int startID,
            InputStream csvStream,
            PartitionPredicate partition)
            throws IOException {
        this.sectName = sectName;
        this.parentID = parentSection.getSectionId();

        int colIndex = 0;
        int rowIndex = 0;

        CSVFile<String> csv = CSVFile.readStream(csvStream, false);

        subSects = setUpSubSectsArray(csv);
        idToRowMap = new HashMap<>();
        idToColMap = new HashMap<>();

        for (int row = 1; row < csv.getNumRows(); row++) {
            List<String> csvLine = csv.getLine(row);
            colIndex = Integer.parseInt(csvLine.get(0));
            rowIndex = Integer.parseInt(csvLine.get(1));
            FaultSection fs = buildFaultSectionFromCsvRow(startID, csvLine);
            fs.setParentSectionId(parentSection.getSectionId());
            fs.setParentSectionName(parentSection.getSectionName());
            fs.setSectionName(
                    parentSection.getSectionName() + "; col: " + colIndex + ", row: " + rowIndex);
            FaultSectionProperties props = new FaultSectionProperties(fs);
            props.setPartition(partition);
            props.setRowIndex(rowIndex);
            props.setColIndex(colIndex);
            props.setDownDipBuilder(this);
            try {
                subSects[colIndex][rowIndex] = fs;
                idToRowMap.put(startID, rowIndex);
                idToColMap.put(startID, colIndex);
                startID++;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public FaultSection[][] getSubSects() {
        return subSects;
    }

    public FaultSection getSubSect(int row, int col) {
        if (subSects.length > col) {
            FaultSection[] rows = subSects[col];
            if (rows.length > row) {
                return rows[row];
            }
        }
        return null;
    }

    public int getRow(FaultSection sect) {
        Preconditions.checkArgument(
                idToRowMap.containsKey(sect.getSectionId()),
                "Unexpected sub sect: %s. %s",
                sect.getSectionId(),
                sect.getSectionName());
        return idToRowMap.get(sect.getSectionId());
    }

    public int getColumn(FaultSection sect) {
        Preconditions.checkArgument(
                idToColMap.containsKey(sect.getSectionId()),
                "Unexpected sub sect: %s. %s",
                sect.getSectionId(),
                sect.getSectionName());
        return idToColMap.get(sect.getSectionId());
    }

    public int getNumCols() {
        return subSects.length;
    }

    public int getNumRows() {
        return subSects[0].length;
    }

    public List<FaultSection> getNeighbors(FaultSection sect) {
        int row = getRow(sect);
        int col = getColumn(sect);
        // include sections above, below, left, and right
        List<FaultSection> neighbors = new ArrayList<>();
        FaultSection candidate;
        if (row > 0) {
            // above
            candidate = subSects[col][row - 1];
            if (candidate != null) {
                neighbors.add(candidate);
            }
        }
        if (row < subSects[col].length - 1) {
            // below
            candidate = subSects[col][row + 1];
            if (candidate != null) {
                neighbors.add(candidate);
            }
        }
        if (col > 0) {
            // left
            candidate = subSects[col - 1][row];
            if (candidate != null) {
                neighbors.add(candidate);
            }
        }
        if (col < subSects.length - 1) {
            // right
            candidate = subSects[col + 1][row];
            if (candidate != null) {
                neighbors.add(candidate);
            }
        }
        return neighbors;
    }

    public List<Integer> getNeighbors(int row, int col) {
        // include sections above, below, left, and right
        List<Integer> neighbors = new ArrayList<>();
        if (row > 0)
            // above
            neighbors.add(subSects[col][row - 1].getSectionId());
        if (row < subSects[col].length - 1)
            // below
            neighbors.add(subSects[col][row + 1].getSectionId());
        if (col > 0)
            // left
            neighbors.add(subSects[col - 1][row].getSectionId());
        if (col < subSects.length - 1)
            // right
            neighbors.add(subSects[col + 1][row].getSectionId());
        return neighbors;
    }

    public String getSectName() {
        return sectName;
    }

    public int getParentID() {
        return parentID;
    }

    public List<FaultSection> getSubSectsList() {
        List<FaultSection> sects = new ArrayList<>();
        for (int col = 0; col < subSects.length; col++)
            for (int row = 0; row < subSects[col].length; row++) {
                FaultSection fs = subSects[col][row];
                if (fs != null) sects.add(fs);
            }
        return sects;
    }
}

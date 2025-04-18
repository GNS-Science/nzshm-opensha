package nz.cri.gns.NZSHM22.opensha.ruptures.downDip;

import com.google.common.base.Preconditions;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nz.cri.gns.NZSHM22.opensha.faults.FaultSectionList;
import nz.cri.gns.NZSHM22.opensha.ruptures.DownDipFaultSection;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.LocationVector;
import org.opensha.commons.util.FaultUtils;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.SimpleFaultData;

public class DownDipSubSectBuilder {

    // [column, row]
    private DownDipFaultSection[][] subSects;
    private String sectName;
    private int parentID;
    private Map<Integer, Integer> idToRowMap;
    private Map<Integer, Integer> idToColMap;

    public static DownDipSubSectBuilder fromList(
            List<DownDipFaultSection> sections, String sectName, int parentId) {
        int maxRow = 0;
        int maxCol = 0;
        for (DownDipFaultSection section : sections) {
            maxRow = Math.max(maxRow, section.getRowIndex());
            maxCol = Math.max(maxCol, section.getColIndex());
        }
        DownDipSubSectBuilder result =
                new DownDipSubSectBuilder(maxCol, maxRow, sectName, parentId);

        for (DownDipFaultSection section : sections) {
            result.subSects[section.getColIndex()][section.getRowIndex()] = section;
            result.idToRowMap.put(section.getSectionId(), section.getRowIndex());
            result.idToColMap.put(section.getSectionId(), section.getColIndex());
        }
        return result;
    }

    // private constructor to aid the fromList method
    private DownDipSubSectBuilder(int maxCol, int maxRow, String sectName, int parentId) {
        subSects = new DownDipFaultSection[maxCol + 1][maxRow + 1];
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
            FaultSectionList subSections, int id, String name, InputStream in) throws IOException {
        FaultSectionPrefData interfaceParentSection = new FaultSectionPrefData();
        interfaceParentSection.setSectionId(id);
        interfaceParentSection.setSectionName(name);
        interfaceParentSection.setAveDip(1); // otherwise the FaultSectionList will complain
        subSections.addParent(interfaceParentSection);

        DownDipSubSectBuilder downDipBuilder =
                new DownDipSubSectBuilder(
                        name, interfaceParentSection, subSections.getSafeId(), in);
        subSections.addAll(downDipBuilder.getSubSectsList());
    }

    private static DownDipFaultSection buildFSD(
            int sectionId,
            FaultTrace trace,
            double upper,
            double lower,
            double dip,
            double slipRate) {
        DownDipFaultSection fsd = new DownDipFaultSection();

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
        return fsd.clone();
    }

    private static double fixLongitudeOffset(String longitude) {
        double lon = Float.parseFloat((String) longitude);
        if (lon < 0) lon += 360.0d;
        return lon;
    }

    private static DownDipFaultSection buildFaultSectionFromCsvRow(
            int sectionId, List<String> row) {
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
    private DownDipFaultSection[][] setUpSubSectsArray(CSVFile<String> csv) {
        int colCount = 0;
        int rowCount = 0;
        for (int row = 1; row < csv.getNumRows(); row++) {
            List<String> csvLine = csv.getLine(row);
            colCount = Math.max(Integer.parseInt(csvLine.get(0)), colCount);
            rowCount = Math.max(Integer.parseInt(csvLine.get(1)), rowCount);
        }
        return new DownDipFaultSection[colCount + 1][rowCount + 1];
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
            String sectName, FaultSection parentSection, int startID, InputStream csvStream)
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
            DownDipFaultSection fs = buildFaultSectionFromCsvRow(startID, csvLine);
            fs.setParentSectionId(parentSection.getSectionId());
            fs.setParentSectionName(parentSection.getSectionName());
            fs.setSectionName(
                    parentSection.getSectionName() + "; col: " + colIndex + ", row: " + rowIndex);
            fs.setRowIndex(rowIndex);
            fs.setColIndex(colIndex);
            fs.setBuilder(this);
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

    public DownDipSubSectBuilder(
            String sectName,
            int parentID,
            int startID,
            SimpleFaultData faultData,
            double aveRake,
            int numAlongStrike,
            int numDownDip) {
        this.sectName = sectName;
        this.parentID = parentID;
        Preconditions.checkArgument(numAlongStrike > 1);
        Preconditions.checkArgument(numDownDip > 1);

        FaultTrace trace = faultData.getFaultTrace();
        double maxSubSectionLen = trace.getTraceLength() / (double) numAlongStrike;
        System.out.println(
                "along-strike distance: tot="
                        + (float) trace.getTraceLength()
                        + ", each: "
                        + (float) maxSubSectionLen);
        ;
        List<FaultTrace> tracesAlongStrike =
                FaultUtils.getEqualLengthSubsectionTraces(trace, maxSubSectionLen, numAlongStrike);
        Preconditions.checkState(tracesAlongStrike.size() == numAlongStrike);

        double lowerDepth = faultData.getLowerSeismogenicDepth();
        double upperDepth = faultData.getUpperSeismogenicDepth();
        double dip = faultData.getAveDip();

        double vertTot = (lowerDepth - upperDepth);
        double vertEach = vertTot / (double) numDownDip;
        System.out.println(
                "down-dip vertical distance: tot="
                        + (float) vertTot
                        + ", each: "
                        + (float) vertEach);
        double dipRad = Math.toRadians(dip);
        double horzTot = vertTot / Math.tan(dipRad);
        double horzEach = horzTot / (double) numDownDip;
        System.out.println(
                "down-dip horizontal distance: tot="
                        + (float) horzTot
                        + ", each: "
                        + (float) horzEach);
        double ddwTot = Math.sqrt(vertTot * vertTot + horzTot * horzTot);
        double ddwEach = ddwTot / (double) numDownDip;
        System.out.println("down-dip width: tot=" + (float) ddwTot + ", each: " + (float) ddwEach);

        double dipDir = faultData.getAveDipDir();
        if (Double.isNaN(dipDir)) dipDir = trace.getDipDirection(); // degrees

        subSects = new DownDipFaultSection[numAlongStrike][numDownDip];
        idToRowMap = new HashMap<>();
        idToColMap = new HashMap<>();
        for (int col = 0; col < numAlongStrike; col++) {
            for (int row = 0; row < numDownDip; row++) {
                String name = sectName + ", Subsection " + col + "." + row;
                FaultTrace subTrace = tracesAlongStrike.get(col);
                if (row > 0) {
                    // move it down dip
                    FaultTrace relocated = new FaultTrace(name);
                    for (Location loc : subTrace) {
                        LocationVector v =
                                new LocationVector(dipDir, row * horzEach, row * vertEach);
                        relocated.add(LocationUtils.location(loc, v));
                    }
                    subTrace = relocated;
                }
                //				if (col == 0) {
                //					System.out.println("ROW "+row);
                //					for (Location loc : subTrace)
                //						System.out.println("\t"+loc);
                //				}
                double subUpperDepth = upperDepth + vertEach * row;
                double subLowerDepth = subUpperDepth + vertEach;
                subSects[col][row] = new DownDipFaultSection();
                idToRowMap.put(startID, row);
                idToColMap.put(startID, col);
                subSects[col][row].setSectionId(startID++);
                subSects[col][row].setSectionName(name);
                subSects[col][row].setParentSectionId(parentID);
                subSects[col][row].setParentSectionName(sectName);
                subSects[col][row].setFaultTrace(subTrace);
                subSects[col][row].setAveUpperDepth(subUpperDepth);
                subSects[col][row].setAveLowerDepth(subLowerDepth);
                subSects[col][row].setAseismicSlipFactor(0d);
                subSects[col][row].setDipDirection((float) dipDir);
                subSects[col][row].setAveDip(dip);
                subSects[col][row].setAveRake(aveRake);
                subSects[col][row].setRowIndex(row).setColIndex(col);
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
        DownDipFaultSection fs = new DownDipFaultSection();
        for (int col = 0; col < subSects.length; col++)
            for (int row = 0; row < subSects[col].length; row++) {
                fs = subSects[col][row];
                if (fs != null) sects.add(fs);
            }
        return sects;
    }

    public static void main(String[] args) {
        String sectName = "Test SubSect Down-Dip Fault";
        int sectID = 0;
        int startID = 0;
        double upperDepth = 0d;
        double lowerDepth = 30d;
        double dip = 35d;
        int numDownDip = 4;
        int numAlongStrike = 10;
        FaultTrace trace = new FaultTrace(sectName);
        trace.add(new Location(34, -118, upperDepth));
        trace.add(new Location(34.1, -118.25, upperDepth));
        trace.add(new Location(34.15, -118.5, upperDepth));
        trace.add(new Location(34.1, -118.75, upperDepth));
        trace.add(new Location(34, -119, upperDepth));

        SimpleFaultData faultData = new SimpleFaultData(dip, lowerDepth, upperDepth, trace);
        double aveRake = 90d;

        DownDipSubSectBuilder builder =
                new DownDipSubSectBuilder(
                        sectName, sectID, startID, faultData, aveRake, numAlongStrike, numDownDip);

        for (int col = 0; col < numAlongStrike; col++) {
            for (int row = 0; row < numDownDip; row++) {
                List<Integer> conns = builder.getNeighbors(row, col);
                System.out.println(
                        "Sect "
                                + builder.subSects[col][row].getSectionId()
                                + " at row="
                                + row
                                + ",col="
                                + col
                                + " has "
                                + conns.size()
                                + " neighbors\n");
                System.out.println(builder.subSects[col][row].toString());
            }
        }
    }
}

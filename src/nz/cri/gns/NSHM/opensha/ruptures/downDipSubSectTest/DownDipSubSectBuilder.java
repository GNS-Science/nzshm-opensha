package nz.cri.gns.NSHM.opensha.ruptures.downDipSubSectTest;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.LocationVector;
import org.opensha.commons.util.FaultUtils;
//import org.opensha.refFaultParamDb.vo.FaultSection;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.SimpleFaultData;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

public class DownDipSubSectBuilder {
	
	// [column, row]
	private FaultSectionPrefData[][] subSects;
	private String sectName;
	private int parentID;
	private Map<Integer, Integer> idToRowMap;
	private Map<Integer, Integer> idToColMap;
	
	
	private static FaultSectionPrefData buildFSD(int sectionId, FaultTrace trace, double upper, double lower, double dip) {
		FaultSectionPrefData fsd = new FaultSectionPrefData();
		fsd.setSectionId(sectionId);
		fsd.setFaultTrace(trace);
		fsd.setAveUpperDepth(upper);
		fsd.setAveLowerDepth(lower);
		fsd.setAveDip(dip);
		fsd.setDipDirection((float) trace.getDipDirection());
		return fsd.clone();
	}
	
	private static FaultSectionPrefData buildFaultSectionFromCsvRow(int sectionId, List<String> row) {
		// along_strike_index, down_dip_index, lon1(deg), lat1(deg), lon2(deg), lat2(deg), dip (deg), top_depth (km), bottom_depth (km),neighbours
		// [3, 9, 172.05718990191556, -43.02716092186062, 171.94629898533478, -43.06580050196082, 12.05019252859843, 36.59042136801586, 38.67810629370413, [(4, 9), (3, 10), (4, 10)]]	
		FaultTrace trace = new FaultTrace("SubductionTile_" + (String)row.get(0) + "_" + (String)row.get(1) );
		trace.add(new Location(Float.parseFloat((String)row.get(3)), 
			Float.parseFloat((String)row.get(2)), 
			Float.parseFloat((String)row.get(7)))
		);
		trace.add(new Location(Float.parseFloat((String)row.get(5)),    //lat
			Float.parseFloat((String)row.get(4)), 						//lon
			Float.parseFloat((String)row.get(7)))						//top_depth (km)
		);
	
		return buildFSD(sectionId, trace, 
			Float.parseFloat((String)row.get(7)), //top
			Float.parseFloat((String)row.get(8)), //bottom
			Float.parseFloat((String)row.get(6))); //dip	
	}
	/*
	 * Build subsections from csv data (ex Hikurangi)
	 * 
	 */
	public DownDipSubSectBuilder(String sectName, FaultSection parentSection, int startID, InputStream csvStream) throws IOException {
		this.sectName = sectName;
		this.parentID = parentSection.getSectionId();
		Integer colIndex = 0;
		Integer rowIndex = 0;
		
		CSVFile<String> csv = CSVFile.readStream(csvStream, false);
		
		subSects = new FaultSectionPrefData[35][35];
		idToRowMap = new HashMap<>();
		idToColMap = new HashMap<>();
		
		for (int row=1; row<csv.getNumRows(); row++) {
			List<String> csvLine = csv.getLine(row);
			FaultSectionPrefData fs = buildFaultSectionFromCsvRow(startID, csvLine);
			fs.setParentSectionId(parentSection.getSectionId());
			fs.setParentSectionName(parentSection.getSectionName());
			colIndex = Integer.parseInt(csvLine.get(0));
			rowIndex = Integer.parseInt(csvLine.get(1));
			
			try  {
				subSects[colIndex][rowIndex] = fs;
				idToRowMap.put(startID, rowIndex);
				idToColMap.put(startID, colIndex);
				startID++;
		    } catch (Exception e) {
		        	e.printStackTrace();
			}
		}		
	}
	
		
	public DownDipSubSectBuilder(String sectName, int parentID, int startID,
			SimpleFaultData faultData, double aveRake, int numAlongStrike, int numDownDip) {
		this.sectName = sectName;
		this.parentID = parentID;
		Preconditions.checkArgument(numAlongStrike > 1);
		Preconditions.checkArgument(numDownDip > 1);
		
		FaultTrace trace = faultData.getFaultTrace();
		double maxSubSectionLen = trace.getTraceLength()/(double)numAlongStrike;
		System.out.println("along-strike distance: tot="+(float)trace.getTraceLength()
			+", each: "+(float)maxSubSectionLen);;
		List<FaultTrace> tracesAlongStrike =
				FaultUtils.getEqualLengthSubsectionTraces(trace, maxSubSectionLen, numAlongStrike);
		Preconditions.checkState(tracesAlongStrike.size() == numAlongStrike);
		
		double lowerDepth = faultData.getLowerSeismogenicDepth();
		double upperDepth = faultData.getUpperSeismogenicDepth();
		double dip = faultData.getAveDip();
		
		double vertTot = (lowerDepth - upperDepth);
		double vertEach = vertTot/(double)numDownDip;
		System.out.println("down-dip vertical distance: tot="+(float)vertTot+", each: "+(float)vertEach);
		double dipRad = Math.toRadians(dip);
		double horzTot = vertTot/Math.tan(dipRad);
		double horzEach = horzTot/(double)numDownDip;
		System.out.println("down-dip horizontal distance: tot="+(float)horzTot+", each: "+(float)horzEach);
		double ddwTot = Math.sqrt(vertTot*vertTot + horzTot*horzTot);
		double ddwEach = ddwTot/(double)numDownDip;
		System.out.println("down-dip width: tot="+(float)ddwTot+", each: "+(float)ddwEach);
		
		double dipDir = faultData.getAveDipDir();
		if (Double.isNaN(dipDir))
			dipDir = trace.getDipDirection(); // degrees
		
		subSects = new FaultSectionPrefData[numAlongStrike][numDownDip];
		idToRowMap = new HashMap<>();
		idToColMap = new HashMap<>();
		for (int col=0; col<numAlongStrike; col++) {
			for (int row=0; row<numDownDip; row++) {
				String name = sectName+", Subsection "+col+"."+row;
				FaultTrace subTrace = tracesAlongStrike.get(col);
				if (row > 0) {
					// move it down dip
					FaultTrace relocated = new FaultTrace(name);
					for (Location loc : subTrace) {
						LocationVector v = new LocationVector(dipDir, row*horzEach, row*vertEach);
						relocated.add(LocationUtils.location(loc, v));
					}
					subTrace = relocated;
				}
//				if (col == 0) {
//					System.out.println("ROW "+row);
//					for (Location loc : subTrace)
//						System.out.println("\t"+loc);
//				}
				double subUpperDepth = upperDepth + vertEach*row;
				double subLowerDepth = subUpperDepth + vertEach;
				subSects[col][row] = new FaultSectionPrefData();
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
				subSects[col][row].setDipDirection((float)dipDir);
				subSects[col][row].setAveDip(dip);
				subSects[col][row].setAveRake(aveRake);
			}
		}
	}
	
	public FaultSection[][] getSubSects() {
		return subSects;
	}
	
	public FaultSection getSubSect(int row, int col) {
		return subSects[col][row];
	}
	
	public int getRow(FaultSection sect) {
		Preconditions.checkArgument(idToRowMap.containsKey(sect.getSectionId()),
				"Unexpected sub sect: %s. %s", sect.getSectionId(), sect.getSectionName());
		return idToRowMap.get(sect.getSectionId());
	}
	
	public int getColumn(FaultSection sect) {
		Preconditions.checkArgument(idToColMap.containsKey(sect.getSectionId()),
				"Unexpected sub sect: %s. %s", sect.getSectionId(), sect.getSectionName());
		return idToColMap.get(sect.getSectionId());
	}
	
	public int getNumCols() {
		return subSects.length;
	}
	
	public int getNumRows() {
		return subSects[0].length;
	}
	
	public List<Integer> getNeighbors(FaultSection sect) {
		int row = getRow(sect);
		int col = getColumn(sect);
		return getNeighbors(row, col);
	}
	
	public List<Integer> getNeighbors(int row, int col) {
		// include sections above, below, left, and right
		List<Integer> neighbors = new ArrayList<>();
		if (row > 0)
			// above
			neighbors.add(subSects[col][row-1].getSectionId());
		if (row < subSects[col].length-1)
			// below
			neighbors.add(subSects[col][row+1].getSectionId());
		if (col > 0)
			// left
			neighbors.add(subSects[col-1][row].getSectionId());
		if (col < subSects.length-1)
			// right
			neighbors.add(subSects[col+1][row].getSectionId());
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
		FaultSectionPrefData fs = new FaultSectionPrefData();
		for (int col=0; col<subSects.length; col++)
			for (int row=0; row<subSects[col].length; row++) {
				fs = subSects[col][row];
				if (fs != null)
					sects.add(fs);
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
		
		DownDipSubSectBuilder builder = new DownDipSubSectBuilder(sectName, sectID, startID,
				faultData, aveRake, numAlongStrike, numDownDip);
		
		for (int col=0; col<numAlongStrike; col++) {
			for (int row=0; row<numDownDip; row++) {
				List<Integer> conns = builder.getNeighbors(row, col);
				System.out.println("Sect "+builder.subSects[col][row].getSectionId()
						+" at row="+row+",col="+col+" has "+conns.size()+" neighbors");
			}
		}
	}

}

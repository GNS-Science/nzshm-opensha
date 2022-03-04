package nz.cri.gns.NZSHM22.opensha.inversion;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.util.modules.helpers.CSV_BackedModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.PolygonFaultGridAssociations;
import org.opensha.sha.faultSurface.FaultSection;

import java.awt.geom.Area;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;

public abstract class RegionSections implements CSV_BackedModule {

    Set<Integer> sections;
    String regionName; 

    public RegionSections(){
    }

    public RegionSections(FaultSystemRupSet rupSet, GriddedRegion region) {
        IntPredicate regionFilter = createRegionFilter(rupSet, region);
        regionName = region.getName();
        sections = rupSet.getFaultSectionDataList().stream()
                .map(FaultSection::getSectionId)
                .filter(regionFilter::test)
                .collect(Collectors.toSet());
    }

    protected static IntPredicate createRegionFilter(FaultSystemRupSet rupSet, GriddedRegion region) {
        Area area = region.getShape();
        PolygonFaultGridAssociations polyMgr = rupSet.getModule(PolygonFaultGridAssociations.class);
        return s -> {
            Area sectionArea = polyMgr.getPoly(s).getShape();
            sectionArea.intersect(area);
            return !sectionArea.isEmpty();
        };
    }

    public boolean isInRegion(int sectionID) {
        return sections.contains(sectionID);
    }

    public boolean isInRegion(FaultSection section) {
        return isInRegion(section.getSectionId());
    }

    public boolean isInRegion(List<Integer> rupture) {
        for (int section : rupture) {
            if (isInRegion(section)) {
                return true;
            }
        }
        return false;
    }

    public Set<Integer> getSections(){
        return sections;
    }

    public String getRegionName(){
        return regionName;
    }

    @Override
    public String getName() {
        return getRegionName() +"RegionSections";
    }

    @Override
    public CSVFile<?> getCSV() {
        CSVFile<Integer> result = new CSVFile<>(true);
        sections.stream().sorted().forEach(section -> result.addLine(List.of(section)));
        return result;
    }

    @Override
    public void initFromCSV(CSVFile<String> csv) {
        sections = new HashSet<>();
        csv.forEach(section -> sections.add(Integer.parseInt(section.get(0))));
    }

    @Override
    public String getFileName() {
        return getRegionName().replace(" ", "_") + "_Sections.csv";
    }
}

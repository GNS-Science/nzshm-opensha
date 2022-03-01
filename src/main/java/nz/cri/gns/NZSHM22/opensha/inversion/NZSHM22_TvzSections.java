package nz.cri.gns.NZSHM22.opensha.inversion;

import nz.cri.gns.NZSHM22.opensha.data.region.NewZealandRegions;
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

public class NZSHM22_TvzSections implements CSV_BackedModule {

    Set<Integer> tvzSections;
    String regionName = new NewZealandRegions.NZ_TVZ_GRIDDED().getName();

    public NZSHM22_TvzSections(){
    }

    public NZSHM22_TvzSections(FaultSystemRupSet rupSet) {
        IntPredicate regionFilter = createRegionFilter(rupSet, new NewZealandRegions.NZ_TVZ_GRIDDED());
        tvzSections = rupSet.getFaultSectionDataList().stream()
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

    public boolean isInTvz(int sectionID) {
        return tvzSections.contains(sectionID);
    }

    public boolean isInTvz(FaultSection section) {
        return isInTvz(section.getSectionId());
    }

    public boolean isInTvz(List<Integer> rupture) {
        for (int section : rupture) {
            if (isInTvz(section)) {
                return true;
            }
        }
        return false;
    }

    public Set<Integer> getTvzSections(){
        return tvzSections;
    }

    public String getRegionName(){
        return regionName;
    }

    @Override
    public String getName() {
        return "NZSHM22_TvzSections";
    }

    @Override
    public CSVFile<?> getCSV() {
        CSVFile<Integer> result = new CSVFile<>(true);
        tvzSections.stream().sorted().forEach(section -> result.addLine(List.of(section)));
        return result;
    }

    @Override
    public void initFromCSV(CSVFile<String> csv) {
        tvzSections = new HashSet<>();
        csv.forEach(sections -> tvzSections.add(Integer.parseInt(sections.get(0))));
    }

    @Override
    public String getFileName() {
        return "TVZ_Region_Sections.csv";
    }
}

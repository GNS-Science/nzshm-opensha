package nz.cri.gns.NZSHM22.opensha.inversion;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import nz.cri.gns.NZSHM22.opensha.ruptures.FaultSectionProperties;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.util.modules.helpers.CSV_BackedModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.faultSurface.FaultSection;

public class TvzDomainSections implements CSV_BackedModule {

    protected Set<Integer> sections;

    public TvzDomainSections() {}

    public TvzDomainSections(FaultSystemRupSet rupSet) {
        sections =
                rupSet.getFaultSectionDataList().stream()
                        .filter(FaultSectionProperties::getTvz)
                        .map(FaultSection::getSectionId)
                        .collect(Collectors.toSet());
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

    public Set<Integer> getSections() {
        return sections;
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
        return getName() + ".csv";
    }

    @Override
    public String getName() {
        return "TvzDomainSections";
    }
}

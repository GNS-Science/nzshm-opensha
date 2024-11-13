package nz.cri.gns.NZSHM22.opensha.inversion;

import com.google.common.base.Preconditions;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_LogicTreeBranch;
import nz.cri.gns.NZSHM22.opensha.faults.FaultSectionList;
import nz.cri.gns.NZSHM22.opensha.faults.NZFaultSection;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.util.modules.helpers.CSV_BackedModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.faultSurface.FaultSection;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;


public class TvzDomainSections implements CSV_BackedModule {

    protected Set<Integer> sections;

    public TvzDomainSections() {
    }

    public TvzDomainSections(FaultSystemRupSet rupSet) {
        NZSHM22_LogicTreeBranch branch = rupSet.requireModule(NZSHM22_LogicTreeBranch.class);
        Predicate<FaultSection> filter = createTvzFilter(branch);
        sections = rupSet.getFaultSectionDataList().stream()
                .filter(filter)
                .map(FaultSection::getSectionId)
                .collect(Collectors.toSet());
    }

    protected static Predicate<FaultSection> createTvzFilter(NZSHM22_LogicTreeBranch branch) {
        NZSHM22_FaultModels faultModel = branch.getValue(NZSHM22_FaultModels.class);
        Preconditions.checkState(faultModel != null);
        Preconditions.checkState(faultModel.getTvzDomain() != null);
        FaultSectionList sectionList = new FaultSectionList();
        try {
            faultModel.fetchFaultSections(sectionList);
        } catch (Exception x) {
            throw new RuntimeException(x);
        }

        return section -> {
            NZFaultSection parent = (NZFaultSection) sectionList.get(section.getParentSectionId());
            Preconditions.checkState(parent.getSectionId() == section.getParentSectionId());
            return parent.getDomainNo().equals(faultModel.getTvzDomain());
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
    public String getFileName(){
        return getName() + ".csv";
    }

    @Override
    public String getName() {
        return "TvzDomainSections";
    }


}

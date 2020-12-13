package nz.cri.gns.NSHM.opensha.ruptures.downDip;

import nz.cri.gns.NSHM.opensha.util.FaultSectionList;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.faultSurface.FaultSection;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

public class DownDipRegistry {

    final HashMap<Integer, DownDipSubSectBuilder> builders;
    final FaultSectionList subSections;

    public DownDipRegistry(FaultSectionList subSections) {
        this.subSections = subSections;
        this.builders = new HashMap<>();
    }

    public void loadFromFile(int id, String name, File csvFile) throws IOException {
        FaultSectionPrefData interfaceParentSection = new FaultSectionPrefData();
        interfaceParentSection.setSectionId(id);
        interfaceParentSection.setSectionName(name);
        interfaceParentSection.setAveDip(1); // otherwise the FaultSectionList will complain
        subSections.addParent(interfaceParentSection);

        try (InputStream inputStream = new FileInputStream(csvFile)) {
            DownDipSubSectBuilder downDipBuilder = new DownDipSubSectBuilder(name, interfaceParentSection, subSections.getSafeId(), inputStream);

            // Add the interface subsections
            subSections.addAll(downDipBuilder.getSubSectsList());
            builders.put(id, downDipBuilder);
        }
    }

    public boolean isDownDip(FaultSubsectionCluster cluster){
        return builders.containsKey(cluster.parentSectionID);
    }

    public boolean isDownDip(FaultSection subSection){
        return builders.containsKey(subSection.getParentSectionId());
    }

    public DownDipSubSectBuilder getBuilder(int id){
        return builders.get(id);
    }

    public DownDipSubSectBuilder getBuilder(FaultSubsectionCluster cluster){
        return builders.get(cluster.parentSectionID);
    }
}

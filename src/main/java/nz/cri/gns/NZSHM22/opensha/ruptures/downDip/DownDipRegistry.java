package nz.cri.gns.NZSHM22.opensha.ruptures.downDip;

import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.faultSurface.FaultSection;

import nz.cri.gns.NZSHM22.opensha.util.FaultSectionList;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

/**
 * This class hides some of the messiness of hacking downdip faults into the framework.
 */
public class DownDipRegistry {

    final HashMap<Integer, DownDipSubSectBuilder> builders;
    final FaultSectionList subSections;

    /**
     * Creates a new DownDipRegistry. There should be only one in your application.
     *
     * @param subSections A list of all FaultSections, downdip or crustal. All downdip Faultsections
     *                    will be added to this.
     */
    public DownDipRegistry(FaultSectionList subSections) {
        this.subSections = subSections;
        this.builders = new HashMap<>();
    }

    /**
     * Loads a downdip fault from a CSV file and adds all sections to the subSections list
     * passed to the registry constructor.
     *
     * @param id      The imaginary parent fault id.
     * @param name    The name of the fault.
     * @param csvFile The CSV file.
     * @throws IOException
     */
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

    /**
     * Returns true if the cluster's parent id is a downdip parent id
     *
     * @param cluster the cluster
     * @return whether the cluster is part of a downdip fault.
     */
    public boolean isDownDip(FaultSubsectionCluster cluster) {
        return builders.containsKey(cluster.parentSectionID);
    }

    /**
     * Returns true if the subsection's parent id is a downdip parent id
     *
     * @param subSection the FaultSection
     * @return whether the cluster is part of a downdip fault.
     */
    public boolean isDownDip(FaultSection subSection) {
        return builders.containsKey(subSection.getParentSectionId());
    }

    /**
     * Returns the builder for the specified downdip id.
     * The builder can be queried for downdip meta data.
     *
     * @param id the id
     * @return the builder matching the id
     */
    public DownDipSubSectBuilder getBuilder(int id) {
        return builders.get(id);
    }
}

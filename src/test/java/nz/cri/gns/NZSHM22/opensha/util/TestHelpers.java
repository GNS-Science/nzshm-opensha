package nz.cri.gns.NZSHM22.opensha.util;

import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_LogicTreeBranch;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_ScalingRelationshipNode;
import nz.cri.gns.NZSHM22.opensha.faults.FaultSectionList;
import org.dom4j.DocumentException;
import org.opensha.commons.util.modules.ModuleArchive;
import org.opensha.commons.util.modules.helpers.FileBackedModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.RupSetScalingRelationship;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipFile;

public class TestHelpers {

    public static FileBackedModule serialiseDeserialise(FileBackedModule module) throws IOException {
        File file = File.createTempFile("archive", ".zip");

        ModuleArchive<FileBackedModule> archive = new ModuleArchive<>();
        archive.addModule(module);
        archive.write(file);

        archive = new ModuleArchive<>(new ZipFile(file), module.getClass());

        return archive.getModule(module.getClass());
    }

    public static FaultSystemRupSet makeRupSet(NZSHM22_FaultModels faultModel, RupSetScalingRelationship scalingRelationship) throws DocumentException, IOException {
        return createRupSet(faultModel, scalingRelationship, List.of(
                List.of(1, 2, 3, 4),
                List.of(5, 6, 7, 8),
                List.of(9, 10, 11, 12)
        ));
    }

    public static FaultSystemRupSet createRupSet(NZSHM22_FaultModels faultModel, RupSetScalingRelationship scalingRelationship, List<List<Integer>> sectionForRups) throws DocumentException, IOException {
        FaultSectionList sections = new FaultSectionList();
        faultModel.fetchFaultSections(sections);
        // simulate subsections exactly the same size as the parents
        sections.forEach(section -> {
            section.setParentSectionId(section.getSectionId());
            section.setParentSectionName(section.getSectionName());
        });

        NZSHM22_LogicTreeBranch branch = new NZSHM22_LogicTreeBranch();
        branch.setValue(faultModel);
        branch.setValue(new NZSHM22_ScalingRelationshipNode(scalingRelationship));

        return FaultSystemRupSet.builder(sections, sectionForRups)
                .forScalingRelationship(scalingRelationship)
                .addModule(branch)
                .build();
    }

}

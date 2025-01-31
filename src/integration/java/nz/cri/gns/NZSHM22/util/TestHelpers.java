package nz.cri.gns.NZSHM22.util;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipFile;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_LogicTreeBranch;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_ScalingRelationshipNode;
import nz.cri.gns.NZSHM22.opensha.faults.FaultSectionList;
import nz.cri.gns.NZSHM22.opensha.inversion.NZSHM22_CrustalInversionRunner;
import nz.cri.gns.NZSHM22.opensha.util.ParameterRunner;
import nz.cri.gns.NZSHM22.opensha.util.Parameters;
import org.dom4j.DocumentException;
import org.opensha.commons.util.io.archive.ArchiveInput;
import org.opensha.commons.util.io.archive.ArchiveOutput;
import org.opensha.commons.util.modules.ModuleArchive;
import org.opensha.commons.util.modules.helpers.FileBackedModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.RupSetScalingRelationship;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;

public class TestHelpers {

    public static FileBackedModule serialiseDeserialise(FileBackedModule module)
            throws IOException {
        File file = File.createTempFile("archive", ".zip");

        ModuleArchive<FileBackedModule> archive = new ModuleArchive<>();
        archive.addModule(module);
        archive.write(file);

        archive = new ModuleArchive<>(new ZipFile(file), module.getClass());

        return archive.getModule(module.getClass());
    }

    /**
     * Creates a rupture set for tests that are concerned with fault sections rather than ruptures.
     *
     * @param faultModel
     * @return
     */
    public static FaultSystemRupSet createRupSetForSections(NZSHM22_FaultModels faultModel)
            throws DocumentException, IOException {
        return makeRupSet(faultModel, ScalingRelationships.SHAW_2009_MOD);
    }

    public static FaultSystemRupSet makeRupSet(
            NZSHM22_FaultModels faultModel, RupSetScalingRelationship scalingRelationship)
            throws DocumentException, IOException {
        return createRupSet(
                faultModel,
                scalingRelationship,
                List.of(List.of(1, 2, 3, 4), List.of(5, 6, 7, 8), List.of(9, 10, 11, 12)));
    }

    public static FaultSystemRupSet createRupSet(
            NZSHM22_FaultModels faultModel,
            RupSetScalingRelationship scalingRelationship,
            List<List<Integer>> sectionForRups)
            throws DocumentException, IOException {
        FaultSectionList sections = new FaultSectionList();
        faultModel.fetchFaultSections(sections);
        // simulate subsections exactly the same size as the parents
        sections.forEach(
                section -> {
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

    /**
     * Creates a solution similar to NZSHM22, but repeatable and without averaging
     *
     * @param rupSet
     * @return
     * @throws IOException
     * @throws DocumentException
     */
    public static FaultSystemSolution createCrustalSolution(FaultSystemRupSet rupSet)
            throws IOException, DocumentException {
        ArchiveOutput.InMemoryZipOutput output = new ArchiveOutput.InMemoryZipOutput(true);
        rupSet.getArchive().write(output);
        ParameterRunner parameterRunner = new ParameterRunner(Parameters.NZSHM22.INVERSION_CRUSTAL);
        NZSHM22_CrustalInversionRunner runner = new NZSHM22_CrustalInversionRunner();
        parameterRunner.setUpCrustalInversionRunner(runner);
        runner.setRuptureSetArchiveInput(output.getCompletedInput());
        runner.setIterationCompletionCriteria(1);
        runner.setSelectionIterations(2);
        runner.setRepeatable(true);
        runner.setInversionAveraging(false);
        return runner.runInversion();
    }

    public static ArchiveInput archiveInput(FaultSystemRupSet rupSet) throws IOException {
        ArchiveOutput.InMemoryZipOutput out = new ArchiveOutput.InMemoryZipOutput(true);
        rupSet.getArchive().write(out);
        return out.getCompletedInput();
    }
}

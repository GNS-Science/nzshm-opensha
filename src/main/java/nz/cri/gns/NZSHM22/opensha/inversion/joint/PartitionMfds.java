package nz.cri.gns.NZSHM22.opensha.inversion.joint;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.opensha.commons.util.io.archive.ArchiveInput;
import org.opensha.commons.util.io.archive.ArchiveOutput;
import org.opensha.commons.util.modules.ArchivableModule;
import org.opensha.commons.util.modules.helpers.FileBackedModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionTargetMFDs;

public class PartitionMfds implements ArchivableModule {

    public Map<PartitionPredicate, InversionTargetMFDs> mfds;

    public PartitionMfds() {
        mfds = new HashMap<>();
    }

    public void add(PartitionPredicate partition, InversionTargetMFDs targetMFDs) {
        // explicitly removing subSeismoOnFaultMFDs here as we don't use them, and they blow up
        // when empty
        InversionTargetMFDs precomp =
                new InversionTargetMFDs.Precomputed(
                        targetMFDs.getParent(),
                        targetMFDs.getTotalRegionalMFD(),
                        targetMFDs.getTotalOnFaultSupraSeisMFD(),
                        targetMFDs.getTotalOnFaultSubSeisMFD(),
                        targetMFDs.getTrulyOffFaultMFD(),
                        targetMFDs.getMFD_Constraints(),
                        null,
                        targetMFDs.getOnFaultSupraSeisNucleationMFDs());

        mfds.put(partition, precomp);
    }

    @Override
    public String getName() {
        return "";
    }

    @Override
    public void writeToArchive(ArchiveOutput output, String entryPrefix) throws IOException {
        String index =
                mfds.keySet().stream()
                        .map(PartitionPredicate::name)
                        .collect(Collectors.joining(","));
        FileBackedModule.initEntry(output, entryPrefix, "NZSHM_MFD_index.csv");
        OutputStreamWriter out = new OutputStreamWriter(output.getOutputStream());
        out.write(index);
        out.flush();
        output.closeEntry();

        for (PartitionPredicate partitionPredicate : mfds.keySet()) {
            InversionTargetMFDs targetMfds = mfds.get(partitionPredicate);
            targetMfds.writeToArchive(output, entryPrefix + partitionPredicate.name() + "_");
        }
    }

    @Override
    public void initFromArchive(ArchiveInput input, String entryPrefix) throws IOException {}
}

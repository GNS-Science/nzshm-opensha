package nz.cri.gns.NZSHM22.opensha.inversion.joint;

import static org.opensha.commons.util.modules.helpers.FileBackedModule.getInputStream;
import static org.opensha.commons.util.modules.helpers.FileBackedModule.initEntry;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.opensha.commons.util.io.archive.ArchiveInput;
import org.opensha.commons.util.io.archive.ArchiveOutput;
import org.opensha.commons.util.modules.ArchivableModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionTargetMFDs;

/** Module that can store multiple InversionTargetMFDs modules in one archive. */
public class PartitionMfds implements ArchivableModule {

    public static final String FILE_NAME = "NZSHM_MFD_index.csv";

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
        initEntry(output, entryPrefix, FILE_NAME);
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
    public void initFromArchive(ArchiveInput input, String entryPrefix) throws IOException {
        BufferedInputStream zin = getInputStream(input, entryPrefix, FILE_NAME);
        BufferedReader reader = new BufferedReader(new InputStreamReader(zin));
        String[] partitionNames = reader.readLine().split(",");
        zin.close();

        for (String partitionName : partitionNames) {
            PartitionPredicate partition = PartitionPredicate.valueOf(partitionName);
            InversionTargetMFDs mfds = new PrecomputedTargetMFDs();
            mfds.initFromArchive(input, entryPrefix + partitionName + "_");
            this.mfds.put(partition, mfds);
        }
    }

    // we need to have access to the default constructor
    static class PrecomputedTargetMFDs extends InversionTargetMFDs.Precomputed {
        public PrecomputedTargetMFDs() {
            super();
        }
    }
}

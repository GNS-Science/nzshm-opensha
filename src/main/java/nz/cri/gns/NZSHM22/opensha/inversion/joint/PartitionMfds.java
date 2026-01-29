package nz.cri.gns.NZSHM22.opensha.inversion.joint;

import static nz.cri.gns.NZSHM22.opensha.inversion.NZSHM22_CrustalInversionTargetMFDs.NZ_MIN_MAG;
import static nz.cri.gns.NZSHM22.opensha.inversion.NZSHM22_CrustalInversionTargetMFDs.NZ_NUM_BINS;
import static org.opensha.commons.util.modules.helpers.FileBackedModule.getInputStream;
import static org.opensha.commons.util.modules.helpers.FileBackedModule.initEntry;
import static scratch.UCERF3.inversion.U3InversionTargetMFDs.DELTA_MAG;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import nz.cri.gns.NZSHM22.opensha.data.region.NewZealandRegions;
import org.opensha.commons.util.io.archive.ArchiveInput;
import org.opensha.commons.util.io.archive.ArchiveOutput;
import org.opensha.commons.util.modules.ArchivableModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionTargetMFDs;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;

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
        return "PartitionMfds";
    }

    public static SummedMagFreqDist safeAdd(SummedMagFreqDist base, IncrementalMagFreqDist added) {
        if (added == null) {
            return base;
        }
        if (base == null) {
            base = new SummedMagFreqDist(NZ_MIN_MAG, NZ_NUM_BINS, DELTA_MAG);
        }
        base.addIncrementalMagFreqDist(added);
        return base;
    }

    public InversionTargetMFDs synthesize(FaultSystemRupSet rupSet) {
        SummedMagFreqDist totalRegionalMFD = null;
        SummedMagFreqDist onFaultSupraSeisMFD = null;
        SummedMagFreqDist onFaultSubSeisMFD = null;
        SummedMagFreqDist trulyOffFaultMFD = null;
        List<IncrementalMagFreqDist> mfdConstraints = new ArrayList<>();
        //         SubSeismoOnFaultMFDs subSeisOnFaultMFDs= null;
        //         ImmutableList<? extends IncrementalMagFreqDist> supraSeisOnFaultNuclMFDs= null;

        for (PartitionPredicate partition : mfds.keySet()) {
            NewZealandRegions.PartitionRegion region =
                    new NewZealandRegions.PartitionRegion(partition);
            InversionTargetMFDs partitionMFDs = mfds.get(partition);
            totalRegionalMFD = safeAdd(totalRegionalMFD, partitionMFDs.getTotalRegionalMFD());
            onFaultSupraSeisMFD =
                    safeAdd(onFaultSupraSeisMFD, partitionMFDs.getTotalOnFaultSupraSeisMFD());
            onFaultSubSeisMFD =
                    safeAdd(onFaultSubSeisMFD, partitionMFDs.getTotalOnFaultSubSeisMFD());
            trulyOffFaultMFD = safeAdd(trulyOffFaultMFD, partitionMFDs.getTrulyOffFaultMFD());
            for (IncrementalMagFreqDist constraint : partitionMFDs.getMFD_Constraints()) {
                constraint.setRegion(region);
                mfdConstraints.add(constraint);
            }
        }
        return new InversionTargetMFDs.Precomputed(
                rupSet,
                totalRegionalMFD,
                onFaultSupraSeisMFD,
                onFaultSubSeisMFD,
                trulyOffFaultMFD,
                mfdConstraints,
                null,
                null);
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

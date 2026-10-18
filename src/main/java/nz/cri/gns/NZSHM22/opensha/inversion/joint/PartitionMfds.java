package nz.cri.gns.NZSHM22.opensha.inversion.joint;

import static nz.cri.gns.NZSHM22.opensha.inversion.NZSHM22_CrustalInversionTargetMFDs.NZ_MIN_MAG;
import static nz.cri.gns.NZSHM22.opensha.inversion.NZSHM22_CrustalInversionTargetMFDs.NZ_NUM_BINS;
import static org.opensha.commons.util.modules.helpers.FileBackedModule.getInputStream;
import static org.opensha.commons.util.modules.helpers.FileBackedModule.initEntry;
import static scratch.UCERF3.inversion.U3InversionTargetMFDs.DELTA_MAG;

import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
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

    public InversionTargetMFDs get(PartitionPredicate partition) {
        return mfds.get(partition);
    }

    /**
     * Sums MFDs into an MFD that spans the magnitude range of all of them, so that no bins are
     * dropped. The range starts at NZ_MIN_MAG or lower.
     *
     * @param added the MFDs to sum, null entries are ignored
     * @return the summed MFD, or null if there are no non-null MFDs
     */
    public static SummedMagFreqDist sum(List<IncrementalMagFreqDist> added) {
        List<IncrementalMagFreqDist> present =
                added.stream().filter(Objects::nonNull).collect(Collectors.toList());
        if (present.isEmpty()) {
            return null;
        }
        double minMag = NZ_MIN_MAG;
        double maxMag = NZ_MIN_MAG + (NZ_NUM_BINS - 1) * DELTA_MAG;
        for (IncrementalMagFreqDist mfd : present) {
            minMag = Math.min(minMag, mfd.getMinX());
            maxMag = Math.max(maxMag, mfd.getMaxX());
        }
        int numBins = (int) Math.round((maxMag - minMag) / DELTA_MAG) + 1;
        SummedMagFreqDist result = new SummedMagFreqDist(minMag, numBins, DELTA_MAG);
        for (IncrementalMagFreqDist mfd : present) {
            result.addIncrementalMagFreqDist(mfd);
        }
        return result;
    }

    /**
     * Creates target MFDs for the whole joint rupture set by summing the on-fault supra-seismogenic
     * MFDs of all partitions. Total regional, sub-seismogenic and truly off-fault MFDs are left out
     * as only the crustal partition has them, so they would not describe the joint rupture set. The
     * result has no MFD constraints, as those only apply to their own partition. Use {@link
     * nz.cri.gns.NZSHM22.opensha.inversion.joint.reporting.PartitionPlotWrapper} to plot them
     * against the partition's rupture set. The partition MFDs are not modified.
     *
     * @param rupSet the joint rupture set that the target MFDs are for
     * @return the summed target MFDs
     */
    public InversionTargetMFDs synthesize(FaultSystemRupSet rupSet) {
        SummedMagFreqDist onFaultSupraSeisMFD =
                sum(
                        mfds.values().stream()
                                .map(InversionTargetMFDs::getTotalOnFaultSupraSeisMFD)
                                .collect(Collectors.toList()));
        return new InversionTargetMFDs.Precomputed(
                rupSet, null, onFaultSupraSeisMFD, null, null, List.of(), null, null);
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

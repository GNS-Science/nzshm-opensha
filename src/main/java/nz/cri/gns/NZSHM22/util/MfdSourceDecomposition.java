package nz.cri.gns.NZSHM22.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionPredicate;
import nz.cri.gns.NZSHM22.opensha.ruptures.FaultSectionProperties;
import org.opensha.commons.calc.FaultMomentCalc;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.AveSlipModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.SlipAlongRuptureModel;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

/**
 * Splits the participation MFD of a subject fault into the contributions of the parent sections
 * that supply it.
 *
 * <p>Every rupture that touches the subject fault is shared out among the parent sections it
 * covers, in proportion to the seismic moment each parent carries in that rupture: the weight of
 * parent P in rupture r is moment(P within r) / moment(r). Because those weights sum to one, the
 * contributions sum bin by bin back to the subject's participation MFD, which makes them safe to
 * stack. The subject's own share is its nucleation MFD.
 *
 * <p>Moment is derived from the rupture set's {@link SlipAlongRuptureModel}, falling back to {@link
 * SlipAlongRuptureModel.Default} when the rupture set carries none. Under a uniform slip model, the
 * NZSHM22 default, slip is the same on every section of a rupture and the shear modulus is a global
 * constant, so the moment share reduces exactly to the aseismicity reduced area share and the
 * rupture's average slip cancels out of the ratio.
 */
public class MfdSourceDecomposition {

    /** Name used for the aggregated tail of the contribution list. */
    public static final String OTHER_NAME = "Other";

    /** Parent id used for the aggregated tail of the contribution list. */
    public static final int OTHER_PARENT_ID = -1;

    /** What one source parent section contributes to the subject fault's MFD. */
    public static class SourceContribution {

        protected final int parentId;
        protected final String parentName;
        protected final PartitionPredicate partition;
        protected final IncrementalMagFreqDist mfd;
        protected final double weightedRate;
        protected final double coRuptureRate;
        protected final int rupCount;
        protected final int nonZeroRupCount;
        protected final double minMag;
        protected final double maxMag;

        /**
         * @param parentId parent section id, or {@link #OTHER_PARENT_ID} for the aggregated tail
         * @param parentName parent section name
         * @param partition partition the parent belongs to, or null if unknown
         * @param mfd moment weighted contribution to the subject's participation MFD
         * @param weightedRate sum of the contribution MFD, the additive contribution
         * @param coRuptureRate summed rate of the shared ruptures, counting each in full
         * @param rupCount number of ruptures shared with the subject fault
         * @param nonZeroRupCount how many of those have a non-zero rate
         * @param minMag smallest magnitude among the shared ruptures
         * @param maxMag largest magnitude among the shared ruptures
         */
        public SourceContribution(
                int parentId,
                String parentName,
                PartitionPredicate partition,
                IncrementalMagFreqDist mfd,
                double weightedRate,
                double coRuptureRate,
                int rupCount,
                int nonZeroRupCount,
                double minMag,
                double maxMag) {
            this.parentId = parentId;
            this.parentName = parentName;
            this.partition = partition;
            this.mfd = mfd;
            this.weightedRate = weightedRate;
            this.coRuptureRate = coRuptureRate;
            this.rupCount = rupCount;
            this.nonZeroRupCount = nonZeroRupCount;
            this.minMag = minMag;
            this.maxMag = maxMag;
        }

        public int getParentId() {
            return parentId;
        }

        public String getParentName() {
            return parentName;
        }

        public PartitionPredicate getPartition() {
            return partition;
        }

        /**
         * @return the moment weighted contribution to the subject fault's participation MFD
         */
        public IncrementalMagFreqDist getMfd() {
            return mfd;
        }

        /**
         * @return the sum of {@link #getMfd()}, this source's additive share of the total rate
         */
        public double getWeightedRate() {
            return weightedRate;
        }

        /**
         * @return the summed rate of the ruptures shared with the subject fault, counting each
         *     rupture in full. Summed over all sources this over-counts multi-fault ruptures, so it
         *     is a reporting figure only and is not additive.
         */
        public double getCoRuptureRate() {
            return coRuptureRate;
        }

        public int getRupCount() {
            return rupCount;
        }

        public int getNonZeroRupCount() {
            return nonZeroRupCount;
        }

        public double getMinMag() {
            return minMag;
        }

        public double getMaxMag() {
            return maxMag;
        }
    }

    /** Mutable per source accumulator, turned into a {@link SourceContribution} at the end. */
    protected static class Accumulator {
        final int parentId;
        String parentName;
        PartitionPredicate partition;
        final double[] bins;
        double coRuptureRate;
        int rupCount;
        int nonZeroRupCount;
        double minMag = Double.POSITIVE_INFINITY;
        double maxMag = Double.NEGATIVE_INFINITY;

        Accumulator(int parentId, int numBins) {
            this.parentId = parentId;
            this.bins = new double[numBins];
        }
    }

    protected final FaultSystemRupSet rupSet;
    protected final Set<Integer> subjectParentIds;
    protected final IncrementalMagFreqDist binning;

    protected final IncrementalMagFreqDist participationMfd;
    protected final IncrementalMagFreqDist selfMfd;
    protected final IncrementalMagFreqDist importedMfd;
    protected final List<SourceContribution> contributions;

    protected final int rupCount;
    protected final int nonZeroRupCount;
    protected final double minMag;
    protected final double maxMag;

    /**
     * Decomposes the participation MFD of the given subject fault.
     *
     * @param solution the solution to analyse
     * @param subjectParentIds parent section ids making up the subject fault
     * @param binning an MFD used only as a bin template, its values are not read
     */
    public MfdSourceDecomposition(
            FaultSystemSolution solution,
            Set<Integer> subjectParentIds,
            IncrementalMagFreqDist binning) {
        this.rupSet = solution.getRupSet();
        this.subjectParentIds = new HashSet<>(subjectParentIds);
        this.binning = binning;

        int numBins = binning.size();
        SlipAlongRuptureModel slipModel = slipAlongRuptureModel(rupSet);
        AveSlipModule aveSlips = rupSet.getModule(AveSlipModule.class);

        Map<Integer, Accumulator> accumulators = new HashMap<>();
        double[] participationBins = new double[numBins];
        double[] selfBins = new double[numBins];

        Set<Integer> rups = rupturesForSubject();
        int rupTotal = 0;
        int nonZeroTotal = 0;
        double minMagSeen = Double.POSITIVE_INFINITY;
        double maxMagSeen = Double.NEGATIVE_INFINITY;

        Map<Integer, Double> momentByParent = new HashMap<>();
        for (int r : rups) {
            double rate = solution.getRateForRup(r);
            double mag = rupSet.getMagForRup(r);
            int bin = binIndex(mag);

            rupTotal++;
            if (rate > 0) {
                nonZeroTotal++;
            }
            minMagSeen = Math.min(minMagSeen, mag);
            maxMagSeen = Math.max(maxMagSeen, mag);
            participationBins[bin] += rate;

            momentByParent.clear();
            double momentTotal = momentByParent(r, slipModel, aveSlips, momentByParent);

            for (Map.Entry<Integer, Double> entry : momentByParent.entrySet()) {
                int parentId = entry.getKey();
                double weight = momentTotal > 0 ? entry.getValue() / momentTotal : 0;

                if (this.subjectParentIds.contains(parentId)) {
                    selfBins[bin] += rate * weight;
                }

                Accumulator acc =
                        accumulators.computeIfAbsent(parentId, id -> newAccumulator(id, numBins));
                acc.bins[bin] += rate * weight;
                acc.coRuptureRate += rate;
                acc.rupCount++;
                if (rate > 0) {
                    acc.nonZeroRupCount++;
                }
                acc.minMag = Math.min(acc.minMag, mag);
                acc.maxMag = Math.max(acc.maxMag, mag);
            }
        }

        this.rupCount = rupTotal;
        this.nonZeroRupCount = nonZeroTotal;
        this.minMag = minMagSeen;
        this.maxMag = maxMagSeen;

        this.participationMfd = toMfd(participationBins, "Participation");
        this.selfMfd = toMfd(selfBins, "Self-sourced");
        double[] importedBins = new double[numBins];
        for (int b = 0; b < numBins; b++) {
            importedBins[b] = participationBins[b] - selfBins[b];
        }
        this.importedMfd = toMfd(importedBins, "Imported");

        List<SourceContribution> built = new ArrayList<>();
        for (Accumulator acc : accumulators.values()) {
            built.add(toContribution(acc));
        }
        built.sort(Comparator.comparingDouble(SourceContribution::getWeightedRate).reversed());
        this.contributions = List.copyOf(built);
    }

    /**
     * The slip along rupture model to derive moment from, falling back to the uniform default when
     * the rupture set carries none.
     *
     * @param rupSet the rupture set
     * @return a slip along rupture model, never null
     */
    protected static SlipAlongRuptureModel slipAlongRuptureModel(FaultSystemRupSet rupSet) {
        SlipAlongRuptureModel model = rupSet.getModule(SlipAlongRuptureModel.class);
        return model == null ? new SlipAlongRuptureModel.Default() : model;
    }

    /**
     * Every rupture touching any parent section of the subject fault.
     *
     * @return rupture indices, each appearing once
     */
    protected Set<Integer> rupturesForSubject() {
        Set<Integer> rups = new HashSet<>();
        for (int parentId : subjectParentIds) {
            // null for a parent that is unknown or carries no ruptures
            List<Integer> forParent = rupSet.getRupturesForParentSection(parentId);
            if (forParent != null) {
                rups.addAll(forParent);
            }
        }
        return rups;
    }

    /**
     * Accumulates the seismic moment each parent section carries in the given rupture.
     *
     * @param rupIndex the rupture
     * @param slipModel model giving slip on each section of the rupture
     * @param aveSlips average rupture slips, or null to use unit slip, which cancels out under a
     *     uniform slip model
     * @param moments map to accumulate into, keyed by parent section id
     * @return the total moment of the rupture
     */
    protected double momentByParent(
            int rupIndex,
            SlipAlongRuptureModel slipModel,
            AveSlipModule aveSlips,
            Map<Integer, Double> moments) {
        List<Integer> sectIndices = rupSet.getSectionsIndicesForRup(rupIndex);
        double aveSlip = aveSlips == null ? 1d : aveSlips.getAveSlip(rupIndex);
        // parallel to sectIndices
        double[] slips = slipModel.calcSlipOnSectionsForRup(rupSet, rupIndex, aveSlip);

        double total = 0;
        for (int i = 0; i < sectIndices.size(); i++) {
            int sectIndex = sectIndices.get(i);
            int parentId = rupSet.getFaultSectionData(sectIndex).getParentSectionId();
            double moment =
                    FaultMomentCalc.SHEAR_MODULUS * rupSet.getAreaForSection(sectIndex) * slips[i];
            moments.merge(parentId, moment, Double::sum);
            total += moment;
        }
        return total;
    }

    /**
     * Creates an accumulator carrying the name and partition of the given parent section.
     *
     * @param parentId the parent section id
     * @param numBins number of magnitude bins
     * @return a new accumulator
     */
    protected Accumulator newAccumulator(int parentId, int numBins) {
        Accumulator acc = new Accumulator(parentId, numBins);
        for (FaultSection sect : rupSet.getFaultSectionDataList()) {
            if (sect.getParentSectionId() == parentId) {
                acc.parentName = sect.getParentSectionName();
                acc.partition = partitionOf(sect);
                break;
            }
        }
        if (acc.parentName == null) {
            acc.parentName = "Parent " + parentId;
        }
        return acc;
    }

    /**
     * The partition of a fault section, tolerating sections that carry no partition property.
     *
     * @param sect the section
     * @return the partition, or null if it is not recorded
     */
    protected static PartitionPredicate partitionOf(FaultSection sect) {
        if (!(sect instanceof GeoJSONFaultSection)) {
            return null;
        }
        return FaultSectionProperties.getPartition(sect);
    }

    /**
     * The bin a magnitude falls into. Magnitudes outside the template are clamped to the end bins
     * rather than dropped, so that no rate is lost and the contributions stay additive.
     *
     * @param mag the magnitude
     * @return a bin index within range
     */
    protected int binIndex(double mag) {
        int index = (int) Math.round((mag - binning.getMinX()) / binning.getDelta());
        return Math.max(0, Math.min(binning.size() - 1, index));
    }

    /**
     * Materialises accumulated bin values as an MFD on the template's discretisation.
     *
     * @param bins the bin values
     * @param name name given to the MFD, used in plot legends
     * @return the MFD
     */
    protected IncrementalMagFreqDist toMfd(double[] bins, String name) {
        IncrementalMagFreqDist mfd =
                new IncrementalMagFreqDist(binning.getMinX(), bins.length, binning.getDelta());
        for (int b = 0; b < bins.length; b++) {
            mfd.set(b, bins[b]);
        }
        mfd.setName(name);
        return mfd;
    }

    /**
     * Converts an accumulator into an immutable contribution.
     *
     * @param acc the accumulator
     * @return the contribution
     */
    protected SourceContribution toContribution(Accumulator acc) {
        IncrementalMagFreqDist mfd = toMfd(acc.bins, acc.parentName);
        double weightedRate = 0;
        for (double bin : acc.bins) {
            weightedRate += bin;
        }
        return new SourceContribution(
                acc.parentId,
                acc.parentName,
                acc.partition,
                mfd,
                weightedRate,
                acc.coRuptureRate,
                acc.rupCount,
                acc.nonZeroRupCount,
                acc.minMag,
                acc.maxMag);
    }

    /**
     * @return the parent section ids making up the subject fault
     */
    public Set<Integer> getSubjectParentIds() {
        return subjectParentIds;
    }

    /**
     * @return the participation MFD of the subject fault, every rupture counted at its full rate
     */
    public IncrementalMagFreqDist getParticipationMfd() {
        return participationMfd;
    }

    /**
     * @return the share of the participation MFD sourced on the subject fault itself, which is its
     *     nucleation MFD
     */
    public IncrementalMagFreqDist getSelfMfd() {
        return selfMfd;
    }

    /**
     * @return the share of the participation MFD sourced on other faults
     */
    public IncrementalMagFreqDist getImportedMfd() {
        return importedMfd;
    }

    /**
     * @return every contributing parent section including the subject's own, sorted by descending
     *     contribution. Their MFDs sum bin by bin to {@link #getParticipationMfd()}.
     */
    public List<SourceContribution> getContributions() {
        return contributions;
    }

    /**
     * @return number of ruptures touching the subject fault
     */
    public int getRupCount() {
        return rupCount;
    }

    /**
     * @return how many of those ruptures have a non-zero rate
     */
    public int getNonZeroRupCount() {
        return nonZeroRupCount;
    }

    /**
     * @return the smallest magnitude among the subject fault's ruptures
     */
    public double getMinMag() {
        return minMag;
    }

    /**
     * @return the largest magnitude among the subject fault's ruptures
     */
    public double getMaxMag() {
        return maxMag;
    }

    /**
     * @return the total participation rate of the subject fault
     */
    public double getTotalRate() {
        return participationMfd.calcSumOfY_Vals();
    }

    /**
     * @return the rate sourced on the subject fault itself
     */
    public double getSelfRate() {
        return selfMfd.calcSumOfY_Vals();
    }

    /**
     * @return the rate sourced on other faults
     */
    public double getImportedRate() {
        return importedMfd.calcSumOfY_Vals();
    }

    /**
     * The largest contributors, with everything below them aggregated into a single {@link
     * #OTHER_NAME} entry so that the result still sums to the participation MFD.
     *
     * @param n how many contributors to keep separate
     * @return at most n + 1 contributions, sorted by descending contribution
     */
    public List<SourceContribution> getTopN(int n) {
        if (contributions.size() <= n) {
            return contributions;
        }
        List<SourceContribution> top = new ArrayList<>(contributions.subList(0, n));
        List<SourceContribution> tail = contributions.subList(n, contributions.size());
        top.add(aggregate(tail, OTHER_NAME));
        return top;
    }

    /**
     * Sums a group of contributions into a single one. Rupture counts are summed and so may
     * double-count ruptures shared by several of the sources.
     *
     * @param group the contributions to combine
     * @param name name for the combined contribution
     * @return the combined contribution
     */
    public SourceContribution aggregate(Collection<SourceContribution> group, String name) {
        double[] bins = new double[binning.size()];
        double weightedRate = 0;
        double coRuptureRate = 0;
        int rups = 0;
        int nonZeroRups = 0;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (SourceContribution contribution : group) {
            for (int b = 0; b < bins.length; b++) {
                bins[b] += contribution.getMfd().getY(b);
            }
            weightedRate += contribution.getWeightedRate();
            coRuptureRate += contribution.getCoRuptureRate();
            rups += contribution.getRupCount();
            nonZeroRups += contribution.getNonZeroRupCount();
            min = Math.min(min, contribution.getMinMag());
            max = Math.max(max, contribution.getMaxMag());
        }
        return new SourceContribution(
                OTHER_PARENT_ID,
                name,
                null,
                toMfd(bins, name),
                weightedRate,
                coRuptureRate,
                rups,
                nonZeroRups,
                min,
                max);
    }
}

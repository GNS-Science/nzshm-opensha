package nz.cri.gns.NZSHM22.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nz.cri.gns.NZSHM22.util.MfdSourceDecomposition.SourceContribution;
import org.junit.Test;
import org.opensha.commons.geo.Location;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SummedMagFreqDist;

/**
 * Tests {@link MfdSourceDecomposition} against a small fixture of five equal area sections spread
 * over three parent faults, which makes every moment weight an exact fraction.
 */
public class MfdSourceDecompositionTest {

    static final int ALPHA = 100;
    static final int BETA = 200;
    static final int GAMMA = 300;

    /** Sections 0-2 belong to Alpha, section 3 to Beta and section 4 to Gamma. */
    static final List<Integer> PARENT_OF_SECTION = List.of(ALPHA, ALPHA, ALPHA, BETA, GAMMA);

    static final Map<Integer, String> PARENT_NAMES =
            Map.of(ALPHA, "Alpha", BETA, "Beta", GAMMA, "Gamma");

    /**
     * Ruptures chosen so that Alpha takes part in three of them with weights 1, 3/4 and 1/3, and so
     * that rupture 2 never touches Alpha.
     */
    static final List<List<Integer>> SECTIONS_FOR_RUPS =
            List.of(List.of(0, 1, 2), List.of(0, 1, 2, 3), List.of(3, 4), List.of(2, 3, 4));

    /**
     * One distinct magnitude per rupture, each sitting on a bin centre so it lands in its own bin.
     */
    static final double[] MAGS = {7.05, 7.55, 8.05, 8.55};

    /** Distinct rates, so a mixed up rupture shows as a wrong total rather than cancelling out. */
    static final double[] RATES = {1e-2, 1e-3, 1e-4, 1e-5};

    /** Magnitude bins wide enough that no rupture magnitude has to be clamped. */
    static IncrementalMagFreqDist binning() {
        return new IncrementalMagFreqDist(5.05, 9.05, 41);
    }

    /**
     * Five sections of identical geometry, so that each carries the same area and the moment
     * weights are decided purely by how many sections a parent contributes to a rupture.
     */
    static List<FaultSection> makeSections() {
        List<FaultSection> sections = new ArrayList<>();
        for (int i = 0; i < PARENT_OF_SECTION.size(); i++) {
            // north-south traces of the same length, spread out along longitude
            FaultTrace trace = new FaultTrace("trace " + i);
            trace.add(new Location(-41.5, 174.0 + i));
            trace.add(new Location(-41.4, 174.0 + i));

            FaultSectionPrefData pref = new FaultSectionPrefData();
            pref.setSectionId(i);
            pref.setSectionName("Section " + i);
            pref.setParentSectionId(PARENT_OF_SECTION.get(i));
            pref.setParentSectionName(PARENT_NAMES.get(PARENT_OF_SECTION.get(i)));
            pref.setFaultTrace(trace);
            pref.setAveSlipRate(10);
            pref.setAveRake(180);
            pref.setAveDip(90);
            pref.setAveUpperDepth(0);
            pref.setAveLowerDepth(15);
            pref.setDipDirection((float) trace.getDipDirection());
            sections.add(GeoJSONFaultSection.fromFaultSection(pref));
        }
        return sections;
    }

    static FaultSystemSolution makeSolution() {
        FaultSystemRupSet rupSet =
                FaultSystemRupSet.builder(makeSections(), SECTIONS_FOR_RUPS)
                        .rupMags(MAGS.clone())
                        .build();
        return new FaultSystemSolution(rupSet, RATES.clone());
    }

    static MfdSourceDecomposition decompose(int... subjectParents) {
        Set<Integer> subject = new java.util.HashSet<>();
        for (int parent : subjectParents) {
            subject.add(parent);
        }
        return new MfdSourceDecomposition(makeSolution(), subject, binning());
    }

    static SourceContribution byName(MfdSourceDecomposition decomposition, String name) {
        for (SourceContribution contribution : decomposition.getContributions()) {
            if (contribution.getParentName().equals(name)) {
                return contribution;
            }
        }
        return null;
    }

    /**
     * All five sections must have the same area, otherwise the expected weights below are wrong.
     */
    @Test
    public void sectionsHaveEqualArea() {
        FaultSystemRupSet rupSet = makeSolution().getRupSet();
        double first = rupSet.getAreaForSection(0);
        assertTrue(first > 0);
        for (int s = 1; s < rupSet.getNumSections(); s++) {
            assertEquals(first, rupSet.getAreaForSection(s), first * 1e-9);
        }
    }

    /** Only the ruptures that touch Alpha are considered. */
    @Test
    public void participationCoversOnlyTheSubjectsRuptures() {
        MfdSourceDecomposition decomposition = decompose(ALPHA);

        assertEquals(3, decomposition.getRupCount());
        assertEquals(3, decomposition.getNonZeroRupCount());
        assertEquals(7.05, decomposition.getMinMag(), 1e-9);
        assertEquals(8.55, decomposition.getMaxMag(), 1e-9);
        // rupture 2 does not touch Alpha and so is left out
        assertEquals(1e-2 + 1e-3 + 1e-5, decomposition.getTotalRate(), 1e-12);
    }

    /** The point of the moment weighting: the contributions add up to the participation MFD. */
    @Test
    public void contributionsSumToParticipationMfd() {
        MfdSourceDecomposition decomposition = decompose(ALPHA);
        IncrementalMagFreqDist participation = decomposition.getParticipationMfd();

        for (int bin = 0; bin < participation.size(); bin++) {
            double sum = 0;
            for (SourceContribution contribution : decomposition.getContributions()) {
                sum += contribution.getMfd().getY(bin);
            }
            assertEquals(participation.getY(bin), sum, 1e-15);
        }
    }

    /** Self plus imported is the whole participation MFD, bin by bin. */
    @Test
    public void selfPlusImportedIsParticipation() {
        MfdSourceDecomposition decomposition = decompose(ALPHA);

        for (int bin = 0; bin < decomposition.getParticipationMfd().size(); bin++) {
            assertEquals(
                    decomposition.getParticipationMfd().getY(bin),
                    decomposition.getSelfMfd().getY(bin) + decomposition.getImportedMfd().getY(bin),
                    1e-15);
        }
    }

    /** Under the uniform default slip model the subject's own share is its nucleation MFD. */
    @Test
    public void selfShareEqualsNucleationMfd() {
        FaultSystemSolution solution = makeSolution();
        IncrementalMagFreqDist binning = binning();
        MfdSourceDecomposition decomposition =
                new MfdSourceDecomposition(solution, Set.of(ALPHA), binning);

        SummedMagFreqDist nucleation =
                MFDPlotCalc.calcNucleationMFD_forParentSect(
                        solution, Set.of(ALPHA), binning.getMinX(), binning.getMaxX(), 41);

        for (int bin = 0; bin < binning.size(); bin++) {
            assertEquals(nucleation.getY(bin), decomposition.getSelfMfd().getY(bin), 1e-15);
        }
    }

    /** A rupture confined to the subject fault is credited to it in full. */
    @Test
    public void singleParentRuptureIsFullyAttributed() {
        MfdSourceDecomposition decomposition = decompose(ALPHA);
        int bin = decomposition.getParticipationMfd().getClosestXIndex(7.05);

        assertEquals(RATES[0], decomposition.getParticipationMfd().getY(bin), 1e-15);
        assertEquals(RATES[0], byName(decomposition, "Alpha").getMfd().getY(bin), 1e-15);
        assertEquals(0d, byName(decomposition, "Beta").getMfd().getY(bin), 1e-15);
    }

    /** Rupture 1 puts three Alpha sections against one Beta section, so it splits 3:1. */
    @Test
    public void areaRatioDecidesTheSplit() {
        MfdSourceDecomposition decomposition = decompose(ALPHA);
        int bin = decomposition.getParticipationMfd().getClosestXIndex(7.55);

        assertEquals(0.75 * RATES[1], byName(decomposition, "Alpha").getMfd().getY(bin), 1e-15);
        assertEquals(0.25 * RATES[1], byName(decomposition, "Beta").getMfd().getY(bin), 1e-15);
    }

    /** Rupture 3 has one section from each parent, so it splits three ways evenly. */
    @Test
    public void threeWayRuptureSplitsEvenly() {
        MfdSourceDecomposition decomposition = decompose(ALPHA);
        int bin = decomposition.getParticipationMfd().getClosestXIndex(8.55);

        for (String name : List.of("Alpha", "Beta", "Gamma")) {
            assertEquals(RATES[3] / 3d, byName(decomposition, name).getMfd().getY(bin), 1e-15);
        }
    }

    /**
     * The co-rupture rate credits each shared rupture in full, unlike the weighted contribution.
     */
    @Test
    public void coRuptureRateCountsSharedRupturesInFull() {
        MfdSourceDecomposition decomposition = decompose(ALPHA);
        SourceContribution beta = byName(decomposition, "Beta");

        // Beta shares ruptures 1 and 3 with Alpha
        assertEquals(RATES[1] + RATES[3], beta.getCoRuptureRate(), 1e-15);
        assertEquals(2, beta.getRupCount());
        assertEquals(0.25 * RATES[1] + RATES[3] / 3d, beta.getWeightedRate(), 1e-15);
    }

    /** Sources come back largest first. */
    @Test
    public void contributionsAreSortedByDescendingShare() {
        MfdSourceDecomposition decomposition = decompose(ALPHA);
        List<SourceContribution> contributions = decomposition.getContributions();

        assertEquals(3, contributions.size());
        assertEquals("Alpha", contributions.get(0).getParentName());
        assertEquals("Beta", contributions.get(1).getParentName());
        assertEquals("Gamma", contributions.get(2).getParentName());
    }

    /** A parent with no ruptures is skipped rather than throwing. */
    @Test
    public void unknownParentYieldsAnEmptyDecomposition() {
        MfdSourceDecomposition decomposition = decompose(999);

        assertEquals(0, decomposition.getRupCount());
        assertEquals(0d, decomposition.getTotalRate(), 1e-15);
        assertTrue(decomposition.getContributions().isEmpty());
    }

    /** A subject made of several parents counts each shared rupture once. */
    @Test
    public void subjectSpanningSeveralParentsCountsRupturesOnce() {
        MfdSourceDecomposition decomposition = decompose(ALPHA, BETA);

        // every rupture touches Alpha or Beta
        assertEquals(4, decomposition.getRupCount());
        assertEquals(1e-2 + 1e-3 + 1e-4 + 1e-5, decomposition.getTotalRate(), 1e-12);
        // Gamma only appears in ruptures 2 and 3
        assertEquals(
                RATES[2] / 2d + RATES[3] / 3d,
                byName(decomposition, "Gamma").getWeightedRate(),
                1e-15);
    }

    /** getTopN folds the tail into "Other" without losing any rate. */
    @Test
    public void topNAggregatesTheTail() {
        MfdSourceDecomposition decomposition = decompose(ALPHA);
        List<SourceContribution> top = decomposition.getTopN(1);

        assertEquals(2, top.size());
        assertEquals("Alpha", top.get(0).getParentName());

        SourceContribution other = top.get(1);
        assertEquals(MfdSourceDecomposition.OTHER_NAME, other.getParentName());
        assertEquals(MfdSourceDecomposition.OTHER_PARENT_ID, other.getParentId());
        assertEquals(
                byName(decomposition, "Beta").getWeightedRate()
                        + byName(decomposition, "Gamma").getWeightedRate(),
                other.getWeightedRate(),
                1e-15);

        for (int bin = 0; bin < decomposition.getParticipationMfd().size(); bin++) {
            double sum = 0;
            for (SourceContribution contribution : top) {
                sum += contribution.getMfd().getY(bin);
            }
            assertEquals(decomposition.getParticipationMfd().getY(bin), sum, 1e-15);
        }
    }

    /** Asking for more sources than there are returns the list unchanged. */
    @Test
    public void topNReturnsEverythingWhenThereIsNoTail() {
        MfdSourceDecomposition decomposition = decompose(ALPHA);
        assertSame(decomposition.getContributions(), decomposition.getTopN(10));
    }

    /** Sections carry a partition when the rupture set records one. */
    @Test
    public void contributionsCarryNoPartitionWhenSectionsHaveNone() {
        MfdSourceDecomposition decomposition = decompose(ALPHA);
        SourceContribution alpha = byName(decomposition, "Alpha");

        assertNotNull(alpha);
        // the fixture sets no Partition property
        assertNull(alpha.getPartition());
    }
}

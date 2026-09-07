package nz.cri.gns.NZSHM22.opensha.inversion.joint.reporting;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionPredicate;
import nz.cri.gns.NZSHM22.opensha.ruptures.FaultSectionProperties;
import nz.cri.gns.NZSHM22.util.MfdSourceDecomposition;
import nz.cri.gns.NZSHM22.util.MfdSourceDecomposition.SourceContribution;
import nz.cri.gns.NZSHM22.util.NZSHM22_ReportPageGen;
import org.junit.Test;
import org.opensha.commons.geo.Location;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.SolMFDPlot;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

/**
 * Tests that {@link FaultSourceMfdPlot} writes a sub-page per subject fault, drops sources whose
 * share rounds to zero, and keeps the subduction interfaces visible on top of the stack.
 */
public class FaultSourceMfdPlotTest {

    static final int ALPHA = 100;
    static final int BETA = 200;
    static final int GAMMA = 300;
    static final int DELTA = 400;
    static final int TINY = 500;

    static final Map<Integer, String> PARENT_NAMES =
            Map.of(ALPHA, "Alpha", BETA, "Beta", GAMMA, "Gamma", DELTA, "Delta", TINY, "Tiny");

    /** Delta stands in for a subduction interface; everything else is crustal. */
    static final Map<Integer, PartitionPredicate> PARTITIONS =
            Map.of(
                    ALPHA, PartitionPredicate.CRUSTAL,
                    BETA, PartitionPredicate.CRUSTAL,
                    GAMMA, PartitionPredicate.CRUSTAL,
                    DELTA, PartitionPredicate.HIKURANGI,
                    TINY, PartitionPredicate.CRUSTAL);

    static final List<Integer> PARENT_OF_SECTION =
            List.of(ALPHA, ALPHA, ALPHA, BETA, GAMMA, DELTA, TINY);

    static final List<List<Integer>> SECTIONS_FOR_RUPS =
            List.of(
                    List.of(0, 1, 2), // Alpha alone
                    List.of(0, 1, 2, 3), // Alpha 3/4, Beta 1/4
                    List.of(3, 4), // no Alpha
                    List.of(2, 3, 4), // Alpha, Beta, Gamma evenly
                    List.of(0, 1, 2, 5), // Alpha 3/4, subduction 1/4
                    List.of(0, 1, 2, 6)); // Alpha 3/4, a source too small to list

    /** One magnitude per rupture, all on bin centres so each lands in its own bin. */
    static final double[] MAGS = {7.05, 7.55, 8.05, 8.55, 6.55, 6.05};

    static final double[] RATES = {1e-2, 1e-3, 1e-4, 1e-5, 1e-4, 1e-12};

    /**
     * Seven sections of identical geometry, so that a parent's moment share in a rupture is simply
     * its share of the sections. The subduction one is given the same geometry as the rest: only
     * its partition property matters here.
     */
    static List<FaultSection> makeSections() {
        List<FaultSection> sections = new ArrayList<>();
        for (int i = 0; i < PARENT_OF_SECTION.size(); i++) {
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

            GeoJSONFaultSection section = GeoJSONFaultSection.fromFaultSection(pref);
            new FaultSectionProperties(section)
                    .setPartition(PARTITIONS.get(PARENT_OF_SECTION.get(i)));
            sections.add(section);
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

    static MfdSourceDecomposition decomposeAlpha(FaultSystemSolution sol) {
        FaultSystemRupSet rupSet = sol.getRupSet();
        return new MfdSourceDecomposition(
                sol,
                Set.of(ALPHA),
                SolMFDPlot.initDefaultMFD(rupSet.getMinMag(), rupSet.getMaxMag()));
    }

    static SourceContribution contributionOf(MfdSourceDecomposition decomposition, int parentId) {
        return decomposition.getContributions().stream()
                .filter(c -> c.getParentId() == parentId)
                .findFirst()
                .orElseThrow();
    }

    /** A report directory with the resources sub-directory that ReportPageGen would have made. */
    static File makeResourcesDir() throws IOException {
        File outputDir = Files.createTempDirectory("faultSourceMfdPlot").toFile();
        outputDir.deleteOnExit();
        File resourcesDir = new File(outputDir, "resources");
        assertTrue(resourcesDir.mkdir());
        return resourcesDir;
    }

    static List<String> runPlot(FaultSourceMfdPlot plot, File resourcesDir) throws IOException {
        FaultSystemSolution sol = makeSolution();
        return plot.plot(
                sol.getRupSet(),
                sol,
                mock(ReportMetadata.class),
                resourcesDir,
                "resources",
                "_[(top)](#table-of-contents)_");
    }

    static String readPage(File resourcesDir, String faultName) throws IOException {
        File readme =
                new File(
                        new File(
                                new File(
                                        resourcesDir.getParentFile(), FaultSourceMfdPlot.PAGES_DIR),
                                faultName),
                        "README.md");
        return Files.readString(readme.toPath());
    }

    @Test
    public void writesASubPagePerFault() throws IOException {
        File resourcesDir = makeResourcesDir();
        List<String> lines =
                runPlot(new FaultSourceMfdPlot(List.of("Alpha", "Beta")), resourcesDir);

        assertNotNull(lines);
        File pagesDir = new File(resourcesDir.getParentFile(), FaultSourceMfdPlot.PAGES_DIR);
        assertTrue(pagesDir.isDirectory());

        for (String name : List.of("Alpha", "Beta")) {
            File faultDir = new File(pagesDir, name);
            assertTrue(name + " page missing", faultDir.isDirectory());
            assertTrue(new File(faultDir, "README.md").isFile());
            assertTrue(new File(faultDir, "index.html").isFile());
            for (String prefix : List.of("source_mfd", "source_mfd_cumulative", "source_mix")) {
                assertTrue(
                        prefix + " missing for " + name,
                        new File(new File(faultDir, "resources"), prefix + ".png").isFile());
            }
        }
    }

    @Test
    public void summaryTableLinksToEachSubPage() throws IOException {
        File resourcesDir = makeResourcesDir();
        List<String> lines = runPlot(new FaultSourceMfdPlot(List.of("Alpha")), resourcesDir);

        String markdown = String.join("\n", lines);
        assertTrue(
                markdown.contains(
                        "[Alpha](resources/../" + FaultSourceMfdPlot.PAGES_DIR + "/Alpha)"));
    }

    /** Alpha rides along on several ruptures, so its page must name the other faults. */
    @Test
    public void subPageListsTheContributingFaults() throws IOException {
        File resourcesDir = makeResourcesDir();
        runPlot(new FaultSourceMfdPlot(List.of("Alpha")), resourcesDir);
        String markdown = readPage(resourcesDir, "Alpha");

        assertTrue(markdown.contains("# Alpha MFD Sources"));
        assertTrue(markdown.contains("## Contributing Faults"));
        assertTrue(markdown.contains("Alpha _(self)_"));
        assertTrue(markdown.contains("| Beta |"));
        assertTrue(markdown.contains("| Gamma |"));
        assertTrue(markdown.contains("| Delta |"));
    }

    /** Tiny supplies a share that rounds to zero, so it gets no row of its own. */
    @Test
    public void tableOmitsSourcesRoundingToZero() throws IOException {
        File resourcesDir = makeResourcesDir();
        runPlot(new FaultSourceMfdPlot(List.of("Alpha")), resourcesDir);
        String markdown = readPage(resourcesDir, "Alpha");

        assertFalse(markdown.contains("| Tiny |"));
        assertTrue(markdown.contains("1 further sources contribute a share that rounds to"));
    }

    /** Every listed source has a share the table can actually show. */
    @Test
    public void everyListedSourceHasANonZeroShare() throws IOException {
        File resourcesDir = makeResourcesDir();
        runPlot(new FaultSourceMfdPlot(List.of("Alpha")), resourcesDir);
        String markdown = readPage(resourcesDir, "Alpha");

        for (String line : markdown.split("\n")) {
            if (line.startsWith("| ") && line.contains("/yr |")) {
                assertFalse("zero share listed: " + line, line.contains("| 0.00% |"));
            }
        }
    }

    /** The subduction interface is pulled out of the tail and placed last, on top of the stack. */
    @Test
    public void subductionSourcesComeAfterOther() {
        MfdSourceDecomposition decomposition = decomposeAlpha(makeSolution());
        List<SourceContribution> ordered =
                new FaultSourceMfdPlot(List.of("Alpha")).setTopN(1).displayOrder(decomposition);
        List<String> names =
                ordered.stream()
                        .map(SourceContribution::getParentName)
                        .collect(Collectors.toList());

        // self, then the single biggest other source, then the aggregated tail, then subduction
        assertEquals(List.of("Alpha", "Beta", MfdSourceDecomposition.OTHER_NAME, "Delta"), names);
    }

    /** A subduction source too small for the top N still gets its own band. */
    @Test
    public void subductionSourceSurvivesTheTopNCut() {
        MfdSourceDecomposition decomposition = decomposeAlpha(makeSolution());
        SourceContribution delta = contributionOf(decomposition, DELTA);

        // Delta contributes less than Beta, so a top-1 cut would otherwise bury it
        assertTrue(
                delta.getWeightedRate()
                        < decomposition.getContributions().get(1).getWeightedRate());
        assertTrue(FaultSourceMfdPlot.isSubduction(delta));
        assertTrue(FaultSourceMfdPlot.contributesToAnyBin(decomposition, delta));
    }

    /** A subduction source that never registers in any bin stays in the tail. */
    @Test
    public void invisibleSubductionSourceIsNotSingledOut() {
        FaultSystemSolution sol = makeSolution();
        // starve the subduction rupture so that its share of its own bin rounds to zero
        sol.getRateForAllRups()[4] = 0d;
        MfdSourceDecomposition decomposition = decomposeAlpha(sol);
        SourceContribution delta = contributionOf(decomposition, DELTA);

        assertTrue(FaultSourceMfdPlot.isSubduction(delta));
        assertFalse(FaultSourceMfdPlot.contributesToAnyBin(decomposition, delta));

        List<String> names =
                new FaultSourceMfdPlot(List.of("Alpha"))
                        .setTopN(1).displayOrder(decomposition).stream()
                                .map(SourceContribution::getParentName)
                                .collect(Collectors.toList());
        assertEquals(MfdSourceDecomposition.OTHER_NAME, names.get(names.size() - 1));
    }

    /**
     * The mix chart normalises every bin to full height, so a bin carrying almost none of the rate
     * has to be left out or it reads as being as important as the bins that carry the hazard.
     */
    @Test
    public void negligibleBinsAreLeftOutOfTheMix() {
        MfdSourceDecomposition decomposition = decomposeAlpha(makeSolution());
        IncrementalMagFreqDist total = decomposition.getParticipationMfd();
        boolean[] plotted = FaultSourceMfdPlot.significantBins(decomposition);

        // rupture 5 is a millionth of a millionth of the rate, rupture 3 under a tenth of a percent
        assertFalse(plotted[total.getClosestXIndex(MAGS[5])]);
        assertFalse(plotted[total.getClosestXIndex(MAGS[3])]);
        // the ruptures that carry the rate stay
        assertTrue(plotted[total.getClosestXIndex(MAGS[0])]);
        assertTrue(plotted[total.getClosestXIndex(MAGS[1])]);
        assertTrue(plotted[total.getClosestXIndex(MAGS[4])]);
    }

    /** What the omitted bins were worth, for the caption to report. */
    @Test
    public void omittedRateAddsUpTheDroppedBins() {
        MfdSourceDecomposition decomposition = decomposeAlpha(makeSolution());
        boolean[] plotted = FaultSourceMfdPlot.significantBins(decomposition);

        assertEquals(
                RATES[3] + RATES[5], FaultSourceMfdPlot.omittedRate(decomposition, plotted), 1e-15);
    }

    /** The caption says how big the aggregated tail is, so a wide grey band can be read. */
    @Test
    public void otherNoteQuantifiesTheTail() {
        MfdSourceDecomposition decomposition = decomposeAlpha(makeSolution());
        FaultSourceMfdPlot plot = new FaultSourceMfdPlot(List.of("Alpha")).setTopN(1);
        boolean[] plotted = FaultSourceMfdPlot.significantBins(decomposition);

        String note =
                FaultSourceMfdPlot.otherNote(
                        decomposition, plot.displayOrder(decomposition), plotted);

        // Beta is named, Delta is pulled out as subduction, so Gamma and Tiny make up Other
        assertNotNull(note);
        assertTrue(note, note.contains("gathers 2 further faults"));
    }

    /** With every source drawn separately there is no tail to describe. */
    @Test
    public void otherNoteIsAbsentWhenEverySourceIsShown() {
        MfdSourceDecomposition decomposition = decomposeAlpha(makeSolution());
        FaultSourceMfdPlot plot = new FaultSourceMfdPlot(List.of("Alpha"));

        assertNull(
                FaultSourceMfdPlot.otherNote(
                        decomposition,
                        plot.displayOrder(decomposition),
                        FaultSourceMfdPlot.significantBins(decomposition)));
    }

    /** A fault the rupture set does not know about is skipped rather than failing the report. */
    @Test
    public void unknownFaultIsSkipped() throws IOException {
        File resourcesDir = makeResourcesDir();
        List<String> lines =
                runPlot(new FaultSourceMfdPlot(List.of("Alpha", "Nowhere")), resourcesDir);

        String markdown = String.join("\n", lines);
        assertTrue(markdown.contains("Alpha"));
        assertFalse(markdown.contains("Nowhere"));
    }

    /** With nothing to report the plot returns null so that no empty section is emitted. */
    @Test
    public void returnsNullWhenNoFaultMatches() throws IOException {
        File resourcesDir = makeResourcesDir();
        assertNull(runPlot(new FaultSourceMfdPlot(List.of("Nowhere")), resourcesDir));
    }

    /** Rupture set only reports need no source decomposition. */
    @Test
    public void returnsNullWithoutASolution() throws IOException {
        FaultSourceMfdPlot plot = new FaultSourceMfdPlot();
        assertNull(
                plot.plot(
                        makeSolution().getRupSet(),
                        null,
                        mock(ReportMetadata.class),
                        makeResourcesDir(),
                        "resources",
                        ""));
    }

    /** The plot is registered so that it can be selected by name, as the python gateway does. */
    @Test
    public void isRegisteredAsASolutionPlot() {
        assertNotNull(new NZSHM22_ReportPageGen().addPlot("FaultSourceMfdPlot"));
    }
}

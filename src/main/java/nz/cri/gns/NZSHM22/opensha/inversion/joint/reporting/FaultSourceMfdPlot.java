package nz.cri.gns.NZSHM22.opensha.inversion.joint.reporting;

import com.google.common.base.Preconditions;
import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nz.cri.gns.NZSHM22.util.MfdSourceDecomposition;
import nz.cri.gns.NZSHM22.util.MfdSourceDecomposition.SourceContribution;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.data.Range;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotElement;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotPreferences;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotUtils;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractRupSetPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.SolMFDPlot;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

/**
 * Report plot that shows, for each of a configured list of subject faults, the fault's
 * magnitude-frequency distribution split into the contributions of the parent faults that supply
 * it.
 *
 * <p>Each subject fault gets its own markdown sub-page under {@code fault_source_pages}, in the
 * same way that opensha's {@code NamedFaultPlot} builds its special fault pages. The main report
 * carries a summary table linking to them.
 *
 * <p>The split itself is done by {@link MfdSourceDecomposition}: rupture rates are shared among the
 * parent faults a rupture covers in proportion to the moment each carries, so the contributions add
 * up to the subject's participation MFD and can be stacked.
 */
public class FaultSourceMfdPlot extends AbstractRupSetPlot {

    /** Subject faults used when none are given, named by parent section name. */
    public static final List<String> DEFAULT_FAULT_NAMES =
            List.of("Alpine: Jacksons to Kaniere", "Wairarapa: 1");

    /** Directory, beside the report's resources directory, holding the per fault sub-pages. */
    public static final String PAGES_DIR = "fault_source_pages";

    /** Colour of the subject fault's own share in every plot. */
    protected static final Color SELF_COLOR = Color.BLUE;

    /** Colour of the subject fault's total participation in every plot. */
    protected static final Color TOTAL_COLOR = Color.BLACK;

    /** Colour of the aggregated tail of the contribution list. */
    protected static final Color OTHER_COLOR = Color.GRAY;

    /**
     * Colours reserved for the subduction interfaces. Deliberately outside the rainbow used for
     * crustal sources, so that an interface band cannot be mistaken for a neighbouring fault.
     */
    protected static final List<Color> SUBDUCTION_COLORS =
            List.of(new Color(148, 0, 211), new Color(139, 69, 19));

    /**
     * How much of the rainbow the crustal sources use. The last fraction of it is blue, which is
     * reserved for the subject fault's own share.
     */
    protected static final double RAINBOW_SPAN = 0.75;

    /** How many decades of rate a log axis shows below its peak. */
    protected static final int Y_RANGE_DECADES = 6;

    /** A share that formats as this is treated as no contribution at all. */
    protected static final String ZERO_PERCENT = percentDF.format(0d);

    /**
     * Magnitude bins holding less than this share of the subject rate are left out of the source
     * mix. A normalised chart draws every bin the same width, so without a floor a bin carrying a
     * millionth of the rate looks exactly as important as the bin carrying a third of it.
     */
    protected static final double MIX_RATE_FLOOR = 1e-3;

    protected final List<String> faultNames;
    protected int topN = 12;

    /** Creates a plot for {@link #DEFAULT_FAULT_NAMES}. */
    public FaultSourceMfdPlot() {
        this(DEFAULT_FAULT_NAMES);
    }

    /**
     * Creates a plot for the given subject faults.
     *
     * @param faultNames parent section names of the faults to report on
     */
    public FaultSourceMfdPlot(List<String> faultNames) {
        this.faultNames = List.copyOf(faultNames);
    }

    /**
     * Sets how many source faults are shown separately before the rest are folded into "Other".
     *
     * @param topN number of sources to keep separate
     * @return this plot, for chaining
     */
    public FaultSourceMfdPlot setTopN(int topN) {
        Preconditions.checkArgument(topN > 0, "topN must be positive");
        this.topN = topN;
        return this;
    }

    @Override
    public String getName() {
        return "Fault Source MFDs";
    }

    @Override
    public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
        return null;
    }

    @Override
    public List<String> plot(
            FaultSystemRupSet rupSet,
            FaultSystemSolution sol,
            ReportMetadata meta,
            File resourcesDir,
            String relPathToResources,
            String topLink)
            throws IOException {

        if (sol == null) return null;

        Map<String, Integer> parentIds = parentIdsByName(rupSet);
        IncrementalMagFreqDist binning =
                SolMFDPlot.initDefaultMFD(rupSet.getMinMag(), rupSet.getMaxMag());

        File faultsDir = new File(resourcesDir.getParentFile(), PAGES_DIR);
        Preconditions.checkState(faultsDir.exists() || faultsDir.mkdir());

        TableBuilder table = MarkdownUtils.tableBuilder();
        table.addLine(
                "Fault",
                "Total Rate",
                "Self-sourced",
                "Imported",
                "Amplification",
                "Sources",
                "Largest Source",
                "Max Mag");

        boolean any = false;
        for (String faultName : faultNames) {
            Integer parentId = parentIds.get(faultName);
            if (parentId == null) {
                System.err.println(
                        "FaultSourceMfdPlot: no parent section named '"
                                + faultName
                                + "', skipping");
                continue;
            }

            MfdSourceDecomposition decomposition =
                    new MfdSourceDecomposition(sol, Set.of(parentId), binning);
            if (decomposition.getRupCount() == 0) {
                System.err.println(
                        "FaultSourceMfdPlot: no ruptures for '" + faultName + "', skipping");
                continue;
            }

            String dirName = writeFaultPage(sol, faultName, decomposition, faultsDir);
            any = true;

            String link = relPathToResources + "/../" + PAGES_DIR + "/" + dirName;
            SourceContribution largest = largestOtherSource(decomposition);
            table.addLine(
                    "[" + faultName + "](" + link + ")",
                    rateStr(decomposition.getTotalRate()),
                    percentStr(decomposition.getSelfRate(), decomposition.getTotalRate()),
                    percentStr(decomposition.getImportedRate(), decomposition.getTotalRate()),
                    amplificationStr(decomposition),
                    countDF.format(decomposition.getContributions().size()),
                    largest == null ? na : largest.getParentName(),
                    twoDigits.format(decomposition.getMaxMag()));
        }

        if (!any) return null;

        List<String> lines = new ArrayList<>();
        lines.add(
                "Each fault's participation MFD split into the contributions of the parent faults "
                        + "that supply it. Rupture rates are shared among the parents a rupture "
                        + "covers in proportion to the moment each carries, so the contributions "
                        + "add up to the fault's total participation rate. The self-sourced share "
                        + "is the fault's nucleation MFD; the imported share is what other faults "
                        + "bring to it.");
        lines.add("");
        lines.addAll(table.build());
        return lines;
    }

    /**
     * Maps parent section names to their ids.
     *
     * @param rupSet the rupture set
     * @return name to parent id, in section order
     */
    protected static Map<String, Integer> parentIdsByName(FaultSystemRupSet rupSet) {
        Map<String, Integer> parentIds = new LinkedHashMap<>();
        for (FaultSection sect : rupSet.getFaultSectionDataList()) {
            if (sect.getParentSectionId() >= 0) {
                parentIds.putIfAbsent(sect.getParentSectionName(), sect.getParentSectionId());
            }
        }
        return parentIds;
    }

    /**
     * The biggest contributor that is not the subject fault itself.
     *
     * @param decomposition the decomposition
     * @return the largest other source, or null if the fault only ever ruptures alone
     */
    protected static SourceContribution largestOtherSource(MfdSourceDecomposition decomposition) {
        for (SourceContribution contribution : decomposition.getContributions()) {
            if (!decomposition.getSubjectParentIds().contains(contribution.getParentId())) {
                return contribution;
            }
        }
        return null;
    }

    /**
     * Splits the contributions into the subject's own share and the other sources, largest first,
     * with everything past {@link #topN} folded into a single "Other" entry.
     *
     * @param decomposition the decomposition
     * @return the subject's own contributions first, then the sources to show separately
     */
    protected List<SourceContribution> displayOrder(MfdSourceDecomposition decomposition) {
        List<SourceContribution> self = new ArrayList<>();
        List<SourceContribution> subduction = new ArrayList<>();
        List<SourceContribution> others = new ArrayList<>();
        for (SourceContribution contribution : decomposition.getContributions()) {
            if (decomposition.getSubjectParentIds().contains(contribution.getParentId())) {
                self.add(contribution);
            } else if (isSubduction(contribution)
                    && contributesToAnyBin(decomposition, contribution)) {
                subduction.add(contribution);
            } else {
                others.add(contribution);
            }
        }

        List<SourceContribution> ordered = new ArrayList<>(self);
        if (others.size() <= topN) {
            ordered.addAll(others);
        } else {
            ordered.addAll(others.subList(0, topN));
            ordered.add(
                    decomposition.aggregate(
                            others.subList(topN, others.size()),
                            MfdSourceDecomposition.OTHER_NAME));
        }
        // subduction interfaces last, so that they sit on top of the stack rather than being
        // swallowed by the aggregated tail
        ordered.addAll(subduction);
        return ordered;
    }

    /**
     * Whether a source is one of the subduction interfaces.
     *
     * @param contribution a contribution
     * @return true if the source sits on the Hikurangi or Puysegur interface
     */
    protected static boolean isSubduction(SourceContribution contribution) {
        return contribution.getPartition() != null && contribution.getPartition().isSubduction();
    }

    /**
     * Whether a source supplies a visible share of at least one magnitude bin. Sources that round
     * to zero everywhere are not worth a curve or a band of their own.
     *
     * @param decomposition the decomposition
     * @param contribution a contribution
     * @return true if its share of some bin does not round to zero
     */
    protected static boolean contributesToAnyBin(
            MfdSourceDecomposition decomposition, SourceContribution contribution) {
        IncrementalMagFreqDist total = decomposition.getParticipationMfd();
        for (int bin = 0; bin < total.size(); bin++) {
            if (total.getY(bin) > 0
                    && !roundsToZeroPercent(contribution.getMfd().getY(bin) / total.getY(bin))) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param fraction a share of a total
     * @return true if the share formats as zero percent
     */
    protected static boolean roundsToZeroPercent(double fraction) {
        return ZERO_PERCENT.equals(percentDF.format(fraction));
    }

    /**
     * A colour per displayed source: the subject's own share is always blue, the aggregated tail
     * always grey, and the rest are spread over a rainbow.
     *
     * @param decomposition the decomposition
     * @param ordered the sources in display order
     * @return one colour per source
     * @throws IOException if the colour palette cannot be loaded
     */
    protected static List<Color> colorsFor(
            MfdSourceDecomposition decomposition, List<SourceContribution> ordered)
            throws IOException {
        int numRainbow = 0;
        for (SourceContribution contribution : ordered) {
            if (!isSelf(decomposition, contribution)
                    && !isOther(contribution)
                    && !isSubduction(contribution)) {
                numRainbow++;
            }
        }
        // reversed so that the palette starts at red, and stretched past the last index so that
        // its blue end goes unused: blue is the subject fault's own colour
        CPT cpt =
                GMT_CPT_Files.RAINBOW_UNIFORM
                        .instance()
                        .reverse()
                        .rescale(0d, Math.max(1, numRainbow - 1) / RAINBOW_SPAN);

        List<Color> colors = new ArrayList<>();
        int rainbowIndex = 0;
        int subductionIndex = 0;
        for (SourceContribution contribution : ordered) {
            if (isSelf(decomposition, contribution)) {
                colors.add(SELF_COLOR);
            } else if (isOther(contribution)) {
                colors.add(OTHER_COLOR);
            } else if (isSubduction(contribution)) {
                colors.add(SUBDUCTION_COLORS.get(subductionIndex++ % SUBDUCTION_COLORS.size()));
            } else {
                colors.add(cpt.getColor(rainbowIndex++));
            }
        }
        return colors;
    }

    /**
     * @param decomposition the decomposition
     * @param contribution a contribution
     * @return true if the contribution is the subject fault's own share
     */
    protected static boolean isSelf(
            MfdSourceDecomposition decomposition, SourceContribution contribution) {
        return decomposition.getSubjectParentIds().contains(contribution.getParentId());
    }

    /**
     * @param contribution a contribution
     * @return true if the contribution is the aggregated tail
     */
    protected static boolean isOther(SourceContribution contribution) {
        return contribution.getParentId() == MfdSourceDecomposition.OTHER_PARENT_ID;
    }

    /**
     * Writes the sub-page for one subject fault.
     *
     * @param sol the solution
     * @param faultName parent section name of the subject fault
     * @param decomposition its decomposition
     * @param faultsDir directory holding all fault sub-pages
     * @return the name of the directory this fault's page was written to
     * @throws IOException if writing fails
     */
    protected String writeFaultPage(
            FaultSystemSolution sol,
            String faultName,
            MfdSourceDecomposition decomposition,
            File faultsDir)
            throws IOException {
        String dirName = getFileSafe(faultName);
        File faultDir = new File(faultsDir, dirName);
        Preconditions.checkState(faultDir.exists() || faultDir.mkdir());
        File resourcesDir = new File(faultDir, "resources");
        Preconditions.checkState(resourcesDir.exists() || resourcesDir.mkdir());

        List<SourceContribution> ordered = displayOrder(decomposition);
        List<Color> colors = colorsFor(decomposition, ordered);
        String topLink = "_[(top)](#table-of-contents)_";

        List<String> lines = new ArrayList<>();
        lines.add("# " + faultName + " MFD Sources");
        lines.add("");
        lines.addAll(headerTable(sol, decomposition).build());
        lines.add("");

        int tocIndex = lines.size();

        lines.addAll(mfdLines(decomposition, ordered, colors, faultName, resourcesDir, topLink));
        lines.addAll(
                sourceMixLines(decomposition, ordered, colors, faultName, resourcesDir, topLink));
        lines.addAll(amplificationLines(decomposition, faultName, resourcesDir, topLink));
        lines.addAll(contributorLines(decomposition, topLink));

        lines.addAll(tocIndex, MarkdownUtils.buildTOC(lines, 2, 3));
        lines.add(tocIndex, "## Table Of Contents");

        MarkdownUtils.writeReadmeAndHTML(lines, faultDir);
        return dirName;
    }

    /**
     * The property table at the top of a fault's page.
     *
     * @param sol the solution
     * @param decomposition the decomposition
     * @return the table
     */
    protected TableBuilder headerTable(
            FaultSystemSolution sol, MfdSourceDecomposition decomposition) {
        int subsections = 0;
        for (FaultSection sect : sol.getRupSet().getFaultSectionDataList()) {
            if (decomposition.getSubjectParentIds().contains(sect.getParentSectionId())) {
                subsections++;
            }
        }
        SourceContribution largest = largestOtherSource(decomposition);

        TableBuilder table = MarkdownUtils.tableBuilder();
        table.addLine("_Property_", "_Value_");
        table.addLine("**Subsections**", countDF.format(subsections));
        table.addLine("**Rupture Count**", countDF.format(decomposition.getRupCount()));
        table.addLine(
                "**Ruptures w/ Nonzero Rates**",
                countDF.format(decomposition.getNonZeroRupCount()));
        table.addLine(
                "**Magnitude Range**",
                "["
                        + twoDigits.format(decomposition.getMinMag())
                        + ", "
                        + twoDigits.format(decomposition.getMaxMag())
                        + "]");
        table.addLine("**Total Participation Rate**", rateStr(decomposition.getTotalRate()));
        table.addLine(
                "**Self-sourced Rate**",
                percentStr(decomposition.getSelfRate(), decomposition.getTotalRate()));
        table.addLine(
                "**Imported Rate**",
                percentStr(decomposition.getImportedRate(), decomposition.getTotalRate()));
        table.addLine("**Co-rupture Amplification**", amplificationStr(decomposition));
        table.addLine(
                "**Contributing Faults**", countDF.format(decomposition.getContributions().size()));
        table.addLine("**Largest Other Source**", largest == null ? na : largest.getParentName());
        return table;
    }

    /**
     * The incremental and cumulative MFD figures, with the subject's total, its own share and the
     * biggest source contributions overlaid on a log axis.
     *
     * @param decomposition the decomposition
     * @param ordered sources in display order
     * @param colors one colour per source
     * @param faultName name of the subject fault
     * @param resourcesDir where to write the images
     * @param topLink markdown link back to the table of contents
     * @return markdown lines
     * @throws IOException if writing fails
     */
    protected List<String> mfdLines(
            MfdSourceDecomposition decomposition,
            List<SourceContribution> ordered,
            List<Color> colors,
            String faultName,
            File resourcesDir,
            String topLink)
            throws IOException {

        List<DiscretizedFunc> incrFuncs = new ArrayList<>();
        List<PlotCurveCharacterstics> incrChars = new ArrayList<>();
        List<DiscretizedFunc> cmlFuncs = new ArrayList<>();
        List<PlotCurveCharacterstics> cmlChars = new ArrayList<>();

        IncrementalMagFreqDist total = decomposition.getParticipationMfd();
        total.setName("Participation (total)");
        incrFuncs.add(total);
        incrChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, TOTAL_COLOR));
        cmlFuncs.add(named(total.getCumRateDistWithOffset(), total.getName()));
        cmlChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, TOTAL_COLOR));

        for (int i = 0; i < ordered.size(); i++) {
            SourceContribution contribution = ordered.get(i);
            IncrementalMagFreqDist mfd = contribution.getMfd();
            mfd.setName(legendName(decomposition, contribution));
            float width = isSelf(decomposition, contribution) ? 3f : 2f;
            incrFuncs.add(mfd);
            incrChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, width, colors.get(i)));
            cmlFuncs.add(named(mfd.getCumRateDistWithOffset(), mfd.getName()));
            cmlChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, width, colors.get(i)));
        }

        Range xRange = magRange(decomposition);
        String incrPrefix = "source_mfd";
        String cmlPrefix = "source_mfd_cumulative";
        writeLogPlot(
                incrFuncs,
                incrChars,
                faultName + " Source Contributions",
                "Incremental Rate (per yr)",
                xRange,
                resourcesDir,
                incrPrefix);
        writeLogPlot(
                cmlFuncs,
                cmlChars,
                faultName + " Source Contributions",
                "Cumulative Rate (per yr)",
                xRange,
                resourcesDir,
                cmlPrefix);

        List<String> lines = new ArrayList<>();
        lines.add("## Magnitude-Frequency Distribution");
        lines.add(topLink);
        lines.add("");
        lines.add(
                "The black curve is the fault's participation MFD: every rupture that touches it, "
                        + "at its full rate. The coloured curves are what each source fault "
                        + "contributes, and they add up to the black curve.");
        lines.add("");
        TableBuilder table = MarkdownUtils.tableBuilder();
        table.addLine("Incremental", "Cumulative");
        table.initNewLine();
        table.addColumn("![Incremental](resources/" + incrPrefix + ".png)");
        table.addColumn("![Cumulative](resources/" + cmlPrefix + ".png)");
        table.finalizeLine();
        lines.addAll(table.build());
        lines.add("");
        return lines;
    }

    /**
     * The stacked source mix figure: what fraction of the fault's rate each source supplies, per
     * magnitude bin.
     *
     * @param decomposition the decomposition
     * @param ordered sources in display order
     * @param colors one colour per source
     * @param faultName name of the subject fault
     * @param resourcesDir where to write the image
     * @param topLink markdown link back to the table of contents
     * @return markdown lines
     * @throws IOException if writing fails
     */
    protected List<String> sourceMixLines(
            MfdSourceDecomposition decomposition,
            List<SourceContribution> ordered,
            List<Color> colors,
            String faultName,
            File resourcesDir,
            String topLink)
            throws IOException {

        IncrementalMagFreqDist total = decomposition.getParticipationMfd();
        boolean[] plotted = significantBins(decomposition);
        int first = firstPlottedBin(plotted);
        if (first < 0) return List.of();
        int last = lastPlottedBin(plotted);

        List<PlotElement> funcs = new ArrayList<>();
        List<PlotCurveCharacterstics> chars = new ArrayList<>();

        double[] lower = new double[total.size()];
        for (int i = 0; i < ordered.size(); i++) {
            SourceContribution contribution = ordered.get(i);
            double[] upper = new double[total.size()];
            for (int bin = 0; bin < total.size(); bin++) {
                // an omitted bin leaves every band with no height, so the column reads as a gap
                double fraction =
                        plotted[bin] ? contribution.getMfd().getY(bin) / total.getY(bin) : 0;
                upper[bin] = lower[bin] + fraction;
            }
            funcs.add(
                    band(
                            total,
                            lower,
                            upper,
                            first,
                            last,
                            legendName(decomposition, contribution)));
            chars.add(new PlotCurveCharacterstics(PlotLineType.POLYGON_SOLID, 1f, colors.get(i)));
            lower = upper;
        }

        String prefix = "source_mix";
        PlotSpec spec =
                new PlotSpec(
                        funcs,
                        chars,
                        faultName + " Source Mix",
                        "Magnitude",
                        "Fraction of Participation Rate");
        spec.setLegendVisible(true);
        spec.setLegendLocation(RectangleEdge.BOTTOM);

        HeadlessGraphPanel gp = PlotUtils.initHeadless(PlotPreferences.getDefaultAppPrefs());
        gp.setTickLabelFontSize(20);
        gp.drawGraphPanel(spec, false, false, binRange(total, first, last), new Range(0d, 1d));
        PlotUtils.writePlots(resourcesDir, prefix, gp, 1000, 850, true, true, false);

        List<String> lines = new ArrayList<>();
        lines.add("## Source Mix by Magnitude");
        lines.add(topLink);
        lines.add("");
        lines.add(
                "The same contributions, normalised so that each magnitude bin sums to one. The "
                        + "bottom band is the fault's own share; the bands above it are what other "
                        + "faults bring in. A fault that breaks on its own at small magnitudes and "
                        + "only ever joins larger events shows the blue band shrinking to the right.");
        lines.add("");
        lines.add("![Source Mix](resources/" + prefix + ".png)");
        lines.add("");
        double omitted = omittedRate(decomposition, plotted);
        if (omitted > 0) {
            lines.add(
                    "_Bins holding less than "
                            + percentDF.format(MIX_RATE_FLOOR)
                            + " of the rate are left out: every bin is drawn the same width, so "
                            + "they would look as important as the bins that carry the hazard. "
                            + "Together they hold "
                            + percentDF.format(omitted / decomposition.getTotalRate())
                            + " of the rate; the magnitude-frequency plot further up still "
                            + "covers them._");
            lines.add("");
        }
        String note = otherNote(decomposition, ordered, plotted);
        if (note != null) {
            lines.add(note);
            lines.add("");
        }
        return lines;
    }

    /**
     * The co-rupture amplification figure: how many times more often the fault shakes than it
     * sources, per magnitude bin.
     *
     * @param decomposition the decomposition
     * @param faultName name of the subject fault
     * @param resourcesDir where to write the image
     * @param topLink markdown link back to the table of contents
     * @return markdown lines
     * @throws IOException if writing fails
     */
    protected List<String> amplificationLines(
            MfdSourceDecomposition decomposition,
            String faultName,
            File resourcesDir,
            String topLink)
            throws IOException {

        IncrementalMagFreqDist total = decomposition.getParticipationMfd();
        IncrementalMagFreqDist self = decomposition.getSelfMfd();

        ArbitrarilyDiscretizedFunc amplification = new ArbitrarilyDiscretizedFunc();
        amplification.setName("Participation / self-sourced");
        double maxY = 1d;
        for (int bin = 0; bin < total.size(); bin++) {
            if (self.getY(bin) > 0) {
                double ratio = total.getY(bin) / self.getY(bin);
                amplification.set(total.getX(bin), ratio);
                maxY = Math.max(maxY, ratio);
            }
        }
        if (amplification.size() == 0) {
            return List.of();
        }

        ArbitrarilyDiscretizedFunc unity = new ArbitrarilyDiscretizedFunc();
        unity.setName("Ruptures on this fault only");
        unity.set(amplification.getMinX(), 1d);
        unity.set(amplification.getMaxX(), 1d);

        List<DiscretizedFunc> funcs = List.of(amplification, unity);
        List<PlotCurveCharacterstics> chars =
                List.of(
                        new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, TOTAL_COLOR),
                        new PlotCurveCharacterstics(PlotLineType.DASHED, 1f, Color.GRAY));

        String prefix = "source_amplification";
        PlotSpec spec =
                new PlotSpec(
                        funcs,
                        chars,
                        faultName + " Co-rupture Amplification",
                        "Magnitude",
                        "Participation Rate / Nucleation Rate");
        spec.setLegendVisible(true);
        spec.setLegendLocation(RectangleEdge.BOTTOM);

        HeadlessGraphPanel gp = PlotUtils.initHeadless(PlotPreferences.getDefaultAppPrefs());
        gp.setTickLabelFontSize(20);
        gp.drawGraphPanel(spec, false, false, magRange(decomposition), new Range(0d, maxY * 1.05));
        PlotUtils.writePlots(resourcesDir, prefix, gp, 1000, 700, true, true, false);

        List<String> lines = new ArrayList<>();
        lines.add("## Co-rupture Amplification");
        lines.add(topLink);
        lines.add("");
        lines.add(
                "How many times more often the fault shakes than it sources. One means every "
                        + "event of that size starts and stays on this fault; higher values mean "
                        + "the fault is being carried along by ruptures sourced elsewhere.");
        lines.add("");
        lines.add("![Amplification](resources/" + prefix + ".png)");
        lines.add("");
        return lines;
    }

    /**
     * The table of every contributing parent fault.
     *
     * @param decomposition the decomposition
     * @param topLink markdown link back to the table of contents
     * @return markdown lines
     */
    protected List<String> contributorLines(MfdSourceDecomposition decomposition, String topLink) {
        TableBuilder table = MarkdownUtils.tableBuilder();
        table.addLine(
                "Source Fault",
                "Partition",
                "Contribution",
                "% of Total",
                "Co-rupture Rate",
                "Ruptures",
                "Nonzero",
                "Mag Range");

        double total = decomposition.getTotalRate();
        int omitted = 0;
        for (SourceContribution contribution : decomposition.getContributions()) {
            double share = total > 0 ? contribution.getWeightedRate() / total : 0d;
            if (roundsToZeroPercent(share)) {
                omitted++;
                continue;
            }
            String name = contribution.getParentName();
            if (isSelf(decomposition, contribution)) {
                name = name + " _(self)_";
            }
            table.addLine(
                    name,
                    contribution.getPartition() == null ? na : contribution.getPartition().name(),
                    rateStr(contribution.getWeightedRate()),
                    percentDF.format(share),
                    rateStr(contribution.getCoRuptureRate()),
                    countDF.format(contribution.getRupCount()),
                    countDF.format(contribution.getNonZeroRupCount()),
                    "["
                            + twoDigits.format(contribution.getMinMag())
                            + ", "
                            + twoDigits.format(contribution.getMaxMag())
                            + "]");
        }

        List<String> lines = new ArrayList<>();
        lines.add("## Contributing Faults");
        lines.add(topLink);
        lines.add("");
        lines.add(
                "_Contribution_ is the moment weighted share of the fault's participation rate. "
                        + "_Co-rupture rate_ credits each shared rupture in full to every fault it "
                        + "touches, so it over-counts multi-fault ruptures and does not sum to the "
                        + "total.");
        if (omitted > 0) {
            lines.add("");
            lines.add(
                    "_"
                            + countDF.format(omitted)
                            + " further sources contribute a share that rounds to "
                            + ZERO_PERCENT
                            + " and are not listed._");
        }
        lines.add("");
        lines.addAll(table.build());
        lines.add("");
        return lines;
    }

    /**
     * Builds a closed polygon covering the band between two cumulative curves, for a stacked area
     * plot.
     *
     * @param binning MFD supplying the magnitude of each bin
     * @param lower running total below this band
     * @param upper running total including this band
     * @param first first bin to include
     * @param last last bin to include
     * @param name name shown in the legend
     * @return the polygon
     */
    protected static DefaultXY_DataSet band(
            IncrementalMagFreqDist binning,
            double[] lower,
            double[] upper,
            int first,
            int last,
            String name) {
        DefaultXY_DataSet polygon = new DefaultXY_DataSet();
        double half = 0.5 * binning.getDelta();
        // along the top of the band, then back along the bottom, as a bar per bin
        for (int bin = first; bin <= last; bin++) {
            polygon.set(binning.getX(bin) - half, upper[bin]);
            polygon.set(binning.getX(bin) + half, upper[bin]);
        }
        for (int bin = last; bin >= first; bin--) {
            polygon.set(binning.getX(bin) + half, lower[bin]);
            polygon.set(binning.getX(bin) - half, lower[bin]);
        }
        polygon.setName(name);
        return polygon;
    }

    /**
     * Renders a plot with a logarithmic y axis, reusing the decade snapping of {@link
     * JointRuptureRatePlot}.
     *
     * @param funcs the data series
     * @param chars line characteristics, one per series
     * @param title plot title
     * @param yLabel y axis label
     * @param xRange x axis range
     * @param resourcesDir where to write the image
     * @param prefix file name prefix
     * @throws IOException if writing fails
     */
    protected static void writeLogPlot(
            List<DiscretizedFunc> funcs,
            List<PlotCurveCharacterstics> chars,
            String title,
            String yLabel,
            Range xRange,
            File resourcesDir,
            String prefix)
            throws IOException {
        PlotSpec spec = new PlotSpec(funcs, chars, title, "Magnitude", yLabel);
        // below the plot rather than inset: with a source per line an inset legend hides the data
        spec.setLegendVisible(true);
        spec.setLegendLocation(RectangleEdge.BOTTOM);

        HeadlessGraphPanel gp = PlotUtils.initHeadless(PlotPreferences.getDefaultAppPrefs());
        gp.setTickLabelFontSize(20);
        gp.drawGraphPanel(spec, false, true, xRange, logYRange(funcs));

        PlotUtils.writePlots(resourcesDir, prefix, gp, 1000, 850, true, true, false);
    }

    /**
     * A log y range covering the data, snapped to decades and floored at {@link #Y_RANGE_DECADES}
     * below the peak so that a handful of tiny contributions cannot stretch the axis until the
     * interesting curves are squashed flat.
     *
     * @param funcs the series to be plotted
     * @return the y range, or null to let the plot decide
     */
    protected static Range logYRange(List<DiscretizedFunc> funcs) {
        double minY = Double.POSITIVE_INFINITY;
        double maxY = 0;
        for (DiscretizedFunc func : funcs) {
            for (int i = 0; i < func.size(); i++) {
                double y = func.getY(i);
                if (y > 0) {
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxY <= 0) return null;

        double top = Math.pow(10, Math.ceil(Math.log10(maxY)));
        double bottom = Math.pow(10, Math.floor(Math.log10(minY)));
        return new Range(Math.max(bottom, top / Math.pow(10, Y_RANGE_DECADES)), top);
    }

    /**
     * Which magnitude bins carry enough rate to be worth drawing in the normalised source mix.
     *
     * @param decomposition the decomposition
     * @return one flag per magnitude bin
     */
    protected static boolean[] significantBins(MfdSourceDecomposition decomposition) {
        IncrementalMagFreqDist total = decomposition.getParticipationMfd();
        double floor = MIX_RATE_FLOOR * decomposition.getTotalRate();
        boolean[] plotted = new boolean[total.size()];
        for (int bin = 0; bin < total.size(); bin++) {
            plotted[bin] = total.getY(bin) > floor;
        }
        return plotted;
    }

    /**
     * @param plotted per bin flags
     * @return the first bin to draw, or -1 if there is none
     */
    protected static int firstPlottedBin(boolean[] plotted) {
        for (int bin = 0; bin < plotted.length; bin++) {
            if (plotted[bin]) return bin;
        }
        return -1;
    }

    /**
     * @param plotted per bin flags
     * @return the last bin to draw, or -1 if there is none
     */
    protected static int lastPlottedBin(boolean[] plotted) {
        for (int bin = plotted.length - 1; bin >= 0; bin--) {
            if (plotted[bin]) return bin;
        }
        return -1;
    }

    /**
     * The rate held by the bins that the source mix leaves out.
     *
     * @param decomposition the decomposition
     * @param plotted per bin flags
     * @return the omitted rate, in events per year
     */
    protected static double omittedRate(MfdSourceDecomposition decomposition, boolean[] plotted) {
        IncrementalMagFreqDist total = decomposition.getParticipationMfd();
        double omitted = 0;
        for (int bin = 0; bin < total.size(); bin++) {
            if (!plotted[bin]) omitted += total.getY(bin);
        }
        return omitted;
    }

    /**
     * A sentence quantifying what the aggregated tail is made of, so that a wide "Other" band can
     * be read as many small faults rather than one large one being hidden.
     *
     * @param decomposition the decomposition
     * @param ordered the sources drawn separately
     * @param plotted per bin flags
     * @return the sentence, or null if every source is drawn separately
     */
    protected static String otherNote(
            MfdSourceDecomposition decomposition,
            List<SourceContribution> ordered,
            boolean[] plotted) {
        Set<Integer> shown = new HashSet<>();
        for (SourceContribution contribution : ordered) {
            shown.add(contribution.getParentId());
        }

        IncrementalMagFreqDist total = decomposition.getParticipationMfd();
        int count = 0;
        double largestOverall = 0;
        double peakBinShare = 0;
        for (SourceContribution contribution : decomposition.getContributions()) {
            if (shown.contains(contribution.getParentId())) continue;
            count++;
            largestOverall = Math.max(largestOverall, contribution.getWeightedRate());
            for (int bin = 0; bin < total.size(); bin++) {
                if (plotted[bin]) {
                    peakBinShare =
                            Math.max(
                                    peakBinShare,
                                    contribution.getMfd().getY(bin) / total.getY(bin));
                }
            }
        }
        if (count == 0) return null;

        return "_Other_ gathers "
                + countDF.format(count)
                + " further faults; the largest supplies "
                + percentDF.format(largestOverall / decomposition.getTotalRate())
                + " of the rate, and none exceeds "
                + percentDF.format(peakBinShare)
                + " of any one magnitude bin. A wide grey band therefore means the rupture is "
                + "shared among many faults, not that one large contributor is hidden.";
    }

    /**
     * The x axis range covering a span of bins, padded by half a bin on each side.
     *
     * @param binning the magnitude bins
     * @param first first bin in the span
     * @param last last bin in the span
     * @return the range
     */
    protected static Range binRange(IncrementalMagFreqDist binning, int first, int last) {
        double half = 0.5 * binning.getDelta();
        return new Range(binning.getX(first) - half, binning.getX(last) + half);
    }

    /**
     * The magnitude range worth plotting: the bins the subject fault actually occupies, padded by
     * half a bin on each side.
     *
     * @param decomposition the decomposition
     * @return the x axis range
     */
    protected static Range magRange(MfdSourceDecomposition decomposition) {
        IncrementalMagFreqDist total = decomposition.getParticipationMfd();
        int first = firstPopulatedBin(total);
        int last = lastPopulatedBin(total);
        double half = 0.5 * total.getDelta();
        return new Range(total.getX(first) - half, total.getX(last) + half);
    }

    /**
     * @param mfd an MFD
     * @return the first bin with a positive rate, or 0 if there is none
     */
    protected static int firstPopulatedBin(IncrementalMagFreqDist mfd) {
        for (int bin = 0; bin < mfd.size(); bin++) {
            if (mfd.getY(bin) > 0) return bin;
        }
        return 0;
    }

    /**
     * @param mfd an MFD
     * @return the last bin with a positive rate, or the last bin if there is none
     */
    protected static int lastPopulatedBin(IncrementalMagFreqDist mfd) {
        for (int bin = mfd.size() - 1; bin >= 0; bin--) {
            if (mfd.getY(bin) > 0) return bin;
        }
        return mfd.size() - 1;
    }

    /**
     * @param decomposition the decomposition
     * @param contribution a contribution
     * @return the legend label for the contribution
     */
    protected static String legendName(
            MfdSourceDecomposition decomposition, SourceContribution contribution) {
        return isSelf(decomposition, contribution)
                ? contribution.getParentName() + " (self)"
                : contribution.getParentName();
    }

    /**
     * @param func a function
     * @param name the name to give it
     * @return the same function, named
     */
    protected static EvenlyDiscretizedFunc named(EvenlyDiscretizedFunc func, String name) {
        func.setName(name);
        return func;
    }

    /**
     * @param rate an annual rate
     * @return the rate formatted for a markdown table
     */
    protected static String rateStr(double rate) {
        return String.format("%.4e /yr", rate);
    }

    /**
     * @param part a rate
     * @param total the total it is part of
     * @return the rate with its share of the total in brackets
     */
    protected static String percentStr(double part, double total) {
        return rateStr(part) + " (" + percentDF.format(total > 0 ? part / total : 0d) + ")";
    }

    /**
     * @param decomposition the decomposition
     * @return the ratio of participation to nucleation rate, formatted
     */
    protected static String amplificationStr(MfdSourceDecomposition decomposition) {
        if (decomposition.getSelfRate() <= 0) return na;
        return twoDigits.format(decomposition.getTotalRate() / decomposition.getSelfRate()) + "x";
    }
}

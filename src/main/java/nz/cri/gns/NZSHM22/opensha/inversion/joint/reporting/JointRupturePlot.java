package nz.cri.gns.NZSHM22.opensha.inversion.joint.reporting;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.*;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionPredicate;
import nz.cri.gns.NZSHM22.opensha.ruptures.FaultSectionProperties;
import org.jfree.data.Range;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.eq.MagUtils;
import org.opensha.commons.geo.Region;
import org.opensha.commons.gui.plot.GeographicMapMaker;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractRupSetPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.SolidFillPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.SolMFDPlot;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupSetMapMaker;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

/**
 * Solution report plot focused on joint ruptures (ruptures spanning crustal and subduction
 * partitions). Produces participation rate maps of joint ruptures, showing only sections that take
 * part in at least one joint rupture, joint rupture MFDs, and a statistics table.
 */
public class JointRupturePlot extends AbstractRupSetPlot implements SolidFillPlot {

    /** The joint rupture categories. */
    protected static final List<String> JOINT_CATEGORIES =
            List.of("CRUSTAL+HIKURANGI", "CRUSTAL+PUYSEGUR");

    /** Colors for each joint category, in the same order as {@link #JOINT_CATEGORIES}. */
    protected static final List<Color> JOINT_COLORS = List.of(Color.MAGENTA, Color.ORANGE);

    protected Boolean fillSurfaces = null;

    @Override
    public void setFillSurfaces(boolean fillSurfaces) {
        this.fillSurfaces = fillSurfaces;
    }

    @Override
    public String getName() {
        return "Joint Ruptures";
    }

    /** Per joint category statistics. */
    protected static class JointStats {
        public int totalCount;
        public int withRateCount;
        public double rateSum;
        public double momentRate;
        public double minMagWithRate = Double.POSITIVE_INFINITY;
        public double maxMagWithRate = Double.NEGATIVE_INFINITY;
        public Set<Integer> sections = new HashSet<>();
    }

    /**
     * Returns the joint category of a rupture, or null if the rupture is not a joint rupture.
     *
     * @param rupSet the rupture set
     * @param rupIndex the rupture index
     * @return the joint category or null
     */
    protected static String jointCategory(FaultSystemRupSet rupSet, int rupIndex) {
        Set<PartitionPredicate> partitions =
                JointRuptureRatePlot.partitionsForRup(rupSet, rupIndex);
        if (partitions.size() < 2) {
            return null;
        }
        String category = JointRuptureRatePlot.classify(partitions);
        return JOINT_CATEGORIES.contains(category) ? category : null;
    }

    /**
     * Builds a log10 CPT covering the positive values, using the same bounds logic as OpenSHA's
     * ParticipationRatePlot.
     *
     * @param values the values to cover
     * @return the CPT
     * @throws IOException if the CPT cannot be loaded
     */
    protected static CPT buildRateCPT(double[] values) throws IOException {
        double minNonZero = Double.POSITIVE_INFINITY;
        double max = 0;
        for (double v : values) {
            if (v > 0) {
                minNonZero = Math.min(minNonZero, v);
                max = Math.max(max, v);
            }
        }
        double lower;
        if (minNonZero >= 1e-4 || Double.isInfinite(minNonZero)) {
            lower = -5;
        } else {
            lower = Math.max(-8, Math.floor(Math.log10(minNonZero)) - 1);
        }
        double upper;
        if (max > 0.2) {
            double logMax = Math.log10(max);
            upper = logMax - Math.floor(logMax) > 0.2 ? Math.ceil(logMax) : Math.floor(logMax);
        } else {
            upper = -1;
        }
        CPT cpt = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(lower, upper);
        cpt.setLog10(true);
        cpt.setNanColor(Color.GRAY);
        return cpt;
    }

    /**
     * Writes a participation map for the given sections.
     *
     * @param sections the sections to show
     * @param partic participation rates indexed by section id
     * @param cpt the color palette
     * @param region the map region
     * @param label the CPT label
     * @param resourcesDir output directory
     * @param prefix file name prefix
     * @throws IOException if writing fails
     */
    protected void writeMap(
            List<FaultSection> sections,
            double[] partic,
            CPT cpt,
            Region region,
            String label,
            File resourcesDir,
            String prefix)
            throws IOException {
        if (region == null) {
            region = GeographicMapMaker.buildBufferedRegion(sections);
        }
        RupSetMapMaker mapMaker = new RupSetMapMaker(sections, region);
        if (fillSurfaces != null) {
            mapMaker.setFillSurfaces(fillSurfaces);
        }
        List<Double> scalars = new ArrayList<>();
        for (FaultSection section : sections) {
            double rate = partic[section.getSectionId()];
            scalars.add(rate > 0 ? rate : Double.NaN);
        }
        mapMaker.plotSectScalars(scalars, cpt, label);
        mapMaker.plot(resourcesDir, prefix, " ");
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

        IncrementalMagFreqDist templateMFD =
                SolMFDPlot.initDefaultMFD(rupSet.getMinMag(), rupSet.getMaxMag());
        int numBins = templateMFD.size();
        int numSections = rupSet.getFaultSectionDataList().size();

        double[] jointPartic = new double[numSections];
        boolean[] inJoint = new boolean[numSections];
        Map<String, JointStats> stats = new LinkedHashMap<>();
        Map<String, IncrementalMagFreqDist> mfds = new LinkedHashMap<>();
        for (String cat : JOINT_CATEGORIES) {
            stats.put(cat, new JointStats());
            mfds.put(
                    cat,
                    new IncrementalMagFreqDist(
                            templateMFD.getMinX(), numBins, templateMFD.getDelta()));
        }
        IncrementalMagFreqDist totalMFD =
                new IncrementalMagFreqDist(templateMFD.getMinX(), numBins, templateMFD.getDelta());
        IncrementalMagFreqDist jointMFD =
                new IncrementalMagFreqDist(templateMFD.getMinX(), numBins, templateMFD.getDelta());

        for (int r = 0; r < rupSet.getNumRuptures(); r++) {
            double rate = sol.getRateForRup(r);
            double mag = rupSet.getMagForRup(r);
            int bin = templateMFD.getClosestXIndex(mag);
            totalMFD.add(bin, rate);

            String category = jointCategory(rupSet, r);
            if (category == null) continue;

            JointStats js = stats.get(category);
            js.totalCount++;
            List<Integer> sectIndices = rupSet.getSectionsIndicesForRup(r);
            for (int s : sectIndices) {
                inJoint[s] = true;
                jointPartic[s] += rate;
            }
            if (rate > 0) {
                js.withRateCount++;
                js.rateSum += rate;
                js.momentRate += rate * MagUtils.magToMoment(mag);
                js.minMagWithRate = Math.min(js.minMagWithRate, mag);
                js.maxMagWithRate = Math.max(js.maxMagWithRate, mag);
                js.sections.addAll(sectIndices);
                mfds.get(category).add(bin, rate);
                jointMFD.add(bin, rate);
            }
        }

        List<String> lines = new ArrayList<>();
        lines.add("Joint ruptures are ruptures that include both crustal and subduction sections.");
        lines.add("");

        // Participation maps
        List<FaultSection> crustalSections = new ArrayList<>();
        List<FaultSection> subductionSections = new ArrayList<>();
        for (int s = 0; s < numSections; s++) {
            if (!inJoint[s]) continue;
            FaultSection section = rupSet.getFaultSectionData(s);
            PartitionPredicate partition = FaultSectionProperties.getPartition(section);
            if (partition != null && partition.isSubduction()) {
                subductionSections.add(section);
            } else {
                crustalSections.add(section);
            }
        }

        if (!crustalSections.isEmpty() || !subductionSections.isEmpty()) {
            CPT cpt = buildRateCPT(jointPartic);
            Region region = meta != null ? meta.region : null;
            String label = "Joint Rupture Participation Rate (events/yr)";
            TableBuilder mapTable = MarkdownUtils.tableBuilder();
            mapTable.initNewLine();
            mapTable.addColumn(MarkdownUtils.boldCentered("Crustal Sections"));
            mapTable.addColumn(MarkdownUtils.boldCentered("Subduction Sections"));
            mapTable.finalizeLine();
            mapTable.initNewLine();
            for (String name : List.of("crustal", "subduction")) {
                List<FaultSection> sections =
                        name.equals("crustal") ? crustalSections : subductionSections;
                if (sections.isEmpty()) {
                    mapTable.addColumn("_No sections_");
                    continue;
                }
                String prefix = "joint_partic_" + name;
                writeMap(sections, jointPartic, cpt, region, label, resourcesDir, prefix);
                mapTable.addColumn("![Map](" + relPathToResources + "/" + prefix + ".png)");
            }
            mapTable.finalizeLine();

            lines.add(getSubHeading() + " Joint Rupture Participation Rates");
            lines.add(topLink);
            lines.add("");
            lines.add(
                    "Rate at which each section participates in joint ruptures. Only sections "
                            + "that are part of at least one joint rupture are shown. Gray "
                            + "sections are part of joint ruptures that all have a zero rate.");
            lines.add("");
            lines.addAll(mapTable.build());
            lines.add("");
        }

        // MFDs
        List<DiscretizedFunc> incrFuncs = new ArrayList<>();
        List<PlotCurveCharacterstics> chars = new ArrayList<>();
        totalMFD.setName("All ruptures");
        incrFuncs.add(totalMFD);
        chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.GRAY));
        jointMFD.setName("All joint");
        incrFuncs.add(jointMFD);
        chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.BLACK));
        for (int i = 0; i < JOINT_CATEGORIES.size(); i++) {
            IncrementalMagFreqDist mfd = mfds.get(JOINT_CATEGORIES.get(i));
            mfd.setName(JOINT_CATEGORIES.get(i));
            incrFuncs.add(mfd);
            chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, JOINT_COLORS.get(i)));
        }
        List<DiscretizedFunc> cmlFuncs = new ArrayList<>();
        for (DiscretizedFunc func : incrFuncs) {
            EvenlyDiscretizedFunc cml = ((IncrementalMagFreqDist) func).getCumRateDistWithOffset();
            cml.setName(func.getName());
            cmlFuncs.add(cml);
        }

        Range xRange =
                new Range(
                        templateMFD.getMinX() - 0.5 * templateMFD.getDelta(),
                        templateMFD.getMaxX() + 0.5 * templateMFD.getDelta());
        String incrPrefix = "joint_only_mfds";
        String cmlPrefix = "joint_only_mfds_cumulative";
        JointRuptureRatePlot.writeMFDPlot(
                incrFuncs,
                chars,
                "Joint Rupture MFD",
                "Incremental Rate (per yr)",
                xRange,
                resourcesDir,
                incrPrefix);
        JointRuptureRatePlot.writeMFDPlot(
                cmlFuncs,
                chars,
                "Joint Rupture Cumulative MFD",
                "Cumulative Rate (per yr)",
                xRange,
                resourcesDir,
                cmlPrefix);

        TableBuilder mfdTable = MarkdownUtils.tableBuilder();
        mfdTable.addLine("Incremental MFD", "Cumulative MFD");
        mfdTable.initNewLine();
        mfdTable.addColumn("![Incremental](" + relPathToResources + "/" + incrPrefix + ".png)");
        mfdTable.addColumn("![Cumulative](" + relPathToResources + "/" + cmlPrefix + ".png)");
        mfdTable.finalizeLine();

        lines.add(getSubHeading() + " Joint Rupture MFDs");
        lines.add(topLink);
        lines.add("");
        lines.addAll(mfdTable.build());
        lines.add("");

        // Statistics
        double totalRate = 0;
        double totalMoment = 0;
        for (int r = 0; r < rupSet.getNumRuptures(); r++) {
            double rate = sol.getRateForRup(r);
            totalRate += rate;
            totalMoment += rate * MagUtils.magToMoment(rupSet.getMagForRup(r));
        }

        lines.add(getSubHeading() + " Joint Rupture Statistics");
        lines.add(topLink);
        lines.add("");
        lines.add(
                "| Category | Ruptures | With Rate > 0 | Rate Sum | % of Total Rate "
                        + "| Moment Rate (N·m/yr) | % of Total Moment | Mag Range (rate > 0) "
                        + "| Sections (rate > 0) |");
        lines.add("|---|---:|---:|---:|---:|---:|---:|---|---:|");
        for (String cat : JOINT_CATEGORIES) {
            JointStats js = stats.get(cat);
            String magRange =
                    js.withRateCount > 0
                            ? String.format("%.2f – %.2f", js.minMagWithRate, js.maxMagWithRate)
                            : "-";
            lines.add(
                    String.format(
                            "| %s | %,d | %,d | %.4e | %.2f%% | %.4e | %.2f%% | %s | %,d |",
                            cat,
                            js.totalCount,
                            js.withRateCount,
                            js.rateSum,
                            totalRate > 0 ? 100.0 * js.rateSum / totalRate : 0.0,
                            js.momentRate,
                            totalMoment > 0 ? 100.0 * js.momentRate / totalMoment : 0.0,
                            magRange,
                            js.sections.size()));
        }
        return lines;
    }

    @Override
    public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
        return null;
    }
}

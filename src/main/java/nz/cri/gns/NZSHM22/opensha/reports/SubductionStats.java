package nz.cri.gns.NZSHM22.opensha.reports;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import org.apache.commons.math3.stat.StatUtils;
import org.jfree.data.Range;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.geo.Region;
import org.opensha.commons.gui.plot.*;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.DataUtils;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.commons.util.cpt.CPTVal;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupSetMapMaker;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class SubductionStats {

    String title;
    FaultSystemRupSet rupSet;
    FaultSystemRupSet crustalRupSet;
    ReportMetadata meta;
    File resourcesDir;
    String relPathToResources;
    String prefix;
    String topLink;

    List<ClusterRupture> ruptures;
    List<ClusterRupture> crustalRuptures;

    // sectionId -> rupture size -> count
    Table<Integer, Integer, Integer> subductionRupSizePerSection;
    // sectionId -> rupture count (in how many ruptures does the section participate?)
    Map<Integer, Integer> subductionRupCountPerSection;
    Histogram<Integer> subductionRuptureSizes;

    Map<Integer, Integer> jumpCountsPerSection;
    Map<Integer, Integer> crustalRupCountsPerSection;
    Map<Integer, Integer> totalCombinationsPerSection;

    Map<Integer, Integer> subRupToCrustRupCount;

    MultiRupSetDist distCalc;

    public SubductionStats(FaultSystemRupSet rupSet,
                           FaultSystemRupSet crustalRupSet,
                           ReportMetadata meta,
                           File resourcesDir,
                           String relPathToResources,
                           String prefix,
                           String topLink,
                           String title) {
        this.rupSet = rupSet;
        this.crustalRupSet = crustalRupSet;
        this.meta = meta;
        this.resourcesDir = resourcesDir;
        this.relPathToResources = relPathToResources;
        this.prefix = prefix;
        this.topLink = topLink;
        this.title = title;

        ruptures = rupSet.requireModule(ClusterRuptures.class).getAll();
        if (crustalRupSet != null) {
            crustalRuptures = crustalRupSet.requireModule(ClusterRuptures.class).getAll();
            distCalc = new MultiRupSetDist(rupSet.getFaultSectionDataList(), crustalRupSet.getFaultSectionDataList());
        }
    }

    public static class LogCPT extends CPT {

        CPT inner;

        public LogCPT(CPT inner) {
            this.inner = inner.asLog10();
            setBelowMinColor(inner.getBelowMinColor());
            setAboveMaxColor(inner.getAboveMaxColor());
            setGapColor(inner.getGapColor());
            setNanColor(inner.getNanColor());
            setBlender(inner.getBlender());

            for (CPTVal val : inner)
                add((CPTVal) val.clone());

            setPreferredTickInterval(inner.getPreferredTickInterval());
        }

        @Override
        public Color getColor(float value) {
            if (value == 0) {
                return getNanColor();
            }
            return inner.getColor((float) Math.log10(value));
        }

    }

    public static class Histogram<T extends Number> extends ArrayList<T> {

        final String title;
        final Set<Properties> properties;

        public Histogram(String title, Properties... properties) {
            super();
            this.title = title;
            this.properties = new HashSet<>(Arrays.asList(properties));
            Preconditions.checkArgument(this.properties.contains(Properties.CLAMP_TO_RANGE) || this.properties.contains(Properties.TRIM_TO_RANGE));
        }

        public Range getRange() {
            double min = Double.POSITIVE_INFINITY;
            double max = Double.NEGATIVE_INFINITY;

            for (T value : this) {
                double dValue = value.doubleValue();
                if (dValue < min) {
                    min = dValue;
                }
                if (dValue > max) {
                    max = dValue;
                }
            }

            return new Range(min, max);
        }

        enum Properties {
            TRIM_TO_RANGE,
            CLAMP_TO_RANGE,
            NORMALISE,
            X_LOG
        }

        public HistogramFunction getHistogramData(double delta) {
            return getHistogramData(getRange(), delta);
        }

        public HistogramFunction getHistogramData(Range range, double delta) {
            HistogramFunction hist = HistogramFunction.getEncompassingHistogram(range.getLowerBound(), range.getUpperBound(), delta);

            for (T value : this) {
                double dValue = value.doubleValue();

                if (properties.contains(Properties.TRIM_TO_RANGE)) {
                    if (!range.contains(dValue)) {
                        continue;
                    }
                } else if (properties.contains(Properties.CLAMP_TO_RANGE)) {
                    dValue = range.constrain(dValue);
                } else {
                    throw new RuntimeException("Expected range property");
                }

//                if (properties.contains(Properties.X_LOG)) {
//                    dValue = Math.log10(dValue);
//                }

                hist.add(hist.getClosestXIndex(dValue), 1);
            }

            return hist;
        }

        public List<String> addStats() {
            MarkdownUtils.TableBuilder table = MarkdownUtils.tableBuilder();
            double[] values = new double[size()];
            double total = 0;
            for (int i = 0; i < values.length; i++) {
                values[i] = get(i).doubleValue();
                total = total + get(i).doubleValue();
            }
            values = Arrays.stream(values).sorted().toArray();
            table.addLine("Property", "Value");
            table.addLine("percentile 25 ", StatUtils.percentile(values, 25));
            table.addLine("percentile 50 ", StatUtils.percentile(values, 50));
            table.addLine("percentile 75 ", StatUtils.percentile(values, 75));
            table.addLine("total", total);
            table.addLine("mean ", StatUtils.mean(values));
            return table.build();
        }

        public String plot(double delta, String title, String xAxisLabel, String yAxisLabel, File outputDir, String prefix, String relPathToResources) throws IOException {
            List<XY_DataSet> funcs = new ArrayList<>();
            HistogramFunction histogramData = getHistogramData(delta);
            Range xRange = new Range(Math.max(histogramData.getMinX() - delta, 0), histogramData.getMaxX() + delta);
            Range yRange = new Range(histogramData.getMinY(), histogramData.getMaxY());

            funcs.add(histogramData);

            List<PlotCurveCharacterstics> chars = new ArrayList<>();
            chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, Color.BLACK));

            PlotSpec spec = new PlotSpec(funcs, chars, title, xAxisLabel, yAxisLabel);

            HeadlessGraphPanel gp = PlotUtils.initHeadless();

            gp.drawGraphPanel(spec, properties.contains(Properties.X_LOG), false, xRange, yRange);

            PlotUtils.writePlots(outputDir, prefix, gp, 800, 550, true, false, false);

            return "![" + title + "](" + relPathToResources + "/" + prefix + ".png)";
        }

    }

    public List<String> plotRupCountMap(FaultSystemRupSet rupSet, Map<Integer, Integer> rupCountPerSection, File outputDir, String relPathToResources) throws IOException {
        Region reg = GeographicMapMaker.buildBufferedRegion(rupSet.getFaultSectionDataList());
        List<Double> values = new ArrayList<>();
        DataUtils.MinMaxAveTracker minMax = new DataUtils.MinMaxAveTracker();
        for (int s = 0; s < rupSet.getNumSections(); s++) {
            Integer value = rupCountPerSection.get(s);
            if (value == null) {
                values.add(Double.NEGATIVE_INFINITY);
            } else {
                double v = (double) value;
                values.add(v);
                minMax.addValue(v);
            }
        }

        CPT cpt = GMT_CPT_Files.RAINBOW_UNIFORM.instance();
        cpt = cpt.rescale(minMax.getMin(), minMax.getMax() + 1);
        cpt.setPreferredTickInterval((minMax.getMax() - minMax.getMin()) / 4.0);

        GeographicMapMaker plotter = new RupSetMapMaker(rupSet, reg);
        plotter.setWritePDFs(false);
        plotter.setWriteGeoJSON(false);
        plotter.plotSectScalars(values, cpt, "Rupture Participation Count");
        plotter.plot(outputDir, prefix + "_thinning_participation", "Rupture Participation Count");

        cpt = new LogCPT(cpt);
        plotter.plotSectScalars(values, cpt, "Rupture Participation Count");
        plotter.plot(outputDir, prefix + "_thinning_participationlog", "Rupture Participation Count (Log)");

        MarkdownUtils.TableBuilder table = MarkdownUtils.tableBuilder();
        table.addLine("Rupture Participation Count", "Rupture Participation Count (Log)");
        table.addLine("![Rupture Participation Count](" + relPathToResources + "/" + prefix + "_thinning_participation.png)",
                "![Rupture Participation Count](" + relPathToResources + "/" + prefix + "_thinning_participationlog.png)");
        return table.build();
    }

    static class MultiRupSetFaultSection extends FaultSectionPrefData {

        static Map<FaultSection, MultiRupSetFaultSection> mapping = new HashMap<>();
        int id;

        public static MultiRupSetFaultSection fromFaultSection(FaultSection original) {
            int id = mapping.size();
            MultiRupSetFaultSection result = new MultiRupSetFaultSection();
            result.id = id;
            result.setFaultSectionPrefData(original);
            mapping.put(original, result);
            return result;
        }

        @Override
        public int getSectionId() {
            return id;
        }

        public static MultiRupSetFaultSection getMultiRupSetFaultSection(FaultSection original) {
            return mapping.get(original);
        }

    }

    static class MultiRupSetDist {
        SectionDistanceAzimuthCalculator distAzCalc;

        public MultiRupSetDist(List<? extends FaultSection>... sections) {
            List<FaultSection> s = new ArrayList<>();
            for (List<? extends FaultSection> sectionsLIst : sections) {
                for (FaultSection section : sectionsLIst) {
                    FaultSection multiSection = MultiRupSetFaultSection.fromFaultSection(section);
                    s.add(multiSection);
                }
            }
            distAzCalc = new SectionDistanceAzimuthCalculator(s);
        }

        public double getDistance(FaultSection a, FaultSection b) {
            FaultSection multiA = MultiRupSetFaultSection.getMultiRupSetFaultSection(a);
            FaultSection multiB = MultiRupSetFaultSection.getMultiRupSetFaultSection(b);
            return distAzCalc.getDistance(multiA, multiB);
        }
    }


    protected void generateRupJumps() {

        System.out.println("generateRupJumps");

        // build map from subduction section to crustal rupture ids
        Map<Integer, Set<Integer>> subSecToRups = new ConcurrentHashMap<>();
        IntStream.range(0, crustalRuptures.size()).parallel().forEach(cr -> {
            List<FaultSection> crustalSecs = crustalRuptures.get(cr).buildOrderedSectionList();
            for (FaultSection crustSec : crustalSecs) {
                for (FaultSection subSec : rupSet.getFaultSectionDataList()) {
                    if (distCalc.getDistance(subSec, crustSec) <= 5) {
                        subSecToRups.compute(subSec.getSectionId(), (id, rups) -> {
                            rups = rups == null ? new HashSet<>() : rups;
                            rups.add(cr);
                            return rups;
                        });
                    }
                }
            }
        });

        System.out.println("subRupToCrustRupCount");

        subRupToCrustRupCount = new ConcurrentHashMap<>();
        IntStream.range(0, ruptures.size()).parallel().forEach(r -> {
            Set<Integer> rupIds = new HashSet<>();
            for (Integer s : rupSet.getSectionsIndicesForRup(r)) {
                Set<Integer> rups = subSecToRups.get(s);
                if (rups != null) {
                    rupIds.addAll(rups);
                }
            }
            if (!rupIds.isEmpty()) {
                subRupToCrustRupCount.put(r, rupIds.size());
            }
        });

        System.out.println("done");

    }

    protected void gatherStats() {
        subductionRupSizePerSection = HashBasedTable.create();
        subductionRupCountPerSection = new HashMap<>();
        subductionRuptureSizes = new Histogram<>(title, Histogram.Properties.TRIM_TO_RANGE);

        for (ClusterRupture rupture : ruptures) {
            List<FaultSection> sections = rupture.buildOrderedSectionList();
            int size = sections.size();
            subductionRuptureSizes.add(size);
            for (FaultSection section : sections) {
                Integer oldCount = subductionRupSizePerSection.get(section.getSectionId(), size);
                subductionRupSizePerSection.put(section.getSectionId(), size, oldCount == null ? 1 : oldCount + 1);
                subductionRupCountPerSection.compute(section.getSectionId(), (s, count) -> count == null ? 1 : count + 1);
            }
        }

        if (crustalRupSet == null) {
            return;
        }

        Map<Integer, Integer> crustalSectionToRupCount = new HashMap<>();
        for (int r = 0; r < crustalRupSet.getNumRuptures(); r++) {
            for (FaultSection section : crustalRupSet.getFaultSectionDataForRupture(r)) {
                crustalSectionToRupCount.compute(section.getSectionId(), (s, count) -> count == null ? 1 : count + 1);
            }
        }

        jumpCountsPerSection = new ConcurrentHashMap<>();
        crustalRupCountsPerSection = new ConcurrentHashMap<>();
        totalCombinationsPerSection = new ConcurrentHashMap<>();

        rupSet.getFaultSectionDataList().parallelStream().forEach(subductionSection -> {
            int count = 0;
            int rupCount = 0;
            for (FaultSection crustalSection : crustalRupSet.getFaultSectionDataList()) {
                if (distCalc.getDistance(subductionSection, crustalSection) <= 5) {
                    count++;
                    rupCount += crustalSectionToRupCount.get(crustalSection.getSectionId());
                }
            }
            if (count > 0) {
                jumpCountsPerSection.put(subductionSection.getSectionId(), count);
            }
            if (rupCount > 0) {
                crustalRupCountsPerSection.put(subductionSection.getSectionId(), rupCount);
                totalCombinationsPerSection.put(subductionSection.getSectionId(), rupCount * subductionRupCountPerSection.get(subductionSection.getSectionId()));
            }
        });

         generateRupJumps();
    }

    public List<String> generateReport() throws IOException {
        gatherStats();

        List<String> lines = new ArrayList<>();

        lines.add("These plots show useful data for assessing different thinning options for joint ruptures.");

        lines.add(subductionRuptureSizes.plot(10, "Rupture Size Count", "Section Count", "Rupture Count", resourcesDir, prefix + "thinning_rupSizes", relPathToResources));
        lines.add("");
        lines.add("The distribution of rupture sizes measured in sections.");
        lines.add("");
        lines.addAll(subductionRuptureSizes.addStats());

        Histogram<Integer> rupCountPerSectionHistogram = new Histogram<>(title, Histogram.Properties.CLAMP_TO_RANGE);
        rupCountPerSectionHistogram.addAll(subductionRupCountPerSection.values());
        lines.add(rupCountPerSectionHistogram.plot(100, "Ruptures per Section", "Rupture Count", "Section Count", resourcesDir, prefix + "thinning_rupCountPerSection", relPathToResources));
        lines.add("");
        lines.add("The distribution of the number of ruptures a section is part of.");
        lines.add("");
        lines.addAll(rupCountPerSectionHistogram.addStats());
        lines.add("");

        lines.add("");
        lines.addAll(plotRupCountMap(rupSet, subductionRupCountPerSection, resourcesDir, relPathToResources));
        lines.add("");
        lines.add("The same data as the previous histogram, plotted on a map.");
        lines.add("");

        if (jumpCountsPerSection != null) {
            Histogram<Integer> jumpCounts = new Histogram<>("jump counts", Histogram.Properties.CLAMP_TO_RANGE);
            jumpCounts.addAll(jumpCountsPerSection.values());
            lines.add(jumpCounts.plot(1, "Jumps per Section", "Jumps", "Section Count", resourcesDir, prefix + "thinning_jumpCounts", relPathToResources));
            lines.add("");
            lines.add("Distribution of jumps to crustal from each subduction section. A jump exists when a crustal section is within 5km of the subduction section.");
            lines.add("");
            lines.addAll(jumpCounts.addStats());
            lines.add("");
        }

        if (crustalRupCountsPerSection != null) {
            Histogram<Integer> rupCounts = new Histogram<>("rupture counts", Histogram.Properties.CLAMP_TO_RANGE);
            rupCounts.addAll(crustalRupCountsPerSection.values());
            lines.add(rupCounts.plot(10000, "Crustal Ruptures per Subduction Section", "Crustal Ruptures", "Subduction Section Count", resourcesDir, prefix + "thinning_rupCounts", relPathToResources));
            lines.add("");
            lines.add("Distribution of jumps to crustal ruptures from each subduction section. A jump exists when a crustal rupture is within 5km of the subduction section.");
            lines.add("");
            lines.addAll(rupCounts.addStats());
            lines.add("");
        }

        if (totalCombinationsPerSection != null) {
            Histogram<Integer> combinationCounts = new Histogram<>("total combinations", Histogram.Properties.CLAMP_TO_RANGE);
            combinationCounts.addAll(totalCombinationsPerSection.values());
            lines.add(combinationCounts.plot(100000000, "Potential Joint Ruptures Per Subduction Section", "Joint Ruptures", "Subduction Section Count", resourcesDir, prefix + "thinning_jointCounts", relPathToResources));
            lines.add("");
            lines.add("Distribution of joint ruptures for each subduction section. This plot assumes that the jump sections matter and will include multiple subduction/crustal rupture combinations with different jumps.");
            lines.add("");
            lines.addAll(combinationCounts.addStats());
            lines.add("");
        }
        if (subRupToCrustRupCount != null) {
            Histogram<Integer> combinationCounts = new Histogram<>("rupture combinations", Histogram.Properties.CLAMP_TO_RANGE);
            combinationCounts.addAll(subRupToCrustRupCount.values());
            lines.add(combinationCounts.plot(10000, "Potential Joint Ruptures Per Subduction Rupture", "Joint Ruptures", "Subduction Rupture Count", resourcesDir, prefix + "thinning_jointRuptureCounts", relPathToResources));
            lines.add("");
            lines.add("Distribution of joint ruptures for each subduction rupture. This plot assumes that the jump sections do not matter and only counts possible subduction/crustal joint ruptures with no duplicates.");
            lines.add("");
            lines.addAll(combinationCounts.addStats());
            lines.add("");
        }
        return lines;
    }

    public static void main(String[] args) throws IOException {
        FaultSystemRupSet rupSet = FaultSystemRupSet.load(new File("C:\\Users\\user\\Downloads\\RupSet_Sub_FM(SBD_0_3_HKR_LR_30)_mnSbS(2)_mnSSPP(2)_mxSSL(0.5)_ddAsRa(2.0,5.0,5)_ddMnFl(0.1)_ddPsCo(0.0)_ddSzCo(0.0)_thFc(0.0).zip"));
        // jumpStats(rupSet, new File("/tmp/rupCartoons"), "jumpstats", "");


//        FaultSystemRupSet rupSet = FaultSystemRupSet.load(new File("C:\\Users\\user\\Downloads\\RupSet_Sub_FM(SBD_0_3_HKR_LR_30)_mnSbS(2)_mnSSPP(2)_mxSSL(0.5)_ddAsRa(2.0,5.0,5)_ddMnFl(0.1)_ddPsCo(0.0)_ddSzCo(0.0)_thFc(0.0).zip"));
//        ClusterRuptures cRups = rupSet.requireModule(ClusterRuptures.class);
//
//        SubductionStats stats = new SubductionStats("Hikurangi");
//        stats.collectRuptureStats(cRups.getAll(), new File("/tmp/rupcartoons"), "hikurangi", "");
//
//        rupSet = FaultSystemRupSet.load(new File("C:\\Users\\user\\Downloads\\NZSHM22_RuptureSet-UnVwdHVyZUdlbmVyYXRpb25UYXNrOjEwMDAzOA==(1).zip"));
//        cRups = rupSet.requireModule(ClusterRuptures.class);
//        stats = new SubductionStats("Crustal");
//        stats.collectRuptureStats(cRups.getAll(), new File("/tmp/rupcartoons"), "crustal", "");
    }
}

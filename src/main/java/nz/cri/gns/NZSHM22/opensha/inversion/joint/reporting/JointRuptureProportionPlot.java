package nz.cri.gns.NZSHM22.opensha.inversion.joint.reporting;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.*;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.ConfigModule;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionPredicate;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.constraints.FilteredFaultSystemRupSet;
import nz.cri.gns.NZSHM22.opensha.ruptures.FaultSectionProperties;
import org.jfree.data.Range;
import org.opensha.commons.gui.plot.*;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractRupSetPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.SolMFDPlot;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

/**
 * Report plot showing, per magnitude bin, the solution rate split into joint ruptures (ruptures
 * spanning more than one partition) and non-joint ruptures as stacked bars on a linear axis, so
 * that each bar's height is the total rate of the bin. One plot is made for the whole solution, and
 * one per partition present in the solution. Partition plots use partition magnitudes, see {@link
 * PartitionPlotWrapper#partitionSolution}.
 */
public class JointRuptureProportionPlot extends AbstractRupSetPlot {

    /** Partitions that a per-partition plot is made for if they are present. */
    protected static final List<PartitionPredicate> PARTITIONS =
            List.of(
                    PartitionPredicate.CRUSTAL,
                    PartitionPredicate.HIKURANGI,
                    PartitionPredicate.PUYSEGUR);

    protected static final Color JOINT_COLOR = Color.MAGENTA.darker();
    protected static final Color NON_JOINT_COLOR = Color.LIGHT_GRAY;

    protected static final String PREFIX = "joint_proportion_mfd";

    /** Lower bound of the magnitude axis. */
    protected static final double MIN_MAG = 6.5;

    @Override
    public String getName() {
        return "Joint Rupture Proportion";
    }

    /**
     * Bins rates by magnitude and returns the joint and non-joint rate per bin. Rates in a bin sum
     * to the total rate of the bin. Ruptures with rate <= 0 are ignored.
     *
     * @param mags rupture magnitudes
     * @param rates rupture rates
     * @param isJoint whether each rupture is a joint rupture
     * @param template the magnitude bins
     * @return the joint rate MFD at index 0, the non-joint rate MFD at index 1
     */
    public static IncrementalMagFreqDist[] jointRates(
            double[] mags, double[] rates, boolean[] isJoint, IncrementalMagFreqDist template) {
        IncrementalMagFreqDist joint =
                new IncrementalMagFreqDist(
                        template.getMinX(), template.size(), template.getDelta());
        IncrementalMagFreqDist nonJoint =
                new IncrementalMagFreqDist(
                        template.getMinX(), template.size(), template.getDelta());
        for (int r = 0; r < mags.length; r++) {
            if (rates[r] <= 0) {
                continue;
            }
            int bin = template.getClosestXIndex(mags[r]);
            IncrementalMagFreqDist target = isJoint[r] ? joint : nonJoint;
            target.add(bin, rates[r]);
        }
        joint.setName("Joint");
        nonJoint.setName("Non-joint");
        return new IncrementalMagFreqDist[] {joint, nonJoint};
    }

    /**
     * Whether each rupture of the full rupture set spans more than one partition.
     *
     * @param rupSet the rupture set
     * @return per rupture, whether it is a joint rupture
     */
    protected static boolean[] jointRuptures(FaultSystemRupSet rupSet) {
        boolean[] result = new boolean[rupSet.getNumRuptures()];
        for (int r = 0; r < result.length; r++) {
            result[r] = JointRuptureRatePlot.partitionsForRup(rupSet, r).size() > 1;
        }
        return result;
    }

    /**
     * Whether each rupture of a partition rupture set was a joint rupture in the original rupture
     * set, i.e. whether it lost sections when it was filtered down to the partition.
     *
     * @param filtered the partition rupture set
     * @param original the rupture set it was filtered from
     * @return per filtered rupture, whether it is a joint rupture
     */
    protected static boolean[] jointRuptures(
            FilteredFaultSystemRupSet filtered, FaultSystemRupSet original) {
        boolean[] result = new boolean[filtered.getNumRuptures()];
        for (int r = 0; r < result.length; r++) {
            int oldId = filtered.getOldRuptureId(r);
            result[r] =
                    filtered.getSectionsIndicesForRup(r).size()
                            < original.getSectionsIndicesForRup(oldId).size();
        }
        return result;
    }

    /**
     * The partitions of {@link #PARTITIONS} that sections of the rupture set belong to.
     *
     * @param rupSet the rupture set
     * @return the partitions present, in {@link #PARTITIONS} order
     */
    protected static List<PartitionPredicate> presentPartitions(FaultSystemRupSet rupSet) {
        Set<PartitionPredicate> present = EnumSet.noneOf(PartitionPredicate.class);
        for (FaultSection section : rupSet.getFaultSectionDataList()) {
            PartitionPredicate partition = FaultSectionProperties.getPartition(section);
            if (partition != null) {
                present.add(partition);
            }
        }
        List<PartitionPredicate> result = new ArrayList<>();
        for (PartitionPredicate partition : PARTITIONS) {
            if (present.contains(partition)) {
                result.add(partition);
            }
        }
        return result;
    }

    /**
     * The total rate per bin.
     *
     * @param rates output of {@link #jointRates}
     * @return joint plus non-joint rate per bin
     */
    protected static IncrementalMagFreqDist total(IncrementalMagFreqDist[] rates) {
        IncrementalMagFreqDist joint = rates[0];
        IncrementalMagFreqDist nonJoint = rates[1];
        IncrementalMagFreqDist total =
                new IncrementalMagFreqDist(joint.getMinX(), joint.size(), joint.getDelta());
        for (int b = 0; b < joint.size(); b++) {
            total.set(b, joint.getY(b) + nonJoint.getY(b));
        }
        return total;
    }

    /**
     * The highest total bin rate among the bins visible above {@link #MIN_MAG}.
     *
     * @param rates output of {@link #jointRates}
     * @return the maximum visible total rate, 0 if there is none
     */
    public static double maxVisibleRate(IncrementalMagFreqDist[] rates) {
        IncrementalMagFreqDist total = total(rates);
        double maxRate = 0;
        for (int b = 0; b < total.size(); b++) {
            if (total.getX(b) + 0.5 * total.getDelta() > MIN_MAG) {
                maxRate = Math.max(maxRate, total.getY(b));
            }
        }
        return maxRate;
    }

    /**
     * Writes a proportion plot. The stack is drawn as the total bar (the total rate of the bin) in
     * the joint colour, overlaid by the non-joint bar, so that non-joint is at the bottom and joint
     * on top. OpenSHA only stacks series that share the same plot characteristics.
     *
     * @param rates output of {@link #jointRates}
     * @param title plot title
     * @param resourcesDir output directory
     * @param prefix file name prefix
     * @param maxRate upper end of the y axis before padding, see {@link #maxVisibleRate}
     * @throws IOException if writing fails
     */
    protected static void writePlot(
            IncrementalMagFreqDist[] rates,
            String title,
            File resourcesDir,
            String prefix,
            double maxRate)
            throws IOException {
        IncrementalMagFreqDist joint = rates[0];
        IncrementalMagFreqDist nonJoint = rates[1];
        IncrementalMagFreqDist total = total(rates);
        total.setName(joint.getName());

        List<IncrementalMagFreqDist> funcs = List.of(total, nonJoint);
        List<PlotCurveCharacterstics> chars =
                List.of(
                        new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, JOINT_COLOR),
                        new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, NON_JOINT_COLOR));

        PlotSpec spec = new PlotSpec(funcs, chars, title, "Magnitude", "Incremental Rate (per yr)");
        spec.setLegendInset(true);

        Range xRange = new Range(MIN_MAG, joint.getMaxX() + 0.5 * joint.getDelta());

        HeadlessGraphPanel gp = PlotUtils.initHeadless(PlotPreferences.getDefaultAppPrefs());
        gp.setTickLabelFontSize(20);
        Range yRange = maxRate > 0 ? new Range(0, maxRate * 1.05) : new Range(0, 1);
        gp.drawGraphPanel(spec, false, false, xRange, yRange);

        PlotUtils.writePlots(resourcesDir, prefix, gp, 1000, 850, true, true, false);
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

        IncrementalMagFreqDist template =
                SolMFDPlot.initDefaultMFD(rupSet.getMinMag(), rupSet.getMaxMag());

        List<String> names = new ArrayList<>();
        List<IncrementalMagFreqDist[]> allRates = new ArrayList<>();
        names.add("All Partitions");
        allRates.add(
                jointRates(
                        rupSet.getMagForAllRups(),
                        sol.getRateForAllRups(),
                        jointRuptures(rupSet),
                        template));

        // partition magnitudes are calculated with the scaling relationship of the ConfigModule
        if (sol.hasModule(ConfigModule.class)) {
            for (PartitionPredicate partition : presentPartitions(rupSet)) {
                FaultSystemSolution partitionSol =
                        PartitionPlotWrapper.partitionSolution(sol, partition, null);
                FilteredFaultSystemRupSet partitionRupSet =
                        (FilteredFaultSystemRupSet) partitionSol.getRupSet();
                names.add(partition.name());
                allRates.add(
                        jointRates(
                                partitionRupSet.getMagForAllRups(),
                                partitionSol.getRateForAllRups(),
                                jointRuptures(partitionRupSet, rupSet),
                                template));
            }
        }

        double sharedMax = 0;
        for (IncrementalMagFreqDist[] rates : allRates) {
            sharedMax = Math.max(sharedMax, maxVisibleRate(rates));
        }

        TableBuilder table = MarkdownUtils.tableBuilder();
        TableBuilder sharedTable = MarkdownUtils.tableBuilder();
        table.initNewLine();
        sharedTable.initNewLine();
        for (String name : names) {
            table.addColumn(name);
            sharedTable.addColumn(name);
        }
        table.finalizeLine();
        sharedTable.finalizeLine();

        table.initNewLine();
        sharedTable.initNewLine();
        for (int i = 0; i < names.size(); i++) {
            IncrementalMagFreqDist[] rates = allRates.get(i);
            String title = "Joint Rupture Proportion";
            String prefix = PREFIX;
            if (i > 0) {
                title += " " + names.get(i);
                prefix += "_" + names.get(i);
            }
            String sharedPrefix = prefix + "_shared_y";
            writePlot(rates, title, resourcesDir, prefix, maxVisibleRate(rates));
            writePlot(rates, title, resourcesDir, sharedPrefix, sharedMax);
            table.addColumn(imageLink(relPathToResources, prefix));
            sharedTable.addColumn(imageLink(relPathToResources, sharedPrefix));
        }
        table.finalizeLine();
        sharedTable.finalizeLine();

        List<String> lines = new ArrayList<>();
        lines.add(
                "Solution rate per magnitude bin, split into joint and non-joint ruptures."
                        + " Partition plots use partition magnitudes.");
        lines.add("");
        lines.addAll(table.wrap(2, 0).build());
        lines.add("");
        lines.add("Same plots with a y axis shared across all plots.");
        lines.add("");
        lines.addAll(sharedTable.wrap(2, 0).build());
        return lines;
    }

    /**
     * Markdown image link for a plot.
     *
     * @param relPathToResources relative path to the resources directory
     * @param prefix file name prefix of the plot
     * @return the markdown image link
     */
    protected static String imageLink(String relPathToResources, String prefix) {
        return "![" + prefix + "](" + relPathToResources + "/" + prefix + ".png)";
    }

    @Override
    public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
        return null;
    }
}

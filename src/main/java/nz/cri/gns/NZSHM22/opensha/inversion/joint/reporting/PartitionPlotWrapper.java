package nz.cri.gns.NZSHM22.opensha.inversion.joint.reporting;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.IntPredicate;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.ConfigModule;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionMfds;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionPredicate;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.constraints.FilteredFaultSystemRupSet;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.scaling.JointScalingRelationship;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.RupSetScalingRelationship;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionTargetMFDs;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractRupSetPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportPageGen.PlotLevel;
import org.opensha.sha.earthquake.faultSysSolution.reports.RupSetMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.SolidFillPlot;

/**
 * A plot wrapper that executes a plot for each partition in a rupture set, by creating a filtered
 * solution for each partition and adding the appropriate target MFDs module. This allows us to
 * reuse existing plotting code to create partition-specific plots without having to add
 * partitioning logic to the plots themselves.
 *
 * <p>Acts as a simple pass-through if the rupture set doesn't have a PartitionMfds module.
 *
 * <p>All configuration that {@link
 * org.opensha.sha.earthquake.faultSysSolution.reports.ReportPageGen} applies to a plot (plot level,
 * thread count, sub heading, solid fill) is forwarded to the wrapped plot, so that the wrapper
 * behaves like the plot it wraps.
 */
public class PartitionPlotWrapper extends AbstractRupSetPlot implements SolidFillPlot {

    final AbstractRupSetPlot inner;

    /**
     * Wraps a plot so that it is run once for the whole solution and once per partition.
     *
     * @param inner the plot to wrap
     */
    public PartitionPlotWrapper(AbstractRupSetPlot inner) {
        this.inner = inner;
    }

    /** Returns the plot that this wrapper delegates to. */
    public AbstractRupSetPlot getInner() {
        return inner;
    }

    @Override
    public void setPlotLevel(PlotLevel plotLevel) {
        super.setPlotLevel(plotLevel);
        inner.setPlotLevel(plotLevel);
    }

    @Override
    public void setNumThreads(int numThreads) {
        super.setNumThreads(numThreads);
        inner.setNumThreads(numThreads);
    }

    @Override
    public void setSubHeading(String subHeading) {
        super.setSubHeading(subHeading);
        inner.setSubHeading(subHeading);
    }

    @Override
    public void setFillSurfaces(boolean fillSurfaces) {
        if (inner instanceof SolidFillPlot) {
            ((SolidFillPlot) inner).setFillSurfaces(fillSurfaces);
        }
    }

    @Override
    public List<String> getSummary(
            ReportMetadata meta, File resourcesDir, String relPathToResources, String topLink) {
        return inner.getSummary(meta, resourcesDir, relPathToResources, topLink);
    }

    /**
     * Creates a solution for a single partition by filtering the rupture set and solution down to
     * the sections of that partition, and adding the partition's target MFDs if they are available.
     *
     * @param sol the full joint solution
     * @param partitionPredicate the partition to filter for
     * @param partitionMfds the target MFDs per partition, may be null
     * @return a solution containing only the sections and ruptures of the partition
     */
    public FaultSystemSolution partitionSolution(
            FaultSystemSolution sol,
            PartitionPredicate partitionPredicate,
            PartitionMfds partitionMfds) {

        ConfigModule config = sol.getModule(ConfigModule.class);
        config.getConfig().hydrateScalingRelationship();
        JointScalingRelationship scalingRelationship = config.getConfig().scalingRelationship;

        IntPredicate intPredicate = partitionPredicate.getPredicate(sol.getRupSet());
        RupSetScalingRelationship rupSetScalingRelationship =
                scalingRelationship.toRupSetScalingRelationship(partitionPredicate.isCrustal());
        FaultSystemSolution result =
                FilteredFaultSystemRupSet.forIntPredicate(
                        sol, intPredicate, rupSetScalingRelationship);

        // plots that don't deal with MFDs (such as slip rate plots) work without target MFDs
        InversionTargetMFDs targetMFDs =
                partitionMfds == null ? null : partitionMfds.get(partitionPredicate);
        if (targetMFDs != null) {
            result.getRupSet().addModule(targetMFDs);
        }

        return result;
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

        List<String> innerResult =
                inner.plot(rupSet, sol, meta, resourcesDir, relPathToResources, topLink);

        PartitionMfds partitionMfds = rupSet.getModule(PartitionMfds.class);

        if (partitionMfds == null || innerResult == null) {
            return innerResult;
        }

        List<String> result = new ArrayList<>();

        result.add(getSubHeading() + " " + "All Partitions Combined");
        result.addAll(innerResult);

        for (PartitionPredicate partitionPredicate :
                partitionMfds.mfds.keySet().stream().sorted().toList()) {

            FaultSystemSolution filteredInversionSolution =
                    partitionSolution(sol, partitionPredicate, partitionMfds);

            RupSetMetadata solMeta =
                    new RupSetMetadata(meta.primary.name, filteredInversionSolution);
            ReportMetadata filteredMeta = null;

            if (meta.hasComparison() && meta.comparison.sol != null) {
                PartitionMfds compPartitionMfds =
                        meta.comparison.rupSet.getModule(PartitionMfds.class);
                FaultSystemSolution filteredComparisonSolution =
                        partitionSolution(
                                meta.comparison.sol, partitionPredicate, compPartitionMfds);
                RupSetMetadata compMeta =
                        new RupSetMetadata(meta.comparison.name, filteredComparisonSolution);
                filteredMeta = new ReportMetadata(solMeta, compMeta);
            } else {
                filteredMeta = new ReportMetadata(solMeta);
            }

            //  create a new resources folder so that MFDs don't overwrite each other
            String partitionRelPathToResources =
                    relPathToResources + "/" + partitionPredicate.name();
            File partitionResourcesDir = new File(resourcesDir, partitionPredicate.name());
            partitionResourcesDir.mkdirs();

            // create the inner plot for this partition
            result.add(getSubHeading() + " " + partitionPredicate.name());
            setSubHeading(getSubHeading() + "#");
            result.addAll(
                    inner.plot(
                            filteredInversionSolution.getRupSet(),
                            filteredInversionSolution,
                            filteredMeta,
                            partitionResourcesDir,
                            partitionRelPathToResources,
                            topLink));
            setSubHeading(getSubHeading().substring(0, getSubHeading().length() - 1));
        }

        return result;
    }

    @Override
    public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
        return inner.getRequiredModules();
    }

    @Override
    public String getName() {
        return inner.getName() + " Split by Partition";
    }
}

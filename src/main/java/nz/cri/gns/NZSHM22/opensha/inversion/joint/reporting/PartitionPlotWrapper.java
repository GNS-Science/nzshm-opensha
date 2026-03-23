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
import org.opensha.sha.earthquake.faultSysSolution.reports.RupSetMetadata;

/**
 * A plot wrapper that executes a plot for each partition in a rupture set, by creating a filtered
 * solution for each partition and adding the appropriate target MFDs module. This allows us to
 * reuse existing plotting code to create partition-specific plots without having to add
 * partitioning logic to the plots themselves.
 *
 * <p>Acts as a simple pass-through if the rupture set doesn't have a PartitionMfds module.
 */
public class PartitionPlotWrapper extends AbstractRupSetPlot {

    final AbstractRupSetPlot inner;

    public PartitionPlotWrapper(AbstractRupSetPlot inner) {
        this.inner = inner;
    }

    // create a solution for this partition by filtering the rupture set and solution, and
    // adding the appropriate target MFDs module
    public FaultSystemSolution partitionSolution(
            FaultSystemSolution sol,
            PartitionPredicate partitionPredicate,
            PartitionMfds partitionMfds) {

        ConfigModule config = sol.getModule(ConfigModule.class);
        config.getConfig().hydrateScalingRelationship();
        JointScalingRelationship scalingRelationship = config.getConfig().scalingRelationship;

        InversionTargetMFDs targetMFDs = partitionMfds.get(partitionPredicate);
        IntPredicate intPredicate = partitionPredicate.getPredicate(sol.getRupSet());
        RupSetScalingRelationship rupSetScalingRelationship =
                scalingRelationship.toRupSetScalingRelationship(partitionPredicate.isCrustal());
        FaultSystemSolution result =
                FilteredFaultSystemRupSet.forIntPredicate(
                        sol, intPredicate, rupSetScalingRelationship);
        result.getRupSet().addModule(targetMFDs);

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

        PartitionMfds partitionMfds = rupSet.getModule(PartitionMfds.class);

        List<String> result = new ArrayList<>();

        if (partitionMfds != null) {
            result.add(getSubHeading() + " " + "All Partitions Combined");
        }

        Collection<String> innerResult =
                inner.plot(rupSet, sol, meta, resourcesDir, relPathToResources, topLink);

        if (innerResult == null) {
            return result;
        }

        result.addAll(innerResult);

        for (PartitionPredicate partitionPredicate :
                partitionMfds.mfds.keySet().stream().sorted().toList()) {

            FaultSystemSolution filteredInversionSolution =
                    partitionSolution(sol, partitionPredicate, partitionMfds);

            RupSetMetadata solMeta =
                    new RupSetMetadata(meta.primary.name, filteredInversionSolution);
            ReportMetadata filteredMeta = null;

            if (meta.hasComparison()) {
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

            // create MFD plot for this partition
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

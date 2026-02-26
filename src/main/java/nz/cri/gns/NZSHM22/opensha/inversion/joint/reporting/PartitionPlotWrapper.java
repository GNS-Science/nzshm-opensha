package nz.cri.gns.NZSHM22.opensha.inversion.joint.reporting;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;
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

/**
 * A plot wrapper that executes a plot for each partition in a rupture set, by creating a filtered
 * solution for each partition and adding the appropriate target MFDs module. This allows us to
 * reuse existing plotting code to create partition-specific plots without having to add
 * partitioning logic to the plots themselves.
 *
 * Acts as a simple pass-through if the rupture set doesn't have a PartitionMfds module.
 */
public class PartitionPlotWrapper extends AbstractRupSetPlot {

    final AbstractRupSetPlot inner;

    public PartitionPlotWrapper(AbstractRupSetPlot inner) {
        this.inner = inner;
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
        // if we don't have our special MFDs, just do the normal plot
        if (partitionMfds == null) {
            return inner.plot(rupSet, sol, meta, resourcesDir, relPathToResources, topLink);
        }

        ConfigModule config = sol.getModule(ConfigModule.class);
        config.getConfig().hydrateScalingRelationship();
        JointScalingRelationship scalingRelationship = config.getConfig().scalingRelationship;

        List<String> result = new ArrayList<>();

        for (PartitionPredicate partitionPredicate :
                partitionMfds.mfds.keySet().stream().sorted().collect(Collectors.toList())) {

            // create a solution for this partition by filtering the rupture set and solution, and
            // adding the appropriate target MFDs module
            InversionTargetMFDs targetMFDs = partitionMfds.get(partitionPredicate);
            IntPredicate intPredicate = partitionPredicate.getPredicate(rupSet);
            RupSetScalingRelationship rupSetScalingRelationship =
                    scalingRelationship.toRupSetScalingRelationship(partitionPredicate.isCrustal());
            FaultSystemSolution filteredInversionSolution =
                    FilteredFaultSystemRupSet.forIntPredicate(
                            sol, intPredicate, rupSetScalingRelationship);
            filteredInversionSolution.getRupSet().addModule(targetMFDs);

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
                            meta,
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

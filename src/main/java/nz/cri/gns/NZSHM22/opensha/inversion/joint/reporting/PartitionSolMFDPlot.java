package nz.cri.gns.NZSHM22.opensha.inversion.joint.reporting;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.ConfigModule;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionMfds;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionPredicate;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.constraints.FilteredFaultSystemRupSet;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.scaling.JointScalingRelationship;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.RupSetScalingRelationship;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionTargetMFDs;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.SolMFDPlot;

public class PartitionSolMFDPlot extends SolMFDPlot {

    public PartitionSolMFDPlot() {
        super();
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
        if (partitionMfds == null) {
            return super.plot(rupSet, sol, meta, resourcesDir, relPathToResources, topLink);
        }

        ConfigModule config = sol.getModule(ConfigModule.class);
        config.getConfig().hydrateScalingRelationship();
        JointScalingRelationship scalingRelationship = config.getConfig().scalingRelationship;

        List<String> result = new ArrayList<>();

        for (PartitionPredicate partitionPredicate :
                partitionMfds.mfds.keySet().stream().sorted().collect(Collectors.toList())) {

            InversionTargetMFDs targetMFDs = partitionMfds.get(partitionPredicate);
            IntPredicate intPredicate = partitionPredicate.getPredicate(rupSet);
            RupSetScalingRelationship rupSetScalingRelationship =
                    scalingRelationship.toRupSetScalingRelationship(partitionPredicate.isCrustal());

            String partitionRelPathToResources =
                    relPathToResources + "/" + partitionPredicate.name();
            File partitionResourcesDir = new File(resourcesDir, partitionPredicate.name());
            partitionResourcesDir.mkdirs();

            FaultSystemSolution filteredInversionSolution =
                    FilteredFaultSystemRupSet.forIntPredicate(
                            sol, intPredicate, rupSetScalingRelationship);
            filteredInversionSolution.getRupSet().addModule(targetMFDs);
            result.add(getSubHeading() + " " + partitionPredicate.name());
            setSubHeading(getSubHeading() + "#");
            result.addAll(
                    super.plot(
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
    public String getName() {
        return "Partition Solution MFDs";
    }
}

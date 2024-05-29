package nz.cri.gns.NZSHM22.opensha.reports;

import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractRupSetPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A report to investigate thinning algorithms.
 */
public class ThinningReport extends AbstractRupSetPlot {
    final String crustalRupsetFileName;

    public ThinningReport(String crustalRupsetFileName) {
        this.crustalRupsetFileName = crustalRupsetFileName;
    }

    @Override
    public List<String> plot(FaultSystemRupSet rupSet, FaultSystemSolution sol, ReportMetadata meta, File resourcesDir, String relPathToResources, String topLink) throws IOException {

        List<String> lines = new ArrayList<>();
        FaultSystemRupSet crustalRupSet = null;
        if (rupSet.getFaultSectionData(0).getSectionName().contains("row:")) {
            crustalRupSet = FaultSystemRupSet.load(new File(crustalRupsetFileName));
        }

        lines.add("### Thinning Report for Crustal");

        SubductionStats stats = new SubductionStats(crustalRupSet, null, meta, resourcesDir, relPathToResources, "crustal", topLink, "Thinning");
        lines.addAll(stats.generateReport());

        lines.add("### Thinning Report for Subduction");
        stats = new SubductionStats(rupSet, crustalRupSet, meta, resourcesDir, relPathToResources, "subduction", topLink, "Thinning");
        lines.addAll(stats.generateReport());
        return lines;
    }

    @Override
    public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
        return null;
    }

    @Override
    public String getName() {
        return "Thinning";
    }
}

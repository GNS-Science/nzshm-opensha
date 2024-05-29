package nz.cri.gns.NZSHM22.opensha.ruptures.experimental.reports;

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
 * A report to investigate thinning algorithms for joint ru[ture creation.
 * <p>
 * See ThinningStats for the implementation.
 */
public class ThinningReport extends AbstractRupSetPlot {
    final String crustalRupsetFileName;

    /**
     * The report is run on a subduction rupture set. We're side-loading the specified crustal rupture set to
     * work out possible combinations for joint ruptures.
     *
     * @param crustalRupsetFileName
     */
    public ThinningReport(String crustalRupsetFileName) {
        this.crustalRupsetFileName = crustalRupsetFileName;
    }

    @Override
    public List<String> plot(FaultSystemRupSet rupSet, FaultSystemSolution sol, ReportMetadata meta, File resourcesDir, String relPathToResources, String topLink) throws IOException {

        List<String> lines = new ArrayList<>();
        FaultSystemRupSet crustalRupSet = null;
        crustalRupSet = FaultSystemRupSet.load(new File(crustalRupsetFileName));

        lines.add("### Thinning Report for Crustal");

        ThinningStats stats = new ThinningStats(crustalRupSet, null, meta, resourcesDir, relPathToResources, "crustal", topLink, "Thinning");
        lines.addAll(stats.generateReport());

        lines.add("### Thinning Report for Subduction");
        crustalRupSet = FaultSystemRupSet.load(new File(crustalRupsetFileName));
        stats = new ThinningStats(rupSet, crustalRupSet, meta, resourcesDir, relPathToResources, "subduction", topLink, "Thinning");
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

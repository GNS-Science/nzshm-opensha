package nz.cri.gns.NZSHM22.util;

import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportPageGen;
import org.opensha.sha.earthquake.faultSysSolution.reports.RupSetMetadata;

import java.io.File;
import java.io.IOException;

public class NZSHM22_ReportPageGen {

    String name;
    String solutionPath;
    String outputPath = "./TEST/reportPage";
    ReportPageGen.PlotLevel plotLevel = ReportPageGen.PlotLevel.LIGHT;

    public NZSHM22_ReportPageGen() {
    }

    public NZSHM22_ReportPageGen setName(String name) {
        this.name = name;
        return this;
    }

    public NZSHM22_ReportPageGen setSolution(String path) {
        this.solutionPath = path;
        return this;
    }

    public NZSHM22_ReportPageGen setOutputPath(String path) {
        this.outputPath = path;
        return this;
    }

    public NZSHM22_ReportPageGen setPlotLevel(String plotLevel) {
        this.plotLevel = ReportPageGen.PlotLevel.valueOf(plotLevel);
        return this;
    }

    public void generatePage() throws IOException {
        FaultSystemSolution solution = FaultSystemSolution.load(new File(solutionPath));
        ReportMetadata solMeta = new ReportMetadata(new RupSetMetadata(name, solution));
        ReportPageGen solReport = new ReportPageGen(solMeta, new File(outputPath),
                ReportPageGen.getDefaultSolutionPlots(plotLevel));
        solReport.generatePage();
    }
}

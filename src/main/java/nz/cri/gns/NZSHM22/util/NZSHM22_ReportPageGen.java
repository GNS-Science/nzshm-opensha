package nz.cri.gns.NZSHM22.util;

import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.reports.*;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class NZSHM22_ReportPageGen {

    String name;
    String solutionPath;
    String outputPath = "./TEST/reportPage";
    ReportPageGen.PlotLevel plotLevel = ReportPageGen.PlotLevel.FULL;
    List<AbstractRupSetPlot> plots = null;
    boolean fillSurfaces = false;

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

    /**
     * Sets the plot level. Any plots added by addPlot will be overwritten.
     * @param plotLevel
     * @return
     */
    public NZSHM22_ReportPageGen setPlotLevel(String plotLevel) {
        plots = ReportPageGen.getDefaultSolutionPlots(ReportPageGen.PlotLevel.valueOf(plotLevel));
        return this;
    }

    /**
     * Adds a specific plot to the report.
     * @param plotName
     * @return
     */
    public NZSHM22_ReportPageGen addPlot(String plotName) {
        if (plots == null) {
            plots = new ArrayList<>();
        }
        switch (plotName) {
            case "SolMFDPlot":
                plots.add(new SolMFDPlot());
                break;
            case "InversionProgressPlot":
                plots.add(new InversionProgressPlot());
                break;
            case "RateVsRateScatter":
                plots.add(new RateVsRateScatter());
                break;
            case "ParticipationRatePlot":
                plots.add(new ParticipationRatePlot());
                break;
            case "PlausibilityConfigurationReport":
                plots.add(new PlausibilityConfigurationReport());
                break;
            case "RupHistogramPlots":
                plots.add(new RupHistogramPlots());
                break;
            case "FaultSectionConnectionsPlot":
                plots.add(new FaultSectionConnectionsPlot());
                break;
            case "SlipRatePlots":
                plots.add(new SlipRatePlots());
                break;
            case "JumpCountsOverDistancePlot":
                plots.add(new JumpCountsOverDistancePlot());
                break;
            case "SegmentationPlot":
                plots.add(new SegmentationPlot());
                break;
            case "SectBySectDetailPlots":
                plots.add(new SectBySectDetailPlots());
                break;
            default:
                throw new IllegalArgumentException("not a valid plot: " + plotName);
        }
        return this;
    }

    public NZSHM22_ReportPageGen setFillSurfaces(boolean fillSurfaces) {
        this.fillSurfaces = fillSurfaces;
        return this;
    }

    public void generatePage() throws IOException {
        FaultSystemSolution solution = FaultSystemSolution.load(new File(solutionPath));
        ReportMetadata solMeta = new ReportMetadata(new RupSetMetadata(name, solution));
        if (plots == null) {
            plots = ReportPageGen.getDefaultSolutionPlots(plotLevel);
        }
        if (fillSurfaces) {
            for (AbstractRupSetPlot plot : plots) {
                if (plot instanceof SolidFillPlot) {
                    ((SolidFillPlot) plot).setFillSurfaces(true);
                }
            }
        }
        ReportPageGen solReport = new ReportPageGen(solMeta, new File(outputPath),
                plots);
        solReport.generatePage();
    }

    public static void main(String[] args) throws IOException {
        NZSHM22_ReportPageGen reportPageGen = new NZSHM22_ReportPageGen();
        reportPageGen.setName("hello!").setOutputPath("TEST/REPORTPAGEGEN")
                .setFillSurfaces(true)
                //.addPlot("SolMFDPlot")
                //.addPlot("ParticipationRatePlot")
//                .setSolution("C:\\Users\\volkertj\\Downloads\\NZSHM22_InversionSolution-UnVwdHVyZUdlbmVyYXRpb25UYXNrOjI1NjZkWUxtYQ==.zip");
                //.setSolution("TEST/inversions/CrustalInversionSolution.zip");
                .setSolution("C:\\Code\\NZSHM\\nzshm-opensha\\TEST\\inversions\\CrustalInversionSolution.zip");
        reportPageGen.generatePage();
    }
}

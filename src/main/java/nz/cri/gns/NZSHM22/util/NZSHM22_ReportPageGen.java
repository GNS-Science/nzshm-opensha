package nz.cri.gns.NZSHM22.util;

import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.reports.*;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public NZSHM22_ReportPageGen setRuptureSet(String path) {
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

    static Map<String, AbstractRupSetPlot> possiblePlots;
    static Map<String, AbstractRupSetPlot> possibleRupSetPlots;

    static{
        possiblePlots = new HashMap<>();
        List<AbstractRupSetPlot> choices = ReportPageGen.getDefaultSolutionPlots(ReportPageGen.PlotLevel.FULL);
        for(AbstractRupSetPlot plot : choices){
            possiblePlots.put(plot.getClass().getSimpleName(), plot);
        }
        possibleRupSetPlots = new HashMap<>();
        choices = ReportPageGen.getDefaultRupSetPlots(ReportPageGen.PlotLevel.FULL);
        for(AbstractRupSetPlot plot : choices){
            possibleRupSetPlots.put(plot.getClass().getSimpleName(), plot);
        }
    }

    /**
     * Adds a specific Solution plot to the report.
     * @param plotName
     * @return
     */
    public NZSHM22_ReportPageGen addPlot(String plotName) {
        if (plots == null) {
            plots = new ArrayList<>();
        }
        AbstractRupSetPlot plot = possiblePlots.get(plotName);
        if (plot != null) {
            plots.add(plot);
        } else {
            throw new IllegalArgumentException("not a valid plot: " + plotName);
        }
        return this;
    }

    /**
     * Adds a specific RupSet plot to the report.
     * @param plotName
     * @return
     */
    public NZSHM22_ReportPageGen addRupSetPlot(String plotName) {
        if (plots == null) {
            plots = new ArrayList<>();
        }
        AbstractRupSetPlot plot = possibleRupSetPlots.get(plotName);
        if (plot != null) {
            plots.add(plot);
        } else {
            throw new IllegalArgumentException("not a valid plot: " + plotName);
        }
        return this;
    }

    public NZSHM22_ReportPageGen setFillSurfaces(boolean fillSurfaces) {
        this.fillSurfaces = fillSurfaces;
        return this;
    }

    public void generatePage() throws IOException {
    	
    	int available = Runtime.getRuntime().availableProcessors();
    	if (available <= 1) {
    		System.err.println("Warning: Execution environment has only " + available + " processors allocated. Report threading is disabled.");
    	}
    	
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

    public void generateRupSetPage() throws IOException{
        int available = Runtime.getRuntime().availableProcessors();
        if (available <= 1) {
            System.err.println("Warning: Execution environment has only " + available + " processors allocated. Report threading is disabled.");
        }

        FaultSystemRupSet rupSet = FaultSystemRupSet.load(new File(solutionPath));
        ReportMetadata solMeta = new ReportMetadata(new RupSetMetadata(name, rupSet));
        if (plots == null) {
            plots = ReportPageGen.getDefaultRupSetPlots(plotLevel);
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
        reportPageGen.setName("SW52ZXJzaW9uU29sdXRpb246MTUzMzYuMExIQkxw")
        	.setOutputPath("TEST/REPORTPAGEGEN5")
            .setFillSurfaces(true)
            .setPlotLevel("FULL")
//                .setSolution("/home/chrisbc/DEV/GNS/AWS_S3_DATA/WORKING/downloads/SW52ZXJzaW9uU29sdXRpb246MTUzMzYuMExIQkxw/NZSHM22_InversionSolution-QXV0b21hdGlvblRhc2s6NTM3MGN3MmJw.zip");
    		.setSolution("./TEST/NZSHM22_InversionSolution-QXV0b21hdGlvblRhc2s6MTQxMjRNQ1cy.zip");
        reportPageGen.generatePage();
        System.out.println("DONE!");
    }
}

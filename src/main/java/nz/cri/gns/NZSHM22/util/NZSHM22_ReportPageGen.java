package nz.cri.gns.NZSHM22.util;

import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_LogicTreeBranch;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.NamedFaults;
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
    FaultSystemRupSet rupSet = null;
    FaultSystemSolution solution = null;

    public NZSHM22_ReportPageGen() {
    }

    public NZSHM22_ReportPageGen setName(String name) {
        this.name = name;
        return this;
    }

    public NZSHM22_ReportPageGen setSolution(FaultSystemSolution solution) {
        this.solution = solution;
        return this;
    }

    public NZSHM22_ReportPageGen setSolution(String path) {
        this.solutionPath = path;
        return this;
    }

    public NZSHM22_ReportPageGen setRuptureSet(FaultSystemRupSet rupSet) {
        this.rupSet = rupSet;
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
     * Sets the plot level.
     * @param plotLevel
     * @return
     */
    public NZSHM22_ReportPageGen setPlotLevel(String plotLevel) {
        if(plotLevel == null){
            this.plotLevel = null;
        } else {
            this.plotLevel = ReportPageGen.PlotLevel.valueOf(plotLevel);
        }
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

    /**
     * Adds a specific RupSet plot to the report.
     *
     * @param plotName
     * @return
     */
    public NZSHM22_ReportPageGen addRupSetPlot(AbstractRupSetPlot plot) {
        if (plots == null) {
            plots = new ArrayList<>();
        }
        plots.add(plot);
        return this;
    }


    public NZSHM22_ReportPageGen setFillSurfaces(boolean fillSurfaces) {
        this.fillSurfaces = fillSurfaces;
        return this;
    }

    public static FaultSystemRupSet addNamedFaults(FaultSystemRupSet rupSet) {

        if (rupSet.getModule(NamedFaults.class) != null) {
            return rupSet;
        }

        NZSHM22_LogicTreeBranch branch = rupSet.getModule(NZSHM22_LogicTreeBranch.class);

        if (branch == null) {
            return rupSet;
        }

        NZSHM22_FaultModels faultModel = branch.getValue(NZSHM22_FaultModels.class);
        if (faultModel == null) {
            return rupSet;
        }

        Map<String, List<Integer>> mapping = faultModel.getNamedFaultsMapAlt();

        if (mapping != null) {
            NamedFaults namedFaults = new NamedFaults(rupSet, mapping);
            rupSet.addModule(namedFaults);
        }

        return rupSet;
    }

    public void generatePage() throws IOException {
    	
    	int available = Runtime.getRuntime().availableProcessors();
    	if (available <= 1) {
    		System.err.println("Warning: Execution environment has only " + available + " processors allocated. Report threading is disabled.");
    	}
    	
        FaultSystemSolution solution = this.solution != null? this.solution : FaultSystemSolution.load(new File(solutionPath));
        addNamedFaults(solution.getRupSet());
        ReportMetadata solMeta = new ReportMetadata(new RupSetMetadata(name, solution));

        List<AbstractRupSetPlot> reportPlots = new ArrayList<>();
        if (plotLevel != null) {
            reportPlots.addAll(ReportPageGen.getDefaultSolutionPlots(plotLevel));
        }
        if(plots != null){
            reportPlots.addAll(plots);
        }
        if (fillSurfaces) {
            for (AbstractRupSetPlot plot : reportPlots) {
                if (plot instanceof SolidFillPlot) {
                    ((SolidFillPlot) plot).setFillSurfaces(true);
                }
            }
        }
        ReportPageGen solReport = new ReportPageGen(solMeta, new File(outputPath), reportPlots);
        solReport.generatePage();
    }

    public void generateRupSetPage() throws IOException{
        int available = Runtime.getRuntime().availableProcessors();
        if (available <= 1) {
            System.err.println("Warning: Execution environment has only " + available + " processors allocated. Report threading is disabled.");
        }

        FaultSystemRupSet rupSet = this.rupSet != null ? this.rupSet : FaultSystemRupSet.load(new File(solutionPath));
        addNamedFaults(rupSet);
        ReportMetadata solMeta = new ReportMetadata(new RupSetMetadata(name, rupSet));

        List<AbstractRupSetPlot> reportPlots = new ArrayList<>();
        if (plotLevel != null) {
            reportPlots.addAll(ReportPageGen.getDefaultRupSetPlots(plotLevel));
        }
        if(plots != null){
            reportPlots.addAll(plots);
        }
        if (fillSurfaces) {
            for (AbstractRupSetPlot plot : reportPlots) {
                if (plot instanceof SolidFillPlot) {
                    ((SolidFillPlot) plot).setFillSurfaces(true);
                }
            }
        }
        ReportPageGen solReport = new ReportPageGen(solMeta, new File(outputPath), reportPlots);
        solReport.generatePage();
    }

    public static void main(String[] args) throws IOException {
        NZSHM22_ReportPageGen reportPageGen = new NZSHM22_ReportPageGen();
//       reportPageGen.setName("Min Mag = 8.05")
//       	.setOutputPath("/tmp/reports/m8_V2")
//           .setFillSurfaces(true)
//           .setPlotLevel("DEFAULT")
////                .setSolution("/home/chrisbc/DEV/GNS/AWS_S3_DATA/WORKING/downloads/SW52ZXJzaW9uU29sdXRpb246MTUzMzYuMExIQkxw/NZSHM22_InversionSolution-QXV0b21hdGlvblRhc2s6NTM3MGN3MmJw.zip");
//   		.setSolution("/tmp/inversions/test_sub_m8.zip");
//       reportPageGen.generatePage();


        reportPageGen.setRuptureSet("C:\\tmp\\NZSHM\\disconnectedPuy.zip")
                .setName("hello!")
                .setOutputPath("TEST/reports")
                .setFillSurfaces(true)
                .setPlotLevel("DEFAULT");
        reportPageGen.generateRupSetPage();

        System.out.println("DONE!");
    }
}

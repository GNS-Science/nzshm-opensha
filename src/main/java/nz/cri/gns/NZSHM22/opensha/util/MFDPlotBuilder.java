package nz.cri.gns.NZSHM22.opensha.util;

import com.google.common.collect.Sets;
import java.io.File;
import java.io.IOException;
import java.util.*;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_LogicTreeBranch;
import nz.cri.gns.NZSHM22.opensha.ruptures.CustomFaultModel;
import nz.cri.gns.NZSHM22.util.MFDPlot;
import org.dom4j.DocumentException;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.NamedFaults;
import org.opensha.sha.faultSurface.FaultSection;

public class MFDPlotBuilder {

    FaultSystemSolution solution;
    File outputDir;
    NZSHM22_FaultModels faultModel;

    public MFDPlotBuilder() {}

    public MFDPlotBuilder setCrustalSolution(String fileName) throws IOException {
        solution = FaultSystemSolution.load(new File(fileName));
        return this;
    }

    public MFDPlotBuilder setSubductionSolution(String fileName) throws IOException {
        solution = FaultSystemSolution.load(new File(fileName));
        return this;
    }

    public MFDPlotBuilder setOutputDir(String dir) {
        this.outputDir = new File(dir);
        return this;
    }

    public void plot() throws IOException {
        HashMap<String, Set<Integer>> parentSections = null;
        if (faultModel == null) {
            NZSHM22_LogicTreeBranch branch =
                    solution.getRupSet().getModule(NZSHM22_LogicTreeBranch.class);
            if (branch != null) {
                faultModel = branch.getValue(NZSHM22_FaultModels.class);
            }
        }

        if (faultModel != null) {

            CustomFaultModel customFaultModel =
                    solution.getRupSet().getModule(CustomFaultModel.class);
            if (customFaultModel != null) {
                faultModel.setCustomModel(customFaultModel.getModelData());
            }

            parentSections = new HashMap<>();

            NamedFaults namedFaults = solution.getRupSet().getModule(NamedFaults.class);

            Map<String, List<Integer>> namedFaultsMap =
                    namedFaults != null ? namedFaults.get() : faultModel.getNamedFaultsMapAlt();
            if (namedFaultsMap != null) {
                for (String name : namedFaultsMap.keySet()) {
                    parentSections.put(name, Sets.newHashSet(namedFaultsMap.get(name)));
                }
            }
        }
        if (parentSections == null) {
            parentSections = new HashMap<>();
            for (FaultSection sect : solution.getRupSet().getFaultSectionDataList()) {
                if (!parentSections.containsKey(sect.getParentSectionName())) {
                    parentSections.put(
                            sect.getParentSectionName(),
                            Sets.newHashSet(sect.getParentSectionId()));
                }
            }
        }
        MFDPlot.writeParentSectionMFDPlots(solution, parentSections, outputDir);
    }

    public static void main(String[] args) throws DocumentException, IOException {

        //    	String solution = "../DATA/2021-06-01-01/UnVwdHVyZUdlbmVyYXRpb25UYXNrOjE2OEpUUmFC/" +
        //    			"InversionSolution-RmlsZToz-rnd0-t1380_RmlsZTo1MTYuMGQ3WlVz.zip"; //LONG CFM 3
        //    	String solution =
        // "/home/chrisbc/DEV/GNS/opensha-new/DATA/2021-06-01-01/UnVwdHVyZUdlbmVyYXRpb25UYXNrOjE4MFJFWXF4" +
        //    			"/" + "InversionSolution-RmlsZTo5-rnd0-t1380_RmlsZTo1MjIuMDN2ZktR.zip"; //LONG CFM
        // 9
        String solution =
                "C:\\Users\\volkertj\\Downloads\\NZSHM22_RuptureSet-UnVwdHVyZUdlbmVyYXRpb25UYXNrOjU0MjJKaFdxVg==(3).zip";

        new MFDPlotBuilder()
                .setOutputDir("/tmp/mfd")
                // faults
                .setCrustalSolution(solution)
                .plot();

        System.out.println("Done!");
    }
}

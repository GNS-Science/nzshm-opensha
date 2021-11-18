package nz.cri.gns.NZSHM22.opensha.util;

import com.google.common.collect.Sets;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.inversion.NZSHM22_InversionFaultSystemSolution;
import nz.cri.gns.NZSHM22.util.MFDPlot;
import org.dom4j.DocumentException;
import org.opensha.sha.faultSurface.FaultSection;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class MFDPlotBuilder {

    NZSHM22_InversionFaultSystemSolution solution;
    File outputDir;
    NZSHM22_FaultModels faultModel;

    public MFDPlotBuilder() {
    }

    public MFDPlotBuilder setCrustalSolution(String fileName) throws DocumentException, IOException {
        solution = NZSHM22_InversionFaultSystemSolution.fromCrustalFile(new File(fileName));
        return this;
    }

    public MFDPlotBuilder setSubductionSolution(String fileName) throws DocumentException, IOException {
        solution = NZSHM22_InversionFaultSystemSolution.fromSubductionFile(new File(fileName));
        return this;
    }

    /**
     * Optional. Set this if you want to only plot named faults.
     * @param faultModelName
     * @return
     */
    public MFDPlotBuilder setFaultModel(String faultModelName) {
        faultModel = NZSHM22_FaultModels.valueOf(faultModelName);
        return this;
    }

    public MFDPlotBuilder setOutputDir(String dir) {
        this.outputDir = new File(dir);
        return this;
    }

    public void plot() throws IOException {
        HashMap<String, Set<Integer>> parentSections = new HashMap<>();
        if (faultModel != null) {
            Map<String, List<Integer>> namedFaultsMap = faultModel.getNamedFaultsMapAlt();
            for (String name : namedFaultsMap.keySet()) {
                parentSections.put(name, Sets.newHashSet(namedFaultsMap.get(name)));
            }
        } else {
            for (FaultSection sect : solution.getRupSet().getFaultSectionDataList()) {
                if (!parentSections.containsKey(sect.getParentSectionName())) {
                    parentSections.put(sect.getParentSectionName(), Sets.newHashSet(sect.getParentSectionId()));
                }
            }
        }
        MFDPlot.writeParentSectionMFDPlots(solution, parentSections, outputDir);
    }

    public static void main(String[] args) throws DocumentException, IOException {
    	
//    	String solution = "../DATA/2021-06-01-01/UnVwdHVyZUdlbmVyYXRpb25UYXNrOjE2OEpUUmFC/" + 
//    			"InversionSolution-RmlsZToz-rnd0-t1380_RmlsZTo1MTYuMGQ3WlVz.zip"; //LONG CFM 3
//    	String solution = "/home/chrisbc/DEV/GNS/opensha-new/DATA/2021-06-01-01/UnVwdHVyZUdlbmVyYXRpb25UYXNrOjE4MFJFWXF4" + 
//    			"/" + "InversionSolution-RmlsZTo5-rnd0-t1380_RmlsZTo1MjIuMDN2ZktR.zip"; //LONG CFM 9
    	String solution = "C:\\Users\\volkertj\\Downloads\\NZSHM22_InversionSolution-QXV0b21hdGlvblRhc2s6NTA3MHRBZ3dG.zip";
    	  				
        new MFDPlotBuilder()
                .setOutputDir("/tmp/mfd")
                .setFaultModel("CFM_0_9_SANSTVZ_D90") // optional, set if you only want to plot named faults
                .setCrustalSolution(solution)
                .plot();
        
        System.out.println("Done!");
    }
}

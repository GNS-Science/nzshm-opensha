package nz.cri.gns.NZSHM22.opensha.enumTreeBranches;

import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.PaleoProbabilityModel;
import scratch.UCERF3.utils.paleoRateConstraints.UCERF3_PaleoProbabilityModel;

import java.io.IOException;
import java.net.URL;

public enum NZSHM22_PaleoProbabilityModel implements LogicTreeNode {

    NZSHM22_C_41("pdetection_C41.txt"),
    NZSHM22_C_42("pdetection_C42.txt"),
    NZSHM22_C_43("pdetection_C43.txt"),

    UCERF3("pdetection2.txt"),
    UCERF3_PLUS_PT25("pdetection2_pluspt25.txt"),
    UCERF3_PLUS_PT5("pdetection2_pluspt5.txt");


    final static String RESOURCE_PATH = "/paleoRates/";

    String fileName;

    NZSHM22_PaleoProbabilityModel(String fileName){
        this.fileName = fileName;
    }

    URL getURL(String fileName) {
        return getClass().getResource(RESOURCE_PATH + fileName);
    }

    public PaleoProbabilityModel fetchModel(){
        PaleoProbabilityModel result;
        try{
            result = UCERF3_PaleoProbabilityModel.fromURL(getURL(fileName));
        } catch(IOException x){
            x.printStackTrace();
            throw new RuntimeException(x);
        }
        return result;
    }

    @Override
    public String getName() {
        return "NZSHM22_ProbabilityModel";
    }

    @Override
    public String getShortName() {
        return getName();
    }

    @Override
    public double getNodeWeight(LogicTreeBranch<?> fullBranch) {
        return 0;
    }

    @Override
    public String getFilePrefix() {
        return null;
    }

    public static LogicTreeLevel<LogicTreeNode> level() {
        return LogicTreeLevel.forEnumUnchecked(NZSHM22_PaleoProbabilityModel.class, "NZSHM22_PaleoProbabilityModel", "NZSHM22_PaleoProbabilityModel");
    }
}

package nz.cri.gns.NZSHM22.opensha.enumTreeBranches;

import nz.cri.gns.NZSHM22.opensha.griddedSeismicity.NZSHM22_GriddedData;
import org.opensha.commons.geo.GriddedRegion;

import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;

public enum NZSHM22_SpatialSeisPDF implements LogicTreeNode {

    NZSHM22_1246("NZSHM22_1246", "1246", "BEST2FLTOLDNC1246.txt"),
    NZSHM22_1246R("NZSHM22_1246R", "1246R", "BEST2FLTOLDNC1246r.txt"),
    NZSHM22_1456("NZSHM22_1456", "1456", "BESTFLTOLDNC1456.txt"),
    NZSHM22_1456R("NZSHM22_1456R", "1456R", "BESTFLTOLDNC1456r.txt"),
    NZSHM22_1346("NZSHM22_1346", "1346", "Gruenthalmod1346ConfDSMsss.txt");

    static final String DATA_DIR = "seismicityGrids/";

    String name;
    String shortName;
    String fileName;

    NZSHM22_GriddedData pdf;

    NZSHM22_SpatialSeisPDF(String name, String shortName, String filename) {
        this.name = name;
        this.shortName = shortName;
        this.fileName = filename;
        if(filename != null){
            pdf = NZSHM22_GriddedData.fromFile(DATA_DIR + fileName);
        }
    }

    @Override
    public String getShortName() {
        return shortName;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public double getNodeWeight(LogicTreeBranch<?> fullBranch) {
        return 0;
    }

    @Override
    public String getFilePrefix() {
        return "";
    }

    public double[] getPDF(GriddedRegion region) {
        return pdf.getValues(region);
    }

    public NZSHM22_GriddedData getGriddedData(){
        return pdf;
    }

    public double getFractionInRegion(GriddedRegion region) {
        return pdf.getFractionInRegion(region);
    }

    public void normaliseRegion(GriddedRegion region){
        pdf.normaliseRegion(region);
    }

    public static LogicTreeLevel<LogicTreeNode> level() {
        return LogicTreeLevel.forEnumUnchecked(NZSHM22_SpatialSeisPDF.class, "NZSHM22_SpatialSeisPDF", "NZSHM22_SpatialSeisPDF");
    }

}

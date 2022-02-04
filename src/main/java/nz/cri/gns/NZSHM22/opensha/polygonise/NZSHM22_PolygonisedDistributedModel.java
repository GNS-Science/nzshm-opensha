package nz.cri.gns.NZSHM22.opensha.polygonise;

import nz.cri.gns.NZSHM22.opensha.griddedSeismicity.NZSHM22_GriddedData;
import org.opensha.commons.util.modules.helpers.FileBackedModule;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;

public class NZSHM22_PolygonisedDistributedModel implements FileBackedModule {

    NZSHM22_GriddedData griddedData;

    public NZSHM22_PolygonisedDistributedModel(){
    }

    public NZSHM22_PolygonisedDistributedModel(NZSHM22_GriddedData griddedData) {
        this.griddedData = griddedData;
    }

    @Override
    public String getFileName() {
        return "NZSHM22_PolygonisedDistributedModel.csv";
    }

    @Override
    public void writeToStream(BufferedOutputStream out) throws IOException {
        griddedData.writeToStream(out);
    }

    @Override
    public void initFromStream(BufferedInputStream in) throws IOException {
        griddedData = new NZSHM22_GriddedData();
        griddedData.initFromStream(in);
    }

    public NZSHM22_GriddedData getGriddedData(){
        return griddedData;
    }

    @Override
    public String getName() {
        return "NZSHM22_PolygonisedDistributedModel";
    }
}

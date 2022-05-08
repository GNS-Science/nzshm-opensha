package nz.cri.gns.NZSHM22.opensha.polygonise;

import nz.cri.gns.NZSHM22.opensha.griddedSeismicity.NZSHM22_GriddedData;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.util.modules.helpers.FileBackedModule;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * A module that writes the result of the polygonization process into the solution archive.
 */
public class NZSHM22_PolygonisedDistributedModel implements FileBackedModule {

    NZSHM22_GriddedData originalGriddedData;
    NZSHM22_GriddedData griddedData;
    List<Double> mMins;

    /**
     * For deserialisation
     */
    public NZSHM22_PolygonisedDistributedModel() {
    }

    /**
     * Creates a new NZSHM22_PolygonisedDistributedModel
     * @param originalGriddedData the grid data before polygonisation
     * @param griddedData the output of the polygonisation
     * @param mMins mMin values for each grid point, in the same order as gridddata.getGridPoints()
     */
    public NZSHM22_PolygonisedDistributedModel(NZSHM22_GriddedData originalGriddedData, NZSHM22_GriddedData griddedData, List<Double> mMins) {
        this.originalGriddedData = originalGriddedData;
        this.griddedData = griddedData;
        this.mMins = mMins;
    }

    @Override
    public String getFileName() {
        return "NZSHM22_PolygonisedDistributedModel.csv";
    }

    @Override
    public void writeToStream(BufferedOutputStream out) throws IOException {
        CSVFile<Double> csv = griddedData.toCsv();
        originalGriddedData.addToCsv(csv);
        csv.addColumn(mMins);
        csv.writeToStream(out);
    }

    @Override
    public void initFromStream(BufferedInputStream in) throws IOException {
        CSVFile<Double> csv = CSVFile.readStreamNumeric(in, true, 5, 0);
        griddedData = new NZSHM22_GriddedData();
        griddedData.initFromCsv(csv);
        mMins = csv.getColumn(3);
    }

    public NZSHM22_GriddedData getGriddedData() {
        return griddedData;
    }

    @Override
    public String getName() {
        return "NZSHM22_PolygonisedDistributedModel";
    }
}

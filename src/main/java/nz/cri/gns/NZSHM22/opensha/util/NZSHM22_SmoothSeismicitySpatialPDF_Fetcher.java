package nz.cri.gns.NZSHM22.opensha.util;

import nz.cri.gns.NZSHM22.opensha.griddedSeismicity.NZSHM22_GridReader;
import org.opensha.commons.geo.GriddedRegion;

/**
 * This reads and provides the smoothed seismicity spatial PDFs
 * <p>
 * based on scratch.UCERF3.utils.SmoothSeismicitySpatialPDF_Fetcher
 *
 * @author chrisbc
 */

public class NZSHM22_SmoothSeismicitySpatialPDF_Fetcher {

    public static final String SUBDIR = "SeismicityGrids";
    public static final String FILENAME_1246 = "BEST2FLTOLDNC1246.txt";
    public static final String FILENAME_1246_R = "BEST2FLTOLDNC1246r.txt";
    public static final String FILENAME_1456 = "BESTFLTOLDNC1456.txt";
    public static final String FILENAME_1456_R = "BESTFLTOLDNC1456r.txt";
    public static final String FILENAME_1346 = "Gruenthalmod1346ConfDSMsss.txt";

    public static double[] get1246(GriddedRegion region){
        return new NZSHM22_GridReader(FILENAME_1246, region).getValues();
    }

    public static double[] get1456(GriddedRegion region){
        return new NZSHM22_GridReader(FILENAME_1456, region).getValues();
    }

    public static double[] get1246R(GriddedRegion region){
        return new NZSHM22_GridReader(FILENAME_1246_R, region).getValues();
    }

    public static double[] get1456R(GriddedRegion region){
        return new NZSHM22_GridReader(FILENAME_1456_R, region).getValues();
    }

    public static double[] get1346(GriddedRegion region){
        return new NZSHM22_GridReader(FILENAME_1346, region).getValues();
    }

}

package nz.cri.gns.NZSHM22.opensha.ruptures.experimental.rsqsims;

import com.google.common.base.Preconditions;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.PlaneUtils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads geometry as specified in https://zenodo.org/records/5534462
 * and as in
 * https://github.com/uc-eqgeo/rsqsim-python-tools/blob/main/src/rsqsim_api/rsqsim_api/fault/multifault.py#L161
 * <p>
 * Geometry File (ASCII): zfault_Deepen.in
 * <p>
 * ASCII file listing patch (triangular) geometry for the simulated faults
 * in a UTM coordinate system (zone 11S). The primary columns are:
 * <p>
 * * /x1, y1, z1/ - UTM coordinates of the first vertex
 * * /x2, y2, z2/ - UTM coordinates of the second vertex
 * * /x3, y3, z3/ - UTM coordinates of the third vertex
 * * /rake/ - Direction of the motion of the hanging wall relative to the
 * footwall (in degrees, following the convention of Aki & Richards, 2002)
 * * /slip_rate/ - Long-term average slip rate (in m/s)
 * <p>
 * The first line in the file (excluding comment lines that start with '#')
 * is the patch with ID=1, the second ID=2, etc. Additional metadata
 * columns may exist in each line beyond those listed and can be ignored.
 */

public class PatchesFile {

    String fileName;
    CoordinateConverter coordinateConverter;

    public PatchesFile(String fileName, CoordinateConverter coordinateConverter) {
        this.fileName = fileName;
        this.coordinateConverter = coordinateConverter;
    }

    Location toLatLon(double easting, double northing, double depth) {
        return coordinateConverter.toWGS84(easting, northing, Math.abs(depth) / 1000);
    }

    Patch loadPatch(int id, String line) {
        String[] parts = line.split(" ");
        Preconditions.checkArgument(parts.length >= 11, "Line must have at least 11 elements");
        double[] values = new double[11];
        for (int i = 0; i < 11; i++) {
            Preconditions.checkArgument(!parts[i].isEmpty());
            values[i] = Double.parseDouble(parts[i]);
        }

        // calculating triangle area
        // we're assuming that the coordinates are in an orthographic coordinate system in meters (such as UTM)
        double[] ab = new double[]{values[3] - values[0], values[4] - values[1], values[5] - values[2]};
        double[] ac = new double[]{values[6] - values[0], values[7] - values[1], values[8] - values[2]};
        double[] crossProduct = PlaneUtils.getCrossProduct(ab, ac, false);
        double area = PlaneUtils.getMagnitude(crossProduct) / 2;

//        double[] cb = new double[]{values[6] - values[3], values[7] - values[4], values[8] - values[5]};
//
//        System.out.println(id +": " + PlaneUtils.getMagnitude(ab) + ", " + PlaneUtils.getMagnitude(ac)+", " + PlaneUtils.getMagnitude(cb) + ", " + area);

        Location l1 = toLatLon(values[0], values[1], values[2]);
        Location l2 = toLatLon(values[3], values[4], values[5]);
        Location l3 = toLatLon(values[6], values[7], values[8]);
        Patch patch = null;
        try {
            patch = Patch.create(id, l1, l2, l3, values[9], values[10], area, parts);
        } catch (Exception x) {
            //x.printStackTrace();
            System.err.println("error at line " + id + " " + x.getMessage());
        }
        return patch;
    }


    public List<Patch> loadPatches() throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(fileName));
        List<Patch> patches = new ArrayList<>();

        String line;
        int id = 1;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            Patch patch = loadPatch(id, line);
            id++;
            patches.add(patch);
        }

        reader.close();
        return patches;
    }
}

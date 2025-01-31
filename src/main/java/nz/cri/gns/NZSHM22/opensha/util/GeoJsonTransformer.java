package nz.cri.gns.NZSHM22.opensha.util;

import java.io.File;
import java.io.IOException;
import java.util.List;
import nz.cri.gns.NZSHM22.opensha.data.region.NewZealandRegions;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.faults.FaultSectionList;
import org.dom4j.DocumentException;
import org.opensha.commons.geo.Region;
import org.opensha.sha.faultSurface.FaultSection;

/** Writing out our geometries for easy inspection */
public class GeoJsonTransformer {

    public static void transformFaultModels(String outputPath)
            throws DocumentException, IOException {
        for (NZSHM22_FaultModels faultModel : NZSHM22_FaultModels.values()) {
            FaultSectionList sections = new FaultSectionList();
            faultModel.fetchFaultSections(sections);

            SimpleGeoJsonBuilder builder = new SimpleGeoJsonBuilder();
            for (FaultSection section : sections) {
                builder.addFaultSection(section);
            }
            builder.toJSON(new File(outputPath, "FaultModel-" + faultModel.name() + ".json"));
        }
    }

    public static void transformRegions(String outputPath) {
        for (Region region :
                List.of(
                        new NewZealandRegions.NZ_RECTANGLE(),
                        new NewZealandRegions.NZ_RECTANGLE_SANS_TVZ(),
                        new NewZealandRegions.NZ_TVZ(),
                        new NewZealandRegions.NZ_TEST())) {
            SimpleGeoJsonBuilder builder = new SimpleGeoJsonBuilder();
            builder.addRegion(region);
            builder.toJSON(new File(outputPath, "Region-" + region.getName() + ".json"));
        }
    }

    public static void main(String[] args) throws DocumentException, IOException {
        transformRegions("TEST/geojson/");
        transformFaultModels("TEST/geojson/");
    }
}

package nz.cri.gns.NZSHM22.opensha.enumTreeBranches;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;

@JsonAdapter(NZSHM22_Regions.Adapter.class)
public class NZSHM22_Regions implements LogicTreeNode {

    protected GriddedRegion sansTvzRegion;
    protected GriddedRegion tvzRegion;

    public NZSHM22_Regions() {}

    public NZSHM22_Regions(GriddedRegion sans, GriddedRegion tvz) {
        this.sansTvzRegion = sans;
        this.tvzRegion = tvz;
    }

    public GriddedRegion getSansTvzRegion() {
        return sansTvzRegion;
    }

    public void setSansTvzRegion(GriddedRegion sansTvzRegion) {
        this.sansTvzRegion = sansTvzRegion;
    }

    public GriddedRegion getTvzRegion() {
        return tvzRegion;
    }

    public void setTvzRegion(GriddedRegion tvzRegion) {
        this.tvzRegion = tvzRegion;
    }

    @Override
    public String getName() {
        return "NZSHM22_Regions";
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

    public static class Level extends LogicTreeLevel.AdapterBackedLevel {
        public Level() {
            super("NZSHM22_Regions", "NZSHM22_Regions", NZSHM22_Regions.class);
        }
    }

    public static class Adapter extends TypeAdapter<NZSHM22_Regions> {

        GriddedRegion.Adapter adapter = new GriddedRegion.Adapter();

        @Override
        public void write(JsonWriter out, NZSHM22_Regions value) throws IOException {
            out.beginObject();
            out.name("sans");
            adapter.write(out, value.sansTvzRegion);
            out.name("tvz");
            adapter.write(out, value.tvzRegion);
            out.endObject();
        }

        @Override
        public NZSHM22_Regions read(JsonReader in) throws IOException {
            NZSHM22_Regions regions = new NZSHM22_Regions();
            in.beginObject();
            while (in.hasNext()) {
                switch (in.nextName()) {
                    case "sans":
                        regions.setSansTvzRegion(adapter.read(in));
                        break;
                    case "tvz":
                        regions.setTvzRegion(adapter.read(in));
                        break;
                }
            }
            in.endObject();
            return regions;
        }
    }
}

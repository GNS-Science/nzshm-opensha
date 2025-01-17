package nz.cri.gns.NZSHM22.opensha.enumTreeBranches;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;

public class NZSHM22_MagBounds implements LogicTreeNode {

    protected double maxMagSans;
    protected double maxMagTvz;
    protected MaxMagType maxMagType = MaxMagType.NONE;

    public enum MaxMagType {
        NONE,
        MANIPULATE_MFD;
    }

    public NZSHM22_MagBounds() {}

    public NZSHM22_MagBounds(double maxMagSans, double maxMagTvz, MaxMagType type) {
        this.maxMagSans = maxMagSans;
        this.maxMagTvz = maxMagTvz;
        this.maxMagType = type;
    }

    public double getMaxMagSans() {
        return maxMagSans;
    }

    public void setMaxMagSans(double maxMagSans) {
        this.maxMagSans = maxMagSans;
    }

    public double getMaxMagTvz() {
        return maxMagTvz;
    }

    public void setMaxMagTvz(double maxMagTvz) {
        this.maxMagTvz = maxMagTvz;
    }

    public MaxMagType getMaxMagType() {
        return maxMagType;
    }

    public void setMaxMagType(MaxMagType maxMagType) {
        this.maxMagType = maxMagType;
    }

    @Override
    public String getName() {
        return "NZSHM22_MagBounds";
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
        return "NZSHM22_MagBounds";
    }

    public static class Adapter extends TypeAdapter<NZSHM22_MagBounds> {

        @Override
        public void write(JsonWriter out, NZSHM22_MagBounds value) throws IOException {
            out.beginObject();
            out.name("maxMagSans");
            out.value(value.maxMagSans);
            out.name("maxMagTvz");
            out.value(value.maxMagTvz);
            out.name("maxMagType");
            out.value(value.maxMagType.name());
            out.endObject();
        }

        @Override
        public NZSHM22_MagBounds read(JsonReader in) throws IOException {
            NZSHM22_MagBounds bounds = new NZSHM22_MagBounds();
            while (in.hasNext()) {
                switch (in.nextName()) {
                    case "maxMagSans":
                        bounds.maxMagSans = in.nextDouble();
                        break;
                    case "maxMagTvz":
                        bounds.maxMagTvz = in.nextDouble();
                        break;
                    case "maxMagType":
                        bounds.maxMagType = MaxMagType.valueOf(in.nextString());
                        break;
                }
            }
            return bounds;
        }
    }

    public static class Level extends LogicTreeLevel.AdapterBackedLevel {
        public Level() {
            super("NZSHM22_MagBounds", "NZSHM22_MagBounds", NZSHM22_MagBounds.class);
        }
    }
}

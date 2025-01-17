package nz.cri.gns.NZSHM22.opensha.enumTreeBranches;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;

@JsonAdapter(NZSHM22_SlipRateFactors.Adapter.class)
public class NZSHM22_SlipRateFactors implements LogicTreeNode {

    protected double tvzFactor;
    protected double sansFactor;

    public NZSHM22_SlipRateFactors() {}

    public NZSHM22_SlipRateFactors(double sansFactor, double tvzFactor) {
        this.tvzFactor = tvzFactor;
        this.sansFactor = sansFactor;
    }

    public double getTvzFactor() {
        return tvzFactor;
    }

    public void setTvzFactor(double tvzFactor) {
        this.tvzFactor = tvzFactor;
    }

    public double getSansFactor() {
        return sansFactor;
    }

    public void setSansFactor(double sansFactor) {
        this.sansFactor = sansFactor;
    }

    @Override
    public String getName() {
        return "NZSHM22_SlipRateFactors";
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

    public static class Adapter extends TypeAdapter<NZSHM22_SlipRateFactors> {

        @Override
        public void write(JsonWriter out, NZSHM22_SlipRateFactors value) throws IOException {
            out.beginObject();
            out.name("tvzFactor");
            out.value(value.tvzFactor);
            out.name("sansFactor");
            out.value(value.sansFactor);
            out.endObject();
        }

        @Override
        public NZSHM22_SlipRateFactors read(JsonReader in) throws IOException {
            NZSHM22_SlipRateFactors result = new NZSHM22_SlipRateFactors();
            in.beginObject();
            while (in.hasNext()) {
                switch (in.nextName()) {
                    case "tvzFactor":
                        result.setTvzFactor(in.nextDouble());
                        break;
                    case "sansFactor":
                        result.setSansFactor(in.nextDouble());
                        break;
                }
            }
            in.endObject();
            return result;
        }
    }

    public static class Level extends LogicTreeLevel.AdapterBackedLevel {
        public Level() {
            super(
                    "NZSHM22_SlipRateFactors",
                    "NZSHM22_SlipRateFactors",
                    NZSHM22_SlipRateFactors.class);
        }
    }
}

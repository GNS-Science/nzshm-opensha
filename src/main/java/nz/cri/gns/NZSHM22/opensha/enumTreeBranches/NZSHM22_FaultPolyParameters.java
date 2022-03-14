package nz.cri.gns.NZSHM22.opensha.enumTreeBranches;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;

import java.io.IOException;

@JsonAdapter(NZSHM22_FaultPolyParameters.Adapter.class)
public class NZSHM22_FaultPolyParameters implements LogicTreeNode {

    protected double bufferSize = 12;
    protected double minBufferSize = 3;

    public NZSHM22_FaultPolyParameters(){

    }

    @Override
    public String getName() {
        return "NZSHM22_FaultPolyParameters";
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

    public double getBufferSize() {
        return bufferSize;
    }

    public void setBufferSize(double bufferSize) {
        this.bufferSize = bufferSize;
    }

    public double getMinBufferSize() {
        return minBufferSize;
    }

    public void setMinBufferSize(double minBufferSize) {
        this.minBufferSize = minBufferSize;
    }

    public static class Adapter extends TypeAdapter<NZSHM22_FaultPolyParameters>{

        final static String BUFFER_SIZE = "bufferSize";
        final static String MIN_BUFFER = "minBufferSize";

        @Override
        public void write(JsonWriter out, NZSHM22_FaultPolyParameters value) throws IOException {
            out.beginObject();
            out.name(BUFFER_SIZE);
            out.value(value.getBufferSize());
            out.name(MIN_BUFFER);
            out.value(value.getMinBufferSize());
            out.endObject();
        }

        @Override
        public NZSHM22_FaultPolyParameters read(JsonReader in) throws IOException {
            NZSHM22_FaultPolyParameters parameters = new NZSHM22_FaultPolyParameters();
            in.beginObject();
            while(in.hasNext()){
                switch(in.nextName()){
                    case BUFFER_SIZE:
                        parameters.setBufferSize(in.nextDouble());
                        break;
                    case MIN_BUFFER:
                        parameters.setMinBufferSize(in.nextDouble());
                        break;
                }
            }
            in.endObject();
            return parameters;
        }
    }

    public static class Level extends LogicTreeLevel.AdapterBackedLevel {
        public Level() {
            super("NZSHM22_FaultPolyParameters",
                    "NZSHM22_FaultPolyParameters",
                    NZSHM22_FaultPolyParameters.class);
        }
    }
}

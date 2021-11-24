package nz.cri.gns.NZSHM22.opensha.enumTreeBranches;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import nz.cri.gns.NZSHM22.opensha.calc.SimplifiedScalingRelationship;
import nz.cri.gns.NZSHM22.opensha.calc.Stirling2021SimplifiedScalingRelationship;
import org.opensha.commons.logicTree.JsonAdapterHelper;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.faultSysSolution.RupSetScalingRelationship;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;

import java.io.IOException;

@JsonAdapter(NZSHM22_ScalingRelationshipNode.Adapter.class)
public class NZSHM22_ScalingRelationshipNode implements RupSetScalingRelationship, LogicTreeNode {

    RupSetScalingRelationship scale;

    public NZSHM22_ScalingRelationshipNode() {
    }

    public NZSHM22_ScalingRelationshipNode(RupSetScalingRelationship scale) {
        this.scale = scale;
    }

    public void setScalingRelationship(RupSetScalingRelationship scale) {
        this.scale = scale;
    }

    public RupSetScalingRelationship getScalingRelationship(){
        return scale;
    }

    public static RupSetScalingRelationship createRelationShip(String name) {
        try {
            return ScalingRelationships.valueOf(name);
        }catch( IllegalArgumentException x){
            switch (name){
                case "SMPL_NZ_INT_UP":
                    return new Stirling2021SimplifiedScalingRelationship("interface", "upper");
                case "SMPL_NZ_INT_MN":
                    return new Stirling2021SimplifiedScalingRelationship("interface", "mean");
                case "SMPL_NZ_INT_LW":
                    return new Stirling2021SimplifiedScalingRelationship("interface", "lower");
                case "SMPL_NZ_CRU_UP":
                    return new Stirling2021SimplifiedScalingRelationship(0, "crustal", "upper");
                case "SMPL_NZ_CRU_MN":
                    return new Stirling2021SimplifiedScalingRelationship(0, "crustal", "mean");
                case "SMPL_NZ_CRU_LW":
                    return new Stirling2021SimplifiedScalingRelationship(0, "crustal", "lower");
                case "Stirling_2021_SimplifiedNZ":
                    return new Stirling2021SimplifiedScalingRelationship();
                case "SimplifiedScalingRelationship":
                    return new SimplifiedScalingRelationship();
            }
        }
        throw new IllegalArgumentException("Unknown scaling relationship " + name);
    }

    @Override
    public String getName() {
        return "NZSHM22_ScalingRelationship";
    }

    @Override
    public double getAveSlip(double area, double length, double origWidth, double aveRake) {
        return scale.getAveSlip(area, length, origWidth, aveRake);
    }

    @Override
    public double getMag(double area, double origWidth, double aveRake) {
        return scale.getMag(area, origWidth, aveRake);
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

    @Override
    public boolean equals(Object o) {
        if (o instanceof NZSHM22_ScalingRelationshipNode) {
            NZSHM22_ScalingRelationshipNode other = (NZSHM22_ScalingRelationshipNode) o;
            return scale.equals(other.scale);
        } else {
            return false;
        }
    }

    public static class Adapter extends TypeAdapter<NZSHM22_ScalingRelationshipNode> {

        @Override
        public void write(JsonWriter out, NZSHM22_ScalingRelationshipNode value) throws IOException {
            out.beginObject();
            if (value.scale instanceof ScalingRelationships) {
                out.name("u3Scale");
                out.value(((ScalingRelationships) value.scale).name());
            } else {
                out.name("scale");
                JsonAdapterHelper.writeAdapterValue(out, value.scale);
            }
            out.endObject();
        }

        @Override
        public NZSHM22_ScalingRelationshipNode read(JsonReader in) throws IOException {
            NZSHM22_ScalingRelationshipNode result = new NZSHM22_ScalingRelationshipNode();
            in.beginObject();
            while (in.hasNext()) {
                switch (in.nextName()) {
                    case "u3Scale":
                        result.setScalingRelationship(createRelationShip(in.nextString()));
                        break;
                    case "scale":
                        result.setScalingRelationship((RupSetScalingRelationship) JsonAdapterHelper.readAdapterValue(in));
                        break;
                }
            }
            in.endObject();

            return result;
        }
    }

    public static class Level extends LogicTreeLevel.AdapterBackedLevel {
        public Level() {
            super("NZSHM22_ScalingRelationship",
                    "NZSHM22_ScalingRelationship",
                    NZSHM22_ScalingRelationshipNode.class);
        }
    }
}

package nz.cri.gns.NZSHM22.opensha.enumTreeBranches;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import nz.cri.gns.NZSHM22.opensha.calc.SimplifiedScalingRelationship;
import nz.cri.gns.NZSHM22.opensha.calc.Stirling2021SimplifiedScalingRelationship;
import nz.cri.gns.NZSHM22.opensha.calc.TMG2017CruScalingRelationship;
import nz.cri.gns.NZSHM22.opensha.calc.TMG2017SubScalingRelationship;
import org.opensha.commons.logicTree.JsonAdapterHelper;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.sha.earthquake.faultSysSolution.RupSetScalingRelationship;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;

@JsonAdapter(NZSHM22_ScalingRelationshipNode.Adapter.class)
public class NZSHM22_ScalingRelationshipNode implements RupSetScalingRelationship {

    RupSetScalingRelationship scale;
    boolean recalc = false;

    public NZSHM22_ScalingRelationshipNode() {}

    public NZSHM22_ScalingRelationshipNode(RupSetScalingRelationship scale) {
        this.scale = scale;
    }

    public void setScalingRelationship(RupSetScalingRelationship scale) {
        this.scale = scale;
    }

    public RupSetScalingRelationship getScalingRelationship() {
        return scale;
    }

    public void setRecalc(boolean recalc) {
        this.recalc = recalc;
    }

    public boolean getReCalc() {
        return recalc;
    }

    public static RupSetScalingRelationship createRelationShip(String name) {
        switch (name) {
            case "TMG_CRU_2017":
                return new TMG2017CruScalingRelationship();
            case "TMG_SUB_2017":
                return new TMG2017SubScalingRelationship();
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
        return ScalingRelationships.valueOf(name);
    }

    public FaultRegime getRegime() {
        if (scale instanceof SimplifiedScalingRelationship) {
            if (((SimplifiedScalingRelationship) scale).getRegime().equals("CRUSTAL")) {
                return FaultRegime.CRUSTAL;
            } else {
                return FaultRegime.SUBDUCTION;
            }
        } else if (scale instanceof Stirling2021SimplifiedScalingRelationship) {
            if (((Stirling2021SimplifiedScalingRelationship) scale).getRegime().equals("CRUSTAL")) {
                return FaultRegime.CRUSTAL;
            } else {
                return FaultRegime.SUBDUCTION;
            }
        } else if (scale == ScalingRelationships.TMG_SUB_2017) {
            return FaultRegime.SUBDUCTION;
        } else {
            return FaultRegime.CRUSTAL;
        }
    }

    @Override
    public String getName() {
        return "NZSHM22_ScalingRelationship";
    }

    @Override
    public double getAveSlip(
            double area, double length, double width, double origWidth, double aveRake) {
        return scale.getAveSlip(area, length, width, origWidth, aveRake);
    }

    @Override
    public double getMag(
            double area, double length, double width, double origWidth, double aveRake) {
        return scale.getMag(area, length, width, origWidth, aveRake);
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
        public void write(JsonWriter out, NZSHM22_ScalingRelationshipNode value)
                throws IOException {
            out.beginObject();
            if (value.scale instanceof ScalingRelationships) {
                out.name("u3Scale");
                out.value(((ScalingRelationships) value.scale).name());
            } else if (value.scale instanceof TMG2017CruScalingRelationship
                    || value.scale instanceof TMG2017SubScalingRelationship) {
                out.name("u3Scale");
                out.value(value.scale.getShortName());
            } else {
                out.name("scale");
                JsonAdapterHelper.writeAdapterValue(out, value.scale);
            }
            out.name("recalc");
            out.value(value.recalc);
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
                        result.setScalingRelationship(
                                (RupSetScalingRelationship) JsonAdapterHelper.readAdapterValue(in));
                        break;
                    case "recalc":
                        result.setRecalc(in.nextBoolean());
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
                    "NZSHM22_ScalingRelationship",
                    "NZSHM22_ScalingRelationship",
                    NZSHM22_ScalingRelationshipNode.class);
        }
    }
}

package nz.cri.gns.NZSHM22.opensha.calc;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import org.opensha.commons.calc.FaultMomentCalc;
import org.opensha.commons.eq.MagUtils;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.sha.earthquake.faultSysSolution.RupSetScalingRelationship;

@JsonAdapter(SimplifiedScalingRelationship.Adapter.class)
public class SimplifiedScalingRelationship implements RupSetScalingRelationship {

    SimplifiedNZ_MagAreaRel magAreaRel;

    public SimplifiedScalingRelationship() {}

    public void setupSubduction(double cValue) {
        magAreaRel = SimplifiedNZ_MagAreaRel.forSubduction(cValue);
    }

    public void setupCrustal(double dipSlipCValue, double strikeSlipCValue) {
        magAreaRel = SimplifiedNZ_MagAreaRel.forCrustal(dipSlipCValue, strikeSlipCValue);
    }

    public void setRake(double rake) {
        magAreaRel.setRake(rake);
    }

    public String getRegime() {
        return magAreaRel.getRegime();
    }

    @Override
    public double getAveSlip(
            double area, double length, double width, double origWidth, double aveRake) {
        double mag = magAreaRel.getMedianMag(area * 1e-6, aveRake);
        double moment = MagUtils.magToMoment(mag);
        return FaultMomentCalc.getSlip(area, moment);
    }

    @Override
    public double getMag(
            double area, double length, double width, double origWidth, double aveRake) {
        return magAreaRel.getMedianMag(area * 1e-6, aveRake);
    }

    @Override
    public String getName() {
        return "Stirling_2021_SimplifiedNZ";
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof SimplifiedScalingRelationship) {
            SimplifiedScalingRelationship other = (SimplifiedScalingRelationship) o;
            return magAreaRel.equals(other.magAreaRel);
        }
        return false;
    }

    @Override
    public String getShortName() {
        return null;
    }

    @Override
    public double getNodeWeight(LogicTreeBranch<?> fullBranch) {
        return 0;
    }

    @Override
    public String getFilePrefix() {
        return null;
    }

    public static class Adapter extends TypeAdapter<SimplifiedScalingRelationship> {
        TypeAdapter<SimplifiedNZ_MagAreaRel> magAreaRelAdapter =
                new SimplifiedNZ_MagAreaRel.Adapter();

        @Override
        public void write(JsonWriter out, SimplifiedScalingRelationship value) throws IOException {
            magAreaRelAdapter.write(out, value.magAreaRel);
        }

        @Override
        public SimplifiedScalingRelationship read(JsonReader in) throws IOException {
            SimplifiedScalingRelationship result = new SimplifiedScalingRelationship();
            result.magAreaRel = magAreaRelAdapter.read(in);
            return result;
        }
    }
}

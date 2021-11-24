package nz.cri.gns.NZSHM22.opensha.calc;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.opensha.commons.calc.FaultMomentCalc;
import org.opensha.commons.eq.MagUtils;
import org.opensha.sha.earthquake.faultSysSolution.RupSetScalingRelationship;

import java.io.IOException;

@JsonAdapter(Stirling2021SimplifiedScalingRelationship.Adapter.class)
public class Stirling2021SimplifiedScalingRelationship implements RupSetScalingRelationship {

    Stirling_2021_SimplifiedNZ_MagAreaRel magAreaRel;

    public Stirling2021SimplifiedScalingRelationship() {
        magAreaRel = new Stirling_2021_SimplifiedNZ_MagAreaRel();
    }

    public Stirling2021SimplifiedScalingRelationship(String initialRegime, String initialEpistemicBound) {
        magAreaRel = new Stirling_2021_SimplifiedNZ_MagAreaRel(initialRegime, initialEpistemicBound);
    }

    public Stirling2021SimplifiedScalingRelationship(double inititalRake, String initialRegime, String initialEpistemicBound) {
        magAreaRel = new Stirling_2021_SimplifiedNZ_MagAreaRel(inititalRake, initialRegime, initialEpistemicBound);
    }

    public void setRake(double rake) {
        magAreaRel.setRake(rake);
    }

    public void setRegime(String regime) {
        magAreaRel.setRegime(regime);
    }

    public String getRegime(){
        return magAreaRel.getRegime();
    }

    public void setEpistemicBound(String bound) {
        magAreaRel.setEpistemicBound(bound);
    }

    @Override
    public double getAveSlip(double area, double length, double origWidth, double aveRake) {
        double mag = magAreaRel.getMedianMag(area * 1e-6, aveRake);
        double moment = MagUtils.magToMoment(mag);
        return FaultMomentCalc.getSlip(area, moment);
    }

    @Override
    public double getMag(double area, double origWidth, double aveRake) {
        return magAreaRel.getMedianMag(area * 1e-6, aveRake);
    }

    @Override
    public String getName() {
        return "Stirling_2021_SimplifiedNZ";
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Stirling2021SimplifiedScalingRelationship) {
            Stirling2021SimplifiedScalingRelationship other = (Stirling2021SimplifiedScalingRelationship) o;
            return magAreaRel.equals(other.magAreaRel);
        }
        return false;
    }

    public static class Adapter extends TypeAdapter<Stirling2021SimplifiedScalingRelationship> {
        TypeAdapter<Stirling_2021_SimplifiedNZ_MagAreaRel> magAreaRelAdapter = new Stirling_2021_SimplifiedNZ_MagAreaRel.Adapter();

        @Override
        public void write(JsonWriter out, Stirling2021SimplifiedScalingRelationship value) throws IOException {
            magAreaRelAdapter.write(out, value.magAreaRel);
        }

        @Override
        public Stirling2021SimplifiedScalingRelationship read(JsonReader in) throws IOException {
            Stirling2021SimplifiedScalingRelationship result = new Stirling2021SimplifiedScalingRelationship();
            result.magAreaRel = magAreaRelAdapter.read(in);
            return result;
        }
    }
}

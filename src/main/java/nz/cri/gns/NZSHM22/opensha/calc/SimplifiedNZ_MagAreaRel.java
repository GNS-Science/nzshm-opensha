package nz.cri.gns.NZSHM22.opensha.calc;

import com.google.common.base.Preconditions;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.FaultRegime;
import org.opensha.commons.calc.magScalingRelations.MagAreaRelationship;

import java.io.IOException;

@JsonAdapter(SimplifiedNZ_MagAreaRel.Adapter.class)
public class SimplifiedNZ_MagAreaRel extends MagAreaRelationship {

    public final static String NAME = "SimplifiedScalingNZNSHM_2021";
    /**
     * Regime is either CRUSTAL or INTERFACE
     */
    protected FaultRegime faultRegime = FaultRegime.CRUSTAL;
    protected double cValue = 0;
    protected double strikeSlipCValue = 0;

    protected SimplifiedNZ_MagAreaRel() {
        super();
    }

    public static SimplifiedNZ_MagAreaRel forSubduction(double cValue){
        SimplifiedNZ_MagAreaRel result = new SimplifiedNZ_MagAreaRel();
        result.setRegime(FaultRegime.SUBDUCTION);
        result.cValue = cValue;
        return result;
    }

    public static SimplifiedNZ_MagAreaRel forCrustal(double dipSlipCValue, double strikeSlipCValue){
        SimplifiedNZ_MagAreaRel result = new SimplifiedNZ_MagAreaRel();
        result.setRegime(FaultRegime.CRUSTAL);
        result.cValue = dipSlipCValue;
        result.strikeSlipCValue = strikeSlipCValue;
        return result;
    }

    /* *
     * @param regime
     */
    protected void setRegime(FaultRegime regime) {
        this.faultRegime = regime;
    }

    public String getRegime() {
        return faultRegime.name();
    }

    /**
     * Computes the median magnitude from rupture area
     *
     * @param area in km^2
     * @return median magnitude MW
     */
    public double getMedianMag(double area) {
        return getC4log10A2Mw() + Math.log(area) * lnToLog;
    }

    /**
     * Gives the standard deviation for the magnitude as a function of area for
     * previously-set rake values
     *
     * @return standard deviation
     */
    public double getMagStdDev() {
        return Double.NaN;
    }

    /**
     * Computes the median rupture area from magnitude
     *
     * @param mag - moment magnitude
     * @return median area in km^2
     */
    public double getMedianArea(double mag) {
        return Math.pow(10.0, -getC4log10A2Mw() + mag);
    }

    /**
     * Computes the standard deviation of log(area) (base-10)
     *
     * @return standard deviation
     */
    public double getAreaStdDev() {
        return Double.NaN;
    }

    public enum FaultType{
        STRIKE_SLIP,
        REVERSE_FAULTING,
        NORMAL_FAULTING;

        public static FaultType fromRake(double rake){
            Preconditions.checkArgument(!Double.isNaN(rake));
            if ((rake <= 45 && rake >= -45) || rake >= 135 || rake <= -135) {
                return STRIKE_SLIP;
            } else if (rake > 0) {
                return REVERSE_FAULTING;
            } else {
                return NORMAL_FAULTING;
            }
        }
    }

    /**
     * Mw = log10A + C
     *
     * @return C
     */
    private double getC4log10A2Mw() {
        if (faultRegime == FaultRegime.CRUSTAL && FaultType.fromRake(rake) == FaultType.STRIKE_SLIP) {
            return strikeSlipCValue;
        } else {
            return cValue;
        }
    }

//    private double getC4log10A2Mw() {
//
//        //rhat AKA cValue => perhaps, we refactor rhat as cvalue
//        Double rhat = Double.NaN;
//        if (faultRegime == CRUSTAL || faultRegime == NONE) {
//            if (faultType == NONE || epistemicBound == NONE) {
//                return Double.NaN;
//            } else if (faultType == STRIKE_SLIP && epistemicBound == MEAN) {
//                rhat = 4.0;
//            } else if (faultType == STRIKE_SLIP && epistemicBound == LOWER) {
//                rhat = 3.65;
//            } else if (faultType == STRIKE_SLIP && epistemicBound == UPPER) {
//                rhat = 4.30;
//            } else if (faultType == REVERSE_FAULTING && epistemicBound == MEAN) {
//                rhat = 4.13;
//            } else if (faultType == REVERSE_FAULTING && epistemicBound == LOWER) {
//                rhat = 3.95;
//            } else if (faultType == REVERSE_FAULTING && epistemicBound == UPPER) {
//                rhat = 4.30;
//            } else if (faultType == NORMAL_FAULTING && epistemicBound == MEAN) {
//                rhat = 4.13;
//            } else if (faultType == NORMAL_FAULTING && epistemicBound == LOWER) {
//                rhat = 3.95;
//            } else if (faultType == NORMAL_FAULTING && epistemicBound == UPPER) {
//                rhat = 4.30;
//            }
//        } else if (faultRegime == SUBDUCTION_INTERFACE) {
//            if (epistemicBound == MEAN) {
//                rhat = 3.85;
//            } else if (epistemicBound == LOWER) {
//                rhat = 3.60;
//            } else if (epistemicBound == UPPER) {
//                rhat = 4.10;
//            } else if (epistemicBound == NONE) {
//                return Double.NaN;
//            }
//        }
//        return rhat;
//    }

    /**
     * Returns the name of the object
     */
    public String getName() {
        return NAME + " " + faultRegime.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof SimplifiedNZ_MagAreaRel) {
            SimplifiedNZ_MagAreaRel other = (SimplifiedNZ_MagAreaRel) o;
            return  faultRegime == other.faultRegime &&
//                    faultType == other.faultType &&
//                    (rake == other.rake || (Double.isNaN(rake) && Double.isNaN(other.rake))) &&
                    cValue == other.cValue &&
                    strikeSlipCValue == other.strikeSlipCValue;
        }
        return false;
    }

    public static class Adapter extends TypeAdapter<SimplifiedNZ_MagAreaRel> {

        @Override
        public void write(JsonWriter out, SimplifiedNZ_MagAreaRel value) throws IOException {
            out.beginObject();
            out.name("regime");
            out.value(value.faultRegime.name());
            out.name("cValue");
            out.value(value.cValue);
            out.name("strikeSlipCValue");
            out.value(value.strikeSlipCValue);
            out.endObject();
        }

        @Override
        public SimplifiedNZ_MagAreaRel read(JsonReader in) throws IOException {
            SimplifiedNZ_MagAreaRel result = new SimplifiedNZ_MagAreaRel();
            in.beginObject();
            while (in.hasNext()) {
                String name = in.nextName();
                switch (name) {
                    case "regime":
                        result.setRegime(FaultRegime.valueOf(in.nextString()));
                        break;
                    case "cValue":
                        result.cValue = in.nextDouble();
                        break;
                    case "strikeSlipCValue":
                        result.strikeSlipCValue = in.nextDouble();
                        break;
                    default:
                        in.skipValue();
                        System.out.println("SimplifiedNZ_MagAreaRel.Adapter: discovered unknown name " + name);
                        break;
                }
            }
            in.endObject();
            return result;
        }
    }
}



package nz.cri.gns.NZSHM22.opensha.calc;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.opensha.commons.calc.magScalingRelations.MagAreaRelationship;

import java.io.IOException;

import static nz.cri.gns.NZSHM22.opensha.calc.Stirling_2021_SimplifiedNZ_FaultRegime.*;

/**
 * Implements the simplified relations as provided by Mark Stirling for the 2022 New Zealand NSHM
 *
 * @version 0.0
 */

@JsonAdapter(Stirling_2021_SimplifiedNZ_MagAreaRel.Adapter.class)
public class Stirling_2021_SimplifiedNZ_MagAreaRel extends MagAreaRelationship {

    public final static String NAME = "SimplifiedScalingNZNSHM_2021";
    /**
     * Regime is either CRUSTAL or INTERFACE
     */
    protected Stirling_2021_SimplifiedNZ_FaultRegime faultRegime = CRUSTAL;
    protected Stirling_2021_SimplifiedNZ_FaultRegime faultType = NONE;
    protected Stirling_2021_SimplifiedNZ_FaultRegime epistemicBound = MEAN;

    public Stirling_2021_SimplifiedNZ_MagAreaRel() {
        super();
    }

    public Stirling_2021_SimplifiedNZ_MagAreaRel(double initalRake, String initialEpistemicBound) {
        super();
        setRake(initalRake);
        setEpistemicBound(initialEpistemicBound);
    }

    public Stirling_2021_SimplifiedNZ_MagAreaRel(String initialRegime, String initialEpistemicBound) {
        super();
        setRegime(initialRegime);
        setEpistemicBound(initialEpistemicBound);
    }

    public Stirling_2021_SimplifiedNZ_MagAreaRel(double inititalRake, String initialRegime, String initialEpistemicBound) {
        super();
        setRake(inititalRake);
        setRegime(initialRegime);
        setEpistemicBound(initialEpistemicBound);
    }

    /* *
     * @param rake
     */
    public void setRake(double rake) {
        super.setRake(rake);
        this.faultType = Stirling_2021_SimplifiedNZ_FaultRegime.fromRake(rake);
    }

    /* *
     * @param regime
     */
    public void setRegime(String regime) {
        this.faultRegime = Stirling_2021_SimplifiedNZ_FaultRegime.fromRegime(regime);
    }

    public String getRegime() {
        return faultRegime.name();
    }

    /* *
     * @param epistemic Bound
     */
    public void setEpistemicBound(String epistemicBound) {
        this.epistemicBound = Stirling_2021_SimplifiedNZ_FaultRegime.fromEpistemicBound(epistemicBound);
    }

    public String getEpistemicBound() {
        return epistemicBound.name();
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

    /**
     * Mw = log10A + C
     *
     * @return C
     */

    private double getC4log10A2Mw() {

        //rhat AKA cValue => perhaps, we refactor rhat as cvalue
        Double rhat = Double.NaN;
        if (faultRegime == CRUSTAL || faultRegime == NONE) {
            if (faultType == NONE || epistemicBound == NONE) {
                return Double.NaN;
            } else if (faultType == STRIKE_SLIP && epistemicBound == MEAN) {
                rhat = 4.0;
            } else if (faultType == STRIKE_SLIP && epistemicBound == LOWER) {
                rhat = 3.65;
            } else if (faultType == STRIKE_SLIP && epistemicBound == UPPER) {
                rhat = 4.30;
            } else if (faultType == REVERSE_FAULTING && epistemicBound == MEAN) {
                rhat = 4.13;
            } else if (faultType == REVERSE_FAULTING && epistemicBound == LOWER) {
                rhat = 3.95;
            } else if (faultType == REVERSE_FAULTING && epistemicBound == UPPER) {
                rhat = 4.30;
            } else if (faultType == NORMAL_FAULTING && epistemicBound == MEAN) {
                rhat = 4.13;
            } else if (faultType == NORMAL_FAULTING && epistemicBound == LOWER) {
                rhat = 3.95;
            } else if (faultType == NORMAL_FAULTING && epistemicBound == UPPER) {
                rhat = 4.30;
            }
        } else if (faultRegime == SUBDUCTION_INTERFACE) {
            if (epistemicBound == MEAN) {
                rhat = 3.85;
            } else if (epistemicBound == LOWER) {
                rhat = 3.60;
            } else if (epistemicBound == UPPER) {
                rhat = 4.10;
            } else if (epistemicBound == NONE) {
                return Double.NaN;
            }
        }
        return rhat;
    }

    /**
     * Returns the name of the object
     */
    public String getName() {

        return NAME + " " + faultType.toString() + " " + faultRegime.toString() + " " + epistemicBound.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Stirling_2021_SimplifiedNZ_MagAreaRel) {
            Stirling_2021_SimplifiedNZ_MagAreaRel other = (Stirling_2021_SimplifiedNZ_MagAreaRel) o;
            return epistemicBound == other.epistemicBound &&
                    faultRegime == other.faultRegime &&
                    faultType == other.faultType &&
                    rake == other.rake;
        }
        return false;
    }

    public static class Adapter extends TypeAdapter<Stirling_2021_SimplifiedNZ_MagAreaRel> {

        @Override
        public void write(JsonWriter out, Stirling_2021_SimplifiedNZ_MagAreaRel value) throws IOException {
            out.beginObject();
            out.name("type");
            out.value(value.faultType.name());
            out.name("regime");
            out.value(value.faultRegime.name());
            out.name("epistemicBound");
            out.value(value.epistemicBound.name());
            out.name("rake");
            out.value(value.rake);
            out.endObject();
        }

        @Override
        public Stirling_2021_SimplifiedNZ_MagAreaRel read(JsonReader in) throws IOException {
            Stirling_2021_SimplifiedNZ_MagAreaRel result = new Stirling_2021_SimplifiedNZ_MagAreaRel();
            in.beginObject();
            while (in.hasNext()) {
                switch (in.nextName()) {
                    case "type":
                        result.faultType = Stirling_2021_SimplifiedNZ_FaultRegime.valueOf(in.nextString());
                        break;
                    case "regime":
                        result.faultRegime = Stirling_2021_SimplifiedNZ_FaultRegime.valueOf(in.nextString());
                        break;
                    case "epistemicBound":
                        result.epistemicBound = Stirling_2021_SimplifiedNZ_FaultRegime.valueOf(in.nextString());
                        break;
                    case "rake":
                        result.rake = in.nextDouble();
                        break;
                }
            }
            in.endObject();
            return result;
        }
    }
}



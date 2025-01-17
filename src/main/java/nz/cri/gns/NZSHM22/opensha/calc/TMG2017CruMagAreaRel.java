package nz.cri.gns.NZSHM22.opensha.calc;

import static nz.cri.gns.NZSHM22.opensha.calc.TMG2017FaultingType.*;

import org.opensha.commons.calc.magScalingRelations.MagAreaRelationship;

/**
 * <b>Title:</b>TMG2017_MagAreaRel<br>
 * <b>Description:</b>
 *
 * <p>This implements CRUSTAL faults specific magnitude versus rupture area relations of Thingbaijam
 * K.K.S., P.M. Mai and K. Goda 2017, Bull. Seism. Soc. Am., 107, 2225–2246.
 *
 * <p>
 *
 * <p>We consider rake to differentiate different broad faulting-types: strike-slip,
 * reverse-faulting and normal-faulting. The classification is as follows: Strike-slip: Rake angles
 * within (or equals) 45 to -45 degrees and 135 to -135 degrees Reverse-faulting: Rake angles within
 * 45 to 135 Normal-faulting: Rake angles within -45 to -135.
 *
 * <p>Notes: [1] Valid rake is in the range -180 to 180 degrees. [2] The standard deviation for area
 * as a function of mag is given for log(area) (base-10) not area.
 *
 * <p>Also see: https://github.com/thingbaijam/sceqsrc
 *
 * @version 0.0
 */
public class TMG2017CruMagAreaRel extends MagAreaRelationship {

    static final String C = "TMG2017CruMagAreaRel";
    public static final String NAME = "Thingbaijam et al.(2017)";

    protected TMG2017FaultingType faultingType = NONE;

    public TMG2017CruMagAreaRel() {
        super();
    }

    public TMG2017CruMagAreaRel(double initalRake) {
        super();
        setRake(initalRake);
    }

    /**
     * Computes the median magnitude from rupture area for previously set rake values.
     *
     * @param area in km
     * @return median magnitude MW
     */
    public double getMedianMag(double area) {

        if (NONE == faultingType) {
            return Double.NaN;
        } else if (STRIKE_SLIP == faultingType) {
            return 3.701 + 1.062 * Math.log(area) * lnToLog;
        } else if (REVERSE_FAULTING == faultingType) {
            return 4.158 + 0.953 * Math.log(area) * lnToLog;
        } else {
            return 3.157 + 1.238 * Math.log(area) * lnToLog;
        }
    }

    /**
     * Gives the standard deviation for the magnitude as a function of area for previously-set rake
     * values
     *
     * @return standard deviation
     */
    public double getMagStdDev() {
        if (NONE == faultingType) {
            return Double.NaN;
        } else if (STRIKE_SLIP == faultingType) {
            return 0.184;
        } else if (REVERSE_FAULTING == faultingType) {
            return 0.121;
        } else {
            return 0.181;
        }
    }

    /**
     * Computes the median rupture area from magnitude (for the previously set rake values).
     *
     * @param mag - moment magnitude
     * @return median area in km
     */
    public double getMedianArea(double mag) {
        if (NONE == faultingType) {
            return Double.NaN;
        } else if (STRIKE_SLIP == faultingType) {
            return Math.pow(10.0, -3.486 + 0.942 * mag);
        } else if (REVERSE_FAULTING == faultingType) {
            return Math.pow(10.0, -4.362 + 1.049 * mag);
        } else {
            return Math.pow(10.0, -2.551 + 0.808 * mag);
        }
    }

    /**
     * Computes the standard deviation of log(area) (base-10) from magnitude (for the previously set
     * rake values)
     *
     * @return standard deviation
     */
    public double getAreaStdDev() {
        return getMagStdDev();
    }

    /**
     * Sets the rake.
     *
     * @param rake
     */
    public void setRake(double rake) {
        super.setRake(rake);
        this.faultingType = TMG2017FaultingType.fromRake(rake);
    }

    /** Returns the name of the object */
    public String getName() {
        String type;
        if (NONE == faultingType) {
            type = "InvalidRake";
        } else if (STRIKE_SLIP == faultingType) {
            type = "Strike-Slip";
        } else if (REVERSE_FAULTING == faultingType) {
            type = "Reverse-Faulting";
        } else {
            type = "Normal-Faulting";
        }
        return NAME + " for crustal " + type + " events";
    }
}

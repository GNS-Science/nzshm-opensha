package nz.cri.gns.NZSHM22.opensha.inversion.joint.scaling;

import org.opensha.commons.calc.FaultMomentCalc;
import org.opensha.commons.eq.MagUtils;

public interface JointScalingRelationship {

    /**
     * This returns the slip (m) for the given rupture area (m-sq) or rupture length (m)
     *
     * @param area (m-sq)
     * @param aveRake average rake of this rupture
     * @return
     */
    default double getAveSlip(double crustalArea, double subductionArea, double aveRake) {
        double mag = getMag(crustalArea, subductionArea, aveRake);
        double moment = MagUtils.magToMoment(mag);
        return FaultMomentCalc.getSlip(crustalArea + subductionArea, moment);
    }

    /**
     * This returns the magnitude for the given rupture area (m-sq) and width (m)
     *
     * @param area (m-sq)
     * @param aveRake average rake of this rupture
     * @return
     */
    double getMag(double crustalArea, double subductionArea, double aveRake);
}

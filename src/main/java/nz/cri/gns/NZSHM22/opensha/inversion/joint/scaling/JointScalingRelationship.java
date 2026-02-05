package nz.cri.gns.NZSHM22.opensha.inversion.joint.scaling;

import org.opensha.commons.calc.FaultMomentCalc;
import org.opensha.commons.eq.MagUtils;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.sha.earthquake.faultSysSolution.RupSetScalingRelationship;

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

    public default RupSetScalingRelationship toRupSetScalingRelationship(boolean isCrustal) {
        return new OldSchoolScaling(this, isCrustal);
    }

    public static class OldSchoolScaling implements RupSetScalingRelationship {

        JointScalingRelationship original;
        boolean isCrustal;

        public OldSchoolScaling(JointScalingRelationship original, boolean isCrustal) {
            this.original = original;
            this.isCrustal = isCrustal;
        }

        @Override
        public double getAveSlip(
                double area, double length, double width, double origWidth, double aveRake) {
            if (isCrustal) {
                return original.getAveSlip(area, 0, aveRake);
            }
            return original.getAveSlip(0, area, aveRake);
        }

        @Override
        public double getMag(
                double area, double length, double width, double origWidth, double aveRake) {
            if (isCrustal) {
                return original.getMag(area, 0, aveRake);
            }
            return original.getMag(0, area, aveRake);
        }

        @Override
        public double getNodeWeight(LogicTreeBranch<?> fullBranch) {
            return 0;
        }

        @Override
        public String getFilePrefix() {
            return "";
        }

        @Override
        public String getShortName() {
            return "";
        }

        @Override
        public String getName() {
            return "";
        }
    }
}

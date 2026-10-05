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

    /**
     * Creates a RupSetScalingRelationship for either crustal or subduction.
     *
     * @param isCrustal whether to calculate the crustal or the subduction component
     * @return a RupSetScalingRelationship
     */
    public default RupSetScalingRelationship toRupSetScalingRelationship(boolean isCrustal) {
        return new OldSchoolScaling(this, isCrustal);
    }

    /**
     * Calculates moment magnitude for a partition of a joint rupture if the slip is the same for
     * the joint rupture and its partition ruptures.
     *
     * <p>With equal slip, moment is proportional to area, so the partition moment is {@code
     * partitionArea / area} of the joint moment. Since {@code Mw = 2/3 log10(M0) + c}, the
     * partition magnitude is {@code magnitude + 2/3 log10(partitionArea / area)}. A partition that
     * covers the whole rupture has the rupture's magnitude, and the partition magnitude approaches
     * it as the partition's share of the area approaches 1.
     *
     * @param partitionArea the area of the partition
     * @param area the area of the joint rupture
     * @param magnitude the magnitude of the joint rupture
     * @return moment magnitude of the partition rupture
     */
    static double partitionMagnitude(double partitionArea, double area, double magnitude) {
        return Math.log10(partitionArea / area) / 1.5 + magnitude;
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
            return "OldSchoolScaling " + (isCrustal ? "crustal" : "subduction");
        }

        @Override
        public String getName() {
            return "OldSchoolScaling " + (isCrustal ? "crustal" : "subduction");
        }
    }
}

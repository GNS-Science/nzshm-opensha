package nz.cri.gns.NZSHM22.opensha.inversion.joint.scaling;

import nz.cri.gns.NZSHM22.opensha.calc.SimplifiedScalingRelationship;

public class SimplifiedJointScalingRelationship implements JointScalingRelationship {

    SimplifiedScalingRelationship simplified;

    public SimplifiedJointScalingRelationship(SimplifiedScalingRelationship simplified) {
        this.simplified = simplified;
    }

    @Override
    public double getMag(double crustalArea, double subductionArea, double aveRake) {
        return simplified.getMag(crustalArea + subductionArea, 0, 0, 0, aveRake);
    }
}

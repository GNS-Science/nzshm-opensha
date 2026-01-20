package nz.cri.gns.NZSHM22.opensha.inversion.joint.scaling;

/**
 * Based on Bruce Shaw's suggestion. See
 * https://github.com/voj/science-playground/blob/f9e56b3efd24b5150e4e6ee493b1744ce8d8eb50/WORKDIR/magnitude.ipynb
 */
public class EstimatedJointScalingRelationship implements JointScalingRelationship {

    double crustal;
    double subduction;

    public EstimatedJointScalingRelationship() {
        crustal = 1e-6 * Math.pow(10, 4.2);
        subduction = 1e-6 * Math.pow(10, 4.0);
    }

    @Override
    public double getMag(double crustalArea, double subductionArea, double aveRake) {
        return Math.log10(subductionArea * subduction + crustalArea * crustal);
    }
}

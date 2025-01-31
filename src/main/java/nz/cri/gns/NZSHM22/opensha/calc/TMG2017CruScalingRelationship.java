package nz.cri.gns.NZSHM22.opensha.calc;

import org.opensha.commons.calc.FaultMomentCalc;
import org.opensha.commons.eq.MagUtils;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.sha.earthquake.faultSysSolution.RupSetScalingRelationship;

public class TMG2017CruScalingRelationship implements RupSetScalingRelationship {

    private final TMG2017CruMagAreaRel tmg_cru_magArea = new TMG2017CruMagAreaRel(0);

    // units of the input dimensions are in m or m^2
    public double getAveSlip(
            double area, double length, double width, double origWidth, double aveRake) {
        tmg_cru_magArea.setRake(aveRake);
        double mag = tmg_cru_magArea.getMedianMag(area * 1e-6);
        double moment = MagUtils.magToMoment(mag);
        return FaultMomentCalc.getSlip(area, moment);
    }

    public double getMag(
            double area, double length, double width, double origWidth, double aveRake) {
        tmg_cru_magArea.setRake(aveRake);
        return tmg_cru_magArea.getMedianMag(area * 1e-6);
    }

    public double getArea(double mag, double origWidth) {
        return tmg_cru_magArea.getMedianArea(mag) * 1e6;
    }

    @Override
    public String getName() {
        return "Thingbaijam et al.(2017) Crustal";
    }

    @Override
    public String getShortName() {
        return "TMG_CRU_2017";
    }

    @Override
    public double getNodeWeight(LogicTreeBranch<?> fullBranch) {
        return 0;
    }

    @Override
    public String getFilePrefix() {
        return null;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TMG2017CruScalingRelationship;
    }
}

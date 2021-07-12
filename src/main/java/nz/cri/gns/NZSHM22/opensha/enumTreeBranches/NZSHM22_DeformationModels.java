package nz.cri.gns.NZSHM22.opensha.enumTreeBranches;

import org.opensha.sha.faultSurface.FaultSection;
import scratch.UCERF3.enumTreeBranches.InversionModels;
import scratch.UCERF3.logicTree.LogicTreeBranchNode;

public enum NZSHM22_DeformationModels implements LogicTreeBranchNode<NZSHM22_DeformationModels> {

    GLOBAL_SLIP_RATE_10MM(10);

    protected double globalSlipRate;

    NZSHM22_DeformationModels(double globalSlipRate){
        this.globalSlipRate = globalSlipRate;
    }

    public void applyTo(FaultSection section){
        section.setAveSlipRate(globalSlipRate);
    }

    public boolean isApplicableTo(NZSHM22_FaultModels faultModel){
        return true;
    }

    @Override
    public String getName() {
        return name();
    }

    @Override
    public String getShortName() {
        return getName();
    }

    @Override
    public double getRelativeWeight(InversionModels im) {
        return 1.0;
    }

    @Override
    public String encodeChoiceString() {
        return getShortName();
    }

    @Override
    public String getBranchLevelName() {
        return "Deformation Model";
    }
}

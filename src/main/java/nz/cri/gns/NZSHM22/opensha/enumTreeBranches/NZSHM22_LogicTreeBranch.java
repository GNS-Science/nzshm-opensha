package nz.cri.gns.NZSHM22.opensha.enumTreeBranches;

import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;
import scratch.UCERF3.logicTree.U3LogicTreeBranch;

/**
 * temporary utility class until we've figured out how we want to do this
 */

public class NZSHM22_LogicTreeBranch {

    public static U3LogicTreeBranch crustal() {
        U3LogicTreeBranch branch = U3LogicTreeBranch.DEFAULT;

        branch.clearValue(ScalingRelationships.class);
        branch.setValue(ScalingRelationships.TMG_CRU_2017);
        branch.clearValue(SlipAlongRuptureModels.class);
        branch.setValue(SlipAlongRuptureModels.UNIFORM);

        return branch;
    }

    public static U3LogicTreeBranch subduction() {
        U3LogicTreeBranch branch = U3LogicTreeBranch.DEFAULT;

        branch.clearValue(ScalingRelationships.class);
        branch.setValue(ScalingRelationships.TMG_SUB_2017);
        branch.clearValue(SlipAlongRuptureModels.class);
        branch.setValue(SlipAlongRuptureModels.UNIFORM);

        return branch;
    }
}

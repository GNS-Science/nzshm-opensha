package nz.cri.gns.NZSHM22.opensha.enumTreeBranches;

import com.google.common.base.Preconditions;
import nz.cri.gns.NZSHM22.opensha.calc.SimplifiedScalingRelationship;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.util.modules.ModuleContainer;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;
import scratch.UCERF3.logicTree.U3LogicTreeBranch;

import java.util.ArrayList;
import java.util.List;

public class NZSHM22_LogicTreeBranch extends LogicTreeBranch<LogicTreeNode> {

    U3LogicTreeBranch u3Branch = null;

    protected static List<LogicTreeLevel<? extends LogicTreeNode>> getLevels() {
        List<LogicTreeLevel<? extends LogicTreeNode>> levels = new ArrayList<>();
        levels.add(FaultRegime.level());
        levels.add(NZSHM22_SpatialSeisPDF.level());
        levels.add(LogicTreeLevel.forEnumUnchecked(SlipAlongRuptureModels.class, SlipAlongRuptureModels.UNIFORM.getBranchLevelName(), SlipAlongRuptureModels.UNIFORM.getShortBranchLevelName()));
        levels.add(new NZSHM22_ScalingRelationshipNode.Level());
        levels.add(NZSHM22_DeformationModel.level());
        return levels;
    }

    protected NZSHM22_LogicTreeBranch() {
        super(getLevels());
        setValue(NZSHM22_SpatialSeisPDF.NZSHM22_1346);
        setValue(SlipAlongRuptureModels.UNIFORM);
    }

    /**
     * Returns a LogicTreeBranch with the default crustal configuration.
     *
     * @return
     */
    public static NZSHM22_LogicTreeBranch crustal() {
        NZSHM22_LogicTreeBranch branch = new NZSHM22_LogicTreeBranch();
        branch.setValue(FaultRegime.CRUSTAL);
        NZSHM22_ScalingRelationshipNode scalingRelationship = new NZSHM22_ScalingRelationshipNode();
        SimplifiedScalingRelationship scaling = new SimplifiedScalingRelationship();
        scaling.setupCrustal(4.0, 4.0);
        scalingRelationship.setScalingRelationship(scaling);
        branch.setValue(scalingRelationship);
        return branch;
    }

    /**
     * Returns a LogicTreeBranch with the default subduction configuration.
     *
     * @return
     */
    public static NZSHM22_LogicTreeBranch subduction() {
        NZSHM22_LogicTreeBranch branch = new NZSHM22_LogicTreeBranch();
        branch.setValue(FaultRegime.SUBDUCTION);
        NZSHM22_ScalingRelationshipNode scalingRelationship = new NZSHM22_ScalingRelationshipNode();
        SimplifiedScalingRelationship scaling = new SimplifiedScalingRelationship();
        scaling.setupSubduction(4.0);
        scalingRelationship.setScalingRelationship(scaling);
        branch.setValue(scalingRelationship);
        return branch;
    }

    /**
     * Returns a NZSHM22_LogicTreeBranch based on the data in the ModuleContainer.
     *
     * @param container
     * @return
     */
    public static NZSHM22_LogicTreeBranch crustalFromModuleContainer(ModuleContainer container) {
        LogicTreeBranch origBranch = (LogicTreeBranch) container.getModule(LogicTreeBranch.class);
        if (origBranch instanceof NZSHM22_LogicTreeBranch) {
            NZSHM22_LogicTreeBranch result = (NZSHM22_LogicTreeBranch) origBranch;
            Preconditions.checkArgument(result.getValue(FaultRegime.class) == FaultRegime.CRUSTAL);
            return result;
        } else if (origBranch instanceof U3LogicTreeBranch) {
            NZSHM22_LogicTreeBranch result = NZSHM22_LogicTreeBranch.crustal();
            result.setU3Branch((U3LogicTreeBranch) origBranch);
            return result;
        } else {
            return NZSHM22_LogicTreeBranch.crustal();
        }
    }

    /**
     * Returns a NZSHM22_LogicTreeBranch based on the data in the ModuleContainer.
     *
     * @param container
     * @return
     */
    public static NZSHM22_LogicTreeBranch subductionFromModuleContainer(ModuleContainer container) {
        LogicTreeBranch origBranch = (LogicTreeBranch) container.getModule(LogicTreeBranch.class);
        if (origBranch instanceof NZSHM22_LogicTreeBranch) {
            NZSHM22_LogicTreeBranch result = (NZSHM22_LogicTreeBranch) origBranch;
            Preconditions.checkArgument(result.getValue(FaultRegime.class) == FaultRegime.SUBDUCTION);
            return result;
        } else if (origBranch instanceof U3LogicTreeBranch) {
            NZSHM22_LogicTreeBranch result = NZSHM22_LogicTreeBranch.subduction();
            result.setU3Branch((U3LogicTreeBranch) origBranch);
            return result;
        } else {
            return NZSHM22_LogicTreeBranch.crustal();
        }
    }

    public void setU3Branch(U3LogicTreeBranch branch) {
        this.u3Branch = branch;

        ScalingRelationships u3Scale = branch.getValue(ScalingRelationships.class);
        if(u3Scale != null){
            clearValue(NZSHM22_ScalingRelationshipNode.class);
            setValue(new NZSHM22_ScalingRelationshipNode(u3Scale));
        }

        SlipAlongRuptureModels slipAlongRuptureModel = branch.getValue(SlipAlongRuptureModels.class);
        if(slipAlongRuptureModel != null){
            clearValue(SlipAlongRuptureModels.class);
            setValue(slipAlongRuptureModel);
        }
    }

    /**
     * Returns a U3LogicTreeBranch configured as much as possible as this NZSHM22_LogicTreeBranch
     *
     * @return
     */
    public U3LogicTreeBranch getU3Branch() {
        if (u3Branch != null) {
            return u3Branch;
        } else {
            U3LogicTreeBranch branch = U3LogicTreeBranch.DEFAULT;
            branch.clearValue(ScalingRelationships.class);
            NZSHM22_ScalingRelationshipNode scalingRelationship = getValue(NZSHM22_ScalingRelationshipNode.class);
            if (scalingRelationship != null &&
                    scalingRelationship.getScalingRelationship() instanceof ScalingRelationships) {
                branch.setValue((ScalingRelationships) scalingRelationship.getScalingRelationship());
            }
            branch.clearValue(SlipAlongRuptureModels.class);
            branch.setValue(getValue(SlipAlongRuptureModels.class));
            return branch;
        }
    }
}

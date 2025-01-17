package nz.cri.gns.NZSHM22.opensha.enumTreeBranches;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.List;
import nz.cri.gns.NZSHM22.opensha.calc.SimplifiedScalingRelationship;
import nz.cri.gns.NZSHM22.opensha.data.region.NewZealandRegions;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.util.modules.ModuleContainer;
import scratch.UCERF3.enumTreeBranches.InversionModels;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;
import scratch.UCERF3.logicTree.U3LogicTreeBranch;

public class NZSHM22_LogicTreeBranch extends LogicTreeBranch<LogicTreeNode> {

    U3LogicTreeBranch u3Branch = null;

    protected static List<LogicTreeLevel<? extends LogicTreeNode>> createLevels() {
        List<LogicTreeLevel<? extends LogicTreeNode>> levels = new ArrayList<>();
        levels.add(
                LogicTreeLevel.forEnumUnchecked(
                        NZSHM22_FaultModels.class, "Fault Model", "Fault Model"));
        levels.add(FaultRegime.level());
        levels.add(NZSHM22_SpatialSeisPDF.level());
        levels.add(
                LogicTreeLevel.forEnumUnchecked(
                        SlipAlongRuptureModels.class,
                        SlipAlongRuptureModels.UNIFORM.getBranchLevelName(),
                        SlipAlongRuptureModels.UNIFORM.getShortBranchLevelName()));
        levels.add(
                LogicTreeLevel.forEnumUnchecked(
                        InversionModels.class,
                        InversionModels.CHAR_CONSTRAINED.getBranchLevelName(),
                        InversionModels.CHAR_CONSTRAINED.getShortBranchLevelName()));
        levels.add(new NZSHM22_ScalingRelationshipNode.Level());
        levels.add(new NZSHM22_FaultPolyParameters.Level());
        levels.add(new NZSHM22_MagBounds.Level());
        levels.add(new NZSHM22_SlipRateFactors.Level());
        levels.add(new NZSHM22_Regions.Level());
        levels.add(NZSHM22_DeformationModel.level());
        return levels;
    }

    public NZSHM22_LogicTreeBranch() {
        super(createLevels());
    }

    protected void commonInversionSettings() {
        setValue(InversionModels.CHAR_CONSTRAINED);
        setValue(NZSHM22_SpatialSeisPDF.NZSHM22_1346);
        setValue(SlipAlongRuptureModels.UNIFORM);
    }

    /**
     * Returns a LogicTreeBranch with the default crustal configuration.
     *
     * @return
     */
    public static NZSHM22_LogicTreeBranch crustalInversion() {
        NZSHM22_LogicTreeBranch branch = new NZSHM22_LogicTreeBranch();
        branch.commonInversionSettings();
        branch.setValue(FaultRegime.CRUSTAL);
        NZSHM22_ScalingRelationshipNode scalingRelationship = new NZSHM22_ScalingRelationshipNode();
        SimplifiedScalingRelationship scaling = new SimplifiedScalingRelationship();
        scaling.setupCrustal(4.0, 4.0);
        scalingRelationship.setScalingRelationship(scaling);
        branch.setValue(scalingRelationship);
        branch.setValue(
                new NZSHM22_Regions(
                        new NewZealandRegions.NZ_RECTANGLE_SANS_TVZ_GRIDDED(),
                        new NewZealandRegions.NZ_TVZ_GRIDDED()));
        return branch;
    }

    /**
     * Returns a LogicTreeBranch with the default subduction configuration.
     *
     * @return
     */
    public static NZSHM22_LogicTreeBranch subductionInversion() {
        NZSHM22_LogicTreeBranch branch = new NZSHM22_LogicTreeBranch();
        branch.commonInversionSettings();
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
        LogicTreeBranch original = (LogicTreeBranch) container.getModule(LogicTreeBranch.class);
        if (original instanceof NZSHM22_LogicTreeBranch) {
            Preconditions.checkArgument(
                    original.getValue(FaultRegime.class) == FaultRegime.CRUSTAL);
            NZSHM22_LogicTreeBranch result = NZSHM22_LogicTreeBranch.crustalInversion();
            result.copyValuesFrom(original);
            return result;
        } else if (original instanceof U3LogicTreeBranch) {
            NZSHM22_LogicTreeBranch result = NZSHM22_LogicTreeBranch.crustalInversion();
            result.setU3Branch((U3LogicTreeBranch) original);
            return result;
        } else {
            return NZSHM22_LogicTreeBranch.crustalInversion();
        }
    }

    /**
     * Returns a NZSHM22_LogicTreeBranch based on the data in the ModuleContainer.
     *
     * @param container
     * @return
     */
    public static NZSHM22_LogicTreeBranch subductionFromModuleContainer(ModuleContainer container) {
        LogicTreeBranch original = (LogicTreeBranch) container.getModule(LogicTreeBranch.class);
        if (original instanceof NZSHM22_LogicTreeBranch) {
            Preconditions.checkArgument(
                    original.getValue(FaultRegime.class) == FaultRegime.SUBDUCTION);
            NZSHM22_LogicTreeBranch result = NZSHM22_LogicTreeBranch.subductionInversion();
            result.copyValuesFrom(original);
            return result;
        } else if (original instanceof U3LogicTreeBranch) {
            NZSHM22_LogicTreeBranch result = NZSHM22_LogicTreeBranch.subductionInversion();
            result.setU3Branch((U3LogicTreeBranch) original);
            return result;
        } else {
            return NZSHM22_LogicTreeBranch.subductionInversion();
        }
    }

    public static NZSHM22_LogicTreeBranch fromContainer(ModuleContainer container) {
        NZSHM22_LogicTreeBranch original =
                (NZSHM22_LogicTreeBranch) container.getModule(NZSHM22_LogicTreeBranch.class);
        if (original != null && original.getValue(FaultRegime.class) == FaultRegime.SUBDUCTION) {
            return subductionFromModuleContainer(container);
        } else {
            return crustalFromModuleContainer(container);
        }
    }

    public void copyValuesFrom(LogicTreeBranch<LogicTreeNode> branch) {
        for (LogicTreeNode node : branch) {
            if (node != null) {
                setValue(node);
            }
        }
    }

    public void setU3Branch(U3LogicTreeBranch branch) {
        this.u3Branch = branch;

        ScalingRelationships u3Scale = branch.getValue(ScalingRelationships.class);
        if (u3Scale != null) {
            clearValue(NZSHM22_ScalingRelationshipNode.class);
            setValue(new NZSHM22_ScalingRelationshipNode(u3Scale));
        }

        SlipAlongRuptureModels slipAlongRuptureModel =
                branch.getValue(SlipAlongRuptureModels.class);
        if (slipAlongRuptureModel != null) {
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
            NZSHM22_ScalingRelationshipNode scalingRelationship =
                    getValue(NZSHM22_ScalingRelationshipNode.class);
            if (scalingRelationship != null
                    && scalingRelationship.getScalingRelationship()
                            instanceof ScalingRelationships) {
                branch.setValue(
                        (ScalingRelationships) scalingRelationship.getScalingRelationship());
            }
            branch.clearValue(SlipAlongRuptureModels.class);
            branch.setValue(getValue(SlipAlongRuptureModels.class));
            return branch;
        }
    }
}

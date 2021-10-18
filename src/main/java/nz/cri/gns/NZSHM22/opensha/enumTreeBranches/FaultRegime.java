package nz.cri.gns.NZSHM22.opensha.enumTreeBranches;

import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;

public enum FaultRegime implements LogicTreeNode {

    CRUSTAL,
    SUBDUCTION;

    @Override
    public String getName() {
        return "Fault Regime";
    }

    @Override
    public String getShortName() {
        return getName();
    }

    @Override
    public double getNodeWeight(LogicTreeBranch<?> fullBranch) {
        return 0;
    }

    @Override
    public String getFilePrefix() {
        return null;
    }

    public static LogicTreeLevel<LogicTreeNode> level(){
        return LogicTreeLevel.forEnumUnchecked(FaultRegime.class, "Fault Regime", "Fault Regime");
    }
}

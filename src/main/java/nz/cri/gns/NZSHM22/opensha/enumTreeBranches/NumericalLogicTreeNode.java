package nz.cri.gns.NZSHM22.opensha.enumTreeBranches;

import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;

public  class NumericalLogicTreeNode implements LogicTreeNode {

    @Override
    public String getName() {
        return null;
    }

    @Override
    public String getShortName() {
        return null;
    }

    @Override
    public double getNodeWeight(LogicTreeBranch<?> fullBranch) {
        return 0;
    }

    @Override
    public String getFilePrefix() {
        return null;
    }
}

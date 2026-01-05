package nz.cri.gns.NZSHM22.opensha.inversion.joint;

import com.google.common.base.Preconditions;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import nz.cri.gns.NZSHM22.opensha.calc.SimplifiedScalingRelationship;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_LogicTreeBranch;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_ScalingRelationshipNode;
import nz.cri.gns.NZSHM22.opensha.inversion.NZSHM22_InversionFaultSystemRuptSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.RupSetScalingRelationship;
import org.opensha.sha.faultSurface.FaultSection;

public class Config {

    protected String ruptureSetPath;
    protected AnnealingConfig annealing;
    protected List<PartitionConfig> partitions;

    protected String scalingRelationshipName = "SIMPLE_CRUSTAL";
    protected double scalingCValDipSlip = 4.2;
    protected double scalingCValStrikeSlip = 4.2;
    protected double scalingCVal;
    protected boolean recalcMags = false;

    protected double sansSlipRateFactor = -1;
    protected double tvzSlipRateFactor = -1;

    // hydrated values
    protected transient RupSetScalingRelationship scalingRelationship;
    protected transient FaultSystemRupSet ruptureSet;

    public Config() {
        partitions = new ArrayList<>();
        annealing = new AnnealingConfig();
    }

    public void setRuptureSet(String ruptureSetPath) {
        this.ruptureSetPath = ruptureSetPath;
    }

    public void setRuptureSet(NZSHM22_InversionFaultSystemRuptSet ruptureSet) {
        this.ruptureSet = ruptureSet;
    }

    public AnnealingConfig getAnnealingConfig() {
        return annealing;
    }

    protected void hydrateScalingRelationship() {
        if (scalingRelationshipName == null) {
            NZSHM22_LogicTreeBranch ltb = ruptureSet.getModule(NZSHM22_LogicTreeBranch.class);
            scalingRelationship = ltb.getValue(NZSHM22_ScalingRelationshipNode.class);
        } else if (scalingRelationshipName.equals("SIMPLE_CRUSTAL")) {
            SimplifiedScalingRelationship sr = new SimplifiedScalingRelationship();
            sr.setupCrustal(scalingCValDipSlip, scalingCValStrikeSlip);
            scalingRelationship = sr;
        } else if (scalingRelationshipName.equals("SIMPLE_SUBDUCTION")) {
            SimplifiedScalingRelationship sr = new SimplifiedScalingRelationship();
            sr.setupSubduction(scalingCVal);
            scalingRelationship = sr;
        } else {
            scalingRelationship =
                    NZSHM22_ScalingRelationshipNode.createRelationShip(scalingRelationshipName);
        }
    }

    protected void apply() throws IOException {

        if (ruptureSet == null && ruptureSetPath != null) {
            ruptureSet = FaultSystemRupSet.load(new File(ruptureSetPath));
        }

        hydrateScalingRelationship();

        RuptureSetSetup.setup(this);

        Preconditions.checkState(ruptureSet != null, "Rupture set not specified");
        Preconditions.checkState(!partitions.isEmpty(), "No constraint configs specified");

        for (PartitionConfig config : partitions) {
            config.init(ruptureSet);
        }

        Set<Integer> seen = new HashSet<>();
        List<Integer> doubleUps = new ArrayList<>();
        for (PartitionConfig config : partitions) {
            for (Integer sectionId : config.getSectionIds()) {
                if (seen.contains(sectionId)) {
                    doubleUps.add(sectionId);
                }
                seen.add(sectionId);
            }
        }

        Preconditions.checkState(
                doubleUps.isEmpty(), doubleUps.size() + " section id double-ups in region config");
        Preconditions.checkState(
                seen.size() == ruptureSet.getNumSections(),
                "Config constraint sections do not match rupture set sections");

        for (FaultSection section : ruptureSet.getFaultSectionDataList()) {
            Preconditions.checkState(
                    seen.contains(section.getSectionId()),
                    "Section "
                            + section.getSectionId()
                            + " is not covered by a constraint config.");
        }
    }
}

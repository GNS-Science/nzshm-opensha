package nz.cri.gns.NZSHM22.opensha.inversion.joint;

import com.google.common.base.Preconditions;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import nz.cri.gns.NZSHM22.opensha.calc.SimplifiedScalingRelationship;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_PaleoProbabilityModel;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_PaleoRates;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.scaling.EstimatedJointScalingRelationship;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.scaling.JointScalingRelationship;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.scaling.SimplifiedJointScalingRelationship;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.faultSurface.FaultSection;

public class Config {

    public String ruptureSetPath;
    public AnnealingConfig annealing;
    public List<PartitionConfig> partitions;

    public String scalingRelationshipName = "SIMPLE_CRUSTAL";
    public double scalingCValDipSlip = 4.2;
    public double scalingCValStrikeSlip = 4.2;
    public double scalingCVal;
    public boolean recalcMags = false;

    // TVZ slip
    public double sansSlipRateFactor = -1;
    public double tvzSlipRateFactor = -1;

    // paleo
    public double paleoRateConstraintWt = 0;
    public double paleoParentRateSmoothnessConstraintWeight = 0;
    public NZSHM22_PaleoRates paleoRates;
    public NZSHM22_PaleoProbabilityModel paleoProbabilityModel;
    public String extraPaleoRatesFile;

    // hydrated values
    public transient JointScalingRelationship scalingRelationship;
    public transient FaultSystemRupSet ruptureSet;

    public Config() {
        partitions = new ArrayList<>();
        annealing = new AnnealingConfig();
    }

    public void setRuptureSet(String ruptureSetPath) {
        this.ruptureSetPath = ruptureSetPath;
    }

    public void setRuptureSet(FaultSystemRupSet ruptureSet) {
        this.ruptureSet = ruptureSet;
    }

    public AnnealingConfig getAnnealingConfig() {
        return annealing;
    }

    protected void hydrateScalingRelationship() {
        // not supporting arbitrary scaling relationships in order
        // to make joint scaling relationships simpler
        //        if (scalingRelationshipName == null) {
        //            NZSHM22_LogicTreeBranch ltb =
        // ruptureSet.getModule(NZSHM22_LogicTreeBranch.class);
        //            scalingRelationship = ltb.getValue(NZSHM22_ScalingRelationshipNode.class);
        //        } else

        if (scalingRelationshipName.equals("SIMPLE_CRUSTAL")) {
            SimplifiedScalingRelationship sr = new SimplifiedScalingRelationship();
            sr.setupCrustal(scalingCValDipSlip, scalingCValStrikeSlip);
            scalingRelationship = new SimplifiedJointScalingRelationship(sr);
        } else if (scalingRelationshipName.equals("SIMPLE_SUBDUCTION")) {
            SimplifiedScalingRelationship sr = new SimplifiedScalingRelationship();
            sr.setupSubduction(scalingCVal);
            scalingRelationship = new SimplifiedJointScalingRelationship(sr);
        } else if (scalingRelationshipName.equals("JOIN_ESTIMATE")) {
            EstimatedJointScalingRelationship sr = new EstimatedJointScalingRelationship();
            scalingRelationship = sr;
        } else {
            throw new RuntimeException(
                    "Unknown or unsupported scaling relationship " + scalingRelationshipName);
        }
    }

    public void init() throws IOException {
        if (ruptureSet == null && ruptureSetPath != null) {
            ruptureSet = FaultSystemRupSet.load(new File(ruptureSetPath));
        }
        Preconditions.checkState(ruptureSet != null, "Rupture set not specified");
        Preconditions.checkState(!partitions.isEmpty(), "No partition configs specified");

        if (ruptureSet.hasModule(LogicTreeBranch.class)) {
            LogicTreeBranch ltb = ruptureSet.getModule(LogicTreeBranch.class);
            ruptureSet.removeModule(ltb);
        }

        hydrateScalingRelationship();

        for (PartitionConfig config : partitions) {
            config.init(this);
        }

        annealing.init();

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
                "Config constraint sections ("
                        + seen.size()
                        + ") do not match rupture set sections ("
                        + ruptureSet.getNumSections()
                        + ")");

        for (FaultSection section : ruptureSet.getFaultSectionDataList()) {
            Preconditions.checkState(
                    seen.contains(section.getSectionId()),
                    "Section "
                            + section.getSectionId()
                            + " is not covered by a constraint config.");
        }
    }
}

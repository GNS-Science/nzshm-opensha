package nz.cri.gns.NZSHM22.opensha.inversion.joint;

import com.google.common.base.Preconditions;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import nz.cri.gns.NZSHM22.opensha.inversion.NZSHM22_InversionFaultSystemRuptSet;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.constraints.ConstraintConfig;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.constraints.RegionPredicate;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.faultSurface.FaultSection;

public class Config {

    protected String ruptureSetPath;
    protected AnnealingConfig annealing;
    protected List<ConstraintConfig> constraints;

    // TODO: should this be on the runner instead since it's not a config?
    protected transient FaultSystemRupSet ruptureSet;

    public Config() {
        constraints = new ArrayList<>();
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

    public ConstraintConfig createCrustalConfig() {
        ConstraintConfig crustalConfig = new ConstraintConfig(RegionPredicate.CRUSTAL);
        constraints.add(crustalConfig);
        return crustalConfig;
    }

    public ConstraintConfig createSubductionConfig() {
        ConstraintConfig subductionConfig = new ConstraintConfig(RegionPredicate.SUBDUCTION);
        constraints.add(subductionConfig);
        return subductionConfig;
    }

    protected void init() throws IOException {

        if (ruptureSet == null && ruptureSetPath != null) {
            ruptureSet = FaultSystemRupSet.load(new File(ruptureSetPath));
        }

        Preconditions.checkState(ruptureSet != null, "Rupture set not specified");
        Preconditions.checkState(!constraints.isEmpty(), "No constraint configs specified");

        for (ConstraintConfig config : constraints) {
            config.init(ruptureSet);
        }

        Set<Integer> seen = new HashSet<>();
        List<Integer> doubleUps = new ArrayList<>();
        for (ConstraintConfig config : constraints) {
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

package nz.cri.gns.NZSHM22.opensha.inversion.joint;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.constraints.ConstraintConfig;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.constraints.RegionPredicate;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.faultSurface.FaultSection;

public class Config {

    protected String ruptureSetPath;
    protected AnnealingConfig annealing;
    protected List<ConstraintConfig> constraints;

    public Config() {
        constraints = new ArrayList<>();
        annealing = new AnnealingConfig();
    }

    public Config setRuptureSet(String ruptureSetPath) {
        this.ruptureSetPath = ruptureSetPath;
        return this;
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

    protected void init(FaultSystemRupSet ruptureSet) {

        if (constraints.isEmpty()) {
            System.err.println("No constraint configs");
            throw new IllegalStateException("No constraint configs");
        }

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

        if (!doubleUps.isEmpty()) {
            System.err.println("Section id double-ups in region config");
            System.err.println(doubleUps);
            throw new IllegalStateException("Section id double-ups");
        }

        if (seen.size() != ruptureSet.getNumSections()) {
            System.err.println("Config sections do not match rupture set sections");
            throw new IllegalStateException("Config section don't match rupture set.");
        }
        for (FaultSection section : ruptureSet.getFaultSectionDataList()) {
            if (!seen.contains(section.getSectionId())) {
                System.err.println(
                        "Section " + section.getSectionId() + " is not covered by a config.");
                throw new IllegalStateException(
                        "Section " + section.getSectionId() + " is not covered by a config.");
            }
        }
    }
}

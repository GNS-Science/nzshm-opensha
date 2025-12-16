package nz.cri.gns.NZSHM22.opensha.inversion.joint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.constraints.ConstraintConfig;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.constraints.RegionPredicate;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.faultSurface.FaultSection;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Config {

    protected AnnealingConfig annealingConfig;
    protected List<ConstraintConfig> constraintConfigs;
    protected transient ConstraintConfig crustalConfig;
    protected transient ConstraintConfig subductionConfig;

    public Config(){
        constraintConfigs = new ArrayList<>();
        annealingConfig = new AnnealingConfig();
    }

    public AnnealingConfig getAnnealingConfig(){
        return annealingConfig;
    }

    public ConstraintConfig getCrustalConfig() {
        if (crustalConfig == null) {
            crustalConfig = new ConstraintConfig(RegionPredicate.CRUSTAL);
            constraintConfigs.add(crustalConfig);
        }
        return crustalConfig;
    }

    public ConstraintConfig getSubductionConfig() {
        if (subductionConfig == null) {
            subductionConfig = new ConstraintConfig(RegionPredicate.SUBDUCTION);
            constraintConfigs.add(subductionConfig);
        }
        return subductionConfig;
    }

    protected void init(FaultSystemRupSet ruptureSet) {
        for (ConstraintConfig config : constraintConfigs) {
            config.init(ruptureSet);
        }

        Set<Integer> seen = new HashSet<>();
        List<Integer> doubleUps = new ArrayList<>();
        for (ConstraintConfig config : constraintConfigs) {
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

    public String toJson() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }

    public static Config fromJson(String json){
        Gson gson = new Gson();
        return gson.fromJson(json, Config.class);
    }
}

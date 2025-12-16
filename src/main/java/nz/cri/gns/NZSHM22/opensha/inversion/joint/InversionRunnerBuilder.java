package nz.cri.gns.NZSHM22.opensha.inversion.joint;

import com.google.gson.Gson;
import java.io.File;
import java.io.IOException;
import java.util.*;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_LogicTreeBranch;
import nz.cri.gns.NZSHM22.opensha.inversion.BaseInversionInputGenerator;
import nz.cri.gns.NZSHM22.opensha.inversion.NZSHM22_InversionFaultSystemRuptSet;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.constraints.ConstraintRegionConfig;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.constraints.JointConstraintGenerator;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.constraints.RegionPredicate;
import org.dom4j.DocumentException;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionInputGenerator;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.faultSurface.FaultSection;

public class InversionRunnerBuilder {

    NZSHM22_InversionFaultSystemRuptSet ruptureSet;
    List<ConstraintRegionConfig> configs;
    ConstraintRegionConfig crustalConfig;
    ConstraintRegionConfig subductionConfig;

    public InversionRunnerBuilder() {
        configs = new ArrayList<>();
    }

    protected void initConfigs() {
        for (ConstraintRegionConfig config : configs) {
            config.init(ruptureSet);
        }

        Set<Integer> seen = new HashSet<>();
        List<Integer> doubleUps = new ArrayList<>();
        for (ConstraintRegionConfig config : configs) {
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

    protected List<InversionConstraint> generateConstraints() {

        initConfigs();

        List<InversionConstraint> constraints = new ArrayList<>();
        for (ConstraintRegionConfig config : configs) {
            constraints.addAll(JointConstraintGenerator.buildSharedConstraints(ruptureSet, config));
        }
        return constraints;
    }

    public void run() throws DocumentException, IOException {

        List<InversionConstraint> constraints = generateConstraints();

        // FIXME: create joint ruptureset correctly.
        // FIXME: create joint LTB correctly.
        // FIXME: create joint inititalsolution and waterlevel
        InversionInputGenerator inputGenerator =
                new BaseInversionInputGenerator(ruptureSet, constraints, null, null);

        Annealer runner = new Annealer();
        runner.setRupSet(ruptureSet);
        runner.runInversion(inputGenerator);
    }

    public void setRuptureSet(NZSHM22_InversionFaultSystemRuptSet ruptureSet) {
        this.ruptureSet = ruptureSet;
    }

    public ConstraintRegionConfig getCrustalConfig() {
        if (crustalConfig == null) {
            crustalConfig = new ConstraintRegionConfig(RegionPredicate.CRUSTAL);
            configs.add(crustalConfig);
        }
        return crustalConfig;
    }

    public ConstraintRegionConfig getSubductionConfig() {
        if (subductionConfig == null) {
            subductionConfig = new ConstraintRegionConfig(RegionPredicate.SUBDUCTION);
            configs.add(subductionConfig);
        }
        return subductionConfig;
    }

    public void serialiseConfig() {
        Gson gson = new Gson();
        String json = gson.toJson(getCrustalConfig());
        System.out.println(json);
    }

    public static void main(String[] args) throws IOException, DocumentException {
        NZSHM22_LogicTreeBranch ltb = NZSHM22_LogicTreeBranch.crustalInversion();
        NZSHM22_InversionFaultSystemRuptSet rupSet =
                NZSHM22_InversionFaultSystemRuptSet.loadCrustalRuptureSet(
                        new File(
                                "C:\\Users\\volkertj\\Downloads\\NZSHM22_RuptureSet-UnVwdHVyZUdlbmVyYXRpb25UYXNrOjEwMDAzOA==(1).zip"),
                        ltb);

        InversionRunnerBuilder builder = new InversionRunnerBuilder();
        builder.setRuptureSet(rupSet);
        builder.serialiseConfig();

        ConstraintRegionConfig crustalConfig = builder.getCrustalConfig();

        builder.run();
    }
}

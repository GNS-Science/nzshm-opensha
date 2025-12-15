package nz.cri.gns.NZSHM22.opensha.inversion.joint;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import nz.cri.gns.NZSHM22.opensha.inversion.BaseInversionInputGenerator;
import nz.cri.gns.NZSHM22.opensha.inversion.NZSHM22_InversionFaultSystemRuptSet;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.constraints.ConstraintRegionConfig;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.constraints.JointConstraintGenerator;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionInputGenerator;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.faultSurface.FaultSection;

public class InversionProcess {

    NZSHM22_InversionFaultSystemRuptSet ruptureSet;
    List<ConstraintRegionConfig> configs;
    ConstraintRegionConfig crustalConfig;
    ConstraintRegionConfig subductionConfig;

    public InversionProcess() {
        configs = new ArrayList<>();
    }

    protected void checkRegionConfigs() {
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
            System.out.println("Section id double-ups in region config");
            System.out.println(doubleUps);
            throw new IllegalStateException("Section id double-ups");
        }
    }

    protected List<InversionConstraint> configure() {
        checkRegionConfigs();

        List<InversionConstraint> constraints = new ArrayList<>();
        for (ConstraintRegionConfig config : configs) {
            constraints.addAll(JointConstraintGenerator.buildSharedConstraints(ruptureSet, config));
        }
        return constraints;
    }

    public void run() {

        List<InversionConstraint> constraints = configure();

        // FIXME: create joint ruptureset correctly.
        // FIXME: create joint LTB correctly.
        // FIXME: create joint inititalsolution and waterlevel
        InversionInputGenerator inputGenerator =
                new BaseInversionInputGenerator(ruptureSet, constraints, null, null);


    }

    public void setRuptureSet(NZSHM22_InversionFaultSystemRuptSet ruptureSet) {
        this.ruptureSet = ruptureSet;
    }

    public static boolean isSubduction(FaultSection faultSection) {
        return faultSection.getSectionName().contains("row:");
    }

    public ConstraintRegionConfig getCrustalConfig() {
        if (crustalConfig == null) {
            List<Integer> sectionIds =
                    ruptureSet.getFaultSectionDataList().stream()
                            .filter(Predicate.not(InversionProcess::isSubduction))
                            .map(FaultSection::getSectionId)
                            .collect(Collectors.toList());
            crustalConfig = new ConstraintRegionConfig(sectionIds);
            configs.add(crustalConfig);
        }
        return crustalConfig;
    }

    public ConstraintRegionConfig getSubductionConfig() {
        if (subductionConfig == null) {
            List<Integer> sectionIds =
                    ruptureSet.getFaultSectionDataList().stream()
                            .filter(InversionProcess::isSubduction)
                            .map(FaultSection::getSectionId)
                            .collect(Collectors.toList());
            subductionConfig = new ConstraintRegionConfig(sectionIds);
            configs.add(subductionConfig);
        }
        return subductionConfig;
    }
}

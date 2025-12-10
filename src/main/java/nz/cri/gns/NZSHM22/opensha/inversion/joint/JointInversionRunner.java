package nz.cri.gns.NZSHM22.opensha.inversion.joint;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import nz.cri.gns.NZSHM22.opensha.inversion.BaseInversionInputGenerator;
import nz.cri.gns.NZSHM22.opensha.inversion.NZSHM22_InversionFaultSystemRuptSet;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.constraints.ConstraintRegionConfig;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.constraints.JointConstraintGenerator;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionInputGenerator;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.faultSurface.FaultSection;

public class JointInversionRunner {

    NZSHM22_InversionFaultSystemRuptSet ruptureSet;
    ConstraintRegionConfig crustalConfig;
    ConstraintRegionConfig subductionConfig;

    public JointInversionRunner() {}

    public void run() {
        List<InversionConstraint> constraints = new ArrayList<>();

        if (crustalConfig != null) {
            constraints.addAll(
                    JointConstraintGenerator.generateCrustalConstraints(ruptureSet, crustalConfig));
        }
        if (subductionConfig != null) {
            constraints.addAll(
                    JointConstraintGenerator.generateSubductionConstraints(
                            ruptureSet, subductionConfig));
        }

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
                            .filter(Predicate.not(JointInversionRunner::isSubduction))
                            .map(FaultSection::getSectionId)
                            .collect(Collectors.toList());
            crustalConfig = new ConstraintRegionConfig(sectionIds);
        }
        return crustalConfig;
    }

    public ConstraintRegionConfig getSubductionConfig() {
        if (subductionConfig == null) {
            List<Integer> sectionIds =
                    ruptureSet.getFaultSectionDataList().stream()
                            .filter(JointInversionRunner::isSubduction)
                            .map(FaultSection::getSectionId)
                            .collect(Collectors.toList());
            subductionConfig = new ConstraintRegionConfig(sectionIds);
        }
        return subductionConfig;
    }
}

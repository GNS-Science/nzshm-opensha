package nz.cri.gns.NZSHM22.opensha.inversion.joint;

import java.io.File;
import java.io.IOException;
import java.util.*;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_LogicTreeBranch;
import nz.cri.gns.NZSHM22.opensha.inversion.BaseInversionInputGenerator;
import nz.cri.gns.NZSHM22.opensha.inversion.NZSHM22_InversionFaultSystemRuptSet;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.constraints.ConstraintConfig;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.constraints.JointConstraintGenerator;
import org.dom4j.DocumentException;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionInputGenerator;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;

public class InversionRunner {

    NZSHM22_InversionFaultSystemRuptSet ruptureSet;
    public Config config;

    public InversionRunner() {
        config = new Config();
    }

    public InversionRunner(Config config) {
        this.config = config;
    }

    public Config getConfig() {
        return config;
    }

    protected List<InversionConstraint> generateConstraints() {

        List<InversionConstraint> constraints = new ArrayList<>();
        for (ConstraintConfig config : config.constraints) {
            constraints.addAll(JointConstraintGenerator.buildSharedConstraints(ruptureSet, config));
        }
        return constraints;
    }

    public FaultSystemSolution run() throws DocumentException, IOException {

        config.init(ruptureSet);

        List<InversionConstraint> constraints = generateConstraints();

        // FIXME: create joint ruptureset correctly.
        // FIXME: create joint LTB correctly.
        // FIXME: create joint inititalsolution and waterlevel
        InversionInputGenerator inputGenerator =
                new BaseInversionInputGenerator(ruptureSet, constraints, null, null);

        Annealer runner = new Annealer(config.getAnnealingConfig(), ruptureSet);
        FaultSystemSolution solution = runner.runInversion(inputGenerator);
        solution.addModule(new ConfigModule(config));

        return solution;
    }

    public void setRuptureSet(NZSHM22_InversionFaultSystemRuptSet ruptureSet) {
        this.ruptureSet = ruptureSet;
    }

    public static void main(String[] args) throws IOException, DocumentException {

        NZSHM22_LogicTreeBranch ltb = NZSHM22_LogicTreeBranch.crustalInversion();
        NZSHM22_InversionFaultSystemRuptSet rupSet =
                NZSHM22_InversionFaultSystemRuptSet.loadCrustalRuptureSet(
                        new File(
                                "C:\\Users\\volkertj\\Downloads\\NZSHM22_RuptureSet-UnVwdHVyZUdlbmVyYXRpb25UYXNrOjEwMDAzOA==(1).zip"),
                        // "C:\\Users\\volkertj\\Code\\ruptureSets\\mergedRupset_5km_cffPatch2km_cff0SelfStiffness.zip"),
                        ltb);

        Config config = new Config();
        config.createCrustalConfig();
        config.createSubductionConfig();
        InversionRunner builder = new InversionRunner(config);
        builder.setRuptureSet(rupSet);

        FaultSystemSolution solution = builder.run();

        solution.write(new File("/tmp/spikeInversionSolution.zip"));
    }
}

package nz.cri.gns.NZSHM22.opensha.inversion.joint;

import static nz.cri.gns.NZSHM22.opensha.inversion.joint.constraints.ConstraintGenerator.generateConstraints;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import nz.cri.gns.NZSHM22.opensha.inversion.BaseInversionInputGenerator;
import org.dom4j.DocumentException;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionInputGenerator;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;

public class InversionRunner {

    protected Config config;

    public InversionRunner(String configPath) throws IOException {
        config = ConfigModule.fromJson(Files.readString(Path.of(configPath)));
    }

    public InversionRunner(Config config) {
        this.config = config;
    }

    public FaultSystemSolution run() throws DocumentException, IOException {

        // hydrate and validate config
        config.init();
        // set up rupture set modules
        RuptureSetSetup.setup(config);
        // generate constraints
        List<InversionConstraint> constraints = generateConstraints(config);

        InversionInputGenerator inputGenerator =
                new BaseInversionInputGenerator(config.ruptureSet, constraints, null, null);

        Annealer runner = new Annealer(config.getAnnealingConfig(), config.ruptureSet);
        FaultSystemSolution solution = runner.runInversion(inputGenerator);
        solution.addModule(new ConfigModule(config));

        return solution;
    }

    public static void main(String[] args) throws IOException, DocumentException {
        // InversionRunner runner = new InversionRunner("NZSHM_config.json");
        InversionRunner runner = new InversionRunner("Hikurangi-reproducible.json");
        FaultSystemSolution solution = runner.run();
        solution.write(new File("/tmp/spikeSubductionInversionSolution.zip"));
    }
}

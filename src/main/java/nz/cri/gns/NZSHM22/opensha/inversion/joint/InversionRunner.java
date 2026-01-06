package nz.cri.gns.NZSHM22.opensha.inversion.joint;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_PaleoRates;
import nz.cri.gns.NZSHM22.opensha.inversion.BaseInversionInputGenerator;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.constraints.JointConstraintGenerator;
import org.dom4j.DocumentException;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionInputGenerator;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.LaplacianSmoothingInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.PaleoProbabilityModel;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.PaleoRateInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.UncertainDataConstraint;
import org.opensha.sha.faultSurface.FaultSection;

public class InversionRunner {

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

    protected List<InversionConstraint> generatePaleoConstraints() throws FileNotFoundException {

        List<InversionConstraint> results = new ArrayList<>();

        if (config.paleoRateConstraintWt <= 0) {
            return results;
        }

        IntPredicate isCrustal = PartitionPredicate.CRUSTAL.getPredicate(config.ruptureSet);

        List<FaultSection> crustalSections =
                config.ruptureSet.getFaultSectionDataList().stream()
                        .filter((section) -> isCrustal.test(section.getSectionId()))
                        .collect(Collectors.toList());

        List<UncertainDataConstraint.SectMappedUncertainDataConstraint> paleoRateConstraints =
                new ArrayList<>();
        if (config.paleoRates != null) {
            paleoRateConstraints.addAll(config.paleoRates.fetchConstraints(crustalSections));
        }
        if (config.extraPaleoRatesFile != null) {
            List<UncertainDataConstraint.SectMappedUncertainDataConstraint> extraConstraints =
                    NZSHM22_PaleoRates.fetchConstraints(
                            crustalSections, new FileInputStream(config.extraPaleoRatesFile));
            for (UncertainDataConstraint.SectMappedUncertainDataConstraint extraConstraint :
                    extraConstraints) {
                for (UncertainDataConstraint.SectMappedUncertainDataConstraint constraint :
                        paleoRateConstraints) {
                    if (constraint.dataLocation.equals(extraConstraint.dataLocation)) {
                        throw new IllegalStateException(
                                "Paleo rate location double-up at " + extraConstraint.dataLocation);
                    }
                }
                paleoRateConstraints.add(extraConstraint);
            }
        }

        PaleoProbabilityModel paleoProbabilityModel = null;
        if (config.paleoProbabilityModel != null) {
            paleoProbabilityModel = config.paleoProbabilityModel.fetchModel();
        }

        results.add(
                new PaleoRateInversionConstraint(
                        config.ruptureSet,
                        config.paleoRateConstraintWt,
                        paleoRateConstraints,
                        paleoProbabilityModel));

        if (config.paleoParentRateSmoothnessConstraintWeight > 0) {
            HashSet<Integer> paleoParentIDs = new HashSet<>();
            for (UncertainDataConstraint.SectMappedUncertainDataConstraint constraint :
                    paleoRateConstraints) {
                paleoParentIDs.add(
                        config.ruptureSet
                                .getFaultSectionDataList()
                                .get(constraint.sectionIndex)
                                .getParentSectionId());
            }
            results.add(
                    new LaplacianSmoothingInversionConstraint(
                            config.ruptureSet,
                            config.paleoParentRateSmoothnessConstraintWeight,
                            paleoParentIDs));
        }

        return results;
    }

    protected List<InversionConstraint> generateConstraints() throws FileNotFoundException {

        List<InversionConstraint> constraints = new ArrayList<>();

        for (PartitionConfig constraintConfig : config.partitions) {
            constraints.addAll(
                    JointConstraintGenerator.buildSharedConstraints(
                            config.ruptureSet, constraintConfig));
        }

        constraints.addAll(generatePaleoConstraints());

        return constraints;
    }

    public FaultSystemSolution run() throws DocumentException, IOException {

        config.apply();

        List<InversionConstraint> constraints = generateConstraints();

        // FIXME: create joint ruptureset correctly.
        // FIXME: create joint LTB correctly.
        // FIXME: create joint inititalsolution and waterlevel
        InversionInputGenerator inputGenerator =
                new BaseInversionInputGenerator(config.ruptureSet, constraints, null, null);

        Annealer runner = new Annealer(config.getAnnealingConfig(), config.ruptureSet);
        FaultSystemSolution solution = runner.runInversion(inputGenerator);
        solution.addModule(new ConfigModule(config));

        return solution;
    }

    public static void main(String[] args) throws IOException, DocumentException {

        Config config = ConfigModule.fromJson(Files.readString(Path.of("NZSHM_config.json")));

        InversionRunner runner = new InversionRunner(config);

        FaultSystemSolution solution = runner.run();

        solution.write(new File("/tmp/spikeInversionSolution.zip"));
    }
}

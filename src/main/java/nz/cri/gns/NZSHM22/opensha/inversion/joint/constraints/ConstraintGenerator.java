package nz.cri.gns.NZSHM22.opensha.inversion.joint.constraints;

import com.google.common.base.Preconditions;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_PaleoRates;
import nz.cri.gns.NZSHM22.opensha.inversion.NZSHM22_SubductionInversionTargetMFDs;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.*;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.LaplacianSmoothingInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.PaleoProbabilityModel;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.PaleoRateInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.UncertainDataConstraint;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionTargetMFDs;
import org.opensha.sha.faultSurface.FaultSection;

/**
 * Generates constraints as specified by the configuration. Uses SharedConstraintGenerator for
 * constraints shared by crustal and subduction fault models.
 */
public class ConstraintGenerator {

    /**
     * Generates paleo constraints. Paleo sites are matched to crustal sections. Constraints are
     * applied to each rupture that contains one of these sections.
     *
     * <p>Also adds a LaplacianSmoothingInversionConstraint if the appropriate weight is set.
     *
     * @param config the config
     * @return the constraints
     * @throws FileNotFoundException
     */
    static List<InversionConstraint> generatePaleoConstraints(Config config)
            throws FileNotFoundException {

        List<InversionConstraint> results = new ArrayList<>();

        if (config.paleoRateConstraintWt <= 0) {
            return results;
        }

        Preconditions.checkState(
                config.paleoRates != null,
                "paleo rates must be set if paleo constraint weight is set");

        IntPredicate isCrustal = PartitionPredicate.CRUSTAL.getPredicate(config.ruptureSet);

        List<FaultSection> crustalSections =
                config.ruptureSet.getFaultSectionDataList().stream()
                        .filter((section) -> isCrustal.test(section.getSectionId()))
                        .collect(Collectors.toList());

        List<UncertainDataConstraint.SectMappedUncertainDataConstraint> paleoRateConstraints =
                new ArrayList<>();
        // fetch paleo rates constraints base don crustal section matches
        if (config.paleoRates != null) {
            paleoRateConstraints.addAll(config.paleoRates.fetchConstraints(crustalSections));
        }
        // load extra paleo rates site
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

    /**
     * Store a set of targetMFDs in the PartitionMfds module
     *
     * @param ruptureSet
     * @param partition
     * @param targetMFDs
     */
    static void storeTargetMFDs(
            FaultSystemRupSet ruptureSet,
            PartitionPredicate partition,
            InversionTargetMFDs targetMFDs) {
        PartitionMfds mfdsModule = ruptureSet.getModule(PartitionMfds.class);
        if (mfdsModule == null) {
            mfdsModule = new PartitionMfds();
            ruptureSet.addModule(mfdsModule);
        }
        mfdsModule.add(partition, targetMFDs);
    }

    public static void createPartitionRuptureSets(Config config) {
        for (PartitionConfig partitionConfig : config.partitions) {
            partitionConfig.partitionRuptureSet =
                    PartitionFaultSystemRupSet.create(
                            config.ruptureSet,
                            partitionConfig.partitionPredicate,
                            config.scalingRelationship.toRupSetScalingRelationship(
                                    partitionConfig.partition.isCrustal()));
        }
    }

    /**
     * Generate all constraints
     *
     * @param config the config
     * @return a list of all constraints, ready to be turned into a matrix
     * @throws FileNotFoundException
     */
    public static List<InversionConstraint> generateConstraints(Config config)
            throws FileNotFoundException {

        // FIXME set up slip rates etc
        createPartitionRuptureSets(config);

        List<InversionConstraint> constraints = new ArrayList<>();

        for (PartitionConfig partitionConfig : config.partitions) {

            if (partitionConfig.partition.isSubduction()) {
                NZSHM22_SubductionInversionTargetMFDs targetMFDs =
                        new NZSHM22_SubductionInversionTargetMFDs(
                                // TODO join: ruptureset might have to return partition-specific
                                // maxMag
                                config.ruptureSet,
                                partitionConfig.totalRateM5,
                                partitionConfig.bValue,
                                partitionConfig.mfdTransitionMag,
                                partitionConfig.minMag,
                                partitionConfig.mfdUncertaintyWeight,
                                partitionConfig.mfdUncertaintyPower,
                                partitionConfig.mfdUncertaintyScalar);

                partitionConfig.mfdConstraints = targetMFDs.getMfdEqIneqConstraints();
                partitionConfig.mfdUncertaintyWeightedConstraints =
                        targetMFDs.getMfdUncertaintyConstraints();

                storeTargetMFDs(config.ruptureSet, partitionConfig.partition, targetMFDs);

            } else {
                CrustalInversionTargetMFDs targetMFDs =
                        new CrustalInversionTargetMFDs(config.ruptureSet, partitionConfig);
                partitionConfig.mfdConstraints = targetMFDs.getMFD_Constraints();
                partitionConfig.mfdUncertaintyWeightedConstraints =
                        targetMFDs.getMfdUncertaintyConstraints();

                storeTargetMFDs(config.ruptureSet, partitionConfig.partition, targetMFDs);
            }

            constraints.addAll(SharedConstraintGenerator.buildSharedConstraints(partitionConfig));
        }

        constraints.addAll(generatePaleoConstraints(config));

        return constraints;
    }
}

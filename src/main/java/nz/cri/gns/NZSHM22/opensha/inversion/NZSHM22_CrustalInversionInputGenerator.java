package nz.cri.gns.NZSHM22.opensha.inversion;

import java.util.*;
import nz.cri.gns.NZSHM22.opensha.analysis.NZSHM22_FaultSystemRupSetCalc;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.*;
import scratch.UCERF3.utils.U3SectionMFD_constraint;

/**
 * This class is used to generate inversion inputs (A/A_ineq matrices, d/d_ineq vectors) for a given
 * rupture set, inversion configuration, paleo rate constraints, improbability constraint, and paleo
 * probability model. It can also save these inputs to a zip file to be run on high performance
 * computing.
 */
public class NZSHM22_CrustalInversionInputGenerator extends BaseInversionInputGenerator {

    public NZSHM22_CrustalInversionInputGenerator(
            NZSHM22_InversionFaultSystemRuptSet rupSet,
            NZSHM22_CrustalInversionConfiguration config,
            List<UncertainDataConstraint.SectMappedUncertainDataConstraint> paleoRateConstraints,
            PaleoProbabilityModel paleoProbabilityModel) {
        super(
                rupSet,
                buildConstraints(rupSet, config, paleoRateConstraints, paleoProbabilityModel),
                config.getInitialRupModel(),
                buildWaterLevel(config, rupSet));
    }

    private static List<InversionConstraint> buildConstraints(
            NZSHM22_InversionFaultSystemRuptSet rupSet,
            NZSHM22_CrustalInversionConfiguration config,
            List<UncertainDataConstraint.SectMappedUncertainDataConstraint> paleoRateConstraints,
            PaleoProbabilityModel paleoProbabilityModel) {

        List<InversionConstraint> constraints = buildSharedConstraints(rupSet, config);

        if (config.getPaleoRateConstraintWt() > 0) {
            constraints.add(
                    new PaleoRateInversionConstraint(
                            rupSet,
                            config.getPaleoRateConstraintWt(),
                            paleoRateConstraints,
                            paleoProbabilityModel));

            if (config.getpaleoParentRateSmoothnessConstraintWeight() > 0) {
                HashSet<Integer> paleoParentIDs = new HashSet();
                for (UncertainDataConstraint.SectMappedUncertainDataConstraint constraint :
                        paleoRateConstraints) {
                    paleoParentIDs.add(
                            rupSet.getFaultSectionDataList()
                                    .get(constraint.sectionIndex)
                                    .getParentSectionId());
                }
                constraints.add(
                        new LaplacianSmoothingInversionConstraint(
                                rupSet,
                                config.getpaleoParentRateSmoothnessConstraintWeight(),
                                paleoParentIDs));
            }
        }

        // MFD Subsection nucleation MFD constraint
        ArrayList<U3SectionMFD_constraint> MFDConstraints = null;
        if (config.getNucleationMFDConstraintWt() > 0.0) {
            MFDConstraints =
                    NZSHM22_FaultSystemRupSetCalc.getCharInversionSectMFD_Constraints(rupSet);
            constraints.add(
                    new U3MFDSubSectNuclInversionConstraint(
                            rupSet, config.getNucleationMFDConstraintWt(), MFDConstraints));
        }

        return constraints;
    }
}

package nz.cri.gns.NZSHM22.opensha.inversion;

import java.io.IOException;
import java.util.*;
import nz.cri.gns.NZSHM22.opensha.analysis.NZSHM22_FaultSystemRupSetCalc;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.InversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.*;
import org.opensha.sha.faultSurface.FaultSection;
import scratch.UCERF3.utils.U3SectionMFD_constraint;
import scratch.UCERF3.utils.aveSlip.U3AveSlipConstraint;
import scratch.UCERF3.utils.paleoRateConstraints.UCERF3_PaleoProbabilityModel;

/**
 * This class is used to generate inversion inputs (A/A_ineq matrices, d/d_ineq vectors) for a given
 * rupture set, inversion configuration, paleo rate constraints, improbability constraint, and paleo
 * probability model. It can also save these inputs to a zip file to be run on high performance
 * computing.
 */
public class NZSHM22_CrustalInversionInputGenerator extends BaseInversionInputGenerator {

    private static final boolean D = false;
    /**
     * this enables use of the getQuick and setQuick methods on the sparse matrices. this comes with
     * a performance boost, but disables range checks and is more prone to errors.
     */
    private static final boolean QUICK_GETS_SETS = true;

    // inputs
    private NZSHM22_InversionFaultSystemRuptSet rupSet;
    private AbstractInversionConfiguration config;
    private List<UncertainDataConstraint.SectMappedUncertainDataConstraint> paleoRateConstraints;
    private List<U3AveSlipConstraint> aveSlipConstraints;
    private double[] improbabilityConstraint;
    private PaleoProbabilityModel paleoProbabilityModel;

    public NZSHM22_CrustalInversionInputGenerator(
            NZSHM22_InversionFaultSystemRuptSet rupSet,
            NZSHM22_CrustalInversionConfiguration config,
            List<UncertainDataConstraint.SectMappedUncertainDataConstraint> paleoRateConstraints,
            List<U3AveSlipConstraint> aveSlipConstraints,
            double[] improbabilityConstraint, // may become an object in the future
            PaleoProbabilityModel paleoProbabilityModel) {
        super(
                rupSet,
                buildConstraints(
                        rupSet,
                        config,
                        paleoRateConstraints,
                        aveSlipConstraints,
                        paleoProbabilityModel),
                config.getInitialRupModel(),
                buildWaterLevel(config, rupSet));
        this.rupSet = rupSet;
        this.config = config;
        this.paleoRateConstraints = paleoRateConstraints;
        this.improbabilityConstraint = improbabilityConstraint;
        this.aveSlipConstraints = aveSlipConstraints;
        this.paleoProbabilityModel = paleoProbabilityModel;
    }

    private static PaleoProbabilityModel defaultProbModel = null;

    /**
     * Loads the default paleo probability model for UCERF3 (Glenn's file). Can be turned into an
     * enum if we get alternatives
     *
     * @return
     * @throws IOException
     */
    public static PaleoProbabilityModel loadDefaultPaleoProbabilityModel() throws IOException {
        if (defaultProbModel == null) defaultProbModel = UCERF3_PaleoProbabilityModel.load();
        return defaultProbModel;
    }

    private static List<InversionConstraint> buildConstraints(
            NZSHM22_InversionFaultSystemRuptSet rupSet,
            NZSHM22_CrustalInversionConfiguration config,
            List<UncertainDataConstraint.SectMappedUncertainDataConstraint> paleoRateConstraints,
            List<U3AveSlipConstraint> aveSlipConstraints,
            PaleoProbabilityModel paleoProbabilityModel) {

        if (aveSlipConstraints != null) {
            throw new RuntimeException("aveslipconstraints aren't ued");
        }

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

    public void generateInputs() {
        generateInputs(null, D);
    }

    /**
     * This returns the normalized distance along a rupture that a paleoseismic trench is located
     * (Glenn's x/L). It is between 0 and 0.5. This currently puts the trench in the middle of the
     * subsection. We need this for the UCERF3 probability of detecting a rupture in a trench.
     *
     * @return
     */
    public static double getDistanceAlongRupture(
            List<FaultSection> sectsInRup, int targetSectIndex) {
        return getDistanceAlongRupture(sectsInRup, targetSectIndex, null);
    }

    public static double getDistanceAlongRupture(
            List<FaultSection> sectsInRup,
            int targetSectIndex,
            Map<Integer, Double> traceLengthCache) {
        double distanceAlongRup = 0;

        double totalLength = 0;
        double lengthToRup = 0;
        boolean reachConstraintLoc = false;

        // Find total length (km) of fault trace and length (km) from one end to the
        // paleo trench location
        for (int i = 0; i < sectsInRup.size(); i++) {
            FaultSection sect = sectsInRup.get(i);
            int sectIndex = sect.getSectionId();
            Double sectLength = null;
            if (traceLengthCache != null) {
                sectLength = traceLengthCache.get(sectIndex);
                if (sectLength == null) {
                    sectLength = sect.getFaultTrace().getTraceLength();
                    traceLengthCache.put(sectIndex, sectLength);
                }
            } else {
                sectLength = sect.getFaultTrace().getTraceLength();
            }
            totalLength += sectLength;
            if (sectIndex == targetSectIndex) {
                reachConstraintLoc = true;
                // We're putting the trench in the middle of the subsection for now
                lengthToRup += sectLength / 2;
            }
            // We haven't yet gotten to the trench subsection so keep adding to lengthToRup
            if (reachConstraintLoc == false) lengthToRup += sectLength;
        }

        if (!reachConstraintLoc) // check to make sure we came across the trench subsection in the
            // rupture
            throw new IllegalStateException(
                    "Paleo site subsection was not included in rupture subsections");

        // Normalized distance along the rainbow (Glenn's x/L) - between 0 and 1
        distanceAlongRup = lengthToRup / totalLength;
        // Adjust to be between 0 and 0.5 (since rainbow is symmetric about 0.5)
        if (distanceAlongRup > 0.5) distanceAlongRup = 1 - distanceAlongRup;

        return distanceAlongRup;
    }

    public AbstractInversionConfiguration getConfig() {
        return config;
    }

    public List<UncertainDataConstraint.SectMappedUncertainDataConstraint>
            getPaleoRateConstraints() {
        return paleoRateConstraints;
    }

    public double[] getImprobabilityConstraint() {
        return improbabilityConstraint;
    }

    public PaleoProbabilityModel getPaleoProbabilityModel() {
        return paleoProbabilityModel;
    }
}

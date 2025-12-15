package nz.cri.gns.NZSHM22.opensha.inversion;

/**
 * This class is used to generate inversion inputs (A/A_ineq matrices, d/d_ineq vectors) for a given
 * rupture set, inversion configuration, paleo rate constraints, improbability constraint, and paleo
 * probability model. It can also save these inputs to a zip file to be run on high performance
 * computing.
 */
public class NZSHM22_SubductionInversionInputGenerator extends BaseInversionInputGenerator {

    private static final boolean D = false;

    public NZSHM22_SubductionInversionInputGenerator(
            NZSHM22_InversionFaultSystemRuptSet rupSet,
            NZSHM22_SubductionInversionConfiguration config) {
        super(
                rupSet,
                buildSharedConstraints(rupSet, config),
                config.getInitialRupModel(),
                buildWaterLevel(config, rupSet));
    }

    public void generateInputs() {
        generateInputs(null, D);
    }
}

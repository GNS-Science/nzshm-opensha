package nz.cri.gns.NZSHM22.opensha.inversion;

/**
 * This class is used to generate inversion inputs (A/A_ineq matrices, d/d_ineq vectors) for a given
 * rupture set, inversion configuration, paleo rate constraints, improbability constraint, and paleo
 * probability model. It can also save these inputs to a zip file to be run on high performance
 * computing.
 */
public class NZSHM22_SubductionInversionInputGenerator extends BaseInversionInputGenerator {

    private static final boolean D = false;
    /**
     * this enables use of the getQuick and setQuick methods on the sparse matrices. this comes with
     * a performance boost, but disables range checks and is more prone to errors.
     */
    //	private static final boolean QUICK_GETS_SETS = true;

    // inputs
    //	private NZSHM22_InversionFaultSystemRuptSet rupSet;
    private NZSHM22_SubductionInversionConfiguration config;
    //	private List<AveSlipConstraint> aveSlipConstraints;
    //	private double[] improbabilityConstraint;
    //	private PaleoProbabilityModel paleoProbabilityModel;

    public NZSHM22_SubductionInversionInputGenerator(
            NZSHM22_InversionFaultSystemRuptSet rupSet,
            NZSHM22_SubductionInversionConfiguration config
            // , List<AveSlipConstraint> aveSlipConstraints
            ) {
        super(
                rupSet,
                buildSharedConstraints(rupSet, config),
                config.getInitialRupModel(),
                buildWaterLevel(config, rupSet));
        //		this.rupSet = rupSet;
        this.config = config;
        //		this.aveSlipConstraints = aveSlipConstraints;
    }

    public void generateInputs() {
        generateInputs(null, D);
    }

    public NZSHM22_SubductionInversionConfiguration getConfig() {
        return config;
    }
}

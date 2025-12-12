package nz.cri.gns.NZSHM22.opensha.inversion;

import com.google.common.base.Preconditions;
import java.io.IOException;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_LogicTreeBranch;
import nz.cri.gns.NZSHM22.opensha.util.ParameterRunner;
import org.dom4j.DocumentException;
import org.opensha.sha.earthquake.faultSysSolution.inversion.Inversions;
import scratch.UCERF3.enumTreeBranches.InversionModels;

/** Runs the standard NSHM inversion on a subduction rupture set. */
public class NZSHM22_SubductionInversionRunner extends NZSHM22_AbstractInversionRunner {

    protected double mfdMinMag = 7.05;

    /** Creates a new NZSHM22_InversionRunner with defaults. */
    public NZSHM22_SubductionInversionRunner() {
        super();
    }

    /**
     * Sets GutenbergRichterMFD arguments
     *
     * @param totalRateM5 the number of M>=5's per year. TODO: ref David Rhodes/Chris Roland? [KKS,
     *     CBC]
     * @param bValue
     * @param mfdTransitionMag magnitude to switch from MFD equality to MFD inequality TODO: how to
     *     validate this number for NZ? (ref Morgan Page in USGS/UCERF3) [KKS, CBC]
     * @param mfdMinMag magnitude of minimum magnitude in MFD target, rate set to 1e-20 below [CDC]
     * @return
     */
    public NZSHM22_SubductionInversionRunner setGutenbergRichterMFD(
            double totalRateM5, double bValue, double mfdTransitionMag, double mfdMinMag) {
        this.totalRateM5 = totalRateM5;
        this.bValue = bValue;
        this.mfdTransitionMag = mfdTransitionMag;
        this.mfdMinMag = mfdMinMag;
        return this;
    }

    public NZSHM22_SubductionInversionRunner setGutenbergRichterMFD(
            double totalRateM5, double bValue, double mfdTransitionMag) {
        this.totalRateM5 = totalRateM5;
        this.bValue = bValue;
        this.mfdTransitionMag = mfdTransitionMag;
        return this;
    }

    @Override
    protected NZSHM22_SubductionInversionRunner configure() throws DocumentException, IOException {

        NZSHM22_LogicTreeBranch branch = NZSHM22_LogicTreeBranch.subductionInversion();
        setupLTB(branch);
        rupSet =
                NZSHM22_InversionFaultSystemRuptSet.loadSubductionRuptureSet(
                        getRupSetInput(), branch);

        if (varPertBasisAsInititalSolution) {
            if (variablePerturbationBasis == null) {
                variablePerturbationBasis = Inversions.getDefaultVariablePerturbationBasis(rupSet);
            }
            Preconditions.checkState(
                    initialSolution == null,
                    "Initial solution must be null if variablePerturbationBasis as initial solution.");
            initialSolution = variablePerturbationBasis.clone();
        }

        InversionModels inversionModel = branch.getValue(InversionModels.class);

        NZSHM22_SubductionInversionConfiguration inversionConfiguration =
                NZSHM22_SubductionInversionConfiguration.forModel(
                        this,
                        inversionModel,
                        rupSet,
                        initialSolution,
                        mfdUncertWtdConstraintWt,
                        mfdUncertWtdConstraintPower,
                        mfdUncertWtdConstraintScalar,
                        totalRateM5,
                        bValue,
                        mfdTransitionMag,
                        mfdMinMag);

        // CBC This may not be needed long term
        solutionMfds =
                ((NZSHM22_SubductionInversionTargetMFDs)
                                inversionConfiguration.getInversionTargetMfds())
                        .getMFDConstraintComponents();

        NZSHM22_SubductionInversionInputGenerator inversionInputGenerator =
                new NZSHM22_SubductionInversionInputGenerator(rupSet, inversionConfiguration);
        setInversionInputGenerator(inversionInputGenerator);
        return this;
    }

    public static void main(String[] args) throws IOException, DocumentException {
        ParameterRunner.runNZSHM22HikurangiInversion();
    }
}

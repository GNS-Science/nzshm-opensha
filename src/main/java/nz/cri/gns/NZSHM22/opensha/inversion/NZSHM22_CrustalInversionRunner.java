package nz.cri.gns.NZSHM22.opensha.inversion;

import com.google.common.base.Preconditions;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import nz.cri.gns.NZSHM22.opensha.data.region.NewZealandRegions;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.*;
import nz.cri.gns.NZSHM22.opensha.polygonise.NZSHM22_PolygonisedDistributedModelBuilder;
import nz.cri.gns.NZSHM22.opensha.util.ParameterRunner;
import org.dom4j.DocumentException;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.inversion.Inversions;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.PaleoProbabilityModel;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.UncertainDataConstraint;
import org.opensha.sha.earthquake.faultSysSolution.modules.PaleoseismicConstraintData;
import scratch.UCERF3.enumTreeBranches.InversionModels;

/** Runs the standard NSHM inversion on a crustal rupture set. */
public class NZSHM22_CrustalInversionRunner extends NZSHM22_AbstractInversionRunner {

    //	private NZSHM22_CrustalInversionConfiguration inversionConfiguration;
    private double totalRateM5_Sans = 3.6;
    private double totalRateM5_TVZ = 0.4;
    private double bValue_Sans = 1.05;
    private double bValue_TVZ = 1.25;
    private double minMag_Sans = 6.95;
    private double minMag_TVZ = 6.95;

    private double maxMagTVZ = 20.0;
    private double maxMagSans = 20.0;
    private NZSHM22_MagBounds.MaxMagType maxMagType = NZSHM22_MagBounds.MaxMagType.NONE;

    private double paleoRateConstraintWt = 0;
    private double paleoParentRateSmoothnessConstraintWeight = 0;
    private NZSHM22_PaleoRates paleoRates;
    private NZSHM22_PaleoProbabilityModel paleoProbabilityModel;
    private String extraPaleoRatesFile;
    private double sansSlipRateFactor = -1;
    private double tvzSlipRateFactor = -1;

    private boolean enableTvzMFDs = false;
    private boolean enableMinMaxSampler = false;

    private NZSHM22_PolygonisedDistributedModelBuilder polygoniser = null;

    /** Creates a new NZSHM22_InversionRunner with defaults. */
    public NZSHM22_CrustalInversionRunner() {
        super();
    }

    /**
     * If the polygonizer is set up, the solution zip file will include a module with the
     * polygonized background.
     *
     * @param exponent the exponent for the weighting function
     * @param weightingFunctionType currently only accepts "LINEAR"
     * @param upScaleStep the absolute step value for the upscaled background that is used during
     *     polygonization. Indications the number of grid points per degree. 10 is the normal step
     *     value for UCERF3 and NZSHM22.
     * @return
     */
    public NZSHM22_CrustalInversionRunner setPolygonizer(
            double exponent, String weightingFunctionType, int upScaleStep) {
        polygoniser = new NZSHM22_PolygonisedDistributedModelBuilder();
        polygoniser.setExponent(exponent);
        polygoniser.setWeightingFunction(weightingFunctionType);
        polygoniser.setStep(upScaleStep);
        return this;
    }

    /**
     * Determines whether NZ is a single region (false) or is split up in TVZ and Sans TVZ (true)
     *
     * @param enableTvz
     * @return
     */
    public NZSHM22_CrustalInversionRunner setEnableTvzMFDs(boolean enableTvz) {
        if (enableTvz) {
            throw new RuntimeException("This feature is not currently supported.");
        }
        this.enableTvzMFDs = enableTvz;
        return this;
    }

    /**
     * Sets the minimum magnitude for targetOnFaultSupraSeisMFDs
     *
     * @param minMagSans
     * @param minMagTvz
     * @return this runner
     */
    public NZSHM22_CrustalInversionRunner setMinMags(double minMagSans, double minMagTvz) {
        this.minMag_Sans = minMagSans;
        this.minMag_TVZ = minMagTvz;
        return this;
    }

    public NZSHM22_CrustalInversionRunner setMaxMags(
            String maxMagType, double maxMagSans, double maxMagTVZ) {
        this.maxMagType = NZSHM22_MagBounds.MaxMagType.valueOf(maxMagType);
        this.maxMagSans = maxMagSans;
        this.maxMagTVZ = maxMagTVZ;
        return this;
    }

    public NZSHM22_CrustalInversionRunner setEnableMinMaxSampler(boolean enable) {
        this.enableMinMaxSampler = enable;
        return this;
    }

    /**
     * Sets regional slip scaling factor
     *
     * @param sansSlipRateFactor
     * @param tvzSlipRateFactor
     * @return
     */
    public NZSHM22_CrustalInversionRunner setSlipRateFactor(
            double sansSlipRateFactor, double tvzSlipRateFactor) {
        this.sansSlipRateFactor = sansSlipRateFactor;
        this.tvzSlipRateFactor = tvzSlipRateFactor;
        return this;
    }

    /**
     * Sets GutenbergRichterMFD arguments
     *
     * @param totalRateM5_Sans
     * @param totalRateM5_TVZ
     * @param bValue_Sans
     * @param bValue_TVZ
     * @param mfdTransitionMag
     * @return
     */
    public NZSHM22_CrustalInversionRunner setGutenbergRichterMFD(
            double totalRateM5_Sans,
            double totalRateM5_TVZ,
            double bValue_Sans,
            double bValue_TVZ,
            double mfdTransitionMag) {
        this.totalRateM5_Sans = totalRateM5_Sans;
        this.totalRateM5_TVZ = totalRateM5_TVZ;
        this.bValue_Sans = bValue_Sans;
        this.bValue_TVZ = bValue_TVZ;
        this.mfdTransitionMag = mfdTransitionMag;
        return this;
    }

    public NZSHM22_CrustalInversionRunner setPaleoRateConstraintWt(double paleoRateConstraintWt) {
        this.paleoRateConstraintWt = paleoRateConstraintWt;
        return this;
    }

    public NZSHM22_CrustalInversionRunner setPaleoRates(NZSHM22_PaleoRates paleoRates) {
        this.paleoRates = paleoRates;
        return this;
    }

    public NZSHM22_CrustalInversionRunner setPaleoProbabilityModel(
            NZSHM22_PaleoProbabilityModel probabilityModel) {
        this.paleoProbabilityModel = probabilityModel;
        return this;
    }

    public NZSHM22_CrustalInversionRunner setPaleoRateConstraints(
            double weight,
            double smoothingWeight,
            String paleoRateConstraints,
            String paleoProbabilityModel) {
        paleoRateConstraintWt = weight;
        paleoParentRateSmoothnessConstraintWeight = smoothingWeight;
        setPaleoRates(NZSHM22_PaleoRates.valueOf(paleoRateConstraints));
        setPaleoProbabilityModel(NZSHM22_PaleoProbabilityModel.valueOf(paleoProbabilityModel));
        return this;
    }

    /**
     * Specifies a CSV to use for paleo rates. The rates in this CSV will be imported as if they
     * were appended to the paleo rates CSV specified in the LogicTreeBranch with setPaleoRates() or
     * setPaleoRateConstraints(). An IllegalStateException will be thrown during inversion if this
     * file has a location double-up with the data in the LogicTreeBranch. Set the LTB paleo rates
     * to CUSTOM if you only want rates from this CSV file. Note that this file will not be recorded
     * in the LogicTreeBranch.
     *
     * @param fileName a path to a paleo rates CSV file
     * @return this runner
     */
    public NZSHM22_CrustalInversionRunner setPaleoRatesFile(String fileName) {
        extraPaleoRatesFile = fileName;
        return this;
    }

    @Override
    protected Set<Integer> createSamplerExclusions() {
        Set<Integer> exclusions = super.createSamplerExclusions();
        if (enableMinMaxSampler) {
            TvzDomainSections tvzSections = rupSet.getModule(TvzDomainSections.class);
            for (int r = 0; r < rupSet.getNumRuptures(); r++) {
                double mag = rupSet.getMagForRup(r);
                boolean inTvz = tvzSections.isInRegion(rupSet.getSectionsIndicesForRup(r));
                if ((inTvz && (mag < minMag_TVZ || mag > maxMagTVZ))
                        || (!inTvz && (mag < minMag_Sans || mag > maxMagSans))) {
                    exclusions.add(r);
                }
            }
        }
        return exclusions;
    }

    @Override
    protected NZSHM22_CrustalInversionRunner configure() throws DocumentException, IOException {

        NZSHM22_LogicTreeBranch branch = NZSHM22_LogicTreeBranch.crustalInversion();
        setupLTB(branch);

        if (!enableTvzMFDs) {
            branch.setValue(
                    new NZSHM22_Regions(
                            NewZealandRegions.NZ, new NewZealandRegions.NZ_EMPTY_GRIDDED()));
        }
        if (maxMagType != NZSHM22_MagBounds.MaxMagType.NONE) {
            branch.setValue(new NZSHM22_MagBounds(maxMagSans, maxMagTVZ, maxMagType));
        }
        if (tvzSlipRateFactor != -1 || sansSlipRateFactor != -1) {
            branch.setValue(new NZSHM22_SlipRateFactors(sansSlipRateFactor, tvzSlipRateFactor));
        }

        rupSet =
                NZSHM22_InversionFaultSystemRuptSet.loadCrustalRuptureSet(getRupSetInput(), branch);

        // XXX joint here?

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

        // this contains all inversion weights
        NZSHM22_CrustalInversionConfiguration inversionConfiguration;

        if (maxMagType == NZSHM22_MagBounds.MaxMagType.MANIPULATE_MFD) {
            inversionConfiguration =
                    NZSHM22_CrustalInversionConfiguration.forModel(
                            this,
                            inversionModel,
                            rupSet,
                            initialSolution,
                            totalRateM5_Sans,
                            totalRateM5_TVZ,
                            bValue_Sans,
                            bValue_TVZ,
                            minMag_Sans,
                            minMag_TVZ,
                            maxMagSans,
                            maxMagTVZ,
                            mfdUncertWtdConstraintPower,
                            mfdUncertWtdConstraintScalar);
        } else {
            inversionConfiguration =
                    NZSHM22_CrustalInversionConfiguration.forModel(
                            this,
                            inversionModel,
                            rupSet,
                            initialSolution,
                            totalRateM5_Sans,
                            totalRateM5_TVZ,
                            bValue_Sans,
                            bValue_TVZ,
                            minMag_Sans,
                            minMag_TVZ,
                            100,
                            100,
                            mfdUncertWtdConstraintPower,
                            mfdUncertWtdConstraintScalar);
        }

        inversionConfiguration
                .setPaleoRateConstraintWt(paleoRateConstraintWt)
                .setpaleoParentRateSmoothnessConstraintWeight(
                        paleoParentRateSmoothnessConstraintWeight);

        /*
         * Build inversion inputs
         */
        List<UncertainDataConstraint.SectMappedUncertainDataConstraint> paleoRateConstraints =
                new ArrayList<>();
        if (paleoRates != null) {
            paleoRateConstraints.addAll(
                    paleoRates.fetchConstraints(rupSet.getFaultSectionDataList()));
        }
        if (extraPaleoRatesFile != null) {
            List<UncertainDataConstraint.SectMappedUncertainDataConstraint> extraConstraints =
                    NZSHM22_PaleoRates.fetchConstraints(
                            rupSet.getFaultSectionDataList(),
                            new FileInputStream(extraPaleoRatesFile));
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
        if (this.paleoProbabilityModel != null) {
            paleoProbabilityModel = this.paleoProbabilityModel.fetchModel();
        }

        NZSHM22_CrustalInversionInputGenerator inversionInputGenerator =
                new NZSHM22_CrustalInversionInputGenerator(
                        rupSet,
                        inversionConfiguration,
                        paleoRateConstraints,
                        paleoProbabilityModel);
        setInversionInputGenerator(inversionInputGenerator);

        rupSet.addModule(
                new PaleoseismicConstraintData(
                        rupSet, paleoRateConstraints, paleoProbabilityModel, null, null));

        if (polygoniser != null) {
            polygoniser.apply(rupSet);
        }

        return this;
    }

    @Override
    public FaultSystemSolution runInversion() throws IOException, DocumentException {
        FaultSystemSolution solution = super.runInversion();
        solution.addModule(rupSet.getInversionTargetMFDs().getOnFaultSubSeisMFDs());
        return solution;
    }

    public static void main(String[] args) throws IOException, DocumentException {

        ParameterRunner.runNZSHM22CrustalInversion();
    }
}

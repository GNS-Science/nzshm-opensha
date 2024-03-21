package nz.cri.gns.NZSHM22.opensha.util;

import nz.cri.gns.NZSHM22.opensha.calc.SimplifiedScalingRelationship;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultModels;
import nz.cri.gns.NZSHM22.opensha.inversion.NZSHM22_CrustalInversionRunner;
import nz.cri.gns.NZSHM22.opensha.ruptures.NZSHM22_CoulombRuptureSetBuilder;
import nz.cri.gns.NZSHM22.opensha.ruptures.NZSHM22_SubductionRuptureSetBuilder;
import org.dom4j.DocumentException;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Utility class to populate inversion runners and rupture builders with Parameters.
 * Can be used to make runs repeatable or to reproduce runs from toshi.
 * <p>
 * The code to apply parameters to runners and builders is ported form runzi.
 */
public class ParameterRunner {

    String inputPath = "/tmp/";
    String outputPath = "/tmp/";

    Parameters arguments;

    public ParameterRunner(Parameters arguments) {
        this.arguments = arguments;
    }

    public ParameterRunner(Parameters.NZSHM22 parameters) throws IOException {
        this(parameters.getParameters());
    }

    /**
     * Ensures that input and output paths etc. exist. Creates them if necessary.
     *
     * @throws IOException
     */
    public void ensurePaths() throws IOException {
        Path inPath = Paths.get(inputPath);
        if (Files.notExists(inPath)) {
            System.err.println("Creating input path " + inputPath);
            Files.createDirectories(inPath);
        }
        Path outPath = Paths.get(outputPath);
        if (Files.notExists(outPath)) {
            System.err.println("Creating output path " + outputPath);
            Files.createDirectories(outPath);
        }

        if (arguments.containsKey("rupture_set")) {
            Path ruptureSetPath = Paths.get(inputPath, arguments.get("rupture_set"));
            if (Files.notExists(ruptureSetPath)) {
                System.err.println("Rupture set file does not exist at " + ruptureSetPath);
                if (arguments.containsKey("rupture_set_file_id")) {
                    System.err.println("Try to download the rupture set from http://simple-toshi-ui.s3-website-ap-southeast-2.amazonaws.com/FileDetail/" + arguments.get("rupture_set_file_id"));
                }
            }
        }
    }

    /**
     * Applies the Parameters to a NZSHM22_CoulombRuptureSetBuilder.
     * Does not run the builder.
     *
     * @param builder the builder to be set up.
     */
    public void setUpCoulombCrustalRuptureSetBuilder(NZSHM22_CoulombRuptureSetBuilder builder) {

        builder.setMaxFaultSections(arguments.getInteger("max_sections"));
        builder.setMaxJumpDistance(arguments.getDouble("max_jump_distance"));
        builder.setAdaptiveMinDist(arguments.getDouble("adaptive_min_distance"));
        builder.setAdaptiveSectFract(arguments.getFloat("thinning_factor"));
        builder.setMinSubSectsPerParent(arguments.getInteger("min_sub_sects_per_parent"));
        builder.setMinSubSections(arguments.getInteger("min_sub_sections"));
        builder.setFaultModel(NZSHM22_FaultModels.valueOf(arguments.get("fault_model")));

        if (arguments.get("fault_model").contains("CFM_1_0")) {
            String tvzDomain = "4";
            builder.setScaleDepthIncludeDomain(tvzDomain, arguments.getDouble("depth_scaling_tvz"));
            builder.setScaleDepthExcludeDomain(tvzDomain, arguments.getDouble("depth_scaling_sans"));
        }

        if (arguments.get("scaling_relationship").equals("SIMPLE_CRUSTAL")) {
            SimplifiedScalingRelationship sr = new SimplifiedScalingRelationship();
            sr.setupCrustal(4.2, 4.2);
            builder.setScalingRelationship(sr);
        }
        // runzi gets this wrong, the types don"t match
//        if(arguments.get("scaling_relationship").equals("TMG_CRU_2017")) {
//            TMG2017CruMagAreaRel sr = new TMG2017CruMagAreaRel();
//            sr.setRake(0.0);
//            builder.setScalingRelationship(sr);
//        }
    }

    /**
     * Applies the Parameters to a NZSHM22_SubductionRuptureSetBuilder.
     * Does not run the builder.
     *
     * @param builder
     */
    public void setUpSubductionRuptureSetBuilder(NZSHM22_SubductionRuptureSetBuilder builder) {
        builder.setDownDipAspectRatio(
                arguments.getDouble("min_aspect_ratio"),
                arguments.getDouble("max_aspect_ratio"),
                arguments.getInteger("aspect_depth_threshold"));
        builder.setDownDipMinFill(arguments.getDouble("min_fill_ratio"));
        builder.setDownDipPositionCoarseness(arguments.getDouble("growth_position_epsilon"));
        builder.setDownDipSizeCoarseness(arguments.getDouble("growth_size_epsilon"));
        builder.setScalingRelationship(arguments.get("scaling_relationship"));
        builder.setSlipAlongRuptureModel(arguments.get("slip_along_rupture_model"));
        builder.setFaultModel(arguments.get("fault_model"));

//        runzi got this wrong, the setter does not exist
//        if (arguments.containsKey("deformation_model")) {
//            builder.setDeformationModel(arguments.get("deformation_model"));
//        }
    }

    /**
     * Applies the parameters to a NZSHM22_CrustalInversionRunner.
     *
     * @param runner a crustal inversion runner
     * @throws IOException
     */
    public void setUpInversionRunner(NZSHM22_CrustalInversionRunner runner) throws IOException {

        runner.setSpatialSeisPDF(arguments.get("spatial_seis_pdf"));
        runner.setDeformationModel(arguments.get("deformation_model"));
        runner.setGutenbergRichterMFD(
                arguments.getDouble("mfd_mag_gt_5_sans"),
                arguments.getDouble("mfd_mag_gt_5_tvz"),
                arguments.getDouble("mfd_b_value_sans"),
                arguments.getDouble("mfd_b_value_tvz"),
                arguments.getDouble("mfd_transition_mag")
        );
        if (arguments.get("mfd_equality_weight") != null && arguments.get("mfd_inequality_weight") != null) {
            runner.setGutenbergRichterMFDWeights(
                    arguments.getDouble("mfd_equality_weight"),
                    arguments.getDouble("mfd_inequality_weight"));
        } else if ((arguments.get("mfd_uncertainty_weight") != null && arguments.get("mfd_uncertainty_power") != null) ||
                (arguments.get("reweight") != null)) {
            double weight = arguments.get("reweight") != null ? 1 : arguments.getDouble("mfd_uncertainty_weight");
            runner.setUncertaintyWeightedMFDWeights(
                    weight, //set default for reweighting
                    arguments.getDouble("mfd_uncertainty_power"),
                    arguments.getDouble("mfd_uncertainty_scalar"));
        } else {
            throw new IOException("Neither eq/ineq , nor uncertainty weights provided for MFD constraint setup");
        }

        if (arguments.get("enable_tvz_mfd") != null) {
            runner.setEnableTvzMFDs(arguments.getBoolean("enable_tvz_mfd"));
        }
        double minMagSans = arguments.getDouble("min_mag_sans");
        double minMagTvz = arguments.getDouble("min_mag_tvz");
        runner.setMinMags(minMagSans, minMagTvz);

        double maxMagSans = arguments.getDouble("max_mag_sans");
        double maxMagTvz = arguments.getDouble("max_mag_tvz");
        String maxMagType = arguments.get("max_mag_type");
        runner.setMaxMags(maxMagType, maxMagSans, maxMagTvz);

        double srf_sans = arguments.getDouble("sans_slip_rate_factor", 1.0);
        double srf_tvz = arguments.getDouble("tvz_slip_rate_factor", 1.0);
        runner.setSlipRateFactor(srf_sans, srf_tvz);

        if (arguments.get("reweight") != null) {
            runner.setReweightTargetQuantity("MAD");
        }

        if (arguments.get("slip_use_scaling") != null) {
            //V3x config
            double weight = arguments.get("reweight") != null ? 1 : arguments.getDouble("slip_uncertainty_weight");
            runner.setSlipRateUncertaintyConstraint(
                    weight, // set default for reweighting
                    arguments.getDouble("slip_uncertainty_scaling_factor"));


            runner.setUnmodifiedSlipRateStdvs(!arguments.getBoolean("slip_use_scaling"));// True means no slips scaling and vice - versa
        } else if (arguments.get("slip_rate_weighting_type") != null && arguments.get("slip_rate_weighting_type").equals("UNCERTAINTY_ADJUSTED")) {
            // Deprecated...
            runner.setSlipRateUncertaintyConstraint(
                    arguments.getInteger("slip_rate_weight"),
                    arguments.getInteger("slip_uncertainty_scaling_factor"));
        } else if (arguments.get("slip_rate_normalized_weight") != null) {
            // covers UCERF3 style SR constraints
            runner.setSlipRateConstraint(
                    arguments.get("slip_rate_weighting_type"),
                    arguments.getDouble("slip_rate_normalized_weight"),
                    arguments.getDouble("slip_rate_unnormalized_weight"));
        } else {
            throw new IOException("invalid slip constraint weight setup {ta}");
        }

        if (arguments.get("paleo_rate_constraint") != null) {
            double weight = arguments.get("reweight") != null ? 1 : arguments.getDouble("paleo_rate_constraint_weight");
            runner.setPaleoRateConstraints(
                    weight, //set default for reweighting
                    arguments.getDouble("paleo_parent_rate_smoothness_constraint_weight"),
                    arguments.get("paleo_rate_constraint"),
                    arguments.get("paleo_probability_model"));
        }

        if (arguments.get("scaling_relationship") != null && arguments.get("scaling_recalc_mag") != null) {
            SimplifiedScalingRelationship sr = (SimplifiedScalingRelationship) NZSHM22_PythonGateway.getScalingRelationship("SimplifiedScalingRelationship");


            if (arguments.get("scaling_relationship").equals("SIMPLE_CRUSTAL")) {
                sr.setupCrustal(arguments.getDouble("scaling_c_val_dip_slip"),
                        arguments.getDouble("scaling_c_val_strike_slip"));
                runner.setScalingRelationship(sr, arguments.getBoolean("scaling_recalc_mag"));
            } else if (arguments.get("scaling_relationship").equals("SIMPLE_SUBDUCTION")) {
                sr.setupSubduction(arguments.getDouble("scaling_c_val"));
                runner.setScalingRelationship(sr, arguments.getBoolean("scaling_recalc_mag"));
            } else {
                runner.setScalingRelationship(arguments.get("scaling_relationship"), arguments.getBoolean("scaling_recalc_mag"));
            }
        }

        runner
                .setInversionSeconds(arguments.getLong("max_inversion_time") * 60)
                .setEnergyChangeCompletionCriteria(0, arguments.getDouble("completion_energy"), 1)
                .setSelectionInterval(arguments.getInteger("selection_interval_secs"))
                .setNumThreadsPerSelector(arguments.getInteger("threads_per_selector"))
                .setNonnegativityConstraintType(arguments.get("non_negativity_function"))
                .setPerturbationFunction(arguments.get("perturbation_function"));

        runner.setRuptureSetFile(new File(inputPath, arguments.get("rupture_set")));

        if (arguments.get("averaging_threads") != null) {
            runner.setInversionAveraging(
                    arguments.getInteger("averaging_threads"),
                    arguments.getInteger("averaging_interval_secs"));
        }

        if (arguments.get("cooling_schedule") != null) {
            runner.setCoolingSchedule(arguments.get("cooling_schedule"));
        }
    }

    public void saveSolution(FaultSystemSolution solution) throws IOException {
        File solutionFile = new File(outputPath, "InversionSolution.zip");
        solution.write(solutionFile);
    }

    public void saveRupSet(FaultSystemRupSet rupSet, NZSHM22_CoulombRuptureSetBuilder builder) throws IOException {
        rupSet.write(new File(outputPath, builder.getDescriptiveName() + ".zip"));
    }

    public void saveRupSet(FaultSystemRupSet rupSet, NZSHM22_SubductionRuptureSetBuilder builder) throws IOException {
        rupSet.write(new File(outputPath, builder.getDescriptiveName() + ".zip"));
    }

    /**
     * Runs and saves an inversion based on Parameters.NZSHM22.INVERSION_CRUSTAL
     *
     * @return
     * @throws IOException
     * @throws DocumentException
     */
    public static FaultSystemSolution runNZSHM22CrustalInversion() throws IOException, DocumentException {
        ParameterRunner parameterRunner = new ParameterRunner(Parameters.NZSHM22.INVERSION_CRUSTAL);
        NZSHM22_CrustalInversionRunner runner = NZSHM22_PythonGateway.getCrustalInversionRunner();
        parameterRunner.ensurePaths();
        parameterRunner.setUpInversionRunner(runner);
        FaultSystemSolution solution = runner.runInversion();
        parameterRunner.saveSolution(solution);
        return solution;
    }

    /**
     * Builds and saves a rupture set based on Parameters.NZSHM22.RUPSET_CRUSTAL
     *
     * @return
     * @throws IOException
     * @throws DocumentException
     */
    public static FaultSystemRupSet buildNZSHM22CoulombCrustalRupset() throws IOException, DocumentException {
        ParameterRunner parameterRunner = new ParameterRunner(Parameters.NZSHM22.RUPSET_CRUSTAL);
        NZSHM22_CoulombRuptureSetBuilder builder = new NZSHM22_CoulombRuptureSetBuilder();
        parameterRunner.ensurePaths();
        parameterRunner.setUpCoulombCrustalRuptureSetBuilder(builder);
        FaultSystemRupSet rupSet = builder.buildRuptureSet();
        parameterRunner.saveRupSet(rupSet, builder);
        return rupSet;
    }

    /**
     * Builds and saves a subduction rupture set.
     *
     * @param parameters
     * @return
     * @throws IOException
     * @throws DocumentException
     */
    public static FaultSystemRupSet buildNZSHM22SubductionRupset(Parameters parameters) throws IOException, DocumentException {
        ParameterRunner parameterRunner = new ParameterRunner(parameters);
        NZSHM22_SubductionRuptureSetBuilder builder = new NZSHM22_SubductionRuptureSetBuilder();
        parameterRunner.ensurePaths();
        parameterRunner.setUpSubductionRuptureSetBuilder(builder);
        FaultSystemRupSet rupSet = builder.buildRuptureSet();
        parameterRunner.saveRupSet(rupSet, builder);
        return rupSet;
    }

    /**
     * Builds and saves a subduction rupture set based on Parameters.NZSHM22.RUPSET_HIKURANGI
     *
     * @return
     * @throws IOException
     * @throws DocumentException
     */
    public static FaultSystemRupSet buildNZSHM22HikurangiRupset() throws IOException, DocumentException {
        Parameters parameters = Parameters.NZSHM22.RUPSET_HIKURANGI.getParameters();
        return buildNZSHM22SubductionRupset(parameters);
    }

    /**
     * Builds and saves a subduction rupture set based on Parameters.NZSHM22.RUPSET_PUYSEGUR
     *
     * @return
     * @throws IOException
     * @throws DocumentException
     */
    public static FaultSystemRupSet buildNZSHM22PuysegurRupset() throws IOException, DocumentException {
        Parameters parameters = Parameters.NZSHM22.RUPSET_PUYSEGUR.getParameters();
        return buildNZSHM22SubductionRupset(parameters);
    }

    public static void main(String[] args) throws IOException, DocumentException {
        // runNZSHM22CrustalInversion();
        buildNZSHM22CoulombCrustalRupset();
        // buildNZSHM22HikurangiRupset();
        // buildNZSHM22PuysegurRupset();
    }
}

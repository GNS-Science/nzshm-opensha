package nz.cri.gns.NZSHM22.opensha.util;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import nz.cri.gns.NZSHM22.opensha.calc.SimplifiedScalingRelationship;
import nz.cri.gns.NZSHM22.util.NZSHM22_ReportPageGen;
import org.dom4j.DocumentException;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * A helper class to run an inversion based on a previous run in Toshi. Allows us to use
 * the inversion arguments from a graphQL query result for easier and more accurate replication
 * of results.
 * <p>
 * To use:
 * - Find the run in Toshi and with the browser's dev tool Network tab open, open Toshi's
 * DETAIL tab of the run. find the grapQL query result for InversionSolutionDetailTabQuery
 * and copy the value of the meta value as an array into a JSON file.
 * - Download the rupture set
 * - in the main method of this class, point to the rupture set and the JSON file.
 */
public class PythonGatewayJsonRunner {

    /**
     * Helper class for reading JSON
     */
    static class KVPair {
        public String k;
        public String v;
    }

    /**
     * Helper class to read JSON data
     */
    static class MapWithPrimitives extends HashMap<String, String> {
        public double getDouble(String key) {
            String value = get(key);
            return Double.parseDouble(value);
        }

        public double getDouble(String key, double defaultValue) {
            if (get(key) == null) {
                return defaultValue;
            } else {
                return getDouble(key);
            }
        }

        public int getInteger(String key) {
            String value = get(key);
            return Integer.parseInt(value);
        }

        public long getLong(String key) {
            String value = get(key);
            return Long.parseLong(value);
        }

        public boolean getBoolean(String key) {
            return get(key) != null && get(key).equalsIgnoreCase("true");
        }
    }

    /**
     * Reads a JSON file as generated by our GraphQl query into a MapWithPrimitives
     *
     * @param path the path to the JSON file
     * @return a map made up of the key/value pairs of the JSON file
     * @throws IOException if anything goes wrong
     */
    static MapWithPrimitives readArguments(String path) throws IOException {
        JsonReader reader = new JsonReader(new FileReader(path));
        Gson gson = new Gson();
        KVPair[] data = gson.fromJson(reader, KVPair[].class);

        MapWithPrimitives arguments = new MapWithPrimitives();
        for (KVPair pair : data) {
            arguments.put(pair.k, pair.v);
        }

        return arguments;
    }

    /**
     * Creates an inversion runner based on the arguments. Ported from runzi.
     *
     * @param arguments      parsed in JSON argument
     * @param ruptureSetPath path to the rupture set file
     * @return the runner
     * @throws IOException if anything goes wrong
     */
    static NZSHM22_PythonGateway.CachedCrustalInversionRunner setUpRunner(MapWithPrimitives arguments, String ruptureSetPath) throws IOException {

        NZSHM22_PythonGateway.CachedCrustalInversionRunner inversion_runner = NZSHM22_PythonGateway.getCrustalInversionRunner();

        inversion_runner.setSpatialSeisPDF(arguments.get("spatial_seis_pdf"));
        inversion_runner.setDeformationModel(arguments.get("deformation_model"));
        inversion_runner.setGutenbergRichterMFD(
                arguments.getDouble("mfd_mag_gt_5_sans"),
                arguments.getDouble("mfd_mag_gt_5_tvz"),
                arguments.getDouble("mfd_b_value_sans"),
                arguments.getDouble("mfd_b_value_tvz"),
                arguments.getDouble("mfd_transition_mag")
        );
        if (arguments.get("mfd_equality_weight") != null && arguments.get("mfd_inequality_weight") != null) {
            inversion_runner.setGutenbergRichterMFDWeights(
                    arguments.getDouble("mfd_equality_weight"),
                    arguments.getDouble("mfd_inequality_weight"));
        } else if ((arguments.get("mfd_uncertainty_weight") != null && arguments.get("mfd_uncertainty_power") != null) ||
                (arguments.get("reweight") != null)) {
            double weight = arguments.get("reweight") != null ? 1 : arguments.getDouble("mfd_uncertainty_weight");
            inversion_runner.setUncertaintyWeightedMFDWeights(
                    weight, //set default for reweighting
                    arguments.getDouble("mfd_uncertainty_power"),
                    arguments.getDouble("mfd_uncertainty_scalar"));
        } else {
            throw new IOException("Neither eq/ineq , nor uncertainty weights provided for MFD constraint setup");
        }

        if (arguments.get("enable_tvz_mfd") != null) {
            inversion_runner.setEnableTvzMFDs(arguments.getBoolean("enable_tvz_mfd"));
        }
        double minMagSans = arguments.getDouble("min_mag_sans");
        double minMagTvz = arguments.getDouble("min_mag_tvz");
        inversion_runner.setMinMags(minMagSans, minMagTvz);

        double maxMagSans = arguments.getDouble("max_mag_sans");
        double maxMagTvz = arguments.getDouble("max_mag_tvz");
        String maxMagType = arguments.get("max_mag_type");
        inversion_runner.setMaxMags(maxMagType, maxMagSans, maxMagTvz);

        double srf_sans = arguments.getDouble("sans_slip_rate_factor", 1.0);
        double srf_tvz = arguments.getDouble("tvz_slip_rate_factor", 1.0);
        inversion_runner.setSlipRateFactor(srf_sans, srf_tvz);

        if (arguments.get("reweight") != null) {
            inversion_runner.setReweightTargetQuantity("MAD");
        }

        if (arguments.get("slip_use_scaling") != null) {
            //V3x config
            double weight = arguments.get("reweight") != null ? 1 : arguments.getDouble("slip_uncertainty_weight");
            inversion_runner.setSlipRateUncertaintyConstraint(
                    weight, // set default for reweighting
                    arguments.getDouble("slip_uncertainty_scaling_factor"));


            inversion_runner.setUnmodifiedSlipRateStdvs(!arguments.getBoolean("slip_use_scaling"));// True means no slips scaling and vice - versa
        } else if (arguments.get("slip_rate_weighting_type") != null && arguments.get("slip_rate_weighting_type").equals("UNCERTAINTY_ADJUSTED")) {
            // Deprecated...
            inversion_runner.setSlipRateUncertaintyConstraint(
                    arguments.getInteger("slip_rate_weight"),
                    arguments.getInteger("slip_uncertainty_scaling_factor"));
        } else if (arguments.get("slip_rate_normalized_weight") != null) {
            // covers UCERF3 style SR constraints
            inversion_runner.setSlipRateConstraint(
                    arguments.get("slip_rate_weighting_type"),
                    arguments.getDouble("slip_rate_normalized_weight"),
                    arguments.getDouble("slip_rate_unnormalized_weight"));
        } else {
            throw new IOException("invalid slip constraint weight setup {ta}");
        }

        if (arguments.get("paleo_rate_constraint") != null) {
            double weight = arguments.get("reweight") != null ? 1 : arguments.getDouble("paleo_rate_constraint_weight");
            inversion_runner.setPaleoRateConstraints(
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
                inversion_runner.setScalingRelationship(sr, arguments.getBoolean("scaling_recalc_mag"));
            } else if (arguments.get("scaling_relationship").equals("SIMPLE_SUBDUCTION")) {
                sr.setupSubduction(arguments.getDouble("scaling_c_val"));
                inversion_runner.setScalingRelationship(sr, arguments.getBoolean("scaling_recalc_mag"));
            } else {
                inversion_runner.setScalingRelationship(arguments.get("scaling_relationship"), arguments.getBoolean("scaling_recalc_mag"));
            }
        }

        inversion_runner
                .setInversionSeconds(arguments.getLong("max_inversion_time") * 60)
                .setEnergyChangeCompletionCriteria(0, arguments.getDouble("completion_energy"), 1)
                .setSelectionInterval(arguments.getInteger("selection_interval_secs"))
                .setNumThreadsPerSelector(arguments.getInteger("threads_per_selector"))
                .setNonnegativityConstraintType(arguments.get("non_negativity_function"))
                .setPerturbationFunction(arguments.get("perturbation_function"));

        inversion_runner.setRuptureSetFile(new File(ruptureSetPath));

        if (arguments.get("averaging_threads") != null) {
            inversion_runner.setInversionAveraging(
                    arguments.getInteger("averaging_threads"),
                    arguments.getInteger("averaging_interval_secs"));
        }

        if (arguments.get("cooling_schedule") != null) {
            inversion_runner.setCoolingSchedule(arguments.get("cooling_schedule"));
        }

        return inversion_runner;
    }


    public static void main(String[] args) throws IOException, DocumentException {

        File outputDir = new File("TEST/inversions");
        String ruptureFile = "C:\\Users\\user\\Downloads\\NZSHM22_RuptureSet-UnVwdHVyZUdlbmVyYXRpb25UYXNrOjEwMDAzOA==(2).zip";
        MapWithPrimitives arguments = readArguments("TEST/arguments68.json");

        NZSHM22_PythonGateway.CachedCrustalInversionRunner runner = setUpRunner(arguments, ruptureFile);

        // runner.setRepeatable(true)
//        runner.setIterationCompletionCriteria(10);
//        runner.setSelectionIterations(10);
        runner.setInversionMinutes(4);
        runner.runInversion();

        System.out.println("Solution MFDS...");
        for (ArrayList<String> row : runner.getTabularSolutionMfds()) {
            System.out.println(row);
        }
        System.out.println("Solution MFDS V2 ...");
        for (ArrayList<String> row : runner.getTabularSolutionMfdsV2()) {
            System.out.println(row);
        }
        runner.writeSolution(new File(outputDir, "crustalInversion.zip").getAbsolutePath());

        NZSHM22_ReportPageGen reportPageGen = new NZSHM22_ReportPageGen();
        reportPageGen.setName("Min Mag = 6.8")
                .setOutputPath("/tmp/reports/m68_sampler1")
                .setFillSurfaces(true)
                .setPlotLevel(null)
                .addPlot("SolMFDPlot")
//                .setSolution("/home/chrisbc/DEV/GNS/AWS_S3_DATA/WORKING/downloads/SW52ZXJzaW9uU29sdXRpb246MTUzMzYuMExIQkxw/NZSHM22_InversionSolution-QXV0b21hdGlvblRhc2s6NTM3MGN3MmJw.zip");
                .setSolution(new File(outputDir, "crustalInversion.zip").toString());
        reportPageGen.generatePage();


    }

}

package nz.cri.gns.NZSHM22.util;

import com.google.common.base.Preconditions;
import java.io.File;
import java.io.IOException;
import java.util.List;
import nz.cri.gns.NZSHM22.opensha.hazard.joint.HazardReportSource;
import nz.cri.gns.NZSHM22.opensha.hazard.joint.JointHazardInput;
import nz.cri.gns.NZSHM22.opensha.hazard.joint.JointHazardInput.GmmMode;
import nz.cri.gns.NZSHM22.opensha.hazard.joint.JointHazardMapCalculator;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;

/**
 * Drives hazard map and hazard curve generation for a crustal + subduction inversion solution. See
 * {@link JointHazardInput} for what the calculation assumes about the solution.
 */
public class JointHazardRunner {

    /** Output directory used when {@code --out} is not given. */
    public static final File DEFAULT_OUTPUT_DIR = new File("hazard");

    protected JointHazardRunner() {}

    /** A parsed command line: which solutions to calculate, how and where to. */
    public static class Options {
        protected final List<File> solutionFiles;
        protected final File outputDir;
        protected final double spacing;
        protected final GmmMode mode;

        /**
         * @param solutionFiles the solutions to calculate, in the order they are merged
         * @param outputDir directory the maps and curves are written to
         * @param spacing map resolution in degrees
         * @param mode how ground motions are calculated; forced to {@link
         *     GmmMode#PER_TECTONIC_REGION} when there is more than one solution, because merged
         *     solutions cannot contain joint ruptures
         */
        public Options(List<File> solutionFiles, File outputDir, double spacing, GmmMode mode) {
            Preconditions.checkArgument(
                    !solutionFiles.isEmpty(), "need at least one solution file");
            this.solutionFiles = List.copyOf(solutionFiles);
            this.outputDir = outputDir;
            this.spacing = spacing;
            this.mode = solutionFiles.size() > 1 ? GmmMode.PER_TECTONIC_REGION : mode;
        }

        public List<File> getSolutionFiles() {
            return solutionFiles;
        }

        public File getOutputDir() {
            return outputDir;
        }

        public double getSpacing() {
            return spacing;
        }

        public GmmMode getMode() {
            return mode;
        }
    }

    /**
     * Loads the solutions, validates them and writes the maps and site curves.
     *
     * <p>More than one solution is merged into a single ERF; see {@link JointHazardInput#combined}.
     */
    public static void run(Options options) throws IOException {
        FaultSystemSolution[] solutions = HazardReportSource.load(options.getSolutionFiles());
        JointHazardInput input =
                solutions.length > 1
                        ? JointHazardInput.combined(solutions)
                        : new JointHazardInput(solutions[0]).setGmmMode(options.getMode());
        run(input, options.getOutputDir(), options.getSpacing());
    }

    protected static void run(JointHazardInput input, File outputDir, double spacing)
            throws IOException {
        input.setSpacing(spacing);

        JointHazardInput.ValidationResult validation = input.validate();
        System.out.println("Solution: " + validation);
        if (input.getGmmMode() == GmmMode.JOINT_RUPTURE) {
            if (validation.getNumSingleSectionWithRate() > 0) {
                System.out.println(
                        "Warning: "
                                + validation.getNumSingleSectionWithRate()
                                + " single-section ruptures carry a rate. A single-section surface"
                                + " carries no tectonic region types, so the GMM classifies those"
                                + " by comparing their magnitude against crustal and interface area"
                                + " scaling. The two differ by only 0.2 magnitude units, so a"
                                + " rupture near the midpoint may be calculated with the wrong"
                                + " component GMM.");
            }
            if (!validation.isJoint()) {
                System.out.println(
                        "Warning: this solution has no joint ruptures. The experimental joint GMM"
                                + " will still work, but it adds nothing over per-partition GMMs.");
            }
        }

        System.out.println(
                "Calculating hazard for "
                        + input.getRegion().getNodeCount()
                        + " sites at "
                        + spacing
                        + " degree spacing using "
                        + input.getGmmMode());
        JointHazardMapCalculator calculator = new JointHazardMapCalculator(input);
        calculator.writeMaps(outputDir);
        for (double period : input.getPeriods()) {
            calculator.writeSiteCurves(outputDir, JointHazardInput.defaultSites(), period);
        }
        System.out.println("Wrote hazard maps and curves to " + outputDir.getAbsolutePath());
    }
}

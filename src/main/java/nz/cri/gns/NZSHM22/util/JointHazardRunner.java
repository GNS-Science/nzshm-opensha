package nz.cri.gns.NZSHM22.util;

import com.google.common.base.Preconditions;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import nz.cri.gns.NZSHM22.opensha.hazard.joint.HazardReportSource;
import nz.cri.gns.NZSHM22.opensha.hazard.joint.JointHazardInput;
import nz.cri.gns.NZSHM22.opensha.hazard.joint.JointHazardInput.GmmMode;
import nz.cri.gns.NZSHM22.opensha.hazard.joint.JointHazardMapCalculator;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;

/**
 * Drives hazard map and hazard curve generation for a crustal + subduction inversion solution. See
 * {@link JointHazardInput} for what the calculation assumes about the solution.
 *
 * <p>Usage:
 *
 * <pre>
 * JointHazardRunner [--per-trt] &lt;solution.zip&gt; [&lt;solution.zip&gt; ...]
 *                   [--out &lt;dir&gt;] [--spacing &lt;degrees&gt;]
 * </pre>
 *
 * <ul>
 *   <li>one solution — calculated with the experimental joint GMM, which is the only mode that can
 *       handle ruptures spanning both tectonic region types.
 *   <li>one solution with {@code --per-trt} — a solution holding crustal and subduction ruptures
 *       but no joint ruptures, each calculated with the GMM for its own tectonic region type.
 *   <li>two or more solutions, typically a crustal and one or more subduction inversions — merged
 *       into one ERF and calculated per tectonic region type. {@code --per-trt} is implied, because
 *       there is no joint rupture to be had across solutions that were inverted separately.
 * </ul>
 *
 * <p>Solutions are positional and the options are named, so a mistyped path is reported as a
 * missing file rather than being quietly taken for something else.
 */
public class JointHazardRunner {

    public static final String PER_TRT_FLAG = "--per-trt";
    public static final String OUT_FLAG = "--out";
    public static final String SPACING_FLAG = "--spacing";

    /** Output directory used when {@code --out} is not given. */
    public static final File DEFAULT_OUTPUT_DIR = new File("hazard");

    public static final String USAGE =
            "Usage: JointHazardRunner ["
                    + PER_TRT_FLAG
                    + "] <solution.zip> [<solution.zip> ...] ["
                    + OUT_FLAG
                    + " <dir>] ["
                    + SPACING_FLAG
                    + " <degrees>]";

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
     * Parses a command line. Every argument that is not a flag or a flag's value is taken as a
     * solution file and has to exist, so that a typo cannot be silently reinterpreted.
     *
     * @throws IllegalArgumentException if a flag is unknown or missing its value, if a solution
     *     file does not exist, if the spacing is not a positive number, or if no solution is given
     */
    public static Options parse(String[] args) {
        List<File> solutionFiles = new ArrayList<>();
        File outputDir = DEFAULT_OUTPUT_DIR;
        double spacing = JointHazardInput.DEFAULT_SPACING;
        GmmMode mode = GmmMode.JOINT_RUPTURE;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (PER_TRT_FLAG.equals(arg)) {
                mode = GmmMode.PER_TECTONIC_REGION;
            } else if (OUT_FLAG.equals(arg)) {
                outputDir = new File(valueOf(args, ++i, OUT_FLAG));
            } else if (SPACING_FLAG.equals(arg)) {
                String value = valueOf(args, ++i, SPACING_FLAG);
                try {
                    spacing = Double.parseDouble(value);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                            SPACING_FLAG + " needs a number, got: " + value, e);
                }
                Preconditions.checkArgument(
                        spacing > 0, "%s must be positive, got %s", SPACING_FLAG, spacing);
            } else if (arg.startsWith("--")) {
                throw new IllegalArgumentException("Unknown option " + arg + "\n" + USAGE);
            } else {
                File solutionFile = new File(arg);
                Preconditions.checkArgument(
                        solutionFile.isFile(),
                        "Solution file %s does not exist. Solutions are positional; everything else"
                                + " is a named option.\n%s",
                        solutionFile.getAbsolutePath(),
                        USAGE);
                solutionFiles.add(solutionFile);
            }
        }

        Preconditions.checkArgument(
                !solutionFiles.isEmpty(), "Need at least one solution file.\n%s", USAGE);
        return new Options(solutionFiles, outputDir, spacing, mode);
    }

    /** The value that follows a flag, or a failure naming the flag that is missing it. */
    protected static String valueOf(String[] args, int index, String flag) {
        Preconditions.checkArgument(index < args.length, "%s needs a value.\n%s", flag, USAGE);
        return args[index];
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
                                + " will still work, but it adds nothing over per-partition GMMs."
                                + " Consider "
                                + PER_TRT_FLAG
                                + ".");
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

    public static void main(String[] args) throws IOException {
        Options options;
        try {
            options = parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.exit(1);
            return;
        }
        run(options);
    }
}

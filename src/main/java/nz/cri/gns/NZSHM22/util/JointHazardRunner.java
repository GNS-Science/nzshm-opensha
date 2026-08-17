package nz.cri.gns.NZSHM22.util;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
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
 * <ul>
 *   <li>{@code JointHazardRunner <solution.zip> [outputDir] [spacingInDegrees]} — one solution,
 *       calculated with the experimental joint GMM.
 *   <li>{@code JointHazardRunner --per-trt <solution.zip> [outputDir] [spacingInDegrees]} — one
 *       solution holding crustal and subduction ruptures but no joint ruptures, each calculated
 *       with the GMM for its own tectonic region type.
 *   <li>{@code JointHazardRunner <crustal.zip> <subduction.zip> [outputDir] [spacingInDegrees]} —
 *       two solutions merged into one ERF and calculated per tectonic region type.
 * </ul>
 */
public class JointHazardRunner {

    /** Calculates a single solution with the experimental joint GMM. */
    public static void run(File solutionFile, File outputDir, double spacing) throws IOException {
        run(solutionFile, outputDir, spacing, GmmMode.JOINT_RUPTURE);
    }

    /** Calculates a single solution in the given mode. */
    public static void run(File solutionFile, File outputDir, double spacing, GmmMode mode)
            throws IOException {
        System.out.println("Loading " + solutionFile.getAbsolutePath());
        FaultSystemSolution solution = FaultSystemSolution.load(solutionFile);
        run(new JointHazardInput(solution).setGmmMode(mode), outputDir, spacing);
    }

    /**
     * Calculates a crustal and a subduction solution together: the two are merged into one ERF, and
     * every source is calculated with the GMM for its own tectonic region type.
     */
    public static void run(File crustalFile, File subductionFile, File outputDir, double spacing)
            throws IOException {
        System.out.println("Loading crustal solution " + crustalFile.getAbsolutePath());
        FaultSystemSolution crustal = FaultSystemSolution.load(crustalFile);
        System.out.println("Loading subduction solution " + subductionFile.getAbsolutePath());
        FaultSystemSolution subduction = FaultSystemSolution.load(subductionFile);
        run(JointHazardInput.combined(crustal, subduction), outputDir, spacing);
    }

    private static void run(JointHazardInput input, File outputDir, double spacing)
            throws IOException {
        input.setSpacing(spacing);

        JointHazardInput.ValidationResult validation = input.validate();
        System.out.println("Solution: " + validation);
        if (input.getGmmMode() == GmmMode.JOINT_RUPTURE) {
            if (validation.getNumSingleSectionWithRate() > 0) {
                System.out.println(
                        "Warning: "
                                + validation.getNumSingleSectionWithRate()
                                + " single-section ruptures carry a rate. The GMM cannot tell from"
                                + " the surface alone whether those are crustal or interface and"
                                + " currently treats all of them as interface.");
            }
            if (!validation.isJoint()) {
                System.out.println(
                        "Warning: this solution has no joint ruptures. The experimental joint GMM"
                                + " will still work, but it adds nothing over per-partition GMMs."
                                + " Consider GmmMode.PER_TECTONIC_REGION.");
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
        GmmMode mode = GmmMode.JOINT_RUPTURE;
        if (args.length > 0 && args[0].equals("--per-trt")) {
            mode = GmmMode.PER_TECTONIC_REGION;
            args = Arrays.copyOfRange(args, 1, args.length);
        }
        if (args.length < 1) {
            System.err.println(
                    "Usage: JointHazardRunner [--per-trt] <solution.zip> [outputDir]"
                            + " [spacingInDegrees]");
            System.err.println(
                    "       JointHazardRunner <crustal.zip> <subduction.zip> [outputDir]"
                            + " [spacingInDegrees]");
            System.exit(1);
        }

        // a second solution file means crustal + subduction, calculated together
        boolean twoSolutions = args.length > 1 && args[1].toLowerCase().endsWith(".zip");
        int rest = twoSolutions ? 2 : 1;
        File outputDir = args.length > rest ? new File(args[rest]) : new File("hazard");
        double spacing =
                args.length > rest + 1
                        ? Double.parseDouble(args[rest + 1])
                        : JointHazardInput.DEFAULT_SPACING;

        if (twoSolutions) {
            run(new File(args[0]), new File(args[1]), outputDir, spacing);
        } else {
            run(new File(args[0]), outputDir, spacing, mode);
        }
    }
}

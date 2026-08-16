package nz.cri.gns.NZSHM22.util;

import java.io.File;
import java.io.IOException;
import nz.cri.gns.NZSHM22.opensha.hazard.joint.JointHazardInput;
import nz.cri.gns.NZSHM22.opensha.hazard.joint.JointHazardMapCalculator;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;

/**
 * Drives hazard map and hazard curve generation for a joint (crustal + subduction) inversion
 * solution. See {@link JointHazardInput} for what the calculation assumes about the solution.
 *
 * <p>Usage: {@code JointHazardRunner <solution.zip> [outputDir] [spacingInDegrees]}
 */
public class JointHazardRunner {

    public static void run(File solutionFile, File outputDir, double spacing) throws IOException {
        System.out.println("Loading " + solutionFile.getAbsolutePath());
        FaultSystemSolution solution = FaultSystemSolution.load(solutionFile);

        JointHazardInput input = new JointHazardInput(solution).setSpacing(spacing);

        JointHazardInput.ValidationResult validation = input.validate();
        System.out.println("Solution: " + validation);
        if (validation.getNumSingleSectionWithRate() > 0) {
            System.out.println(
                    "Warning: "
                            + validation.getNumSingleSectionWithRate()
                            + " single-section ruptures carry a rate. The GMM cannot tell from the"
                            + " surface alone whether those are crustal or interface and currently"
                            + " treats all of them as interface.");
        }
        if (!validation.isJoint()) {
            System.out.println(
                    "Warning: this solution has no joint ruptures. The experimental joint GMM will"
                            + " still work, but it adds nothing over per-partition GMMs.");
        }

        System.out.println(
                "Calculating hazard for "
                        + input.getRegion().getNodeCount()
                        + " sites at "
                        + spacing
                        + " degree spacing");
        JointHazardMapCalculator calculator = new JointHazardMapCalculator(input);
        calculator.writeMaps(outputDir);
        for (double period : input.getPeriods()) {
            calculator.writeSiteCurves(outputDir, JointHazardInput.defaultSites(), period);
        }
        System.out.println("Wrote hazard maps and curves to " + outputDir.getAbsolutePath());
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println(
                    "Usage: JointHazardRunner <solution.zip> [outputDir] [spacingInDegrees]");
            System.exit(1);
        }
        File solutionFile = new File(args[0]);
        File outputDir = args.length > 1 ? new File(args[1]) : new File("hazard");
        double spacing =
                args.length > 2 ? Double.parseDouble(args[2]) : JointHazardInput.DEFAULT_SPACING;
        run(solutionFile, outputDir, spacing);
    }
}

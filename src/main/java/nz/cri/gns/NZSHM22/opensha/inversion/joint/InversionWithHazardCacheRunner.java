package nz.cri.gns.NZSHM22.opensha.inversion.joint;

import java.io.File;
import java.io.IOException;
import nz.cri.gns.NZSHM22.opensha.hazard.joint.HazardComparisonReport;
import org.dom4j.DocumentException;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;

/**
 * Runs a joint inversion like {@link InversionRunner}, but attaches the hazard map curves to the
 * solution before writing it, see {@link
 * HazardComparisonReport#attachHazardCurves(FaultSystemSolution)}. Hazard reports on the solution
 * then read the curves instead of calculating them. Used by {@code scripts/batch_inversion.py}.
 */
public class InversionWithHazardCacheRunner {

    /**
     * Runs the inversion, attaches the hazard map curves and writes the solution.
     *
     * @param configPath the inversion config
     * @param solutionFile where the solution is written
     * @return the solution that was written
     */
    public FaultSystemSolution run(String configPath, File solutionFile)
            throws IOException, DocumentException {
        FaultSystemSolution solution = addHazardCache(runInversion(configPath));
        solution.write(solutionFile);
        System.out.println("Wrote solution to " + solutionFile.getAbsolutePath());
        return solution;
    }

    protected FaultSystemSolution runInversion(String configPath)
            throws IOException, DocumentException {
        return new InversionRunner(configPath).run();
    }

    protected FaultSystemSolution addHazardCache(FaultSystemSolution solution) {
        return HazardComparisonReport.attachHazardCurves(solution);
    }

    /**
     * Entry point.
     *
     * @param args the config path and the output solution zip
     */
    public static void main(String[] args) throws IOException, DocumentException {
        if (args.length != 2) {
            System.err.println(
                    "Usage: InversionWithHazardCacheRunner <configPath> <outputSolutionZip>");
            System.exit(1);
        }
        new InversionWithHazardCacheRunner().run(args[0], new File(args[1]));
    }
}

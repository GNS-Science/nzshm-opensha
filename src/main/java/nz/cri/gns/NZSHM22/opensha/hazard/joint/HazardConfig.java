package nz.cri.gns.NZSHM22.opensha.hazard.joint;

import com.google.common.base.Preconditions;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;

/**
 * One named hazard source of a {@link HazardComparisonReport}: a display name and the {@link
 * JointHazardInput} that the hazard is calculated from.
 *
 * <p>The two ways a NZSHM22 hazard source is put together each have a factory:
 *
 * <ul>
 *   <li>{@link #combined} for the classic model, where separate crustal and subduction inversions
 *       are merged into one ERF and each source is calculated with the GMM for its own tectonic
 *       region type,
 *   <li>{@link #joint} for a joint inversion, whose ruptures may span both tectonic region types
 *       and are calculated with the experimental joint GMM.
 * </ul>
 *
 * <p>The underlying inputs stay accessible through {@link #getInput()}, so region, periods and
 * thread count can be set before the report is generated. They are locked once the calculation is
 * set up.
 */
public class HazardConfig {

    protected final String name;
    protected final JointHazardInput input;

    /**
     * @param name display name, used in headings, plot titles and image file names
     * @param input the calculation inputs
     */
    public HazardConfig(String name, JointHazardInput input) {
        Preconditions.checkArgument(name != null && !name.isBlank(), "need a name");
        this.name = name;
        this.input = Preconditions.checkNotNull(input, "need inputs");
    }

    /**
     * A hazard source made up of any number of solutions calculated together, typically a crustal
     * and one or more subduction inversions. They are merged into a single ERF where each source is
     * calculated with the GMM for its own tectonic region type. See {@link
     * JointHazardInput#combined}.
     */
    public static HazardConfig combined(String name, FaultSystemSolution... solutions) {
        return new HazardConfig(name, JointHazardInput.combined(solutions));
    }

    /** As {@link #combined(String, FaultSystemSolution...)}, loading the solutions from disk. */
    public static HazardConfig combined(String name, List<File> solutionFiles) throws IOException {
        Preconditions.checkArgument(!solutionFiles.isEmpty(), "need at least one solution file");
        return combined(name, load(solutionFiles));
    }

    /**
     * A hazard source made up of a single joint solution, calculated with the experimental joint
     * GMM. Its ruptures may span crustal and subduction sections.
     */
    public static HazardConfig joint(String name, FaultSystemSolution solution) {
        return new HazardConfig(
                name,
                new JointHazardInput(solution).setGmmMode(JointHazardInput.GmmMode.JOINT_RUPTURE));
    }

    /** As {@link #joint(String, FaultSystemSolution)}, loading the solution from disk. */
    public static HazardConfig joint(String name, File solutionFile) throws IOException {
        return joint(name, FaultSystemSolution.load(solutionFile));
    }

    /**
     * The zip files in a directory, sorted by name. Convenience for pointing {@link
     * #combined(String, List)} at a directory of inversion solutions.
     *
     * @throws IllegalArgumentException if the directory does not exist or holds no zip file
     */
    public static List<File> solutionsIn(File directory) {
        Preconditions.checkArgument(
                directory.isDirectory(), "%s is not a directory", directory.getAbsolutePath());
        File[] files =
                directory.listFiles(f -> f.getName().toLowerCase(Locale.ROOT).endsWith(".zip"));
        Preconditions.checkArgument(
                files != null && files.length > 0,
                "No solution zip files in %s",
                directory.getAbsolutePath());
        List<File> solutions = new ArrayList<>(Arrays.asList(files));
        solutions.sort(File::compareTo);
        return solutions;
    }

    protected static FaultSystemSolution[] load(List<File> files) throws IOException {
        FaultSystemSolution[] solutions = new FaultSystemSolution[files.size()];
        for (int i = 0; i < solutions.length; i++) {
            System.out.println("Loading " + files.get(i).getAbsolutePath());
            solutions[i] = FaultSystemSolution.load(files.get(i));
        }
        return solutions;
    }

    public String getName() {
        return name;
    }

    public JointHazardInput getInput() {
        return input;
    }

    public FaultSystemSolution getSolution() {
        return input.getSolution();
    }

    /** The name reduced to lower case letters, digits and underscores, for use in file names. */
    public String getId() {
        String id = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        return id.replaceAll("^_|_$", "");
    }

    @Override
    public String toString() {
        return name + " (" + input.getGmmMode() + ")";
    }
}

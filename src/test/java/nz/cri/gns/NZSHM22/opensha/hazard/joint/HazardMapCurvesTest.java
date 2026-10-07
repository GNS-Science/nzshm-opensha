package nz.cri.gns.NZSHM22.opensha.hazard.joint;

import static nz.cri.gns.NZSHM22.opensha.hazard.joint.JointTestSolutions.*;
import static org.junit.Assert.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.commons.util.io.archive.ArchiveInput;
import org.opensha.commons.util.io.archive.ArchiveOutput;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;

/** Tests for {@link HazardMapCurves}. */
public class HazardMapCurvesTest {

    @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

    static GriddedRegion region(double spacing) {
        return new GriddedRegion(
                new Region(new Location(-41.6, 174.5), new Location(-41.1, 175.2)),
                spacing,
                GriddedRegion.ANCHOR_0_0);
    }

    static JointHazardInput input(FaultSystemSolution solution, double... periods) {
        return new JointHazardInput(solution).setRegion(region(0.25)).setPeriods(periods);
    }

    /** Fake curves, distinct per node, so that a mix-up shows. */
    static DiscretizedFunc[] curves(GriddedRegion region) {
        DiscretizedFunc[] curves = new DiscretizedFunc[region.getNodeCount()];
        for (int i = 0; i < curves.length; i++) {
            ArbitrarilyDiscretizedFunc curve = new ArbitrarilyDiscretizedFunc();
            curve.set(0.01, 0.1 / (i + 1));
            curve.set(0.1, 0.01 / (i + 1));
            curve.set(1, 0.001 / (i + 1));
            curves[i] = curve;
        }
        return curves;
    }

    static void assertCurvesEqual(DiscretizedFunc[] expected, DiscretizedFunc[] actual) {
        assertNotNull(actual);
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            for (int j = 0; j < expected[i].size(); j++) {
                assertEquals((float) expected[i].getX(j), (float) actual[i].getX(j), 0f);
                assertEquals(expected[i].getY(j), actual[i].getY(j), 1e-15);
            }
        }
    }

    @Test
    public void testWrittenWithSolution() throws Exception {
        FaultSystemSolution solution = makeSolution();
        JointHazardInput input = input(solution, 0d);
        DiscretizedFunc[] curves = curves(input.getRegion());
        HazardMapCurves.addTo(solution, input, Collections.singletonList(curves));

        File file = new File(tempFolder.getRoot(), "solution.zip");
        solution.write(file);
        FaultSystemSolution loaded = FaultSystemSolution.load(file);

        HazardMapCurves module = loaded.getModule(HazardMapCurves.class);
        assertNotNull(module);
        assertEquals(1, module.getKeys().size());
        assertCurvesEqual(
                curves,
                module.getCurves(HazardMapCurves.Key.of(input(loaded, 0d), 0d), input.getRegion()));
    }

    @Test
    public void testAddingKeepsExistingCurves() {
        FaultSystemSolution solution = makeSolution();
        GriddedRegion region = region(0.25);
        HazardMapCurves.addTo(
                solution, input(solution, 0d), Collections.singletonList(curves(region)));
        HazardMapCurves.addTo(
                solution, input(solution, 1d), Collections.singletonList(curves(region)));

        HazardMapCurves module = solution.getModule(HazardMapCurves.class);
        assertEquals(2, module.getKeys().size());
        assertTrue(module.contains(HazardMapCurves.Key.of(input(solution, 0d), 0d)));
        assertTrue(module.contains(HazardMapCurves.Key.of(input(solution, 1d), 1d)));
    }

    @Test
    public void testNotAttachedByCalculating() {
        FaultSystemSolution solution = makeSolution();
        new JointHazardMapCalculator(input(solution, 0d).setNumThreads(1)).calcHazardCurves();
        assertNull(solution.getModule(HazardMapCurves.class));
    }

    @Test
    public void testKeysDifferOnInputs() {
        FaultSystemSolution solution = makeSolution();
        HazardMapCurves.Key key = HazardMapCurves.Key.of(input(solution, 0d), 0d);
        assertEquals(key, HazardMapCurves.Key.of(input(solution, 0d), 0d));
        assertEquals(key, HazardMapCurves.Key.of(input(makeSolution(), 0d), 0d));
        assertNotEquals(key, HazardMapCurves.Key.of(input(solution, 1d), 1d));
        assertNotEquals(
                key,
                HazardMapCurves.Key.of(
                        input(solution, 0d)
                                .setGmmMode(JointHazardInput.GmmMode.PER_TECTONIC_REGION),
                        0d));
        assertNotEquals(
                key, HazardMapCurves.Key.of(input(solution, 0d).setRegion(region(0.2)), 0d));

        double[] rates = solution.getRateForAllRups().clone();
        rates[0] *= 2;
        FaultSystemSolution changed = new FaultSystemSolution(solution.getRupSet(), rates);
        assertNotEquals(key, HazardMapCurves.Key.of(input(changed, 0d), 0d));
    }

    @Test
    public void testOtherVersionIsIgnored() throws Exception {
        FaultSystemSolution solution = makeSolution();
        JointHazardInput input = input(solution, 0d);
        HazardMapCurves.Precomputed module =
                (HazardMapCurves.Precomputed)
                        HazardMapCurves.addTo(
                                solution,
                                input,
                                Collections.singletonList(curves(input.getRegion())));

        File file = new File(tempFolder.getRoot(), "module.zip");
        try (ArchiveOutput.ZipFileOutput out = new ArchiveOutput.ZipFileOutput(file)) {
            module.writeToArchive(out, null);
        }
        assertEquals(1, load(file).getKeys().size());

        setVersion(file, HazardMapCurves.CURVES_VERSION + 1);
        assertTrue(load(file).getKeys().isEmpty());
    }

    static HazardMapCurves.Precomputed load(File file) throws Exception {
        HazardMapCurves.Precomputed module = new HazardMapCurves.Precomputed();
        try (ArchiveInput.ZipFileInput in = new ArchiveInput.ZipFileInput(file)) {
            module.initFromArchive(in, null);
        }
        return module;
    }

    /** Rewrites the version recorded in the module index of an archive. */
    static void setVersion(File file, int version) throws Exception {
        String indexName =
                HazardMapCurves.Precomputed.DIR + HazardMapCurves.Precomputed.INDEX_FILE_NAME;
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipFile zip = new ZipFile(file)) {
            for (ZipEntry entry : Collections.list(zip.entries())) {
                try (InputStream in = zip.getInputStream(entry)) {
                    entries.put(entry.getName(), in.readAllBytes());
                }
            }
        }
        String index = new String(entries.get(indexName), StandardCharsets.UTF_8);
        String replaced = index.replaceFirst("\"version\":\\s*\\d+", "\"version\": " + version);
        assertNotEquals("index should record a version", index, replaced);
        entries.put(indexName, replaced.getBytes(StandardCharsets.UTF_8));
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
    }
}

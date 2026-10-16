package nz.cri.gns.NZSHM22.opensha.inversion.joint.reporting;

import static org.junit.Assert.*;

import java.io.File;
import java.util.List;
import nz.cri.gns.NZSHM22.opensha.inversion.joint.PartitionPredicate;
import nz.cri.gns.NZSHM22.opensha.ruptures.FaultSectionProperties;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.RupSetMetadata;
import org.opensha.sha.faultSurface.FaultSection;

/** Tests for {@link ParticipationChangeMapPlot}. */
public class ParticipationChangeMapPlotTest {

    static final double DELTA = 1e-9;
    static final double RADIUS = 25;

    @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

    /** Two sections ~250 km apart, one rupture each. */
    protected static FaultSystemSolution makeSolution(double[] rates) {
        List<FaultSection> sections =
                List.of(
                        MaxMagnitudeMapPlotTest.makeSection(0, 173),
                        MaxMagnitudeMapPlotTest.makeSection(1, 176));
        FaultSystemRupSet rupSet =
                FaultSystemRupSet.builder(sections, List.of(List.of(0), List.of(1)))
                        .rupMags(new double[] {7.0, 7.0})
                        .build();
        return new FaultSystemSolution(rupSet, rates);
    }

    /**
     * A crustal and a subduction section ~250 km apart, one rupture on each and a joint rupture
     * across both.
     */
    protected static FaultSystemSolution makeJointSolution(double[] rates) {
        FaultSection crustal = MaxMagnitudeMapPlotTest.makeSection(0, 173);
        new FaultSectionProperties(crustal).setPartition(PartitionPredicate.CRUSTAL);
        FaultSection subduction = MaxMagnitudeMapPlotTest.makeSection(1, 176);
        new FaultSectionProperties(subduction).setPartition(PartitionPredicate.HIKURANGI);
        FaultSystemRupSet rupSet =
                FaultSystemRupSet.builder(
                                List.of(crustal, subduction),
                                List.of(List.of(0), List.of(1), List.of(0, 1)))
                        .rupMags(new double[] {7.0, 7.0, 7.5})
                        .build();
        return new FaultSystemSolution(rupSet, rates);
    }

    protected static GriddedRegion region() {
        return new GriddedRegion(
                new Location(-42, 172), new Location(-40, 177), 0.5, GriddedRegion.ANCHOR_0_0);
    }

    protected static double valueAt(GriddedGeoDataSet map, double lat, double lon) {
        return map.get(map.getRegion().indexForLocation(new Location(lat, lon)));
    }

    /** Without solutions, rupture sets are mapped with a rate of 1 per rupture. */
    @Test
    public void testPlotRupSets() throws Exception {
        FaultSystemRupSet rupSet = makeJointSolution(new double[3]).getRupSet();
        FaultSystemRupSet compRupSet = makeJointSolution(new double[3]).getRupSet();
        ReportMetadata meta =
                new ReportMetadata(
                        new RupSetMetadata("primary", rupSet),
                        new RupSetMetadata("comp", compRupSet));
        File resourcesDir = tempFolder.newFolder("resources");

        String allText =
                String.join(
                        "\n",
                        new ParticipationChangeMapPlot(region(), RADIUS)
                                .plot(rupSet, null, meta, resourcesDir, "resources", ""));
        assertTrue(allText.contains("resources/partic_change_map_all_change.png"));
        assertTrue(allText.contains("resources/partic_change_map_joint_change.png"));
        assertTrue(new File(resourcesDir, "partic_change_map_joint_change.png").exists());
    }

    /** The default plot maps NZ_RECTANGLE and outlines the hazard map region. */
    @Test
    public void testDefaultRegions() throws Exception {
        FaultSystemSolution sol = makeSolution(new double[] {1e-3, 2e-3});
        File resourcesDir = tempFolder.newFolder("resources");
        ParticipationChangeMapPlot plot = new ParticipationChangeMapPlot();
        assertTrue(plot.region.getNodeCount() > plot.outline.getNodeCount());
        plot.plot(sol.getRupSet(), sol, (ReportMetadata) null, resourcesDir, "resources", "");
        assertTrue(new File(resourcesDir, "partic_change_map_all.png").exists());
    }

    /** Every rupture has a rate of 1. */
    @Test
    public void testUniformSolution() {
        FaultSystemRupSet rupSet = makeJointSolution(new double[3]).getRupSet();
        double[] values =
                ParticipationChangeMapPlot.sectionRateArea(
                        ParticipationChangeMapPlot.uniformSolution(rupSet));
        assertEquals(2 * rupSet.getAreaForSection(0) * 1e-6, values[0], DELTA);
    }

    /** Rate times area in km². */
    @Test
    public void testSectionRateArea() {
        FaultSystemSolution sol = makeSolution(new double[] {1e-3, 2e-3});
        double[] values = ParticipationChangeMapPlot.sectionRateArea(sol);
        double area0 = sol.getRupSet().getAreaForSection(0) * 1e-6;
        assertEquals(1e-3 * area0, values[0], DELTA);
        assertEquals(2 * values[0], values[1], DELTA);
    }

    /** Only the selected ruptures count towards participation. */
    @Test
    public void testSectionRateAreaSelected() {
        FaultSystemSolution sol = makeJointSolution(new double[] {1e-3, 2e-3, 1e-4});
        double[] values =
                ParticipationChangeMapPlot.sectionRateArea(sol, new boolean[] {false, false, true});
        FaultSystemRupSet rupSet = sol.getRupSet();
        assertEquals(1e-4 * rupSet.getAreaForSection(0) * 1e-6, values[0], DELTA);
        assertEquals(1e-4 * rupSet.getAreaForSection(1) * 1e-6, values[1], DELTA);

        values = ParticipationChangeMapPlot.sectionRateArea(sol, null);
        assertEquals(1.1e-3 * rupSet.getAreaForSection(0) * 1e-6, values[0], DELTA);
    }

    /** A node next to a section gets the whole section value; distant nodes are NaN. */
    @Test
    public void testRasterise() {
        FaultSystemSolution sol = makeSolution(new double[] {1e-3, 2e-3});
        FaultSystemRupSet rupSet = sol.getRupSet();
        double[] values = ParticipationChangeMapPlot.sectionRateArea(sol);
        GriddedGeoDataSet map =
                ParticipationChangeMapPlot.rasterise(rupSet, values, s -> true, region(), RADIUS);

        // the section is ~22 km long, so every sample point is within 25 km of -41,173
        assertEquals(values[0], valueAt(map, -41, 173), values[0] * 1e-6);
        assertEquals(values[1], valueAt(map, -41, 176), values[1] * 1e-6);
        assertTrue(Double.isNaN(valueAt(map, -41, 174.5)));

        // filtered out
        map = ParticipationChangeMapPlot.rasterise(rupSet, values, s -> s == 1, region(), RADIUS);
        assertTrue(Double.isNaN(valueAt(map, -41, 173)));
    }

    /** Each sample point counts only for the cell it is in; empty cells are NaN. */
    @Test
    public void testBinInCells() {
        FaultSystemSolution sol = makeSolution(new double[] {1e-3, 2e-3});
        FaultSystemRupSet rupSet = sol.getRupSet();
        double[] values = ParticipationChangeMapPlot.sectionRateArea(sol);
        GriddedGeoDataSet map =
                ParticipationChangeMapPlot.binInCells(rupSet, values, s -> true, region());

        // the sections lie within the cells of -41,173 and -41,176
        assertEquals(values[0], valueAt(map, -41, 173), values[0] * 1e-9);
        assertEquals(values[1], valueAt(map, -41, 176), values[1] * 1e-9);
        assertTrue(Double.isNaN(valueAt(map, -41.5, 173)));
        assertTrue(Double.isNaN(valueAt(map, -41, 173.5)));

        map = ParticipationChangeMapPlot.binInCells(rupSet, values, s -> s == 1, region());
        assertTrue(Double.isNaN(valueAt(map, -41, 173)));
    }

    /** The cell binning is used for the plot when selected. */
    @Test
    public void testPlotCellBinning() throws Exception {
        FaultSystemSolution sol = makeSolution(new double[] {1e-3, 2e-3});
        File resourcesDir = tempFolder.newFolder("resources");
        String allText =
                String.join(
                        "\n",
                        new ParticipationChangeMapPlot(
                                        region(),
                                        region(),
                                        RADIUS,
                                        ParticipationChangeMapPlot.Binning.CELL)
                                .plot(
                                        sol.getRupSet(),
                                        sol,
                                        (ReportMetadata) null,
                                        resourcesDir,
                                        "resources",
                                        ""));
        assertTrue(allText.contains("inside each grid cell"));
        assertTrue(new File(resourcesDir, "partic_change_map_all.png").exists());
    }

    /** Percentage change, with the floor applied to tiny values. */
    @Test
    public void testPercentChange() {
        GriddedRegion region = region();
        GriddedGeoDataSet primary = new GriddedGeoDataSet(region, false);
        GriddedGeoDataSet comp = new GriddedGeoDataSet(region, false);
        for (int i = 0; i < region.getNodeCount(); i++) {
            primary.set(i, Double.NaN);
            comp.set(i, Double.NaN);
        }
        primary.set(0, 1.5);
        comp.set(0, 1);
        primary.set(1, 1);
        comp.set(1, 1);
        // comparison negligible, primary not: relative to the floor of 1e-3 * median (1)
        primary.set(2, 1);
        comp.set(2, 1e-9);
        // both negligible
        primary.set(3, 1e-9);
        comp.set(3, 1e-9);

        GriddedGeoDataSet change = ParticipationChangeMapPlot.percentChange(primary, comp);
        assertEquals(50, change.get(0), DELTA);
        assertEquals(0, change.get(1), DELTA);
        assertEquals(100 * (1 / 1e-3 - 1), change.get(2), 1e-6);
        assertTrue(Double.isNaN(change.get(3)));
        assertTrue(Double.isNaN(change.get(4)));
    }

    /** Writes primary, comparison and change maps for all sections. */
    @Test
    public void testPlotWithComparison() throws Exception {
        FaultSystemSolution sol = makeSolution(new double[] {1e-3, 2e-3});
        FaultSystemSolution comp = makeSolution(new double[] {1e-3, 1e-3});
        ReportMetadata meta =
                new ReportMetadata(
                        new RupSetMetadata("primary", sol), new RupSetMetadata("comp", comp));
        File resourcesDir = tempFolder.newFolder("resources");

        List<String> lines =
                new ParticipationChangeMapPlot(region(), RADIUS)
                        .plot(sol.getRupSet(), sol, meta, resourcesDir, "resources", "");

        String allText = String.join("\n", lines);
        assertTrue(allText.contains("resources/partic_change_map_all.png"));
        assertTrue(allText.contains("resources/partic_change_map_all_comparison.png"));
        assertTrue(allText.contains("resources/partic_change_map_all_change.png"));
        // the sections have no partition
        assertFalse(allText.contains("partic_change_map_crustal"));
        assertTrue(new File(resourcesDir, "partic_change_map_all_change.png").exists());
        // no joint ruptures
        assertFalse(allText.contains("partic_change_map_joint"));
    }

    /**
     * The joint rupture row only gets comparison and change maps if the comparison has joint
     * ruptures with rate.
     */
    @Test
    public void testPlotJointRow() throws Exception {
        FaultSystemSolution sol = makeJointSolution(new double[] {1e-3, 1e-3, 1e-4});
        FaultSystemSolution comp = makeJointSolution(new double[] {1e-3, 1e-3, 0});
        ReportMetadata meta =
                new ReportMetadata(
                        new RupSetMetadata("primary", sol), new RupSetMetadata("comp", comp));
        File resourcesDir = tempFolder.newFolder("resources");

        String allText =
                String.join(
                        "\n",
                        new ParticipationChangeMapPlot(region(), RADIUS)
                                .plot(sol.getRupSet(), sol, meta, resourcesDir, "resources", ""));
        assertTrue(allText.contains("resources/partic_change_map_crustal_change.png"));
        assertTrue(allText.contains("resources/partic_change_map_subduction_change.png"));
        assertTrue(allText.contains("resources/partic_change_map_joint.png"));
        assertFalse(allText.contains("partic_change_map_joint_comparison"));
        assertTrue(new File(resourcesDir, "partic_change_map_joint.png").exists());

        comp = makeJointSolution(new double[] {1e-3, 1e-3, 2e-4});
        meta =
                new ReportMetadata(
                        new RupSetMetadata("primary", sol), new RupSetMetadata("comp", comp));
        allText =
                String.join(
                        "\n",
                        new ParticipationChangeMapPlot(region(), RADIUS)
                                .plot(sol.getRupSet(), sol, meta, resourcesDir, "resources", ""));
        assertTrue(allText.contains("resources/partic_change_map_joint_change.png"));
    }
}

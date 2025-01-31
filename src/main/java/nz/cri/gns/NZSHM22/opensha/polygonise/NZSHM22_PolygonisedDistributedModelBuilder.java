package nz.cri.gns.NZSHM22.opensha.polygonise;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_LogicTreeBranch;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_SpatialSeisPDF;
import nz.cri.gns.NZSHM22.opensha.griddedSeismicity.NZSHM22_GriddedData;
import nz.cri.gns.NZSHM22.opensha.inversion.NZSHM22_CrustalInversionTargetMFDs;
import nz.cri.gns.NZSHM22.opensha.util.SimpleGeoJsonBuilder;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModSectMinMags;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import scratch.UCERF3.analysis.FaultSystemRupSetCalc;

/**
 * Takes a background pdf grid and re-weights grid points inside fault polygons so that points close
 * to the fault trace Have a lower weight than grid points further away.
 */
public class NZSHM22_PolygonisedDistributedModelBuilder {

    protected double exponent;
    protected int step;
    protected FaultSystemSolution solution;
    protected Function<Double, Double> weightingFunction =
            getWeightingFunction(WeightingFunctionType.LINEAR, null);
    List<FaultSectionPolygonWeights> polygonWeights;

    public NZSHM22_PolygonisedDistributedModelBuilder() {}

    protected List<Double> mMinsPerSection(FaultSystemRupSet rupSet) {
        ModSectMinMags finalMinMags = rupSet.getModule(ModSectMinMags.class);
        ArrayList<GutenbergRichterMagFreqDist> grNuclMFD_List =
                FaultSystemRupSetCalc.calcImpliedGR_NuclMFD_ForEachSection(
                        rupSet,
                        NZSHM22_CrustalInversionTargetMFDs.NZ_MIN_MAG,
                        NZSHM22_CrustalInversionTargetMFDs.NZ_NUM_BINS,
                        NZSHM22_CrustalInversionTargetMFDs.DELTA_MAG);
        List<Double> result = new ArrayList<>();
        for (int s = 0; s < grNuclMFD_List.size(); s++) {
            GutenbergRichterMagFreqDist grNuclMFD = grNuclMFD_List.get(s);
            int mMaxIndex = grNuclMFD.getClosestXIndex(rupSet.getMinMagForSection(s));
            mMaxIndex =
                    Math.max(
                            mMaxIndex,
                            grNuclMFD.getClosestXIndex(finalMinMags.getMinMagForSection(s)));
            result.add(grNuclMFD.getX(mMaxIndex));
        }
        return result;
    }

    /**
     * Returns the mMin for a grid point. This accumulates the mmins from all polygons that the grid
     * point is in. Mmins from more than one polygon are weighted by intersection of polygon and
     * grid bin.
     *
     * @param point
     * @param gridBin
     * @param mmins
     * @return
     */
    protected double gridMmin(Location point, Region gridBin, List<Double> mmins) {
        double totalArea = 0;
        double totalMmin = 0;
        for (int s = 0; s < polygonWeights.size(); s++) {
            if (polygonWeights.get(s).originalPoly.contains(point)) {
                Region intersection = Region.intersect(gridBin, polygonWeights.get(s).originalPoly);
                if (intersection != null) {
                    double area = intersection.getExtent();
                    totalArea += area;
                    totalMmin += area * mmins.get(s);
                }
            }
        }
        if (totalArea == 0) {
            return 0;
        } else {
            return totalMmin / totalArea;
        }
    }

    protected List<Double> gridMmins(NZSHM22_GriddedData grid, FaultSystemRupSet rupSet) {
        List<Double> sectionMmins = mMinsPerSection(rupSet);
        List<Double> result = new ArrayList<>();
        List<Location> points = grid.getGridPoints();
        List<Region> bins = grid.getGridBins();
        for (int p = 0; p < points.size(); p++) {
            result.add(gridMmin(points.get(p), bins.get(p), sectionMmins));
        }
        return result;
    }

    private void printGridPoints(
            int section, NZSHM22_GriddedData origGrid, NZSHM22_GriddedData resultGrid) {
        FaultSectionPolygonWeights polyWeights = polygonWeights.get(section);
        GriddedRegion polyRegion =
                new GriddedRegion(
                        polyWeights.originalPoly, origGrid.getSpacing(), GriddedRegion.ANCHOR_0_0);
        for (Location gridPoint : polyRegion.getNodeList()) {
            System.out.println(
                    gridPoint.getLatitude()
                            + ", "
                            + gridPoint.getLongitude()
                            + ", "
                            + polyWeights.polygonWeight(gridPoint)
                            + ", "
                            + origGrid.getValue(gridPoint)
                            + ", "
                            + resultGrid.getValue(gridPoint));
        }
    }

    private void geojsonGrid(
            int section, NZSHM22_GriddedData grid, NZSHM22_GriddedData resultGrid) {
        SimpleGeoJsonBuilder builder = new SimpleGeoJsonBuilder();
        FaultSectionPolygonWeights polyWeights = polygonWeights.get(section);
        GriddedRegion polyRegion =
                new GriddedRegion(
                        polyWeights.originalPoly, grid.getSpacing(), GriddedRegion.ANCHOR_0_0);
        for (Location gridPoint : polyRegion.getNodeList()) {
            builder.addLocation(
                    gridPoint,
                    "value",
                    "" + grid.getValue(gridPoint),
                    "resultValue",
                    "" + resultGrid.getValue(gridPoint),
                    "weight",
                    "" + polyWeights.polygonWeight(gridPoint));
        }
        builder.toJSON("polypoints.geojson");
    }

    protected NZSHM22_GriddedData scalePDF(FaultSystemRupSet rupSet) {
        NZSHM22_LogicTreeBranch branch = rupSet.getModule(NZSHM22_LogicTreeBranch.class);
        NZSHM22_SpatialSeisPDF spatialSeisPDF = branch.getValue(NZSHM22_SpatialSeisPDF.class);

        NZSHM22_GriddedData upSampled =
                NZSHM22_GriddedData.reSample(spatialSeisPDF.getGriddedData(), step);

        Map<Location, List<Double>> tempGrid = new HashMap<>();

        for (int s = 0; s < rupSet.getNumSections(); s++) {
            FaultSectionPolygonWeights polyWeights = polygonWeights.get(s);
            GriddedRegion polyRegion =
                    new GriddedRegion(
                            polyWeights.originalPoly,
                            upSampled.getSpacing(),
                            GriddedRegion.ANCHOR_0_0);
            List<Location> gridPoints = new ArrayList<>();
            List<Double> oldValues = new ArrayList<>();
            for (Location gridPoint : polyRegion.getNodeList()) {
                Double oldValue = upSampled.getValue(gridPoint);
                if (oldValue != null) {
                    gridPoints.add(gridPoint);
                    oldValues.add(oldValue);
                }
            }

            List<Double> ds =
                    gridPoints.stream()
                            .map(
                                    location ->
                                            Math.pow(polyWeights.polygonWeight(location), exponent))
                            .collect(Collectors.toList());
            double sumOfAllDs =
                    ds.stream().filter(v -> v >= 0).mapToDouble(Double::doubleValue).sum();

            for (int p = 0; p < gridPoints.size(); p++) {
                Location location = gridPoints.get(p);
                double d = ds.get(p);
                double oldValue = oldValues.get(p);
                List<Double> newValues = tempGrid.computeIfAbsent(location, k -> new ArrayList<>());
                if (d == -1) {
                    // not in a polygon
                    newValues.add(oldValue);
                } else {
                    //                    newValues.add(oldValue * d / sumOfAllDs);
                    newValues.add(oldValue * d);
                }
            }
        }

        for (Location location : tempGrid.keySet()) {
            List<Double> values = tempGrid.get(location);
            double newValue;
            if (values.size() == 1) {
                newValue = values.get(0);
            } else {
                newValue = values.stream().mapToDouble(Double::doubleValue).sum() / values.size();
            }
            upSampled.setValue(location, newValue);
        }

        return NZSHM22_GriddedData.reSample(upSampled, NZSHM22_GriddedData.STEP);
    }

    public NZSHM22_PolygonisedDistributedModelBuilder setExponent(double pdfWeight) {
        this.exponent = pdfWeight;
        return this;
    }

    public NZSHM22_PolygonisedDistributedModelBuilder setStep(int step) {
        this.step = step;
        return this;
    }

    protected Function<Double, Double> getWeightingFunction(
            WeightingFunctionType type, double[] parameters) {
        switch (type) {
            case LINEAR:
                return weight -> weight;
            default:
                throw new IllegalArgumentException(
                        "Unsupported WeightingFunctionType " + type.name());
        }
    }

    /**
     * The weighting function is linear by default.
     *
     * @param type
     * @param parameters
     * @return
     */
    public NZSHM22_PolygonisedDistributedModelBuilder setWeightingFunction(
            String type, double... parameters) {
        weightingFunction = getWeightingFunction(WeightingFunctionType.valueOf(type), parameters);
        return this;
    }

    public void apply(FaultSystemRupSet rupSet) {
        NZSHM22_LogicTreeBranch branch = rupSet.getModule(NZSHM22_LogicTreeBranch.class);
        NZSHM22_SpatialSeisPDF spatialSeisPDF = branch.getValue(NZSHM22_SpatialSeisPDF.class);
        NZSHM22_GriddedData oldGrid = spatialSeisPDF.getGriddedData();
        polygonWeights = FaultSectionPolygonWeights.fromRupSet(rupSet);
        NZSHM22_GriddedData newGrid = scalePDF(rupSet);
        List<Double> mMins = gridMmins(newGrid, rupSet);
        rupSet.addModule(new NZSHM22_PolygonisedDistributedModel(oldGrid, newGrid, mMins));
    }
}

package nz.cri.gns.NZSHM22.opensha.polygonise;

import nz.cri.gns.NZSHM22.opensha.griddedSeismicity.NZSHM22_GriddedData;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.faultSurface.FaultSection;

import java.util.ArrayList;
import java.util.List;

public class Polygoniser {

    FaultSystemSolution solution;
    List<FaultSectionPolygonWeights> polygonWeights;
    NZSHM22_GriddedData griddedData;
    List<NZSHM22_GriddedData.GridPoint> gridPoints;

    public Polygoniser(FaultSystemSolution solution, NZSHM22_GriddedData griddedData) {
        this.solution = solution;
        this.griddedData = griddedData;
        gridPoints = griddedData.getPoints();
        polygonWeights = FaultSectionPolygonWeights.fromSolution(solution);
    }

    public void polygonise(FaultSectionPolygonWeights section) {
        List<NZSHM22_GriddedData.GridPoint> sectionPoints = new ArrayList<>();
        for (NZSHM22_GriddedData.GridPoint point : gridPoints) {
            double weight = section.polygonWeight(point);
            if (weight != -1) {

            }
        }


    }


}

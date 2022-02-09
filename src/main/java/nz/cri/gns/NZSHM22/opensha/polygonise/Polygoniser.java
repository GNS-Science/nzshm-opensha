package nz.cri.gns.NZSHM22.opensha.polygonise;

import nz.cri.gns.NZSHM22.opensha.griddedSeismicity.NZSHM22_GriddedData;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.faultSurface.FaultSection;

import java.util.ArrayList;
import java.util.List;

public class Polygoniser {

    FaultSystemSolution solution;
    FaultPolygon polygonWeights;
    NZSHM22_GriddedData griddedData;
    List<NZSHM22_GriddedData.GridPoint> gridPoints;

    public Polygoniser(FaultSystemSolution solution, NZSHM22_GriddedData griddedData){
        this.solution = solution;
        this.griddedData = griddedData;
        this.gridPoints = griddedData.getPoints();
        polygonWeights = new FaultPolygon(solution);
    }

    public void polygonise(FaultSection section){
        FaultPolygon.Section polygonSection = polygonWeights.get(section.getSectionId());
        List<NZSHM22_GriddedData.GridPoint> sectionPoints = new ArrayList<>();
        for(NZSHM22_GriddedData.GridPoint point : gridPoints){
            if(polygonSection.contains(point.getLocation())){
                sectionPoints.add(point);
            }
        }





    }




}

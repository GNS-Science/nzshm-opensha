package nz.cri.gns.NZSHM22.opensha.inversion;

import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_LogicTreeBranch;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_SpatialSeisPDF;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModSectMinMags;
import org.opensha.sha.earthquake.faultSysSolution.modules.PolygonFaultGridAssociations;
import org.opensha.sha.faultSurface.FaultSection;
import scratch.UCERF3.griddedSeismicity.FaultPolyMgr;
import scratch.UCERF3.inversion.U3InversionTargetMFDs;

import java.awt.geom.Area;
import java.util.*;
import java.util.function.IntPredicate;

public class RegionalRupSetData {

    FaultSystemRupSet original;
    GriddedRegion region;
    NZSHM22_SpatialSeisPDF spatialSeisPDF;

    PolygonFaultGridAssociations polygonFaultGridAssociations;
    List<FaultSection> sections = new ArrayList<>();
    List<Double> minMags = new ArrayList<>();
    double maxMag = 0;

    public RegionalRupSetData(FaultSystemRupSet original, GriddedRegion region){
        this.original = original;
        this.region= region;
        this.spatialSeisPDF = original.getModule(NZSHM22_LogicTreeBranch.class).getValue(NZSHM22_SpatialSeisPDF.class);
        filter(createRegionFilter(region));
    }

    protected void filter(IntPredicate sectionPredicate) {
        List<? extends FaultSection> originalSections = original.getFaultSectionDataList();
        ModSectMinMags minMagsModule = original.getModule(ModSectMinMags.class);
        for (int s = 0; s < originalSections.size(); s++) {
            if (sectionPredicate.test(s)) {
                FaultSection section = originalSections.get(s).clone();
                section.setSectionId(sections.size());
                sections.add(section);
                minMags.add(minMagsModule.getMinMagForSection(s));

                for (Integer r : original.getRupturesForSection(s)) {
                    maxMag = Math.max(maxMag, original.getMagForRup(r));
                }
            }
        }
        polygonFaultGridAssociations = FaultPolyMgr.create(sections, U3InversionTargetMFDs.FAULT_BUFFER, region);
    }

    protected IntPredicate createRegionFilter(GriddedRegion region) {
        Area area = region.getShape();
        PolygonFaultGridAssociations polyMgr = original.getModule(PolygonFaultGridAssociations.class);
        return s -> {
            Area sectionArea = polyMgr.getPoly(s).getShape();
            sectionArea.intersect(area);
            return !sectionArea.isEmpty();
        };
    }

    public List<? extends FaultSection> getFaultSectionDataList(){
        return sections;
    }

    public PolygonFaultGridAssociations getPolygonFaultGridAssociations(){
        return polygonFaultGridAssociations;
    }

    public double getMaxMag(){
        return maxMag;
    }

    public double getMinMagForSection(int sectionIndex){
        return minMags.get(sectionIndex);
    }

    public NZSHM22_SpatialSeisPDF getSpatialSeisPDF(){
        return spatialSeisPDF;
    }
}

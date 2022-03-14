package nz.cri.gns.NZSHM22.opensha.inversion;

import nz.cri.gns.NZSHM22.opensha.analysis.NZSHM22_FaultSystemRupSetCalc;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_FaultPolyParameters;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_LogicTreeBranch;
import nz.cri.gns.NZSHM22.opensha.enumTreeBranches.NZSHM22_SpatialSeisPDF;
import nz.cri.gns.NZSHM22.opensha.griddedSeismicity.NZSHM22_FaultPolyMgr;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.PolygonFaultGridAssociations;
import org.opensha.sha.faultSurface.FaultSection;

import java.util.*;
import java.util.function.IntPredicate;

public class RegionalRupSetData {

    FaultSystemRupSet original;
    GriddedRegion region;
    NZSHM22_SpatialSeisPDF spatialSeisPDF;
    double minSeismoMag;

    PolygonFaultGridAssociations polygonFaultGridAssociations;
    List<FaultSection> sections = new ArrayList<>();
    List<Double> minMags = new ArrayList<>();
    double maxMag = 0;

    boolean[] originalSectionIncluded;
    double[] originalMinMags;

    public RegionalRupSetData(FaultSystemRupSet original, GriddedRegion region, IntPredicate sectionIdFilter, double minSeismoMag){
        this.original = original;
        this.region= region;
        this.spatialSeisPDF = original.getModule(NZSHM22_LogicTreeBranch.class).getValue(NZSHM22_SpatialSeisPDF.class);
        this.minSeismoMag = minSeismoMag;
        filter(sectionIdFilter);
    }

    protected void filter(IntPredicate sectionPredicate) {
        List<? extends FaultSection> originalSections = original.getFaultSectionDataList();
        originalMinMags = NZSHM22_FaultSystemRupSetCalc.computeMinSeismoMagForSections(original, minSeismoMag);
        originalSectionIncluded = new boolean[originalMinMags.length];

        for (int s = 0; s < originalSections.size(); s++) {
            if (sectionPredicate.test(s)) {
                FaultSection section = originalSections.get(s).clone();
                section.setSectionId(sections.size());
                sections.add(section);
                minMags.add(originalMinMags[s]);
                originalSectionIncluded[s] = true;

                for (Integer r : original.getRupturesForSection(s)) {
                    maxMag = Math.max(maxMag, original.getMagForRup(r));
                }
            }
        }
        NZSHM22_FaultPolyParameters parameters = original.getModule(NZSHM22_LogicTreeBranch.class).getValue(NZSHM22_FaultPolyParameters.class);
        polygonFaultGridAssociations = NZSHM22_FaultPolyMgr.create(sections, parameters.getBufferSize(), parameters.getMinBufferSize(), region);
        spatialSeisPDF.normaliseRegion(region);
    }

    public GriddedRegion getRegion(){
        return region;
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

    public boolean isInRegion(int originalSectionId){
        return originalSectionIncluded[originalSectionId];
    }

    public double getMinMagForOriginalSectionid(int originalSectionid){
        return originalMinMags[originalSectionid];
    }
}

package nz.cri.gns.NZSHM22.opensha.ruptures.experimental.rsqsims;

import com.google.common.base.Preconditions;
import nz.cri.gns.NZSHM22.opensha.util.SimpleGeoJsonBuilder;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture.MultiRuptureJump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture.StiffnessCalcModule;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture.impl.MultiRuptureCoulombFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture.impl.MultiRuptureFractCoulombPositiveFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture.impl.MultiRuptureNetCoulombPositiveFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityResult;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.coulomb.ParentCoulombCompatibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;

import java.io.*;
import java.util.*;

public class CoulombTester {

    String stiffnessCache;
    FaultSystemRupSet rupSet;
    final SectionDistanceAzimuthCalculator disAzCalc;
    StiffnessCalcModule stiffness;
    List<MultiRuptureCoulombFilter> filters = new ArrayList<>();

    public CoulombTester(FaultSystemRupSet rupSet, String stiffnessCache) throws IOException {
        this.rupSet = rupSet;
        this.stiffnessCache = stiffnessCache;
        this.disAzCalc = new SectionDistanceAzimuthCalculator(rupSet.getFaultSectionDataList());
    }

    void setupStiffness() {

        stiffness = new StiffnessCalcModule(rupSet, 2, new File(stiffnessCache));

        // what fraction of interactions should be positive? this number will take some tuning
        float fractThreshold = 0.75f;
        MultiRuptureFractCoulombPositiveFilter fractCoulombFilter = new MultiRuptureFractCoulombPositiveFilter(stiffness.stiffnessCalc, fractThreshold, ParentCoulombCompatibilityFilter.Directionality.BOTH);
        filters.add(fractCoulombFilter);

        // force the net coulomb from one rupture to the other to positive; this more heavily weights nearby interactions
        ParentCoulombCompatibilityFilter.Directionality netDirectionality = ParentCoulombCompatibilityFilter.Directionality.BOTH; // require it to be positive to from subduction to crustal AND from crustal to subduction
//     	Directionality netDirectionality = Directionality.EITHER; // require it to be positive to from subduction to crustal OR from crustal to subduction
        MultiRuptureNetCoulombPositiveFilter netCoulombFilter = new MultiRuptureNetCoulombPositiveFilter(stiffness.stiffnessCalc, netDirectionality);
        filters.add(netCoulombFilter);
    }

    public void saveCache() {
        stiffness.checkUpdateStiffnessCache();
    }

    public List<PlausibilityResult> applyCoulomb(MultiRuptureJump jump){
        PlausibilityResult r0 = filters.get(0).apply(jump, false);
        PlausibilityResult r1 = filters.get(1).apply(jump, false);
        PlausibilityResult result = r0.logicalAnd(r1);
        return List.of(r0, r1, result);
    }


}

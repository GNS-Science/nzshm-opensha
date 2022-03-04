package nz.cri.gns.NZSHM22.opensha.inversion;

import com.google.common.base.Preconditions;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.RupSetScalingRelationship;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class RupSetMaxMagFilter {

    public static FaultSystemRupSet filter(FaultSystemRupSet original, RupSetScalingRelationship scaling, double maxMagTVZ, double maxMagSans) throws IOException {
        Preconditions.checkArgument(original.getModule(RegionSections.class) != null);
        List<List<Integer>> filteredRups = filterRups(original, maxMagTVZ, maxMagSans);

        FaultSystemRupSet rupSet = FaultSystemRupSet.builder(original.getFaultSectionDataList(), filteredRups)
                .forScalingRelationship(scaling)
                .build();

        rupSet.addModule(ClusterRuptures.singleStranged(rupSet));

        return rupSet;
    }

    protected static List<List<Integer>> filterRups(FaultSystemRupSet rupSet, double maxMagTVZ, double maxMagSans) {
        List<List<Integer>> result = new ArrayList<>();
        RegionSections tvzSections = rupSet.getModule(RegionSections.class);

        for (int r = 0; r < rupSet.getNumRuptures(); r++) {
            List<Integer> rupture = rupSet.getSectionIndicesForAllRups().get(r);
            boolean inTVZ = tvzSections.isInRegion(rupture);
            double mag = rupSet.getMagForRup(r);
            if ((inTVZ && mag <= maxMagTVZ) || (!inTVZ && mag <= maxMagSans)) {
                result.add(rupture);
            }
        }

        return result;
    }

}

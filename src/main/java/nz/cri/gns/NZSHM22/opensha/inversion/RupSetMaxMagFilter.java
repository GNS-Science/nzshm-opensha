package nz.cri.gns.NZSHM22.opensha.inversion;

import com.google.common.base.Preconditions;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.RupSetScalingRelationship;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class RupSetMaxMagFilter {

    public static FaultSystemRupSet filter(FaultSystemRupSet original, RupSetScalingRelationship scaling, double maxMagTVZ, double maxMagSans) throws IOException {
        Preconditions.checkArgument(original.getModule(NZSHM22_TvzSections.class) != null);
        List<List<Integer>> filteredRups = filterRups(original, maxMagTVZ, maxMagSans);

        FaultSystemRupSet rupSet = FaultSystemRupSet.builder(original.getFaultSectionDataList(), filteredRups)
                .forScalingRelationship(scaling)
                .build();

        return rupSet;
    }

    protected static List<List<Integer>> filterRups(FaultSystemRupSet rupSet, double maxMagTVZ, double maxMagSans) {
        List<List<Integer>> result = new ArrayList<>();
        NZSHM22_TvzSections tvzSections = rupSet.getModule(NZSHM22_TvzSections.class);

        for (int r = 0; r < rupSet.getNumRuptures(); r++) {
            List<Integer> rupture = rupSet.getSectionIndicesForAllRups().get(r);
            boolean inTVZ = tvzSections.isInTvz(rupture);
            double mag = rupSet.getMagForRup(r);
            if ((inTVZ && mag <= maxMagTVZ) || (!inTVZ && mag <= maxMagSans)) {
                result.add(rupture);
            }
        }

        return result;
    }

}

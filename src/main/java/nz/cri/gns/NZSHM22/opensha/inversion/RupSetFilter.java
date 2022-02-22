package nz.cri.gns.NZSHM22.opensha.inversion;

import nz.cri.gns.NZSHM22.opensha.data.region.NewZealandRegions;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.RupSetScalingRelationship;
import org.opensha.sha.earthquake.faultSysSolution.modules.AveSlipModule;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;

public class RupSetFilter {

    public static FaultSystemRupSet filter(FaultSystemRupSet original, RupSetScalingRelationship scaling, double maxMagTVZ, double maxMagSans) throws IOException {

        List<List<Integer>> filteredRups = filterRups(original, maxMagTVZ, maxMagSans);

        FaultSystemRupSet rupSet = FaultSystemRupSet.builder(original.getFaultSectionDataList(), filteredRups)
                .forScalingRelationship(scaling)
                .build();

        for (OpenSHA_Module module : original.getModules(true)) {
            if (!(module instanceof AveSlipModule)) {
                rupSet.addModule(module);
            }
        }

        return rupSet;
    }

    public static List<List<Integer>> filterRups(FaultSystemRupSet rupSet, double maxMagTVZ, double maxMagSans ) {
        List<List<Integer>> result = new ArrayList<>();
        IntPredicate predicate = RegionalRupSetData.createRegionFilter(rupSet, new NewZealandRegions.NZ_TVZ_GRIDDED());
        Set<Integer> tvzSections = new HashSet<>();
        for (int i = 0; i < rupSet.getNumSections(); i++) {
            if (predicate.test(i)) {
                tvzSections.add(i);
            }
        }
        for (int r = 0; r < rupSet.getNumRuptures(); r++) {
            List<Integer> rupture = rupSet.getSectionIndicesForAllRups().get(r);
            boolean inTVZ = false;
            for (int section : rupture) {
                if (tvzSections.contains(section)) {
                    inTVZ = true;
                    break;
                }
            }
            double mag = rupSet.getMagForRup(r);
            if ((inTVZ && mag <= maxMagTVZ ) || (!inTVZ && mag <= maxMagSans)) {
                result.add(rupture);
            }
        }

        return result;
    }

//    public static void main(String[] args) throws IOException {
//        FaultSystemRupSet rupSet = filter("C:\\Code\\NZSHM\\nzshm-opensha\\TEST\\ruptures\\original.zip",
//                NZSHM22_LogicTreeBranch.crustalInversion());
//        rupSet.write(new File("TEST/ruptures/filteredRupset.zip"));
//    }
}

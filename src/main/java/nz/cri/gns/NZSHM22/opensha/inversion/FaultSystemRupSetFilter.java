package nz.cri.gns.NZSHM22.opensha.inversion;

import org.opensha.commons.util.modules.SubModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.faultSurface.FaultSection;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

public class FaultSystemRupSetFilter {


    public static FaultSystemRupSet filter0Slip(FaultSystemRupSet original) {
        return filter(original, r -> {
            for (FaultSection section : original.getFaultSectionDataForRupture(r)) {
                if (0 == section.getOrigAveSlipRate()) {
                    return false;
                }
            }
            return true;
        });
    }


    public static FaultSystemRupSet filter(FaultSystemRupSet original, IntPredicate predicate) {
        List<List<Integer>> sectionForRups = new ArrayList<>();
        List<Double> mags = new ArrayList<>();
        List<Double> rakes = new ArrayList<>();
        List<Double> rupAreas = new ArrayList<>();
        List<Double> rupLengths = new ArrayList<>();
        List<ClusterRupture> clusterRuptures = new ArrayList<>();

        ClusterRuptures rupturesModule = original.getModule(ClusterRuptures.class);

        for (int i = 0; i < original.getMagForAllRups().length; i++) {
            if (predicate.test(i)) {
                sectionForRups.add(original.getSectionsIndicesForRup(i));
                mags.add(original.getMagForRup(i));
                rakes.add(original.getAveRakeForRup(i));
                rupAreas.add(original.getAreaForRup(i));
                rupLengths.add(original.getLengthForRup(i));
                clusterRuptures.add(rupturesModule.get(i));
            }
        }

        FaultSystemRupSet result = new FaultSystemRupSet(
                original.getFaultSectionDataList(),
                sectionForRups,
                mags.stream().mapToDouble(Double::doubleValue).toArray(),
                rakes.stream().mapToDouble(Double::doubleValue).toArray(),
                rupAreas.stream().mapToDouble(Double::doubleValue).toArray(),
                rupLengths.stream().mapToDouble(Double::doubleValue).toArray());

        result.addModule(ClusterRuptures.instance(result,clusterRuptures));

        System.out.println("Original rupture count " + original.getMagForAllRups().length);
        System.out.println("New rupture count: " + result.getMagForAllRups().length);

        return result;

    }
}

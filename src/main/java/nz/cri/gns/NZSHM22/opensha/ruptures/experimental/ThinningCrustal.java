package nz.cri.gns.NZSHM22.opensha.ruptures.experimental;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.JumpAzimuthChangeFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.TotalAzimuthChangeFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.U3CompatibleCumulativeRakeChangeFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;

public class ThinningCrustal {

    public static List<Integer> filterCrustal(FaultSystemRupSet crustalRupSet) {
        ClusterRuptures cRups = crustalRupSet.getModule(ClusterRuptures.class);
        if (cRups == null) {
            // assume single stranded for our purposes here
            cRups = ClusterRuptures.singleStranged(crustalRupSet);
        }
        List<ClusterRupture> crustalRuptures = new ArrayList<>(cRups.getAll());
        Map<ClusterRupture, Integer> indices = new HashMap<>();
        for (int r = 0; r < crustalRuptures.size(); r++) {
            indices.put(crustalRuptures.get(r), r);
        }

        crustalRuptures.removeIf(r -> r.clusters[0].startSect.getSectionName().contains("row:"));

        System.out.println("crustal ruptures " + crustalRuptures.size());
        SectionDistanceAzimuthCalculator crustalDistAzCalc =
                new SectionDistanceAzimuthCalculator(crustalRupSet.getFaultSectionDataList());
        JumpAzimuthChangeFilter.AzimuthCalc azimuthCalc =
                new JumpAzimuthChangeFilter.SimpleAzimuthCalc(crustalDistAzCalc);
        TotalAzimuthChangeFilter totAzFilter =
                new TotalAzimuthChangeFilter(azimuthCalc, 10, true, true);
        crustalRuptures.removeIf(r -> !totAzFilter.apply(r, false).isPass());
        System.out.println("crustal ruptures after azimuth filtering " + crustalRuptures.size());
        U3CompatibleCumulativeRakeChangeFilter rakeChangeFilter =
                new U3CompatibleCumulativeRakeChangeFilter(10);
        crustalRuptures.removeIf(r -> !rakeChangeFilter.apply(r, false).isPass());
        System.out.println("crustal ruptures after rake filtering " + crustalRuptures.size());

        return crustalRuptures.stream().map(indices::get).collect(Collectors.toList());
    }
}
